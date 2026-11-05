package com.yuka.gamecube.tools.hooks.profiles;

import com.yuka.gamecube.tools.core.ProfileDonors;
import java.util.List;

/** Selects current OEM objects; candidate names never authorize fabricated tuning. */
final class NativeProfileResolver {
    @FunctionalInterface interface Eligibility { boolean allows(String pkg) throws Throwable; }
    record Result(Object profile, String donor, String reason) {}

    static Result resolve(List<?> nativeEntries, boolean frame, String target, String explicit,
                          Eligibility eligibility) throws Throwable {
        if (find(nativeEntries, frame, target) != null) return new Result(null, target, "native_target");
        String rejected = "no_native_candidate";
        String last = "";
        boolean auto = explicit == null || explicit.isEmpty();
        for (String candidate : ProfileDonors.candidates(frame ? "frame" : "sr", target, explicit)) {
            last = candidate;
            Object profile = find(nativeEntries, frame, candidate);
            if (profile == null) { rejected = "donor_not_in_native_cache"; continue; }
            if (!eligibility.allows(candidate)) { rejected = "original_donor_gate_false"; continue; }
            if (frame && auto && !conservativeFrame(profile)) {
                rejected = "auto_profile_not_conservative"; continue;
            }
            return new Result(profile, candidate, "ready");
        }
        return new Result(null, last, rejected);
    }

    static Object find(List<?> nativeEntries, boolean frame, String pkg) throws Throwable {
        if (pkg == null || pkg.isEmpty()) return null;
        for (Object entry : nativeEntries)
            if (pkg.equals(ProfileAccess.call(entry, frame ? os4(entry)?"h":"k" : "p"))) return entry;
        return null;
    }

    private static boolean conservativeFrame(Object profile) throws Throwable {
        if (Boolean.TRUE.equals(ProfileAccess.call(profile, sdkGetter(profile)))) return false;
        Object values = ProfileAccess.call(profile, "a");
        if (!(values instanceof List<?> boost) || boost.isEmpty()) return false;
        for (Object triple : boost) {
            if ((Integer) ProfileAccess.call(triple, "c") != 60
                    || (Integer) ProfileAccess.call(triple, "a") != 45
                    || (Integer) ProfileAccess.call(triple, "b") != 90) return false;
        }
        return true;
    }

    static String describe(Object profile, boolean frame) throws Throwable {
        String description = "index=" + ProfileAccess.call(profile, frame&&os4(profile)?"e":"f");
        if (!frame) return description + ",state=" + ProfileAccess.call(profile, "q")
                + ",frame=" + ProfileAccess.call(profile, "d") + "/" + ProfileAccess.call(profile, "e");
        StringBuilder values = new StringBuilder();
        Object raw = ProfileAccess.call(profile, "a");
        if (raw instanceof List<?> triples) for (Object triple : triples) {
            if (values.length()>0) values.append(';');
            values.append(ProfileAccess.call(triple, "c")).append('/')
                    .append(ProfileAccess.call(triple, "a")).append('/').append(ProfileAccess.call(triple, "b"));
        }
        return description + ",sdk=" + ProfileAccess.call(profile, sdkGetter(profile)) + ",boost=" + values;
    }
    private static boolean os4(Object profile) { return profile.getClass().getName().equals("q6.a"); }
    static String sdkGetter(Object profile) { return os4(profile)?"i":"m"; }
}
