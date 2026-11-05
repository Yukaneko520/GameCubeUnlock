package com.yuka.gamecube.tools.diagnostics

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RootLogProcessTest {
    private fun child(mode: String): Process {
        val java = File(System.getProperty("java.home"), "bin/java").path
        val classes = File(requireNotNull(RootLogChild::class.java.protectionDomain).codeSource.location.toURI()).path
        return ProcessBuilder(java, "-cp", classes, RootLogChild::class.java.name, mode).redirectErrorStream(true).start()
    }
    @Test fun closesStdinAndPreservesACompleteCapture() {
        assertEquals("hook record after stdin EOF", ReadOnlyRoot.readProcess(child("eof")).trim())
    }
    @Test fun oversizedOutputIsDrainedInsteadOfDeadlockingTheChild() {
        val text = ReadOnlyRoot.readProcess(child("large"), limit=1024)
        assertTrue(text.startsWith("x".repeat(1024)))
        assertTrue(text.contains("output truncated at 1024 bytes"))
        assertFalse(text.contains("timed out"))
    }
    @Test fun failedCommandsKeepTheirStderrAndExitCode() {
        val text = ReadOnlyRoot.readProcess(child("failure"))
        assertTrue(text.contains("captured stderr detail")); assertTrue(text.contains("command exit=7"))
    }
    @Test fun timedOutCommandsKeepAlreadyReadHookRecords() {
        val text = ReadOnlyRoot.readProcess(child("timeout"), timeoutMillis=1500)
        assertTrue(text.contains("record before timeout")); assertTrue(text.contains("timed out after 1500ms"))
        assertTrue(text.contains(HookLogReader.READER_WARNING))
    }
}
