package com.yuka.gamecube.tools.hooks.backend;

import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;
import com.yuka.gamecube.tools.core.DiagnosticThrottle;
import com.yuka.gamecube.tools.hooks.HookLog;
import com.yuka.gamecube.tools.hooks.Reflect;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.function.BiConsumer;

/** Reuses identity/preferences transport; publication is serialized with owned dispatch. */
final class SuperControlRuntime {
    final XposedModule module;
    final ClassLoader loader;
    final Context context;
    final Object gate=new Object();
    volatile BackendPolicy policy=BackendPolicy.closed();
    private final ArrayList<XposedInterface.HookHandle> handles=new ArrayList<>();
    private final DiagnosticThrottle throttle=new DiagnosticThrottle(256);
    private BackendConfig config;
    SuperControlRuntime(XposedModule module,ClassLoader loader,Context context) {
        this.module=module;this.loader=loader;this.context=context;
    }
    void start(Handler owner,BiConsumer<BackendPolicy,BackendPolicy> changed) {
        start(owner,changed,"superControls");
    }
    void start(Handler owner,BiConsumer<BackendPolicy,BackendPolicy> changed,String feature) {
        config=new BackendConfig(module,context,owner,new BackendConfig.Sink() {
            public void apply(BackendPolicy next,long token,java.util.function.LongSupplier epoch) {
                BackendPolicy old;
                synchronized(gate) {
                    if(token!=epoch.getAsLong()) return;
                    old=policy;policy=next;
                }
                log(feature,"","config","verified="+next.verified+" master="+next.snapshot.enabled+
                        " selected="+next.snapshot.apps.entrySet().stream()
                        .filter(e->feature.equals("visual")?next.visual(e.getKey()):
                                next.control(e.getKey(),"superTouch")||next.control(e.getKey(),"superGyro"))
                        .map(e->feature.equals("visual")?e.getKey()+":"+e.getValue().visual:
                                e.getKey()+":touch="+e.getValue().superTouch+":gyro="+e.getValue().superGyro).toList());
                changed.accept(old,next);
            }
            public void invalidate(String pkg) {
                BackendPolicy old;
                synchronized(gate) { old=policy;policy=old.without(pkg); }
                owner.post(()->changed.accept(old,policy));
            }
        },this::transport,this::error);
        config.request();
    }
    Class<?> type(String name) throws ClassNotFoundException { return Class.forName(name,false,loader); }
    Method method(String owner,String name,Class<?>...args) throws Exception { return Reflect.method(type(owner),name,args); }
    void hook(Method method,XposedInterface.Hooker body) {
        handles.add(module.hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(body));
    }
    void hook(String type,String name,Class<?> result,XposedInterface.Hooker body,Class<?>...args) throws Throwable {
        Method method=method(type,name,args);
        if(method.getReturnType()!=result) throw new NoSuchMethodException(type+"."+name+" return type");
        hook(method,body);
    }
    Object original(Method method,Object owner,Object...args) throws Throwable {
        return module.getInvoker(method).setType(XposedInterface.Invoker.Type.ORIGIN).invoke(owner,args);
    }
    void close() { for(var handle:handles) handle.unhook();handles.clear();policy=BackendPolicy.closed(); }
    void log(String feature,String pkg,String stage,String detail) {
        String line="feature="+feature+" pkg="+pkg+" revision="+policy.snapshot.revision+" stage="+stage+" "+detail;
        if(throttle.allow(line,SystemClock.elapsedRealtime(),1000))
            HookLog.write(module,source(),line,null);
    }
    void error(String stage,Throwable failure) {
        if(throttle.allow("error:"+stage,SystemClock.elapsedRealtime(),5000))
            HookLog.write(module,source(),"feature=superControls stage="+stage,failure);
    }
    private String source() {
        String process=android.app.Application.getProcessName();
        return process!=null?process:("android".equals(context.getPackageName())?"system_server":context.getPackageName());
    }
    private void transport(String line) { log("superControls","","transport",line); }
}
