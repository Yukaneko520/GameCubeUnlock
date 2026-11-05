package com.yuka.gamecube.tools.ui

import com.yuka.gamecube.tools.core.ConfigSnapshot
import com.yuka.gamecube.tools.core.FrameworkState

internal data class ModuleHealth(val issue: String, val targets: List<String> = emptyList()) {
    val warning: Boolean get() = issue in listOf("scope_unknown", "sync_pending")
}

internal fun moduleHealth(config: ConfigSnapshot, supported: Boolean, status: String, framework: FrameworkState): ModuleHealth {
    if (!framework.connected()) return ModuleHealth("disconnected")
    if (!supported) return ModuleHealth("unsupported")
    if (framework.scopeError().isNotEmpty()) return ModuleHealth("scope_unknown")
    val scopes = mutableListOf("com.vivo.gamecube", "com.vivo.gamewatch", "system")
    scopes += config.apps.filterValues { it.enabled && it.sr && it.tuning.srCustom() }.keys
    val missing = scopes.filter { it !in framework.scopes() }
    if (missing.isNotEmpty()) return ModuleHealth("scope_missing", missing)
    if (deliveryState(status) == DeliveryState.ERROR) return ModuleHealth("sync_failed")
    if (deliveryState(status) != DeliveryState.SENT) return ModuleHealth("sync_pending")
    // Running targets describe active processes, not configured scope completeness.
    return ModuleHealth("")
}

internal fun healthReason(health: ModuleHealth, language: UiLanguage): String = with(language) {
    when (health.issue) {
        "disconnected" -> text("Chưa kết nối LSPosed. Kiểm tra framework và bật mô-đun trong LSPosed.",
            "LSPosed is not connected. Check the framework and enable this module in LSPosed.")
        "unsupported" -> text("Thiết bị hoặc GameCube không tương thích.", "Device or GameCube is not compatible.")
        "scope_unknown" -> text("Không đọc được scope LSPosed. Thử kiểm tra lại.", "Unable to read LSPosed scopes. Check again.")
        "scope_missing" -> text("Thiếu scope: ", "Missing scope: ") + health.targets.joinToString(", ")
        "sync_failed" -> text("Không gửi được cấu hình tới LSPosed. Xem nhật ký.", "Configuration could not be sent to LSPosed. See logs.")
        "sync_pending" -> text("Cấu hình chưa đồng bộ với LSPosed.", "Configuration has not synced with LSPosed.")
        else -> ""
    }
}
