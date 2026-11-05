package com.yuka.gamecube.tools.hooks.thermal;

import com.yuka.gamecube.tools.compat.FeatureThermalLayout;
import com.yuka.gamecube.tools.hooks.HookRuntime;
import com.yuka.gamecube.tools.hooks.Reflect;

/** Bypass only the original heat/recovery predicates, retaining real readings and native feature ownership. */
public final class FeatureThermalHooks {
    private final HookRuntime r;
    private final ThermalGate gate;
    private FeatureThermalHooks(HookRuntime r) { this.r=r;gate=new ThermalGate(r); }
    public static void install(HookRuntime r) throws Throwable {
        var hooks=new FeatureThermalHooks(r);
        for(var decision:FeatureThermalLayout.decisions(r.adapter.baseline().sdk()))
            r.installFamily("thermal "+decision.feature(),()->hooks.install(decision));
        r.installFamily("thermal recording",()->RecorderThermalHooks.install(r));
        r.installFamily("thermal autoplay commands",()->AutoplayThermalCommands.install(r));
        r.installFamily("thermal autoplay inline",()->LegacyAutoplayThermal.install(r));
        r.installFamily("thermal double play",()->DoublePlayThermalHooks.install(r));
        int sdk=r.adapter.baseline().sdk();
        for(String caller:sdk==34?new String[]{"l9.d0","q8.q"}:sdk==35?new String[]{"p9.e0","u8.o"}:new String[]{"va.d0"})
            hooks.gate.deopt(caller);
    }
    private void install(FeatureThermalLayout.Decision d) throws Throwable {
        if(d.pkg()!=null) Reflect.field(r.type(d.owner()),d.pkg());
        if(d.capability()!=null) r.method(d.owner(),d.capability());
        bind(d,d.hot(),false);
        if(d.cool()!=null) bind(d,d.cool(),true);
        gate.deopt(d.owner());
        if(d.feature().equals("image")) gate.deopt("com.vivo.gameassistant.gamefilter.widget.GameFilterSettingView");
        if(d.feature().equals("image") && r.adapter.baseline().sdk()==34)
            gate.deopt("com.vivo.gameassistant.gamefilter.widget.UniversalFilterView");
        if(d.feature().equals("ray_tracing")) {
            gate.deopt("cc.c");gate.deopt("com.vivo.gameassistant.homegui.sideslide.panels.qchip.QLightTrackView");
        }
        r.log("feature=thermal stage=feature_bindings feature_name="+d.feature()+" owner="+d.owner()+" hot="+d.hot()+" cool="+d.cool());
    }
    private void bind(FeatureThermalLayout.Decision d,String method,boolean recovery) throws Throwable {
        r.hook(d.owner(),method,boolean.class,chain->{
            Object stock=chain.proceed(),owner=chain.getThisObject();
            String pkg=d.pkg()==null?r.currentPackage():(String)Reflect.get(owner,d.pkg());
            var session=gate.foreground(pkg);
            if(!gate.valid(session)) return stock;
            if(d.argument()==FeatureThermalLayout.Argument.MODE) {
                int mode=(Integer)chain.getArg(1);
                if((mode!=1 && mode!=2) || !Boolean.TRUE.equals(Reflect.call(owner,d.capability()))) return stock;
            }
            gate.decision(session,d.feature()+(recovery?"_recovery":"_hot"),stock,recovery);
            return recovery;
        },d.argument().types());
    }
}
