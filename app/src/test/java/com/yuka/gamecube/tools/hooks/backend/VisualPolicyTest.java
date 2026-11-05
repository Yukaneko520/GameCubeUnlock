package com.yuka.gamecube.tools.hooks.backend;

import com.yuka.gamecube.tools.core.*;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class VisualPolicyTest {
    private ConfigSnapshot config() {
        return new ConfigSnapshot(true,0,false,1,Map.of("flar2.devcheck",AppRule.defaults().withEnabled(true).withFeature("visual",true)));
    }
    @Test public void backendAdmissionRequiresVerifiedPrimaryUserAndExactOptIn() {
        var s=config();var p=new BackendPolicy(s,true,Map.of("flar2.devcheck",10398));
        assertTrue(p.visual("flar2.devcheck"));assertFalse(p.visual("flar2.devcheck.beta"));
        assertTrue(p.visual("flar2.devcheck",10398));assertFalse(p.visual("flar2.devcheck",110398));
        assertFalse(p.visual("flar2.devcheck",10399));assertFalse(p.visual("flar2.devcheck",-1));
        assertFalse(new BackendPolicy(s,false,Map.of("flar2.devcheck",10398)).visual("flar2.devcheck"));
        for(int uid:new int[]{1000,110398}) assertFalse(new BackendPolicy(s,true,Map.of("flar2.devcheck",uid)).visual("flar2.devcheck"));
        assertFalse(p.without("flar2.devcheck").visual("flar2.devcheck"));
        assertFalse(p.without("com.vivo.gamecube").visual("flar2.devcheck"));
        assertFalse(new BackendPolicy(s.withGlobal(false,0,false),true,Map.of("flar2.devcheck",10398)).visual("flar2.devcheck"));
    }
    @Test public void colorLeaseRejectsFallbackAndHonorsExternalChanges() {
        var lease=new VisualColorLease();assertFalse(lease.claim("flar2.devcheck",271,258));
        assertFalse(lease.matches(258));assertTrue(lease.claim("flar2.devcheck",271,271));
        assertTrue(lease.matches(271));assertFalse(lease.matches(272));
        lease.clear();assertFalse(lease.matches(271));
        assertFalse(lease.claim("flar2.devcheck",509,509));
    }
}
