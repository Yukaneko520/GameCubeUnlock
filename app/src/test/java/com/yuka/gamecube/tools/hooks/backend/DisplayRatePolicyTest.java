package com.yuka.gamecube.tools.hooks.backend;

import com.yuka.gamecube.tools.core.AppRule;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
import java.util.Map;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class DisplayRatePolicyTest {
    @Test public void withdrawingOneGameIsObservedWhileOtherRulesRemainEnabled() {
        var before=new DisplayRatePolicy.Selection(false,Map.of("flar2.devcheck",10398,"com.example.other",10400));
        var after=new DisplayRatePolicy.Selection(false,Map.of("com.example.other",10400));
        assertTrue(DisplayRatePolicy.withdraws(before,after));
        assertFalse(DisplayRatePolicy.withdraws(after,before));
    }
    @Test public void globalOffAndReplacedUidAlsoWithdrawSelection() {
        assertTrue(DisplayRatePolicy.withdraws(new DisplayRatePolicy.Selection(true,Map.of()),DisplayRatePolicy.Selection.closed()));
        assertTrue(DisplayRatePolicy.withdraws(new DisplayRatePolicy.Selection(false,Map.of("flar2.devcheck",10398)),
                new DisplayRatePolicy.Selection(false,Map.of("flar2.devcheck",10400))));
    }
    private static final String GAME="com.example.game";
    private static final int UID=10361;
    private static ConfigSnapshot selected() {
        return ConfigSnapshot.defaults().withGlobal(true,0,false)
                .withApp(GAME,AppRule.defaults().withEnabled(true).withFeature("force144",true));
    }
    private static DisplayRatePolicy.Selection select(ConfigSnapshot config,boolean verified) {
        return DisplayRatePolicy.select(new BackendPolicy(config,verified,Map.of(GAME,UID)));
    }
    private static DisplayRatePolicy.Window window(int type,int display,boolean focused,boolean fullscreen,
                                                 boolean screen,boolean hardware) {
        return new DisplayRatePolicy.Window(GAME,UID,type,focused,true,true,fullscreen,false,true,display,screen,hardware);
    }
    @Test public void additiveFieldsRoundTripAndEveryCopyPreservesThem() throws Exception {
        ConfigSnapshot config=selected().with144Global(true);
        ConfigSnapshot copy=config.withRevision(42).withGlobal(true,2,true)
                .withApp(GAME,config.rule(GAME).withDonor("audio","com.miHoYo.Yuanshen")
                        .withFeature("audio",true).withEnabled(true));
        ConfigSnapshot parsed=ConfigSnapshot.parse(copy.toJson());
        assertTrue(parsed.force144Global); assertTrue(parsed.rule(GAME).force144);
        assertTrue(parsed.rule(GAME).audio); assertEquals(42+2,parsed.revision);
        assertFalse(parsed.with144Global(false).force144Global);
        assertFalse(parsed.rule(GAME).withFeature("force144",false).force144);
    }
    @Test public void oldConfigurationsDefaultOffAndWrongTypesAreRejected() throws Exception {
        JSONObject old=new JSONObject(selected().toJson()); old.remove("force144Global");
        old.getJSONObject("apps").getJSONObject(GAME).remove("force144");
        ConfigSnapshot parsed=ConfigSnapshot.parse(old.toString());
        assertFalse(parsed.force144Global); assertFalse(parsed.rule(GAME).force144);
        old.put("force144Global",144);
        assertThrows(Exception.class,()->ConfigSnapshot.parse(old.toString()));
        old.put("force144Global",false).getJSONObject("apps").getJSONObject(GAME).put("force144","true");
        assertThrows(Exception.class,()->ConfigSnapshot.parse(old.toString()));
    }
    @Test public void perAppRequiresVerifiedInstallationAndEnabledRuleNotSr() {
        var config=selected(); var policy=select(config,true);
        assertFalse(config.rule(GAME).sr);
        assertEquals("requested",DisplayRatePolicy.reason(policy,window(1,0,true,true,true,true)));
        assertFalse(select(config,false).wants(GAME,UID));
        assertFalse(select(config.withGlobal(false,0,false),true).wants(GAME,UID));
        assertFalse(DisplayRatePolicy.select(new BackendPolicy(config,true,Map.of())).wants(GAME,UID));
        assertFalse(policy.wants(GAME,UID+1));
        assertFalse(select(config.withApp(GAME,config.rule(GAME).withEnabled(false)),true).wants(GAME,UID));
    }
    @Test public void globalWorksWithoutRulesButCannotBypassIdentityOrMaster() {
        ConfigSnapshot global=ConfigSnapshot.defaults().withGlobal(true,0,false).with144Global(true);
        assertTrue(select(global,true).wants("com.bbk.launcher2",10050));
        assertFalse(select(global,false).global());
        assertFalse(select(global.withGlobal(false,0,false),true).global());
    }
    @Test public void foregroundWindowAndPhysicalModeGatesRemainRequired() {
        var global=select(selected().with144Global(true),true);
        assertEquals("special_window",DisplayRatePolicy.reason(global,window(2011,0,true,true,true,true)));
        assertEquals("special_window",DisplayRatePolicy.reason(global,window(1,0,true,false,true,true)));
        assertEquals("requested",DisplayRatePolicy.reason(global,window(1,0,false,true,true,true)));
        assertEquals("display_unavailable",DisplayRatePolicy.reason(global,window(1,1,true,true,true,true)));
        assertEquals("display_unavailable",DisplayRatePolicy.reason(global,window(1,0,true,true,false,true)));
        assertEquals("display_unavailable",DisplayRatePolicy.reason(global,window(1,0,true,true,true,false)));
        assertEquals("owner_or_user",DisplayRatePolicy.reason(global,new DisplayRatePolicy.Window(
                GAME,99910361,1,true,true,true,true,false,true,0,true,true)));
        assertEquals("excluded_package",DisplayRatePolicy.reason(global,new DisplayRatePolicy.Window(
                "com.android.systemui",UID,1,true,true,true,true,false,true,0,true,true)));
    }
    @Test public void floatingAndSplitAppsKeepTheSelectionWithoutInputFocus() {
        var policy=select(selected(),true);
        for(int mode:new int[]{1,2,5,6,106}) {
            assertTrue(DisplayRatePolicy.applicationMode(mode));
            assertEquals("requested",DisplayRatePolicy.reason(policy,window(1,0,false,
                    DisplayRatePolicy.applicationMode(mode),true,true)));
        }
        for(int mode:new int[]{0,3,4,99}) assertFalse(DisplayRatePolicy.applicationMode(mode));
        assertEquals("not_foreground",DisplayRatePolicy.reason(policy,new DisplayRatePolicy.Window(
                GAME,UID,1,false,false,true,true,false,true,0,true,true)));
        assertEquals("not_foreground",DisplayRatePolicy.reason(policy,new DisplayRatePolicy.Window(
                GAME,UID,1,true,true,false,true,false,true,0,true,true)));
    }
    @Test public void idleAnimationAndAppCapsUse144OnlyInsideSelectedNormalScenes() {
        for(String scene:new String[]{"Interaction","AppRequest","Animation"}) {
            int base=switch(scene) { case "Interaction"->70000; case "AppRequest"->80000; default->100000; };
            for(int stock:new int[]{60,90,120,144}) {
                assertEquals(144,DisplayRatePolicy.sceneFps(true,scene,base,stock));
                assertEquals(stock,DisplayRatePolicy.sceneFps(false,scene,base,stock));
            }
            assertEquals(0,DisplayRatePolicy.sceneFps(true,scene,base,0));
            assertEquals(-1,DisplayRatePolicy.sceneFps(true,scene,base,-1));
            assertEquals(60,DisplayRatePolicy.sceneFps(true,scene,base+101,60));
        }
    }
    @Test public void thermalPowerFixRateAndUnknownScenesNeverGetOverridden() {
        for(String scene:new String[]{"Power","FixRate","AOD","Unknown"})
            for(int priority:new int[]{70000,80000,90000,90050,100000,105100})
                assertEquals(60,DisplayRatePolicy.sceneFps(true,scene,priority,60));
        assertEquals(60,DisplayRatePolicy.sceneFps(true,"AppRequest",90050,60));
    }
    @Test public void physicalRangeLocksOnlyAnActuallyChosenNormal144ModeAndRestoresOnDisable() {
        for(int switching:new int[]{0,1,2,3}) {
            assertEquals(0,DisplayRatePolicy.modeSwitchType(true,"Animation",100000,144,144,switching));
            assertEquals(switching,DisplayRatePolicy.modeSwitchType(false,"Animation",100000,144,144,switching));
            assertEquals(switching,DisplayRatePolicy.modeSwitchType(true,"AppRequest",80000,120,120,switching));
            assertEquals(switching,DisplayRatePolicy.modeSwitchType(true,"AppRequest",80000,144,120,switching));
            assertEquals(switching,DisplayRatePolicy.modeSwitchType(true,"Power",90050,144,144,switching));
            assertEquals(switching,DisplayRatePolicy.modeSwitchType(true,"FixRate",105100,144,144,switching));
        }
    }
    @Test public void noUnnecessaryRebuildForRevisionFpsAndDonorChanges() {
        var config=selected(); var first=select(config,true);
        assertEquals(first,select(config.withRevision(500).withGlobal(true,2,true),true));
        assertEquals(first,select(config.withApp(GAME,config.rule(GAME).withDonor("sr","com.test.donor")),true));
        assertNotEquals(first,select(config.withApp(GAME,config.rule(GAME).withFeature("force144",false)),true));
        assertNotEquals(first,DisplayRatePolicy.select(new BackendPolicy(config,true,Map.of(GAME,UID+1))));
        assertNotEquals(first,DisplayRatePolicy.select(new BackendPolicy(config,true,Map.of())));
    }
    @Test public void videoAndAnimationCanSelectPrivate144OnlyInsideAuthorizedEvaluation() {
        // Captured 0.1.3 failures: video priority80030 and animation priority100000 map real144 to120.
        assertTrue(DisplayRatePolicy.privateMode(false,true,"AppRequest",80030,144));
        assertTrue(DisplayRatePolicy.privateMode(false,true,"Animation",100000,144));
        assertTrue(DisplayRatePolicy.privateMode(false,true,"Interaction",70030,144));
        assertFalse(DisplayRatePolicy.privateMode(false,false,"AppRequest",80030,144));
        assertFalse(DisplayRatePolicy.privateMode(false,false,"Animation",100000,144));
        for(int fps:new int[]{0,60,90,120}) assertFalse(DisplayRatePolicy.privateMode(false,true,"Animation",100000,fps));
    }
    @Test public void privateModeKeepsNativeSupportAndDoesNotExtendPowerOrUnknownRequests() {
        for(String scene:new String[]{"Power","FixRate","AOD","Unknown"}) {
            assertFalse(DisplayRatePolicy.privateMode(false,true,scene,90050,144));
            assertTrue(DisplayRatePolicy.privateMode(true,false,scene,90050,60));
        }
        assertFalse(DisplayRatePolicy.privateMode(false,true,"AppRequest",90050,144));
        assertFalse(DisplayRatePolicy.privateMode(false,true,"Animation",100101,144));
        assertTrue(DisplayRatePolicy.privateMode(true,false,"AppRequest",80030,120));
    }
}
