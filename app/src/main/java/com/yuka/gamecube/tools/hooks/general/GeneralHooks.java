package com.yuka.gamecube.tools.hooks.general;

import io.github.libxposed.api.XposedInterface;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
import com.yuka.gamecube.tools.hooks.HookRuntime;
import java.lang.reflect.Method;

public final class GeneralHooks {
    public static void install(HookRuntime r) throws Throwable {
        r.installFamily("touch-gyro membership", () -> membership(r, false));
        r.installFamily("super touch-gyro admission", () -> {
            var api=com.yuka.gamecube.tools.compat.SuperControlLayout.forSdk(r.adapter.baseline().sdk());
            r.hook(api.touchHelper(),"f",boolean.class,chain->superAdmission(r,chain,"superTouch"),String.class);
            if(api.gyroHelper()!=null)
                r.hook(api.gyroHelper(),"h",boolean.class,chain->superAdmission(r,chain,"superGyro"),String.class);
            String ui=r.adapter.baseline().sdk()==34?"d":"getTabData";
            r.log("feature=superControls stage=ui_deopt result="+r.module.deoptimize(r.method(
                    "com.vivo.gameassistant.gamemanipulation.widget.GameManipulationView",ui)));
        });
        r.installFamily("legacy audio profiles and lifecycle", () -> membership(r, true));
    }

    private static void membership(HookRuntime r, boolean withAudio) throws Throwable {
        RuleAccess access = new RuleAccess(r);
        var api=r.adapter.support();
        RuleOverlay overlay = new RuleOverlay(access);
        LegacyAudio audio = withAudio ? new LegacyAudio(r, overlay) : null;
        if (audio != null) audio.install();
        r.hook(api.evaluator(), api.function(), boolean.class, chain -> evaluate(r, access, overlay, audio, chain, false),
                String.class, String.class, access.config);
        r.hook(api.evaluator(), api.membership(), boolean.class, chain -> evaluate(r, access, overlay, audio, chain, false),
                String.class, String.class, access.config);
        r.hook(api.evaluator(), api.scoped(), boolean.class, chain -> evaluate(r, access, overlay, audio, chain, true),
                String.class, access.config);
    }

    private static Object evaluate(HookRuntime r, RuleAccess access, RuleOverlay overlay,
                                   LegacyAudio audio, XposedInterface.Chain chain, boolean scoped) throws Throwable {
        ConfigSnapshot snapshot = r.snapshot();
        Object stock = chain.proceed();
        if (!Boolean.FALSE.equals(stock)) return stock;
        try {
            String target = (String) chain.getArg(scoped ? 0 : 1);
            Object config = chain.getArg(scoped ? 1 : 2);
            if (config == null) return stock;
            String function = scoped ? RuleAccess.string(config, "a") : (String) chain.getArg(0);
            String feature = HookPolicy.feature(function);
            if (feature == null || !snapshot.allows(target, feature)) return stock;
            if ("audio".equals(feature) != (audio != null)) return stock;
            LegacyAudio.Admission admission = null;
            if ("audio".equals(feature)) {
                admission = audio.prepare(snapshot, target, function, config, scoped);
                if (admission == null) { r.log("audio unresolved donor/capability/version for " + target); return stock; }
            }
            Object replacement = overlay.build(function, target, config, scoped,
                    admission == null ? null : admission.donor);
            if (replacement == null) {
                if (audio != null) audio.withdraw(target);
                r.log("membership extension declined for " + target + "/" + feature); return stock;
            }
            Object result = scoped
                    ? access.invoke((Method) chain.getExecutable(), chain.getThisObject(), target, replacement)
                    : access.invoke((Method) chain.getExecutable(), chain.getThisObject(), function, target, replacement);
            if (!Boolean.TRUE.equals(result)) return stock;
            if (admission != null && !audio.commit(admission)) return stock;
            return result;
        } catch (Throwable failure) {
            r.error("membership_extension",failure);
            return stock;
        }
    }

    private static Object superAdmission(HookRuntime r,XposedInterface.Chain chain,String feature) throws Throwable {
        Object stock = chain.proceed();
        String target=(String)chain.getArg(0);
        boolean selected=r.snapshot().allows(target,feature);
        if(selected || r.snapshot().apps.containsKey(target)) r.event(feature,target,"ui_admission",
                "stock="+stock+" selected="+selected+" effective="+(Boolean.TRUE.equals(stock)||selected)
                        +" backend=separate_hook physical_effect=unverified");
        return Boolean.FALSE.equals(stock) && selected?true:stock;
    }

    private GeneralHooks() {}
}
