package com.yuka.gamecube.tools.ui

import com.yuka.gamecube.tools.core.AppRule
import com.yuka.gamecube.tools.core.ConfigSnapshot
import com.yuka.gamecube.tools.core.FrameworkState
import com.yuka.gamecube.tools.core.GraphicsTuning
import org.junit.Assert.*
import org.junit.Test

class ModuleHealthTest {
    private val config = ConfigSnapshot.defaults().withGlobal(true, 1, false)
    private val scopes = setOf("com.vivo.gamecube", "com.vivo.gamewatch", "system")
    private val processes = setOf("com.vivo.gamecube:assistantui", "system_server")
    private val state = FrameworkState(true, scopes, processes, "", "")
    private fun check(c: ConfigSnapshot = config, f: FrameworkState = state, status: String = "Configuration sent #1", supported: Boolean = true) =
        moduleHealth(c, supported, status, f)

    @Test fun disconnectedNeverReportsReadyEvenWhenLocalConfigIsEnabled() {
        assertEquals("disconnected", check(f = FrameworkState.disconnected()).issue)
    }
    @Test fun removingAnyBaseScopeCannotRemainGreenEvenWithLoadedProcesses() {
        assertEquals("", check().issue)
        for (removed in scopes) {
            val health = check(f = FrameworkState(true, scopes - removed, processes, "", ""))
            assertEquals("scope_missing", health.issue)
            assertEquals(listOf(removed), health.targets)
        }
        assertEquals("", check().issue)
    }
    @Test fun customSrGameScopeIsRequiredOnlyForAnEnabledCustomSrRule() {
        val rule = AppRule.defaults().withEnabled(true).withFeature("sr", true)
        val base = rule.tuning
        val tuning = GraphicsTuning(false, base.boost(), base.power(), true, 1, 0, -1, 2.5, -40.0, 20.0)
        val next = config.withApp("com.example.game", rule.withTuning(tuning))
        assertEquals(listOf("com.example.game"), check(c = next).targets)
        assertEquals("", check(c = config.withApp("com.example.game", rule)).issue)
        assertEquals("", check(c = next.withApp("com.example.game", rule.withTuning(tuning).withEnabled(false))).issue)
        assertEquals("", check(c = next, f = FrameworkState(true, scopes + "com.example.game", emptySet(), "", "")).issue)
    }
    @Test fun scopeReadFailureDoesNotPretendScopesAreMissing() {
        assertEquals("scope_unknown", check(f = FrameworkState(true, emptySet(), processes, "unavailable", "")).issue)
    }
    @Test fun optionalTargetQueryFailureDoesNotInvalidateScopeAndSyncChecks() {
        assertEquals("", check(f = FrameworkState(true, scopes, emptySet(), "", "unavailable")).issue)
    }
    @Test fun unopenedGameCubeDoesNotRequireLaunchingAGameToClearHome() {
        assertEquals("", check(f = FrameworkState(true, scopes, emptySet(), "", "")).issue)
        assertEquals("", check(f = FrameworkState(true, scopes, setOf("system_server"), "", "")).issue)
    }
    @Test fun systemServerAbsentFromDiagnosticTargetsIsNotAMissingScope() {
        assertEquals("", check(f = FrameworkState(true, scopes, setOf("com.vivo.gamecube:assistantui"), "", "")).issue)
    }
    @Test fun scopedGameWatchDoesNotNeedToBeRunningForHomeReadiness() {
        val next = config.withApp("com.example.game", AppRule.defaults().withEnabled(true).withFeature("thermalBypass", true))
        assertEquals("", check(c = next).issue)
    }
    @Test fun optionalTargetFailuresCannotHideRequiredScopeOrSyncFailures() {
        val partial = FrameworkState(true, scopes - "system", emptySet(), "", "unavailable")
        assertEquals("scope_missing", check(f = partial).issue)
        assertEquals("sync_failed", check(f = FrameworkState(true, scopes, emptySet(), "", "unavailable"),
            status = "Sync failed; configuration saved locally").issue)
    }
    @Test fun syncFailureAndCompatibilityFailureCannotBeGreen() {
        assertEquals("sync_failed", check(status = "Sync failed; configuration saved locally").issue)
        assertEquals("sync_pending", check(status = "Saving and syncing...").issue)
        assertEquals("unsupported", check(supported = false).issue)
    }
    @Test fun completeFrameworkObservationsAndPublishedConfigAreReady() {
        assertEquals("", check().issue)
        assertTrue(healthReason(check(f = FrameworkState.disconnected()), UiLanguage.ENGLISH).contains("LSPosed"))
    }
    @Test fun uncertainOrPendingStatesAreWarningsAndConfirmedFailuresAreErrors() {
        listOf("scope_unknown", "sync_pending").forEach {
            assertTrue(it, ModuleHealth(it).warning)
        }
        listOf("", "disconnected", "scope_missing", "sync_failed", "unsupported").forEach {
            assertFalse(it, ModuleHealth(it).warning)
        }
    }
}
