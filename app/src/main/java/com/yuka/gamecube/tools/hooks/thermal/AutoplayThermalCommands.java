package com.yuka.gamecube.tools.hooks.thermal;

import android.os.Bundle;
import com.yuka.gamecube.tools.hooks.HookRuntime;
import com.yuka.gamecube.tools.hooks.Reflect;

/** Suppress only heat stop commands, retaining user/death/timeout/battery/screen cleanup. */
final class AutoplayThermalCommands {
    static void install(HookRuntime r) throws Throwable {
        int sdk=r.adapter.baseline().sdk();String prefix=sdk==34?"s9.":sdk==35?"v9.":"bb.";
        var gate=new ThermalGate(r);
        String framework=prefix+"c",watch=prefix+(sdk==36?"f":"g");
        for(String name:new String[]{framework,watch}) {
            boolean nativeFramework=name.equals(framework);
            String pkg=nativeFramework?"d":"c",pid=nativeFramework?"c":"e";
            for(String field:new String[]{"a",pkg,pid}) Reflect.field(r.type(name),field);
            r.hook(name,nativeFramework?"p":"o",void.class,chain->{
                Object owner=chain.getThisObject();int command=(Integer)chain.getArg(0);
                var session=gate.session((String)Reflect.get(owner,pkg));
                if(Boolean.TRUE.equals(Reflect.get(owner,"a")) &&
                        ThermalFeaturePolicy.thermalStop(command,true,gate.valid(session))) {
                    int saved=(Integer)Reflect.get(owner,pid);
                    if(saved<=0 || saved==session.pid()) {
                        gate.decision(session,"autoplay_heat_stop",command,"suppressed");return null;
                    }
                }
                return chain.proceed();
            },nativeFramework?new Class<?>[]{int.class,Bundle.class}:new Class<?>[]{int.class});
            if(!nativeFramework) r.hook(name,"t",void.class,chain->{
                Object owner=chain.getThisObject();int command=(Integer)chain.getArg(0);
                var session=gate.session((String)Reflect.get(owner,pkg),(Integer)Reflect.get(owner,pid));
                if(Boolean.TRUE.equals(Reflect.get(owner,"a")) &&
                        ThermalFeaturePolicy.thermalStop(command,false,gate.valid(session))) {
                    gate.decision(session,"autoplay_call_heat_stop",command,"suppressed");return null;
                }
                return chain.proceed();
            },int.class,Bundle.class);
            gate.deopt(name);
        }
        r.log("feature=thermal stage=feature_bindings feature_name=autoplay_commands framework="+framework+" watch="+watch);
    }
    private AutoplayThermalCommands() {}
}
