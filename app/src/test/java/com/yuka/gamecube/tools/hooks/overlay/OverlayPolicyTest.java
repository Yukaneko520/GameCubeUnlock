package com.yuka.gamecube.tools.hooks.overlay;

import org.junit.Test;
import static org.junit.Assert.*;

public class OverlayPolicyTest {
    @Test public void stockAndDisabledPreserveBothNativeCapabilities() {
        for (boolean stock : new boolean[] {false, true}) {
            assertEquals(stock, OverlayPolicy.fps(true, 0, stock));
            for (int mode = 0; mode <= 2; mode++)
                assertEquals(stock, OverlayPolicy.fps(false, mode, stock));
        }
    }

    @Test public void showHideAndInvalidModeHaveDistinctSemantics() {
        assertTrue(OverlayPolicy.fps(true, 1, false));
        assertFalse(OverlayPolicy.fps(true, 2, true));
        assertFalse(OverlayPolicy.fps(true, 55, false));
        assertTrue(OverlayPolicy.fps(true, 55, true));
    }

    @Test public void embeddedCenterHideRequiresMasterAndRequest() {
        assertTrue(OverlayPolicy.hideCenter(true, true));
        assertFalse(OverlayPolicy.hideCenter(false, true));
        assertFalse(OverlayPolicy.hideCenter(true, false));
    }
}
