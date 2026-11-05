package com.yuka.gamecube.tools.hooks.backend;

import com.yuka.gamecube.tools.core.*;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class BenchmarkPolicyTest {
    @Test public void verifiedIdentityExactUidMasterAndPerAppRuleAreAllRequired() {
        String pkg="com.example.game";
        var rule=AppRule.defaults().withEnabled(true).withBenchmarkMode(2);
        var config=new ConfigSnapshot(true,0,false,1,Map.of(pkg,rule));
        var policy=new BackendPolicy(config,true,Map.of(pkg,10042));
        assertEquals(2,policy.benchmarkMode(pkg,10042));
        assertEquals(0,policy.benchmarkMode(pkg,110042));
        assertEquals(0,policy.benchmarkMode("com.example.other",10042));
        assertEquals(0,policy.without(pkg).benchmarkMode(pkg,10042));
        assertEquals(0,new BackendPolicy(config,false,Map.of(pkg,10042)).benchmarkMode(pkg,10042));
        assertEquals(0,new BackendPolicy(config.withGlobal(false,0,false),true,Map.of(pkg,10042)).benchmarkMode(pkg,10042));
        assertEquals(0,new BackendPolicy(config.withApp(pkg,rule.withEnabled(false)),true,Map.of(pkg,10042)).benchmarkMode(pkg,10042));
        assertFalse(config.allows(pkg,"thermalBypass"));
    }
}
