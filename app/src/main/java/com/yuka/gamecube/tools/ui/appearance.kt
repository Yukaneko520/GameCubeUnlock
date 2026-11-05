package com.yuka.gamecube.tools.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.window.WindowDialog

internal enum class Appearance(val label: String, val mode: ColorSchemeMode) {
    SYSTEM("Theo hệ thống", ColorSchemeMode.System), LIGHT("Sáng", ColorSchemeMode.Light), DARK("Tối", ColorSchemeMode.Dark);
    companion object {
        fun fromStored(value: String?): Appearance = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

@Composable
internal fun AppearanceSelector(selected: Appearance, select: (Appearance) -> Unit) {
    SectionCard(inset = false) {
        Column(Modifier.selectableGroup()) {
            Appearance.entries.forEach { appearance ->
                val label = when (appearance) { Appearance.SYSTEM -> tr("Theo hệ thống", "System default")
                    Appearance.LIGHT -> tr("Sáng", "Light"); Appearance.DARK -> tr("Tối", "Dark") }
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected == appearance,
                    role = Role.RadioButton, onClick = { select(appearance) }).padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f).padding(vertical = 8.dp))
                    RadioButton(selected == appearance, onClick = null)
                }
            }
        }
    }
}

@Composable
internal fun SettingsScreen(status: String, refresh: () -> Unit,
    appearance: Appearance, changeAppearance: (Appearance) -> Unit, language: UiLanguage,
    changeLanguage: (UiLanguage) -> Unit, openLogs: () -> Unit,
    developer: DeveloperSettings, openDeveloper: () -> Unit = {}) {
    var languageDialog by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle(tr("Giao diện", "Appearance"))
        SectionCard(inset = false) {
            PreferenceRow(title = tr("Ngôn ngữ", "Language"), summary = language.label,
                holdDownState = languageDialog, onClick = { languageDialog = true })
        }
        AppearanceSelector(appearance, changeAppearance)
        SectionTitle(tr("Chẩn đoán", "Diagnostics"))
        SectionCard(inset = false) {
            PreferenceRow(title = tr("Nhật ký ứng dụng và hook", "App and hook logs"),
                summary = tr("Xem trực tiếp, đọc log hook và chia sẻ để debug.", "View live logs, capture hook logs and share a debug report."),
                onClick = openLogs)
            SectionDivider()
            PreferenceRow(title = tr("Kết nối LSPosed", "LSPosed connection"), summary = connectionLabel(status),
                onClick = refresh)
            if (developer.unlocked) {
                SectionDivider()
                PreferenceRow(tr("Chế độ nâng cao", "Advanced mode"), "", onClick = openDeveloper)
            }
        }
        SectionTitle(tr("Tác giả", "Author"))
        DeveloperUnlockAuthor(developer)
    }
    WindowDialog(show = languageDialog, title = tr("Ngôn ngữ", "Language"), onDismissRequest = { languageDialog = false }) {
        Column(Modifier.selectableGroup()) {
            UiLanguage.entries.forEach { option ->
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).selectable(language == option, role = Role.RadioButton,
                    onClick = { changeLanguage(option); languageDialog = false }), verticalAlignment = Alignment.CenterVertically) {
                    Text(option.label, Modifier.weight(1f))
                    RadioButton(language == option, onClick = null)
                }
            }
        }
    }
}
