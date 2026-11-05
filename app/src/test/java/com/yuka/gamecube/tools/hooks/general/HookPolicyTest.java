package com.yuka.gamecube.tools.hooks.general;

import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class HookPolicyTest {
    @Test public void mapsOnlyMembershipFeatures() {
        assertEquals("touch", HookPolicy.feature("game_manipulation_touch"));
        assertEquals("gyro", HookPolicy.feature("game_manipulation_gyroscope"));
        assertEquals("audio", HookPolicy.feature("game_custom_sound_for_version_2"));
        assertNull(HookPolicy.feature("super_touch"));
        assertNull(HookPolicy.feature("frame_interpolation"));
        assertNull(HookPolicy.feature("game_manipulation_competition_param"));
        assertNull(HookPolicy.feature(null));
    }

    @Test public void blackPrefixCannotBeDonatedAround() {
        assertEquals(HookPolicy.DENY, HookPolicy.match("com.game.beta", Arrays.asList(
                new HookPolicy.Row("com.game", false, true),
                new HookPolicy.Row("com.game.beta", true, true))));
    }

    @Test public void everyOverlappingWhiteMustPassHardware() {
        assertEquals(HookPolicy.DENY, HookPolicy.match("com.game.beta", Arrays.asList(
                new HookPolicy.Row("com.game", true, true),
                new HookPolicy.Row("game.beta", true, false))));
    }

    @Test public void channelClonePrecedesFirstMatchingWhite() {
        assertEquals(1, HookPolicy.match("com.game.beta", Arrays.asList(
                new HookPolicy.Row("other", true, true),
                new HookPolicy.Row("com.game", true, true),
                new HookPolicy.Row("game.beta", true, true))));
    }

    @Test public void noMembershipAndMalformedRulesAreDifferent() {
        assertEquals(HookPolicy.MISSING, HookPolicy.match("com.new.game", Collections.singletonList(
                new HookPolicy.Row("com.native.game", true, true))));
        assertEquals(HookPolicy.DENY, HookPolicy.match("com.new.game", Collections.singletonList(
                new HookPolicy.Row("", true, true))));
        assertEquals(HookPolicy.DENY, HookPolicy.match(null, Collections.emptyList()));
    }
}
