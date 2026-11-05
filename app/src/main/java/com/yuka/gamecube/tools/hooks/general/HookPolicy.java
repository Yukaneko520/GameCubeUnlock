package com.yuka.gamecube.tools.hooks.general;

import java.util.List;

/** Membership policy only; device predicates remain in the original evaluator. */
final class HookPolicy {
    static final int DENY = -2, MISSING = -1;

    static boolean suppressGyroSampler(com.yuka.gamecube.tools.core.ConfigSnapshot snapshot,String pkg) {
        return snapshot.allows(pkg,"gyro");
    }

    static String feature(String function) {
        if ("game_manipulation_touch".equals(function)) return "touch";
        if ("game_manipulation_gyroscope".equals(function)) return "gyro";
        if ("game_custom_sound_for_version_1".equals(function)
                || "game_custom_sound_for_version_2".equals(function)) return "audio";
        return null;
    }

    static int match(String pkg, List<Row> rows) {
        if (pkg == null || pkg.isEmpty() || rows == null) return DENY;
        int first = MISSING;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (row == null || row.base == null || row.base.isEmpty()) return DENY;
            if (!pkg.contains(row.base)) continue;
            if (!row.white || !row.nestedAllowed) return DENY;
            if (first == MISSING) first = i;
        }
        return first;
    }

    static final class Row {
        final String base;
        final boolean white, nestedAllowed;
        Row(String base, boolean white, boolean nestedAllowed) {
            this.base = base;
            this.white = white;
            this.nestedAllowed = nestedAllowed;
        }
    }

    private HookPolicy() {}
}
