package com.yuka.gamecube.tools.hooks.demo;

import com.yuka.gamecube.tools.core.AppRule;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
import java.util.LinkedHashMap;

/** Assistant-only projection; preserve the saved rules and reuse normal profile ownership/cleanup. */
public final class DemonstrationPolicy {
    public static ConfigSnapshot assistant(ConfigSnapshot saved) {
        if(!saved.enabled || !saved.demonstrationMode) return saved;
        var apps=new LinkedHashMap<String,AppRule>();
        saved.apps.forEach((pkg,original)->{
            AppRule rule=original;
            if(rule.enabled) for(String feature:new String[]{"touch","gyro","superTouch","superGyro","audio","frame","sr"})
                rule=rule.withFeature(feature,true);
            apps.put(pkg,rule);
        });
        return new ConfigSnapshot(saved.enabled,saved.fpsMode,saved.hideGameCenter,saved.revision,apps,
                saved.force144Global,saved.demonstrationMode,saved.disableFlaGlobal);
    }
    public static <T> java.util.List<T> visibleItems(java.util.List<T> shown,java.util.List<T> hidden,
                                                   java.util.function.Function<T,Object> identity) {
        var result=new java.util.ArrayList<T>();var seen=new java.util.HashSet<Object>();
        for(var source:java.util.List.of(shown,hidden)) for(T item:source) {
            Object key=identity.apply(item);
            if(key==null || seen.add(key)) result.add(item);
        }
        return result;
    }
    private DemonstrationPolicy() {}
}
