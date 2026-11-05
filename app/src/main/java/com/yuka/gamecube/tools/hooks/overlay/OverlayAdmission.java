package com.yuka.gamecube.tools.hooks.overlay;

import com.yuka.gamecube.tools.core.ConfigSnapshot;
import java.util.ArrayList;
import java.util.List;

/** Extend the returned runtime list, never the OEM's stored selection. */
final class OverlayAdmission {
    static boolean selected(ConfigSnapshot config,String pkg) {
        return config.enabled && config.rule(pkg).enabled;
    }
    static boolean handoff(ConfigSnapshot config,String pkg,String current,int requestedPid,int livePid,int user,boolean master) {
        return selected(config,pkg) && master && pkg.equals(current) && requestedPid>0 && requestedPid==livePid
                && primaryUser(user);
    }
    static boolean primaryUser(int value) {
        // OEM task maps contain a user prefix (0 for primary), while PID fallbacks return a full UID.
        return value==0 || value>=1000 && value<100000;
    }
    static List<String> extend(List<String> stock,ConfigSnapshot config,String excluded) {
        ArrayList<String> result=null;
        if(config.enabled) for(var entry:config.apps.entrySet()) {
            String pkg=entry.getKey();
            if(!entry.getValue().enabled || pkg.equals(excluded) || (stock!=null && stock.contains(pkg))) continue;
            if(result==null) result=stock==null?new ArrayList<>():new ArrayList<>(stock);
            result.add(pkg);
        }
        return result==null?stock:result;
    }
    private OverlayAdmission() {}
}
