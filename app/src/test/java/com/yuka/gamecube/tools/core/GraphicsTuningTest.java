package com.yuka.gamecube.tools.core;

import org.junit.Test;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class GraphicsTuningTest {
    private GraphicsTuning custom() {
        return new GraphicsTuning(true,GraphicsTuning.parseTuples("60/48/144\n120/60/120"),
                GraphicsTuning.parseTuples("60/30/60"),true,4,1260,0,1.75,-32,16);
    }
    @Test public void allRuleCopyAndSnapshotPathsPreserveTuning() throws Exception {
        var values=custom();
        AppRule rule=AppRule.defaults().withTuning(values).withEnabled(true).withFeature("frame",true)
                .withFeature("sr",true).withFeature("force144",true).withDonor("frame","com.test.source");
        var snapshot=ConfigSnapshot.defaults().withGlobal(true,1,true).withApp("com.test.game",rule).with144Global(true).withRevision(99);
        var parsed=ConfigSnapshot.parse(snapshot.toJson());
        assertEquals(values,parsed.rule("com.test.game").tuning);
        assertTrue(parsed.allows("com.test.game","frame")); assertTrue(parsed.allows("com.test.game","sr"));
        assertTrue(parsed.force144Global); assertTrue(parsed.rule("com.test.game").force144);
    }
    @Test public void absentTuningIsOffAndDefaultJsonDoesNotInflateOldRules() throws Exception {
        assertFalse(AppRule.defaults().json().has("tuning"));
        assertEquals(GraphicsTuning.defaults(),AppRule.parse(new JSONObject()).tuning);
        assertFalse(GraphicsTuning.defaults().frameCustom()); assertFalse(GraphicsTuning.defaults().srCustom());
        assertThrows(Exception.class,()->AppRule.parse(new JSONObject().put("tuning","invalid")));
    }
    @Test public void tuplesAreBoundedUniqueOrderedAndImmutable() {
        var values=GraphicsTuning.parseTuples("60 / 45 / 90; \n120,60,144\n");
        assertEquals(2,values.size()); assertEquals(144,values.get(1).outputFps());
        assertEquals("60/45/90\n120/60/144",GraphicsTuning.formatTuples(values));
        assertThrows(UnsupportedOperationException.class,()->values.clear());
        for(String bad:List.of("","60/45","60/70/90","60/30/240","60/45/30","0/1/90","60/45/90;60/30/60"))
            assertThrows("Must reject "+bad,IllegalArgumentException.class,()->GraphicsTuning.parseTuples(bad));
        var many=new ArrayList<GraphicsTuning.FrameValue>();
        for(int n=1;n<=13;n++) many.add(new GraphicsTuning.FrameValue(n,n,144));
        assertThrows(IllegalArgumentException.class,()->new GraphicsTuning(true,many,values,false,0,0,-1,2.5,-40,20));
    }
    @Test public void numericTypesRangesAndNonfiniteValuesCannotReachNative() throws Exception {
        JSONObject json=new JSONObject().put("tuning",custom().json());
        json.getJSONObject("tuning").put("algorithm",1.0);
        assertThrows(Exception.class,()->GraphicsTuning.parse(json));
        json.getJSONObject("tuning").put("algorithm",4).put("sharpness","2.5");
        assertThrows(Exception.class,()->GraphicsTuning.parse(json));
        var d=GraphicsTuning.defaults();
        for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1,11})
            assertThrows(IllegalArgumentException.class,()->new GraphicsTuning(false,d.boost(),d.power(),true,1,0,-1,bad,-40,20));
        assertThrows(IllegalArgumentException.class,()->new GraphicsTuning(false,d.boost(),d.power(),true,7,0,-1,2.5,-40,20));
        assertThrows(IllegalArgumentException.class,()->new GraphicsTuning(false,d.boost(),d.power(),true,1,4001,-1,2.5,-40,20));
    }
    @Test public void changesAreSeparatedByFeatureAndDisabledValuesDoNotRetireOtherSessions() {
        var d=GraphicsTuning.defaults();
        var inactive=new GraphicsTuning(false,custom().boost(),d.power(),false,6,1440,1,4,-12,10);
        assertTrue(d.sameFrame(inactive)); assertTrue(d.sameSr(inactive));
        var srOnly=new GraphicsTuning(false,d.boost(),d.power(),true,6,1440,1,4,-12,10);
        assertTrue(d.sameFrame(srOnly)); assertFalse(d.sameSr(srOnly));
        assertFalse(d.sameFrame(custom()));
    }
}
