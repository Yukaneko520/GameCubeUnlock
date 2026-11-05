package com.yuka.gamecube.tools.hooks.backend;

import com.yuka.gamecube.tools.core.AppRule;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class ThermalRatePolicyTest {
    private final String game="com.example.game";
    private BackendPolicy policy(boolean bypass) {
        var rule=AppRule.defaults().withEnabled(true).withFeature("thermalBypass",bypass);
        return new BackendPolicy(new ConfigSnapshot(true,0,false,1,Map.of(game,rule)),true,Map.of(game,10234));
    }
    private DisplayRatePolicy.Window window(String pkg,int uid,boolean focused,boolean on) {
        return new DisplayRatePolicy.Window(pkg,uid,1,focused,true,true,true,false,true,0,on,false);
    }
    @Test public void ignoresOnlyThermalPowerOrNamedThermalFixRate() {
        assertTrue(ThermalRatePolicy.thermal("Power",90050,null));
        assertTrue(ThermalRatePolicy.thermal("FixRate",105050,"Thermal:com.example.game/47"));
        assertFalse(ThermalRatePolicy.thermal("Power",90000,"PowerSave"));
        assertFalse(ThermalRatePolicy.thermal("FixRate",105050,"user-request"));
        assertFalse(ThermalRatePolicy.thermal("AppRequest",90050,"Thermal:test"));
    }
    @Test public void onlySelectedFocusedPrimaryGameIsEligibleEvenWithout144Support() {
        var p=policy(true);
        assertTrue(ThermalRatePolicy.selected(p,window(game,10234,true,true)));
        assertFalse(ThermalRatePolicy.selected(policy(false),window(game,10234,true,true)));
        assertFalse(ThermalRatePolicy.selected(p,window("com.other.game",10234,true,true)));
        assertFalse(ThermalRatePolicy.selected(p,window(game,10235,true,true)));
        assertFalse(ThermalRatePolicy.selected(p,window(game,10234,false,true)));
        assertFalse(ThermalRatePolicy.selected(p,window(game,10234,true,false)));
        assertEquals(DisplayRatePolicy.Selection.closed(),DisplayRatePolicy.select(p));
    }
    @Test public void otherAppVotesAndPidReuseArePreserved() {
        assertTrue(ThermalRatePolicy.subject("Thermal:"+game+"/47",game,123,10234,game,123,10234));
        assertTrue(ThermalRatePolicy.subject("Thermal",null,0,0,game,123,10234));
        assertFalse(ThermalRatePolicy.subject("Thermal:com.other.game/47",null,0,0,game,123,10234));
        assertFalse(ThermalRatePolicy.subject("Thermal",game,456,10234,game,123,10234));
        assertFalse(ThermalRatePolicy.subject("Thermal","com.other.game",123,10235,game,123,10234));
        assertFalse(ThermalRatePolicy.subject("Thermal",null,0,0,game,0,10234));
    }
    @Test public void toggleOrIdentityRevocationTriggersNormalArbitrationAgain() {
        assertTrue(ThermalRatePolicy.changed(policy(true),policy(false)));
        assertTrue(ThermalRatePolicy.changed(policy(false),policy(true)));
        assertFalse(ThermalRatePolicy.changed(policy(true),policy(true)));
        assertTrue(ThermalRatePolicy.changed(policy(true),BackendPolicy.closed()));
    }
}
