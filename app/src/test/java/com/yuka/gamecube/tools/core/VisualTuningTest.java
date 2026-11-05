package com.yuka.gamecube.tools.core;

import org.junit.Test;
import org.json.JSONObject;
import static org.junit.Assert.*;

public class VisualTuningTest {
    @Test public void oldConfigurationRemainsByteCompatibleUntilVisualIsChanged() throws Exception {
        AppRule r=AppRule.defaults();assertFalse(r.json().has("visual"));
        assertEquals(VisualTuning.defaults(),AppRule.parse(r.json()).visual);
    }
    @Test public void everyExistingRuleCopyPreservesVisualSettings() throws Exception {
        var value=new VisualTuning(true,0,6,2,17,12,240,60,25,272);
        AppRule r=AppRule.defaults().withVisual(value);
        for(var copy:new AppRule[]{r.withEnabled(true),r.withFeature("audio",true),r.withDonor("sr","com.example.game"),
                r.withTuning(GraphicsTuning.defaults()),r.withBenchmarkMode(1),AppRule.parse(r.json())})
            assertEquals(value,copy.visual);
        assertTrue(r.has("visual"));assertEquals(value.withEnabled(false),r.withFeature("visual",false).visual);
    }
    @Test public void hostileImportedValuesAreRejected() throws Exception {
        JSONObject o=AppRule.defaults().withFeature("visual",true).json();
        o.getJSONObject("visual").put("hue",91);
        assertThrows(IllegalArgumentException.class,()->AppRule.parse(o));
        o.getJSONObject("visual").put("hue","10");
        assertThrows(org.json.JSONException.class,()->AppRule.parse(o));
        assertThrows(IllegalArgumentException.class,()->new VisualTuning(true,2,-1,0,13,0,128,50,0,271));
    }
}
