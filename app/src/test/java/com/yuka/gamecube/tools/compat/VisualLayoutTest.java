package com.yuka.gamecube.tools.compat;

import org.junit.Test;
import static org.junit.Assert.*;

public class VisualLayoutTest {
    @Test public void os4UsesItsOwnControllerAndHasNoInventedSpecialEnumGate() {
        var n=VisualLayout.forSdk(34);
        assertEquals("e8.e",n.controller());assertEquals("f8.c",n.strategy());
        assertEquals("X",n.open());assertEquals("C",n.close());assertEquals("k",n.writer());
        assertNull(n.light());assertNull(n.night());
    }
    @Test public void newerRomsKeepDistinctControllerAndCapabilityNames() {
        var five=VisualLayout.forSdk(35);var six=VisualLayout.forSdk(36);
        assertEquals("h8.e",five.controller());assertEquals("y0",five.light());assertEquals("H0",five.night());
        assertEquals("h0",six.open());assertEquals("d0",six.highFps());assertEquals("X0",six.light());
        assertEquals("h1",six.night());assertNotEquals(five.foreground(),six.foreground());
        assertThrows(IllegalArgumentException.class,()->VisualLayout.forSdk(33));
        assertThrows(IllegalArgumentException.class,()->VisualLayout.forSdk(37));
    }
    @Test public void effectiveOs4PolicyKeepsVisualParametersWithoutReenablingSr() {
        var visual=new com.yuka.gamecube.tools.core.VisualTuning(true,0,7,1,17,0,128,50,0,271);
        var rule=com.yuka.gamecube.tools.core.AppRule.defaults().withEnabled(true).withVisual(visual).withFeature("sr",true);
        var config=new com.yuka.gamecube.tools.core.ConfigSnapshot(true,0,false,1,java.util.Map.of("com.example.game",rule));
        var effective=RomFeatures.effective(config,34);
        assertEquals(visual,effective.rule("com.example.game").visual);assertTrue(effective.allows("com.example.game","visual"));
        assertFalse(effective.allows("com.example.game","sr"));assertTrue(config.rule("com.example.game").sr);
    }
}
