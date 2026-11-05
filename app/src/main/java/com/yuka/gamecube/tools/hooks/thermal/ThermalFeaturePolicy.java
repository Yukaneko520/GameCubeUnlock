package com.yuka.gamecube.tools.hooks.thermal;

import com.yuka.gamecube.tools.core.ConfigSnapshot;

/** Shared admission for feature gates; clock policies are outside this switch. */
public final class ThermalFeaturePolicy {
    private ThermalFeaturePolicy() {}
    public static boolean session(ConfigSnapshot config,String pkg,int pid,int expectedUid,int actualUid) {
        return pkg!=null && ConfigSnapshot.validPackage(pkg) && config.allows(pkg,"thermalBypass")
                && pid>0 && expectedUid>0 && expectedUid<100000 && expectedUid==actualUid;
    }
    public static boolean recording(boolean hot,boolean conflict,boolean powerSave,boolean extinguish,boolean bypass) {
        return !(conflict || powerSave || extinguish || hot && !bypass);
    }
    public static boolean thermalStop(int command,boolean temperatureOnly,boolean allowed) {
        return allowed && (temperatureOnly?command==4:command==256);
    }
    public static boolean legacyAutoplayEntry(int temperature,int battery,int plugged,boolean allowed) {
        return allowed && temperature>41 && !(battery<10 && plugged==0);
    }
    public static boolean autoplayReady(boolean initialized,boolean current,boolean samePackage,boolean samePid) {
        return initialized && current && samePackage && samePid;
    }
    public static boolean doublePlayEntry(boolean batteryAllowed,boolean powerSave,boolean allowed) {
        return allowed && batteryAllowed && !powerSave;
    }
    public record DoublePlayTick(int count,boolean expired) {}
    public static DoublePlayTick doublePlayTick(int previous) {
        int count=previous+1;
        return new DoublePlayTick(count,count>30);
    }
}
