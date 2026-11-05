package com.yuka.gamecube.tools.hooks.backend;

import com.yuka.gamecube.tools.core.ConfigSnapshot;
import com.yuka.gamecube.tools.core.DevicePolicy;
import java.util.HashMap;
import java.util.Map;

/** No physical mode IDs, I/O, scene priority changes or SR capability assumptions. */
final class DisplayRatePolicy {
    record Selection(boolean global, Map<String,Integer> packages) {
        Selection { packages=Map.copyOf(packages); }
        static Selection closed() { return new Selection(false,Map.of()); }
        boolean wants(String pkg,int uid) { return global || packages.getOrDefault(pkg,-1)==uid; }
    }
    record Window(String pkg,int uid,int type,boolean focused,boolean foreground,boolean alive,
                  boolean applicationMode,boolean starting,boolean ownerMatches,
                  int displayId,boolean screenOn,boolean supports144) {}
    static boolean withdraws(Selection previous,Selection next) {
        return previous.global() && !next.global() || previous.packages().entrySet().stream()
                .anyMatch(entry->!java.util.Objects.equals(next.packages().get(entry.getKey()),entry.getValue()));
    }
    static boolean target(String pkg) {
        return ConfigSnapshot.validPackage(pkg) && !pkg.equals("android")
                && !pkg.equals("com.android.systemui") && !pkg.equals(DevicePolicy.TARGET);
    }
    static Selection select(BackendPolicy policy) {
        var config=policy.snapshot;
        if(!policy.verified || !config.enabled) return Selection.closed();
        if(config.force144Global) return new Selection(true,Map.of());
        Map<String,Integer> packages=new HashMap<>();
        config.apps.forEach((pkg,rule)-> {
            Integer uid=policy.installedUid(pkg);
            if(target(pkg) && rule.enabled && rule.force144 && uid!=null && primaryUid(uid)) packages.put(pkg,uid);
        });
        return new Selection(false,packages);
    }
    static boolean primaryUid(int uid) { return uid>=10000 && uid<99000; }
    static String reason(Selection selection,Window w) {
        if(!target(w.pkg)) return "excluded_package";
        if(!primaryUid(w.uid) || !w.ownerMatches) return "owner_or_user";
        if(w.type!=1 && w.type!=2 || w.starting || !w.applicationMode) return "special_window";
        if(!w.foreground || !w.alive) return "not_foreground";
        if(w.displayId!=0 || !w.screenOn || !w.supports144) return "display_unavailable";
        return selection.wants(w.pkg,w.uid)?"requested":"not_selected";
    }
    static boolean applicationMode(int mode) {
        return mode==1 || mode==2 || mode==5 || mode==6 || mode==106;
    }
    static int sceneFps(boolean selected,String scene,int priority,int original) {
        return selected && original>0 && normalScene(scene,priority)?144:original;
    }
    static int modeSwitchType(boolean selected,String scene,int priority,int mappedFps,int modeFps,int original) {
        return selected && mappedFps==144 && modeFps==144 && normalScene(scene,priority)?0:original;
    }
    static boolean privateMode(boolean original,boolean selected,String scene,int priority,int realFps) {
        return original || selected && realFps==144 && normalScene(scene,priority);
    }
    private static boolean normalScene(String scene,int priority) {
        int base=switch(scene) { case "Interaction"->70000; case "AppRequest"->80000;
            case "Animation"->100000; default->-1; };
        return base>0 && priority>=base-100 && priority<=base+100;
    }
    private DisplayRatePolicy() {}
}
