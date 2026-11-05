package com.yuka.gamecube.tools.hooks.backend;

import com.yuka.gamecube.tools.core.AppRule;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
import com.yuka.gamecube.tools.core.DevicePolicy;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.*;

public class BackendPublicationTest {
    private static final String PKG="com.example.game";
    private static BackendPolicy selected() {
        return new BackendPolicy(new ConfigSnapshot(true,0,false,7,Map.of(PKG,
                AppRule.defaults().withEnabled(true).withFeature("sr",true))),true,Map.of(PKG,10123));
    }
    @Test public void resultWaitingForNativeGateCannotReopenInvalidatedPackage() throws Exception {
        BackendPolicy.Publication state=new BackendPolicy.Publication();
        AtomicLong epoch=new AtomicLong(1);
        assertTrue(state.publish(selected(),1,epoch::get));
        CountDownLatch waiting=new CountDownLatch(1),release=new CountDownLatch(1);
        AtomicReference<Throwable> failure=new AtomicReference<>();
        Thread old=new Thread(()-> {
            try {
                waiting.countDown(); assertTrue(release.await(2,TimeUnit.SECONDS));
                assertFalse(state.publish(selected(),1,epoch::get));
            } catch(Throwable error) { failure.set(error); }
        });
        old.start();
        try {
            assertTrue(waiting.await(2,TimeUnit.SECONDS));
            epoch.incrementAndGet(); state.invalidate(PKG);
            assertFalse(state.current().authorized(PKG));
        } finally { release.countDown(); old.join(2000); }
        assertNull(failure.get()); assertFalse(state.current().authorized(PKG));
        assertTrue(state.publish(selected(),2,epoch::get));
        assertTrue(state.current().authorized(PKG));
    }
    @Test public void gameCubeInvalidationClosesIdentityUntilFreshPublication() {
        BackendPolicy.Publication state=new BackendPolicy.Publication(); AtomicLong epoch=new AtomicLong(1);
        assertTrue(state.publish(selected(),1,epoch::get));
        epoch.incrementAndGet(); state.invalidate(DevicePolicy.TARGET);
        assertFalse(state.current().verified); assertFalse(state.current().authorized(PKG));
        assertFalse(state.publish(selected(),1,epoch::get));
        assertTrue(state.publish(BackendPolicy.closed(),2,epoch::get));
        assertFalse(state.current().authorized(PKG));
    }
}
