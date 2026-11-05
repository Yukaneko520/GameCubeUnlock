package com.yuka.gamecube.tools.ui

import com.yuka.gamecube.tools.core.AppRule
import com.yuka.gamecube.tools.core.ConfigSnapshot
import com.yuka.gamecube.tools.core.GraphicsTuning
import com.yuka.gamecube.tools.diagnostics.logBufferSnapshot
import com.yuka.gamecube.tools.diagnostics.threadtimeEntry
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class EditorInputTest {
    @Test fun numericFieldsRejectLettersPasteExponentWhitespaceAndWrongSign() {
        for (bad in listOf("a", "12abc", "1e2", "0x20", " 12", "12\n", "NaN", "Infinity", "2..5", "1-2")) {
            assertFalse(bad, numericEditAllowed(bad, decimal = true, negative = true))
        }
        assertFalse(numericEditAllowed("-40", decimal = true))
        assertFalse(numericEditAllowed("2.5"))
        assertFalse(numericEditAllowed("1234567890123"))
        assertTrue(numericEditAllowed("1260"))
        for (valid in listOf("", "-", "-.", "-40.5", ".5", "0.0"))
            assertTrue(valid, numericEditAllowed(valid, decimal = true, negative = true))
    }
    @Test fun structuredRowsPreserveOtherCellsAndMatchExistingWireFormat() {
        val original = "60/45/90\n120/60/120"
        val changed = replaceFrameCell(original, 1, 2, "144")
        assertEquals("60/45/90\n120/60/144", changed)
        assertEquals(144, GraphicsTuning.parseTuples(changed)[1].outputFps())
        val incomplete = replaceFrameCell(changed, 0, 1, "")
        assertThrows(IllegalArgumentException::class.java) { GraphicsTuning.parseTuples(incomplete) }
        assertThrows(IllegalArgumentException::class.java) { replaceFrameCell(original, 0, 0, "bad") }
        assertEquals(original, replaceFrameCell(changed, 1, 2, "120"))
    }
    @Test fun unchangedOrRevertedRulesCompareEqualIncludingDonorsAndLatentTuning() {
        val saved = AppRule.defaults().withEnabled(true).withFeature("sr", true)
        assertEquals(saved, AppRule.defaults().withEnabled(true).withFeature("sr", true))
        assertEquals(saved.hashCode(), saved.withEnabled(true).hashCode())
        assertNotEquals(saved, saved.withFeature("force144", true))
        assertNotEquals(saved, saved.withDonor("audio", "com.example.game"))
        assertEquals(saved, saved.withFeature("force144", true).withFeature("force144", false))
        assertNotEquals(saved, saved.withTuning(GraphicsTuning(true,
            GraphicsTuning.defaults().boost(), GraphicsTuning.defaults().power(), false, 0, 0, -1, 2.5, -40.0, 20.0)))
        val roundTrip = ConfigSnapshot.parse(ConfigSnapshot.defaults().withApp("com.test.game", saved).toJson())
        assertEquals(saved, roundTrip.rule("com.test.game"))
    }
    @Test fun logFormatPreservesThreadColumnsMultilineStacksAndRealBufferBoundaries() {
        val line = threadtimeEntry(0, 123, 456, 'E', "GC/test", "failure\nat call", ZoneId.of("UTC"))
        assertTrue(line.startsWith("01-01 00:00:00.000   123   456 E GC/test"))
        assertEquals(2, line.lineSequence().count { it.startsWith("01-01 ") })
        val android = "--------- beginning of main\nmain entry\n--------- beginning of crash\ncrash entry\n"
        val snapshot = logBufferSnapshot(line, android)
        assertTrue(snapshot.startsWith("--------- beginning of manager (module ring buffer)\n"))
        assertTrue(snapshot.endsWith(android))
        assertTrue(logBufferSnapshot("", "").contains("Android buffers not captured"))
    }
}
