package com.yuka.gamecube.tools.hooks;

import com.yuka.gamecube.tools.hooks.thermal.BenchmarkLease;
import com.yuka.gamecube.tools.hooks.thermal.BenchmarkForeground;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Deterministic API boundary tests; these do not simulate thermal or hardware performance. */
public class BenchmarkLeaseTest {
    @Test public void screenWakeReacquiresOnlyAfterFreshSessionVerification() {
        var driver=new Driver();var lease=new BenchmarkLease(driver,s->{});var foreground=new BenchmarkForeground();
        foreground.foreground(10001,true,123,"com.example.game");foreground.remember();var session=foreground.current();
        lease.update(session.pkg(),session.pid(),2,0);
        foreground.suspend();lease.clear("screen_off");
        assertNull(foreground.current());assertEquals(List.of(7),driver.releases);
        assertFalse(foreground.resume(session,true,true,false));assertEquals(1,driver.acquisitions);
        assertTrue(foreground.resume(session,true,true,true));
        lease.update(session.pkg(),session.pid(),2,10000);
        assertEquals(2,driver.acquisitions);assertEquals(16,driver.resources.length);
        foreground.death(session.uid(),session.pid(),null);lease.update("",0,0,10001);
        assertEquals(List.of(7,7),driver.releases);assertNull(foreground.candidate());
    }
    static class Driver implements BenchmarkLease.Driver {
        int handle=7, acquisitions;boolean failAcquire,failRelease;int[] resources;
        List<Integer> releases=new ArrayList<>();List<String> order=new ArrayList<>();
        public int acquire(int duration,int[] values) {
            assertEquals(6000,duration);acquisitions++;resources=values;order.add("acquire");
            if(failAcquire) throw new IllegalStateException("API disconnected");return handle;
        }
        public int release(int value) {
            releases.add(value);order.add("release");
            if(failRelease) throw new IllegalStateException("API disconnected");return 0;
        }
    }
    @Test public void offDoesNotAcquireAndLeaseIsFiniteWithBoundedRenewal() {
        var driver=new Driver();var lease=new BenchmarkLease(driver,s->{});
        lease.update("com.example.game",100,0,0);assertEquals(0,driver.acquisitions);
        lease.update("com.example.game",100,1,0);lease.update("com.example.game",100,1,1999);
        assertEquals(1,driver.acquisitions);
        lease.update("com.example.game",100,1,2000);assertEquals(2,driver.acquisitions);
        assertTrue(driver.releases.isEmpty());
        lease.clear("game_exit");assertEquals(List.of(7),driver.releases);
    }
    @Test public void maximumToAutoReleasesBeforeNewRequestAndOtherGameReplacesOwner() {
        var d=new Driver();var logs=new ArrayList<String>();var lease=new BenchmarkLease(d,logs::add);
        lease.update("com.example.a",100,2,0);assertEquals(16,d.resources.length);
        assertTrue(logs.get(0).contains("gpu_min_pwrlevel_requested=0"));
        lease.update("com.example.a",100,1,1);
        assertEquals(List.of("acquire","release","acquire"),d.order);
        assertEquals(2,d.resources.length);
        assertTrue(logs.get(logs.size()-1).contains("gpu_min_pwrlevel_requested=none"));
        lease.update("com.example.b",101,2,2);assertEquals(2,d.releases.size());
        lease.update("com.example.b",101,0,3);assertEquals(3,d.releases.size());
        lease.clear("screen_off");assertEquals(3,d.releases.size());
    }
    @Test public void replacedHandlesReleaseOnlyOldHandleAndFailureWithdrawsOwnedRequest() {
        var d=new Driver();var logs=new ArrayList<String>();var lease=new BenchmarkLease(d,logs::add);
        lease.update("com.example.a",100,1,0);d.handle=8;
        lease.update("com.example.a",100,1,2000);assertEquals(List.of(7),d.releases);
        d.failAcquire=true;lease.update("com.example.a",100,1,4000);
        assertEquals(List.of(7,8),d.releases);
        d.failAcquire=false;d.failRelease=true;
        lease.update("com.example.a",100,2,6000);lease.clear("revoked");
        assertTrue(logs.stream().anyMatch(s->s.contains("fallback=finite_lease_expiry")));
    }
    @Test public void rejectedHandleIsNotTreatedAsSuccessAndPidReuseReleasesOldLease() {
        var d=new Driver();var lease=new BenchmarkLease(d,s->{});
        lease.update("com.example.a",100,1,0);
        lease.update("com.example.a",101,1,1);assertEquals(List.of(7),d.releases);
        d.handle=-1;lease.update("com.example.a",101,1,2001);
        assertEquals(List.of(7,7),d.releases);
        lease.clear("exit");assertEquals(2,d.releases.size());
    }
}
