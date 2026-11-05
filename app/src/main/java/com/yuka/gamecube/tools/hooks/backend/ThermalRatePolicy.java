package com.yuka.gamecube.tools.hooks.backend;

import java.util.HashMap;
import java.util.Map;

/** Ignore a heat ceiling for the selected foreground game; never synthesize a refresh-rate floor. */
final class ThermalRatePolicy {
    static boolean active(BackendPolicy policy) {
        if(!policy.verified || !policy.snapshot.enabled) return false;
        for(var rule:policy.snapshot.apps.values()) if(rule.enabled && rule.thermalBypass) return true;
        return false;
    }
    static boolean thermal(String scene,int priority,String reason) {
        return "Power".equals(scene) && priority==90050
                || "FixRate".equals(scene) && reason!=null && reason.startsWith("Thermal");
    }
    static boolean selected(BackendPolicy policy,DisplayRatePolicy.Window window) {
        return policy.verified && policy.snapshot.allows(window.pkg(),"thermalBypass")
                && DisplayRatePolicy.target(window.pkg()) && DisplayRatePolicy.primaryUid(window.uid())
                && java.util.Objects.equals(policy.installedUid(window.pkg()),window.uid())
                && window.focused() && window.foreground() && window.alive() && window.ownerMatches()
                && window.applicationMode() && !window.starting() && (window.type()==1 || window.type()==2)
                && window.displayId()==0 && window.screenOn();
    }
    static boolean subject(String reason,String requestedPackage,int requestedPid,int requestedUid,
                           String foregroundPackage,int foregroundPid,int foregroundUid) {
        if(foregroundPid<=0 || !DisplayRatePolicy.primaryUid(foregroundUid)) return false;
        if(reason!=null && reason.startsWith("Thermal:")) {
            String target=reason.substring(8).split("/",2)[0];
            if(!target.equals(foregroundPackage)) return false;
        }
        return requestedPackage==null || requestedUid>=0 && requestedUid<10000
                || requestedUid==foregroundUid && requestedPid==foregroundPid && foregroundPackage.equals(requestedPackage);
    }
    static boolean changed(BackendPolicy previous,BackendPolicy next) { return !selected(previous).equals(selected(next)); }
    private static Map<String,Integer> selected(BackendPolicy policy) {
        var result=new HashMap<String,Integer>();
        if(policy.verified && policy.snapshot.enabled) policy.snapshot.apps.forEach((pkg,rule)->{
            Integer uid=policy.installedUid(pkg);
            if(rule.enabled && rule.thermalBypass && uid!=null && DisplayRatePolicy.primaryUid(uid)) result.put(pkg,uid);
        });
        return result;
    }
    private ThermalRatePolicy() {}
}
