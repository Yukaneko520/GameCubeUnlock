package com.yuka.gamecube.tools.hooks.backend;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import com.yuka.gamecube.tools.compat.*;
import com.yuka.gamecube.tools.core.DiagnosticThrottle;
import com.yuka.gamecube.tools.hooks.*;
import com.yuka.gamecube.tools.hooks.thermal.ThermalSessions;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface;
import java.util.ArrayList;

/** GameWatch reuses the system backend's verified, reconnecting configuration transport. */
public final class ThermalWatchHooks {
    final XposedModule module;
    final ClassLoader loader;
    final ThermalLayout names;
    final Context context;
    final boolean os5;
    final ThermalSessions sessions=new ThermalSessions();
    private volatile BackendPolicy policy=BackendPolicy.closed();
    private final DiagnosticThrottle logs=new DiagnosticThrottle(256);
    private final ArrayList<XposedInterface.HookHandle> handles=new ArrayList<>();
    private final Handler main=new Handler(Looper.getMainLooper());
    private BackendConfig config;
    private ThermalFpsHooks fps;
    private ThermalGraphicsHooks graphics;
    private BenchmarkWatchHooks benchmark;
    private ThermalWatchHooks(XposedModule module,ClassLoader loader,Context context,TargetBaseline baseline) {
        this.context=context;
        this.module=module;this.loader=loader;names=ThermalLayout.forSdk(baseline.sdk());os5=baseline.sdk()<=35;
    }
    public static ThermalWatchHooks install(XposedModule module,ClassLoader loader,Context context,TargetBaseline baseline) throws Throwable {
        var watch=new ThermalWatchHooks(module,loader,context,baseline);
        try {
            watch.fps=new ThermalFpsHooks(watch);
            if(watch.names.memc()!=null) watch.graphics=new ThermalGraphicsHooks(watch);
            watch.fps.install();if(watch.graphics!=null) watch.graphics.install();
            try { watch.benchmark=new BenchmarkWatchHooks(watch,watch.main); }
            catch(Throwable error) { HookLog.write(module,android.app.Application.getProcessName(),"feature=benchmark stage=preflight_closed",error); }
            watch.config=new BackendConfig(module,context,watch.main,new BackendConfig.Sink() {
                @Override public void apply(BackendPolicy next,long token,java.util.function.LongSupplier epoch) {
                    BackendPolicy old;
                    synchronized(watch.fps.lock()) {
                        if(token!=epoch.getAsLong()) return;
                        old=watch.policy;watch.policy=next;
                    }
                    watch.reconcile(old,next);
                }
                @Override public void invalidate(String pkg) {
                    BackendPolicy old;
                    synchronized(watch.fps.lock()) { old=watch.policy;watch.policy=old.without(pkg); }
                    watch.main.post(()->watch.reconcile(old,watch.policy));
                }
            },watch::log,watch::error);
            watch.config.request();
            watch.log("stage=bindings_ready baseline="+baseline.id()+" identity=pending scope=com.vivo.gamewatch benchmark_policy=separate_opt_in");
            return watch;
        } catch(Throwable failure) {
            for(var handle:watch.handles) handle.unhook();
            throw failure;
        }
    }
    void foreground(int uid,boolean entered,int pid,String pkg) {
        if(benchmark!=null) benchmark.foreground(uid,entered,pid,pkg);
    }
    void death(int uid,int pid,String pkg) { if(benchmark!=null) benchmark.death(uid,pid,pkg); }
    int benchmarkMode(String pkg,int uid) {
        return policy.benchmarkMode(pkg,uid);
    }
    boolean allowed(int pid,String pkg) {
        BackendPolicy current=policy;
        return current.verified && current.installedUid(pkg)!=null && sessions.allows(current.snapshot,pid,pkg);
    }
    long revision() { return policy.snapshot.revision; }
    boolean changed(BackendPolicy old,BackendPolicy next,String pkg) {
        return selected(old,pkg)!=selected(next,pkg);
    }
    private boolean selected(BackendPolicy p,String pkg) {
        return p.verified && p.installedUid(pkg)!=null && p.snapshot.allows(pkg,"thermalBypass");
    }
    private void reconcile(BackendPolicy old,BackendPolicy next) {
        fps.reconcile(old,next);if(graphics!=null) graphics.reconcile(old,next);
        if(benchmark!=null) benchmark.reconcile();
        log("stage=config revision="+revision()+" verified="+next.verified+" selected="+next.snapshot.apps.entrySet().stream()
                .filter(e->selected(next,e.getKey())).map(java.util.Map.Entry::getKey).toList());
        HookLog.write(module,android.app.Application.getProcessName(),"feature=benchmark stage=config revision="+revision()
                +" verified="+next.verified+" master="+next.snapshot.enabled+" rules="+next.snapshot.apps.entrySet().stream()
                .filter(e->e.getValue().benchmarkMode!=0)
                .map(e->e.getKey()+":"+e.getValue().benchmarkMode+":enabled="+e.getValue().enabled).toList(),null);
    }
    Class<?> type(String name) throws ClassNotFoundException { return Class.forName(name,false,loader); }
    void hook(String owner,String name,Class<?> returns,XposedInterface.Hooker callback,Class<?>...args) throws Throwable {
        var method=Reflect.method(type(owner),name,args);
        if(method.getReturnType()!=returns) throw new NoSuchMethodException(owner+"."+name+" return mismatch");
        handles.add(module.hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(callback));
    }
    void log(String detail) {
        if(logs.allow(detail,SystemClock.elapsedRealtime(),5000)) HookLog.write(module,android.app.Application.getProcessName(),"feature=thermal "+detail,null);
    }
    void error(String stage,Throwable error) { HookLog.write(module,android.app.Application.getProcessName(),"feature=thermal stage="+stage,error); }
}
