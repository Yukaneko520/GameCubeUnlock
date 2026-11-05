package com.yuka.gamecube.tools.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class ThermalRuleTest {
    private ConfigSnapshot parse(String rule) throws Exception {
        return ConfigSnapshot.parse("{\"schema\":1,\"enabled\":true,\"fpsMode\":0,\"hideGameCenter\":false,\"revision\":1,\"apps\":{\"com.example.game\":"+rule+"}}");
    }
    @Test public void missingFlagIsOffAndRoundTripPreservesExplicitOptIn() throws Exception {
        var old=parse("{\"enabled\":true}");
        assertFalse(old.apps.get("com.example.game").has("thermalBypass"));
        var opted=parse("{\"enabled\":true,\"thermalBypass\":true}");
        assertTrue(opted.apps.get("com.example.game").has("thermalBypass"));
        assertEquals(opted.toJson(),ConfigSnapshot.parse(opted.toJson()).toJson());
    }
    @Test public void editingOtherFieldsPreservesOptInAndDirtyState() throws Exception {
        var rule=parse("{\"thermalBypass\":true}").apps.get("com.example.game");
        assertTrue(rule.withEnabled(true).withDonor("sr","com.example.donor")
                .withTuning(GraphicsTuning.defaults()).withFeature("frame",true).has("thermalBypass"));
        assertNotEquals(rule,AppRule.defaults());
        assertEquals(rule,AppRule.defaults().withFeature("thermalBypass",true));
        assertEquals(AppRule.defaults(),rule.withFeature("thermalBypass",false));
    }
    @Test public void invalidFlagIsRejected() throws Exception {
        assertThrows(Exception.class,()->parse("{\"thermalBypass\":\"true\"}"));
    }
}
