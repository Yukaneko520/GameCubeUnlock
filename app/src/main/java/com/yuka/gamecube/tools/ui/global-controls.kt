package com.yuka.gamecube.tools.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.yuka.gamecube.tools.core.ConfigSnapshot
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun GlobalControls(config: ConfigSnapshot, supported: Boolean, save: (ConfigSnapshot) -> Unit) {
    SectionTitle(tr("Điều khiển chung", "General"))
    SectionCard(inset = false) {
        ToggleRow(tr("Bật mô-đun", "Enable module"), tr("Áp dụng các quy tắc đã lưu.", "Apply saved rules."),
            config.enabled, supported) { save(config.withGlobal(it, config.fpsMode, config.hideGameCenter)) }
        SectionDivider()
        ToggleRow(tr("Ẩn Game Center", "Hide Game Center"),
            tr("Ẩn trong bảng công cụ GameCube.", "Hide it in the GameCube sidebar."),
            config.hideGameCenter, supported && config.enabled) {
            save(config.withGlobal(config.enabled, config.fpsMode, it))
        }
        SectionDivider()
        val fpsAvailable = com.yuka.gamecube.tools.compat.RomFeatures.available(android.os.Build.VERSION.SDK_INT, "fps")
        FpsPreference(if (fpsAvailable) config.fpsMode else 0, supported && config.enabled && fpsAvailable) {
            save(config.withGlobal(config.enabled, it, config.hideGameCenter))
        }
        if (!fpsAvailable) Caption(tr("Không có trên OriginOS 4.", "Unavailable on OriginOS 4."), Modifier.padding(20.dp))
        SectionDivider()
        ToggleRow(tr("144 Hz toàn hệ thống", "System-wide 144 Hz"),
            tr("Giữ 144 Hz khi ngừng chạm, chuyển ứng dụng và dùng cửa sổ nhỏ; Vivo vẫn quản lý nhiệt và nguồn.",
                "Keep 144 Hz while idle, switching apps and using floating windows; Vivo still manages heat and power."),
            config.force144Global, supported && config.enabled) { save(config.with144Global(it)) }
    }
}

@Composable
private fun FpsPreference(selected: Int, enabled: Boolean, select: (Int) -> Unit) {
    var show by remember { mutableStateOf(false) }
    val title = tr("Bộ đếm FPS", "FPS counter")
    val options = listOf(tr("Theo hệ thống", "System default"), tr("Luôn hiển thị", "Always show"), tr("Luôn ẩn", "Always hide"))
    PreferenceRow(title, options.getOrElse(selected) { options[0] }, enabled = enabled, holdDownState = show, onClick = { show = true })
    WindowDialog(show = show, title = title,
        summary = tr("Chỉ thay đổi hiển thị, không đổi giới hạn FPS của game.",
            "Changes the counter display, not the game's FPS limit."), onDismissRequest = { show = false }) {
        Column(Modifier.selectableGroup()) {
            options.forEachIndexed { index, label ->
                Row(Modifier.fillMaxWidth().selectable(selected == index, enabled = enabled,
                    role = Role.RadioButton, onClick = { select(index); show = false }).padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(label, Modifier.weight(1f))
                    RadioButton(selected == index, onClick = null, enabled = enabled)
                }
            }
            TextButton(tr("Đóng", "Close"), { show = false }, modifier = Modifier.fillMaxWidth())
        }
    }
}
