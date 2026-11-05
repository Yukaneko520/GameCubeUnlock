package com.yuka.gamecube.tools.compat;

import org.junit.Test;
import static org.junit.Assert.*;

public class SuperControlLayoutTest {
    @Test public void os4HasItsOwnControllerAndNoSuperGyroWriter() {
        var n=SuperControlLayout.forSdk(34);
        assertEquals("h8.c",n.ordinaryController());assertEquals("h8.d",n.touchHelper());
        assertNull(n.gyroHelper());assertEquals("w6.a",n.ordinaryWriter());
        assertEquals("b0.h",n.monitor());assertEquals("b0.u",n.pacing());
        assertEquals("s",n.dispatch());assertEquals("t",n.enabled());assertEquals("D",n.adjust());
        assertEquals("I",n.gameMembership());assertEquals("p",n.foreground());
        assertEquals("L",n.remove());assertEquals("l",n.deathForward());assertEquals("c",n.baseMethod());
    }
    @Test public void os5UsesItsOwnUiAndEarlierWatchDispatch() {
        var n=SuperControlLayout.forSdk(35);
        assertEquals("k8.f",n.touchHelper());assertEquals("k8.e",n.gyroHelper());
        assertEquals("p6.a",n.ordinaryWriter());assertEquals("e0.v",n.pacing());
        assertEquals("e0.h",n.monitor());assertEquals("z",n.dispatch());assertEquals("v",n.enabled());
        assertEquals("e",n.foreground());assertEquals("l.d",n.gameList());
        assertEquals("K",n.gameMembership());assertEquals("S",n.remove());
        assertEquals("i",n.deathForward());
        assertEquals("k8.c",n.ordinaryController());
    }
    @Test public void os6UsesItsOwnWatchAndUnknownRomDoesNotGuess() {
        var n=SuperControlLayout.forSdk(36);
        assertEquals("com.vivo.common.gamemanipulation.b",n.touchHelper());
        assertEquals("e0.f0",n.pacing());assertEquals("e0.n",n.monitor());
        assertEquals("J",n.dispatch());assertEquals("D",n.enabled());
        assertEquals("f",n.foreground());assertEquals("k.d",n.gameList());
        assertEquals("L",n.gameMembership());assertEquals("f0",n.remove());
        assertEquals("n",n.deathForward());
        assertEquals("com.vivo.gameassistant.gamemanipulation.c",n.ordinaryController());
        assertThrows(IllegalArgumentException.class,()->SuperControlLayout.forSdk(33));
        assertThrows(IllegalArgumentException.class,()->SuperControlLayout.forSdk(37));
    }
}
