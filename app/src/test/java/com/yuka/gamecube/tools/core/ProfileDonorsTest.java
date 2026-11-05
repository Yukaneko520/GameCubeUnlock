package com.yuka.gamecube.tools.core;

import static org.junit.Assert.*;
import java.util.List;
import org.junit.Test;

public class ProfileDonorsTest {
    @Test public void overseasStarRailPrefersItsNativeRegionalProfile() {
        for (String feature : List.of("frame", "audio"))
            assertEquals(List.of("com.miHoYo.hkrpg", "com.miHoYo.Yuanshen"),
                    ProfileDonors.candidates(feature, "com.HoYoverse.hkrpgoversea", ""));
    }
    @Test public void arbitraryTargetsUseSeparateConservativeSources() {
        assertEquals(List.of("com.miHoYo.Yuanshen"),
                ProfileDonors.candidates("frame", "com.xd.rotaeno.googleplay", ""));
        assertEquals(List.of("com.miHoYo.Yuanshen"),
                ProfileDonors.candidates("audio", "com.xd.rotaeno.googleplay", ""));
        assertEquals(List.of("com.tencent.tmgp.cf"),
                ProfileDonors.candidates("sr", "com.xd.rotaeno.googleplay", ""));
    }
    @Test public void explicitSourceNeverFallsBackToAutomaticCandidates() {
        assertEquals(List.of("com.example.missing"),
                ProfileDonors.candidates("frame", "com.HoYoverse.hkrpgoversea", "com.example.missing"));
    }
    @Test public void controlFeaturesCannotReceiveSyntheticProfiles() {
        assertTrue(ProfileDonors.candidates("touch", "com.example.game", "").isEmpty());
        assertTrue(ProfileDonors.candidates("superGyro", "com.example.game", "").isEmpty());
    }
}
