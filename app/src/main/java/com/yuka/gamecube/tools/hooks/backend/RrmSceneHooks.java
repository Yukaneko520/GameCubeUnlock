package com.yuka.gamecube.tools.hooks.backend;

import android.os.SystemClock;
import com.yuka.gamecube.tools.core.DiagnosticThrottle;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/** Change normal scene rates inside one OEM arbitration; leave Power/FixRate and priorities intact. */
final class RrmSceneHooks {
    private final XposedModule module;
    private final RrmAccess access;
    private final Object manager;
    private final Supplier<DisplayRatePolicy.Selection> selection;
    private final java.util.function.BooleanSupplier withdrawalTrace;
    private final Consumer<String> log;
    private final BiConsumer<String,Throwable> errorLog;
    private final DiagnosticThrottle throttle=new DiagnosticThrottle(64);
    private final ThreadLocal<DisplayRatePolicy.Selection> evaluating=new ThreadLocal<>();
    private final ThreadLocal<Object> evaluatingDisplay=new ThreadLocal<>();
    private static final class Pass {
        final Object display;
        DisplayRatePolicy.Selection selection;
        Object winner;
        Pass(Object display) { this.display=display; }
    }
    private final ThreadLocal<Pass> rendering=new ThreadLocal<>();
    private final Method choose,sceneName,update,interactionUpdate,requestUpdate,privateMode,mapFps;
    private final List<Method> rates=new ArrayList<>();
    private final Method smartWindow;
    private final Method applyMode,activeFps,perform,buildSpecs;
    private final Field configs,configFps;
    private final Field owner,priority,reqFps,realFps,fps,reason;

    RrmSceneHooks(XposedModule module,ClassLoader loader,String prefix,RrmAccess access,Object manager,
                  Supplier<DisplayRatePolicy.Selection> selection,java.util.function.BooleanSupplier withdrawalTrace,Consumer<String> log,
                  BiConsumer<String,Throwable> errorLog) throws Exception {
        this.module=module; this.access=access; this.manager=manager; this.selection=selection; this.log=log;
        this.withdrawalTrace=withdrawalTrace;
        this.errorLog=errorLog;
        Class<?> display=Class.forName(prefix+"PhysicalDisplay",false,loader);
        Class<?> request=Class.forName(prefix+"RefreshRateRequest",false,loader);
        Class<?> scene=Class.forName(prefix+"scene.BaseScene",false,loader);
        applyMode=RrmAccess.method(display,"applyDisplayModeSpecs",void.class,false);
        activeFps=RrmAccess.method(display,"getActiveFps",int.class,false);
        perform=RrmAccess.method(display,"performSetActiveConfig",boolean.class,false,int.class,boolean.class);
        boolean modern=prefix.equals("com.vivo.services.rrm.");
        buildSpecs=modern?RrmAccess.method(display,"buildDisplayModeSpecs",boolean.class,false,int.class,int.class,int.class,int.class)
                :RrmAccess.method(display,"buildDisplayModeSpecs",boolean.class,false,int.class,int.class);
        configs=RrmAccess.requestField(display,"mSupportedConfigs",ArrayList.class);
        configFps=RrmAccess.requestField(Class.forName(prefix+"DisplayConfig",false,loader),"fps",int.class);
        choose=RrmAccess.method(Class.forName(prefix+"SceneManager",false,loader),
                "chooseHighestPriorityRequest",request,false,display,boolean.class);
        sceneName=RrmAccess.method(scene,"getName",String.class,false);
        update=RrmAccess.method(scene,"updateByDisplay",boolean.class,false,display,request);
        interactionUpdate=RrmAccess.method(Class.forName(prefix+"scene.InteractionScene",false,loader),
                "updateByDisplay",boolean.class,false,display,request);
        requestUpdate=RrmAccess.method(request,"update",boolean.class,false,display);
        privateMode=RrmAccess.method(request,"isUsePrivateFps",boolean.class,false,display);
        mapFps=RrmAccess.method(scene,"getFpsByDisplay",int.class,false,display,request);
        for(String type:List.of("BaseScene","AppRequestScene","AnimationScene"))
            rates.add(RrmAccess.method(Class.forName(prefix+"scene."+type,false,loader),
                    "getRealFps",int.class,false,display,request));
        smartWindow=prefix.equals("com.vivo.services.rrm.")
                ?RrmAccess.method(scene,"updateForSpecialScene",int.class,false,request,int.class):null;
        owner=RrmAccess.requestField(request,"owner",scene);
        priority=RrmAccess.requestField(request,"priority",int.class);
        reqFps=RrmAccess.requestField(request,"reqFps",int.class);
        realFps=RrmAccess.requestField(request,"realFps",int.class);
        fps=RrmAccess.requestField(request,"fps",int.class);
        reason=RrmAccess.requestField(request,"reason",String.class);
    }
    void install(List<XposedInterface.HookHandle> handles) throws Throwable {
        handles.add(module.hook(perform).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
            var previous=rendering.get(); rendering.set(new Pass(chain.getThisObject()));
            try { return chain.proceed(); }
            finally { if(previous==null) rendering.remove(); else rendering.set(previous); }
        }));
        handles.add(module.hook(choose).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
            var previous=evaluating.get();
            var previousDisplay=evaluatingDisplay.get();
            evaluating.remove(); evaluatingDisplay.remove();
            try {
                try {
                    var live=selection.get();
                    if(live!=null && (live.global() || !live.packages().isEmpty())) {
                        if(access.selected(live,manager,chain.getArg(0))) {
                            evaluating.set(live); evaluatingDisplay.set(chain.getArg(0));
                        }
                        else if(throttle.allow("gate",SystemClock.elapsedRealtime(),5000))
                            log.accept("stage=scene_gate reason=no_visible_selected_app_or_display_unavailable"
                                    +" global="+live.global()+" packages="+live.packages().keySet());
                    }
                } catch(Throwable error) { errorLog.accept("selection_failed",error); }
                Object result=chain.proceed();
                Pass pass=rendering.get();
                if(pass!=null && pass.display==chain.getArg(0)) { pass.selection=evaluating.get(); pass.winner=result; }
                try {
                    if(result!=null && (evaluating.get()!=null || withdrawalTrace.getAsBoolean())) {
                        String name=(String)RrmAccess.call(sceneName,owner.get(result));
                        if(throttle.allow(name+":"+fps.getInt(result),SystemClock.elapsedRealtime(),2000))
                            log.accept("stage=arbitration scene="+name+" priority="+priority.getInt(result)
                                    +" reqFps="+reqFps.getInt(result)+" realFps="+realFps.getInt(result)
                                    +" mappedFps="+fps.getInt(result)+" reason="+reason.get(result)
                                    +" revision_current="+(evaluating.get()==selection.get())
                                    +" selected="+(evaluating.get()!=null)+" withdrawal_trace="+withdrawalTrace.getAsBoolean());
                    }
                } catch(Throwable error) { errorLog.accept("winner_trace_failed",error); }
                return result;
            } finally {
                if(previous==null) evaluating.remove(); else evaluating.set(previous);
                if(previousDisplay==null) evaluatingDisplay.remove(); else evaluatingDisplay.set(previousDisplay);
            }
        }));
        handles.add(module.hook(privateMode).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(this::admitPrivateMode));
        for(Method rate:rates) handles.add(module.hook(rate)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                    int stock=(Integer)chain.proceed();
                    return rate(chain.getArg(1),stock);
                }));
        if(smartWindow!=null) handles.add(module.hook(smartWindow)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                    int stock=(Integer)chain.proceed();
                    return rate(chain.getArg(0),stock);
                }));
        handles.add(module.hook(buildSpecs).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
            Object[] args=chain.getArgs().toArray();
            try {
                Pass pass=rendering.get();
                if(pass!=null && pass.display==chain.getThisObject() && pass.winner!=null
                        && pass.selection!=null && pass.selection==selection.get()
                        && access.selected(pass.selection,manager,pass.display)) {
                    int original=(Integer)chain.getArg(1);
                    Object config=((List<?>)configs.get(pass.display)).get((Integer)chain.getArg(0));
                    String scene=(String)RrmAccess.call(sceneName,owner.get(pass.winner));
                    int switching=DisplayRatePolicy.modeSwitchType(true,scene,priority.getInt(pass.winner),
                            fps.getInt(pass.winner),configFps.getInt(config),original);
                    args[1]=switching;
                    if(switching!=original && throttle.allow("range:"+scene,SystemClock.elapsedRealtime(),2000))
                        log.accept("stage=physical_range scene="+scene+" oldSwitchType="+original
                                +" finalSwitchType="+switching+" minHz=144 maxHz=144");
                }
            } catch(Throwable error) { errorLog.accept("physical_range_failed",error); }
            return chain.proceed(args);
        }));
        handles.add(module.hook(applyMode).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
            Object result=chain.proceed();
            try {
                var live=selection.get();
                if(withdrawalTrace.getAsBoolean() || live!=null && (live.global() || !live.packages().isEmpty())
                        && access.selected(live,manager,chain.getThisObject()))
                    log.accept("stage=display_mode frameworkActiveFps="+RrmAccess.call(activeFps,chain.getThisObject())
                            +" global="+(live!=null&&live.global())+" withdrawal_trace="+withdrawalTrace.getAsBoolean()
                            +" evidence=framework_mode_not_panel_measurement");
            } catch(Throwable error) { errorLog.accept("display_trace_failed",error); }
            return result;
        }));
        // Callers may have inlined the original rate calculators before the module was loaded.
        for(Method caller:List.of(perform,choose,update,interactionUpdate,requestUpdate,mapFps))
            log.accept("stage=deopt method="+caller+" result="+module.deoptimize(caller));
        for(Method rate:rates) log.accept("stage=deopt method="+rate+" result="+module.deoptimize(rate));
        log.accept("stage=scene_hooks_ready scenes=Interaction,AppRequest,Animation privateMode=scoped_144 smartWindow="+(smartWindow!=null));
    }
    private Object admitPrivateMode(XposedInterface.Chain chain) throws Throwable {
        boolean stock=(Boolean)chain.proceed();
        if(stock) return true;
        Object request=chain.getThisObject(),display=chain.getArg(0);
        var live=selection.get(); Pass pass=rendering.get();
        boolean scoped=live!=null && (evaluating.get()==live && evaluatingDisplay.get()==display
                || pass!=null && pass.selection==live && pass.display==display && pass.winner==request);
        if(!scoped) return false;
        try {
            String scene=(String)RrmAccess.call(sceneName,owner.get(request));
            boolean admitted=DisplayRatePolicy.privateMode(false,true,scene,priority.getInt(request),realFps.getInt(request));
            if(admitted && throttle.allow("private:"+scene,SystemClock.elapsedRealtime(),2000))
                log.accept("stage=private_mode_admission scene="+scene+" realFps=144 old=false final=true reason="+reason.get(request));
            return admitted && live==selection.get();
        } catch(Throwable error) { errorLog.accept("private_mode_failed",error); return false; }
    }
    private int rate(Object request,int stock) {
        var scoped=evaluating.get();
        if(scoped==null || scoped!=selection.get()) return stock;
        try {
            String name=(String)RrmAccess.call(sceneName,owner.get(request));
            int result=DisplayRatePolicy.sceneFps(true,name,priority.getInt(request),stock);
            if(result!=stock && throttle.allow("rate:"+name+":"+stock,SystemClock.elapsedRealtime(),2000))
                log.accept("stage=scene_rate scene="+name+" priority="+priority.getInt(request)
                        +" oldFps="+stock+" finalFps="+result+" reason="+reason.get(request));
            return result;
        } catch(Throwable error) { errorLog.accept("scene_rate_failed",error); return stock; }
    }
}
