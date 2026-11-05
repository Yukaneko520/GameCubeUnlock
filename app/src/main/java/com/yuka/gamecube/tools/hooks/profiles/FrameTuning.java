package com.yuka.gamecube.tools.hooks.profiles;

import io.github.libxposed.api.XposedInterface;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
import com.yuka.gamecube.tools.core.GraphicsTuning;
import com.yuka.gamecube.tools.hooks.HookRuntime;
import android.content.Context;
import java.util.*;

/** Consumer/dispatch overlays; profile getters and database serialization stay untouched. */
final class FrameTuning {
    private final HookRuntime runtime;
    private final boolean legacy;
    private final com.yuka.gamecube.tools.compat.LegacyFrameLayout names;
    private record Ticket(ConfigSnapshot snapshot,String pkg,int pid,GraphicsTuning.FrameValue value) {}
    private final ThreadLocal<Ticket> applying=new ThreadLocal<>();
    private FrameTuning(HookRuntime runtime) {
        this.runtime=runtime;legacy=runtime.adapter.baseline().sdk()<=35;
        names=legacy?com.yuka.gamecube.tools.compat.LegacyFrameLayout.forSdk(runtime.adapter.baseline().sdk()):null;
    }
    static void install(HookRuntime runtime) throws Throwable {
        FrameTuning tuning=new FrameTuning(runtime); ProfileAccess group=new ProfileAccess(runtime);
        Class<?> state=runtime.type(FrameHooks.STATE);
        String strategy=runtime.adapter.frameProfile().strategy();
        String helper="com.vivo.gameassistant.frameinterpolation."+(tuning.legacy?"g":"i");
        String dispatch=tuning.legacy?tuning.names.dispatch():"l";
        try {
            // Deoptimize the verified dispatch callers so AOT-inlined setters cannot bypass the hook.
            for(var method:List.of(runtime.method(strategy,dispatch,int.class,int.class,int.class,int.class,state),
                    runtime.method(helper,tuning.legacy&&tuning.names.os4()?"f":"h",int.class,int.class,int.class,int.class)))
                runtime.log("feature=frame_tuning deopt="+method+" result="+runtime.module.deoptimize(method));
            group.hook(strategy,dispatch,void.class,tuning::dispatch,int.class,int.class,int.class,int.class,state);
            group.hook(tuning.legacy?tuning.names.utils():ProfileStore.UTILS,tuning.legacy?tuning.names.pidRevised():"V0",boolean.class,chain-> {
                Ticket ticket=tuning.applying.get();
                if(ticket!=null && ticket.pkg.equals(chain.getArg(0)) && tuning.valid(ticket)) return false;
                return chain.proceed();
            },String.class);
            group.hook(tuning.legacy?tuning.names.writer():"com.vivo.common.utils.l0","b",void.class,tuning::emit,
                    Context.class,boolean.class,int.class,int.class,int.class,int.class);
            for(String name:tuning.legacy?tuning.names.os4()?List.<String>of():List.of("Q","S"):List.of("o0","q0")) {
                boolean pair=name.equals("q0")||name.equals("S");
                group.hook(tuning.legacy?Os5FrameHooks.CONTROLLER:FrameHooks.CONTROLLER,name,
                        pair?int[].class:int.class,chain->tuning.display(chain,pair),state,String.class);
            }
        } catch(Throwable error) { group.close(); throw error; }
    }
    static GraphicsTuning.FrameValue choose(GraphicsTuning tune,boolean boost,boolean sdk,int gameFps) {
        List<GraphicsTuning.FrameValue> values=boost?tune.boost():tune.power();
        return sdk?values.stream().filter(v->v.gameFps()==gameFps).findFirst().orElse(null):values.get(0);
    }
    private Object profile(String pkg) throws Throwable {
        Object raw=ProfileAccess.get(runtime.type(ProfileStore.SERVICE),runtime.adapter.frameProfile().cache());
        return raw instanceof List<?> list?NativeProfileResolver.find(list,true,pkg):null;
    }
    private boolean valid(Ticket ticket) throws Throwable {
        return runtime.snapshot()==ticket.snapshot && ticket.pkg.equals(runtime.currentPackage())
                && ticket.pid==(Integer)(legacy?runtime.original(runtime.method(names.utils(),names.lookupPid(),Context.class,String.class),null,runtime.context(),ticket.pkg):
                runtime.original(runtime.method("com.vivo.common.utils.l","A0",String.class),null,ticket.pkg));
    }
    private Object dispatch(XposedInterface.Chain chain) throws Throwable {
        Ticket next=null;
        ConfigSnapshot snapshot=runtime.snapshot();
        try {
            String pkg=runtime.currentPackage();
            var rule=snapshot.rule(pkg);
            String mode=String.valueOf(chain.getArg(4));
            if(snapshot.allows(pkg,"frame") && rule.tuning.frameCustom()
                    && (mode.equals("BOOST_FRAME") || mode.equals("OPTIMIZE_POWER_CONSUMPTION"))) {
                Object nativeProfile=profile(pkg);
                if(nativeProfile!=null) {
                    boolean sdk=Boolean.TRUE.equals(ProfileAccess.call(nativeProfile,NativeProfileResolver.sdkGetter(nativeProfile)));
                    var value=choose(rule.tuning,mode.equals("BOOST_FRAME"),sdk,(Integer)chain.getArg(0));
                    int pid=(Integer)chain.getArg(2);
                    if(value!=null && pid>0) {
                        Ticket ticket=new Ticket(snapshot,pkg,pid,value);
                        if(valid(ticket)) next=ticket;
                        else runtime.log("feature=frame_tuning pkg="+pkg+" stage=pid_not_bound pid="+pid);
                    }
                }
            }
        } catch(Throwable error) { runtime.error("frame_tuning_prepare_closed",error); }
        Ticket previous=applying.get();
        if(next==null) applying.remove(); else applying.set(next);
        try { return chain.proceed(); }
        finally { if(previous==null) applying.remove(); else applying.set(previous); }
    }
    private Object emit(XposedInterface.Chain chain) throws Throwable {
        Ticket ticket=applying.get();
        if(ticket==null || !Boolean.TRUE.equals(chain.getArg(1))) return chain.proceed();
        try {
            if((Integer)chain.getArg(3)!=ticket.pid || !valid(ticket)) {
                runtime.log("feature=frame_tuning stage=stale_dispatch_skipped pkg="+ticket.pkg); return null;
            }
        } catch(Throwable error) { runtime.error("frame_tuning_dispatch_closed",error); return null; }
        Object[] args=chain.getArgs().toArray();
        runtime.log("feature=frame_tuning stage=dispatch pkg="+ticket.pkg+" pid="+ticket.pid
                +" old="+args[4]+"/"+args[5]+" new="+ticket.value.inputFps()+"/"+ticket.value.outputFps()
                +" index="+args[2]+" revision="+ticket.snapshot.revision+" actual_ack=see_backend_logs");
        args[4]=ticket.value.inputFps(); args[5]=ticket.value.outputFps();
        return chain.proceed(args);
    }
    private Object display(XposedInterface.Chain chain,boolean pair) throws Throwable {
        Object stock=chain.proceed(); ConfigSnapshot snapshot=runtime.snapshot();
        try {
            String pkg=(String)chain.getArg(1),mode=String.valueOf(chain.getArg(0));
            var rule=snapshot.rule(pkg);
            if(!snapshot.allows(pkg,"frame") || !rule.tuning.frameCustom() || !pkg.equals(runtime.currentPackage())
                    || !List.of("BOOST_FRAME","OPTIMIZE_POWER_CONSUMPTION").contains(mode)) return stock;
            Object nativeProfile=profile(pkg); if(nativeProfile==null) return stock;
            boolean sdk=Boolean.TRUE.equals(ProfileAccess.call(nativeProfile,"m"));
            int fps=sdk?(Integer)ProfileAccess.call(ProfileAccess.call(runtime.type("com.vivo.gameassistant.frameinterpolation."+(legacy?"g":"i")),"e"),
                    "c",runtime.context(),pkg):0;
            var value=choose(rule.tuning,mode.equals("BOOST_FRAME"),sdk,fps);
            if(value==null || runtime.snapshot()!=snapshot) return stock;
            return pair?new int[]{value.gameFps(),value.outputFps()}:value.outputFps();
        } catch(Throwable error) { runtime.error("frame_tuning_display_closed",error); return stock; }
    }
}
