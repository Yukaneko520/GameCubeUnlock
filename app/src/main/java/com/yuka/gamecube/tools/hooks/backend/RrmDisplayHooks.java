package com.yuka.gamecube.tools.hooks.backend;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import com.yuka.gamecube.tools.BuildConfig;
import com.yuka.gamecube.tools.core.DiagnosticThrottle;
import com.yuka.gamecube.tools.hooks.HookLog;
import java.util.*;
import java.util.function.LongSupplier;

/** Selected windows and normal scene rates use 144; OEM owns arbitration, caps and handle release. */
public final class RrmDisplayHooks implements BackendConfig.Sink {
    private final XposedModule module;
    private final RrmAccess access;
    private final BackendPolicy.Publication publication=new BackendPolicy.Publication();
    private record Armed(BackendPolicy source,DisplayRatePolicy.Selection selection) {}
    private volatile Armed armed=new Armed(publication.current(),DisplayRatePolicy.Selection.closed());
    private final Set<Object> owned=Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private final DiagnosticThrottle throttle=new DiagnosticThrottle(128);
    private final List<XposedInterface.HookHandle> handles=new ArrayList<>();
    private Handler owner;
    private Object manager;
    private BackendConfig config;
    private final ClassLoader loader;
    private final String prefix;
    private volatile long withdrawalTraceUntil;
    private RrmDisplayHooks(XposedModule module,ClassLoader loader) throws Exception {
        this.module=module;
        var baseline=com.yuka.gamecube.tools.compat.TargetProfiles.descriptors(android.os.Build.VERSION.SDK_INT);
        access=new RrmAccess(loader,com.yuka.gamecube.tools.compat.RomAdapter.forBaseline(baseline).display());
        this.loader=loader;
        prefix=com.yuka.gamecube.tools.compat.RomAdapter.forBaseline(baseline).display().prefix();
    }
    public static RrmDisplayHooks install(XposedModule module,ClassLoader loader) throws Throwable {
        RrmDisplayHooks adapter=new RrmDisplayHooks(module,loader);
        adapter.handles.add(module.hook(adapter.access.initialize)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain-> {
                    Object result=chain.proceed();
                    try { adapter.ready((Context)chain.getArg(0)); }
                    catch(Throwable error) { adapter.error("ready_closed",error); }
                    return result;
                }));
        adapter.log("stage=await_rms_initialize"); return adapter;
    }
    private synchronized void ready(Context context) throws Throwable {
        if(config!=null) { config.request(); return; }
        manager=access.manager(); owner=access.handler(manager);
        if(context==null || owner==null) throw new IllegalStateException("RMS context/handler unavailable");
        List<XposedInterface.HookHandle> family=new ArrayList<>();
        try {
            new RrmSceneHooks(module,loader,prefix,access,manager,()->{
                Armed live=armed;
                return live.source==publication.current()?live.selection:null;
            },()->SystemClock.elapsedRealtime()<withdrawalTraceUntil,this::log,this::error).install(family);
            List<XposedInterface.HookHandle> thermal=new ArrayList<>();
            try {
                new RrmThermalSceneHooks(module,loader,prefix,access,manager,publication::current).install(thermal);
                family.addAll(thermal);
            } catch(Throwable failure) {
                for(int i=thermal.size()-1;i>=0;i--) thermal.get(i).unhook();
                HookLog.write(module,"system","feature=thermal stage=refresh_vote_bindings_failed",failure);
            }
            family.add(module.hook(access.getRequest).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::rewrite));
            family.add(module.hook(access.cancelRequest).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::cancelled));
            family.add(module.hook(access.applyRequest).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::beforeApply));
            config=new BackendConfig(module,context,owner,this,DisplayRatePolicy::target,
                    info->DisplayRatePolicy.primaryUid(info.uid),this::log,this::error);
            handles.addAll(family); config.request();
            log("stage=ready thread="+owner.getLooper().getThread().getName()+" admission=pending_identity");
        } catch(Throwable error) {
            for(int i=family.size()-1;i>=0;i--) family.get(i).unhook(); config=null; throw error;
        }
    }
    private Object rewrite(XposedInterface.Chain chain) throws Throwable {
        Object stock=chain.proceed(); // Native failures propagate unchanged.
        try {
            Armed policy=armed;
            if(chain.getThisObject()!=manager || policy.source!=publication.current()) return stock;
            if(!policy.selection.global() && policy.selection.packages().isEmpty()) return stock;
            if(Looper.myLooper()!=owner.getLooper()) {
                if(throttle.allow("wrong_thread",SystemClock.elapsedRealtime(),5000)) log("stage=gate reason=not_owner_thread");
                return stock;
            }
            Object display=chain.getArg(0),record=chain.getArg(1);
            DisplayRatePolicy.Window window=access.window(display,record,chain.getArg(3));
            String reason=DisplayRatePolicy.reason(policy.selection,window);
            if(!reason.equals("requested")) {
                if(throttle.allow(window.pkg()+":"+reason,SystemClock.elapsedRealtime(),5000))
                    log("stage=gate pkg="+window.pkg()+" uid="+window.uid()+" reason="+reason
                            +" type="+window.type()+" focus="+window.focused()+" applicationMode="+window.applicationMode()
                            +" display="+window.displayId()+" screenOn="+window.screenOn()+" supported144="+window.supports144());
                return stock;
            }
            Object request=stock;
            if(request==null) request=access.constructor.newInstance(display,record,null,chain.getArg(2),null);
            int before=access.number(request,"reqFps"),priority=access.number(request,"reqPriority");
            Object beforeReason=access.get(request,"reqReason");
            // Recheck after reflective work in case a package invalidation raced this callback.
            if(policy.source!=publication.current()) return stock;
            access.request144(request); owned.add(request);
            if(throttle.allow(window.pkg()+":requested",SystemClock.elapsedRealtime(),1000))
                log("stage=request pkg="+window.pkg()+" uid="+window.uid()
                        +" gamePid="+access.number(access.get(record,"client"),"mPid")+" oldFps="+before+" finalFps=144"
                        +" oldReason="+beforeReason+" reason=reqFps oldPriority="+priority
                        +" finalPriority="+access.number(request,"reqPriority")+" synthesized="+(stock==null)
                        +" revision="+policy.source.snapshot.revision);
            return request;
        } catch(Throwable error) { error("request_closed",error); return stock; }
    }
    private Object cancelled(XposedInterface.Chain chain) throws Throwable {
        Object result=chain.proceed();
        if(owned.remove(chain.getThisObject())) try {
            Object record=access.get(chain.getThisObject(),"owner");
            log("stage=request_released pkg="+access.get(record,"pkgName")+" via=oem_cancel");
        } catch(Throwable failure) { error("release_trace",failure); }
        return result;
    }
    private Object beforeApply(XposedInterface.Chain chain) throws Throwable {
        // Recheck an owned request before the OEM same-window cache can retain it across mode changes.
        if(chain.getThisObject()==manager && (Integer)chain.getArg(0)==0 && Looper.myLooper()==owner.getLooper()) try {
            Object display=access.display(),current=display==null?null:access.current(display);
            if(current!=null && owned.contains(current)) {
                Armed policy=armed;
                boolean keep=false;
                try {
                    keep=policy.source==publication.current() && DisplayRatePolicy.reason(policy.selection,
                            access.window(display,access.get(current,"owner"),access.focus(display))).equals("requested");
                } catch(Throwable invalidWindow) { error("revalidate_closed",invalidWindow); }
                if(!keep) { access.cancelCurrent(display); owned.remove(current); log("stage=withdraw via=window_revalidation"); }
            }
        } catch(Throwable error) { error("window_revalidation",error); }
        return chain.proceed();
    }
    @Override public void apply(BackendPolicy next,long token,LongSupplier epoch) {
        if(publication.publish(next,token,epoch)) refresh();
    }
    @Override public void invalidate(String pkg) {
        publication.invalidate(pkg);
        Handler handler=owner;
        if(handler!=null) handler.post(this::refresh);
    }
    private void refresh() {
        BackendPolicy live=publication.current();
        Armed previous=armed,next=new Armed(live,DisplayRatePolicy.select(live)); armed=next;
        log("stage=config revision="+live.snapshot.revision+" verified="+live.verified
                +" global="+next.selection.global()+" packages="+next.selection.packages().keySet());
        if(previous.selection.equals(next.selection)) {
            if(ThermalRatePolicy.changed(previous.source,next.source)) try { access.updateConfig(); }
            catch(Throwable failure) { error("thermal_arbitration_refresh",failure); }
            return;
        }
        if(DisplayRatePolicy.withdraws(previous.selection,next.selection))
            withdrawalTraceUntil=SystemClock.elapsedRealtime()+15_000;
        try {
            Object display=access.display();
            if(display==null) return;
            Object current=access.current(display);
            if(current==null) return;
            boolean wasOwned=owned.contains(current);
            // Revocation must not depend on metadata of a window/process that is already closing.
            boolean nowSelected=!wasOwned && DisplayRatePolicy.reason(next.selection,
                    access.window(display,access.get(current,"owner"),access.focus(display))).equals("requested");
            if(!wasOwned && !nowSelected) return;
            // Defeat OEM cached-request early return using its own cancellation/rebuild lifecycle.
            access.cancelCurrent(display);
            owned.remove(current);
            RrmAccess.call(access.applyRequest,manager,0);
            log("stage=rebuild oldOwned="+wasOwned+" nowSelected="+nowSelected
                    +" revision="+live.snapshot.revision);
        } catch(Throwable error) { error("rebuild_failed",error); }
        finally {
            try { access.updateConfig(); }
            catch(Throwable error) { error("arbitration_refresh_failed",error); }
        }
    }
    private void log(String message) {
        HookLog.write(module,"system","feature=refresh144 moduleVersion="+BuildConfig.VERSION_NAME+" "+message,null);
    }
    private void error(String stage,Throwable error) {
        if(throttle.allow("error:"+stage,SystemClock.elapsedRealtime(),5000))
            HookLog.write(module,"system","feature=refresh144 stage="+stage,error);
    }
}
