package com.yuka.gamecube.tools.diagnostics

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val threadtime = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS", Locale.US)

internal fun threadtimeEntry(time: Long, pid: Int, tid: Int, level: Char, tag: String, message: String,
    zone: ZoneId = ZoneId.systemDefault()): String {
    val stamp = threadtime.format(Instant.ofEpochMilli(time).atZone(zone))
    val prefix = String.format(Locale.US, "%s %5d %5d %c %-16s: ", stamp, pid, tid, level, tag)
    return message.lineSequence().joinToString("\n", postfix = "\n") { prefix + it }
}

internal fun logBufferSnapshot(manager: String, android: String): String =
    "--------- beginning of manager (module ring buffer)\n$manager\n" +
        android.ifBlank { "--------- Android buffers not captured\n" }
