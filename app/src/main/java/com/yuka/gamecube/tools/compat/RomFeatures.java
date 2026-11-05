package com.yuka.gamecube.tools.compat;

import com.yuka.gamecube.tools.core.AppRule;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
import com.yuka.gamecube.tools.core.GraphicsTuning;
import java.util.LinkedHashMap;
import java.util.Set;

/** Shipped PD2352 capabilities; unavailable settings never reach injected hosts. */
public final class RomFeatures {
    private static final Set<String> OS4_MISSING=Set.of("fps","superGyro","sr","nativeSr");
    private static final Set<String> KNOWN=Set.of("fps","hideGameCenter","touch","gyro","superTouch",
            "superGyro","audio","frame","sr","nativeSr","force144","monsterPlus","thermalBypass","benchmark","visual","fla");
    private RomFeatures() {}
    public static boolean available(int sdk,String feature) {
        return sdk>=34 && sdk<=36 && KNOWN.contains(feature) && (sdk!=34 || !OS4_MISSING.contains(feature))
                && (!feature.equals("fla") || sdk==36);
    }
    /** Preserve the saved intent; filter only the effective copy for this ROM. */
    public static ConfigSnapshot effective(ConfigSnapshot saved,int sdk) {
        var apps=new LinkedHashMap<String,AppRule>();
        for(var entry:saved.apps.entrySet()) {
            AppRule rule=entry.getValue();
            for(String feature:new String[]{"touch","gyro","superTouch","superGyro","audio","frame","sr",
                    "force144","monsterPlus","thermalBypass"})
                if(!available(sdk,feature) && rule.has(feature)) rule=rule.withFeature(feature,false);
            if(!available(sdk,"benchmark")) rule=rule.withBenchmarkMode(0);
            if(!available(sdk,"visual")) rule=rule.withFeature("visual",false);
            if(!available(sdk,"nativeSr") && rule.tuning.srCustom()) {
                var t=rule.tuning;
                rule=rule.withTuning(new GraphicsTuning(t.frameCustom(),t.boost(),t.power(),false,t.algorithm(),
                        t.targetShortSide(),t.sourceSize(),t.sharpness(),t.darkLimit(),t.whiteLimit()));
            }
            apps.put(entry.getKey(),rule);
        }
        return new ConfigSnapshot(saved.enabled,available(sdk,"fps")?saved.fpsMode:0,
                available(sdk,"hideGameCenter")&&saved.hideGameCenter,saved.revision,apps,
                available(sdk,"force144")&&saved.force144Global,saved.demonstrationMode,
                available(sdk,"fla")&&saved.disableFlaGlobal);
    }
}
