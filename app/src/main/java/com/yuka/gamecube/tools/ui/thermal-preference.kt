package com.yuka.gamecube.tools.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun ThermalPreference(enabled: Boolean, editable: Boolean, change: (Boolean) -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    val title = tr("Bỏ giới hạn nhiệt GameCube/GameWatch", "Bypass GameCube/GameWatch thermal limits")
    ToggleRow(title, thermalBypassSummary(), enabled, editable) {
        if (it) confirm = true else change(false)
    }
    LaunchedEffect(editable) { if (!editable) confirm = false }
    WindowDialog(show = confirm && editable, title = tr("Cảnh báo nhiệt độ", "Thermal warning"),
        summary = tr("Các tính năng có thể tiếp tục chạy khi máy nóng, gây hao pin, crash hoặc giảm tuổi thọ phần cứng. Tùy chọn này không thay đổi giới hạn xung CPU/GPU.",
            "Features may keep running while the device is hot, increasing battery drain, crashes or hardware wear. This option does not change CPU/GPU clock limits."),
        onDismissRequest = { confirm = false }) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(tr("Hủy", "Cancel"), { confirm = false }, modifier = Modifier.fillMaxWidth())
            TextButton(tr("Bật bất chấp cảnh báo", "Enable despite warning"), {
                if (editable) change(true)
                confirm = false
            }, colors = ButtonDefaults.textButtonColorsPrimary(), modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun thermalBypassSummary() = tr("Bỏ việc giảm hoặc tắt các tính năng trò chơi do nhiệt. Không can thiệp giới hạn xung CPU/GPU.",
    "Bypass thermal reductions and shutdowns of game features. Does not change CPU/GPU clock limits.")
