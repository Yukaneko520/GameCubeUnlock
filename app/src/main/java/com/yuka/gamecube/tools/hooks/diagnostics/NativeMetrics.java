package com.yuka.gamecube.tools.hooks.diagnostics;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import com.yuka.gamecube.tools.hooks.HookRuntime;
import com.yuka.gamecube.tools.hooks.Reflect;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Two bounded read samples after an actual OEM command; never polls or modifies a FPS result. */
final class NativeMetrics {
    private final HookRuntime r;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService reads=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1),new ThreadPoolExecutor.DiscardOldestPolicy());
    private final AtomicLong generation=new AtomicLong();
    private volatile Sample nativeSample;
    private final java.util.List<Runnable> pending=new java.util.ArrayList<>();
    private record Sample(String pkg,int pid,long revision,float fps,long time) {}
    NativeMetrics(HookRuntime runtime) { r=runtime; }
    void rememberNative(String pkg,float fps) {
        try { nativeSample=new Sample(pkg,ownerPid(),r.snapshot().revision,fps,SystemClock.elapsedRealtime()); }
        catch (Throwable ignored) { nativeSample=null; }
    }
    synchronized void afterCommand(String pkg,String command,long revision) {
        for (Runnable task:pending) main.removeCallbacks(task);
        pending.clear();
        nativeSample=null;
        long token=generation.incrementAndGet();
        int pid=0;
        try { pid=Integer.parseInt(command.split(":")[2]); } catch (RuntimeException ignored) {}
        final int commandPid=pid;
        for (long delay : new long[]{600,1800}) {
            Runnable task=() -> {
                if (current(pkg,revision,token,commandPid)) reads.execute(() -> sample(pkg,command,revision,token,commandPid));
            };
            pending.add(task); main.postDelayed(task,delay);
        }
    }
    private boolean current(String pkg,long revision,long token,int pid) {
        if (generation.get()!=token || r.snapshot().revision!=revision || !pkg.equals(r.currentPackage()) || pid<=0) return false;
        try {
            return ownerPid()==pid;
        } catch (Throwable ignored) { return false; }
    }
    private int ownerPid() throws Throwable {
        Object owner=Reflect.staticCall(r.type("com.vivo.gameassistant.o"),"V");
        return ((Number)Reflect.call(owner,"N0")).intValue();
    }
    private void sample(String pkg,String command,long revision,long token,int pid) {
        if (!current(pkg,revision,token,pid)) return;
        try {
            Object manager=Reflect.staticCall(r.type("vivo.common.AbsVivoDisplayEnhanceManager"),"getInstance");
            Object vendor=Reflect.call(manager,"getDisplayEnhanceMemcFps");
            if (!current(pkg,revision,token,pid)) return;
            Sample current=nativeSample;
            String nativeValue=current!=null && current.pkg.equals(pkg) && current.pid==pid && current.revision==revision
                    && SystemClock.elapsedRealtime()-current.time<3000
                    ?Float.toString(current.fps):"unknown";
            String memcSelector=com.yuka.gamecube.tools.core.Compatibility.property("sys.game.memc.sw.enable");
            r.event("frame",pkg,"vendor_read", "command="+command+" memc_api="+vendor
                    +" native_observed="+nativeValue+" memc_selector="+memcSelector+" effect=unverified");
        } catch (Throwable error) {
            r.error("frame_vendor_read:"+pkg,error);
            r.event("frame",pkg,"vendor_read_unavailable", "reason="+error.getClass().getSimpleName()+" effect=unknown");
        }
    }
}
