package com.yuka.gamecube.tools.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.yuka.gamecube.tools.core.NativeProfileCatalog.FrameProfile
import com.yuka.gamecube.tools.core.NativeProfileCatalog.FrameValues
import com.yuka.gamecube.tools.core.NativeProfileCatalog.SrProfile
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

internal data class ProfileOption(val packageName: String, val title: String, val details: String)

private fun autoProfile(language: UiLanguage) = ProfileOption("", language.text("Tự động", "Automatic"),
    language.text("Chọn nguồn gốc theo chính sách tự động và kiểm tra hiện tại của Vivo.",
        "Select an original source using automatic policy and current Vivo checks."))

internal fun selectedProfile(value: String, profiles: List<ProfileOption>, language: UiLanguage = UiLanguage.VIETNAMESE): ProfileOption =
    if (value.isEmpty()) autoProfile(language) else profiles.firstOrNull { it.packageName == value }
        ?: ProfileOption(value, language.text("Tùy chỉnh", "Custom"),
            language.text("Gói này không có trong danh mục gốc đã chụp.", "This package is not in the original catalog snapshot."))

internal fun applyProfileSelection(current: String, requested: String, editable: Boolean): String =
    if (editable) requested.trim() else current

internal fun frameTuples(values: List<FrameValues>, language: UiLanguage = UiLanguage.VIETNAMESE): String = values.takeIf { it.isNotEmpty() }
    ?.joinToString("; ") { language.text("Game ${it.gameFps()} / hạ ${it.depressFps()} / nội suy ${it.interFps()} FPS",
        "Game ${it.gameFps()} / input ${it.depressFps()} / output ${it.interFps()} FPS") }
    ?: language.text("Không có", "None")

internal fun frameDetails(profile: FrameProfile, language: UiLanguage = UiLanguage.VIETNAMESE): String =
    "Boost: ${frameTuples(profile.boost(), language)}\n${language.text("Tối ưu điện", "Power saving")}: ${frameTuples(profile.power(), language)}\n" +
        "Index: ${profile.index()} • SDK: ${if (profile.sdk()) language.text("Có", "Yes") else language.text("Không", "No")} • Monster+: ${profile.monsterPlus()}"

private fun associatedFrames(depress: Int, inter: Int, language: UiLanguage): String =
    if (depress < 0 || inter < 0) language.text("Không có ($depress / $inter)", "None ($depress / $inter)")
    else language.text("hạ $depress / nội suy $inter FPS", "input $depress / output $inter FPS")

internal fun srDetails(profile: SrProfile, language: UiLanguage = UiLanguage.VIETNAMESE): String =
    "${if (profile.state() == 1) "HIGH (state 1)" else language.text("State ${profile.state()} · chế độ chưa xác minh", "State ${profile.state()} · unverified mode")}\n" +
        "Index: ${profile.index()} • Monster+: ${profile.monsterPlus()}\n" +
        "${language.text("Nội suy kèm SR", "MEMC with SR")}: ${associatedFrames(profile.frameDepress(), profile.frameInter(), language)}\n" +
        "${language.text("Nội suy kèm HIGH", "MEMC with HIGH")}: ${associatedFrames(profile.highDepress(), profile.highInter(), language)}"

@Composable
internal fun ProfilePicker(title: String, value: String, profiles: List<ProfileOption>,
    enabled: Boolean, change: (String) -> Unit) {
    var show by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(false) }
    val language = LocalLanguage.current
    val selected = selectedProfile(value, profiles, language)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PreferenceRow(title = title, summary = selected.title,
            enabled = enabled, holdDownState = show,
            onClick = {
                manual = value.isNotEmpty() && profiles.none { it.packageName == value }
                show = true
            })
        if (!validDonorPackage(value)) Text(tr("Tên gói không hợp lệ; cần sửa trước khi lưu.", "Invalid package name; correct it before saving."),
            modifier = Modifier.padding(horizontal = 20.dp), color = MiuixTheme.colorScheme.error)
    }
    WindowDialog(show = show, title = tr("Hồ sơ · $title", "Profile · $title"),
        onDismissRequest = { show = false }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (manual) {
                TextField(value, { change(applyProfileSelection(value, it, enabled)) },
                    label = tr("Tên gói tham chiếu", "Reference package name"), singleLine = true, enabled = enabled,
                    modifier = Modifier.fillMaxWidth())
                if (!validDonorPackage(value)) Text(tr("Tên gói không hợp lệ.", "Invalid package name."), color = MiuixTheme.colorScheme.error)
            } else {
                val choices = remember(profiles, language) { listOf(autoProfile(language)) + profiles }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp).selectableGroup()) {
                    items(choices, key = { it.packageName }) { option ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                            selected = value == option.packageName, enabled = enabled, role = Role.RadioButton,
                            onClick = {
                                change(applyProfileSelection(value, option.packageName, enabled))
                                show = false
                            }).padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(option.title)
                                if (option.packageName.isNotEmpty()) Caption(option.packageName)
                                Caption(option.details)
                            }
                            RadioButton(selected = value == option.packageName, onClick = null, enabled = enabled)
                        }
                    }
                }
            }
            TextButton(if (manual) tr("Chọn từ danh mục", "Select from catalog") else tr("Nhập gói thủ công", "Enter package manually"), { manual = !manual },
                enabled = enabled, modifier = Modifier.fillMaxWidth())
            TextButton(tr("Đóng", "Close"), { show = false }, modifier = Modifier.fillMaxWidth())
        }
    }
}
