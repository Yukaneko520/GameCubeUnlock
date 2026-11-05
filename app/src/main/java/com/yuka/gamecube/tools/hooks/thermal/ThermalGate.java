package com.yuka.gamecube.tools.hooks.thermal;

import android.app.ActivityManager;
import android.os.Process;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
import com.yuka.gamecube.tools.hooks.HookRuntime;
import com.yuka.gamecube.tools.hooks.Reflect;

/** Per-game feature admission, including an explicitly owned background autoplay session. */
final class ThermalGate {
    private final HookRuntime r;
    record Session(ConfigSnapshot config,String pkg,int pid,int uid) {}
    ThermalGate(HookRuntime r) { this.r=r; }
    Session foreground(String pkg) {
        try {
            if(pkg==null || !r.snapshot().allows(pkg,"thermalBypass") || !pkg.equals(r.currentPackage())) return null;
            var owner=r.adapter.owner();
            int pid=(Integer)Reflect.call(Reflect.staticCall(r.type(owner.type()),owner.instance()),owner.pid());
            return session(pkg,pid);
        } catch(Throwable failure) { r.error("thermal_foreground_identity",failure);return null; }
    }
    Session foreground() { return foreground(r.currentPackage()); }
    Session session(String pkg,int pid) {
        ConfigSnapshot config=r.snapshot();
        if(pkg==null || !config.allows(pkg,"thermalBypass") || pid<=0) return null;
        try {
            int expected=r.context().getPackageManager().getApplicationInfo(pkg,0).uid;
            int actual=-1;
            try { actual=(Integer)Reflect.staticCall(Process.class,"getUidForPid",pid); } catch(Throwable unavailable) { /* OEM process list remains available. */ }
            if(actual<0) {
                ActivityManager am=r.context().getSystemService(ActivityManager.class);
                var processes=am==null?null:am.getRunningAppProcesses();
                if(processes!=null) for(var process:processes) if(process.pid==pid &&
                        (pkg.equals(process.processName) || process.pkgList!=null && java.util.Arrays.asList(process.pkgList).contains(pkg))) {
                    actual=process.uid;break;
                }
            }
            if(ThermalFeaturePolicy.session(config,pkg,pid,expected,actual)) return new Session(config,pkg,pid,expected);
            r.event("thermal",pkg,"feature_identity_declined","pid="+pid+" expected_uid="+expected+" actual_uid="+actual);
        } catch(Throwable failure) { r.error("thermal_session_identity",failure); }
        return null;
    }
    Session session(String pkg) {
        if(pkg==null || !r.snapshot().allows(pkg,"thermalBypass")) return null;
        try {
            ActivityManager am=r.context().getSystemService(ActivityManager.class);
            var processes=am==null?null:am.getRunningAppProcesses();
            if(processes!=null) for(var process:processes) if(pkg.equals(process.processName)) return session(pkg,process.pid);
        } catch(Throwable failure) { r.error("thermal_background_identity",failure); }
        return null;
    }
    Session ownedPid(int pid) {
        if(pid<=0 || !r.snapshot().enabled) return null;
        try {
            ActivityManager am=r.context().getSystemService(ActivityManager.class);
            var processes=am==null?null:am.getRunningAppProcesses();
            if(processes!=null) for(var process:processes) if(process.pid==pid) {
                if(r.snapshot().allows(process.processName,"thermalBypass")) return session(process.processName,pid);
                if(process.pkgList!=null && process.pkgList.length==1) return session(process.pkgList[0],pid);
                return null;
            }
        } catch(Throwable failure) { r.error("thermal_owned_pid",failure); }
        return null;
    }
    boolean valid(Session session) {
        if(session==null || session.config!=r.snapshot()) return false;
        var live=session(session.pkg,session.pid);
        return live!=null && live.uid==session.uid && live.config==session.config && live.config==r.snapshot();
    }
    void decision(Session session,String feature,Object original,Object replacement) {
        r.event("thermal",session.pkg,"feature_"+feature,"pid="+session.pid+" original="+original+" override="+replacement
                +" temperature_unchanged=true cpu_gpu_policy_unchanged=true");
    }
    void deopt(String name) throws Throwable {
        r.deoptimize(name);
    }
}
