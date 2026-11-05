package com.yuka.gamecube.tools.hooks.overlay;

import com.yuka.gamecube.tools.core.AppRule;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
import org.junit.Test;
import static org.junit.Assert.*;

public class MonsterSessionTest {
    private final Object controller=new Object();
    private ConfigSnapshot selected() {
        return ConfigSnapshot.defaults().withGlobal(true,0,false).withApp("com.test.game",
                AppRule.defaults().withEnabled(true).withFeature("monsterPlus",true));
    }
    @Test public void admissionRequiresExactForegroundRuleAndNativeCapability() {
        MonsterSession session=new MonsterSession();
        assertFalse(session.allows(selected(),true));
        session.enter(controller,"com.test.game");assertTrue(session.allows(selected(),true));
        assertFalse(session.allows(selected(),false));
        session.enter(controller,"com.test.game.extra");assertFalse(session.allows(selected(),true));
        session.enter(controller,null);assertFalse(session.allows(selected(),true));
    }
    @Test public void masterAppAndFeatureDisableWithdrawImmediately() {
        MonsterSession session=new MonsterSession();session.enter(controller,"com.test.game");
        ConfigSnapshot on=selected();assertTrue(session.allows(on,true));
        assertFalse(session.allows(on.withGlobal(false,0,false),true));
        assertFalse(session.allows(on.withApp("com.test.game",on.rule("com.test.game").withEnabled(false)),true));
        assertFalse(session.allows(on.withApp("com.test.game",on.rule("com.test.game").withFeature("monsterPlus",false)),true));
        assertFalse(session.allows(ConfigSnapshot.defaults(),true));
    }
    @Test public void backgroundRemovesAdmissionBeforeNativeCleanup() {
        MonsterSession session=new MonsterSession();session.enter(controller,"com.test.game");
        session.exit(controller);assertFalse(session.allows(selected(),true));
        assertNull(session.controller());assertEquals("",session.pkg());
        assertFalse(MonsterSession.protectDisconnectClear(false,1,session.allows(selected(),true)));
    }
    @Test public void lateOldControllerExitCannotClearTheNewSession() {
        MonsterSession session=new MonsterSession();Object next=new Object();
        session.enter(controller,"com.test.other");session.enter(next,"com.test.game");
        session.exit(controller);assertSame(next,session.controller());
        assertTrue(session.allows(selected(),true));
        session.exit(next);assertFalse(session.allows(selected(),true));
    }
    @Test public void disconnectProtectionNeverSwallowsUserOffOrBackgroundCleanup() {
        assertTrue(MonsterSession.protectDisconnectClear(false,1,true));
        assertTrue(MonsterSession.protectDisconnectClear(false,2,true));
        assertFalse(MonsterSession.protectDisconnectClear(false,0,true));
        assertFalse(MonsterSession.protectDisconnectClear(false,1,false));
        assertFalse(MonsterSession.protectDisconnectClear(true,1,true));
    }
}
