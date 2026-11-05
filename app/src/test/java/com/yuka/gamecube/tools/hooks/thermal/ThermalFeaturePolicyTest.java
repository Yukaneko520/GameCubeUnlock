package com.yuka.gamecube.tools.hooks.thermal;

import com.yuka.gamecube.tools.core.AppRule;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class ThermalFeaturePolicyTest {
    private final String pkg="com.example.game";
    private ConfigSnapshot config(boolean master,boolean app,boolean bypass) {
        return new ConfigSnapshot(master,0,false,1,Map.of(pkg,AppRule.defaults().withEnabled(app).withFeature("thermalBypass",bypass)));
    }
    @Test public void onlyOptedPrimaryLiveSessionIsAllowed() {
        assertTrue(ThermalFeaturePolicy.session(config(true,true,true),pkg,123,10234,10234));
        assertFalse(ThermalFeaturePolicy.session(config(false,true,true),pkg,123,10234,10234));
        assertFalse(ThermalFeaturePolicy.session(config(true,false,true),pkg,123,10234,10234));
        assertFalse(ThermalFeaturePolicy.session(config(true,true,false),pkg,123,10234,10234));
        assertFalse(ThermalFeaturePolicy.session(config(true,true,true),"com.other.game",123,10234,10234));
        assertFalse(ThermalFeaturePolicy.session(config(true,true,true),pkg,-1,10234,10234));
        assertFalse(ThermalFeaturePolicy.session(config(true,true,true),pkg,123,10234,10235));
        assertFalse(ThermalFeaturePolicy.session(config(true,true,true),pkg,123,1010234,1010234));
    }
    @Test public void bypassNeverClearsOtherRecordingRestrictions() {
        for(boolean hot:new boolean[]{false,true}) for(boolean conflict:new boolean[]{false,true})
            for(boolean power:new boolean[]{false,true}) for(boolean screen:new boolean[]{false,true}) {
                assertEquals(!(conflict||power||screen),ThermalFeaturePolicy.recording(hot,conflict,power,screen,true));
                assertEquals(!(hot||conflict||power||screen),ThermalFeaturePolicy.recording(hot,conflict,power,screen,false));
            }
    }
    @Test public void onlyThermalAutoplayStopCommandsAreSuppressed() {
        for(int command:new int[]{1,2,4,5,8,16,32,64,128,256}) {
            assertEquals(command==4,ThermalFeaturePolicy.thermalStop(command,true,true));
            assertEquals(command==256,ThermalFeaturePolicy.thermalStop(command,false,true));
            assertFalse(ThermalFeaturePolicy.thermalStop(command,true,false));
        }
    }
    @Test public void inlineAutoplayKeepsLowBatteryAndNormalEntryPath() {
        assertTrue(ThermalFeaturePolicy.legacyAutoplayEntry(42,50,0,true));
        assertTrue(ThermalFeaturePolicy.legacyAutoplayEntry(42,5,1,true));
        assertFalse(ThermalFeaturePolicy.legacyAutoplayEntry(42,5,0,true));
        assertFalse(ThermalFeaturePolicy.legacyAutoplayEntry(41,50,0,true));
        assertFalse(ThermalFeaturePolicy.legacyAutoplayEntry(42,50,0,false));
    }
    @Test public void doublePlayKeepsBatteryPowerAndTimeoutRestrictions() {
        assertTrue(ThermalFeaturePolicy.doublePlayEntry(true,false,true));
        assertFalse(ThermalFeaturePolicy.doublePlayEntry(false,false,true));
        assertFalse(ThermalFeaturePolicy.doublePlayEntry(true,true,true));
        assertFalse(ThermalFeaturePolicy.doublePlayEntry(true,false,false));
        for(int minute=0;minute<=30;minute++) {
            var step=ThermalFeaturePolicy.doublePlayTick(minute);
            assertEquals(minute+1,step.count());assertEquals(minute>=30,step.expired());
        }
    }
    @Test public void autoplayCannotOpenAfterRevocationOrProcessReplacement() {
        for(boolean initialized:new boolean[]{false,true}) for(boolean current:new boolean[]{false,true})
            for(boolean samePackage:new boolean[]{false,true}) for(boolean samePid:new boolean[]{false,true})
                assertEquals(initialized && current && samePackage && samePid,
                        ThermalFeaturePolicy.autoplayReady(initialized,current,samePackage,samePid));
    }
}
