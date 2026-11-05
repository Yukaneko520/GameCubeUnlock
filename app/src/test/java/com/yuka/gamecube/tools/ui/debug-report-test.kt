package com.yuka.gamecube.tools.ui

import com.yuka.gamecube.tools.diagnostics.HookLogReader
import com.yuka.gamecube.tools.diagnostics.hookEvidence
import org.junit.Assert.*
import org.junit.Test

class DebugReportTest {
    @Test fun moduleHistoryHasItsOwnLimitBeforeVendorNoise() {
        assertTrue(HookLogReader.moduleCommand.contains("GameCubeUnlock:V"))
        assertFalse(HookLogReader.moduleCommand.contains("IRIS_LOG"))
        assertTrue(HookLogReader.moduleCommand.endsWith("tail -n 2000"))
        assertTrue(HookLogReader.vendorCommand.contains("VSRController:D"))
        assertTrue(HookLogReader.thermalCommand.contains("ThermalEngine:D VivoPerfManager:D VPerformance:D"))
        assertTrue(HookLogReader.thermalCommand.endsWith("tail -n 1000"))
        assertFalse(HookLogReader.thermalCommand.contains("logcat -c"))
        assertFalse(HookLogReader.moduleCommand.contains("logcat -c"))
    }
    @Test fun summaryKeepsLatestFamilyStatusAndDoesNotTreatMissingProcessAsSuccess() {
        val log = "source=com.vivo.gamecube:assistantui stage=port_status families={audio:failed}\n" +
            "source=com.vivo.gamecube:assistantui stage=port_status families={audio:ready}\nsource=system_server policy=ready"
        val text = hookEvidence(log)
        assertTrue(text.contains("com.vivo.gamecube:assistantui: present"))
        assertTrue(text.contains("com.vivo.gamecube:daemon: not present in this capture"))
        assertTrue(text.contains("audio:ready")); assertFalse(text.contains("audio:failed"))
        assertTrue(hookEvidence("").contains("does not prove that hooks loaded"))
    }
}
