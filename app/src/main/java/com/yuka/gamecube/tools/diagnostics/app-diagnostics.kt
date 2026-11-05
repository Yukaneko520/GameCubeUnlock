package com.yuka.gamecube.tools.diagnostics

import android.content.Context
import android.os.Build
import android.os.Process
import com.yuka.gamecube.tools.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.Executors

/** Manager-only bounded log. Hook processes retain their existing Android/LSPosed sinks. */
object AppDiagnostics {
    private val writer = Executors.newSingleThreadExecutor()
    private val state = MutableStateFlow("")
    val lines = state.asStateFlow()
    private var file: File? = null
    @JvmStatic fun start(context: Context) {
        writer.execute {
            // Keep the previous format's file intact; new sessions use threadtime consistently.
            file = File(context.filesDir, "manager-threadtime.log")
            state.value = runCatching { file!!.takeIf { it.exists() }?.readText().orEmpty().takeLast(128_000) }
                .getOrDefault("")
            append("session", "build=${BuildConfig.VERSION_NAME}/${BuildConfig.VERSION_CODE} model=${Build.MODEL} " +
                "device=${Build.DEVICE} android=${Build.VERSION.RELEASE}/${Build.VERSION.SDK_INT}")
        }
    }
    @JvmStatic fun log(source: String, message: String) { enqueue(source, message, 'I') }
    @JvmStatic fun error(source: String, error: Throwable) { enqueue(source, error.stackTraceToString(), 'E') }
    private fun enqueue(source: String, message: String, level: Char) {
        val time = System.currentTimeMillis()
        val pid = Process.myPid()
        val tid = Process.myTid()
        writer.execute { append(source, message, level, time, pid, tid) }
    }
    /** Serialized with writes; only clears this manager's private log file. */
    @JvmStatic fun clear() { writer.execute {
        val failure = runCatching { file?.writeText("") }.exceptionOrNull()
        state.value = ""
        if (failure != null) append("clear", "Unable to clear saved log: ${failure.message}")
    } }
    private fun append(source: String, message: String, level: Char = 'I', time: Long = System.currentTimeMillis(),
        pid: Int = Process.myPid(), tid: Int = Process.myTid()) {
        val line = threadtimeEntry(time, pid, tid, level, "GC/" + source, message)
        state.value = boundedLogTail(state.value + line)
        runCatching {
            val target = file ?: return
            if (target.length() + line.toByteArray(Charsets.UTF_8).size > 512_000) target.writeText(state.value)
            else target.appendText(line)
        }
    }
}

internal fun boundedLogTail(text: String, maxChars: Int = 128_000): String {
    require(maxChars > 0)
    if (text.length <= maxChars) return text
    val tail = text.takeLast(maxChars)
    val firstBreak = tail.indexOf('\n')
    return if (firstBreak >= 0 && firstBreak < tail.lastIndex) tail.substring(firstBreak + 1) else tail
}
