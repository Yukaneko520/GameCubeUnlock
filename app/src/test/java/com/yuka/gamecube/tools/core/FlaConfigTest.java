package com.yuka.gamecube.tools.core;

import com.yuka.gamecube.tools.compat.RomFeatures;
import com.yuka.gamecube.tools.hooks.demo.DemonstrationPolicy;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class FlaConfigTest {
    private ConfigSnapshot selected() throws Exception {
        var raw=new JSONObject(ConfigSnapshot.defaults().withGlobal(true,0,false).toJson());
        raw.put("disableFlaGlobal",true);
        return ConfigSnapshot.parse(raw.toString());
    }
    private boolean enabled(ConfigSnapshot value) throws Exception {
        return new JSONObject(value.toJson()).optBoolean("disableFlaGlobal",false);
    }
    @Test public void optionalFlagRoundTripsInsteadOfBeingLost() throws Exception {
        assertTrue(enabled(selected()));
        var old=new JSONObject(selected().toJson());old.remove("disableFlaGlobal");
        assertFalse(enabled(ConfigSnapshot.parse(old.toString())));
        old.put("disableFlaGlobal","true");
        assertThrows(Exception.class,()->ConfigSnapshot.parse(old.toString()));
    }
    @Test public void everyExistingCopyPreservesTheGlobalSetting() throws Exception {
        var value=selected();
        assertTrue(enabled(value.withGlobal(false,1,true)));
        assertTrue(enabled(value.with144Global(true)));
        assertTrue(enabled(value.withDemonstration(true)));
        assertTrue(enabled(value.withApp("com.example.game",AppRule.defaults().withEnabled(true))));
        assertTrue(enabled(value.withRevision(99)));
        assertTrue(enabled(DemonstrationPolicy.assistant(value.withDemonstration(true))));
    }
    @Test public void onlyEffectiveOs6ConfigurationCanEnableIt() throws Exception {
        assertTrue(enabled(RomFeatures.effective(selected(),36)));
        assertFalse(enabled(RomFeatures.effective(selected(),35)));
        assertFalse(enabled(RomFeatures.effective(selected(),34)));
        assertTrue(enabled(selected()));
    }
}
