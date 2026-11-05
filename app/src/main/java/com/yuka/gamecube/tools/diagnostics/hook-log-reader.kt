package com.yuka.gamecube.tools.diagnostics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal object HookLogReader {
    // Fixed, read-only command: no user-provided shell text, clear-log flag, settings or service mutation.
    internal const val moduleCommand = "set -o pipefail; timeout 6 logcat -b main -b system -b crash -d -v threadtime " +
        "GameCubeUnlock:V '*:S' | tail -n 2000"
    internal const val vendorCommand = "set -o pipefail; timeout 6 logcat -b main -b system -b crash -d -v threadtime " +
        "GameCube:D VSRController:D GameManager:I GameWatch:I VSRManager:I VSRBufferQueue:I " +
        "GameLib:I IRIS_LOG_SERV_I5:D AndroidRuntime:E DEBUG:F libc:F '*:S' | tail -n 1000"
    internal const val thermalCommand = "set -o pipefail; timeout 6 logcat -b main -b system -d -v threadtime " +
        "ThermalEngine:D VivoPerfManager:D VPerformance:D '*:S' | tail -n 1000"

    suspend fun read(): String {
        val saved = capture("saved hooks",savedCommand)
        val module = capture("module",moduleCommand)
        val vendor = capture("OEM",vendorCommand)
        val thermal = capture("thermal",thermalCommand)
        return "--------- beginning of saved hook journals (active then previous files, last 400 records each)\n$saved\n" +
            "--------- beginning of module hooks (filtered capture, last 2000 records)\n$module\n" +
            "--------- beginning of OEM backend (last 1000 records)\n$vendor\n" +
            "--------- beginning of thermal and VPerf backend (last 1000 records)\n$thermal"
    }
    private suspend fun capture(label: String, command: String): String = try { ReadOnlyRoot.read(command) }
    catch(error: Exception) {
        if(error is kotlinx.coroutines.CancellationException) throw error
        "$READER_WARNING $label: ${error.message}\n"
    }
    internal const val READER_WARNING = "--------- root reader warning:"
    internal const val savedCommand = "timeout 6 sh -c 'for f in " +
        "/data/user/0/com.vivo.gamecube/files/gamecube-tools-assistantui.log " +
        "/data/user/0/com.vivo.gamecube/files/gamecube-tools-daemon.log " +
        "/data/user/0/com.vivo.gamewatch/files/gamecube-tools-gamewatch.log " +
        "/data/system/gamecube-tools/gamecube-tools-system.log " +
        "/data/user/0/com.vivo.gamecube/files/gamecube-tools-assistantui.log.old " +
        "/data/user/0/com.vivo.gamecube/files/gamecube-tools-daemon.log.old " +
        "/data/user/0/com.vivo.gamewatch/files/gamecube-tools-gamewatch.log.old " +
        "/data/system/gamecube-tools/gamecube-tools-system.log.old; " +
        "do if [ -r \"\$f\" ]; then tail -n 400 \"\$f\"; fi; done'"
}

internal object ReadOnlyRoot {
    internal suspend fun read(command: String): String = withContext(Dispatchers.IO) {
        readProcess(ProcessBuilder("su", "-c", command).redirectErrorStream(true).start())
    }
    internal fun readProcess(process: Process, timeoutMillis: Long = 12000, limit: Int = 1_000_000): String {
        val executor = Executors.newSingleThreadExecutor()
        val bytes = ByteArrayOutputStream()
        var truncated = false
        try {
            process.outputStream.close()
            val output = executor.submit {
                process.inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        synchronized(bytes) {
                            val keep = minOf(count, (limit - bytes.size()).coerceAtLeast(0))
                            bytes.write(buffer, 0, keep)
                            if (keep < count) truncated = true
                        }
                    }
                }
            }
            val finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
            if (!finished) process.destroyForcibly()
            val drainFailure = runCatching { output.get(1, TimeUnit.SECONDS) }.exceptionOrNull()
            return synchronized(bytes) {
                val text = bytes.toString(Charsets.UTF_8.name())
                val warning = when {
                    !finished -> "timed out after ${timeoutMillis}ms; partial output retained"
                    process.exitValue() != 0 -> "command exit=${process.exitValue()}; partial output retained"
                    drainFailure != null -> "output read failed: ${drainFailure.message}"
                    truncated -> "output truncated at $limit bytes"
                    else -> null
                }
                text + if(warning == null) "" else "\n${HookLogReader.READER_WARNING} $warning\n"
            }
        } finally {
            process.destroyForcibly()
            executor.shutdownNow()
            runCatching { process.inputStream.close() }
        }
    }
}

