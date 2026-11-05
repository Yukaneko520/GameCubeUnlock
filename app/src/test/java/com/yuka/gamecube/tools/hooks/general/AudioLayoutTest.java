package com.yuka.gamecube.tools.hooks.general;
import org.junit.Test;
import static org.junit.Assert.*;

public class AudioLayoutTest {
    @Test public void os5PinsOriginalEnumAndControllerInsteadOfOs6Resolver() {
        var old=AudioLayout.forSdk(35);var modern=AudioLayout.forSdk(36);
        assertEquals("e8.c",old.controller());assertEquals("fromPkgName",old.resolverMethod());
        assertEquals(old.preset(),old.resolverOwner());assertEquals("a",old.command());
        assertEquals("o0",old.capable());assertNull(old.spatializer());
        assertEquals("com.vivo.gameassistant.gamecustomsound.c",modern.controller());
        assertEquals("t1",modern.spatializer());assertEquals("Y",modern.apply());
        assertThrows(IllegalArgumentException.class,()->AudioLayout.forSdk(33));
    }
}
