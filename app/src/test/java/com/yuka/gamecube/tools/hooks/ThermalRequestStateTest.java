package com.yuka.gamecube.tools.hooks;

import com.yuka.gamecube.tools.hooks.thermal.ThermalRequestState;
import org.junit.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

/** Unit fixture for the verified raw DEX field/release contract, not an OEM performance simulation. */
public class ThermalRequestStateTest {
    static final class Os4Request { int c=120;double d=48;long l=76;Object n=new Object(),o=new Object(); }
    static final class Os4Monitor {
        boolean fail;long released;
        private void k(long handle,Os4Request request) {
            if(fail) throw new IllegalStateException("release failed");
            if(handle!=request.l) throw new AssertionError("wrong owner");released=handle;
        }
    }
    @Test public void os4UsesItsOwnHandleAndRowsWithoutTouchingTemperature() throws Throwable {
        var monitor=new Os4Monitor();var request=new Os4Request();monitor.fail=true;
        assertThrows(IllegalStateException.class,()->ThermalRequestState.release(monitor,request,"l","k"));
        assertEquals(76,request.l);monitor.fail=false;
        assertEquals(76,ThermalRequestState.release(monitor,request,"l","k"));
        assertEquals(76,monitor.released);assertEquals(-1,request.l);
        ThermalRequestState.invalidateSelection(request,"n","o");
        assertNull(request.n);assertNull(request.o);assertEquals(-1,request.c);assertEquals(48,request.d,0);
    }
    static final class Request { int c=144; double d=48.25; long m=76; Object o=new Object(),p=new Object(); }
    static final class Monitor {
        final Set<Long> handles=new HashSet<>(Set.of(76L,88L));boolean fail;
        private void l(long handle,Request requester) {
            if(fail) throw new IllegalStateException("release failed");
            if(handle!=requester.m) throw new AssertionError("wrong owner");
            handles.remove(handle);
        }
    }
    @Test public void releaseClearsOnlyTheExactOwnedHandleAndNeverChangesTemperature() throws Throwable {
        var monitor=new Monitor();var request=new Request();
        assertEquals(76,ThermalRequestState.release(monitor,request));
        assertEquals(Set.of(88L),monitor.handles);assertEquals(-1,request.m);
        assertEquals(48.25,request.d,0);assertEquals(144,request.c);
        assertEquals(-1,ThermalRequestState.release(monitor,request));
    }
    @Test public void failedReleaseRetainsOwnershipSoItCanBeRetried() throws Throwable {
        var monitor=new Monitor();var request=new Request();monitor.fail=true;
        assertThrows(IllegalStateException.class,()->ThermalRequestState.release(monitor,request));
        assertEquals(76,request.m);assertTrue(monitor.handles.contains(76L));
        monitor.fail=false;assertEquals(76,ThermalRequestState.release(monitor,request));
    }
    @Test public void invalidateForcesReevaluationWithoutSpoofingSensorOrDroppingHandle() throws Throwable {
        var request=new Request();ThermalRequestState.invalidateSelection(request);
        assertEquals(-1,request.c);assertNull(request.o);assertNull(request.p);
        assertEquals(48.25,request.d,0);assertEquals(76,request.m);
    }
}
