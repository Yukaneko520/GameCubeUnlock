package com.yuka.gamecube.tools.compat;
import org.junit.Test;
import static org.junit.Assert.*;

public class LegacyFrameLayoutTest {
    @Test public void os4AndOs5NeverShareChangedLifecycleOrPidBindings() {
        var old=LegacyFrameLayout.forSdk(34);var next=LegacyFrameLayout.forSdk(35);
        assertEquals("n0",old.bind());assertEquals("F0",next.bind());
        assertEquals("N",old.cool());assertEquals("O",old.hot());assertEquals("L",old.disable());
        assertEquals("V",old.state());assertEquals("m0",old.view());assertEquals("R",old.recover());
        assertEquals("W",old.dispose());assertEquals("e0",old.schedule());assertEquals("G",old.change());
        assertEquals("p",old.strategyField());assertEquals("mb.n0",old.utils());assertEquals("Q",old.lookupPid());
        assertEquals("k",old.dispatch());assertEquals("o",old.lookupProfile());assertEquals("e7.n",old.writer());
        assertEquals("com.vivo.gameassistant.utils.z0",next.utils());assertEquals("a7.t",next.writer());
        assertEquals("l",next.dispatch());assertEquals("s",next.lookupProfile());
        assertThrows(IllegalArgumentException.class,()->LegacyFrameLayout.forSdk(36));
    }
}
