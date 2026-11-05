package com.yuka.gamecube.tools.hooks.profiles;

import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class NativeProfileResolverTest {
    @Test public void os4ProfileUsesItsOwnPackageSdkIndexAndCopyCodec() throws Throwable {
        var donor=new q6.a("com.miHoYo.Yuanshen","Native","#",3,false,
                List.of(new ProfileCopiesTest.Triple(60,45,90)),List.of(),Map.of());
        assertSame(donor,NativeProfileResolver.find(List.of(donor),true,"com.miHoYo.Yuanshen"));
        assertSame(donor,NativeProfileResolver.resolve(List.of(donor),true,"com.target.game","",pkg->true).profile());
        assertNull(NativeProfileResolver.resolve(List.of(donor),true,"com.target.game","",pkg->false).profile());
        var copy=(q6.a)ProfileCopies.frame(donor,"com.target.game");
        assertEquals("com.target.game",copy.h());assertEquals("com.miHoYo.Yuanshen",donor.h());
        assertEquals("index=3,sdk=false,boost=60/45/90",NativeProfileResolver.describe(copy,true));
        assertEquals("native_target",NativeProfileResolver.resolve(List.of(copy),true,"com.target.game","",pkg->false).reason());
    }
    private static final String HSR = "com.HoYoverse.hkrpgoversea";
    private static final String CN_HSR = "com.miHoYo.hkrpg", GENSHIN = "com.miHoYo.Yuanshen";
    private ProfileCopiesTest.Frame frame(String pkg, int index, boolean sdk, int target) {
        return new ProfileCopiesTest.Frame(pkg, "native", "#", index, sdk,
                List.of(new ProfileCopiesTest.Triple(60, 45, target)), List.of(), Map.of(), -1, -1, 0, 0);
    }
    @Test public void emptyDonorResolvesSameGameNativeProfileBeforeConservativeFallback() throws Throwable {
        var genshin = frame(GENSHIN, 3, false, 90);
        var hsr = frame(CN_HSR, 29, false, 90);
        var result = NativeProfileResolver.resolve(List.of(genshin, hsr), true, HSR, "", pkg -> true);
        assertSame(hsr, result.profile());
        assertEquals(CN_HSR, result.donor());
        assertEquals(29, ((ProfileCopiesTest.Frame) result.profile()).f());
        assertSame(genshin, NativeProfileResolver.resolve(List.of(genshin), true, HSR, "", pkg -> true).profile());
    }
    @Test public void autoRequiresNativeConservativeNonSdkValuesAndOriginalEligibility() throws Throwable {
        var hsr = frame(CN_HSR, 29, true, 90);
        var genshin = frame(GENSHIN, 3, false, 90);
        assertSame(genshin, NativeProfileResolver.resolve(List.of(hsr, genshin), true, HSR, "", pkg -> true).profile());
        assertNull(NativeProfileResolver.resolve(List.of(frame(GENSHIN, 3, false, 144)), true, "other", "", pkg -> true).profile());
        assertNull(NativeProfileResolver.resolve(List.of(genshin), true, "other", "", pkg -> false).profile());
    }
    @Test public void explicitOverrideNeverFallsBackAndRetainsItsRealModes() throws Throwable {
        var genshin = frame(GENSHIN, 3, true, 144);
        var rows = List.of(genshin);
        assertSame(genshin, NativeProfileResolver.resolve(rows, true, HSR, GENSHIN, pkg -> true).profile());
        assertNull(NativeProfileResolver.resolve(rows, true, HSR, "missing.pkg", pkg -> true).profile());
    }
    @Test public void nativeTargetPreventsMappingAndSrUsesOnlyActualCfProfile() throws Throwable {
        var target = frame(HSR, 99, false, 90);
        assertEquals("native_target", NativeProfileResolver.resolve(List.of(target), true, HSR, "missing.pkg", pkg -> true).reason());
        var cf = new ProfileCopiesTest.Sr("com.tencent.tmgp.cf", "native", "#", 1, 0, 0, 0, 0,
                46, 42, 47, 43, -1, 0, 0, 0, "");
        assertSame(cf, NativeProfileResolver.resolve(List.of(cf), false, HSR, "", pkg -> true).profile());
        assertNull(NativeProfileResolver.resolve(List.of(), false, HSR, "", pkg -> true).profile());
    }
    @Test public void replacementSourceProducesReplacementProfileAndMissingSourceWithdraws() throws Throwable {
        var first = frame(GENSHIN, 3, false, 90);
        var replacement = frame(GENSHIN, 3, false, 90);
        Object old = NativeProfileResolver.resolve(List.of(first), true, "other", "", pkg -> true).profile();
        Object next = NativeProfileResolver.resolve(List.of(replacement), true, "other", "", pkg -> true).profile();
        assertNotSame(old, next);
        assertFalse(new ProfilePolicy.Ticket("other", 10, 1, old).matches("other", 10, 1, next));
        assertNull(NativeProfileResolver.resolve(List.of(), true, "other", "", pkg -> true).profile());
    }
}
