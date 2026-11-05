package com.yuka.gamecube.tools.ui

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.*
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Invokes the production switch/dialog semantics; confirmation must precede the first draft update. */
class ThermalConsentTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.NoActionBar")

    @Test fun cancelDoesNotEnableAndConfirmationIsRequiredForEachEnable() {
        var completed = false
        paparazzi.gif(start = 0L, end = 8_000L, fps = 20) {
            CompositionLocalProvider(LocalLanguage provides UiLanguage.ENGLISH) {
                MaterialTheme { MiuixTheme {
                    val view = LocalView.current
                    var enabled by remember { mutableStateOf(false) }
                    var updates by remember { mutableIntStateOf(0) }
                    Column { ThermalPreference(enabled, true) { enabled = it; updates++ } }
                    LaunchedEffect(Unit) {
                        frames(5)
                        check(texts(view).any { it.contains("Bypass thermal reductions and shutdowns of game features") })
                        click(view, "Bypass GameCube/GameWatch thermal limits")
                        frames(20)
                        check(!enabled && updates == 0) { "switch applied before consent" }
                        check(texts(view).any { it.contains("Thermal warning") }) { "warning is missing: ${texts(view)}" }
                        check(texts(view).any { it.contains("This option does not change CPU/GPU clock limits") })
                        click(view, "Cancel")
                        frames(20)
                        check(!enabled && updates == 0) { "cancel changed the draft" }
                        click(view, "Bypass GameCube/GameWatch thermal limits")
                        frames(20)
                        click(view, "Enable despite warning")
                        frames(20)
                        check(enabled && updates == 1) { "confirmation did not enable" }
                        click(view, "Bypass GameCube/GameWatch thermal limits")
                        frames(20)
                        check(!enabled && updates == 2) { "OFF should apply without confirmation" }
                        click(view, "Bypass GameCube/GameWatch thermal limits")
                        frames(20)
                        check(!enabled && updates == 2) { "second ON skipped consent" }
                        completed = true
                    }
                } }
            }
        }
        assertTrue("consent scenario did not complete", completed)
    }
    @Test fun benchmarkRequiresConsentAndSelectsBothFrequencyModes() {
        var completed = false
        paparazzi.gif(start = 0L, end = 12_000L, fps = 20) {
            CompositionLocalProvider(LocalLanguage provides UiLanguage.ENGLISH) {
                MiuixTheme {
                    val view = LocalView.current
                    var mode by remember { mutableIntStateOf(0) }
                    var editable by remember { mutableStateOf(true) }
                    var changes by remember { mutableIntStateOf(0) }
                    Column { BenchmarkPreference(mode, editable) { mode = it; changes++ } }
                    LaunchedEffect(Unit) {
                        frames(5)
                        click(view, "CPU/GPU benchmark mode"); frames(15)
                        check(mode == 0 && changes == 0)
                        check(texts(view).any { it.contains("Benchmark warning") })
                        click(view, "Cancel"); frames(15)
                        check(mode == 0 && changes == 0)
                        click(view, "CPU/GPU benchmark mode"); frames(15)
                        click(view, "Enable despite warning"); frames(15)
                        check(mode == 1 && changes == 1)
                        click(view, "CPU/GPU frequency"); frames(15)
                        click(view, "Request maximum"); frames(15)
                        check(mode == 2 && changes == 2)
                        click(view, "CPU/GPU frequency"); frames(15)
                        click(view, "Automatic"); frames(15)
                        check(mode == 1 && changes == 3)
                        click(view, "CPU/GPU benchmark mode"); frames(15)
                        check(mode == 0 && changes == 4)
                        click(view, "CPU/GPU benchmark mode"); frames(15)
                        check(mode == 0 && changes == 4)
                        editable = false; frames(15)
                        check(texts(view).none { it.contains("Benchmark warning") })
                        check(mode == 0 && changes == 4)
                        completed = true
                    }
                }
            }
        }
        assertTrue("Benchmark consent/mode flow did not complete", completed)
    }

    @Test fun flaRequiresConsentAndRevocationClosesTheWarning() {
        var completed = false
        paparazzi.gif(start = 0L, end = 8_000L, fps = 20) {
            CompositionLocalProvider(LocalLanguage provides UiLanguage.ENGLISH) { MiuixTheme {
                val view = LocalView.current
                var disabled by remember { mutableStateOf(false) }
                var editable by remember { mutableStateOf(true) }
                var changes by remember { mutableIntStateOf(0) }
                Column { FlaPreference(disabled, editable) { disabled = it; changes++ } }
                LaunchedEffect(Unit) {
                    frames(5)
                    click(view, "Disable FLA (experimental)"); frames(15)
                    check(!disabled && changes == 0)
                    check(texts(view).any { it.contains("does not clear an existing CPU ceiling") })
                    click(view, "Cancel"); frames(15)
                    check(!disabled && changes == 0)
                    click(view, "Disable FLA (experimental)"); frames(15)
                    click(view, "Enable experiment"); frames(15)
                    check(disabled && changes == 1)
                    click(view, "Disable FLA (experimental)"); frames(15)
                    check(!disabled && changes == 2)
                    click(view, "Disable FLA (experimental)"); frames(15)
                    editable = false; frames(15)
                    check(texts(view).none { it.contains("Disable FLA experiment") })
                    check(!disabled && changes == 2)
                    completed = true
                }
            } }
        }
        assertTrue("FLA consent scenario did not complete", completed)
    }
    private suspend fun frames(count: Int) { repeat(count) { withFrameNanos { } } }
    private fun nodes(view: View): List<SemanticsNode> = buildList {
        val visited = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<View, Boolean>())
        fun visit(v: View) {
            if (!visited.add(v)) return
            if (v is ViewRootForTest) addAll(v.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true))
            if (v is ViewGroup) repeat(v.childCount) { visit(v.getChildAt(it)) }
        }
        visit(view.rootView)
        // WindowDialog uses an Android Dialog window with its own Compose semantics owner.
        val manager = Class.forName("android.view.WindowManagerGlobal")
        val instance = manager.getDeclaredMethod("getInstance").invoke(null)
        val field = manager.getDeclaredField("mViews").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (field.get(instance) as List<View>).forEach(::visit)
    }
    private fun label(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun texts(view: View) = nodes(view).map(::label).filter { it.isNotEmpty() }
    private fun click(view: View, text: String) {
        val node = nodes(view).firstOrNull { label(it).contains(text) && it.config.getOrNull(SemanticsActions.OnClick)?.action != null }
            ?: error("Clickable '$text' missing: ${texts(view)}")
        check(node.config[SemanticsActions.OnClick].action!!.invoke())
    }
}
