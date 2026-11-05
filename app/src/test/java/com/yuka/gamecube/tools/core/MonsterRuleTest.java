package com.yuka.gamecube.tools.core;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class MonsterRuleTest {
    @Test public void oldConfigurationsStayOffAndRejectMalformedFlags() throws Exception {
        AppRule old=AppRule.parse(new JSONObject("{\"enabled\":true,\"sr\":true}"));
        assertFalse(old.monsterPlus);assertTrue(old.sr);
        assertThrows(Exception.class,()->AppRule.parse(new JSONObject("{\"monsterPlus\":1}")));
        assertThrows(Exception.class,()->AppRule.parse(new JSONObject("{\"monsterPlus\":\"true\"}")));
    }
    @Test public void enabledRuleSurvivesJsonAndAllExistingCopyOperations() throws Exception {
        AppRule selected=AppRule.defaults().withEnabled(true).withFeature("monsterPlus",true);
        assertEquals(selected,AppRule.parse(selected.json()));
        assertEquals(selected.hashCode(),AppRule.parse(selected.json()).hashCode());
        assertTrue(selected.withEnabled(false).monsterPlus);
        assertTrue(selected.withDonor("sr","com.test.donor").monsterPlus);
        assertTrue(selected.withTuning(GraphicsTuning.defaults()).monsterPlus);
        for(String feature:new String[]{"touch","gyro","superTouch","superGyro","audio","frame","sr","force144"})
            assertTrue(feature,selected.withFeature(feature,true).monsterPlus);
        assertNotEquals(selected,selected.withFeature("monsterPlus",false));
        ConfigSnapshot config=ConfigSnapshot.defaults().withGlobal(true,0,false).withApp("com.test.game",selected);
        assertTrue(ConfigSnapshot.parse(config.toJson()).allows("com.test.game","monsterPlus"));
    }
}
