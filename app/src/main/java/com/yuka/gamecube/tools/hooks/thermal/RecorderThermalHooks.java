package com.yuka.gamecube.tools.hooks.thermal;

import com.yuka.gamecube.tools.hooks.HookRuntime;
import com.yuka.gamecube.tools.hooks.Reflect;

/** Keep real fever state; only the recorder's effective heat/availability decisions are overridden. */
final class RecorderThermalHooks {
    static void install(HookRuntime r) throws Throwable {
        int sdk=r.adapter.baseline().sdk();
        if(sdk==34) {
            r.log("feature=thermal stage=feature_not_present feature_name=record_status sdk=34");return;
        }
        String bean=sdk==35?"kb.a":"yc.a",manager=sdk==35?"jb.c":"xc.c";
        var gate=new ThermalGate(r);
        var hot=r.method(bean,"d");
        for(String method:new String[]{"a","b","c","d","e"}) r.method(bean,method);
        r.hook(bean,"d",boolean.class,chain->{
            Object stock=chain.proceed();var session=gate.foreground();
            if(!gate.valid(session)) return stock;
            gate.decision(session,"recording_hot",stock,false);return false;
        });
        r.hook(bean,"a",boolean.class,chain->{
            Object stock=chain.proceed(),status=chain.getThisObject();var session=gate.foreground();
            if(!gate.valid(session)) return stock;
            boolean allowed=ThermalFeaturePolicy.recording((Boolean)r.original(hot,status),
                    (Boolean)Reflect.call(status,"c"),(Boolean)Reflect.call(status,"e"),(Boolean)Reflect.call(status,"b"),true);
            gate.decision(session,"recording_available",stock,allowed);return allowed;
        });
        gate.deopt(bean);gate.deopt(manager);
        gate.deopt("com.vivo.gameassistant.recorder.recordback.view.BackRecordFloatView");
        gate.deopt("com.vivo.gameassistant.recorder.highlightrecoder.HighLightRecorderView");
        gate.deopt(sdk==35?"gb.f":"vc.q");
        r.log("feature=thermal stage=feature_bindings feature_name=recording status="+bean+" raw_state_retained=true");
    }
    private RecorderThermalHooks() {}
}
