package com.yuka.gamecube.tools.ui

import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.*
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.yuka.gamecube.tools.core.AppRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.theme.MiuixTheme

class DeveloperFlowTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.NoActionBar")

    @Test fun sevenTapsPersistUnlockAndVisibilityWithoutChangingThermalRule() {
        var completed = false
        paparazzi.gif(start = 0, end = 9_000, fps = 20) {
            CompositionLocalProvider(LocalLanguage provides UiLanguage.ENGLISH) {
                MaterialTheme { MiuixTheme {
                    val view = LocalView.current
                    val prefs = remember { hostDeveloperPreferences() }
                    val settings = remember { DeveloperSettings(prefs) }
                    var page by remember { mutableIntStateOf(0) }
                    val app = remember { InstalledApp("com.example.game", "Example game", false) }
                    val rule = remember { AppRule.defaults().withEnabled(true).withFeature("thermalBypass", true) }
                    when (page) {
                        0 -> SettingsScreen("Connected", {}, Appearance.SYSTEM, {}, UiLanguage.ENGLISH, {}, {},
                            settings, openDeveloper = { page = 1 })
                        1 -> ManagerFrame(2, "Advanced mode", true, onBack = { page = 0 }) { padding, _ ->
                            Box(Modifier.padding(padding)) { DeveloperScreen(settings) }
                        }
                        2 -> AppRuleScreen(app, rule, true, showThermalControls = settings.showThermalControls,
                            showBenchmarkControls = settings.showBenchmarkControls,
                            showNativeSrControls = settings.showNativeSrControls) {
                            error("Visibility must not save the rule")
                        }
                    }
                    LaunchedEffect(Unit) {
                        frames(4)
                        settings.showThermalControls(true)
                        settings.showBenchmarkControls(true)
                        settings.showNativeSrControls(true)
                        check(!settings.showNativeSrControls && !prefs.getBoolean("show_native_sr_controls", false))
                        check(!settings.showBenchmarkControls)
                        check(!settings.showThermalControls && !prefs.getBoolean("show_thermal_controls", false))
                        check(texts(view).none { it == "Advanced mode" })
                        repeat(6) { click(view, "Telegram"); frames(2); check(!settings.unlocked) }
                        click(view, "Telegram"); frames(4)
                        check(settings.unlocked && !settings.showThermalControls && !settings.showBenchmarkControls)
                        check(texts(view).any { it == "Advanced mode" })
                        click(view, "Advanced mode"); frames(4)
                        click(view, "Show GameCube/GameWatch thermal bypass"); frames(4)
                        check(settings.showThermalControls && !settings.showBenchmarkControls)
                        click(view, "Show CPU/GPU benchmark mode"); frames(4)
                        check(settings.showBenchmarkControls && DeveloperSettings(prefs).showBenchmarkControls)
                        click(view, "Show custom SR parameters"); frames(4)
                        check(settings.showNativeSrControls && DeveloperSettings(prefs).showNativeSrControls)
                        check(DeveloperSettings(prefs).let { it.unlocked && it.showThermalControls })
                        click(view, "Back"); frames(4)
                        check(texts(view).any { it.startsWith("Telegram") })
                        page = 2; frames(5)
                        check(texts(view).any { it.startsWith("Custom SR") })
                        settings.showNativeSrControls(false); frames(4)
                        check(texts(view).none { it.startsWith("Custom SR") })
                        check(texts(view).any { it.startsWith("Bypass GameCube/GameWatch thermal limits") })
                        check(texts(view).any { it.startsWith("CPU/GPU benchmark mode") })
                        settings.showBenchmarkControls(false); frames(4)
                        check(texts(view).none { it.startsWith("CPU/GPU benchmark mode") })
                        settings.showThermalControls(false); frames(5)
                        check(texts(view).none { it.startsWith("Bypass GameCube/GameWatch thermal limits") })
                        check(rule.thermalBypass && !DeveloperSettings(prefs).showThermalControls)
                        completed = true
                    }
                } }
            }
        }
        assertTrue("Developer flow did not complete", completed)
    }

    @Test fun incompleteTapSequenceResetsWhenLeavingSettings() {
        var completed = false
        paparazzi.gif(start = 0, end = 4_000, fps = 20) {
            CompositionLocalProvider(LocalLanguage provides UiLanguage.ENGLISH) {
                MiuixTheme {
                    val view = LocalView.current
                    val settings = remember { DeveloperSettings(hostDeveloperPreferences()) }
                    var visible by remember { mutableStateOf(true) }
                    if (visible) SettingsScreen("Connected", {}, Appearance.SYSTEM, {}, UiLanguage.ENGLISH, {}, {}, settings)
                    LaunchedEffect(Unit) {
                        frames(3)
                        repeat(3) { click(view, "Telegram"); frames(2) }
                        visible = false; frames(3); visible = true; frames(3)
                        repeat(4) { click(view, "Telegram"); frames(2) }
                        check(!settings.unlocked)
                        completed = true
                    }
                }
            }
        }
        assertTrue(completed)
    }

    @Test fun turningOffAdvancedModeLocksAndRequiresSevenNewTaps() {
        var completed = false
        paparazzi.gif(start = 0, end = 6_000, fps = 20) {
            CompositionLocalProvider(LocalLanguage provides UiLanguage.ENGLISH) {
                MiuixTheme {
                    val view = LocalView.current
                    val prefs = remember { hostDeveloperPreferences() }
                    val settings = remember { DeveloperSettings(prefs).apply {
                        unlock(); showThermalControls(true); showBenchmarkControls(true); showNativeSrControls(true)
                    } }
                    if (settings.unlocked) DeveloperScreen(settings)
                    else SettingsScreen("Connected", {}, Appearance.SYSTEM, {}, UiLanguage.ENGLISH, {}, {}, settings)
                    LaunchedEffect(Unit) {
                        frames(4)
                        click(view, "Enable advanced mode"); frames(5)
                        check(!settings.unlocked && !settings.showThermalControls && !settings.showBenchmarkControls)
                        check(!settings.showNativeSrControls && !DeveloperSettings(prefs).showNativeSrControls)
                        check(DeveloperSettings(prefs).let { !it.unlocked && !it.showThermalControls && !it.showBenchmarkControls })
                        check(texts(view).none { it == "Advanced mode" })
                        repeat(6) { click(view, "Telegram"); frames(2); check(!settings.unlocked) }
                        click(view, "Telegram"); frames(4)
                        check(settings.unlocked && !settings.showThermalControls && !settings.showBenchmarkControls)
                        completed = true
                    }
                }
            }
        }
        assertTrue(completed)
    }

    private suspend fun frames(count: Int) { repeat(count) { withFrameNanos { } } }
    private fun nodes(view: View) = (view as ViewRootForTest).semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)
    private fun label(node: SemanticsNode) = (node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
        ?: node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()).trim()
    private fun texts(view: View) = nodes(view).map(::label)
    private fun click(view: View, text: String) {
        val node = nodes(view).firstOrNull { label(it).contains(text) && it.config.getOrNull(SemanticsActions.OnClick)?.action != null }
            ?: error("Clickable '$text' missing: ${texts(view)}")
        check(node.config[SemanticsActions.OnClick].action!!.invoke())
    }
}

// Layoutlib's BridgeContext preferences are no-op; exercise Android's actual preference implementation instead.
internal fun hostDeveloperPreferences(): android.content.SharedPreferences {
    val folder = java.nio.file.Files.createTempDirectory("developer-ui-prefs").toFile()
    val type = Class.forName("android.app.SharedPreferencesImpl")
    return type.getDeclaredConstructor(java.io.File::class.java, Int::class.javaPrimitiveType)
        .apply { isAccessible = true }.newInstance(java.io.File(folder, "settings.xml"), 0) as android.content.SharedPreferences
}
