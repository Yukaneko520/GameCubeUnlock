package com.yuka.gamecube.tools.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun FlaPreference(disabled: Boolean, editable: Boolean, change: (Boolean) -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    ToggleRow(tr("Tắt FLA (thử nghiệm)", "Disable FLA (experimental)"),
        tr("Toàn hệ thống. Cần khởi động lại hoàn toàn để thử từ trạng thái sạch.",
            "System-wide. A full reboot is required for a clean test."), disabled, editable) {
        if (it) confirm = true else change(false)
    }
    LaunchedEffect(editable) { if (!editable) confirm = false }
    WindowDialog(show = confirm && editable, title = tr("Thử nghiệm tắt FLA", "Disable FLA experiment"),
        summary = tr("Tắt điều chỉnh CPU theo khung hình có thể tăng hao pin, nhiệt và ảnh hưởng độ mượt. Không xóa trần CPU đang mắc. Sau khi bật, hãy khởi động lại hoàn toàn rồi kiểm tra lại.",
            "Disabling frame-based CPU control may increase power use, heat and affect smoothness. It does not clear an existing CPU ceiling. Enable, then perform a full reboot and test again."),
        onDismissRequest = { confirm = false }) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(tr("Hủy", "Cancel"), { confirm = false }, modifier = Modifier.fillMaxWidth())
            TextButton(tr("Bật thử nghiệm", "Enable experiment"), {
                if (editable) change(true)
                confirm = false
            }, colors = ButtonDefaults.textButtonColorsPrimary(), modifier = Modifier.fillMaxWidth())
        }
    }
}
