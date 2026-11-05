package com.yuka.gamecube.tools.hooks.backend;

import android.os.Binder;
import android.os.Handler;
import android.os.SystemClock;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import com.yuka.gamecube.tools.BuildConfig;
import com.yuka.gamecube.tools.core.DiagnosticThrottle;
import com.yuka.gamecube.tools.hooks.HookLog;
import java.lang.reflect.Method;
import java.util.ArrayList;

/** Original-first system adapter. Admission does not imply native rendering compatibility. */
public final class VsrBackendHooks implements BackendConfig.Sink {
    private final XposedModule module;
    private final BackendAccess access;
    private final Object lock=new Object();
    private final BackendLease leases=new BackendLease();
    private final NativeCallGate nativeGate=new NativeCallGate();
    private final ArrayList<XposedInterface.HookHandle> handles=new ArrayList<>();
    private final DiagnosticThrottle logs=new DiagnosticThrottle(128);
    private final ThreadLocal<Attempt> attempt=new ThreadLocal<>();
    private final BackendPolicy.Publication state=new BackendPolicy.Publication();
    private volatile Object controller;
    private Handler owner;
    private BackendConfig config;
    private boolean sw,pp,ppOnly;
    private static final class Attempt { String extended; }

    private VsrBackendHooks(XposedModule module,ClassLoader loader) throws Throwable {
        this.module=module; access=new BackendAccess(loader,android.os.Build.VERSION.SDK_INT);
    }
    public static VsrBackendHooks install(XposedModule module,ClassLoader loader) throws Throwable {
        VsrBackendHooks adapter=new VsrBackendHooks(module,loader);
        adapter.handles.add(module.hook(adapter.access.ready)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain-> {
                    Object result=chain.proceed(); // OEM owns class initialization and systemReady.
                    try { adapter.ready(chain.getThisObject()); }
                    catch(Throwable error) { adapter.error("initialization_closed",error); }
                    return result;
                }));
        adapter.log("await native systemReady loader="+loader.getClass().getName()); return adapter;
    }
    private synchronized void ready(Object receiver) throws Throwable {
        if(config!=null) { config.request(); return; }
        var context=access.context(receiver); Handler handler=access.handler(receiver);
        if(context==null || handler==null) throw new IllegalStateException("Native context/handler unavailable");
        sw=access.sw(); pp=access.pp(); ppOnly=access.ppOnly(); owner=handler; controller=receiver;
        int start=handles.size();
        try {
            handles.add(module.hook(access.predicate).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::predicate));
            handles.add(module.hook(access.update).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(this::update));
            config=new BackendConfig(module,context,owner,this,this::log,this::error); config.request();
            try { SystemVsrTrace.install(module,access.get("mService",receiver),
                    pkg->state.current().snapshot.apps.containsKey(pkg),uid->state.current().selectedCaller(uid),
                    ()->state.current().snapshot.revision>0,this::log,this::error); }
            catch(Throwable failure) { error("diagnostics_unavailable",failure); }
            log("native ready SW="+sw+" PP="+pp+" ppOnly="+ppOnly+" owner_thread="+owner.getLooper().getThread().getName()
                    +"; admission awaits identity/config");
            log("native capabilities "+access.capabilities());
        } catch(Throwable error) {
            while(handles.size()>start) handles.remove(handles.size()-1).unhook();
            controller=null; throw error;
        }
    }
    private Object predicate(XposedInterface.Chain chain) throws Throwable {
        Object stock=chain.proceed(); // Preserve native true and exceptions exactly.
        if(chain.getThisObject()!=controller) return stock;
        String pkg=(String)chain.getArg(0);
        try {
            boolean selected=state.current().snapshot.apps.containsKey(pkg);
            if(Boolean.TRUE.equals(stock)) { if(selected) log("predicate pkg="+pkg+" reason=native_true"); return stock; }
            if(!nativeGate.armed() || !ppOnly || nativeGameWatch(pkg)) {
                if(selected) log("predicate pkg="+pkg+" reason=native_route gate_armed="+nativeGate.armed()+" ppOnly="+ppOnly);
                return stock;
            }
            boolean disabled=access.disabled(controller,pkg);
            int uid=Binder.getCallingUid();
            boolean local=Binder.getCallingPid()==android.os.Process.myPid() && uid==android.os.Process.myUid();
            int user=local?access.user(controller):-1;
            String reason;
            synchronized(lock) {
                reason=state.current().admissionReason(pkg,sw,pp,disabled,uid,local,user);
                if(reason.equals("admitted")) { Attempt active=attempt.get(); if(active!=null) active.extended=pkg; }
            }
            if(selected) log("predicate pkg="+pkg+" reason="+reason+" revision="+state.current().snapshot.revision
                    +" caller_uid="+uid+" caller_pid="+Binder.getCallingPid()+" local="+local+" user="+user);
            return reason.equals("admitted")?true:stock;
        } catch(Throwable error) { error("admission_closed",error); return stock; }
    }
    private Object update(XposedInterface.Chain chain) throws Throwable {
        if(chain.getThisObject()!=controller) return chain.proceed();
        try { return nativeGate.run(()->observedUpdate(chain)); }
        finally { if(!nativeGate.armed() && ppOnly && config!=null) config.request(); }
    }
    private Object observedUpdate(XposedInterface.Chain chain) throws Throwable {
        if(!nativeGate.armed()) return chain.proceed();
        int[] values=(int[])chain.getArg(1);
        int type=(Integer)chain.getArg(0),requested=values!=null && values.length==1?values[0]:-1;
        boolean enable=type==9 && requested>0;
        Attempt previous=attempt.get(),active=new Attempt(); long call;
        synchronized(lock) { call=leases.begin(); }
        attempt.set(active); boolean success=false;
        try {
            log("stage=native_update_begin call="+call+" type="+type+" request="+requested
                    +" caller_uid="+Binder.getCallingUid()+" caller_pid="+Binder.getCallingPid());
            // Pinned PP-only native path posts callbacks; it never waits for client IPC.
            Object result=chain.proceed();
            success=Boolean.TRUE.equals(result); return result;
        } finally {
            attempt.set(previous);
            try {
                BackendLease.Session actual=access.session(controller);
                synchronized(lock) { leases.complete(call,success,enable,active.extended,actual); }
                if(active.extended!=null || state.current().authorized(actual.pkg()))
                    log("stage=native_update_end call="+call+" type="+type+" request="+requested+" result="+success
                            +" extension="+active.extended+" actual_pkg="+actual.pkg()+" user="+actual.user()
                            +" pp="+actual.pp()+" competing="+actual.competing()+" render=unverified");
                scheduleCleanup();
            } catch(Throwable error) { error("native_observation_unavailable",error); }
        }
    }
    @Override public void apply(BackendPolicy next,long token,java.util.function.LongSupplier epoch) {
        if(next.verified && ppOnly && !nativeGate.armIfIdle()) {
            log("activation deferred until earlier native call returns"); return;
        }
        if(!state.publish(next,token,epoch)) { log("policy discarded stale_epoch="+token+" current_epoch="+epoch.getAsLong()); return; }
        log("policy revision="+next.snapshot.revision+" identity="+next.verified+" enabled="+next.snapshot.enabled
                +" selected="+next.snapshot.apps.keySet()+" epoch="+token+" native_sw="+sw+" native_pp="+pp+" pp_only="+ppOnly); scheduleCleanup();
    }
    @Override public void invalidate(String pkg) {
        boolean relevant=state.current().snapshot.apps.containsKey(pkg)
                || com.yuka.gamecube.tools.core.DevicePolicy.TARGET.equals(pkg);
        state.invalidate(pkg);
        if(relevant) log("package invalidation pkg="+pkg);
        scheduleCleanup();
    }
    private void scheduleCleanup() {
        BackendLease.Token token;
        synchronized(lock) {
            token=leases.current();
            if(token==null || state.current().authorized(token.session().pkg())) return;
        }
        owner.post(()->cleanup(token));
    }
    private void cleanup(BackendLease.Token token) {
        try {
            nativeGate.run(()-> { cleanupNative(token); return null; });
        } catch(Throwable error) { error("owned_PP_cleanup_pending",error); }
    }
    private void cleanupNative(BackendLease.Token token) throws Throwable {
            BackendLease.Session actual=access.session(controller);
            boolean stock=Boolean.TRUE.equals(original(access.predicate,controller,actual.pkg())) || nativeGameWatch(actual.pkg());
            synchronized(lock) {
                if(state.current().authorized(token.session().pkg()) || !leases.canClose(token,actual,stock)) return;
            }
            // Same gate as every native update makes final ownership check and OFF indivisible.
            Object result=original(access.update,controller,9,new int[]{0},1,false);
            BackendLease.Session after=access.session(controller);
            synchronized(lock) {
                if(leases.current()==token && Boolean.TRUE.equals(result)) {
                    leases.begin(); leases.complete(true,false,null,after);
                }
            }
            log("owned PP cleanup pkg="+token.session().pkg()+" result="+result);
    }
    private boolean nativeGameWatch(String pkg) throws Throwable {
        return Boolean.TRUE.equals(original(access.alternative,controller,pkg));
    }
    private Object original(Method method,Object receiver,Object...args) throws Throwable {
        return module.getInvoker(method).setType(XposedInterface.Invoker.Type.ORIGIN).invoke(receiver,args);
    }
    private void log(String message) {
        if(!logs.allow(message,SystemClock.elapsedRealtime(),10000)) return;
        String line="backendVSR module="+BuildConfig.VERSION_NAME+"/"+BuildConfig.VERSION_CODE+" "+message;
        HookLog.write(module,"system_server",line,null);
    }
    private void error(String stage,Throwable error) {
        if(logs.allow("error:"+stage,SystemClock.elapsedRealtime(),10000))
            HookLog.write(module,"system_server","backendVSR stage="+stage,error);
    }
}
