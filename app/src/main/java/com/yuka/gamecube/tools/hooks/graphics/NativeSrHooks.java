package com.yuka.gamecube.tools.hooks.graphics;

import android.os.Build;
import android.os.Process;
import io.github.libxposed.api.XposedModule;
import com.yuka.gamecube.tools.BuildConfig;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
import com.yuka.gamecube.tools.core.DevicePolicy;
import com.yuka.gamecube.tools.hooks.HookLog;
import com.yuka.gamecube.tools.hooks.backend.BackendPreferences;
import java.util.concurrent.*;

/** Game-process-only native tuning. No root, engine loading, activation or system settings writes. */
public final class NativeSrHooks {
    private final XposedModule module;
    private final String pkg;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
    private final BackendPreferences preferences;
    private ConfigSnapshot snapshot=ConfigSnapshot.defaults();
    private long highest=-1;
    private boolean loaded;
    private boolean identityVerified;
    private ScheduledFuture<?> poll;
    private String verifiedPath="",lastState="";
    private NativeSrHooks(XposedModule module,String pkg) {
        this.module=module; this.pkg=pkg;
        preferences=new BackendPreferences(()->module.getRemotePreferences(ConfigSnapshot.GROUP),worker,
                ()->worker.execute(this::reload),this::log);
    }
    public static NativeSrHooks install(XposedModule module,String pkg,String process) {
        if(!ConfigSnapshot.validPackage(pkg) || !pkg.equals(process) || Process.myUid()<10000 || Process.myUid()>=99000
                || pkg.equals("android") || pkg.equals(DevicePolicy.TARGET) || pkg.equals(BuildConfig.APPLICATION_ID)) return null;
        if((Build.VERSION.SDK_INT!=35 && Build.VERSION.SDK_INT!=36) || !Process.is64Bit()) {
            HookLog.write(module,process,"feature=sr_tuning stage=unavailable reason=unsupported_native_abi_or_sdk sdk="+Build.VERSION.SDK_INT,null);
            return null;
        }
        NativeSrHooks hooks=new NativeSrHooks(module,pkg);
        // The scoped app has not started yet. Give the worker a bounded bootstrap window before its renderer loads.
        var bootstrap=hooks.worker.submit(hooks::reload);
        try { bootstrap.get(1500,TimeUnit.MILLISECONDS); }
        catch(Exception pending) { hooks.log("stage=bootstrap_pending renderer_may_need_restart=true"); }
        return hooks;
    }
    private void reload() {
        try {
            Object raw=preferences.read();
            try {
                if(!(raw instanceof String text)) throw new IllegalArgumentException("Missing configuration");
                ConfigSnapshot next=ConfigSnapshot.parse(text);
                if(next.revision>=highest) { snapshot=next; highest=next.revision; }
            } catch(Exception invalid) { snapshot=ConfigSnapshot.defaults(); error("invalid_config_closed",invalid); }
        } catch(BackendPreferences.ReconnectPending pending) { /* Retain the last valid policy during reconnect. */ }
        catch(Throwable unavailable) { error("preferences_unavailable",unavailable); preferences.retryLater(); }
        if(poll!=null) { poll.cancel(false); poll=null; }
        apply();
    }
    private boolean wanted() {
        return snapshot.allows(pkg,"sr") && snapshot.rule(pkg).tuning.srCustom();
    }
    private boolean device() throws Exception {
        if(identityVerified) return true;
        var get=Class.forName("android.os.SystemProperties").getMethod("get",String.class);
        String product=(String)get.invoke(null,"ro.vivo.product.model"),display=(String)get.invoke(null,"persist.vivo.displayic.ver");
        boolean allowed=DevicePolicy.deviceAllowed(Build.MODEL,product,Build.VERSION.SDK_INT,display);
        if(!allowed) log("stage=identity_rejected model="+Build.MODEL+" product="+product+" sdk="+Build.VERSION.SDK_INT+" displayic="+display);
        if(!allowed) return false;
        var baseline=com.yuka.gamecube.tools.compat.TargetProfiles.device(Build.MODEL,product,Build.VERSION.SDK_INT,display);
        // The game's package visibility can hide GameCube/GameWatch. Its renderer has separate full-file and mapped-ELF checks.
        identityVerified=true; log("stage=device_verified baseline="+baseline.id()+" engine_identity=pending"); return true;
    }
    private void apply() {
        try {
            boolean wanted=wanted() && device();
            if(!wanted) {
                if(loaded) configure(false);
                state("inactive revision="+snapshot.revision); return;
            }
            if(!loaded) {
                NativeSrBridge.load(); loaded=true;
                NativeSrBridge.metadata(pkg,BuildConfig.VERSION_NAME+"/"+BuildConfig.VERSION_CODE);
                if(!NativeSrBridge.selectTarget(Build.VERSION.SDK_INT)) throw new IllegalStateException("Native baseline selection rejected");
                log("stage=library_loaded native_api_status="+NativeSrBridge.status());
            }
            if(NativeSrBridge.status()==-1) { configure(false); state("native_api_unavailable_restart_required"); return; }
            configure(true); // Native code stays passthrough until the mapped immutable segments have been verified.
            String path=NativeSrBridge.candidate();
            if(path==null) { state("waiting_for_oem_engine"); schedule(); return; }
            if(!path.equals(verifiedPath)) {
                String sha=NativeLibraryDigest.sha256(path);
                if(!NativeLibraryDigest.expected(Build.VERSION.SDK_INT).equals(sha)) { configure(false); state("so_hash_rejected path="+path+" sha="+sha); return; }
                int result=NativeSrBridge.install(path,sha);
                log("stage=native_install path="+path+" result="+result+" sha256="+sha);
                if(result!=1) {
                    configure(false);
                    if(installPending(result)) { state("native_install_pending"); schedule(); }
                    else state("native_unavailable status="+result);
                    return;
                }
                verifiedPath=path;
            }
            if(NativeSrBridge.status()!=1) { configure(false); state("native_unavailable status="+NativeSrBridge.status()); return; }
            configure(true);
            state("configured sdk="+Build.VERSION.SDK_INT+" revision="+snapshot.revision+
                    " actual_render_ack=see_native_renderer_log restart_required=true");
        } catch(Throwable error) {
            try { if(loaded) configure(false); } catch(Throwable ignored) { }
            error("native_tuning_closed",error);
        }
    }
    private void configure(boolean enabled) {
        var tune=snapshot.rule(pkg).tuning;
        NativeSrBridge.configure(enabled,tune.algorithm(),tune.targetShortSide(),tune.sourceSize(),
                tune.sharpness(),tune.darkLimit(),tune.whiteLimit(),snapshot.revision);
    }
    private void schedule() { poll=worker.schedule(()-> { poll=null; apply(); },1,TimeUnit.SECONDS); }
    static boolean installPending(int result) { return result==0 || result==2; }
    private void state(String text) { if(!text.equals(lastState)) { lastState=text; log("stage="+text); } }
    private void log(String text) { HookLog.write(module,pkg,"feature=sr_tuning "+text,null); }
    private void error(String stage,Throwable error) { HookLog.write(module,pkg,"feature=sr_tuning stage="+stage,error); }
}
