package com.yuka.gamecube.tools.hooks.backend;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import io.github.libxposed.api.XposedModule;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
import com.yuka.gamecube.tools.core.DevicePolicy;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.BiConsumer;

/** Retained event transport. All parsing, package queries and hashes run on one worker. */
final class BackendConfig {
    interface Sink {
        void apply(BackendPolicy policy,long token,java.util.function.LongSupplier epoch);
        void invalidate(String pkg);
    }
    private final Context context;
    private final Handler owner;
    private final Sink sink;
    private final Consumer<String> log;
    private final BiConsumer<String,Throwable> error;
    private final java.util.concurrent.ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
    private final AtomicLong epoch=new AtomicLong();
    private final AtomicBoolean queued=new AtomicBoolean();
    private final BackendPolicy.Revisions revisions=new BackendPolicy.Revisions();
    private final BackendIdentity identity=new BackendIdentity();
    private final BackendPreferences preferences;
    private final java.util.function.Predicate<String> targets;
    private final java.util.function.Predicate<android.content.pm.ApplicationInfo> applications;
    private boolean receiverRegistered;
    private final BroadcastReceiver receiver=new BroadcastReceiver() {
        @Override public void onReceive(Context c,Intent intent) {
            if(intent.getIntExtra("android.intent.extra.user_handle",0)!=0 || intent.getData()==null) return;
            String pkg=intent.getData().getSchemeSpecificPart();
            // Synchronous closure precedes the worker read and invalidates already posted results.
            epoch.incrementAndGet();
            boolean dependency=DevicePolicy.TARGET.equals(pkg) || com.yuka.gamecube.tools.compat.TargetProfiles.ALL.stream()
                    .anyMatch(baseline->baseline.packages().containsKey(pkg));
            if(dependency) {identity.invalidate();sink.invalidate(DevicePolicy.TARGET);}
            else sink.invalidate(pkg);
            request();
        }
    };
    BackendConfig(XposedModule module,Context context,Handler owner,Sink sink,Consumer<String> log,BiConsumer<String,Throwable> error) {
        this(module,context,owner,sink,BackendPolicy::target,BackendIdentity::userApplication,log,error);
    }
    BackendConfig(XposedModule module,Context context,Handler owner,Sink sink,
                  java.util.function.Predicate<String> targets,
                  java.util.function.Predicate<android.content.pm.ApplicationInfo> applications,
                  Consumer<String> log,BiConsumer<String,Throwable> error) {
        this.context=context; this.owner=owner; this.sink=sink; this.log=log; this.error=error;
        this.targets=targets; this.applications=applications;
        preferences=new BackendPreferences(()->module.getRemotePreferences(ConfigSnapshot.GROUP),worker,this::request,log);
    }
    void request() {
        epoch.incrementAndGet(); enqueue();
    }
    private void enqueue() {
        if(!queued.compareAndSet(false,true)) return;
        worker.execute(()-> {
            long token=-1;
            try {
                do { token=epoch.get(); reload(token); } while(epoch.get()!=token);
            } finally {
                queued.set(false);
                if(epoch.get()!=token) enqueue();
            }
        });
    }
    private void reload(long token) {
        try {
            if(!receiverRegistered) {
                IntentFilter filter=new IntentFilter(); filter.addDataScheme("package");
                filter.addAction(Intent.ACTION_PACKAGE_ADDED); filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
                filter.addAction(Intent.ACTION_PACKAGE_REPLACED); filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
                context.registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED); receiverRegistered=true;
            }
            Object raw=preferences.read();
            var read=revisions.read(raw,true);
            log.accept("config read="+read+" revision="+revisions.snapshot.revision+" epoch="+token);
            if(read==BackendPolicy.Read.INVALID) { withdraw(token); return; }
        } catch(BackendPreferences.ReconnectPending pending) {
            revisions.read(null,false);
        } catch(Throwable unavailable) {
            // Read availability is not a revocation. Still revalidate package changes against last config.
            log.accept("preferences unavailable; retain validated revision");
            error.accept("preferences_unavailable",unavailable);
            revisions.read(null,false);
            preferences.retryLater();
        }
        boolean verified=false;
        try { verified=receiverRegistered && identity.verify(context,log); }
        catch(Throwable failure) { identity.invalidate(); error.accept("identity_unavailable",failure); preferences.retryLater(); }
        var installed=new HashMap<String,Integer>();
        for(String pkg:revisions.snapshot.apps.keySet()) {
            if(!targets.test(pkg)) continue;
            try {
                var info=context.getPackageManager().getApplicationInfo(pkg,0);
                if(applications.test(info)) installed.put(pkg,info.uid);
                log.accept("target pkg="+pkg+" uid="+info.uid+" user_app="+BackendIdentity.userApplication(info)
                        +" target_admitted="+applications.test(info)+" enabled="+revisions.snapshot.rule(pkg).enabled
                        +" sr="+revisions.snapshot.rule(pkg).sr+" force144="+revisions.snapshot.rule(pkg).force144);
            } catch(Throwable unavailable) { error.accept("target_unavailable:"+pkg,unavailable); }
        }
        post(token,new BackendPolicy(com.yuka.gamecube.tools.compat.RomFeatures.effective(
                revisions.snapshot,android.os.Build.VERSION.SDK_INT),verified,installed));
    }
    private void withdraw(long token) {
        revisions.accept(null,true);
        post(token,BackendPolicy.closed()); log.accept("invalid configuration; withdraw admission");
    }
    private void post(long token,BackendPolicy next) {
        owner.post(()->sink.apply(next,token,epoch::get));
    }
}
