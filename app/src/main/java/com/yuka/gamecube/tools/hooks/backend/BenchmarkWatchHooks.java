package com.yuka.gamecube.tools.hooks.backend;

import android.content.BroadcastReceiver;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import com.yuka.gamecube.tools.hooks.HookLog;
import com.yuka.gamecube.tools.hooks.Reflect;
import com.yuka.gamecube.tools.hooks.thermal.BenchmarkLease;
import com.yuka.gamecube.tools.hooks.thermal.BenchmarkForeground;

/** Uses OEM Java VPerf in GameWatch, without impersonating a benchmark package or touching its locks. */
final class BenchmarkWatchHooks {
    private final ThermalWatchHooks w;
    private final Handler handler;
    private final BenchmarkLease lease;
    private final BenchmarkForeground foreground=new BenchmarkForeground();
    private final Runnable tick=this::reconcile;
    private final Runnable wake=this::resume;
    private int wakeAttempts;
    BenchmarkWatchHooks(ThermalWatchHooks watch,Handler handler) throws Throwable {
        w=watch;this.handler=handler;
        Class<?> type=w.type("com.vivo.framework.vperf.VivoPerfManager");
        var acquire=Reflect.method(type,"perfLockAcquire",int.class,int[].class);
        var release=Reflect.method(type,"perfLockReleaseHandler",int.class);
        var releaseCurrent=Reflect.method(type,"perfLockRelease");
        if(acquire.getReturnType()!=int.class || release.getReturnType()!=int.class || releaseCurrent.getReturnType()!=int.class)
            throw new NoSuchMethodException("VPerf return type");
        // A dedicated instance retains its own mHandle; never use the OEM helper's shared owner map.
        Object owner=type.getDeclaredConstructor().newInstance();
        lease=new BenchmarkLease(new BenchmarkLease.Driver() {
            private int current;
            public int acquire(int duration,int[] resources) throws Throwable {
                try { current=(Integer)acquire.invoke(owner,duration,resources);return current; }
                catch(java.lang.reflect.InvocationTargetException e) { report("acquire",e.getCause());throw e.getCause(); }
            }
            public int release(int handle) throws Throwable {
                try {
                    // No-arg release resets the private native mHandle; explicit release is for a replaced lease.
                    if(handle==current) { current=0;return (Integer)releaseCurrent.invoke(owner); }
                    return (Integer)release.invoke(owner,handle);
                } catch(java.lang.reflect.InvocationTargetException e) { report("release",e.getCause());throw e.getCause(); }
            }
        },this::log);
        IntentFilter screen=new IntentFilter(Intent.ACTION_SCREEN_OFF);
        screen.addAction(Intent.ACTION_SCREEN_ON);screen.addAction(Intent.ACTION_USER_PRESENT);
        w.context.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context c,Intent i) {
                String action=i.getAction();
                handler.post(()->{
                    handler.removeCallbacks(wake);
                    if(Intent.ACTION_SCREEN_OFF.equals(action)) {
                        foreground.suspend();handler.removeCallbacks(tick);lease.clear("screen_off");
                        log("stage=screen_suspended candidate="+foreground.candidate());
                    } else { wakeAttempts=0;resume(); }
                });
            }
        },screen,Context.RECEIVER_NOT_EXPORTED);
        log("stage=bindings_ready lease_ms="+BenchmarkLease.DURATION_MS+" default=off native_hook=false");
    }
    void foreground(int userUid,boolean entered,int process,String name) {
        handler.post(()->{
            foreground.foreground(userUid,entered,process,name);
            log("stage=foreground uid="+userUid+" pid="+process+" pkg="+name+" entered="+entered);
            reconcile();
        });
    }
    void death(int userUid,int process,String name) {
        handler.post(()->{
            foreground.death(userUid,process,name);
            log("stage=process_death uid="+userUid+" pid="+process+" pkg="+name);reconcile();
        });
    }
    void reconcile() {
        handler.removeCallbacks(tick);
        try {
            var session=foreground.current();
            PowerManager power=w.context.getSystemService(PowerManager.class);
            KeyguardManager keyguard=w.context.getSystemService(KeyguardManager.class);
            int configured=session==null?0:w.benchmarkMode(session.pkg(),session.uid());
            if(configured!=0) foreground.remember();
            int mode=power!=null && power.isInteractive() && keyguard!=null && !keyguard.isKeyguardLocked()?configured:0;
            lease.update(session==null?"":session.pkg(),session==null?0:session.pid(),mode,SystemClock.elapsedRealtime());
            if(mode!=0) handler.postDelayed(tick,BenchmarkLease.RENEW_MS);
        } catch(Throwable error) {
            lease.clear("reconcile_failed");report("reconcile",error);
        }
    }
    private void resume() {
        handler.removeCallbacks(wake);
        var current=foreground.current();
        if(current!=null && current.uid()<10000) foreground.suspend();
        var candidate=foreground.candidate();
        if(candidate==null) return;
        try {
            PowerManager power=w.context.getSystemService(PowerManager.class);
            KeyguardManager keyguard=w.context.getSystemService(KeyguardManager.class);
            if(power==null || !power.isInteractive() || keyguard==null || keyguard.isKeyguardLocked()) {
                boolean retry=retryWake();
                log("stage="+(retry?"wake_wait":"wake_declined")+" pkg="+candidate.pkg()+" reason=screen_or_keyguard");return;
            }
            boolean allowed=w.benchmarkMode(candidate.pkg(),candidate.uid())!=0;
            if(!allowed) { log("stage=wake_declined pkg="+candidate.pkg()+" reason=policy_off");return; }
            int focusedUid=com.yuka.gamecube.tools.hooks.visual.VisualForeground.primaryUid(w.loader,candidate.pkg());
            ActivityManager am=w.context.getSystemService(ActivityManager.class);
            var processes=am==null?null:am.getRunningAppProcesses();
            boolean alive=false;
            if(processes!=null) for(var process:processes)
                if(process.pid==candidate.pid() && process.uid==candidate.uid() && (candidate.pkg().equals(process.processName)
                        || process.pkgList!=null && java.util.Arrays.asList(process.pkgList).contains(candidate.pkg()))) { alive=true;break; }
            boolean verified=alive && focusedUid==candidate.uid();
            if(foreground.resume(verified?candidate:null,allowed,true,true)) {
                log("stage=wake_resumed pkg="+candidate.pkg()+" pid="+candidate.pid()+" uid="+candidate.uid()+" identity_verified=true");
                reconcile();return;
            }
            if(!retryWake()) log("stage=wake_declined pkg="+candidate.pkg()+" pid="+candidate.pid()+" focused_uid="+focusedUid
                    +" alive="+alive+" reason=foreground_or_process_mismatch");
        } catch(Throwable failure) { report("wake_revalidation",failure); }
    }
    private boolean retryWake() {
        if(++wakeAttempts>=10) return false;
        handler.postDelayed(wake,200);return true;
    }
    private void log(String detail) {
        HookLog.write(w.module,android.app.Application.getProcessName(),"feature=benchmark "+detail+" revision="+w.revision(),null);
    }
    private void report(String stage,Throwable error) {
        HookLog.write(w.module,android.app.Application.getProcessName(),"feature=benchmark stage="+stage+"_failed",error);
    }
}
