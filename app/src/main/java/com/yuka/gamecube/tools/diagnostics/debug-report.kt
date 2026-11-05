package com.yuka.gamecube.tools.diagnostics

import android.content.Context
import android.os.Build
import com.yuka.gamecube.tools.core.Compatibility
import com.yuka.gamecube.tools.core.ConfigSnapshot
import com.yuka.gamecube.tools.core.FrameworkConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Export current state as well as historical messages; manager publish success is not proof of hook installation. */
internal suspend fun diagnosticHeader(context: Context, hooks: String): String = withContext(Dispatchers.IO) {
    buildString {
        appendLine("--------- beginning of current diagnostic state")
        appendLine("Device: ${Build.MODEL} / ${Build.DEVICE}; Android ${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}")
        val result = Compatibility.verify(context)
        appendLine("Identity: allowed=${result.allowed()} baseline=${result.baseline()?.id()} reason=${result.reason()}")
        val service = FrameworkConnection.service
        appendLine(runCatching { "LSPosed: ${service?.frameworkName} ${service?.frameworkVersion}; scope=${service?.scope}" }
            .getOrElse { "LSPosed scope read failed: ${it.message}" })
        appendLine("Current saved configuration: " + context.getSharedPreferences("desired_config", Context.MODE_PRIVATE)
            .getString(ConfigSnapshot.KEY, "missing"))
        appendLine(hookEvidence(hooks))
    }
}

internal fun hookEvidence(hooks: String): String = buildString {
    appendLine("Hook evidence in captured records:")
    for (process in listOf("com.vivo.gamecube:assistantui", "com.vivo.gamecube:daemon", "system_server")) {
        val present = hooks.contains("source=$process ") || process == "system_server" && hooks.contains("source=system ")
        appendLine("  $process: " + if (present) "present" else "not present in this capture")
    }
    val status = hooks.lineSequence().filter { it.contains("stage=port_status ") }
        .groupBy { Regex("source=([^ ]+)").find(it)?.groupValues?.get(1).orEmpty() }
    status.values.forEach { appendLine(it.last()) }
    if (status.isEmpty()) appendLine("No hook-family status captured. Startup logs may have expired; this does not prove that hooks loaded.")
    appendLine("Render quality/FPS cannot be inferred from configuration delivery or menu visibility.")
}
