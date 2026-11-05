package com.yuka.gamecube.tools.core;

import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class AdditionalConfigTest {
    private static final String[] FEATURES = {"touch","gyro","superTouch","superGyro","audio","frame","sr"};

    private static JSONObject valid() throws Exception {
        return new JSONObject(ConfigSnapshot.defaults().toJson());
    }

    private static void rejects(JSONObject value) {
        assertThrows("Malformed config must fail closed: " + value, Exception.class,
                () -> ConfigSnapshot.parse(value.toString()));
    }

    @Test public void rejectsCoercedGlobalTypes() throws Exception {
        for (String key : new String[]{"enabled", "hideGameCenter"})
            for (Object bad : new Object[]{"true", "false", 1, JSONObject.NULL})
                rejects(valid().put(key, bad));
        for (String key : new String[]{"schema", "revision", "fpsMode"})
            for (Object bad : new Object[]{"1", 1.5, true, JSONObject.NULL})
                rejects(valid().put(key, bad));
    }

    @Test public void rejectsCoercedAppTypes() throws Exception {
        for (String key : new String[]{"enabled", "touch", "gyro", "superTouch", "superGyro", "audio", "frame", "sr"}) {
            JSONObject app = new JSONObject().put(key, "true");
            rejects(valid().put("apps", new JSONObject().put("com.test.game", app)));
        }
        for (String donor : new String[]{"audioDonor", "frameDonor", "srDonor"}) {
            JSONObject app = new JSONObject().put(donor, JSONObject.NULL);
            rejects(valid().put("apps", new JSONObject().put("com.test.game", app)));
        }
    }

    @Test public void rejectsSchemaBoundsRevisionAndPackageViolations() throws Exception {
        for (Object bad : new Object[]{0, 2, -1, 4294967297L}) rejects(valid().put("schema", bad));
        for (Object bad : new Object[]{-1, 3, 4294967296L}) rejects(valid().put("fpsMode", bad));
        rejects(valid().put("revision", -1));
        rejects(valid().put("revision", 1e30));
        rejects(valid().put("apps", new JSONObject().put("com.test.*", new JSONObject())));
        rejects(valid().put("apps", new JSONObject().put("com.test.game", true)));
        assertThrows(Exception.class, () -> ConfigSnapshot.parse(null));
        assertThrows(Exception.class, () -> ConfigSnapshot.parse(" ".repeat(512001)));
    }

    @Test public void appAndFeatureSelectionsStayIndependentAcrossUpdates() throws Exception {
        ConfigSnapshot base = ConfigSnapshot.defaults().withGlobal(true, 0, false);
        for (String selected : FEATURES) {
            ConfigSnapshot next = base.withApp("com.test.game", AppRule.defaults().withEnabled(true).withFeature(selected, true));
            next = ConfigSnapshot.parse(next.toJson());
            for (String feature : FEATURES) {
                assertEquals(selected.equals(feature), next.allows("com.test.game", feature));
                assertFalse(next.allows("com.test.other", feature));
                assertFalse(base.allows("com.test.game", feature));
                assertFalse(next.withApp("com.test.game", next.rule("com.test.game").withEnabled(false)).allows("com.test.game", feature));
            }
        }
    }

    @Test public void mapAndDonorEditsCannotAlterPublishedSnapshot() {
        Map<String, AppRule> input = new LinkedHashMap<>();
        AppRule donor = AppRule.defaults().withEnabled(true).withDonor("frame", "com.donor.frame")
                .withDonor("sr", "com.donor.sr").withDonor("audio", "com.donor.audio");
        input.put("com.test.game", donor);
        ConfigSnapshot snapshot = new ConfigSnapshot(true, 1, false, 7, input);
        input.clear();
        assertEquals(1, snapshot.apps.size());
        assertEquals("com.donor.frame", snapshot.rule("com.test.game").frameDonor);
        assertEquals("com.donor.sr", donor.withDonor("frame", "com.changed.game").srDonor);
        assertEquals("com.donor.audio", donor.withDonor("sr", "com.changed.game").audioDonor);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.apps.put("com.other.app", donor));
        for (String bad : new String[]{"com.bad.*", "../data", "com.bad;mode=1", "com.bad\nmode=1"})
            assertThrows(IllegalArgumentException.class, () -> donor.withDonor("audio", bad));
    }

    @Test public void enforcesRuleCountAndSafeRevisionIncrement() {
        Map<String, AppRule> apps = new LinkedHashMap<>();
        for (int i = 0; i < 2000; i++) apps.put("com.test.game" + i, AppRule.defaults());
        assertEquals(2000, new ConfigSnapshot(false, 0, false, 0, apps).apps.size());
        apps.put("com.test.extra", AppRule.defaults());
        assertThrows(IllegalArgumentException.class, () -> new ConfigSnapshot(false, 0, false, 0, apps));
        ConfigSnapshot end = ConfigSnapshot.defaults().withRevision(Long.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> end.withGlobal(true, 0, false));
        assertThrows(IllegalArgumentException.class, () -> end.withApp("com.test.game", AppRule.defaults()));
    }
}
