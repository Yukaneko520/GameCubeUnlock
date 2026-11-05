package com.yuka.gamecube.tools.hooks.backend;

import org.junit.Test;
import static org.junit.Assert.*;

public class SuperTouchSessionsTest {
    @Test public void latestOriginalForegroundCanSeedAfterConfigurationOrSwitchArrives() {
        var sessions=new SuperTouchSessions();
        assertNull(sessions.foreground());
        sessions.foreground("com.example.game",42,10123,true);
        assertEquals(new SuperTouchSessions.Foreground("com.example.game",42,10123),sessions.foreground());
        sessions.foreground("com.example.launcher",17,10124,false);
        assertEquals(42,sessions.foreground().pid());
    }
    @Test public void backgroundedGameCannotBeReseededAndOlderCallbackCannotClearNewForeground() {
        var sessions=new SuperTouchSessions();
        sessions.foreground("com.example.one",42,10123,true);
        sessions.foreground("com.example.two",43,10124,true);
        sessions.foreground("com.example.one",42,10123,false);
        assertEquals("com.example.two",sessions.foreground().pkg());
        sessions.foreground("com.example.two",43,10124,false);
        assertNull(sessions.foreground());
    }
    @Test public void ownershipRequiresExactPackagePidAndOriginalRowIdentity() {
        var sessions=new SuperTouchSessions();Object row=new Object();
        sessions.claim("com.example.game",42,row);
        assertTrue(sessions.owns("com.example.game",42,row));
        assertFalse(sessions.owns("com.example.other",42,row));
        assertFalse(sessions.owns("com.example.game",43,row));
        assertFalse(sessions.owns("com.example.game",42,new Object()));
        assertFalse(sessions.owns("com.example.game",42,null));
    }
    @Test public void releasingOwnedRecordPreservesForegroundForOffOnWithoutAppRestart() {
        var sessions=new SuperTouchSessions();Object row=new Object();
        sessions.foreground("com.example.game",42,10123,true);
        sessions.claim("com.example.game",42,row);sessions.removed(42);
        assertFalse(sessions.owns("com.example.game",42,row));assertTrue(sessions.pids().isEmpty());
        assertEquals(42,sessions.foreground().pid());
        Object next=new Object();sessions.claim("com.example.game",42,next);
        assertTrue(sessions.owns("com.example.game",42,next));
        assertFalse(sessions.owns("com.example.game",42,row));
    }
    @Test public void invalidOrAbsentRecordsCannotBecomeOwned() {
        var sessions=new SuperTouchSessions();
        sessions.claim(null,42,new Object());sessions.claim("com.example.game",0,new Object());
        sessions.claim("com.example.game",42,null);assertTrue(sessions.pids().isEmpty());
        sessions.foreground("com.example.game",0,10123,true);assertNull(sessions.foreground());
    }
    @Test public void observerUidIsBoundToExactPackagePidAndNativeRowIdentity() {
        var sessions=new SuperTouchSessions();Object row=new Object();
        sessions.observe("com.example.game",42,10123,row);
        assertEquals(Integer.valueOf(10123),sessions.uid("com.example.game",42,row));
        assertNull(sessions.uid("com.example.other",42,row));
        assertNull(sessions.uid("com.example.game",43,row));
        assertNull(sessions.uid("com.example.game",42,new Object()));
        sessions.removed(42);assertNull(sessions.uid("com.example.game",42,row));
        sessions.observe("com.example.game",42,-1,row);assertNull(sessions.uid("com.example.game",42,row));
    }
    @Test public void disowningReplacedRowDoesNotEraseNewOemUidEvidence() {
        var sessions=new SuperTouchSessions();Object oldRow=new Object(),newRow=new Object();
        sessions.claim("com.example.game",42,oldRow);sessions.observe("com.example.game",42,10123,newRow);
        assertFalse(sessions.owns("com.example.game",42,newRow));sessions.disown(42);
        assertTrue(sessions.pids().isEmpty());assertEquals(Integer.valueOf(10123),sessions.uid("com.example.game",42,newRow));
    }
    @Test public void processDeathForgetsForegroundSoDeadPidCannotBeReseeded() {
        var sessions=new SuperTouchSessions();Object row=new Object();
        sessions.foreground("com.example.game",42,10123,true);sessions.observe("com.example.game",42,10123,row);
        sessions.removed(42);assertNotNull(sessions.foreground());
        sessions.died(42);assertNull(sessions.foreground());assertNull(sessions.uid("com.example.game",42,row));
        sessions.foreground("com.example.other",43,10124,true);sessions.died(42);
        assertEquals(43,sessions.foreground().pid());
    }
}
