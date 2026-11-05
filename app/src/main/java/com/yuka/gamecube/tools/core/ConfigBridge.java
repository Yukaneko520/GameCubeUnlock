package com.yuka.gamecube.tools.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.HookedTarget;
import com.yuka.gamecube.tools.diagnostics.AppDiagnostics;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ConfigBridge implements AutoCloseable {
    private final Context context;
    private final SharedPreferences local;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable connectionListener=this::refresh;
    private volatile Runnable listener;
    private volatile ConfigSnapshot desired;
    private volatile String status="Saved locally; waiting for LSPosed";
    private volatile String compatibility="Verifying device and APK...";
    private volatile boolean supported,closed;
    private volatile java.util.Set<String> nativeScopes=java.util.Set.of();
    private volatile String scopeError="";
    private volatile FrameworkState frameworkState=FrameworkState.disconnected();
    public ConfigBridge(Context context) {
        this.context=context.getApplicationContext();
        local=this.context.getSharedPreferences("desired_config",Context.MODE_PRIVATE);
        try { desired=ConfigSnapshot.parse(local.getString(ConfigSnapshot.KEY,ConfigSnapshot.defaults().toJson())); }
        catch(Exception e) { desired=ConfigSnapshot.defaults(); status="Configuration error; using disabled defaults"; }
        FrameworkConnection.start(); FrameworkConnection.add(connectionListener); refresh();
    }
    public ConfigSnapshot current() { return desired; }
    public String status() { return status; }
    public String compatibility() { return compatibility; }
    public boolean supported() { return supported; }
    public java.util.Set<String> nativeScopes() { return nativeScopes; }
    public String scopeError() { return scopeError; }
    public FrameworkState frameworkState() { return frameworkState; }
    /** Only called by the manager's explicit scope button; framework owns the approval dialog. */
    public void requestNativeScope(String pkg) {
        if(!supported || !com.yuka.gamecube.tools.compat.RomFeatures.available(android.os.Build.VERSION.SDK_INT,"nativeSr")
                || !ConfigSnapshot.validPackage(pkg) || pkg.equals(DevicePolicy.TARGET)
                || pkg.equals("android") || pkg.equals("com.android.systemui")) return;
        io.execute(()-> {
            try {
                var info=context.getPackageManager().getApplicationInfo(pkg,0);
                if(info.uid<10000 || info.uid>=99000) throw new IllegalArgumentException("Primary app required");
                XposedService service=FrameworkConnection.service;
                if(service==null) throw new IllegalStateException("LSPosed disconnected");
                scopeError="";
                service.requestScope(java.util.List.of(pkg),new XposedService.OnScopeEventListener() {
                    @Override public void onScopeRequestApproved(java.util.List<String> approved) {
                        AppDiagnostics.log("native_scope","approved="+approved); refresh();
                    }
                    @Override public void onScopeRequestFailed(String message) {
                        scopeError=pkg+" · "+message; AppDiagnostics.log("native_scope",scopeError); notifyUi();
                    }
                });
            } catch(Exception error) { scopeError=pkg+" · "+error.getMessage(); AppDiagnostics.error("native_scope",error); notifyUi(); }
        });
    }
    public void setListener(Runnable listener) { this.listener=listener; notifyUi(); }
    private void notifyUi() {
        AppDiagnostics.log("config", "revision="+desired.revision+" identity="+Compatibility.diagnosticReason(compatibility)
                +" supported="+supported+" framework_connected="+(FrameworkConnection.service!=null));
        main.post(()-> { Runnable r=listener; if(!closed && r!=null) r.run(); });
    }
    public synchronized void save(ConfigSnapshot config) {
        if(!supported) throw new IllegalArgumentException("Device or GameCube has not been verified");
        ConfigSnapshot next=config.withRevision(Math.max(System.currentTimeMillis(),desired.revision+1));
        AppDiagnostics.log("config_save", next.toJson());
        desired=next; status="Saving and syncing..."; notifyUi();
        io.execute(()-> {
            persistAndPublish(next);
        });
    }
    public void refresh() {
        if(closed) return;
        io.execute(()-> {
            var service=FrameworkConnection.service;
            var scopes=java.util.Set.<String>of();
            var loaded=new java.util.HashSet<String>();
            String scopeFailure="",targetFailure="";
            if(service!=null) {
                try {
                    scopes=java.util.Set.copyOf(service.getScope());
                    AppDiagnostics.log("framework_scope","configured="+scopes);
                }
                catch(Exception error) { scopeFailure=error.toString(); AppDiagnostics.error("native_scope_read",error); }
                try {
                    var targets=service.getRunningTargets();
                    AppDiagnostics.log("framework_targets","count="+targets.size()+" diagnostic_only=true");
                    for(var target:targets) {
                        AppDiagnostics.log("framework_target","process="+target.getProcessName()
                                +" pid="+target.getPid()+" uid="+target.getUid()+" state="+target.getState()
                                +" loaded_version="+target.getLoadedVersionCode()+" diagnostic_only=true");
                        if(target.getState()==HookedTarget.State.UP_TO_DATE
                                && target.getLoadedVersionCode()==com.yuka.gamecube.tools.BuildConfig.VERSION_CODE)
                            loaded.add(target.getProcessName());
                    }
                } catch(Exception error) { targetFailure=error.toString(); AppDiagnostics.error("framework_targets_read",error); }
            }
            nativeScopes=scopes;
            frameworkState=service==FrameworkConnection.service && service!=null
                    ?new FrameworkState(true,scopes,loaded,scopeFailure,targetFailure):FrameworkState.disconnected();
            compatibility=Compatibility.check(context); supported=compatibility.startsWith("Compatible:");
            if(supported) persistAndPublish(desired); else notifyUi();
        });
    }
    private void persistAndPublish(ConfigSnapshot next) {
        try {
            if(!local.edit().putString(ConfigSnapshot.KEY,next.toJson()).commit())
                throw new IllegalStateException("Unable to persist local configuration");
        } catch(Exception error) {
            AppDiagnostics.error("config_persist", error);
            status="Save failed; not sent to LSPosed: "+error.getMessage(); notifyUi(); return;
        }
        publish(next);
    }
    private void publish(ConfigSnapshot next) {
        XposedService service=FrameworkConnection.service;
        if(service==null) {
            AppDiagnostics.log("config_publish","revision="+next.revision+" result=waiting_for_framework");
            status="Saved locally; waiting for LSPosed; enable the module and GameCube scope"; notifyUi(); return;
        }
        try {
            if(service.getApiVersion()<102 || (service.getFrameworkProperties() & XposedService.PROP_CAP_REMOTE)==0)
                throw new IllegalStateException("API102 with Remote Preferences is required");
            if(!service.getRemotePreferences(ConfigSnapshot.GROUP).edit().putString(ConfigSnapshot.KEY,next.toJson()).commit())
                throw new IllegalStateException("Unable to publish framework configuration");
            AppDiagnostics.log("config_publish","revision="+next.revision+" result=sent framework="+service.getFrameworkName()
                    +" version="+service.getFrameworkVersion());
            status="Configuration sent #"+next.revision+" • "+service.getFrameworkName()+" "+service.getFrameworkVersion()
                    +"; applies at the next evaluation or sidebar opening";
        } catch(Exception e) {
            AppDiagnostics.error("config_publish", e);
            status="Sync failed; configuration saved locally: "+e.getMessage();
        }
        notifyUi();
    }
    @Override public void close() { closed=true; listener=null; FrameworkConnection.remove(connectionListener); io.shutdown(); }
}
