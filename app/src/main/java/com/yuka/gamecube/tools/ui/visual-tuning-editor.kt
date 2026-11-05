package com.yuka.gamecube.tools.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yuka.gamecube.tools.core.VisualTuning

@Composable
internal fun VisualTuningEditor(value: VisualTuning, enabled: Boolean, change: (VisualTuning) -> Unit) {
    SectionTitle(tr("Tăng cường hình ảnh", "Image enhancement"))
    SectionCard(inset = false) {
        ToggleRow(tr("Tăng cường hình ảnh trò chơi tùy chỉnh", "Custom game image enhancement"),
            tr("Mở khóa các tính năng ẩn của chế độ này", "Unlock hidden features of this mode"),
            value.enabled(), enabled) { change(value.edit(active = it)) }
        if (value.enabled()) {
            SectionDivider()
            TuningChoice(tr("Cách xử lý", "Processing"), listOf(tr("Bộ lọc Iris", "Iris filters"),
                tr("Màu màn hình Hawkeye", "Hawkeye display color")), value.route(), enabled) { change(value.edit(route = it)) }
            if (value.route() == 1) {
                TuningChoice(tr("Chế độ màu", "Color mode"), listOf(tr("Tiêu chuẩn", "Standard"),
                    tr("Chuyên nghiệp", "Professional"), tr("Tăng cường", "Enhanced"), "ZEISS"),
                    value.colorMode() - 270, enabled) { change(value.edit(colorMode = it + 270)) }
                Caption(tr("Bật Tinh chỉnh chất lượng hình ảnh trong GameCube để dùng màu đã chọn. Bộ lọc chung sẽ bị vô hiệu hóa.",
                    "Enable Quality tuning in GameCube to use the selected color. General filter is disabled."), Modifier.padding(20.dp))
            }
        }
    }
}

private fun VisualTuning.edit(active: Boolean = enabled(), route: Int = route(), colorMode: Int = colorMode()) =
    VisualTuning(active, route, profile(), filterMode(), preset(), hue(), saturation(), contrast(), lightness(), colorMode)
