package com.yuka.gamecube.tools.hooks;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
import com.yuka.gamecube.tools.core.DiagnosticThrottle;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

public final class HookRuntime {
    public final com.yuka.gamecube.tools.compat.RomAdapter adapter;
    public final XposedModule module;
    public final ClassLoader loader;
    private final Context context;
    private volatile ConfigSnapshot config=ConfigSnapshot.defaults();
    private final List<XposedInterface.HookHandle> handles=new ArrayList<>();
    private final List<ConfigListener> listeners=new CopyOnWriteArrayList<>();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService updates=Executors.newSingleThreadExecutor();
    private final SharedPreferences prefs;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener=(p,k)-> { if(k==null||ConfigSnapshot.KEY.equals(k)) reload(); };
    private boolean loaded;
    private long highestRevision=-1;
    private final DiagnosticThrottle logs=new DiagnosticThrottle(256);
    private final String process=android.app.Application.getProcessName();
    private final java.util.Map<String,String> families=new java.util.LinkedHashMap<>();

    public HookRuntime(XposedModule module,ClassLoader loader,Context context,
                       com.yuka.gamecube.tools.compat.TargetBaseline baseline) {
        adapter=com.yuka.gamecube.tools.compat.RomAdapter.forBaseline(baseline);
        this.module=module; this.loader=loader; this.context=context;
        HookLog.startJournal(context::getFilesDir,process.endsWith(":assistantui")
                ?"gamecube-tools-assistantui.log":"gamecube-tools-daemon.log");
        prefs=module.getRemotePreferences(ConfigSnapshot.GROUP);
        prefs.registerOnSharedPreferenceChangeListener(listener);
        try {
            config=read(); loaded=true; highestRevision=config.revision;
            log("Initial config revision="+config.revision+" selected="+config.apps.keySet());
            logRules(config);
        } catch(Throwable e) { error("initial_config",e); }
        IntentFilter packages=new IntentFilter(); packages.addAction(Intent.ACTION_PACKAGE_ADDED);
        packages.addAction(Intent.ACTION_PACKAGE_REMOVED); packages.addDataScheme("package");
        context.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context c,Intent i) { reload(); }
        },packages,Context.RECEIVER_NOT_EXPORTED);
        main.postDelayed(new Runnable() {
            @Override public void run() {
                if(config.enabled) status();
                main.postDelayed(this,30000);
            }
        },30000);
    }
    public Context context() { return context; }
    public ConfigSnapshot snapshot() { return config; }
    public Class<?> type(String name) throws ClassNotFoundException { return Class.forName(name,false,loader); }
    public Method method(String owner,String name,Class<?>...args) throws ReflectiveOperationException { return Reflect.method(type(owner),name,args); }
    public void hook(String owner,String name,Class<?> returns,XposedInterface.Hooker callback,Class<?>...args) throws Throwable {
        Method method=method(owner,name,args);
        if(method.getReturnType()!=returns) throw new NoSuchMethodException("Unexpected return type: "+owner+"."+name);
        hook(method,callback);
    }
    public void hook(java.lang.reflect.Executable executable,XposedInterface.Hooker callback) throws Throwable {
        handles.add(module.hook(executable).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(callback));
    }
    public Object original(Method method,Object receiver,Object...args) throws Throwable {
        return module.getInvoker(method).setType(XposedInterface.Invoker.Type.ORIGIN).invoke(receiver,args);
    }
    public String currentPackage() {
        try {
            var api=adapter.owner();
            Object value=Reflect.call(Reflect.staticCall(type(api.type()),api.instance()),api.currentPackage());
            return value instanceof String pkg?pkg:"";
        }
        catch(Throwable ignored) { return ""; }
    }
    public void log(String message) {
        if(logs.allow("message:"+message,android.os.SystemClock.elapsedRealtime(),10000)) write(message);
    }
    public void event(String feature,String target,String stage,String detail) {
        String key=feature+"/"+target+"/"+stage+"/"+config.revision;
        if(logs.allow(key,android.os.SystemClock.elapsedRealtime(),1000))
            write("feature="+feature+" pkg="+target+" revision="+config.revision+" stage="+stage+" "+detail);
    }
    private void write(String message) {
        HookLog.write(module,process,message,null);
    }
    public void error(String stage,Throwable error) {
        if(logs.allow("error:"+stage,android.os.SystemClock.elapsedRealtime(),10000)) {
            HookLog.write(module,process,"stage="+stage+" revision="+config.revision,error);
        }
    }
    public void addListener(ConfigListener listener) { listeners.add(listener); }
    public void deoptimize(String name) throws Throwable {
        int count=0;var types=new java.util.ArrayDeque<Class<?>>();types.add(type(name));
        while(!types.isEmpty()) {
            Class<?> candidate=types.removeFirst();java.util.Collections.addAll(types,candidate.getDeclaredClasses());
            for(var constructor:candidate.getDeclaredConstructors()) if(module.deoptimize(constructor)) count++;
            for(var method:candidate.getDeclaredMethods())
                if(!java.lang.reflect.Modifier.isNative(method.getModifiers()) && !java.lang.reflect.Modifier.isAbstract(method.getModifiers()))
                    if(module.deoptimize(method)) count++;
        }
        log("stage=caller_deopt owner="+name+" count="+count);
    }
    @FunctionalInterface public interface ThrowingRunnable { void run() throws Throwable; }
    public void installFamily(String name,ThrowingRunnable installer) {
        int start=handles.size(), previousListeners=listeners.size();
        try {
            installer.run();families.put(name,"ready");
            log("port baseline="+adapter.baseline().id()+" family="+name+" stage=preflight result=ok");
        }
        catch(Throwable e) {
            families.put(name,"failed:"+e.getClass().getSimpleName()+":"+String.valueOf(e.getMessage()));
            for(int i=handles.size()-1;i>=start;i--) handles.remove(i).unhook();
            while(listeners.size()>previousListeners) listeners.remove(listeners.size()-1);
            error("port baseline="+adapter.baseline().id()+" install_family:"+name,e);
        }
    }
    private ConfigSnapshot read() throws Exception {
        var identity=com.yuka.gamecube.tools.core.Compatibility.verify(context);
        if(!identity.allowed() || !adapter.baseline().equals(identity.baseline()))
            throw new InvalidConfigException(new IllegalStateException("dependency_identity:"+identity.reason()));
        String raw;
        try { raw=prefs.getString(ConfigSnapshot.KEY,null); }
        catch(ClassCastException e) { throw new InvalidConfigException(e); }
        ConfigSnapshot parsed;
        try {
            if(raw==null) throw new IllegalArgumentException("Configuration envelope removed");
            parsed=ConfigSnapshot.parse(raw);
        }
        catch(org.json.JSONException | IllegalArgumentException e) { throw new InvalidConfigException(e); }
        var installed=new java.util.LinkedHashMap<String,com.yuka.gamecube.tools.core.AppRule>();
        for(var e:parsed.apps.entrySet()) {
            if(e.getKey().equals("com.vivo.gamecube") || e.getKey().equals("com.yuka.gamecube.tools")) continue;
            try { context.getPackageManager().getApplicationInfo(e.getKey(),0); installed.put(e.getKey(),e.getValue()); }
            catch(android.content.pm.PackageManager.NameNotFoundException ignored) {}
        }
        ConfigSnapshot effective=com.yuka.gamecube.tools.compat.RomFeatures.effective(new ConfigSnapshot(parsed.enabled,parsed.fpsMode,
                parsed.hideGameCenter,parsed.revision,installed,parsed.force144Global,parsed.demonstrationMode,parsed.disableFlaGlobal),adapter.baseline().sdk());
        return process.endsWith(":assistantui")?com.yuka.gamecube.tools.hooks.demo.DemonstrationPolicy.assistant(effective):effective;
    }
    public void reload() {
        updates.execute(()-> {
            try { ConfigSnapshot next=read(); main.post(()->apply(next,false)); }
            catch(InvalidConfigException error) { error("invalid_config_withdraw",error); main.post(()->apply(ConfigSnapshot.defaults(),true)); }
            catch(Throwable transport) { error("config_transport_retain",transport); }
        });
    }
    private static final class InvalidConfigException extends Exception {
        InvalidConfigException(Throwable cause) { super(cause); }
    }
    private void apply(ConfigSnapshot next,boolean invalid) {
        ConfigSnapshot old=config;
        if(!invalid && (next.revision<highestRevision || loaded && next.revision==old.revision && next.toJson().equals(old.toJson()))) return;
        for(ConfigListener listener:listeners) try { listener.beforeChange(old,next); } catch(Throwable e) { error("config_cleanup",e); }
        config=next; loaded=!invalid;
        if(!invalid) highestRevision=Math.max(highestRevision,next.revision);
        for(ConfigListener listener:listeners) try { listener.afterChange(old,next); } catch(Throwable e) { error("config_refresh",e); }
        log("Config revision "+next.revision+(invalid?" invalid/stock":" observed"));
        logRules(next);
        status();
    }
    private void status() {
        log("stage=port_status baseline="+adapter.baseline().id()+" current_pkg="+currentPackage()
                +" revision="+config.revision+" enabled="+config.enabled+" demonstration="+config.demonstrationMode+" families="+new org.json.JSONObject(families));
    }
    private void logRules(ConfigSnapshot next) {
        for(var entry:next.apps.entrySet()) {
            var rule=entry.getValue();
            log("stage=config_rule revision="+next.revision+" master="+next.enabled+" pkg="+entry.getKey()
                    +" enabled="+rule.enabled+" touch="+rule.touch+" gyro="+rule.gyro+" superTouch="+rule.superTouch
                    +" superGyro="+rule.superGyro+" audio="+rule.audio+" frame="+rule.frame+" sr="+rule.sr
                    +" force144="+rule.force144+" monsterPlus="+rule.monsterPlus+" benchmarkMode="+rule.benchmarkMode+" thermalBypass="+rule.thermalBypass+" frameCustom="+rule.tuning.frameCustom()+" srCustom="+rule.tuning.srCustom()+" visual="+rule.visual
                    +" donors="+rule.audioDonor+","+rule.frameDonor+","+rule.srDonor);
        }
    }
}
