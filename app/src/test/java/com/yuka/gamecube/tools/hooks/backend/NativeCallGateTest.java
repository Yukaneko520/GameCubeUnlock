package com.yuka.gamecube.tools.hooks.backend;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.*;

public class NativeCallGateTest {
    @Test public void cannotArmUntilEarlierNativeCallCompletes() throws Throwable {
        NativeCallGate gate=new NativeCallGate();
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        AtomicReference<Throwable> error=new AtomicReference<>();
        Thread worker=thread(error,()->gate.run(()-> { entered.countDown(); await(release); return null; }));
        try { await(entered); assertFalse(gate.armIfIdle()); assertFalse(gate.armed()); }
        finally { release.countDown(); worker.join(2000); }
        assertNull(error.get()); assertTrue(gate.armIfIdle()); assertTrue(gate.armed());
    }
    @Test public void newNativeSessionCannotCrossFinalCheckAndOff() throws Throwable {
        NativeCallGate gate=new NativeCallGate(); assertTrue(gate.armIfIdle());
        List<String> events=Collections.synchronizedList(new ArrayList<>());
        CountDownLatch checked=new CountDownLatch(1),release=new CountDownLatch(1),newStarted=new CountDownLatch(1);
        AtomicReference<Throwable> error=new AtomicReference<>();
        Thread cleanup=thread(error,()->gate.run(()-> {
            events.add("old_checked"); checked.countDown(); await(release); events.add("old_off"); return null;
        }));
        await(checked);
        Thread next=thread(error,()-> { newStarted.countDown(); return gate.run(()-> { events.add("new_enable"); return null; }); });
        try { await(newStarted); assertEquals(List.of("old_checked"),events); }
        finally { release.countDown(); cleanup.join(2000); next.join(2000); }
        assertNull(error.get()); assertEquals(List.of("old_checked","old_off","new_enable"),events);
    }
    @Test public void originalExceptionIsPreservedAndDoesNotPoisonGate() throws Throwable {
        NativeCallGate gate=new NativeCallGate(); RuntimeException exact=new RuntimeException("native");
        try { gate.run(()-> { throw exact; }); fail(); } catch(RuntimeException result) { assertSame(exact,result); }
        assertTrue(gate.armIfIdle());
        try { gate.run(()-> { throw exact; }); fail(); } catch(RuntimeException result) { assertSame(exact,result); }
        assertEquals("still_available",gate.run(()->"still_available"));
    }
    private static void await(CountDownLatch latch) throws InterruptedException {
        assertTrue("worker timed out",latch.await(2,TimeUnit.SECONDS));
    }
    private static Thread thread(AtomicReference<Throwable> error,NativeCallGate.Call<?> work) {
        Thread worker=new Thread(()-> { try { work.run(); } catch(Throwable failure) { error.compareAndSet(null,failure); } });
        worker.start(); return worker;
    }
}
