package com.yuka.gamecube.tools.hooks.backend;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import com.yuka.gamecube.tools.hooks.HookLog;
import java.util.ArrayList;
import java.util.function.LongSupplier;

/** Experimental global admission gate. Applying OFF cannot clear an already retained QoS ceiling. */
public final class FlaHooks implements BackendConfig.Sink {
    private final XposedModule module;
    private final FlaAccess access;
    private final FlaOwnership ownership;
    private final FlaAdmission admission=new FlaAdmission();
    private final BackendPolicy.Publication publication=new BackendPolicy.Publication();
    private final ArrayList<XposedInterface.HookHandle> handles=new ArrayList<>();
    private volatile BackendConfig config;
    private Handler owner;
    private boolean kernelVerified;
    private boolean ownershipLoaded;

    private FlaHooks(XposedModule module,ClassLoader loader) throws ReflectiveOperationException {
        this.module=module;access=new FlaAccess(loader);
        ownership=new FlaOwnership(java.nio.file.Path.of("/data/system/gamecube-tools/fla-owner"),access::bootId);
    }
    public static FlaHooks install(XposedModule module,ClassLoader loader) throws Throwable {
        if(android.os.Build.VERSION.SDK_INT!=36) throw new IllegalArgumentException("FLA experiment is OS6-only");
        FlaHooks adapter=new FlaHooks(module,loader);
        try {
            adapter.handles.add(module.hook(adapter.access.initialize)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                        Object result=chain.proceed();
                        try { adapter.ready((Context)chain.getArg(0)); }
                        catch(Throwable error) { adapter.error("ready_failed",error); }
                        return result;
                    }));
            adapter.handles.add(module.hook(adapter.access.bootCompleted)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                        Object result=chain.proceed();
                        try {
                            BackendConfig current=adapter.config;
                            if(current!=null) current.request();
                        } catch(Throwable error) { adapter.error("boot_recheck_failed",error); }
                        return result;
                    }));
        } catch(Throwable failure) {
            for(var handle:adapter.handles) handle.unhook();
            throw failure;
        }
        adapter.log("stage=await_rms_initialize default=off clean_kernel_reboot_required=true");
        return adapter;
    }
    private synchronized void ready(Context context) {
        if(config!=null) { config.request(); return; }
        if(context==null) throw new IllegalStateException("RMS context unavailable");
        HandlerThread thread=new HandlerThread("GameCube-FLA");thread.start();
        owner=new Handler(thread.getLooper());
        // Global policy needs dependency identity, not per-game package/UID queries.
        config=new BackendConfig(module,context,owner,this,pkg->false,info->false,this::log,this::error);
        config.request();
        log("stage=ready owner_thread="+thread.getName()+" admission=pending_identity_and_config");
    }
    @Override public void apply(BackendPolicy next,long token,LongSupplier epoch) {
        if(!publication.publish(next,token,epoch)) { log("stage=discarded_stale_policy token="+token);return; }
        log("stage=config revision="+next.snapshot.revision+" verified="+next.verified
                +" master="+next.snapshot.enabled+" requested="+next.snapshot.disableFlaGlobal);
        reconcile(next,0,true);
    }
    @Override public void invalidate(String pkg) {
        publication.invalidate(pkg);
        Handler current=owner;
        if(current!=null) current.post(()->reconcile(publication.current(),0,false));
    }
    private void reconcile(BackendPolicy source,int attempt,boolean checkLater) {
        if(source!=publication.current()) return;
        boolean disable=FlaAdmission.requested(source);
        try {
            if(!ownershipLoaded) {
                boolean resumed=ownership.resume();
                if(resumed) admission.resumeOwned();
                ownershipLoaded=true;log("stage=ownership_initialized resumed="+resumed);
            }
            if(disable && !kernelVerified) {
                access.verifyKernel();kernelVerified=true;
                log("stage=kernel_verified source=uname release="+FlaAccess.KERNEL);
            }
            var result=admission.reconcile(disable,new FlaAdmission.Io() {
                public String read() throws Throwable { return access.read(); }
                public int write(String value) throws Throwable {
                    if(source!=publication.current()) throw new IllegalStateException("FLA policy superseded before write");
                    if(value.equals("0")) ownership.acquire();
                    if(source!=publication.current()) throw new IllegalStateException("FLA policy superseded after ownership save");
                    log("stage=write_begin revision="+source.snapshot.revision+" requested="+value+" path="+FlaAccess.FLAG);
                    return access.write(value);
                }
            });
            if(!admission.owned()) ownership.clear();
            log("stage="+result.stage()+" revision="+source.snapshot.revision+" disable="+disable
                    +" before="+result.before()+" after="+result.after()+" native_result="+result.nativeResult()
                    +" confirmed="+result.confirmed()+" owned="+result.owned()+" hardware_effect=unverified");
            if(result.nativeResult()!=null || checkLater && disable) access.diagnostics(this::log);
            if(!result.confirmed()) throw new IllegalStateException("FLA native result/readback mismatch");
            if(checkLater && disable) owner.postDelayed(()->reconcile(source,0,false),10000);
        } catch(Throwable failure) {
            error("control_failed revision="+source.snapshot.revision+" attempt="+attempt+" owned="+admission.owned(),failure);
            if(source==publication.current() && attempt<2)
                owner.postDelayed(()->reconcile(source,attempt+1,checkLater),1000L*(attempt+1));
        }
    }
    private void log(String message) { HookLog.write(module,"system_server","feature=fla "+message,null); }
    private void error(String stage,Throwable failure) { HookLog.write(module,"system_server","feature=fla stage="+stage,failure); }
}
