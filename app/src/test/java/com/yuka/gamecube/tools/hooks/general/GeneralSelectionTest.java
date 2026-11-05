package com.yuka.gamecube.tools.hooks.general;

import static org.junit.Assert.*;
import com.yuka.gamecube.tools.core.AppRule;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
import java.util.Map;
import org.junit.Test;

public class GeneralSelectionTest {
    private static AppRule touchOnly() {
        return new AppRule(true, true, false, false, false, false, false, false, "", "", "");
    }

    @Test public void optInUsesExactCaseSensitivePackage() {
        ConfigSnapshot snapshot = new ConfigSnapshot(true, 0, false, 1,
                Map.of("com.example.game", touchOnly()));
        assertTrue(snapshot.allows("com.example.game", "touch"));
        for (String other : new String[]{"com.example.game.beta", "Com.example.game", "com.example", ""})
            assertFalse(snapshot.allows(other, "touch"));
    }

    @Test public void featureBitsStayIndependent() {
        ConfigSnapshot snapshot = new ConfigSnapshot(true, 0, false, 1,
                Map.of("com.example.game", touchOnly()));
        for (String feature : new String[]{"gyro", "superTouch", "superGyro", "audio", "frame", "sr"})
            assertFalse(snapshot.allows("com.example.game", feature));
    }

    @Test public void capturedGenerationDoesNotFollowNewSnapshot() {
        ConfigSnapshot captured = new ConfigSnapshot(true, 0, false, 1,
                Map.of("com.example.game", touchOnly()));
        ConfigSnapshot next = captured.withGlobal(false, 0, false);
        assertTrue(captured.allows("com.example.game", "touch"));
        assertFalse(next.allows("com.example.game", "touch"));
        assertEquals(1, captured.revision);
        assertEquals(2, next.revision);
    }

    @Test public void auxiliaryGyroSamplerIsSuppressedOnlyForTheSelectedOrdinaryGyroRule() {
        AppRule gyro=touchOnly().withFeature("gyro",true);
        ConfigSnapshot snapshot=new ConfigSnapshot(true,0,false,1,Map.of("com.example.game",gyro));
        assertTrue(HookPolicy.suppressGyroSampler(snapshot,"com.example.game"));
        assertFalse(HookPolicy.suppressGyroSampler(snapshot,"com.example.game.beta"));
        assertFalse(HookPolicy.suppressGyroSampler(snapshot,"Com.example.game"));
    }

    @Test public void auxiliaryGyroSamplerReturnsToStockWhenMasterRuleOrOrdinaryGyroIsOff() {
        AppRule gyro=touchOnly().withFeature("gyro",true);
        ConfigSnapshot snapshot=new ConfigSnapshot(true,0,false,1,Map.of("com.example.game",gyro));
        assertFalse(HookPolicy.suppressGyroSampler(snapshot.withGlobal(false,0,false),"com.example.game"));
        assertFalse(HookPolicy.suppressGyroSampler(new ConfigSnapshot(true,0,false,2,
                Map.of("com.example.game",gyro.withEnabled(false))),"com.example.game"));
        assertFalse(HookPolicy.suppressGyroSampler(new ConfigSnapshot(true,0,false,3,
                Map.of("com.example.game",gyro.withFeature("gyro",false))),"com.example.game"));
    }

    @Test public void superGyroDoesNotAuthorizeSuppressingOrdinaryGyroSampling() {
        ConfigSnapshot snapshot=new ConfigSnapshot(true,0,false,1,
                Map.of("com.example.game",touchOnly().withFeature("superGyro",true)));
        assertFalse(HookPolicy.suppressGyroSampler(snapshot,"com.example.game"));
    }
}
