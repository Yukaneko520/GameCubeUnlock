package com.yuka.gamecube.tools.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class BenchmarkRuleTest {
    @Test public void oldSchemaDefaultsOffAndBothModesRoundTrip() throws Exception {
        assertEquals(0,AppRule.parse(new org.json.JSONObject("{}" )).benchmarkMode);
        for(int mode=0;mode<=2;mode++) {
            var rule=AppRule.defaults().withBenchmarkMode(mode);
            assertEquals(rule,AppRule.parse(rule.json()));
            assertEquals(mode!=0,rule.has("benchmark"));
        }
    }
    @Test public void allEditsPreserveModeAndModeParticipatesInDirtyState() {
        var rule=AppRule.defaults().withBenchmarkMode(2);
        assertEquals(2,rule.withEnabled(true).withFeature("frame",true).withDonor("sr","com.example.game")
                .withTuning(GraphicsTuning.defaults()).withFeature("thermalBypass",true).benchmarkMode);
        assertNotEquals(rule,rule.withBenchmarkMode(1));
        assertNotEquals(rule.hashCode(),rule.withBenchmarkMode(1).hashCode());
        assertEquals(AppRule.defaults(),rule.withBenchmarkMode(0));
    }
    @Test public void invalidWireModesAreRejected() {
        for(String value:new String[]{"-1","3","1.0","true","\"1\"","null","4294967297"})
            assertThrows(Exception.class,()->AppRule.parse(new org.json.JSONObject("{\"benchmarkMode\":"+value+"}")));
        assertThrows(IllegalArgumentException.class,()->AppRule.defaults().withBenchmarkMode(3));
    }
    @Test public void automaticDoesNotVoteClocksAndMaximumIncludesGpuFloor() {
        assertArrayEquals(new int[0],BenchmarkMode.resources(0));
        assertArrayEquals(new int[]{0xF0100000,100},BenchmarkMode.resources(1));
        assertArrayEquals(new int[]{0xF0100000,100,0x40804000,4095,0x40804100,4095,0x40804200,4095,
                0x40800000,4095,0x40800100,4095,0x40800200,4095,0x42804000,0},BenchmarkMode.resources(2));
    }
}
