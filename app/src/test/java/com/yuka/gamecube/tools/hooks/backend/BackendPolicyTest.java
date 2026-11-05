package com.yuka.gamecube.tools.hooks.backend;

import com.yuka.gamecube.tools.core.AppRule;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class BackendPolicyTest {
    private static final String PKG="com.example.game";
    private ConfigSnapshot config(long revision, boolean sr) {
        return new ConfigSnapshot(true,0,false,revision,Map.of(PKG,
                AppRule.defaults().withEnabled(true).withFeature(sr?"sr":"frame",true)));
    }
    private BackendPolicy policy(boolean verified) {
        return new BackendPolicy(config(7,true),verified,Map.of(PKG,10123));
    }
    @Test public void nativeTrueAlwaysPreserved() {
        assertTrue(BackendPolicy.closed().admit(true,null,false,false,true,-1,false,-1));
    }
    @Test public void diagnosticReasonsMatchActualAdmissionAndSelectedCaller() {
        BackendPolicy p=policy(true);
        assertEquals("admitted",p.admissionReason(PKG,true,true,false,10123,false,0));
        assertTrue(p.selectedCaller(10123)); assertFalse(p.selectedCaller(10124));
        assertEquals("caller_or_user_mismatch",p.admissionReason(PKG,true,true,false,10124,false,0));
        assertEquals("caller_or_user_mismatch",p.admissionReason(PKG,true,true,false,1000,true,10));
        assertEquals("native_disabled_or_failed",p.admissionReason(PKG,true,true,true,10123,false,0));
        assertEquals("identity_unverified",policy(false).admissionReason(PKG,true,true,false,10123,false,0));
        assertEquals("installation_unverified",new BackendPolicy(config(7,true),true,Map.of(PKG,1000))
                .admissionReason(PKG,true,true,false,1000,false,0));
        assertFalse(new BackendPolicy(config(7,true),true,Map.of(PKG,1000)).admit(false,PKG,true,true,false,1000,false,0));
        assertEquals("sr_off",new BackendPolicy(config(7,false),true,Map.of(PKG,10123))
                .admissionReason(PKG,true,true,false,10123,false,0));
    }
    @Test public void exactSrTargetAndCallerOnly() {
        BackendPolicy p=policy(true);
        assertTrue(p.admit(false,PKG,true,true,false,10123,false,9));
        assertTrue(p.admit(false,PKG,true,true,false,1000,true,0));
        for(String other:new String[]{null,"",PKG+".other","Com.example.game","android","com.vivo.gamecube","com.yuka.gamecube.tools"})
            assertFalse(p.admit(false,other,true,true,false,10123,false,0));
        assertFalse(p.admit(false,PKG,true,true,false,10124,false,0));
        assertFalse(p.admit(false,PKG,true,true,false,110123,false,0));
        assertFalse(p.admit(false,PKG,true,true,false,1000,true,10));
    }
    @Test public void everyMissingPrerequisiteClosesExtension() {
        assertFalse(policy(false).admit(false,PKG,true,true,false,10123,false,0));
        assertFalse(policy(true).admit(false,PKG,false,true,false,10123,false,0));
        assertFalse(policy(true).admit(false,PKG,true,false,false,10123,false,0));
        assertFalse(policy(true).admit(false,PKG,true,true,true,10123,false,0));
        assertFalse(new BackendPolicy(config(1,false),true,Map.of(PKG,10123)).authorized(PKG));
        assertFalse(new BackendPolicy(config(1,true),true,Map.of()).authorized(PKG));
        assertFalse(new BackendPolicy(config(1,true),true,Map.of(PKG,1000)).authorized(PKG));
        assertFalse(new BackendPolicy(config(1,true),true,Map.of(PKG,110123)).authorized(PKG));
        assertFalse(new BackendPolicy(ConfigSnapshot.defaults(),true,Map.of(PKG,10123)).authorized(PKG));
    }
    @Test public void invalidWithdrawsButDoesNotResetRevisionFloor() {
        BackendPolicy.Revisions revisions=new BackendPolicy.Revisions();
        assertFalse(revisions.snapshot.enabled);
        assertTrue(revisions.accept(config(7,true),false));
        assertTrue(revisions.accept(null,true));
        assertFalse(revisions.snapshot.enabled);
        assertFalse(revisions.accept(config(6,true),false));
        assertFalse(revisions.snapshot.enabled);
        assertTrue(revisions.accept(config(7,false),false));
        assertFalse(revisions.snapshot.allows(PKG,"sr"));
        assertTrue(revisions.accept(config(7,true),false));
        assertTrue(revisions.snapshot.allows(PKG,"sr"));
        // Availability failure does not call accept: validated revision and policy stay intact.
        assertEquals(7,revisions.snapshot.revision);
    }
    @Test public void installedCacheIsImmutableAndInvalidationExact() {
        var uids=new java.util.HashMap<String,Integer>(); uids.put(PKG,10123);
        BackendPolicy p=new BackendPolicy(config(1,true),true,uids); uids.clear();
        assertTrue(p.authorized(PKG));
        assertFalse(p.without(PKG).authorized(PKG));
        assertTrue(p.without(PKG+".other").authorized(PKG));
        assertFalse(p.without("com.vivo.gamecube").authorized(PKG));
    }
    @Test public void envelopeRemovalMalformedAndWrongTypeWithdrawImmediately() {
        for(Object raw:new Object[]{null,"{broken",42,"{}"}) {
            var revisions=new BackendPolicy.Revisions();
            assertEquals(BackendPolicy.Read.VALID,revisions.read(config(9,true).toJson(),true));
            assertEquals(BackendPolicy.Read.INVALID,revisions.read(raw,true));
            assertFalse(revisions.snapshot.enabled);
            assertEquals(BackendPolicy.Read.STALE,revisions.read(config(8,true).toJson(),true));
            assertFalse(revisions.snapshot.enabled);
        }
    }
    @Test public void unavailableTransportRetainsActualValidatedSnapshot() {
        var revisions=new BackendPolicy.Revisions();
        assertEquals(BackendPolicy.Read.UNAVAILABLE,revisions.read(null,false));
        assertFalse(revisions.snapshot.enabled);
        revisions.read(config(11,true).toJson(),true);
        ConfigSnapshot validated=revisions.snapshot;
        assertEquals(BackendPolicy.Read.UNAVAILABLE,revisions.read(null,false));
        assertSame(validated,revisions.snapshot);
        assertEquals(BackendPolicy.Read.VALID,revisions.read(config(11,false).toJson(),true));
        assertFalse(revisions.snapshot.allows(PKG,"sr"));
    }
}
