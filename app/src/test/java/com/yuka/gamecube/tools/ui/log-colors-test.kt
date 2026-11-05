package com.yuka.gamecube.tools.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class LogPriorityTest {
    @Test fun preservesBlankLinesAndCarriageReturnsForRawLogCopy() {
        val input = "\n--------- beginning of main\r\n10-24 12:00:00.000 1 2 E tag: failure\r\n\n    at call\r\n"
        assertEquals(input, coloredLogLines(input, true, Color.Gray).joinToString("\n") { it.text })
    }
    @Test fun preservesTextAndHandlesAllPrioritiesInBothThemes() {
        val input = "VDIWEF".mapIndexed { index, priority ->
            "10-23 23:00:00.000 ${10000 + index} 123 $priority GameCubeUnlock: row $index"
        }.joinToString("\n")
        listOf(false, true).forEach { dark ->
            val rendered = coloredLogLines(input, dark, Color.Gray)
            assertEquals(input, rendered.joinToString("\n") { it.text })
            assertEquals(6, rendered.flatMap { it.spanStyles }.map { it.item.color }.distinct().size)
        }
    }

    @Test fun continuationInheritsSeverityAndBufferMarkerResetsIt() {
        val input = "2026-10-23 23:00:00.000 123 456 E tag: failure\n    at call\n" +
            "--------- beginning of system\nplain header\n--------- root reader warning: timeout\n"
        val rendered = coloredLogLines(input, true, Color.Gray)
        val colors = rendered.flatMap { it.spanStyles }.map { it.item.color }
        assertEquals(input, rendered.joinToString("\n") { it.text })
        assertEquals(colors[0], colors[1])
        assertEquals(Color.Gray, colors[2])
        assertEquals(Color.Gray, colors[3])
        assertNotEquals(colors[0], colors[4])
        assertEquals(listOf(""), coloredLogLines("", false, Color.Gray).map { it.text })
    }
}
