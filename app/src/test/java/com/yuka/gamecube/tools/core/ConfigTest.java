package com.yuka.gamecube.tools.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class ConfigTest {
    @Test public void selectionIsExactAndIndependent() throws Exception {
        ConfigSnapshot c=ConfigSnapshot.defaults().withGlobal(true,1,true)
                .withApp("com.test.game",AppRule.defaults().withEnabled(true).withFeature("touch",true));
        c=ConfigSnapshot.parse(c.toJson());
        assertTrue(c.allows("com.test.game","touch"));
        assertFalse(c.allows("com.test.game.extra","touch"));
        assertFalse(c.allows("com.test.game","gyro"));
        assertFalse(c.withGlobal(false,1,true).allows("com.test.game","touch"));
    }
    @Test public void updatesCannotMutateOldSnapshot() {
        ConfigSnapshot old=ConfigSnapshot.defaults(); ConfigSnapshot next=old.withApp("com.test.game",AppRule.defaults());
        assertTrue(old.apps.isEmpty()); assertEquals(1,next.apps.size()); assertTrue(next.revision>old.revision);
        assertThrows(UnsupportedOperationException.class,()->next.apps.clear());
    }
    @Test public void rejectsInvalidInput() {
        assertThrows(Exception.class,()->ConfigSnapshot.parse("{\"schema\":2}"));
        assertThrows(IllegalArgumentException.class,()->AppRule.defaults().withDonor("frame","../../data"));
        assertThrows(IllegalArgumentException.class,()->ConfigSnapshot.defaults().withGlobal(true,5,false));
        assertThrows(IllegalArgumentException.class,()->ConfigSnapshot.defaults().withApp("com.test.*",AppRule.defaults()));
    }
}
