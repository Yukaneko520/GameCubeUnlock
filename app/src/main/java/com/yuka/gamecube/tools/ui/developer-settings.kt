package com.yuka.gamecube.tools.ui

import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

internal class DeveloperSettings(private val preferences: SharedPreferences) {
    var unlocked by mutableStateOf(preferences.getBoolean("developer_unlocked", false))
        private set
    var showThermalControls by mutableStateOf(unlocked && preferences.getBoolean("show_thermal_controls", false))
        private set

    var showBenchmarkControls by mutableStateOf(unlocked && preferences.getBoolean("show_benchmark_controls", false))
        private set
    var showNativeSrControls by mutableStateOf(unlocked && preferences.getBoolean("show_native_sr_controls", false))
        private set

    fun showNativeSrControls(show: Boolean) {
        if (!unlocked) return
        preferences.edit().putBoolean("show_native_sr_controls", show).apply()
        showNativeSrControls = show
    }

    fun showBenchmarkControls(show: Boolean) {
        if (!unlocked) return
        preferences.edit().putBoolean("show_benchmark_controls", show).apply()
        showBenchmarkControls = show
    }

    fun unlock() {
        preferences.edit().putBoolean("developer_unlocked", true).apply()
        unlocked = true
    }

    fun lock() {
        preferences.edit().putBoolean("developer_unlocked", false)
            .putBoolean("show_thermal_controls", false).putBoolean("show_benchmark_controls", false)
            .putBoolean("show_native_sr_controls", false).apply()
        unlocked = false
        showThermalControls = false
        showBenchmarkControls = false
        showNativeSrControls = false
    }

    fun showThermalControls(show: Boolean) {
        if (!unlocked) return
        preferences.edit().putBoolean("show_thermal_controls", show).apply()
        showThermalControls = show
    }
}

@Composable
internal fun DeveloperScreen(settings: DeveloperSettings, demonstrationMode: Boolean = false,
    demonstrationEditable: Boolean = true, disableFlaGlobal: Boolean = false,
    flaEditable: Boolean = false, changeFla: (Boolean) -> Unit = {},
    changeDemonstration: (Boolean) -> Unit = {}) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionCard(inset = false) {
            ToggleRow(tr("Bật chế độ nâng cao", "Enable advanced mode"),
                tr("Tắt để khóa lại các tùy chọn nâng cao.", "Turn off to lock advanced options again."), settings.unlocked) {
                if (it) settings.unlock() else settings.lock()
            }
        }
        SectionTitle(tr("Tính năng thử nghiệm", "Experimental features"))
        if (settings.unlocked) SectionCard(inset = false) {
            ToggleRow(tr("Chế độ trình diễn", "Demonstration mode"),
                tr("Hiện mọi tính năng GameCube cho ứng dụng đã bật. Chỉ mở giao diện, không bổ sung phần cứng.",
                    "Show all GameCube features for enabled apps. Reveals UI; does not add hardware."),
                demonstrationMode, demonstrationEditable, changeDemonstration)
            SectionDivider()
            ToggleRow(tr("Hiện bỏ giới hạn GameCube/GameWatch", "Show GameCube/GameWatch thermal bypass"),
                thermalBypassSummary(),
                settings.showThermalControls, onChange = settings::showThermalControls)
            SectionDivider()
            ToggleRow(tr("Hiện chế độ benchmark CPU/GPU", "Show CPU/GPU benchmark mode"),
                tr("Nới giới hạn nhiệt CPU/GPU theo game; chọn xung tự động hoặc tối đa.",
                    "Relax CPU/GPU thermal limits per game; choose automatic or maximum CPU/GPU frequency."),
                settings.showBenchmarkControls, onChange = settings::showBenchmarkControls)
            SectionDivider()
            FlaPreference(disableFlaGlobal, flaEditable, changeFla)
            if (!com.yuka.gamecube.tools.compat.RomFeatures.available(android.os.Build.VERSION.SDK_INT, "fla"))
                Caption(tr("Chỉ hỗ trợ bản OriginOS 6 đã kiểm tra.", "Only supported on the reviewed OriginOS 6 build."),
                    Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            SectionDivider()
            ToggleRow(tr("Hiện thông số SR riêng", "Show custom SR parameters"), nativeSrSummary(),
                settings.showNativeSrControls, onChange = settings::showNativeSrControls)
        }
    }
}

@Composable
internal fun DeveloperUnlockAuthor(settings: DeveloperSettings) {
    var taps by remember { mutableIntStateOf(0) }
    var toast by remember { mutableStateOf<Toast?>(null) }
    LaunchedEffect(settings.unlocked) { taps = 0 }
    val context = LocalContext.current
    val language = LocalLanguage.current
    DisposableEffect(Unit) { onDispose { toast?.cancel() } }
    SectionCard(inset = false) {
        PreferenceRow("Telegram", "@Koskori", onClick = {
            val message = if (settings.unlocked) language.text("Chế độ nâng cao đã mở.", "Advanced mode is already unlocked.")
            else {
                taps++
                val remaining = 7 - taps
                when {
                    remaining == 0 -> {
                        settings.unlock()
                        language.text("Đã mở chế độ nâng cao.", "Advanced mode unlocked.")
                    }
                    remaining < 5 -> language.text("Còn $remaining lần chạm để mở chế độ nâng cao.",
                        "$remaining more taps to unlock advanced mode.")
                    else -> null
                }
            }
            message?.let {
                toast?.cancel()
                toast = Toast.makeText(context, it, Toast.LENGTH_SHORT).also { next -> next.show() }
            }
        }, endActions = {})
    }
}
