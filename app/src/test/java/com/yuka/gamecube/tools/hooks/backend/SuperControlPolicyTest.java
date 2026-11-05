package com.yuka.gamecube.tools.hooks.backend;

import com.yuka.gamecube.tools.core.AppRule;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
import org.junit.Test;
import java.util.Map;
import static org.junit.Assert.*;

public class SuperControlPolicyTest {
    private static final String PKG="com.example.game";
    private BackendPolicy policy(boolean verified,int uid,boolean master,boolean enabled,String feature) {
        var rule=AppRule.defaults().withEnabled(enabled).withFeature(feature,true);
        return new BackendPolicy(new ConfigSnapshot(master,0,false,12,Map.of(PKG,rule)),verified,Map.of(PKG,uid));
    }
    @Test public void selectionRequiresIdentityInstallationPrimaryUserAndExactFeature() {
        var p=policy(true,10123,true,true,"superTouch");
        assertTrue(p.control(PKG,"superTouch"));
        for(String feature:new String[]{"superGyro","touch","sr","",null}) assertFalse(p.control(PKG,feature));
        for(String pkg:new String[]{null,"",PKG+".other","android","com.vivo.gamecube","com.yuka.gamecube.tools"})
            assertFalse(p.control(pkg,"superTouch"));
        for(int uid:new int[]{0,1000,9999,100000,110123}) assertFalse(policy(true,uid,true,true,"superTouch").control(PKG,"superTouch"));
        assertFalse(policy(false,10123,true,true,"superTouch").control(PKG,"superTouch"));
        assertFalse(policy(true,10123,false,true,"superTouch").control(PKG,"superTouch"));
        assertFalse(policy(true,10123,true,false,"superTouch").control(PKG,"superTouch"));
        assertFalse(p.without(PKG).control(PKG,"superTouch"));
        assertFalse(p.without("com.vivo.gamecube").control(PKG,"superTouch"));
    }
    @Test public void gyroAndTouchAreIndependentPermissions() {
        var p=policy(true,10123,true,true,"superGyro");
        assertTrue(p.control(PKG,"superGyro"));assertFalse(p.control(PKG,"superTouch"));
    }
    @Test public void gyroWhitelistCannotSpillIntoAnotherForegroundUidOrScreenOff() {
        var p=policy(true,10123,true,true,"superGyro");
        assertTrue(p.controlSession(PKG,"superGyro",PKG,10123,true));
        assertFalse(p.controlSession(PKG,"superGyro",PKG+".other",10123,true));
        assertFalse(p.controlSession(PKG,"superGyro",PKG,10124,true));
        assertFalse(p.controlSession(PKG,"superGyro",PKG,null,true));
        assertFalse(p.controlSession(PKG,"superGyro",PKG,10123,false));
        assertFalse(p.controlSession(PKG,"superTouch",PKG,10123,true));
        assertFalse(p.without(PKG).controlSession(PKG,"superGyro",PKG,10123,true));
    }
    @Test public void touchNeedsOriginalExplicitSwitchAndBoundPidUidNotJustVisibleUi() {
        var p=policy(true,10123,true,true,"superTouch");
        assertTrue(SuperTouchState.eligible(p,PKG,10123,true,1,true,true));
        for(Object value:new Object[]{null,0,2,true,"1",1L})
            assertFalse(SuperTouchState.eligible(p,PKG,10123,true,value,true,true));
        assertFalse(SuperTouchState.eligible(p,PKG,10124,true,1,true,true));
        assertFalse(SuperTouchState.eligible(p,PKG,10123,false,1,true,true));
        assertFalse(SuperTouchState.eligible(p,PKG,10123,true,1,false,true));
        assertFalse(SuperTouchState.eligible(p,PKG,10123,true,1,true,false));
        assertFalse(SuperTouchState.eligible(p.without(PKG),PKG,10123,true,1,true,true));
    }
    @Test public void originalSupportedPacingIsNotRetuned() {
        assertFalse(SuperTouchState.needsExtension(true,true,0x10001));
        assertFalse(SuperTouchState.needsExtension(false,false,0));
        assertTrue(SuperTouchState.needsExtension(true,false,0));
        assertTrue(SuperTouchState.needsExtension(true,true,0));
    }
    @Test public void foregroundEventMustMatchLiveUidAndNegativeUidIsNotAnInternalSentinel() {
        assertTrue(SuperTouchState.matchesEvent(10123,10123));
        assertTrue(SuperTouchState.matchesEvent(10123,null));
        assertFalse(SuperTouchState.matchesEvent(10123,-1));
        assertFalse(SuperTouchState.matchesEvent(10123,10124));
        assertFalse(SuperTouchState.matchesEvent(10123,110123));
    }
    @Test public void packedOemFlagsKeepOtherClientsBitsAndEarlierEnabledBit() {
        assertEquals((0x82f&0xffff)|(0x82f<<16),SuperTouchState.FLAGS);
        int other=0x40004000;
        assertEquals(other,SuperTouchState.flags(other)&other);
        assertEquals(SuperTouchState.FLAGS,SuperTouchState.flags(0));
        assertEquals(0x40000001,SuperTouchState.base(0x40000000));
        assertEquals(1,SuperTouchState.base(0));
    }
    @Test public void unknownProcUidNeedsValidPrimaryUserObserverEvidence() {
        assertEquals(10123,SuperTouchState.resolveUid(-1,10123));
        assertEquals(-1,SuperTouchState.resolveUid(-1,null));
        for(int invalid:new int[]{-1,0,1000,9999,100000,110123})
            assertEquals(-1,SuperTouchState.resolveUid(-1,invalid));
    }
    @Test public void existingProcUidIsNeverOverriddenByObserverEvidence() {
        for(int actual:new int[]{0,1000,10124,110123}) {
            assertEquals(actual,SuperTouchState.resolveUid(actual,10123));
            assertFalse(SuperTouchState.matchesEvent(SuperTouchState.resolveUid(actual,10123),10123));
        }
        assertEquals(10123,SuperTouchState.resolveUid(10123,null));
    }
}
