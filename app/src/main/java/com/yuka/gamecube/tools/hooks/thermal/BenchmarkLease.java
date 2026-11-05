package com.yuka.gamecube.tools.hooks.thermal;

import com.yuka.gamecube.tools.core.BenchmarkMode;
import java.util.function.Consumer;

/** Single-owner finite request; a killed/stalled host cannot renew it indefinitely. */
public final class BenchmarkLease {
    public static final int DURATION_MS=6000, RENEW_MS=2000;
    public interface Driver {
        int acquire(int duration,int[] resources) throws Throwable;
        int release(int handle) throws Throwable;
    }
    private final Driver driver;
    private final Consumer<String> log;
    private String pkg="";
    private int pid,mode,handle;
    private long renewAt;
    public BenchmarkLease(Driver driver,Consumer<String> log) { this.driver=driver;this.log=log; }
    public void update(String wanted,int process,int requested,long now) {
        BenchmarkMode.validate(requested);
        if(requested==0 || process<=0 || wanted==null || wanted.isEmpty()) { clear("inactive"); return; }
        if(!wanted.equals(pkg) || process!=pid || requested!=mode) {
            clear("selection_changed");pkg=wanted;pid=process;mode=requested;
        }
        if(now<renewAt) return;
        renewAt=now+RENEW_MS;
        try {
            int old=handle;
            int next=driver.acquire(DURATION_MS,BenchmarkMode.resources(mode));
            if(next<=0) {
                clear("acquire_rejected");return;
            }
            handle=next;
            // Binder-backed implementations can allocate a new handle instead of extending the old one.
            if(old>0 && old!=next) release(old,"replaced");
            log.accept("stage="+(old>0?"renew":"acquire")+" pkg="+pkg+" pid="+pid+" mode="+mode
                    +" handle="+handle+" duration_ms="+DURATION_MS+" key_requested=100 vendor_policy=not_observed"
                    +" cpu_max_requested="+(mode==BenchmarkMode.MAXIMUM)
                    +" gpu_min_pwrlevel_requested="+(mode==BenchmarkMode.MAXIMUM?"0":"none")+" hardware_effect=unverified");
        } catch(Throwable error) {
            log.accept("stage=acquire_failed error="+error);clear("acquire_failed");
        }
    }
    public void clear(String reason) {
        if(handle>0) release(handle,reason);
        handle=0;pkg="";pid=0;mode=0;renewAt=0;
    }
    private void release(int owned,String reason) {
        try {
            int result=driver.release(owned);
            log.accept("stage=release pkg="+pkg+" pid="+pid+" handle="+owned+" reason="+reason+" result="+result
                    +(result<0?" fallback=finite_lease_expiry":""));
        } catch(Throwable error) {
            log.accept("stage=release_failed handle="+owned+" reason="+reason+" fallback=finite_lease_expiry error="+error);
        }
    }
}
