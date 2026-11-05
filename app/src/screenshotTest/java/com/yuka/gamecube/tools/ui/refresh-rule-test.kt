package com.yuka.gamecube.tools.ui

import android.os.SystemClock
import android.view.MotionEvent
import android.view.InputDevice
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.theme.MiuixTheme

class RefreshRuleTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.NoActionBar")

    @Test fun globalLocksEffectiveSwitchWithoutChangingSavedAppValue() {
        var completed = false
        paparazzi.gif(start = 0, end = 6_000, fps = 20) {
            CompositionLocalProvider(LocalLanguage provides UiLanguage.ENGLISH) {
                MiuixTheme {
                    val view = LocalView.current
                    val scroll = rememberScrollState()
                    var global by remember { mutableStateOf(true) }
                    var saved by remember { mutableStateOf(AppRule.defaults().withEnabled(true)) }
                    var saves by remember { mutableIntStateOf(0) }
                    AppRuleScreen(InstalledApp("com.example.game", "Example game", false), saved, true,
                        scrollState = scroll, force144Global = global) { saved = it; saves++ }
                    LaunchedEffect(Unit) {
                        frames(5)
                        scroll.scrollTo((scroll.value + row(view, "144 Hz").positionInRoot.y.toInt() - view.height / 3).coerceAtLeast(0))
                        frames(4)
                        fun checkSwitch(locked: Boolean, checked: Boolean) {
                            val node = row(view, "144 Hz")
                            check((node.config.getOrNull(SemanticsProperties.Disabled) != null) == locked) { "Expected locked=$locked: ${node.config}" }
                            check(node.config[SemanticsProperties.ToggleableState] == ToggleableState(checked)) { "Expected checked=$checked: ${node.config}" }
                        }
                        fun clean() {
                            val node = row(view, "Save configuration")
                            check(node.config.getOrNull(SemanticsActions.OnClick) == null ||
                                node.config.getOrNull(SemanticsProperties.Disabled) != null)
                        }
                        checkSwitch(true, true); clean()
                        tap(view, row(view, "144 Hz")); frames(4)
                        checkSwitch(true, true); clean(); check(!saved.force144 && saves == 0)
                        global = false; frames(4)
                        checkSwitch(false, false); clean()
                        tap(view, row(view, "144 Hz")); frames(4)
                        checkSwitch(false, true)
                        check(row(view, "Save configuration").config[SemanticsActions.OnClick].action!!.invoke())
                        frames(4); check(saved.force144 && saves == 1); clean()
                        global = true; frames(4)
                        checkSwitch(true, true); clean()
                        global = false; frames(4)
                        checkSwitch(false, true); clean(); check(saved.force144 && saves == 1)
                        completed = true
                    }
                }
            }
        }
        assertTrue("Global/per-app refresh UI scenario did not complete", completed)
    }
    private suspend fun frames(count: Int) { repeat(count) { withFrameNanos { } } }
    private fun row(view: View, text: String) = (view as ViewRootForTest).semanticsOwner
        .getAllSemanticsNodes(mergingEnabled = true).first {
            it.config.getOrNull(SemanticsProperties.Text)?.any { line -> line.text == text } == true
        }
    private suspend fun tap(view: View, node: SemanticsNode) {
        val bounds = node.boundsInRoot
        val point = bounds.center
        check(bounds.width > 0 && bounds.height > 0 && point.y in 0f..view.height.toFloat()) { "Tap target is not visible: $bounds" }
        val start = SystemClock.uptimeMillis()
        fun send(action: Int) {
            val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, point.x, point.y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { view.dispatchTouchEvent(event) } finally { event.recycle() }
        }
        send(MotionEvent.ACTION_DOWN); frames(2)
        send(MotionEvent.ACTION_UP)
    }
}
