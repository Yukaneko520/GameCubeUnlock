package com.yuka.gamecube.tools.hooks.backend;

import com.yuka.gamecube.tools.core.ConfigSnapshot;
import com.yuka.gamecube.tools.core.DevicePolicy;
import java.util.HashMap;
import java.util.Map;

/** Immutable hot-path policy. Installation, identity and parsing happen elsewhere. */
final class BackendPolicy {
    final ConfigSnapshot snapshot;
    final boolean verified;
    private final Map<String,Integer> installed;

    BackendPolicy(ConfigSnapshot snapshot,boolean verified,Map<String,Integer> installed) {
        this.snapshot=snapshot; this.verified=verified; this.installed=Map.copyOf(installed);
    }
    static BackendPolicy closed() { return new BackendPolicy(ConfigSnapshot.defaults(),false,Map.of()); }
    Integer installedUid(String pkg) { return installed.get(pkg); }
    boolean visual(String pkg) {
        Integer uid=installed.get(pkg);
        return verified && target(pkg) && uid!=null && uid>=10000 && uid<100000 && snapshot.allows(pkg,"visual");
    }
    boolean visual(String pkg,int uid) { return visual(pkg) && java.util.Objects.equals(installed.get(pkg),uid); }
    boolean control(String pkg,String feature) {
        if(!target(pkg) || !("superTouch".equals(feature)||"superGyro".equals(feature))) return false;
        Integer uid=installed.get(pkg);
        return verified && uid!=null && uid>=10000 && uid<100000
                && snapshot.allows(pkg,feature);
    }
    boolean controlSession(String pkg,String feature,String current,Object uid,boolean interactive) {
        return control(pkg,feature) && pkg.equals(current) && interactive && installed.get(pkg).equals(uid);
    }
    int benchmarkMode(String pkg,int uid) {
        return verified && target(pkg) && uid>=10000 && uid<100000 && java.util.Objects.equals(installed.get(pkg),uid)
                && snapshot.allows(pkg,"benchmark")?snapshot.rule(pkg).benchmarkMode:0;
    }
    static boolean target(String pkg) {
        return ConfigSnapshot.validPackage(pkg) && !pkg.equals(DevicePolicy.TARGET)
                && !pkg.equals("com.yuka.gamecube.tools") && !pkg.equals("android")
                && !pkg.startsWith("com.android.");
    }
    boolean authorized(String pkg) {
        if(!verified || !target(pkg)) return false;
        Integer uid=installed.get(pkg);
        return uid!=null && uid>=10000 && uid<100000 && snapshot.allows(pkg,"sr");
    }
    boolean selectedCaller(int uid) {
        for(var entry:installed.entrySet()) if(entry.getValue()==uid && snapshot.apps.containsKey(entry.getKey())) return true;
        return false;
    }
    String admissionReason(String pkg,boolean sw,boolean pp,boolean disabled,int uid,boolean local,int user) {
        if(!sw || !pp) return "native_capability";
        if(!verified) return "identity_unverified";
        if(!target(pkg)) return "target_excluded";
        Integer installedUid=installed.get(pkg);
        if(installedUid==null || installedUid<10000 || installedUid>=100000) return "installation_unverified";
        if(!snapshot.enabled) return "master_off";
        if(!snapshot.rule(pkg).enabled) return "rule_off";
        if(!snapshot.rule(pkg).sr) return "sr_off";
        if(disabled) return "native_disabled_or_failed";
        return (local?user==0:installed.get(pkg)==uid)?"admitted":"caller_or_user_mismatch";
    }
    boolean admit(boolean stock,String pkg,boolean sw,boolean pp,boolean disabled,int callerUid,
                  boolean local,int foregroundUser) {
        if(stock) return true;
        return admissionReason(pkg,sw,pp,disabled,callerUid,local,foregroundUser).equals("admitted");
    }
    BackendPolicy without(String pkg) {
        if(DevicePolicy.TARGET.equals(pkg)) return new BackendPolicy(snapshot,false,installed);
        Map<String,Integer> next=new HashMap<>(installed); next.remove(pkg);
        return new BackendPolicy(snapshot,verified,next);
    }
    enum Read { VALID,STALE,INVALID,UNAVAILABLE }
    static final class Publication {
        private volatile BackendPolicy current=closed();
        BackendPolicy current() { return current; }
        synchronized boolean publish(BackendPolicy next,long token,java.util.function.LongSupplier epoch) {
            if(epoch.getAsLong()!=token) return false;
            current=next; return true;
        }
        synchronized void invalidate(String pkg) { current=current.without(pkg); }
    }
    static final class Revisions {
        ConfigSnapshot snapshot=ConfigSnapshot.defaults();
        private long highest=-1;
        Read read(Object envelope,boolean available) {
            if(!available) return Read.UNAVAILABLE;
            try {
                if(!(envelope instanceof String raw)) throw new IllegalArgumentException("Absent/wrong type");
                return accept(ConfigSnapshot.parse(raw),false)?Read.VALID:Read.STALE;
            } catch(org.json.JSONException | IllegalArgumentException invalid) {
                accept(null,true); return Read.INVALID;
            }
        }
        boolean accept(ConfigSnapshot next,boolean invalid) {
            if(invalid) { snapshot=ConfigSnapshot.defaults(); return true; }
            if(next.revision<highest) return false;
            highest=Math.max(highest,next.revision); snapshot=next; return true;
        }
    }
}
