package com.yuka.gamecube.tools.core;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ConfigSnapshot {
    public static final String GROUP = "gamecube_config", KEY = "snapshot";
    public final boolean enabled, hideGameCenter, force144Global, demonstrationMode, disableFlaGlobal;
    public final int fpsMode;
    public final long revision;
    public final Map<String, AppRule> apps;

    public ConfigSnapshot(boolean enabled,int fpsMode,boolean hideGameCenter,long revision,Map<String,AppRule> apps) {
        this(enabled,fpsMode,hideGameCenter,revision,apps,false);
    }
    public ConfigSnapshot(boolean enabled,int fpsMode,boolean hideGameCenter,long revision,Map<String,AppRule> apps,boolean force144Global) {
        this(enabled,fpsMode,hideGameCenter,revision,apps,force144Global,false);
    }
    public ConfigSnapshot(boolean enabled,int fpsMode,boolean hideGameCenter,long revision,Map<String,AppRule> apps,boolean force144Global,boolean demonstrationMode) {
        this(enabled,fpsMode,hideGameCenter,revision,apps,force144Global,demonstrationMode,false);
    }
    public ConfigSnapshot(boolean enabled,int fpsMode,boolean hideGameCenter,long revision,Map<String,AppRule> apps,boolean force144Global,boolean demonstrationMode,boolean disableFlaGlobal) {
        if (fpsMode<0 || fpsMode>2 || revision<0 || apps==null || apps.size()>2000) throw new IllegalArgumentException("Invalid configuration");
        for (var entry : apps.entrySet()) if (!validPackage(entry.getKey()) || entry.getValue()==null) throw new IllegalArgumentException("Invalid app rule");
        this.enabled=enabled; this.fpsMode=fpsMode; this.hideGameCenter=hideGameCenter; this.revision=revision;
        this.force144Global=force144Global;
        this.demonstrationMode=demonstrationMode;
        this.disableFlaGlobal=disableFlaGlobal;
        this.apps=Collections.unmodifiableMap(new LinkedHashMap<>(apps));
    }
    public static boolean validPackage(String p) { return p!=null && p.length()<=255 && (p.equals("android") || p.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")); }
    public static ConfigSnapshot defaults() { return new ConfigSnapshot(false,0,false,0,Map.of()); }
    public AppRule rule(String pkg) { return apps.getOrDefault(pkg, AppRule.defaults()); }
    public boolean allows(String pkg,String feature) { AppRule r=rule(pkg); return enabled && r.enabled && r.has(feature); }
    public boolean demonstrates(String pkg) { return enabled && demonstrationMode && rule(pkg).enabled; }
    public ConfigSnapshot withDemonstration(boolean value) { return new ConfigSnapshot(enabled,fpsMode,hideGameCenter,revision+1,apps,force144Global,value,disableFlaGlobal); }
    public ConfigSnapshot withGlobal(boolean master,int fps,boolean hide) { return new ConfigSnapshot(master,fps,hide,revision+1,apps,force144Global,demonstrationMode,disableFlaGlobal); }
    public ConfigSnapshot with144Global(boolean value) { return new ConfigSnapshot(enabled,fpsMode,hideGameCenter,revision+1,apps,value,demonstrationMode,disableFlaGlobal); }
    public ConfigSnapshot withFlaDisabled(boolean value) { return new ConfigSnapshot(enabled,fpsMode,hideGameCenter,revision+1,apps,force144Global,demonstrationMode,value); }
    public ConfigSnapshot withApp(String pkg,AppRule rule) { Map<String,AppRule> a=new LinkedHashMap<>(apps); a.put(pkg,rule); return new ConfigSnapshot(enabled,fpsMode,hideGameCenter,revision+1,a,force144Global,demonstrationMode,disableFlaGlobal); }
    public ConfigSnapshot withRevision(long value) { return new ConfigSnapshot(enabled,fpsMode,hideGameCenter,value,apps,force144Global,demonstrationMode,disableFlaGlobal); }
    public String toJson() {
        try {
            JSONObject a=new JSONObject(); for(var e:apps.entrySet()) a.put(e.getKey(),e.getValue().json());
            return new JSONObject().put("schema",1).put("revision",revision).put("enabled",enabled)
                    .put("fpsMode",fpsMode).put("hideGameCenter",hideGameCenter).put("force144Global",force144Global)
                    .put("demonstrationMode",demonstrationMode).put("disableFlaGlobal",disableFlaGlobal).put("apps",a).toString();
        } catch (JSONException e) { throw new IllegalStateException(e); }
    }
    public static ConfigSnapshot parse(String raw) throws JSONException {
        if (raw==null || raw.length()>512_000) throw new IllegalArgumentException("Configuration too large or absent");
        JSONObject o=new JSONObject(raw);
        if (number(o,"schema")!=1) throw new IllegalArgumentException("Unsupported schema");
        Map<String,AppRule> rules=new LinkedHashMap<>(); JSONObject a=o.getJSONObject("apps");
        for (var keys=a.keys(); keys.hasNext();) { String p=keys.next(); rules.put(p,AppRule.parse(a.getJSONObject(p))); }
        long fps=number(o,"fpsMode");
        if(fps<0 || fps>2) throw new IllegalArgumentException("Invalid FPS mode");
        if(!o.has("enabled") || !o.has("hideGameCenter")) throw new JSONException("Missing global flag");
        return new ConfigSnapshot(AppRule.flag(o,"enabled"),(int)fps,AppRule.flag(o,"hideGameCenter"),number(o,"revision"),rules,AppRule.flag(o,"force144Global"),AppRule.flag(o,"demonstrationMode"),AppRule.flag(o,"disableFlaGlobal"));
    }
    private static long number(JSONObject o,String key) throws JSONException {
        Object value=o.get(key);
        if(value instanceof Integer || value instanceof Long) return ((Number)value).longValue();
        throw new JSONException("Expected integer: "+key);
    }
}
