package com.yuka.gamecube.tools.hooks;

import android.app.Application;
import android.content.Context;
import android.util.Log;
import io.github.libxposed.api.XposedModule;
import com.yuka.gamecube.tools.BuildConfig;
import com.yuka.gamecube.tools.core.Compatibility;
import com.yuka.gamecube.tools.core.DevicePolicy;
import com.yuka.gamecube.tools.hooks.general.GeneralHooks;
import com.yuka.gamecube.tools.hooks.profiles.ProfileHooks;
import com.yuka.gamecube.tools.hooks.overlay.OverlayHooks;
import com.yuka.gamecube.tools.hooks.diagnostics.NativeDiagnostics;
import com.yuka.gamecube.tools.hooks.backend.VsrBackendHooks;
import com.yuka.gamecube.tools.hooks.backend.RrmDisplayHooks;
import com.yuka.gamecube.tools.hooks.backend.ThermalWatchHooks;
import com.yuka.gamecube.tools.hooks.backend.SuperTouchHooks;
import com.yuka.gamecube.tools.hooks.backend.SuperGyroHooks;
import com.yuka.gamecube.tools.hooks.thermal.CubeThermalHooks;
import com.yuka.gamecube.tools.hooks.graphics.NativeSrHooks;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ModuleEntry extends XposedModule {
    private String process="";
    private final AtomicBoolean initialized=new AtomicBoolean();
    private HookRuntime runtime;
    private VsrBackendHooks backend;
    private RrmDisplayHooks displayBackend;
    private NativeSrHooks nativeSr;
    private ThermalWatchHooks thermalWatch;
    private SuperTouchHooks superTouch;
    private SuperGyroHooks superGyro;
    private com.yuka.gamecube.tools.hooks.backend.SystemVisualHooks visualBackend;
    private com.yuka.gamecube.tools.hooks.backend.FlaHooks fla;
    @Override public void onSystemServerStarting(SystemServerStartingParam param) {
        HookLog.startSystemJournal();
        entryLog("system_entry api="+getApiVersion()+" capabilities="+getFrameworkProperties());
        if(android.os.Build.VERSION.SDK_INT==34)
            entryLog("stage=rom_features baseline=os4-pd2352-w10 unavailable=fps,superGyro,sr,nativeSr");
        if(getApiVersion()<102 || (getFrameworkProperties() & PROP_CAP_REMOTE)==0) {
            entryLog("system_entry_blocked reason=remote_api_unavailable"); return;
        }
        try { if(android.os.Build.VERSION.SDK_INT==36)
            fla=com.yuka.gamecube.tools.hooks.backend.FlaHooks.install(this,param.getClassLoader()); }
        catch(Throwable error) { HookLog.write(this,"system","feature=fla stage=preflight_closed",error); }
        try { if(com.yuka.gamecube.tools.compat.RomFeatures.available(android.os.Build.VERSION.SDK_INT,"sr"))
            backend=VsrBackendHooks.install(this,param.getClassLoader()); }
        catch(Throwable error) {
            Log.e("GameCubeUnlock","System VSR hook skipped moduleVersion="+BuildConfig.VERSION_NAME,error);
            log(Log.ERROR,"GameCubeUnlock","System VSR hook skipped",error);
        }
        try { displayBackend=RrmDisplayHooks.install(this,param.getClassLoader()); }
        catch(Throwable error) { HookLog.write(this,"system","feature=refresh144 stage=preflight_closed",error); }
        try { if(com.yuka.gamecube.tools.compat.RomFeatures.available(android.os.Build.VERSION.SDK_INT,"superGyro"))
            superGyro=SuperGyroHooks.install(this,param.getClassLoader()); }
        catch(Throwable error) { HookLog.write(this,"system","feature=superGyro stage=preflight_closed",error); }
        try { visualBackend=com.yuka.gamecube.tools.hooks.backend.SystemVisualHooks.install(this,param.getClassLoader()); }
        catch(Throwable error) { HookLog.write(this,"system","feature=visual stage=color_preflight_closed",error); }
    }
    @Override public void onModuleLoaded(ModuleLoadedParam param) {
        process=param.getProcessName();
        entryLog("module_loaded process="+process+" system="+param.isSystemServer()+" api="+getApiVersion());
        if(!param.isSystemServer() && getApiVersion()>=102 && (getFrameworkProperties() & PROP_CAP_REMOTE)!=0)
            try { nativeSr=NativeSrHooks.install(this,process,process); }
            catch(Throwable error) { HookLog.write(this,process,"feature=sr_tuning stage=early_entry_closed",error); }
    }
    private void entryLog(String message) {
        String line=message+" moduleVersion="+BuildConfig.VERSION_NAME+" moduleCode="+BuildConfig.VERSION_CODE;
        HookLog.write(this,process.isEmpty()?"entry":process,line,null);
    }
    @Override public void onPackageReady(PackageReadyParam param) {
        if(!param.isFirstPackage()) return;
        if(getApiVersion()<102 || (getFrameworkProperties() & PROP_CAP_REMOTE)==0) {
            Log.w("GameCubeUnlock","entry_blocked process="+process+" reason=remote_api_unavailable"); return;
        }
        boolean watch="com.vivo.gamewatch".equals(param.getPackageName());
        if(!watch && !DevicePolicy.TARGET.equals(param.getPackageName())) {
            try { if(nativeSr==null) nativeSr=NativeSrHooks.install(this,param.getPackageName(),process); }
            catch(Throwable error) { HookLog.write(this,process,"feature=sr_tuning stage=entry_closed",error); }
            return;
        }
        if(!watch && !DevicePolicy.processAllowed(process)) return;
        try {
            ClassLoader loader=param.getClassLoader();
            hook(Application.class.getDeclaredMethod("attach",Context.class)).intercept(chain-> {
                Object result=chain.proceed();
                if(!initialized.compareAndSet(false,true)) return result;
                Context context=(Context)chain.getArg(0);
                if(watch) {
                    HookLog.startJournal(context::getFilesDir,"gamecube-tools-gamewatch.log");
                    // Install inert descriptors now; the backend worker verifies full hashes before admission.
                    try { thermalWatch=ThermalWatchHooks.install(this,loader,context,
                            com.yuka.gamecube.tools.compat.TargetProfiles.descriptors(android.os.Build.VERSION.SDK_INT)); }
                    catch(Throwable error) { HookLog.write(this,process,"feature=thermal stage=watch_install_closed",error); }
                    // Run after handleBindApplication finishes OEM Application.onCreate and initializes its helpers.
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{
                        try { superTouch=SuperTouchHooks.install(this,loader,context,android.os.Build.VERSION.SDK_INT); }
                        catch(Throwable error) { HookLog.write(this,process,"feature=superTouch stage=watch_install_closed",error); }
                    });
                    return result;
                }
                var verified=Compatibility.verify(context);
                String check=verified.message();
                String identityReason=Compatibility.diagnosticReason(check);
                Log.i("GameCubeUnlock","identity process="+process+" result="+identityReason);
                if(!verified.allowed()) { log(Log.WARN,"GameCubeUnlock","identity="+verified.reason()); return result; }
                try {
                    runtime=new HookRuntime(this,loader,context,verified.baseline());
                    HookRuntime r=runtime;
                    r.installFamily("general",()->GeneralHooks.install(r));
                    NativeDiagnostics.install(r,process.endsWith(":assistantui"));
                    if(process.endsWith(":daemon")) r.installFamily("overlay enrollment",
                            ()->com.yuka.gamecube.tools.hooks.overlay.OverlayEnrollment.install(r));
                    if(process.endsWith(":assistantui")) {
                        r.installFamily("demonstration",()->com.yuka.gamecube.tools.hooks.demo.DemonstrationHooks.install(r));
                        r.installFamily("visual tuning",()->com.yuka.gamecube.tools.hooks.visual.VisualHooks.install(r));
                        r.installFamily("gyro sampling isolation",()->com.yuka.gamecube.tools.hooks.general.GyroSamplingHooks.install(r));
                        r.installFamily("profiles",()->ProfileHooks.install(r));
                        r.installFamily("overlay",()->OverlayHooks.install(r));
                        r.installFamily("thermal",()->CubeThermalHooks.install(r));
                        r.installFamily("feature thermal",()->com.yuka.gamecube.tools.hooks.thermal.FeatureThermalHooks.install(r));
                    }
                    r.reload();
                } catch(Throwable e) {
                    Log.e("GameCubeUnlock","Initialization skipped process="+process,e);
                    log(Log.ERROR,"GameCubeUnlock","Initialization skipped",e);
                }
                return result;
            });
        } catch(Throwable e) {
            Log.e("GameCubeUnlock","Entrypoint skipped process="+process,e);
            log(Log.ERROR,"GameCubeUnlock","Entrypoint skipped",e);
        }
    }
}
