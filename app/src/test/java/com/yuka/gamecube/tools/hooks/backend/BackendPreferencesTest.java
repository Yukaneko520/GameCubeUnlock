package com.yuka.gamecube.tools.hooks.backend;

import android.content.SharedPreferences;
import com.yuka.gamecube.tools.core.AppRule;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Delayed;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class BackendPreferencesTest {
    private static final String PKG="com.example.game";
    private static String config(long revision,boolean enabled) {
        return new ConfigSnapshot(true,0,false,revision,Map.of(PKG,
                AppRule.defaults().withEnabled(true).withFeature("sr",enabled))).toJson();
    }
    private static final class Remote {
        Object value=config(7,true);
        boolean failRegister,failRead;
        int registrations,detachments;
        SharedPreferences.OnSharedPreferenceChangeListener listener;
        final SharedPreferences prefs=(SharedPreferences)Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},(p,m,args)-> {
                    return switch(m.getName()) {
                        case "registerOnSharedPreferenceChangeListener" -> {
                            registrations++; listener=(SharedPreferences.OnSharedPreferenceChangeListener)args[0];
                            if(failRegister) throw new IllegalStateException("subscription unavailable");
                            yield null;
                        }
                        case "unregisterOnSharedPreferenceChangeListener" -> { detachments++; listener=null; yield null; }
                        case "getString" -> {
                            if(failRead) throw new IllegalStateException("read unavailable");
                            yield (String)value;
                        }
                        default -> throw new AssertionError("Unexpected preference operation: "+m.getName());
                    };
                });
        void change(String key) { if(listener!=null) listener.onSharedPreferenceChanged(prefs,key); }
    }
    private static final class Task extends FutureTask<Void> implements ScheduledFuture<Void> {
        Task(Runnable command) { super(command,null); }
        public long getDelay(TimeUnit unit) { return unit.convert(5,TimeUnit.SECONDS); }
        public int compareTo(Delayed other) { return Long.compare(getDelay(TimeUnit.NANOSECONDS),other.getDelay(TimeUnit.NANOSECONDS)); }
    }
    private static final class Scheduler extends ScheduledThreadPoolExecutor {
        final ArrayList<Task> tasks=new ArrayList<>();
        Scheduler() { super(1); }
        @Override public ScheduledFuture<?> schedule(Runnable command,long delay,TimeUnit unit) {
            assertEquals(5000,unit.toMillis(delay)); Task task=new Task(command); tasks.add(task); return task;
        }
        long pending() { return tasks.stream().filter(t->!t.isDone()).count(); }
        void tick() { tasks.stream().filter(t->!t.isDone()).findFirst().orElseThrow().run(); }
    }
    private static void fails(BackendPreferences preferences) {
        try { preferences.read(); fail("Expected transport failure"); }
        catch(IllegalStateException expected) { }
        catch(BackendPreferences.ReconnectPending expected) { }
        catch(Throwable unexpected) { throw new AssertionError(unexpected); }
    }
    @Test public void initialFailureRecoversWithoutNativeCallsAndReceivesLaterDisable() throws Throwable {
        Scheduler scheduler=new Scheduler(); Remote remote=new Remote(); AtomicInteger opens=new AtomicInteger(),reloads=new AtomicInteger();
        BackendPreferences prefs=new BackendPreferences(()-> {
            if(opens.incrementAndGet()==1) throw new IllegalStateException("framework not ready");
            return remote.prefs;
        },scheduler,reloads::incrementAndGet,s->{});
        NativeCallGate gate=new NativeCallGate(); assertTrue(gate.armIfIdle());
        fails(prefs); assertEquals(1,scheduler.pending());
        scheduler.tick(); assertEquals(1,reloads.get());
        BackendPolicy.Revisions revisions=new BackendPolicy.Revisions();
        assertEquals(BackendPolicy.Read.VALID,revisions.read(prefs.read(),true));
        assertTrue(revisions.snapshot.allows(PKG,"sr")); assertEquals(0,scheduler.pending());
        remote.value=config(8,false); remote.change(ConfigSnapshot.KEY);
        assertEquals(2,reloads.get()); revisions.read(prefs.read(),true);
        assertFalse(revisions.snapshot.allows(PKG,"sr")); assertEquals(1,remote.registrations);
        remote.change("unrelated"); assertEquals(2,reloads.get()); remote.change(null); assertEquals(3,reloads.get());
    }
    @Test public void partialSubscriptionFailureDetachesAndCoalescesRetry() throws Throwable {
        Scheduler scheduler=new Scheduler(); Remote remote=new Remote(); remote.failRegister=true;
        BackendPreferences prefs=new BackendPreferences(()->remote.prefs,scheduler,()->{},s->{});
        fails(prefs); fails(prefs); prefs.retryLater();
        assertEquals(1,scheduler.pending()); assertEquals(1,remote.detachments); assertNull(remote.listener);
        assertEquals(1,remote.registrations);
        scheduler.tick(); fails(prefs); assertEquals(2,remote.registrations); assertEquals(1,scheduler.pending());
        scheduler.tick();
        remote.failRegister=false; assertEquals(remote.value,prefs.read());
        assertEquals(0,scheduler.pending()); assertEquals(3,remote.registrations);
        scheduler.tasks.getFirst().run(); assertEquals(0,scheduler.pending());
        prefs.retryLater(); assertEquals(1,scheduler.pending());
        assertEquals(remote.value,prefs.read()); assertEquals(0,scheduler.pending());
    }
    @Test public void transportFailureRetainsRevisionThenReconnectsToMalformedRevocation() throws Throwable {
        Scheduler scheduler=new Scheduler(); Remote remote=new Remote();
        BackendPreferences prefs=new BackendPreferences(()->remote.prefs,scheduler,()->{},s->{});
        BackendPolicy.Revisions revisions=new BackendPolicy.Revisions(); revisions.read(prefs.read(),true);
        ConfigSnapshot validated=revisions.snapshot; remote.failRead=true;
        fails(prefs); revisions.read(null,false); assertSame(validated,revisions.snapshot);
        assertEquals(1,remote.detachments); assertEquals(1,scheduler.pending());
        remote.failRead=false; remote.value="{malformed"; scheduler.tick();
        assertEquals(BackendPolicy.Read.INVALID,revisions.read(prefs.read(),true));
        assertFalse(revisions.snapshot.enabled); assertEquals(0,scheduler.pending());
        remote.value=config(6,true); assertEquals(BackendPolicy.Read.STALE,revisions.read(prefs.read(),true));
        assertFalse(revisions.snapshot.enabled);
    }
    @Test public void wrongTypeAndRemovalWithdrawWithoutConnectionRetry() throws Throwable {
        for(Object invalid:new Object[]{42,null}) {
            Scheduler scheduler=new Scheduler(); Remote remote=new Remote(); remote.value=invalid;
            BackendPreferences prefs=new BackendPreferences(()->remote.prefs,scheduler,()->{},s->{});
            BackendPolicy.Revisions revisions=new BackendPolicy.Revisions(); revisions.read(config(7,true),true);
            assertEquals(BackendPolicy.Read.INVALID,revisions.read(prefs.read(),true));
            assertFalse(revisions.snapshot.enabled); assertEquals(0,scheduler.pending()); assertEquals(0,remote.detachments);
        }
    }
}
