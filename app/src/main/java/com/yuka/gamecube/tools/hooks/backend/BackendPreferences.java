package com.yuka.gamecube.tools.hooks.backend;

import android.content.SharedPreferences;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Worker-confined subscription recovery, independent of native-call gate activation. */
public final class BackendPreferences {
    public static final class ReconnectPending extends Exception {
        ReconnectPending() { super("Preferences reconnect cooldown",null,false,false); }
    }
    private final Supplier<SharedPreferences> open;
    private final ScheduledExecutorService worker;
    private final Runnable reload;
    private final Consumer<String> log;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener;
    private SharedPreferences prefs;
    private ScheduledFuture<?> retry;
    private boolean unavailable;

    public BackendPreferences(Supplier<SharedPreferences> open,ScheduledExecutorService worker,
                       Runnable reload,Consumer<String> log) {
        this.open=open; this.worker=worker; this.reload=reload; this.log=log;
        listener=(p,k)-> { if(k==null || ConfigSnapshot.KEY.equals(k)) reload.run(); };
    }
    public Object read() throws Throwable {
        if(prefs==null && retry!=null) throw new ReconnectPending();
        SharedPreferences candidate=prefs;
        try {
            if(candidate==null) {
                candidate=open.get();
                candidate.registerOnSharedPreferenceChangeListener(listener);
                prefs=candidate;
            }
            Object raw;
            try { raw=candidate.getString(ConfigSnapshot.KEY,null); }
            catch(ClassCastException invalid) { raw=null; }
            if(retry!=null) { retry.cancel(false); retry=null; }
            if(unavailable) log.accept("preferences connection recovered");
            unavailable=false;
            return raw;
        } catch(Throwable error) {
            prefs=null;
            if(candidate!=null) {
                try { candidate.unregisterOnSharedPreferenceChangeListener(listener); }
                catch(Throwable ignored) { /* A stale callback still coalesces through request(). */ }
            }
            retryLater();
            throw error;
        }
    }
    public void retryLater() {
        unavailable=true;
        if(retry!=null) return;
        retry=worker.schedule(()-> { retry=null; reload.run(); },5,TimeUnit.SECONDS);
        log.accept("preferences reconnect scheduled delay_ms=5000");
    }
}
