package com.yuka.gamecube.tools.hooks.backend;

import org.junit.Test;
import static org.junit.Assert.*;

public class ControlTicketsTest {
    @Test public void equalPayloadsDoNotCrossAttributeAsynchronousRequests() {
        var tickets=new ControlTickets();
        Object first=new String("same"),second=new String("same");
        var a=ControlTickets.create("com.example.one",7,"delay",2,true);
        var b=ControlTickets.create("com.example.two",8,"anti_shake",1,true);
        assertNotEquals(a.id(),b.id());
        tickets.put(first,a);tickets.put(second,b);
        assertSame(a,tickets.take(first));assertNull(tickets.take(first));
        assertSame(b,tickets.take(second));assertEquals(0,tickets.size());
    }
    @Test public void stalePositiveCommandsStayGuardedThroughEveryFactoryRetry() {
        var tickets=new ControlTickets();Object result=new Object();
        var ticket=ControlTickets.create("com.example.game",9,"anti_shake",2,true);
        tickets.put(result,ticket);
        for(int retry=0;retry<3;retry++) assertTrue(ControlTickets.suppress(tickets.attempt(result),false));
        assertEquals(0,tickets.size());assertNull(tickets.attempt(result));
    }
    @Test public void cleanupZeroAndStockCommandsSurviveRevocation() {
        assertFalse(ControlTickets.suppress(ControlTickets.create("com.example.game",9,"delay",0,true),false));
        assertFalse(ControlTickets.suppress(ControlTickets.create("com.example.game",9,"delay",2,false),false));
        assertFalse(ControlTickets.suppress(ControlTickets.create("com.example.game",9,"delay",2,true),true));
        assertFalse(ControlTickets.suppress(null,false));
    }
    @Test public void missingCallbackCannotGrowDiagnosticsWithoutBound() {
        var tickets=new ControlTickets();
        var ticket=ControlTickets.create("com.example.game",1,"delay",1,true);
        Object first=new Object();assertTrue(tickets.put(first,ticket));
        for(int i=1;i<128;i++) assertTrue(tickets.put(new Object(),ticket));
        for(int i=0;i<1000;i++) assertFalse(tickets.put(new Object(),ticket));
        assertEquals(128,tickets.size());
        assertSame(ticket,tickets.take(first)); // Full capacity never evicts a queued command's revocation guard.
        assertTrue(tickets.put(new Object(),ticket));
        tickets.put(null,ticket);tickets.put(new Object(),null);
        assertEquals(128,tickets.size());
    }
}
