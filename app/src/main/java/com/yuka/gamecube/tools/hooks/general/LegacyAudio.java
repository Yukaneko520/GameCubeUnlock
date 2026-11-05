package com.yuka.gamecube.tools.hooks.general;

import io.github.libxposed.api.XposedInterface;
import com.yuka.gamecube.tools.core.AppRule;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
import com.yuka.gamecube.tools.core.ProfileDonors;
import com.yuka.gamecube.tools.hooks.HookRuntime;
import com.yuka.gamecube.tools.hooks.ConfigListener;
import java.util.HashMap;
import java.util.Map;

/** Pins real OEM presets; never changes the package passed to the audio backend. */
final class LegacyAudio {
    private final LegacyAudioAccess a;
    private final HookRuntime r;
    private final RuleOverlay overlay;
    private final Map<String, Admission> admitted = new HashMap<>();
    private final Map<String, Admission> prepared = new HashMap<>();
    private ConfigSnapshot admissionSnapshot;

    LegacyAudio(HookRuntime runtime, RuleOverlay overlay) throws Throwable {
        r = runtime;
        this.overlay = overlay;
        admissionSnapshot = r.snapshot();
        a = new LegacyAudioAccess(r);
    }

    void install() throws Throwable {
        for(var caller:a.applies) r.log("feature=audio stage=deopt method="+caller+" result="+r.module.deoptimize(caller));
        r.hook(a.layout.resolverOwner(),a.layout.resolverMethod(), a.resolver.getReturnType(), chain -> resolve(chain), String.class);
        r.hook(a.layout.controller(),a.layout.command(), String.class,
                chain -> soundCommand(chain), String.class, Boolean.class, Boolean.class);
        r.hook(a.layout.controller(),a.layout.apply(), void.class,
                chain -> apply(chain), String.class, Boolean.class, Boolean.class, Boolean.class,
                a.equalizer, a.enhancer, a.reason);
        r.hook(a.layout.controller(),a.layout.alternate(), void.class,
                chain -> apply(chain), String.class, Boolean.class, a.enhancer, a.equalizer, boolean.class);
        r.addListener(new ConfigListener() {
            @Override public void beforeChange(ConfigSnapshot old, ConfigSnapshot next) {
                revoke(next);
            }
        });
    }

    synchronized Admission prepare(ConfigSnapshot snapshot, String target, String function, Object config,
                      boolean scoped) throws Throwable {
        AppRule rule = snapshot.rule(target);
        prepared.remove(target);
        if (!snapshot.allows(target, "audio")) { withdraw(target); return null; }
        Object values = r.original(a.common, null);
        if (r.context() == null || !Boolean.TRUE.equals(r.original(a.capable, values, r.context()))
                || a.conflict(r,values)) {
            r.log("feature=audio stage=eligibility result=blocked reason=capability pkg=" + target + " revision=" + snapshot.revision);
            withdraw(target);
            return null;
        }
        int active = ((Number) r.original(a.version, values)).intValue();
        if (!function.equals("game_custom_sound_for_version_" + active)) {
            r.log("feature=audio stage=eligibility result=blocked reason=version pkg=" + target + " revision=" + snapshot.revision);
            withdraw(target); return null;
        }
        boolean oversea = Boolean.TRUE.equals(r.original(a.region, values));
        AudioProfiles.Selection selected;
        try {
            selected = AudioProfiles.resolve(ProfileDonors.candidates("audio", target, rule.audioDonor),
                    donor -> overlay.donorEligible(function, target, donor, config, scoped),
                    donor -> r.original(a.resolver, null, donor));
        } catch (Throwable failure) { withdraw(target); throw failure; }
        r.log("feature=audio stage=profile mode=" + (rule.audioDonor.isEmpty() ? "auto" : "explicit")
                + " pkg=" + target + " donor=" + (selected == null ? "unresolved" : selected.donor)
                + " version=" + active + " oversea=" + oversea + " revision=" + snapshot.revision);
        if (selected == null) { withdraw(target); return null; }
        Admission pin = new Admission(target, selected.donor, function, selected.preset, snapshot, oversea, config);
        Admission old = admitted.get(target);
        if (old != null && !pin.sameProfile(old) && !close(old)) return null;
        prepared.put(target, pin);
        return pin;
    }

    synchronized boolean commit(Admission admission) throws Throwable {
        if (prepared.get(admission.target) != admission
                || !admission.canCommit(admissionSnapshot, r.snapshot())) return false;
        Object values = r.original(a.common, null);
        if (!admission.function.equals("game_custom_sound_for_version_" + r.original(a.version, values))
                || admission.oversea != Boolean.TRUE.equals(r.original(a.region, values))) return false;
        Admission old = admitted.get(admission.target);
        if (old != null) {
            if (admission.sameProfile(old) && !old.revoked) admission.used = old.used;
            else if (!close(old)) return false;
        }
        admitted.put(admission.target, admission);
        prepared.remove(admission.target);
        return true;
    }

    private Object resolve(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        if (result != null) return result;
        try {
            synchronized (this) {
                Admission pin = admitted.get((String) chain.getArg(0));
                // Retained tombstones protect delayed original T calls from null getId().
                // T below suppresses enables after revocation; pins never authorize a new gate.
                if (pin != null) {
                    Object values = r.original(a.common, null);
                    if (pin.oversea == Boolean.TRUE.equals(r.original(a.region, values))) return pin.preset;
                }
            }
        } catch (Throwable failure) { r.error("audio_resolver_extension",failure); }
        return result;
    }

    private synchronized Object soundCommand(XposedInterface.Chain chain) throws Throwable {
        ConfigSnapshot snapshot = r.snapshot();
        String target = (String) chain.getArg(0);
        Admission pin = admitted.get(target);
        if (pin == null) return chain.proceed();
        try {
            Object values = r.original(a.common, null);
            boolean active = !pin.revoked && pin.allowed(snapshot)
                    && pin.function.equals("game_custom_sound_for_version_" + r.original(a.version, values))
                    && pin.oversea == Boolean.TRUE.equals(r.original(a.region, values))
                    && !a.conflict(r,values)
                    && Boolean.TRUE.equals(r.original(a.capable, values, r.context()));
            if (!active) return "SoundEQ:package=" + target + ";mode=0;";
        } catch (Throwable failure) {
            r.error("audio_command_validation",failure);
            return "SoundEQ:package=" + target + ";mode=0;";
        }
        Object result = chain.proceed();
        pin.used |= result instanceof String && !((String) result).endsWith("mode=0;");
        r.log("feature=audio stage=command-built pkg=" + target + " donor=" + pin.donor
                + " revision=" + snapshot.revision + " command=" + result + " backend=unknown");
        return result;
    }

    private synchronized Object apply(XposedInterface.Chain chain) throws Throwable {
        // Hold through the OEM backend call: a revoke must close after a pending enable, not before it.
        return chain.proceed();
    }

    private synchronized void revoke(ConfigSnapshot next) {
        // Snapshot identity also changes on installed-package refreshes and invalid-config withdrawal.
        admissionSnapshot = next;
        prepared.clear();
        for (Admission pin : admitted.values()) {
            if (pin.allowed(next)) continue;
            close(pin);
        }
    }

    private boolean close(Admission pin) {
        pin.revoked = true;
        if (!pin.used) return true;
        try {
            if (r.context() == null) throw new IllegalStateException("no audio context");
            Object audio = a.audioConstructor.newInstance(r.context(), null, null);
            r.original(a.setAudio, audio, "SoundEQ:package=" + pin.target + ";mode=0;", null);
            pin.used = false;
            r.log("experimental audio close submitted for " + pin.target);
            return true;
        } catch (Throwable failure) {
            r.error("audio_close_preset_retained",failure);
            return false;
        }
    }

    synchronized void withdraw(String target) {
        Admission old = admitted.get(target);
        if (old != null) close(old);
    }

    static final class Admission {
        final String target, donor, function, requestedSelector;
        final Object preset, source;
        final ConfigSnapshot snapshot;
        final boolean oversea;
        boolean used, revoked;
        Admission(String target, String donor, String function, Object preset, ConfigSnapshot snapshot, boolean oversea) {
            this(target, donor, function, preset, snapshot, oversea, null);
        }
        Admission(String target, String donor, String function, Object preset, ConfigSnapshot snapshot,
                  boolean oversea, Object source) {
            this.target = target; this.donor = donor; this.function = function;
            this.preset = preset; this.snapshot = snapshot; this.oversea = oversea; this.source = source;
            AppRule rule = snapshot.rule(target);
            requestedSelector = rule.audioDonor;
        }
        boolean allowed(ConfigSnapshot current) {
            AppRule rule = current.rule(target);
            return !revoked && snapshot == current && current.allows(target, "audio")
                    && requestedSelector.equals(rule.audioDonor)
                    && (requestedSelector.isEmpty() || requestedSelector.equals(donor));
        }
        boolean sameProfile(Admission old) {
            return snapshot == old.snapshot && source == old.source && preset == old.preset
                    && donor.equals(old.donor) && function.equals(old.function) && oversea == old.oversea;
        }
        boolean canCommit(ConfigSnapshot expected, ConfigSnapshot published) {
            return snapshot == expected && snapshot == published && allowed(published);
        }
    }
}

