package com.yuka.gamecube.tools.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun BenchmarkPreference(mode: Int, editable: Boolean, change: (Int) -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    ToggleRow(tr("Chế độ benchmark CPU/GPU", "CPU/GPU benchmark mode"),
        tr("Nới giới hạn nhiệt CPU/GPU khi chơi.",
            "Relax CPU/GPU thermal limits while playing."),
        mode != 0, editable) { if (it) confirm = true else change(0) }
    if (mode != 0) {
        TuningChoice(tr("Xung nhịp CPU/GPU", "CPU/GPU frequency"),
            listOf(tr("Tự động", "Automatic"), tr("Yêu cầu tối đa", "Request maximum")),
            mode - 1, editable) { change(it + 1) }
        Caption(tr("Tự động điều chỉnh theo tải. Tối đa yêu cầu xung CPU/GPU cao nhất.",
            "Automatic adjusts clocks with load. Maximum requests the highest CPU/GPU clocks."),
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
    }
    LaunchedEffect(editable) { if (!editable) confirm = false }
    WindowDialog(show = confirm && editable, title = tr("Cảnh báo benchmark", "Benchmark warning"),
        summary = tr("Nới giới hạn nhiệt CPU/GPU có thể gây quá nhiệt, hao pin, crash hoặc hỏng phần cứng. Chế độ xung tối đa làm tăng rủi ro. Bạn chịu trách nhiệm khi sử dụng.",
            "Relaxing CPU/GPU thermal limits may cause overheating, battery drain, crashes or hardware damage. Maximum frequency increases the risk. Use at your own responsibility."),
        onDismissRequest = { confirm = false }) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(tr("Hủy", "Cancel"), { confirm = false }, modifier = Modifier.fillMaxWidth())
            TextButton(tr("Bật bất chấp cảnh báo", "Enable despite warning"), {
                if (editable) change(1)
                confirm = false
            }, colors = ButtonDefaults.textButtonColorsPrimary(), modifier = Modifier.fillMaxWidth())
        }
    }
}
