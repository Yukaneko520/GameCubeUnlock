package com.yuka.gamecube.tools.core;

import static org.junit.Assert.*;
import org.junit.Test;

public class DiagnosticThrottleTest {
    @Test public void suppressedReadsDoNotPostponeTheNextAllowedEvent() {
        DiagnosticThrottle throttle = new DiagnosticThrottle(4);
        assertTrue(throttle.allow("fps", 100, 1000));
        assertFalse(throttle.allow("fps", 900, 1000));
        assertTrue(throttle.allow("fps", 1100, 1000));
    }
    @Test public void enableAndDisableHaveIndependentKeys() {
        DiagnosticThrottle throttle = new DiagnosticThrottle(4);
        assertTrue(throttle.allow("frame:enable", 100, 1000));
        assertTrue(throttle.allow("frame:disable", 101, 1000));
        assertFalse(throttle.allow("frame:enable", 102, 1000));
    }
    @Test public void cardinalityStaysBoundedAndClockResetDoesNotSilenceLogs() {
        DiagnosticThrottle throttle = new DiagnosticThrottle(2);
        for (int i=0; i<20; i++) assertTrue(throttle.allow("pkg"+i, i, 1000));
        assertEquals(2, throttle.size());
        assertTrue(throttle.allow("pkg19", 1, 1000));
    }
}
