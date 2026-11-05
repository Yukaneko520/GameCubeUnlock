package com.yuka.gamecube.tools.hooks;

import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import io.github.libxposed.api.XposedModule;
import com.yuka.gamecube.tools.BuildConfig;
import java.util.concurrent.atomic.AtomicLong;

/** Both existing log sinks receive the same build/session stamp and full exception stack. */
public final class HookLog {
    private static final AtomicLong sequence=new AtomicLong();
    private static volatile HookJournal journal;
    private HookLog() {}
    public static synchronized void startJournal(java.util.function.Supplier<java.io.File> directory,String name) {
        if(journal==null) journal=new HookJournal(directory,name,512_000);
    }
    public static void startSystemJournal() {
        startJournal(()->{
            java.io.File folder=new java.io.File("/data/system/gamecube-tools");
            if(!folder.isDirectory()) {
                if(!folder.mkdirs() && !folder.isDirectory()) throw new IllegalStateException("System journal unavailable");
                folder.setReadable(false,false);folder.setWritable(false,false);folder.setExecutable(false,false);
                folder.setReadable(true,true);folder.setWritable(true,true);folder.setExecutable(true,true);
            }
            return folder;
        },"gamecube-tools-system.log");
    }
    public static void write(XposedModule module,String source,String message,Throwable error) {
        String line="build="+BuildConfig.VERSION_NAME+"/"+BuildConfig.VERSION_CODE
                +" pid="+Process.myPid()+" tid="+Process.myTid()+" uptime_ms="+SystemClock.elapsedRealtime()
                +" seq="+sequence.incrementAndGet()+" source="+source+" "+message;
        int priority=error==null?Log.INFO:Log.ERROR;
        HookJournal saved=journal;
        if(saved!=null) saved.record(System.currentTimeMillis(),Process.myPid(),Process.myTid(),
                error==null?'I':'E',line,error);
        try { if(error==null) Log.i("GameCubeUnlock",line); else Log.e("GameCubeUnlock",line,error); }
        catch(Throwable ignored) {}
        try { module.log(priority,"GameCubeUnlock",line,error); } catch(Throwable ignored) {}
    }
}
