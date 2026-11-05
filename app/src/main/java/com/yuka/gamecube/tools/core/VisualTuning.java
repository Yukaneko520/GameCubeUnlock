package com.yuka.gamecube.tools.core;

import org.json.JSONException;
import org.json.JSONObject;

/** Visual routing; legacy Iris fields remain readable for old configuration compatibility only. */
public record VisualTuning(boolean enabled,int route,int profile,int filterMode,int preset,
                           int hue,int saturation,int contrast,int lightness,int colorMode) {
    public VisualTuning {
        if(route<0 || route>1 || profile< -1 || profile>10 || filterMode<0 || filterMode>2
                || preset<10 || preset>17 || hue< -90 || hue>90 || saturation<0 || saturation>255
                || contrast<0 || contrast>100 || lightness< -64 || lightness>64
                || colorMode<270 || colorMode>273) throw new IllegalArgumentException("Invalid visual tuning");
    }
    public static VisualTuning defaults() { return new VisualTuning(false,0,-1,0,13,0,128,50,0,271); }
    public VisualTuning withEnabled(boolean value) {
        return new VisualTuning(value,route,profile,filterMode,preset,hue,saturation,contrast,lightness,colorMode);
    }
    static VisualTuning parse(JSONObject rule) throws JSONException {
        if(!rule.has("visual")) return defaults();
        JSONObject o=rule.getJSONObject("visual");var d=defaults();
        return new VisualTuning(AppRule.flag(o,"enabled"),number(o,"route",d.route),number(o,"profile",d.profile),
                number(o,"filterMode",d.filterMode),number(o,"preset",d.preset),number(o,"hue",d.hue),
                number(o,"saturation",d.saturation),number(o,"contrast",d.contrast),
                number(o,"lightness",d.lightness),number(o,"colorMode",d.colorMode));
    }
    private static int number(JSONObject o,String key,int fallback) throws JSONException {
        if(!o.has(key)) return fallback;
        if(o.get(key) instanceof Integer value) return value;
        throw new JSONException("Expected integer: visual."+key);
    }
    JSONObject json() throws JSONException {
        return new JSONObject().put("enabled",enabled).put("route",route).put("profile",profile)
                .put("filterMode",filterMode).put("preset",preset).put("hue",hue).put("saturation",saturation)
                .put("contrast",contrast).put("lightness",lightness).put("colorMode",colorMode);
    }
}
