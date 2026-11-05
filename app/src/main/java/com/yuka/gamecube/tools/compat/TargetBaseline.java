package com.yuka.gamecube.tools.compat;

import java.util.Map;
import java.util.Set;

/** Exact, immutable artifact identity. A matching SDK alone never grants hook admission. */
public record TargetBaseline(String id,int sdk,Set<String> models,long gameCubeCode,String gameCubeVersion,
                             String gameCubeSha,Map<String,String> frameworks,Map<String,String> packages,
                             boolean nativeSr,long gameWatchCode,String gameWatchVersion) {
    public TargetBaseline {
        models=Set.copyOf(models); frameworks=Map.copyOf(frameworks); packages=Map.copyOf(packages);
    }
    public boolean deviceMatches(String model,String product,int sdk,String displayIc) {
        return this.sdk==sdk && models.contains(model==null?"":model) && "PD2352".equals(product) && "".equals(displayIc);
    }
    public boolean apkMatches(long code,String name,String sha) {
        return gameCubeCode==code && gameCubeVersion.equals(name) && gameCubeSha.equals(sha);
    }
    public boolean gameWatchMatches(long code,String name,String sha) {
        return gameWatchCode==code && gameWatchVersion.equals(name)
                && packages.get("com.vivo.gamewatch").equals(sha);
    }
    public boolean dependenciesMatch(Map<String,String> actual) {
        return frameworks.entrySet().stream().allMatch(e->e.getValue().equals(actual.get(e.getKey())))
                && packages.entrySet().stream().allMatch(e->e.getValue().equals(actual.get(e.getKey())));
    }
}
