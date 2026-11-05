package com.yuka.gamecube.tools.core;

import java.util.List;

/** Candidate names only: every caller must still validate current native objects and eligibility. */
public final class ProfileDonors {
    private ProfileDonors() {}

    public static List<String> candidates(String feature, String target, String explicit) {
        if (!List.of("frame", "sr", "audio").contains(feature)) return List.of();
        if (explicit != null && !explicit.isEmpty()) return List.of(explicit);
        if (feature.equals("sr")) return List.of("com.tencent.tmgp.cf");
        if ("com.HoYoverse.hkrpgoversea".equals(target))
            return List.of("com.miHoYo.hkrpg", "com.miHoYo.Yuanshen");
        return List.of("com.miHoYo.Yuanshen");
    }
}
