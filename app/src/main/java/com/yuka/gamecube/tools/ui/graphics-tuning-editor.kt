package com.yuka.gamecube.tools.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.yuka.gamecube.tools.core.GraphicsTuning
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun GraphicsTuningEditor(pkg: String, initial: GraphicsTuning, frameEnabled: Boolean, srEnabled: Boolean,
    nativeScope: Boolean, scopeError: String, requestScope: () -> Unit, validity: (Boolean) -> Unit,
    showNativeSrControls: Boolean = false,
    change: (GraphicsTuning) -> Unit) {
    var frame by rememberSaveable(pkg) { mutableStateOf(initial.frameCustom()) }
    var sr by rememberSaveable(pkg) { mutableStateOf(initial.srCustom()) }
    var boost by rememberSaveable(pkg) { mutableStateOf(GraphicsTuning.formatTuples(initial.boost())) }
    var power by rememberSaveable(pkg) { mutableStateOf(GraphicsTuning.formatTuples(initial.power())) }
    var algorithm by rememberSaveable(pkg) { mutableIntStateOf(initial.algorithm()) }
    var width by rememberSaveable(pkg) { mutableStateOf(initial.targetShortSide().toString()) }
    var source by rememberSaveable(pkg) { mutableIntStateOf(initial.sourceSize()) }
    var sharp by rememberSaveable(pkg) { mutableStateOf(initial.sharpness().toString()) }
    var dark by rememberSaveable(pkg) { mutableStateOf(initial.darkLimit().toString()) }
    var white by rememberSaveable(pkg) { mutableStateOf(initial.whiteLimit().toString()) }
    val parsed = remember(frame, sr, frameEnabled, srEnabled, boost, power, algorithm, width, source, sharp, dark, white) {
        runCatching { GraphicsTuning(frame,
            if (frame && frameEnabled) GraphicsTuning.parseTuples(boost) else initial.boost(),
            if (frame && frameEnabled) GraphicsTuning.parseTuples(power) else initial.power(), sr,
            algorithm, if (sr && srEnabled) width.toInt() else initial.targetShortSide(), source,
            if (sr && srEnabled) sharp.toDouble() else initial.sharpness(), if (sr && srEnabled) dark.toDouble() else initial.darkLimit(),
            if (sr && srEnabled) white.toDouble() else initial.whiteLimit()) }
    }
    LaunchedEffect(parsed.getOrNull(), parsed.isFailure) {
        validity(parsed.isSuccess)
        parsed.getOrNull()?.let(change)
    }
    SectionTitle(tr("Tùy chỉnh nâng cao", "Advanced tuning"))
    SectionCard(inset = false) {
        ToggleRow(tr("Thông số nội suy riêng", "Custom interpolation"),
            tr("Đặt FPS game, FPS đầu vào và FPS đầu ra cho từng chế độ.",
                "Set game, input and output FPS for each mode."), frame, frameEnabled) { frame = it }
        if (frame) Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FrameModeEditor(tr("Hiệu suất · FPS", "Boost · FPS"), boost, frameEnabled) { boost = it }
            FrameModeEditor(tr("Tiết kiệm điện · FPS", "Power saving · FPS"), power, frameEnabled) { power = it }
            Caption(tr("Đầu ra tối đa 144 FPS. Với game SDK, thêm một dòng cho từng mức FPS game; game khác dùng dòng đầu.",
                "Output up to 144 FPS. For SDK games, add a row per game FPS level; other games use the first row."))
            Caption(tr("Lưu rồi bật lại nội suy trong overlay để áp dụng hoặc khôi phục.",
                "Save, then toggle interpolation in the sidebar to apply or restore."))
        }
    }
    if (showNativeSrControls) {
        SectionTitle(tr("Chế độ nâng cao", "Advanced mode"))
        SectionCard(inset = false) {
            val nativeAvailable = com.yuka.gamecube.tools.compat.RomFeatures.available(android.os.Build.VERSION.SDK_INT, "nativeSr")
            ToggleRow(tr("Thông số SR riêng", "Custom SR"),
                nativeSrSummary(),
                sr && nativeAvailable, srEnabled && nativeAvailable) { sr = it }
            if (!nativeAvailable) Caption(tr("Không có trên ROM này.", "Unavailable on this ROM."), Modifier.padding(20.dp))
            if (sr && nativeAvailable) {
                val algorithms = listOf(tr("Theo Vivo", "Vivo default")) + (1..6).map { tr("Thuật toán $it", "Algorithm $it") }
                TuningChoice(tr("Thuật toán SR", "SR algorithm"), algorithms, algorithm, srEnabled) { algorithm = it }
                TuningChoice(tr("Kích thước nguồn", "Source size"), listOf(tr("Theo Vivo", "Vivo default"),
                    tr("Cho phép tăng kích thước", "Allow upscaling"), tr("Giữ kích thước nguồn", "Keep source size")),
                    source + 1, srEnabled) { source = it - 1 }
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumericField(width, { width = it }, tr("Cạnh ngắn đích · px (0 = tự động)", "Target short side · px (0 = auto)"),
                        srEnabled, Modifier.fillMaxWidth())
                    Caption(tr("Đây là số pixel, không phải hệ số: 1080 = 1080 px, không dùng 2 để chỉ 2×. Chọn cho phép tăng kích thước để dùng giá trị này; Vivo có thể giữ kích thước nguồn nếu không phù hợp.",
                        "This is pixels, not a scale: 1080 means 1080 px, not 2 for 2×. Allow upscaling to use this value; Vivo may keep the source size if unsuitable."))
                    NumericField(sharp, { sharp = it }, tr("Độ sắc nét · 0–10", "Sharpness · 0–10"),
                        srEnabled, Modifier.fillMaxWidth(), decimal = true)
                    NumericField(dark, { dark = it }, tr("Giới hạn vùng tối · −255–0", "Dark limit · −255–0"),
                        srEnabled, Modifier.fillMaxWidth(), decimal = true, negative = true)
                    NumericField(white, { white = it }, tr("Giới hạn vùng sáng · 0–255", "White limit · 0–255"),
                        srEnabled, Modifier.fillMaxWidth(), decimal = true)
                    Caption(tr("Thuật toán 1–6 dùng bộ hệ số riêng của Vivo; shader của chúng không đọc trực tiếp ba giá trị trên. Tăng các số này không đảm bảo ảnh sắc nét hơn; số thuật toán cũng không phải mức chất lượng.",
                        "Algorithms 1–6 use Vivo kernels; their shaders do not directly read the three values above. Higher values do not guarantee sharper output; algorithm numbers are not quality levels."))
                    Caption(tr("Hook LSPosed vào game có thể gây crash hoặc bị cơ chế bảo vệ ứng dụng nhận là can thiệp. Scope game chỉ cần cho custom SR.",
                        "LSPosed injection can cause crashes or be detected as interference by app protection. Game scope is only needed for custom SR."))
                    if (!nativeScope) {
                        Caption(tr("Cho phép mô-đun chạy trong game qua LSPosed, rồi mở lại game.",
                            "Allow the module in this game through LSPosed, then restart the game."))
                        TextButton(tr("Cấp phạm vi cho game", "Grant game scope"), requestScope,
                            enabled = srEnabled, modifier = Modifier.fillMaxWidth())
                    }
                    if (scopeError.isNotEmpty()) Caption(scopeError)
                }
            }
        }
    }
    if (parsed.isFailure) Text(tr("Thông số không hợp lệ. Kiểm tra FPS, phạm vi và dấu thập phân.",
            "Invalid values. Check FPS, ranges and decimal notation."), color = MiuixTheme.colorScheme.error,
            modifier = Modifier.padding(20.dp))
}

@Composable
internal fun nativeSrSummary() = tr("Nạp thư viện vào tiến trình game; cần scope LSPosed của game và mở lại game.",
    "Loads a library into the game process; requires its LSPosed scope and a game restart.")

@Composable
internal fun TuningChoice(title: String, options: List<String>, selected: Int, enabled: Boolean, select: (Int) -> Unit) {
    var show by remember { mutableStateOf(false) }
    PreferenceRow(title, options.getOrElse(selected) { options[0] }, enabled = enabled,
        holdDownState = show, onClick = { show = true })
    WindowDialog(show = show, title = title, onDismissRequest = { show = false }) {
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()).selectableGroup()) {
            options.forEachIndexed { index, option ->
                Row(Modifier.fillMaxWidth().selectable(index == selected, enabled = enabled, role = Role.RadioButton,
                    onClick = { select(index); show = false }).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(option, Modifier.weight(1f)); RadioButton(index == selected, null, enabled = enabled)
                }
            }
        }
    }
}
