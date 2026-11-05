package com.yuka.gamecube.tools.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import com.yuka.gamecube.tools.compat.TargetProfiles;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class NativeProfileCatalogTest {
    @Test public void os4CatalogUsesItsOwnShippedXmlProfilesWithoutSoftwareSrRows() throws Exception {
        assertEquals("native-profile-catalog-os4.json",NativeProfileCatalog.assetForSdk(34));
        assertEquals("native-profile-catalog.json",NativeProfileCatalog.assetForSdk(35));
        assertEquals("native-profile-catalog.json",NativeProfileCatalog.assetForSdk(36));
        Path path=Path.of("src/main/assets",NativeProfileCatalog.assetForSdk(34));
        if(!Files.exists(path)) path=Path.of("app/src/main/assets",NativeProfileCatalog.assetForSdk(34));
        String raw=Files.readString(path);
        var catalog=NativeProfileCatalog.parse(raw);
        assertEquals(34,catalog.frames().size());assertTrue(catalog.sr().isEmpty());
        var genshin=catalog.frames().stream().filter(p->p.packageName().equals("com.miHoYo.Yuanshen")).findFirst().orElseThrow();
        assertEquals(3,genshin.index());assertFalse(genshin.sdk());
        assertEquals(List.of(new NativeProfileCatalog.FrameValues(60,45,90)),genshin.boost());
        assertTrue(catalog.frames().stream().filter(p->p.packageName().equals("com.tencent.tmgp.sgame")).findFirst().orElseThrow().sdk());
        var provenance=new JSONObject(raw).getJSONObject("provenance");
        assertEquals(34,provenance.getInt("androidSdk"));
        assertEquals("3965085db90facfd56e5f0a173072525bea561670eb56b2b32f30a696b03cc35",provenance.getString("sourceSha256"));
        assertEquals(TargetProfiles.OS4.gameCubeSha(),provenance.getString("gamecubeApkSha256"));
    }
    private String asset() throws Exception {
        Path path = Path.of("src/main/assets/native-profile-catalog.json");
        if (!Files.exists(path)) path = Path.of("app/src/main/assets/native-profile-catalog.json");
        return Files.readString(path);
    }
    private JSONObject empty() throws Exception {
        return new JSONObject().put("schema", 1).put("provenance", new JSONObject()
                .put("source", "tmp/device-debug-20261021/effective-profile-data.json")
                .put("sourceSha256", "8fd01b41c50af616cf49ea403ced6efe3ab195efc358ae1b7f5ecf33fca34831")
                .put("capturedAt", "2026-10-21").put("model", "PD2352").put("deviceModel", "V2352GA")
                .put("androidSdk", 36).put("gamecubeVersion", "14.0.10.00_de").put("gamecubeVersionCode", 1401000)
                .put("gamecubeApkSha256", "924a0700a9c64ac2f2f3af90197ec1907c021a9094312a8f8b2255efc8cc3c7e"))
                .put("frames", new JSONArray()).put("sr", new JSONArray());
    }
    private void rejected(JSONObject value) throws Exception {
        try { NativeProfileCatalog.parse(value.toString()); fail("Malformed catalog accepted"); }
        catch (IllegalArgumentException | org.json.JSONException expected) { }
    }
    @Test public void capturedRowsPinCountsSourceAndIndependentNativeModes() throws Exception {
        NativeProfileCatalog catalog = NativeProfileCatalog.parse(asset());
        assertEquals(35, catalog.frames().size()); assertEquals(9, catalog.sr().size());
        assertTrue(catalog.provenance().contains("PD2352"));
        assertTrue(catalog.provenance().contains("2026-10-21"));
        var hsr = catalog.frames().stream().filter(p -> p.packageName().equals("com.miHoYo.hkrpg")).findFirst().orElseThrow();
        assertEquals(List.of(new NativeProfileCatalog.FrameValues(60, 45, 90)), hsr.boost());
        assertEquals(29, hsr.index()); assertFalse(hsr.sdk()); assertEquals(0, hsr.monsterPlus());
        var pubg = catalog.frames().stream().filter(p -> p.packageName().equals("com.tencent.tmgp.pubgmhd")).findFirst().orElseThrow();
        assertEquals(List.of(new NativeProfileCatalog.FrameValues(90, 60, 120)), pubg.boost());
        var cod = catalog.frames().stream().filter(p -> p.packageName().equals("com.tencent.tmgp.cod")).findFirst().orElseThrow();
        assertTrue(cod.boost().isEmpty());
        assertEquals(List.of(new NativeProfileCatalog.FrameValues(144, 60, 120)), cod.power());
        assertTrue(catalog.frames().stream().anyMatch(p -> p.packageName().equals("com.miHoYo.Yuanshen")));
        assertFalse(catalog.frames().stream().anyMatch(p -> p.packageName().equals("com.mihoyo.yuanshen")));
        for (var sr : catalog.sr()) {
            assertEquals(1, sr.state()); assertEquals(-1, sr.index()); assertEquals(0, sr.monsterPlus());
            assertEquals(-1, sr.frameDepress()); assertEquals(-1, sr.frameInter());
            assertEquals(-1, sr.highDepress()); assertEquals(-1, sr.highInter());
        }
    }
    @Test public void capturedSnapshotAndDetailedSourceProvenanceArePinned() throws Exception {
        String raw = asset(); JSONObject source = new JSONObject(raw).getJSONObject("provenance");
        assertEquals("tmp/device-debug-20261021/effective-profile-data.json", source.getString("source"));
        assertEquals("8fd01b41c50af616cf49ea403ced6efe3ab195efc358ae1b7f5ecf33fca34831", source.getString("sourceSha256"));
        assertEquals("924a0700a9c64ac2f2f3af90197ec1907c021a9094312a8f8b2255efc8cc3c7e", source.getString("gamecubeApkSha256"));
        assertEquals("c085f743133caad838c28158cf0bac0e89d3e77e91f1612ec224f4fbe2d9f621", HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(raw.replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8))));
    }
    @Test public void parserPreservesSdkMonsterAndIndependentSrMetadata() throws Exception {
        JSONObject value = new JSONObject(asset());
        value.getJSONArray("frames").getJSONObject(0).put("sdk", true).put("monsterPlus", 1);
        value.getJSONArray("sr").getJSONObject(0).put("monsterPlus", 1).put("state", 3).put("frameInter", 120);
        var catalog = NativeProfileCatalog.parse(value.toString());
        assertTrue(catalog.frames().get(0).sdk()); assertEquals(1, catalog.frames().get(0).monsterPlus());
        assertEquals(1, catalog.sr().get(0).monsterPlus()); assertEquals(3, catalog.sr().get(0).state());
        assertEquals(120, catalog.sr().get(0).frameInter()); assertEquals(-1, catalog.sr().get(0).highInter());
    }
    @Test public void collectionsAndRecordModeListsAreDefensiveAndImmutable() throws Exception {
        var catalog = NativeProfileCatalog.parse(asset());
        try { catalog.frames().clear(); fail(); } catch (UnsupportedOperationException expected) { }
        try { catalog.sr().clear(); fail(); } catch (UnsupportedOperationException expected) { }
        var values = new ArrayList<>(List.of(new NativeProfileCatalog.FrameValues(60, 45, 90)));
        var profile = new NativeProfileCatalog.FrameProfile("game.test", "Game", "#", 3, false, 0, values, List.of());
        values.clear(); assertEquals(1, profile.boost().size());
        try { profile.boost().clear(); fail(); } catch (UnsupportedOperationException expected) { }
    }
    @Test public void emptyFamiliesAreValidButProvenanceAndSchemaRemainRequired() throws Exception {
        var catalog = NativeProfileCatalog.parse(empty().toString());
        assertTrue(catalog.frames().isEmpty()); assertTrue(catalog.sr().isEmpty());
        rejected(empty().put("schema", "1"));
        JSONObject bad = empty(); bad.getJSONObject("provenance").remove("sourceSha256"); rejected(bad);
        bad = empty(); bad.getJSONObject("provenance").put("capturedAt", "2026-02-30"); rejected(bad);
        bad = empty(); bad.getJSONObject("provenance").put("sourceSha256", "not-a-hash"); rejected(bad);
    }
    @Test public void malformedRowsAndCoercedPrimitivesAreRejected() throws Exception {
        JSONObject baseline = new JSONObject(asset());
        for (Object[] change : new Object[][]{{"packageName", "bad package"}, {"index", "3"},
                {"index", 3.5}, {"sdk", 0}, {"monsterPlus", 2}, {"label", JSONObject.NULL}}) {
            JSONObject value = new JSONObject(baseline.toString());
            value.getJSONArray("frames").getJSONObject(0).put((String) change[0], change[1]); rejected(value);
        }
        JSONObject value = new JSONObject(baseline.toString()); value.getJSONArray("frames").getJSONObject(0).remove("power"); rejected(value);
        value = new JSONObject(baseline.toString()); value.getJSONArray("sr").getJSONObject(0).put("state", "1"); rejected(value);
        value = new JSONObject(baseline.toString());
        value.getJSONArray("frames").put(value.getJSONArray("frames").getJSONObject(0)); rejected(value);
    }
    @Test public void oversizedInputsFamiliesAndModeListsAreRejected() throws Exception {
        try { NativeProfileCatalog.parse(" ".repeat(256_001)); fail(); } catch (IllegalArgumentException expected) { }
        JSONObject value = empty(); JSONArray rows = new JSONArray();
        for (int i = 0; i < 257; i++) rows.put(new JSONObject());
        rejected(value.put("frames", rows));
        value = new JSONObject(asset()); JSONArray modes = new JSONArray();
        for (int i = 0; i < 33; i++) modes.put(new JSONObject().put("gameFps", 60).put("depressFps", 45).put("interFps", 90));
        value.getJSONArray("frames").getJSONObject(0).put("boost", modes); rejected(value);
        value = new JSONObject(asset()); value.getJSONArray("sr").getJSONObject(0).put("frameInter", 481); rejected(value);
    }
}
