package com.yuka.gamecube.tools.hooks.backend;

import org.junit.Test;
import static org.junit.Assert.*;

public class BackendLeaseTest {
    private final Object controller=new Object();
    private BackendLease.Session pp(String pkg) { return new BackendLease.Session(controller,pkg,0,1,0,0); }
    @Test public void ignoredEnableForAnotherPackageKeepsExactOwnedSessionAndAdvancesToken() {
        BackendLease leases=new BackendLease(); leases.begin();
        leases.complete(true,true,"com.example.a",pp("com.example.a"));
        var old=leases.current(); leases.begin();
        leases.complete(true,true,"com.example.b",pp("com.example.a"));
        assertNotNull(leases.current());
        assertFalse(leases.canClose(old,pp("com.example.a"),false));
        assertTrue(leases.canClose(leases.current(),pp("com.example.a"),false));
        leases.begin(); leases.complete(true,true,"com.example.b",pp("com.example.b"));
        assertEquals("com.example.b",leases.current().session().pkg());
    }
    @Test public void successfulUnmatchedTransitionNeverKeepsChangedSession() {
        for(BackendLease.Session actual:new BackendLease.Session[]{
                new BackendLease.Session(new Object(),"com.example.a",0,1,0,0),
                new BackendLease.Session(controller,"com.example.a",10,1,0,0),
                new BackendLease.Session(controller,"com.example.a",0,2,0,0),
                new BackendLease.Session(controller,"com.example.a",0,1,1,0),
                new BackendLease.Session(controller,"com.example.a",0,1,0,1)}) {
            BackendLease leases=new BackendLease(); leases.begin();
            leases.complete(true,true,"com.example.a",pp("com.example.a"));
            leases.begin(); leases.complete(true,true,"com.example.b",actual); assertNull(leases.current());
        }
    }
    @Test public void queriesAndFailedEnableDoNotOwnSessions() {
        BackendLease leases=new BackendLease();
        assertNull(leases.current());
        leases.begin(); leases.complete(false,true,"com.example.game",pp("com.example.game"));
        assertNull(leases.current());
    }
    @Test public void ownsOnlySuccessfulExactPrimaryPpSession() {
        for(BackendLease.Session s:new BackendLease.Session[]{
                pp("com.other.game"),new BackendLease.Session(controller,"com.example.game",10,1,0,0),
                new BackendLease.Session(controller,"com.example.game",0,1,1,0),
                new BackendLease.Session(controller,"com.example.game",0,1,0,1),
                new BackendLease.Session(controller,"com.example.game",0,0,0,0)}) {
            BackendLease leases=new BackendLease(); leases.begin();
            leases.complete(true,true,"com.example.game",s); assertNull(leases.current());
        }
        BackendLease leases=new BackendLease(); leases.begin();
        leases.complete(true,true,"com.example.game",pp("com.example.game"));
        assertNotNull(leases.current());
        assertTrue(leases.canClose(leases.current(),pp("com.example.game"),false));
        assertFalse(leases.canClose(leases.current(),pp("com.other.game"),false));
        assertFalse(leases.canClose(leases.current(),pp("com.example.game"),true));
    }
    @Test public void newGenerationEvenSamePackageRejectsQueuedCleanup() {
        BackendLease leases=new BackendLease(); leases.begin();
        leases.complete(true,true,"com.example.game",pp("com.example.game"));
        var old=leases.current(); leases.begin();
        leases.complete(true,true,"com.example.game",pp("com.example.game"));
        assertFalse(leases.canClose(old,pp("com.example.game"),false));
        assertTrue(leases.canClose(leases.current(),pp("com.example.game"),false));
    }
    @Test public void failedCleanupRetainsLeaseAndUnrelatedTransitionDiscardsIt() {
        BackendLease leases=new BackendLease(); leases.begin();
        leases.complete(true,true,"com.example.game",pp("com.example.game"));
        var token=leases.current(); // An exception from OFF leaves ownership available for event-driven retry.
        assertTrue(leases.canClose(token,pp("com.example.game"),false));
        leases.begin(); leases.complete(false,false,null,pp("com.example.game"));
        assertNotNull(leases.current());
        assertFalse(leases.canClose(token,pp("com.example.game"),false));
        leases.begin(); leases.complete(true,true,null,pp("com.other.game"));
        assertNull(leases.current());
    }
    @Test public void revokeDuringEnableStillCapturesCompletedOwnedSession() {
        BackendLease leases=new BackendLease(); long call=leases.begin();
        // Policy revokes while original is outside the bookkeeping lock. No ownership yet.
        assertNull(leases.current());
        leases.complete(call,true,true,"com.example.game",pp("com.example.game"));
        assertTrue(leases.canClose(leases.current(),pp("com.example.game"),false));
    }
    @Test public void inFlightNativeUpdateBlocksQueuedCleanup() {
        BackendLease leases=new BackendLease(); leases.begin();
        leases.complete(true,true,"com.example.game",pp("com.example.game"));
        var token=leases.current(); long call=leases.begin();
        assertFalse(leases.canClose(token,pp("com.example.game"),false));
        leases.complete(call,true,true,"com.example.game",pp("com.example.game"));
        assertFalse(leases.canClose(token,pp("com.example.game"),false));
    }
}
