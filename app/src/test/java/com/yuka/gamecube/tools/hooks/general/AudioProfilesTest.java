package com.yuka.gamecube.tools.hooks.general;

import static org.junit.Assert.*;
import com.yuka.gamecube.tools.core.ProfileDonors;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class AudioProfilesTest {
    private static final String HSR = "com.miHoYo.hkrpg", GENSHIN = "com.miHoYo.Yuanshen";

    @Test public void autoSkipsIneligibleOrUnresolvedNativePresets() throws Throwable {
        Object preset = new Object();
        AudioProfiles.Selection selected = AudioProfiles.resolve(
                ProfileDonors.candidates("audio", "com.HoYoverse.hkrpgoversea", ""),
                pkg -> true, pkg -> Map.of(GENSHIN, preset).get(pkg));
        assertEquals(GENSHIN, selected.donor);
        assertSame(preset, selected.preset);
        assertNull(AudioProfiles.resolve(List.of(HSR), pkg -> false, pkg -> preset));
    }

    @Test public void explicitInvalidDonorNeverFallsBackToAutomaticCandidate() throws Throwable {
        List<String> visited = new ArrayList<>();
        assertNull(AudioProfiles.resolve(ProfileDonors.candidates("audio", "com.example", "com.missing"),
                pkg -> { visited.add(pkg); return false; }, pkg -> new Object()));
        assertEquals(List.of("com.missing"), visited);
    }

    @Test public void presetResolverNeverRunsForFailedOriginalEligibility() throws Throwable {
        assertNull(AudioProfiles.resolve(List.of(HSR, GENSHIN), pkg -> false,
                pkg -> { throw new AssertionError("ineligible preset evaluated"); }));
    }
}
