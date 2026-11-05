package com.yuka.gamecube.tools.compat;

import java.util.HashMap;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class TargetProfilesTest {
    @Test public void os4UsesTheSuppliedRomAndDoesNotRequireAnAbsentGameLib() {
        var b=TargetProfiles.OS4;var a=RomAdapter.forBaseline(b);
        assertEquals(34,b.sdk());assertEquals(1200713,b.gameCubeCode());assertEquals(30079,b.gameWatchCode());
        assertEquals(4,b.frameworks().size());assertEquals(1,b.packages().size());assertFalse(b.nativeSr());
        assertFalse(b.packages().containsKey("com.vivo.gamelib"));assertNull(a.fps());
        assertEquals("h6.a",a.commonType());assertEquals("f7.o",a.owner().type());
        assertEquals("g0",a.owner().instance());assertEquals("M0",a.owner().currentPackage());
        assertEquals("L0",a.owner().pid());assertEquals("d7.b",a.support().evaluator());
        assertEquals("m",a.support().coupling());assertNull(a.support().extendedGame());
        assertEquals("j",a.frameProfile().cache());assertEquals("q6.a",a.frameProfile().entity());
        assertEquals("t7.h",a.frameProfile().strategy());assertEquals("t7.j",a.frameProfile().values());
        assertEquals("e9.k",a.monster().controller());assertEquals("M",a.monster().enter());
        assertEquals("L",a.monster().exit());assertEquals("u",a.monster().barBind());
        assertEquals("p",a.monster().radio());
    }
    @Test public void gameWatchRequiresExactVersionCodeNameAndDigestForEachRom() {
        assertEquals(250003,TargetProfiles.OS5.gameWatchCode());
        assertEquals("25.0.0.03",TargetProfiles.OS5.gameWatchVersion());
        assertEquals(250006,TargetProfiles.OS6.gameWatchCode());
        assertEquals("25.0.0.06",TargetProfiles.OS6.gameWatchVersion());
        for(var baseline:TargetProfiles.ALL) {
            String sha=baseline.packages().get("com.vivo.gamewatch");
            assertTrue(baseline.gameWatchMatches(baseline.gameWatchCode(),baseline.gameWatchVersion(),sha));
            assertFalse(baseline.gameWatchMatches(baseline.gameWatchCode()+1,baseline.gameWatchVersion(),sha));
            assertFalse(baseline.gameWatchMatches(baseline.gameWatchCode(),"changed",sha));
            assertFalse(baseline.gameWatchMatches(baseline.gameWatchCode(),null,sha));
            assertFalse(baseline.gameWatchMatches(baseline.gameWatchCode(),baseline.gameWatchVersion(),"changed"));
            var other=baseline==TargetProfiles.OS5?TargetProfiles.OS6:TargetProfiles.OS5;
            assertFalse(baseline.gameWatchMatches(other.gameWatchCode(),other.gameWatchVersion(),
                    other.packages().get("com.vivo.gamewatch")));
        }
    }
    @Test public void monsterBindingsStayGenerationSpecific() {
        var old=RomAdapter.forBaseline(TargetProfiles.OS5).monster();
        var modern=RomAdapter.forBaseline(TargetProfiles.OS6).monster();
        assertEquals("i9.a",old.info());assertEquals("i9.j",old.controller());assertEquals("i9.l",old.helper());
        assertEquals("P",old.enter());assertEquals("O",old.exit());assertEquals("Q",old.disconnect());
        assertEquals("y",old.barBind());assertEquals("w",old.flag());assertEquals("x",old.shift());
        assertEquals("S",old.init());assertEquals("s",old.radio());assertEquals("M0",old.capability());
        assertTrue(modern.controller().endsWith(".modeshift.k"));
        assertEquals("h0",modern.enter());assertEquals("g0",modern.exit());assertEquals("i0",modern.disconnect());
        assertEquals("H",modern.barBind());assertEquals("v",modern.flag());assertEquals("w",modern.shift());
        assertEquals("k0",modern.init());assertEquals("B",modern.radio());assertEquals("n1",modern.capability());
    }
    @Test public void compatibilityMessagesAndDiagnosticReasonsUseEnglishForBothRoms() {
        for(var baseline:TargetProfiles.ALL) {
            var result=new com.yuka.gamecube.tools.core.Compatibility.Result(baseline,"");
            assertTrue(result.allowed()); assertTrue(result.message().startsWith("Compatible:"));
            assertEquals("compatible",com.yuka.gamecube.tools.core.Compatibility.diagnosticReason(result.message()));
        }
        var rejected=new com.yuka.gamecube.tools.core.Compatibility.Result(null,"device_baseline_unknown");
        assertFalse(rejected.allowed()); assertEquals("Unsupported: device_baseline_unknown",rejected.message());
        assertEquals("device_baseline_unknown",com.yuka.gamecube.tools.core.Compatibility.diagnosticReason(rejected.message()));
        assertEquals("identity_unverified",com.yuka.gamecube.tools.core.Compatibility.diagnosticReason("Verifying device and APK..."));
    }
    @Test public void os5Pd2352UsesV3ProfileCacheRatherThanLegacyMembershipRows() {
        // OEM a7.j.k means model >= threshold: PD2352 is newer than both PD2241 and PD2301.
        var layout=RomAdapter.forBaseline(TargetProfiles.OS5).frameProfile();
        assertEquals("i",layout.cache());assertEquals("h6.a",layout.entity());assertEquals("f6.b",layout.triplet());
        assertEquals("w7.c",layout.factory());assertEquals("w7.h",layout.strategy());assertEquals("w7.j",layout.values());
        var modern=RomAdapter.forBaseline(TargetProfiles.OS6).frameProfile();
        assertEquals("h",modern.cache());assertEquals("c9.h",modern.strategy());assertEquals("c9.j",modern.values());
        assertNotEquals(layout.entity(),modern.entity());
    }
    @Test public void exactOs5AndOs6DevicesDoNotMix() {
        assertSame(TargetProfiles.OS5,TargetProfiles.device("V2352A","PD2352",35,""));
        assertSame(TargetProfiles.OS6,TargetProfiles.device("V2352GA","PD2352",36,""));
        assertSame(TargetProfiles.OS5,TargetProfiles.device("V2352GA","PD2352",35,""));
        assertNull(TargetProfiles.device("V2352A","PD2352",35,"q1"));
        assertSame(TargetProfiles.OS4,TargetProfiles.device("V2352A","PD2352",34,""));
        assertSame(TargetProfiles.OS4,TargetProfiles.device("V2352GA","PD2352",34,""));
        assertNull(TargetProfiles.device("V2352A","PD2352",33,""));
        for(var baseline:TargetProfiles.ALL) {
            assertTrue(baseline.apkMatches(baseline.gameCubeCode(),baseline.gameCubeVersion(),baseline.gameCubeSha()));
            assertFalse(baseline.apkMatches(baseline.gameCubeCode()+1,baseline.gameCubeVersion(),baseline.gameCubeSha()));
            assertFalse(baseline.apkMatches(baseline.gameCubeCode(),baseline.gameCubeVersion(),"changed"));
        }
        assertFalse(TargetProfiles.OS5.apkMatches(TargetProfiles.OS6.gameCubeCode(),TargetProfiles.OS6.gameCubeVersion(),TargetProfiles.OS6.gameCubeSha()));
        assertFalse(TargetProfiles.OS6.apkMatches(TargetProfiles.OS5.gameCubeCode(),TargetProfiles.OS5.gameCubeVersion(),TargetProfiles.OS5.gameCubeSha()));
    }
    @Test public void everyFrameworkAndPackageIsRequiredAndGenerationSpecific() {
        for(var baseline:TargetProfiles.ALL) {
            var actual=new HashMap<>(baseline.frameworks()); actual.putAll(baseline.packages());
            assertTrue(baseline.dependenciesMatch(actual));
            for(var key:List.copyOf(actual.keySet())) {
                String stock=actual.remove(key); assertFalse(key,baseline.dependenciesMatch(actual));
                actual.put(key,"wrong"); assertFalse(key,baseline.dependenciesMatch(actual));
                actual.put(key,stock);
            }
            var other=baseline==TargetProfiles.OS5?TargetProfiles.OS6:TargetProfiles.OS5;
            actual.put("vivo-services.jar",other.frameworks().get("vivo-services.jar"));
            assertFalse(baseline.dependenciesMatch(actual));
        }
    }
    @Test public void semanticMappingsRetainOs6AndUseVerifiedOs5FpsFieldsAndLifecycle() {
        var a=RomAdapter.forBaseline(TargetProfiles.OS5);
        var b=RomAdapter.forBaseline(TargetProfiles.OS6);
        assertEquals("z6.b",a.support().evaluator()); assertEquals("i",a.support().capability());
        assertEquals("j0",a.fps().capability()); assertEquals("j2",a.fps().enterExit());
        assertEquals("J",a.fps().label()); assertEquals("M",a.fps().container());
        assertEquals("Y0",a.owner().currentPackage()); assertEquals("X0",a.owner().pid());
        assertEquals("c8.b",b.support().evaluator()); assertEquals("E0",b.fps().capability());
        assertEquals("h2",b.fps().enterExit()); assertEquals("I",b.fps().label());
        assertTrue(a.hideOnlyGameCenter());
        assertTrue(TargetProfiles.OS5.nativeSr()); assertTrue(TargetProfiles.OS6.nativeSr());
        assertThrows(IllegalArgumentException.class,()->TargetProfiles.descriptors(37));
    }
}
