package com.yuka.gamecube.tools.hooks.backend;

import android.os.SystemClock;
import com.yuka.gamecube.tools.core.DiagnosticThrottle;
import com.yuka.gamecube.tools.hooks.HookLog;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Supplier;

/** Preserve native request handles; normal arbitration resumes as soon as bypass/focus is withdrawn. */
final class RrmThermalSceneHooks {
    private final XposedModule module;
    private final RrmAccess access;
    private final Object manager;
    private final Supplier<BackendPolicy> policy;
    private final Method valid,name,choose;
    private final ThreadLocal<Object> evaluatingDisplay=new ThreadLocal<>();
    private final Class<?> sceneManager;
    private final Field priority,reason,client,uid,pid,pkg;
    private final DiagnosticThrottle logs=new DiagnosticThrottle(64);
    RrmThermalSceneHooks(XposedModule module,ClassLoader loader,String prefix,RrmAccess access,Object manager,
                         Supplier<BackendPolicy> policy) throws Exception {
        this.module=module;this.access=access;this.manager=manager;this.policy=policy;
        Class<?> scene=Class.forName(prefix+"scene.BaseScene",false,loader);
        Class<?> display=Class.forName(prefix+"PhysicalDisplay",false,loader);
        Class<?> request=Class.forName(prefix+"RefreshRateRequest",false,loader);
        sceneManager=Class.forName(prefix+"SceneManager",false,loader);
        valid=RrmAccess.method(scene,"isValid",boolean.class,false,display,request);
        choose=RrmAccess.method(sceneManager,"chooseHighestPriorityRequest",request,false,display,boolean.class);
        name=RrmAccess.method(scene,"getName",String.class,false);
        priority=RrmAccess.requestField(request,"priority",int.class);reason=RrmAccess.requestField(request,"reason",String.class);
        client=com.yuka.gamecube.tools.hooks.Reflect.field(request,"client");
        Class<?> process=client.getType();
        uid=RrmAccess.requestField(process,"mUid",int.class);pid=RrmAccess.requestField(process,"mPid",int.class);
        pkg=RrmAccess.requestField(process,"mPkgName",String.class);
    }
    void install(List<XposedInterface.HookHandle> handles) throws Throwable {
        handles.add(module.hook(choose).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
            if(!ThermalRatePolicy.active(policy.get())) return chain.proceed();
            Object previous=evaluatingDisplay.get();evaluatingDisplay.set(chain.getArg(0));
            try { return chain.proceed(); }
            finally { if(previous==null) evaluatingDisplay.remove();else evaluatingDisplay.set(previous); }
        }));
        handles.add(module.hook(valid).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
            Object stock=chain.proceed();
            if(!Boolean.TRUE.equals(stock) || evaluatingDisplay.get()!=chain.getArg(0)) return stock;
            try {
                BackendPolicy current=policy.get();
                if(!ThermalRatePolicy.active(current)) return stock;
                Object display=chain.getArg(0),request=chain.getArg(1);
                String scene=(String)RrmAccess.call(name,chain.getThisObject()),why=(String)reason.get(request);
                if(!ThermalRatePolicy.thermal(scene,priority.getInt(request),why)) return stock;
                Object focus=access.focus(display),requested=client.get(request);
                for(Object record:access.windows(manager)) {
                    String candidate=(String)access.get(record,"pkgName");
                    if(candidate==null || !current.snapshot.allows(candidate,"thermalBypass")
                            || access.get(record,"win")==null || access.get(record,"client")==null) continue;
                    var window=access.window(display,record,focus);
                    if(!ThermalRatePolicy.selected(current,window)) continue;
                    Object process=access.get(record,"client");int livePid=access.number(process,"mPid");
                    if(!ThermalRatePolicy.subject(why,requested==null?null:(String)pkg.get(requested),
                            requested==null?0:pid.getInt(requested),requested==null?0:uid.getInt(requested),
                            window.pkg(),livePid,window.uid())) continue;
                    if(current!=policy.get()) return stock;
                    if(logs.allow(window.pkg()+":"+why+":"+current.snapshot.revision,SystemClock.elapsedRealtime(),1000))
                        HookLog.write(module,"system","feature=thermal stage=refresh_vote_suppressed pkg="+window.pkg()
                                +" pid="+livePid+" uid="+window.uid()+" scene="+scene+" reason="+why
                                +" revision="+current.snapshot.revision+" requested_fps_unforced=true cpu_gpu_policy_unchanged=true",null);
                    return false;
                }
            } catch(Throwable failure) {
                if(logs.allow("error",SystemClock.elapsedRealtime(),10000))
                    HookLog.write(module,"system","feature=thermal stage=refresh_vote_closed",failure);
            }
            return stock;
        }));
        for(Method method:sceneManager.getDeclaredMethods())
            if(!java.lang.reflect.Modifier.isAbstract(method.getModifiers()) && !java.lang.reflect.Modifier.isNative(method.getModifiers()))
                module.deoptimize(method);
        HookLog.write(module,"system","feature=thermal stage=refresh_vote_bindings owner="+valid.getDeclaringClass().getName(),null);
    }
}
