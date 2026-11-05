package com.yuka.gamecube.tools.core;

import java.time.LocalDate;
import java.time.DateTimeException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Captured UI metadata only. Runtime hooks must resolve their own current OEM profiles. */
public final class NativeProfileCatalog {
    public record FrameValues(int gameFps, int depressFps, int interFps) {}
    public record FrameProfile(String packageName, String label, String channel, int index, boolean sdk,
                               int monsterPlus, List<FrameValues> boost, List<FrameValues> power) {
        public FrameProfile { boost = List.copyOf(boost); power = List.copyOf(power); }
    }
    public record SrProfile(String packageName, String label, String channel, int index, int state,
                            int monsterPlus, int frameDepress, int frameInter, int highDepress, int highInter) {}

    private final List<FrameProfile> frames;
    private final List<SrProfile> sr;
    private final String provenance;
    private NativeProfileCatalog(List<FrameProfile> frames, List<SrProfile> sr, String provenance) {
        this.frames = List.copyOf(frames); this.sr = List.copyOf(sr); this.provenance = provenance;
    }
    public List<FrameProfile> frames() { return frames; }
    public List<SrProfile> sr() { return sr; }
    public String provenance() { return provenance; }
    public static String assetForSdk(int sdk) {
        return sdk==34?"native-profile-catalog-os4.json":"native-profile-catalog.json";
    }

    public static NativeProfileCatalog parse(String raw) throws JSONException {
        if (raw == null || raw.length() > 256_000) throw new IllegalArgumentException("Catalog too large or absent");
        JSONObject root = new JSONObject(raw);
        integer(root, "schema", 1, 1);
        String provenance = provenance(object(root.get("provenance")));
        List<FrameProfile> frames = new ArrayList<>();
        Set<String> packages = new HashSet<>();
        JSONArray rows = array(root, "frames", 256);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = object(rows.get(i));
            String pkg = packageName(row, packages);
            frames.add(new FrameProfile(pkg, text(row, "label", 256), text(row, "channel", 128),
                    integer(row, "index", -1, 65_535), flag(row, "sdk"), integer(row, "monsterPlus", 0, 1),
                    values(row, "boost"), values(row, "power")));
        }
        List<SrProfile> sr = new ArrayList<>();
        packages.clear(); rows = array(root, "sr", 256);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = object(rows.get(i));
            String pkg = packageName(row, packages);
            sr.add(new SrProfile(pkg, text(row, "label", 256), text(row, "channel", 128),
                    integer(row, "index", -1, 65_535), integer(row, "state", 1, 3),
                    integer(row, "monsterPlus", 0, 1), integer(row, "frameDepress", -1, 480),
                    integer(row, "frameInter", -1, 480), integer(row, "highDepress", -1, 480),
                    integer(row, "highInter", -1, 480)));
        }
        return new NativeProfileCatalog(frames, sr, provenance);
    }

    private static String provenance(JSONObject value) throws JSONException {
        text(value, "source", 256);
        hash(value, "sourceSha256"); hash(value, "gamecubeApkSha256");
        String date = text(value, "capturedAt", 10);
        try {
            if (!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new DateTimeException("date format");
            LocalDate.parse(date);
        } catch (DateTimeException error) { throw new IllegalArgumentException("Invalid capture date", error); }
        String model = text(value, "model", 64), device = text(value, "deviceModel", 64);
        integer(value, "androidSdk", 1, 100);
        String version = text(value, "gamecubeVersion", 64);
        int code = integer(value, "gamecubeVersionCode", 1, Integer.MAX_VALUE);
        return date + " · " + model + " / " + device + " · GameCube " + version + " (" + code + ")";
    }
    private static void hash(JSONObject value, String key) throws JSONException {
        if (!text(value, key, 64).matches("[0-9a-f]{64}")) throw new JSONException("Invalid SHA-256: " + key);
    }
    private static String packageName(JSONObject row, Set<String> packages) throws JSONException {
        String pkg = text(row, "packageName", 255);
        if (!ConfigSnapshot.validPackage(pkg) || !packages.add(pkg)) throw new JSONException("Invalid/duplicate package: " + pkg);
        return pkg;
    }
    private static List<FrameValues> values(JSONObject row, String key) throws JSONException {
        JSONArray values = array(row, key, 32);
        List<FrameValues> result = new ArrayList<>();
        for (int i = 0; i < values.length(); i++) {
            JSONObject tuple = object(values.get(i));
            result.add(new FrameValues(integer(tuple, "gameFps", 1, 480), integer(tuple, "depressFps", 1, 480),
                    integer(tuple, "interFps", 1, 480)));
        }
        return result;
    }
    private static JSONObject object(Object value) throws JSONException {
        if (value instanceof JSONObject object) return object;
        throw new JSONException("Expected object");
    }
    private static JSONArray array(JSONObject object, String key, int max) throws JSONException {
        Object value = object.get(key);
        if (value instanceof JSONArray array && array.length() <= max) return array;
        throw new JSONException("Expected bounded array: " + key);
    }
    private static String text(JSONObject object, String key, int max) throws JSONException {
        Object value = object.get(key);
        if (value instanceof String text && !text.isBlank() && text.length() <= max
                && text.chars().noneMatch(character -> character < 32)) return text;
        throw new JSONException("Expected bounded text: " + key);
    }
    private static int integer(JSONObject object, String key, int min, int max) throws JSONException {
        Object value = object.get(key);
        if (value instanceof Integer || value instanceof Long) {
            long integer = ((Number) value).longValue();
            if (integer >= min && integer <= max) return (int) integer;
        }
        throw new JSONException("Expected bounded integer: " + key);
    }
    private static boolean flag(JSONObject object, String key) throws JSONException {
        Object value = object.get(key);
        if (value instanceof Boolean flag) return flag;
        throw new JSONException("Expected boolean: " + key);
    }
}
