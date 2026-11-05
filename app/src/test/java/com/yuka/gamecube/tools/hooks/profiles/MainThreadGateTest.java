package com.yuka.gamecube.tools.hooks.profiles;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class MainThreadGateTest {
    @Test public void workerDefersCoalescesAndReadsFreshStateOnMain() throws Exception {
        ExecutorService main = Executors.newSingleThreadExecutor();
        CountDownLatch blocked = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicReference<Thread> mainThread = new AtomicReference<>();
        AtomicInteger state = new AtomicInteger(1), observed = new AtomicInteger(), runs = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try {
            main.submit(() -> { mainThread.set(Thread.currentThread()); blocked.countDown();
                try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
            assertTrue(blocked.await(5, TimeUnit.SECONDS));
            MainThreadGate gate = new MainThreadGate(() -> Thread.currentThread() == mainThread.get(),
                    main::execute, failure::set);
            MainThreadGate.Task refresh = () -> {
                assertSame(mainThread.get(), Thread.currentThread());
                observed.set(state.get()); runs.incrementAndGet();
                assertTrue(gate.check(() -> fail("main must not queue another refresh")));
            };
            assertFalse(gate.check(refresh));
            assertFalse(gate.check(refresh));
            assertEquals(0, runs.get());
            state.set(2); release.countDown();
            main.submit(() -> {}).get(5, TimeUnit.SECONDS);
            assertEquals(2, observed.get()); assertEquals(1, runs.get()); assertNull(failure.get());
        } finally { release.countDown(); main.shutdownNow(); }
    }
}
