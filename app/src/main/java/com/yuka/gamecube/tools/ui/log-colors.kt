package com.yuka.gamecube.tools.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

private val logPriority = Regex("^(?:\\d{4}-)?\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d+\\s+\\d+\\s+\\d+\\s+([VDIWEFA])\\s+")

internal fun coloredLogLines(text: String, dark: Boolean, neutral: Color) = buildList {
    var level: Char? = null
    text.splitToSequence('\n').forEach { line ->
        level = when {
            line.startsWith("---------") -> if (line.startsWith("--------- root reader warning:")) 'W' else null
            else -> logPriority.find(line)?.groupValues?.get(1)?.single() ?: level
        }
        val color = when (level) {
            'V' -> if (dark) Color(0xFFA0A0A0) else Color(0xFF555555)
            'D' -> if (dark) Color(0xFF82B1FF) else Color(0xFF145CCA)
            'I' -> if (dark) Color(0xFF8CD690) else Color(0xFF237A3B)
            'W' -> if (dark) Color(0xFFFFCC80) else Color(0xFF8B5700)
            'E' -> if (dark) Color(0xFFFF8A80) else Color(0xFFC62828)
            'F', 'A' -> if (dark) Color(0xFFFF80C8) else Color(0xFFA71670)
            else -> neutral
        }
        add(buildAnnotatedString { withStyle(SpanStyle(color = color)) { append(line) } })
    }
}
