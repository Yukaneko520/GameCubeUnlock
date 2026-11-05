package com.yuka.gamecube.tools.ui

import android.view.View
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.yuka.gamecube.tools.core.AppRule
import com.yuka.gamecube.tools.core.ConfigSnapshot
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.theme.MiuixTheme

class DemonstrationUiTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.NoActionBar")

    @Test fun advancedTogglePublishesTheFlagAndRelockingDoesNotEraseIt() {
        var completed = false
        paparazzi.gif(start = 0, end = 3_000, fps = 20) {
            CompositionLocalProvider(LocalLanguage provides UiLanguage.ENGLISH) { MiuixTheme {
                val view = LocalView.current
                val settings = remember { DeveloperSettings(hostDeveloperPreferences()).apply { unlock() } }
                var config by remember { mutableStateOf(ConfigSnapshot.defaults().withGlobal(true, 0, false)
                    .withApp("com.example.game", AppRule.defaults().withEnabled(true))) }
                var saves by remember { mutableIntStateOf(0) }
                DeveloperScreen(settings, config.demonstrationMode, true) {
                    config = config.withDemonstration(it); saves++
                }
                LaunchedEffect(Unit) {
                    frames(5)
                    check(row(view, "Demonstration mode").config[SemanticsActions.OnClick].action!!.invoke())
                    frames(4)
                    check(ConfigSnapshot.parse(config.toJson()).demonstrates("com.example.game") && saves == 1)
                    settings.lock(); frames(4); check(config.demonstrationMode)
                    settings.unlock(); frames(4)
                    check(row(view, "Demonstration mode").config[SemanticsProperties.ToggleableState] == ToggleableState.On)
                    check(row(view, "Demonstration mode").config[SemanticsActions.OnClick].action!!.invoke())
                    frames(4); check(!config.demonstrationMode && saves == 2)
                    completed = true
                }
            } }
        }
        assertTrue("Advanced demonstration toggle flow did not finish", completed)
    }

    @Test fun demoLocksOnlyFeatureGroupsAndRestoresSavedChoices() {
        var completed = false
        paparazzi.gif(start = 0, end = 8_000, fps = 20) {
            CompositionLocalProvider(LocalLanguage provides UiLanguage.ENGLISH) { MiuixTheme {
                val view = LocalView.current
                val scroll = rememberScrollState()
                var demo by remember { mutableStateOf(true) }
                var saved by remember { mutableStateOf(AppRule.defaults().withEnabled(true)) }
                var saves by remember { mutableIntStateOf(0) }
                AppRuleScreen(InstalledApp("com.example.game", "Example game", false), saved, true,
                    scrollState = scroll, demonstrationMode = demo, showThermalControls = true,
                    showBenchmarkControls = true) { saved = it; saves++ }
                LaunchedEffect(Unit) {
                    frames(5)
                    val features = listOf("Touch", "Gyroscope", "Super touch", "Super gyroscope",
                        "Game audio", "Frame interpolation", "Super resolution", "Custom game image enhancement")
                    suspend fun show(text: String) {
                        scroll.scrollTo((scroll.value + row(view, text).positionInRoot.y.toInt() - view.height / 3).coerceAtLeast(0))
                        frames(3)
                    }
                    fun checked(text: String, locked: Boolean, on: Boolean) {
                        val node = row(view, text)
                        check((node.config.getOrNull(SemanticsProperties.Disabled) != null) == locked) { text }
                        check(node.config[SemanticsProperties.ToggleableState] == ToggleableState(on)) { text }
                    }
                    for (text in features) { show(text); checked(text, true, true) }
                    for (text in listOf("144 Hz", "Monster+", "Bypass GameCube/GameWatch thermal limits")) {
                        show(text); checked(text, false, false)
                    }
                    show("144 Hz")
                    check(row(view, "144 Hz").config[SemanticsActions.OnClick].action!!.invoke())
                    frames(3)
                    check(row(view, "Save configuration").config[SemanticsActions.OnClick].action!!.invoke())
                    frames(3)
                    check(saves == 1 && saved.force144 && !saved.touch && !saved.visual.enabled())
                    demo = false; frames(3)
                    for (text in features) {
                        show(text)
                        val missing = when (text) {
                            "Super gyroscope" -> !com.yuka.gamecube.tools.compat.RomFeatures.available(android.os.Build.VERSION.SDK_INT, "superGyro")
                            "Super resolution" -> !com.yuka.gamecube.tools.compat.RomFeatures.available(android.os.Build.VERSION.SDK_INT, "sr")
                            else -> false
                        }
                        checked(text, missing, false)
                    }
                    show("144 Hz"); checked("144 Hz", false, true)
                    check(saves == 1)
                    completed = true
                }
            } }
        }
        assertTrue("Demonstration inheritance flow did not finish", completed)
    }

    private suspend fun frames(count: Int) { repeat(count) { withFrameNanos { } } }
    private fun row(view: View, text: String) = (view as ViewRootForTest).semanticsOwner
        .getAllSemanticsNodes(mergingEnabled = true).first {
            it.config.getOrNull(SemanticsProperties.Text)?.any { line -> line.text == text } == true
        }
}
