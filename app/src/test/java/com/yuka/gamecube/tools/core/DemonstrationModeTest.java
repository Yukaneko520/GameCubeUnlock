package com.yuka.gamecube.tools.core;

import com.yuka.gamecube.tools.compat.RomFeatures;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class DemonstrationModeTest {
    private final String pkg="com.example.game";
    private ConfigSnapshot selected() {
        return ConfigSnapshot.defaults().withGlobal(true,0,false).withApp(pkg,AppRule.defaults().withEnabled(true));
    }
    @Test public void oldSnapshotsDefaultOffAndWrongTypesAreRejected() throws Exception {
        JSONObject old=new JSONObject(selected().toJson());old.remove("demonstrationMode");
        assertFalse(ConfigSnapshot.parse(old.toString()).demonstrationMode);
        old.put("demonstrationMode","true");
        assertThrows(Exception.class,()->ConfigSnapshot.parse(old.toString()));
    }
    @Test public void demoIsSelectedAndRevocableWithoutChangingSavedChoices() throws Exception {
        var original=selected();var demo=ConfigSnapshot.parse(original.withDemonstration(true).toJson());
        assertTrue(demo.demonstrates(pkg));assertFalse(demo.demonstrates(pkg+".other"));
        assertFalse(demo.withGlobal(false,0,false).demonstrates(pkg));
        assertFalse(demo.withApp(pkg,demo.rule(pkg).withEnabled(false)).demonstrates(pkg));
        assertEquals(original.rule(pkg),demo.withDemonstration(false).rule(pkg));
        assertFalse(demo.allows(pkg,"sr"));assertFalse(demo.allows(pkg,"thermalBypass"));
        assertFalse(demo.allows(pkg,"monsterPlus"));assertFalse(demo.force144Global);
    }
    @Test public void everyCopyAndRomFilterPreservesTheFlag() {
        var demo=selected().withDemonstration(true);
        assertTrue(demo.withGlobal(true,1,true).demonstrationMode);
        assertTrue(demo.with144Global(true).demonstrationMode);
        assertTrue(demo.withRevision(100).demonstrationMode);
        assertTrue(demo.withApp(pkg,demo.rule(pkg).withFeature("force144",true)).demonstrationMode);
        for(int sdk:new int[]{34,35,36}) assertTrue(RomFeatures.effective(demo,sdk).demonstrates(pkg));
    }
    @Test public void assistantProjectionReusesUnlocksWithoutEnablingPerformanceOrInjection() {
        var saved=selected().withDemonstration(true);
        var effective=com.yuka.gamecube.tools.hooks.demo.DemonstrationPolicy.assistant(saved);
        for(String feature:new String[]{"touch","gyro","superTouch","superGyro","audio","frame","sr"}) {
            assertTrue(effective.allows(pkg,feature));assertFalse(saved.allows(pkg,feature));
        }
        assertFalse(effective.allows(pkg,"thermalBypass"));assertFalse(effective.allows(pkg,"monsterPlus"));
        assertFalse(effective.allows(pkg,"benchmark"));assertFalse(effective.allows(pkg,"force144"));
        assertEquals(saved.rule(pkg).tuning,effective.rule(pkg).tuning);
        assertEquals(saved.rule(pkg).visual,effective.rule(pkg).visual);
        assertSame(saved.withDemonstration(false).apps.get(pkg),saved.rule(pkg));
        var off=saved.withDemonstration(false);
        assertSame(off,com.yuka.gamecube.tools.hooks.demo.DemonstrationPolicy.assistant(off));
        var disabled=saved.withApp(pkg,saved.rule(pkg).withEnabled(false));
        assertFalse(com.yuka.gamecube.tools.hooks.demo.DemonstrationPolicy.assistant(disabled).rule(pkg).touch);
    }
    @Test public void hiddenMenuItemsBecomeVisibleWithoutDuplicatesOrChangingSavedOrder() {
        var shown=java.util.List.of("voice_mod","sr");var hidden=java.util.List.of("voice_mod","q3");
        assertEquals(java.util.List.of("voice_mod","sr","q3"),
                com.yuka.gamecube.tools.hooks.demo.DemonstrationPolicy.visibleItems(shown,hidden,value->value));
        assertEquals(java.util.List.of("voice_mod","q3"),hidden);
    }
}
