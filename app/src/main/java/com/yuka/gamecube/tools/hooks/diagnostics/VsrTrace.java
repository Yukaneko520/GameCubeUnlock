package com.yuka.gamecube.tools.hooks.diagnostics;

import android.content.Context;
import android.provider.Settings;
import com.yuka.gamecube.tools.hooks.HookRuntime;
import com.yuka.gamecube.tools.hooks.Reflect;

/** PD2352's software VSR branch returns before the Secure SR writer. Observations never authorize it. */
final class VsrTrace {
    private static final String CONTROLLER="com.vivo.gameassistant.superresolution.i";
    private static final String KEY="vsr_value_from_gamecube";
    private VsrTrace() {}
    static void install(HookRuntime r) throws Throwable {
        r.hook("com.vivo.common.algorithem.b","f",boolean.class,chain -> {
            Object result=chain.proceed();
            try {
                String pkg=(String)chain.getArg(1);
                if ("vsr_controlled".equals(chain.getArg(2)) && r.snapshot().apps.containsKey(pkg))
                    r.event("sr",pkg,"algorithm_result","intercepted="+result+" backend_ack=unknown");
            } catch (Throwable error) { r.error("vsr_algorithm_observation",error); }
            return result;
        },Context.class,String.class,String.class);
        r.hook(CONTROLLER,"D1",boolean.class,chain -> {
            String pkg=(String)chain.getArg(1);
            boolean observe=r.snapshot().apps.containsKey(pkg);
            Context context=(Context)chain.getArg(0);
            String before=observe?read(context):"unobserved";
            Object result=chain.proceed();
            if (!observe) return result;
            try {
                Object common=Reflect.staticCall(r.type("com.vivo.common.a"),"p");
                Object vsr=r.original(r.method("com.vivo.common.a","k0"),common);
                Object hd=r.original(r.method("com.vivo.common.utils.l","W1"),null);
                Object strategy=Reflect.get(chain.getThisObject(),"z");
                Object monster=Reflect.get(chain.getThisObject(),"x");
                Object required=Reflect.call(strategy,"l");
                Object pid=Reflect.call(Reflect.staticCall(r.type("com.vivo.gameassistant.o"),"V"),"N0");
                r.event("sr",pkg,"request_returned:"+chain.getArg(2),
                        "enable="+chain.getArg(2)+" extreme="+chain.getArg(3)+" return="+result
                                +" pid="+pid+" vsr="+vsr+" hd="+hd+" monster_required="+required
                                +" monster_current="+monster+" global_before="+before+" global_after="+read(context)
                                +" readback_only=true backend_ack=unknown effect=unverified");
            } catch (Throwable error) { r.error("vsr_request_observation",error); }
            return result;
        },Context.class,String.class,boolean.class,boolean.class);
    }
    private static String read(Context context) {
        try { return Settings.Global.getString(context.getContentResolver(),KEY); }
        catch (Throwable ignored) { return "unavailable"; }
    }
}
