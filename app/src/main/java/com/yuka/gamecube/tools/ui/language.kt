package com.yuka.gamecube.tools.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

internal enum class UiLanguage(val tag: String, val label: String) {
    ENGLISH("en", "English"), VIETNAMESE("vi", "Tiếng Việt");
    fun text(vi: String, en: String) = if (this == VIETNAMESE) vi else en
    companion object {
        fun fromStored(tag: String?): UiLanguage = entries.firstOrNull { it.tag == tag }
            ?: if (Locale.getDefault().language == "vi") VIETNAMESE else ENGLISH
    }
}
internal val LocalLanguage = staticCompositionLocalOf { UiLanguage.VIETNAMESE }
@Composable internal fun tr(vi: String, en: String): String = LocalLanguage.current.text(vi, en)

@Composable internal fun connectionLabel(status: String): String = when (deliveryState(status)) {
    DeliveryState.SENT -> tr("Đã gửi cấu hình", "Configuration sent")
    DeliveryState.SYNCING -> tr("Đang đồng bộ…", "Syncing…")
    DeliveryState.ERROR -> tr("Lỗi cấu hình · Xem nhật ký", "Configuration error · View logs")
    DeliveryState.WAITING -> tr("Đã lưu · Chờ LSPosed", "Saved · Waiting for LSPosed")
}

@Composable internal fun compatibilityLabel(value: String): String = when {
    value.startsWith("Compatible:") -> tr("Tương thích:", "Compatible:") + value.substringAfter(':')
    value.startsWith("Unsupported:") -> tr("Không hỗ trợ:", "Unsupported:") + value.substringAfter(':')
    value.startsWith("Verifying") -> tr("Đang xác minh thiết bị/GameCube…", "Verifying device/GameCube...")
    else -> value
}
