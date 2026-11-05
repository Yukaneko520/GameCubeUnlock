package com.yuka.gamecube.tools.hooks.general;

import android.content.Context;
import com.yuka.gamecube.tools.compat.SuperControlLayout;
import com.yuka.gamecube.tools.hooks.ConfigListener;
import com.yuka.gamecube.tools.hooks.HookRuntime;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
import java.lang.reflect.Modifier;

/** Isolates ordinary X/Y gain from GameCube's empty 100-Hz gyro listener. */
public final class GyroSamplingHooks {
    private GyroSamplingHooks() {}

    public static void install(HookRuntime r) throws Throwable {
        String owner=SuperControlLayout.forSdk(r.adapter.baseline().sdk()).ordinaryController();
        var instance=r.method(owner,"f");
        var unregister=r.method(owner,"v");
        var foreground=r.method(owner,"g",Context.class,boolean.class,String.class);
        var save=r.method(owner,"m",Context.class,
                r.type("com.vivo.common.gamemanipulation.bean.GameManipulationBean"));
        if(instance.getReturnType()!=r.type(owner) || !Modifier.isStatic(instance.getModifiers())
                || unregister.getReturnType()!=void.class)
            throw new NoSuchMethodException("Unexpected gyro controller declaration: "+owner);

        r.hook(owner,"l",void.class,chain->{
            String pkg=(String)chain.getArg(1);
            if(!HookPolicy.suppressGyroSampler(r.snapshot(),pkg)) return chain.proceed();
            try {
                // Remove an existing listener too; the original unregister is idempotent.
                unregister.invoke(chain.getThisObject());
            } catch(Throwable failure) {
                r.error("gyro_sampler_cleanup_failed",failure);
                return chain.proceed();
            }
            r.event("gyro",pkg,"sampling_listener_suppressed",
                    "oem_period_us=10000 cleanup=oem_unregister gain_dispatch=unchanged super_gyro=independent");
            return null;
        },Context.class,String.class);
        for(var caller:new java.lang.reflect.Method[]{foreground,save})
            r.log("feature=gyro stage=sampling_deopt method="+caller+" result="+r.module.deoptimize(caller));

        r.addListener(new ConfigListener() {
            @Override public void afterChange(ConfigSnapshot old,ConfigSnapshot next) throws Throwable {
                String pkg=r.currentPackage();
                boolean before=HookPolicy.suppressGyroSampler(old,pkg);
                boolean after=HookPolicy.suppressGyroSampler(next,pkg);
                if(before==after) return;
                Object helper=instance.invoke(null);
                r.method(owner,"l",Context.class,String.class).invoke(helper,r.context(),pkg);
                r.event("gyro",pkg,"sampling_reconcile",
                        "suppressed_before="+before+" suppressed_now="+after+" admission=oem_when_unselected");
            }
        });
    }
}
