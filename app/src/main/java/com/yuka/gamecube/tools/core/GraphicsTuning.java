package com.yuka.gamecube.tools.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;

/** Immutable values, independent of donor selection. Disabled flags preserve stock parameters. */
public record GraphicsTuning(boolean frameCustom,List<FrameValue> boost,List<FrameValue> power,
                             boolean srCustom,int algorithm,int targetShortSide,int sourceSize,
                             double sharpness,double darkLimit,double whiteLimit) {
    public record FrameValue(int gameFps,int inputFps,int outputFps) {
        public FrameValue {
            if(gameFps<1 || gameFps>1000 || inputFps<1 || inputFps>gameFps
                    || outputFps<inputFps || outputFps>144) throw new IllegalArgumentException("Invalid MEMC tuple");
        }
    }
    public GraphicsTuning {
        boost=checked(boost); power=checked(power);
        if(algorithm<0 || algorithm>6 || targetShortSide<0 || targetShortSide>4000
                || sourceSize< -1 || sourceSize>1) throw new IllegalArgumentException("Invalid SR configuration");
        if(!Double.isFinite(sharpness) || sharpness<0 || sharpness>10
                || !Double.isFinite(darkLimit) || darkLimit< -255 || darkLimit>0
                || !Double.isFinite(whiteLimit) || whiteLimit<0 || whiteLimit>255)
            throw new IllegalArgumentException("Invalid SR filter values");
    }
    private static List<FrameValue> checked(List<FrameValue> values) {
        if(values==null || values.isEmpty() || values.size()>12) throw new IllegalArgumentException("Expected 1–12 MEMC tuples");
        var result=List.copyOf(values); var fps=new HashSet<Integer>();
        for(var value:result) if(!fps.add(value.gameFps)) throw new IllegalArgumentException("Duplicate game FPS");
        return result;
    }
    private static final GraphicsTuning DEFAULT=new GraphicsTuning(false,List.of(new FrameValue(60,45,90)),
            List.of(new FrameValue(60,30,60)),false,0,0,-1,2.5,-40,20);
    public static GraphicsTuning defaults() { return DEFAULT; }
    public boolean sameFrame(GraphicsTuning other) {
        return frameCustom==other.frameCustom && (!frameCustom || boost.equals(other.boost) && power.equals(other.power));
    }
    public boolean sameSr(GraphicsTuning other) {
        return srCustom==other.srCustom && (!srCustom || algorithm==other.algorithm && targetShortSide==other.targetShortSide
                && sourceSize==other.sourceSize && sharpness==other.sharpness && darkLimit==other.darkLimit && whiteLimit==other.whiteLimit);
    }
    public static List<FrameValue> parseTuples(String text) {
        if(text==null || text.length()>1000) throw new IllegalArgumentException("Invalid tuple input");
        var result=new ArrayList<FrameValue>();
        for(String line:text.trim().split("[;\\r\\n]+")) {
            if(line.isBlank()) continue;
            String[] values=line.trim().split("\\s*[/,]\\s*");
            if(values.length!=3) throw new IllegalArgumentException("Use game/input/output FPS");
            result.add(new FrameValue(Integer.parseInt(values[0]),Integer.parseInt(values[1]),Integer.parseInt(values[2])));
        }
        return checked(result);
    }
    public static String formatTuples(List<FrameValue> values) {
        return values.stream().map(v->v.gameFps+"/"+v.inputFps+"/"+v.outputFps).collect(java.util.stream.Collectors.joining("\n"));
    }
    static GraphicsTuning parse(JSONObject rule) throws JSONException {
        if(!rule.has("tuning")) return defaults();
        Object raw=rule.get("tuning");
        if(!(raw instanceof JSONObject o)) throw new JSONException("Expected tuning object");
        var d=defaults();
        return new GraphicsTuning(AppRule.flag(o,"frameCustom"),tuples(o,"boost",d.boost),tuples(o,"power",d.power),
                AppRule.flag(o,"srCustom"),integer(o,"algorithm",0),integer(o,"targetShortSide",0),integer(o,"sourceSize",-1),
                decimal(o,"sharpness",2.5),decimal(o,"darkLimit",-40),decimal(o,"whiteLimit",20));
    }
    private static int integer(JSONObject o,String key,int fallback) throws JSONException {
        if(!o.has(key)) return fallback;
        Object value=o.get(key);
        if(!(value instanceof Integer || value instanceof Long)) throw new JSONException("Expected integer: "+key);
        long n=((Number)value).longValue();
        if(n<Integer.MIN_VALUE || n>Integer.MAX_VALUE) throw new JSONException("Integer out of range");
        return (int)n;
    }
    private static double decimal(JSONObject o,String key,double fallback) throws JSONException {
        if(!o.has(key)) return fallback;
        if(!(o.get(key) instanceof Number n)) throw new JSONException("Expected number: "+key);
        return n.doubleValue();
    }
    private static List<FrameValue> tuples(JSONObject o,String key,List<FrameValue> fallback) throws JSONException {
        if(!o.has(key)) return fallback;
        JSONArray array=o.getJSONArray(key); var values=new ArrayList<FrameValue>();
        if(array.length()>12) throw new JSONException("Too many tuples");
        for(int i=0;i<array.length();i++) {
            JSONObject v=array.getJSONObject(i);
            values.add(new FrameValue(integer(v,"game",0),integer(v,"input",0),integer(v,"output",0)));
        }
        return values;
    }
    private static JSONArray jsonTuples(List<FrameValue> values) throws JSONException {
        JSONArray array=new JSONArray();
        for(var value:values) array.put(new JSONObject().put("game",value.gameFps).put("input",value.inputFps).put("output",value.outputFps));
        return array;
    }
    JSONObject json() throws JSONException {
        return new JSONObject().put("frameCustom",frameCustom).put("boost",jsonTuples(boost)).put("power",jsonTuples(power))
                .put("srCustom",srCustom).put("algorithm",algorithm).put("targetShortSide",targetShortSide).put("sourceSize",sourceSize)
                .put("sharpness",sharpness).put("darkLimit",darkLimit).put("whiteLimit",whiteLimit);
    }
}
