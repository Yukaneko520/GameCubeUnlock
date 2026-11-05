package com.yuka.gamecube.tools.hooks.thermal;

import android.os.BatteryManager;
import android.provider.Settings;
import com.yuka.gamecube.tools.hooks.HookRuntime;
import com.yuka.gamecube.tools.hooks.Reflect;

/** The native helper combines heat, battery, power-saving and timeout checks in two inline branches. */
final class DoublePlayThermalHooks {
    static void install(HookRuntime r) throws Throwable {
        int sdk=r.adapter.baseline().sdk();var gate=new ThermalGate(r);
        String helper=sdk==34?"r7.b":sdk==35?"t7.b":"z8.b";
        String controller=sdk==34?"r7.l":sdk==35?"t7.k":"z8.k";
        String stop=sdk==34?"a0":sdk==35?"c0":"r0";
        String temperature=sdk==34?"f7.u0":sdk==35?"b7.e1":"com.vivo.common.e";
        var reading=sdk==36?r.method(temperature,"c",android.content.Context.class):r.method(temperature,sdk==34?"b":"c");
        for(String field:new String[]{"b","d","g"}) Reflect.field(r.type(helper),field);
        Reflect.field(r.type(controller),"f");r.method(controller,stop,boolean.class);
        r.hook(helper,"f",boolean.class,chain->{
            Object stock=chain.proceed();var session=gate.foreground();
            if(Boolean.TRUE.equals(stock) || !gate.valid(session)) return stock;
            BatteryManager battery=(BatteryManager)Reflect.get(chain.getThisObject(),"g");
            boolean batteryAllowed=true;
            if(battery!=null) {
                int status=battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS);
                batteryAllowed=status==2 || status==5 || battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)>20;
            }
            boolean powerSave=Settings.System.getInt(r.context().getContentResolver(),"power_save_type",0)==2;
            boolean allowed=ThermalFeaturePolicy.doublePlayEntry(batteryAllowed,powerSave,gate.valid(session));
            gate.decision(session,"double_play_entry",stock,allowed);return allowed;
        });
        r.hook(helper,"i",void.class,chain->{
            Object owner=chain.getThisObject(),target=Reflect.get(owner,"b");
            if(target==null) return chain.proceed();
            var session=gate.ownedPid((Integer)Reflect.get(target,"f"));
            if(!gate.valid(session)) return chain.proceed();
            int actual=sdk==36?(Integer)r.original(reading,null,r.context()):(Integer)r.original(reading,null);
            if(actual<=43 || !gate.valid(session)) return chain.proceed();
            var step=ThermalFeaturePolicy.doublePlayTick((Integer)Reflect.get(owner,"d"));
            Reflect.set(owner,"d",step.count());
            if(step.expired()) {
                Reflect.call(target,stop,true);Reflect.set(owner,"d",0);
            }
            gate.decision(session,"double_play_running_heat",actual,"suppressed timeout_expired="+step.expired()+" counter="+step.count());
            return null;
        },Long.class);
        gate.deopt(helper);gate.deopt(controller);
        r.log("feature=thermal stage=feature_bindings feature_name=double_play helper="+helper);
    }
    private DoublePlayThermalHooks() {}
}
