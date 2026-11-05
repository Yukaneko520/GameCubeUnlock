package com.yuka.gamecube.tools.hooks;

import com.yuka.gamecube.tools.hooks.thermal.ThermalSessions;
import com.yuka.gamecube.tools.core.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ThermalSessionsTest {
    private final ConfigSnapshot config=ConfigSnapshot.defaults().withGlobal(true,0,false)
            .withApp("com.test.game",AppRule.defaults().withEnabled(true).withFeature("thermalBypass",true));
    @Test public void mustMatchLiveProcessAndExactPackage() {
        var sessions=new ThermalSessions();
        assertFalse(sessions.allows(config,31,"com.test.game"));
        sessions.enter(31,"com.test.game");
        assertTrue(sessions.allows(config,31,"com.test.game"));
        assertFalse(sessions.allows(config,32,"com.test.game"));
        assertFalse(sessions.allows(config,31,"com.test.other"));
        assertFalse(sessions.allows(config.withGlobal(false,0,false),31,"com.test.game"));
        assertFalse(sessions.allows(config.withApp("com.test.game",config.rule("com.test.game").withEnabled(false)),31,"com.test.game"));
    }
    @Test public void backgroundAndPidReuseInvalidateOldTickets() {
        var sessions=new ThermalSessions();sessions.enter(31,"com.test.game");
        var old=sessions.get(31);
        sessions.exit(31,"com.test.other");assertSame(old,sessions.get(31));
        sessions.exit(31,"com.test.game");assertFalse(sessions.current(old));
        sessions.enter(31,"com.test.game");assertFalse(sessions.current(old));
        var newer=sessions.get(31);
        sessions.enter(31,"com.test.other");assertFalse(sessions.current(newer));
        assertFalse(sessions.allows(config,31,"com.test.game"));
    }
}
