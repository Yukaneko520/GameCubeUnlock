package com.yuka.gamecube.tools.hooks.profiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure ownership rules shared by both runtime profile lists. */
final class ProfilePolicy {
    private ProfilePolicy() {}

    static <T> List<T> nativeEntries(List<T> effective, Set<T> owned) {
        List<T> result = new ArrayList<>();
        for (T entry : effective) if (!owned.contains(entry)) result.add(entry);
        return result;
    }

    static boolean canClone(boolean enabled, boolean hardware, boolean nativeTarget,
                            String target, String donor, boolean nativeDonorEligible) {
        return enabled && hardware && !nativeTarget && target != null && !target.isEmpty()
                && donor != null && !donor.isEmpty() && !target.equals(donor)
                && nativeDonorEligible;
    }
    static <T> List<T> retiredProfiles(Map<String, T> old, Map<String, T> next) {
        List<T> retired = new ArrayList<>();
        for (var entry : old.entrySet())
            if (next.get(entry.getKey()) != entry.getValue()) retired.add(entry.getValue());
        return retired;
    }
    static final class CacheStamp {
        final Object snapshot, list;
        final int size;
        CacheStamp(Object snapshot, Object list, int size) {
            this.snapshot = snapshot; this.list = list; this.size = size;
        }
        boolean matches(Object snapshot, Object list, int size) {
            return this.snapshot == snapshot && this.list == list && this.size == size;
        }
    }

    static final class Ticket {
        final String pkg;
        final int pid;
        final long revision;
        final Object profile;
        Ticket(String pkg, int pid, long revision, Object profile) {
            this.pkg = pkg;
            this.pid = pid;
            this.revision = revision;
            this.profile = profile;
        }
        boolean matches(String current, int currentPid, long currentRevision, Object currentProfile) {
            return pkg.equals(current) && pid > 0 && pid == currentPid
                    && revision == currentRevision && profile == currentProfile;
        }
    }
}
