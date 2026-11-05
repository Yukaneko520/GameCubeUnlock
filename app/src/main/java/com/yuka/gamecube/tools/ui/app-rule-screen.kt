package com.yuka.gamecube.tools.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.yuka.gamecube.tools.core.AppRule
import com.yuka.gamecube.tools.core.ConfigSnapshot
import com.yuka.gamecube.tools.core.NativeProfileCatalog
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton

@Composable
internal fun AppRuleScreen(app: InstalledApp, initial: AppRule, supported: Boolean,
    scrollState: ScrollState = rememberScrollState(), nativeScope: Boolean = false, scopeError: String = "",
    requestScope: () -> Unit = {}, showThermalControls: Boolean = false, showBenchmarkControls: Boolean = false,
    showNativeSrControls: Boolean = false, force144Global: Boolean = false, demonstrationMode: Boolean = false,
    onSave: (AppRule) -> Unit) {
    var draft by remember(app.packageName) { mutableStateOf(initial) }
    var audioDonor by rememberSaveable(app.packageName) { mutableStateOf(initial.audioDonor) }
    var frameDonor by rememberSaveable(app.packageName) { mutableStateOf(initial.frameDonor) }
    var srDonor by rememberSaveable(app.packageName) { mutableStateOf(initial.srDonor) }
    var tuningValid by remember { mutableStateOf(true) }
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { InstalledApps(context) }
    val icon by rememberAppIcon(repository, app)
    val language = LocalLanguage.current
    val catalog = remember(context) {
        runCatching {
            context.assets.open(NativeProfileCatalog.assetForSdk(android.os.Build.VERSION.SDK_INT)).bufferedReader().use {
                NativeProfileCatalog.parse(it.readText())
            }
        }.getOrNull()
    }
    val frameProfiles = remember(catalog, language) { catalog?.frames()?.map {
        ProfileOption(it.packageName(), "${it.label()} · ${it.channel()}", frameDetails(it, language))
    }.orEmpty() }
    val srProfiles = remember(catalog, language) { catalog?.sr()?.map {
        ProfileOption(it.packageName(), "${it.label()} · ${it.channel()}", srDetails(it, language))
    }.orEmpty() }
    val editable = supported && draft.enabled
    val featureEditable = editable && !demonstrationMode
    val superGyroAvailable = com.yuka.gamecube.tools.compat.RomFeatures.available(android.os.Build.VERSION.SDK_INT, "superGyro")
    val srAvailable = com.yuka.gamecube.tools.compat.RomFeatures.available(android.os.Build.VERSION.SDK_INT, "sr")
    val donorsValid = listOf(audioDonor, frameDonor, srDonor).all(::validDonorPackage)
    val pending = if (donorsValid) draft.withDonor("audio", audioDonor).withDonor("frame", frameDonor)
        .withDonor("sr", srDonor) else null
    Column(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.weight(1f).verticalScroll(scrollState).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard(inset = false) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        icon?.let { Image(it, null, Modifier.size(48.dp)) }
                            ?: Text(app.label.take(1), fontSize = 24.sp)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(app.label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Caption(app.packageName)
                    }
                }
                SectionDivider()
                ToggleRow(tr("Bật quy tắc riêng", "Enable app rules"), tr("Mở GameCube và áp dụng quy tắc cho ứng dụng này.", "Enable GameCube and apply this app's rules."),
                    draft.enabled, supported) { draft = draft.withEnabled(it) }
                if (!supported) Caption(tr("Thiết bị hoặc phiên bản GameCube chưa tương thích. Xem lý do ở Trang chủ.", "This device or GameCube version is not supported. See Home for details."), Modifier.padding(20.dp))
            }
            if (demonstrationMode) Caption(tr("Đã mở bằng Chế độ trình diễn. Tắt trong Chế độ nâng cao để chỉnh riêng.",
                "Unlocked by Demonstration mode. Turn it off in Advanced mode to edit these features."), Modifier.padding(horizontal = 8.dp))
            SectionTitle(tr("Điều khiển trò chơi", "Game controls"))
            SectionCard(inset = false) {
                ToggleRow(tr("Cảm ứng", "Touch"), tr("Mở điều chỉnh; chọn giá trị và xác nhận trong overlay.", "Unlock adjustments; choose values and confirm in the sidebar."), demonstrationMode || draft.touch, featureEditable) {
                    draft = draft.withFeature("touch", it)
                }
                SectionDivider()
                ToggleRow(tr("Con quay hồi chuyển", "Gyroscope"), tr("Mở điều chỉnh; hiệu lực phụ thuộc luồng cảm biến của game.", "Unlock adjustments; effects depend on the game sensor input."), demonstrationMode || draft.gyro, featureEditable) {
                    draft = draft.withFeature("gyro", it)
                }
                SectionDivider()
                ToggleRow(tr("Siêu cảm ứng", "Super touch"), tr("Mở công tắc trong GameCube (thử nghiệm).", "Unlock the GameCube switch (experimental)."), demonstrationMode || draft.superTouch, featureEditable) {
                    draft = draft.withFeature("superTouch", it)
                }
                SectionDivider()
                ToggleRow(tr("Siêu con quay", "Super gyroscope"), if (superGyroAvailable || demonstrationMode)
                    tr("Mở các mức đáp ứng và chống rung (thử nghiệm).", "Unlock response and anti-shake controls (experimental).")
                    else tr("Không có trên OriginOS 4.", "Unavailable on OriginOS 4."),
                    demonstrationMode || (draft.superGyro && superGyroAvailable), featureEditable && superGyroAvailable) {
                    draft = draft.withFeature("superGyro", it)
                }
            }
            SectionTitle(tr("Âm thanh & hình ảnh", "Audio and graphics"))
            SectionCard(inset = false) {
                ToggleRow(tr("Âm thanh trò chơi", "Game audio"), tr("Mở các hiệu ứng âm thanh của GameCube.", "Unlock GameCube audio effects."), demonstrationMode || draft.audio, featureEditable) {
                    draft = draft.withFeature("audio", it)
                }
                SectionDivider()
                ToggleRow(tr("Nội suy khung hình", "Frame interpolation"), tr("Mở mục tăng độ mượt khung hình (MEMC).", "Unlock smoother motion (MEMC)."), demonstrationMode || draft.frame, featureEditable) {
                    draft = draft.withFeature("frame", it)
                }
                SectionDivider()
                ToggleRow(tr("Siêu phân giải", "Super resolution"), if (srAvailable || demonstrationMode)
                    tr("Mở mục tăng độ nét hình ảnh (SR).", "Unlock image detail enhancement (SR).")
                    else tr("ROM OriginOS 4 này không có engine SR cho thiết bị.", "This OriginOS 4 ROM has no SR engine for this device."),
                    demonstrationMode || (draft.sr && srAvailable), featureEditable && srAvailable) {
                    draft = draft.withFeature("sr", it)
                }
                SectionDivider()
                ToggleRow("144 Hz", if (force144Global) tr("Theo cài đặt 144 Hz toàn hệ thống.", "Controlled by system-wide 144 Hz.")
                    else tr("Yêu cầu tần số quét 144 Hz khi chơi; không phải giới hạn FPS của game.",
                        "Request a 144 Hz display while playing; this does not set the game's FPS limit."),
                    force144Global || draft.force144, editable && !force144Global) { draft = draft.withFeature("force144", it) }
            }
            VisualTuningEditor(if (demonstrationMode) draft.visual.withEnabled(true) else draft.visual, featureEditable) { draft = draft.withVisual(it) }
            SectionTitle(tr("Hiệu năng", "Performance"))
            SectionCard(inset = false) {
                ToggleRow("Monster+", tr(
                    "Mở nút Monster+ trong GameCube.",
                    "Show Monster+ in GameCube."),
                    draft.monsterPlus, editable) { draft = draft.withFeature("monsterPlus", it) }
                if (showThermalControls) {
                    SectionDivider()
                    key(app.packageName) {
                        ThermalPreference(draft.thermalBypass, editable) { draft = draft.withFeature("thermalBypass", it) }
                    }
                }
            }
            if (showBenchmarkControls) {
                SectionCard(inset = false) {
                    key(app.packageName) { BenchmarkPreference(draft.benchmarkMode, editable) { draft = draft.withBenchmarkMode(it) } }
                }
            }
            SectionTitle(tr("Hồ sơ tham chiếu", "Reference profiles"))
            SectionCard(inset = false) {
                ProfilePicker(tr("Âm thanh", "Audio"), audioDonor, audioProfiles(), editable) { audioDonor = it }
                SectionDivider()
                ProfilePicker(tr("Nội suy khung hình", "Frame interpolation"), frameDonor, frameProfiles, editable) { frameDonor = it }
                SectionDivider()
                ProfilePicker(tr("Siêu phân giải", "Super resolution"), srDonor, srProfiles, editable && srAvailable) { srDonor = it }
            }
            GraphicsTuningEditor(app.packageName, draft.tuning, editable && draft.frame, editable && draft.sr && srAvailable,
                nativeScope, scopeError, requestScope, { tuningValid = it }, showNativeSrControls) {
                if (draft.tuning != it) draft = draft.withTuning(it)
            }
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(tr("Lưu cấu hình", "Save configuration"), {
                pending?.let(onSave)
            }, enabled = supported && tuningValid && pending != null && pending != initial,
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.fillMaxWidth())
        }
    }
}

internal fun validDonorPackage(value: String): Boolean = value.isEmpty() ||
    ConfigSnapshot.validPackage(value)
