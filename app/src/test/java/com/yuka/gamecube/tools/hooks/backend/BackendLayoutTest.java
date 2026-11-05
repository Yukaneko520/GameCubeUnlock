package com.yuka.gamecube.tools.hooks.backend;

import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class BackendLayoutTest {
    @Test public void os5UsesActualLegacyStateAndNeverInventsPipelineOrVnssFields() {
        var old=BackendLayout.forSdk(35);var modern=BackendLayout.forSdk(36);
        assertEquals("isVSRWhitelistApp",old.alternativePredicate());
        assertEquals(List.of("SUPPORT_VSR"),old.competingCapabilities());
        assertEquals(List.of("mVSRRunning"),old.competingState());assertNull(old.disabledPackages());
        assertEquals("isVSRWhitelistAppFromGameWatch",modern.alternativePredicate());
        assertEquals(List.of("mVSRPipelineRunning","mVNSSRunning"),modern.competingState());
        assertThrows(IllegalArgumentException.class,()->BackendLayout.forSdk(37));
    }
    @Test public void legacyPpOwnershipRequiresExactInactiveAlternativeAndRetiresOnRouteChange() {
        Object controller=new Object();BackendLease lease=new BackendLease();
        var pp=new BackendLease.Session(controller,"com.example.game",0,1,Map.of("mVSRRunning",0));
        lease.begin();lease.complete(true,true,pp.pkg(),pp);
        assertTrue(lease.canClose(lease.current(),pp,false));
        assertFalse(new BackendLease.Session(controller,pp.pkg(),0,1,Map.of()).ppOnly());
        assertFalse(new BackendLease.Session(controller,pp.pkg(),0,1,Map.of("mVSRRunning",1)).ppOnly());
        var wrong=new BackendLease.Session(controller,pp.pkg(),0,1,Map.of("mVSRPipelineRunning",0,"mVNSSRunning",0));
        assertFalse(lease.canClose(lease.current(),wrong,false));
        lease.begin();lease.complete(true,true,pp.pkg(),wrong);
        assertFalse(lease.canClose(lease.current(),pp,false));
    }
}
