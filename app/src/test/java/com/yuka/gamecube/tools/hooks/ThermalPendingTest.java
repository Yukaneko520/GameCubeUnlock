package com.yuka.gamecube.tools.hooks;

import com.yuka.gamecube.tools.hooks.thermal.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ThermalPendingTest {
    private final ThermalSessions sessions=new ThermalSessions();
    private final Object owner=new Object(),request=new Object();
    private ThermalPending pending(boolean bypass) {
        sessions.enter(41,"com.test.game");return new ThermalPending(owner,sessions.get(41),3,bypass);
    }
    @Test public void defaultOffDoesNotChangeNativeQueuedWork() {
        var stock=pending(false);
        sessions.exit(41,"com.test.game");
        assertFalse(stock.blocked(sessions,false,request,new Object()));
        assertTrue(stock.blocked(sessions,true,request,request));
        stock.invalidate();assertTrue(stock.blocked(sessions,false,request,request));
    }
    @Test public void bypassRenderRequiresSameSelectionProcessAndRequest() {
        var bypass=pending(true);
        assertFalse(bypass.blocked(sessions,true,request,request));
        assertTrue(bypass.blocked(sessions,false,request,request));
        assertTrue(bypass.blocked(sessions,true,request,new Object()));
        sessions.exit(41,"com.test.game");sessions.enter(41,"com.test.game");
        assertTrue(bypass.blocked(sessions,true,request,request));
    }
    @Test public void rapidOnOffCannotResurrectAnEarlierCommand() {
        var bypass=pending(true);bypass.invalidate();
        assertTrue(bypass.blocked(sessions,true,request,request));
        assertTrue(bypass.blocked(sessions,false,request,request));
        assertFalse(pending(true).blocked(sessions,true,request,request));
    }
}
