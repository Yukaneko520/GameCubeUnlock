package com.yuka.gamecube.tools.compat;

import com.yuka.gamecube.tools.core.*;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class RomFeaturesTest {
    @Test public void os4DisablesOnlyAbsentPd2352Features() {
        for(String feature:new String[]{"fps","superGyro","sr","nativeSr"})
            assertFalse(feature,RomFeatures.available(34,feature));
        for(String feature:new String[]{"touch","gyro","superTouch","audio","frame","force144",
                "hideGameCenter","monsterPlus","thermalBypass","benchmark"})
            assertTrue(feature,RomFeatures.available(34,feature));
        assertFalse(RomFeatures.available(33,"touch"));
        assertFalse(RomFeatures.available(37,"touch"));
        assertFalse(RomFeatures.available(35,"unknown"));
    }
    @Test public void os5AndOs6KeepExistingFeatures() {
        for(int sdk:new int[]{35,36}) for(String feature:new String[]{"fps","superGyro","sr","nativeSr"})
            assertTrue(RomFeatures.available(sdk,feature));
    }
    @Test public void savedOrImportedSettingsCannotReenableMissingFeaturesAndAreNotDestroyed() {
        var rule=new AppRule(true,true,true,true,true,true,true,true,"","","");
        var t=rule.tuning;
        rule=rule.withTuning(new GraphicsTuning(true,t.boost(),t.power(),true,2,1080,-1,3,-40,20));
        var saved=new ConfigSnapshot(true,1,true,10,Map.of("com.example.game",rule),true);
        var actual=RomFeatures.effective(saved,34);
        assertEquals(0,actual.fpsMode);
        assertFalse(actual.rule("com.example.game").superGyro);
        assertFalse(actual.rule("com.example.game").sr);
        assertFalse(actual.rule("com.example.game").tuning.srCustom());
        assertTrue(actual.rule("com.example.game").tuning.frameCustom());
        assertTrue(actual.rule("com.example.game").frame);
        assertTrue(actual.rule("com.example.game").superTouch);
        assertTrue(actual.force144Global);
        assertEquals(saved.revision,actual.revision);
        assertTrue(saved.rule("com.example.game").sr);
        assertTrue(saved.rule("com.example.game").tuning.srCustom());
        assertEquals(saved.toJson(),RomFeatures.effective(saved,35).toJson());
        assertEquals(saved.toJson(),RomFeatures.effective(saved,36).toJson());
        assertEquals(actual.toJson(),RomFeatures.effective(actual,34).toJson());
    }
}
