package com.yuka.gamecube.tools.core;

import org.json.JSONException;
import org.json.JSONObject;

public final class AppRule {
    public final boolean enabled, touch, gyro, superTouch, superGyro, audio, frame, sr, force144, monsterPlus, thermalBypass;
    public final String frameDonor, srDonor, audioDonor;
    public final GraphicsTuning tuning;
    public final VisualTuning visual;
    public final int benchmarkMode; // 0=off, 1=automatic clocks, 2=maximum CPU/GPU requests

    public AppRule(boolean enabled, boolean touch, boolean gyro, boolean superTouch,
                   boolean superGyro, boolean audio, boolean frame, boolean sr,
                   String frameDonor, String srDonor, String audioDonor) {
        this(enabled,touch,gyro,superTouch,superGyro,audio,frame,sr,frameDonor,srDonor,audioDonor,false);
    }
    public AppRule(boolean enabled, boolean touch, boolean gyro, boolean superTouch,
                   boolean superGyro, boolean audio, boolean frame, boolean sr,
                   String frameDonor, String srDonor, String audioDonor, boolean force144) {
        this(enabled,touch,gyro,superTouch,superGyro,audio,frame,sr,frameDonor,srDonor,audioDonor,force144,GraphicsTuning.defaults());
    }
    public AppRule(boolean enabled, boolean touch, boolean gyro, boolean superTouch,
                   boolean superGyro, boolean audio, boolean frame, boolean sr,
                   String frameDonor, String srDonor, String audioDonor, boolean force144, GraphicsTuning tuning) {
        this(enabled,touch,gyro,superTouch,superGyro,audio,frame,sr,frameDonor,srDonor,audioDonor,force144,tuning,false);
    }
    public AppRule(boolean enabled, boolean touch, boolean gyro, boolean superTouch,
                   boolean superGyro, boolean audio, boolean frame, boolean sr,
                   String frameDonor, String srDonor, String audioDonor, boolean force144, GraphicsTuning tuning,
                   boolean monsterPlus) {
        this(enabled,touch,gyro,superTouch,superGyro,audio,frame,sr,frameDonor,srDonor,audioDonor,force144,tuning,monsterPlus,false);
    }
    public AppRule(boolean enabled, boolean touch, boolean gyro, boolean superTouch,
                   boolean superGyro, boolean audio, boolean frame, boolean sr,
                   String frameDonor, String srDonor, String audioDonor, boolean force144, GraphicsTuning tuning,
                   boolean monsterPlus, boolean thermalBypass) {
        this(enabled,touch,gyro,superTouch,superGyro,audio,frame,sr,frameDonor,srDonor,audioDonor,force144,tuning,monsterPlus,thermalBypass,0);
    }
    public AppRule(boolean enabled, boolean touch, boolean gyro, boolean superTouch,
                   boolean superGyro, boolean audio, boolean frame, boolean sr,
                   String frameDonor, String srDonor, String audioDonor, boolean force144, GraphicsTuning tuning,
                   boolean monsterPlus, boolean thermalBypass, int benchmarkMode) {
        this(enabled,touch,gyro,superTouch,superGyro,audio,frame,sr,frameDonor,srDonor,audioDonor,
                force144,tuning,monsterPlus,thermalBypass,benchmarkMode,VisualTuning.defaults());
    }
    public AppRule(boolean enabled, boolean touch, boolean gyro, boolean superTouch,
                   boolean superGyro, boolean audio, boolean frame, boolean sr,
                   String frameDonor, String srDonor, String audioDonor, boolean force144, GraphicsTuning tuning,
                   boolean monsterPlus, boolean thermalBypass, int benchmarkMode, VisualTuning visual) {
        BenchmarkMode.validate(benchmarkMode);
        this.benchmarkMode = benchmarkMode;
        this.enabled = enabled; this.touch = touch; this.gyro = gyro;
        this.superTouch = superTouch; this.superGyro = superGyro;
        this.audio = audio; this.frame = frame; this.sr = sr; this.force144 = force144;
        this.monsterPlus = monsterPlus;
        this.thermalBypass = thermalBypass;
        this.frameDonor = donor(frameDonor); this.srDonor = donor(srDonor); this.audioDonor = donor(audioDonor);
        this.tuning=java.util.Objects.requireNonNull(tuning);
        this.visual=java.util.Objects.requireNonNull(visual);
    }
    private static String donor(String value) {
        if (value == null) return "";
        value = value.trim();
        if (!value.isEmpty() && !ConfigSnapshot.validPackage(value)) throw new IllegalArgumentException("Invalid donor package");
        return value;
    }
    public static AppRule defaults() { return new AppRule(false,false,false,false,false,false,false,false,"","",""); }
    @Override public boolean equals(Object value) {
        if(!(value instanceof AppRule other)) return false;
        return enabled==other.enabled && touch==other.touch && gyro==other.gyro && superTouch==other.superTouch
                && superGyro==other.superGyro && audio==other.audio && frame==other.frame && sr==other.sr
                && force144==other.force144 && monsterPlus==other.monsterPlus && thermalBypass==other.thermalBypass && benchmarkMode==other.benchmarkMode && frameDonor.equals(other.frameDonor) && srDonor.equals(other.srDonor)
                && audioDonor.equals(other.audioDonor) && tuning.equals(other.tuning) && visual.equals(other.visual);
    }
    @Override public int hashCode() {
        return java.util.Objects.hash(enabled,touch,gyro,superTouch,superGyro,audio,frame,sr,force144,monsterPlus,thermalBypass,
                frameDonor,srDonor,audioDonor,tuning,benchmarkMode,visual);
    }
    public boolean has(String feature) {
        return switch (feature) {
            case "touch" -> touch; case "gyro" -> gyro; case "superTouch" -> superTouch;
            case "superGyro" -> superGyro; case "audio" -> audio; case "frame" -> frame; case "sr" -> sr;
            case "force144" -> force144;
            case "monsterPlus" -> monsterPlus;
            case "thermalBypass" -> thermalBypass;
            case "benchmark" -> benchmarkMode!=0;
            case "visual" -> visual.enabled();
            default -> false;
        };
    }
    public AppRule withEnabled(boolean value) { return new AppRule(value,touch,gyro,superTouch,superGyro,audio,frame,sr,frameDonor,srDonor,audioDonor,force144,tuning,monsterPlus,thermalBypass,benchmarkMode,visual); }
    public AppRule withTuning(GraphicsTuning value) { return new AppRule(enabled,touch,gyro,superTouch,superGyro,audio,frame,sr,frameDonor,srDonor,audioDonor,force144,value,monsterPlus,thermalBypass,benchmarkMode,visual); }
    public AppRule withVisual(VisualTuning value) { return new AppRule(enabled,touch,gyro,superTouch,superGyro,audio,frame,sr,frameDonor,srDonor,audioDonor,force144,tuning,monsterPlus,thermalBypass,benchmarkMode,value); }
    public AppRule withFeature(String key, boolean value) {
        if("visual".equals(key)) return withVisual(visual.withEnabled(value));
        if (!java.util.Set.of("touch","gyro","superTouch","superGyro","audio","frame","sr","force144","monsterPlus","thermalBypass").contains(key)) throw new IllegalArgumentException("Unknown feature");
        return new AppRule(enabled, key.equals("touch")?value:touch, key.equals("gyro")?value:gyro,
                key.equals("superTouch")?value:superTouch, key.equals("superGyro")?value:superGyro,
                key.equals("audio")?value:audio, key.equals("frame")?value:frame, key.equals("sr")?value:sr,
                frameDonor,srDonor,audioDonor,key.equals("force144")?value:force144,tuning,key.equals("monsterPlus")?value:monsterPlus,key.equals("thermalBypass")?value:thermalBypass,benchmarkMode,visual);
    }
    public AppRule withDonor(String key, String value) {
        if (!java.util.Set.of("frame","sr","audio").contains(key)) throw new IllegalArgumentException("Unknown donor type");
        return new AppRule(enabled,touch,gyro,superTouch,superGyro,audio,frame,sr,
                key.equals("frame")?value:frameDonor,key.equals("sr")?value:srDonor,key.equals("audio")?value:audioDonor,force144,tuning,monsterPlus,thermalBypass,benchmarkMode,visual);
    }
    public AppRule withBenchmarkMode(int value) {
        return new AppRule(enabled,touch,gyro,superTouch,superGyro,audio,frame,sr,frameDonor,srDonor,audioDonor,force144,tuning,monsterPlus,thermalBypass,value,visual);
    }
    static AppRule parse(JSONObject o) throws JSONException {
        return new AppRule(flag(o,"enabled"),flag(o,"touch"),flag(o,"gyro"),flag(o,"superTouch"),
                flag(o,"superGyro"),flag(o,"audio"),flag(o,"frame"),flag(o,"sr"),
                text(o,"frameDonor"),text(o,"srDonor"),text(o,"audioDonor"),flag(o,"force144"),GraphicsTuning.parse(o),flag(o,"monsterPlus"),flag(o,"thermalBypass"),benchmark(o),VisualTuning.parse(o));
    }
    private static int benchmark(JSONObject o) throws JSONException {
        if(!o.has("benchmarkMode")) return 0;
        Object value=o.get("benchmarkMode");
        if(!(value instanceof Integer)) throw new JSONException("Expected integer: benchmarkMode");
        int mode=(Integer)value; BenchmarkMode.validate(mode); return mode;
    }
    static boolean flag(JSONObject o,String name) throws JSONException {
        if(!o.has(name)) return false;
        if(o.get(name) instanceof Boolean b) return b;
        throw new JSONException("Expected boolean: "+name);
    }
    private static String text(JSONObject o,String name) throws JSONException {
        if(!o.has(name)) return "";
        if(o.get(name) instanceof String s) return s;
        throw new JSONException("Expected string: "+name);
    }
    JSONObject json() throws JSONException {
        JSONObject o = new JSONObject().put("enabled",enabled);
        for (String key : new String[]{"touch","gyro","superTouch","superGyro","audio","frame","sr","force144","monsterPlus","thermalBypass"}) o.put(key,has(key));
        o.put("benchmarkMode",benchmarkMode);
        o.put("frameDonor",frameDonor).put("srDonor",srDonor).put("audioDonor",audioDonor);
        if(!tuning.equals(GraphicsTuning.defaults())) o.put("tuning",tuning.json());
        if(!visual.equals(VisualTuning.defaults())) o.put("visual",visual.json());
        return o;
    }
}
