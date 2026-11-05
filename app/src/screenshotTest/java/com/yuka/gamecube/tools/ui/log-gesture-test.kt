package com.yuka.gamecube.tools.ui

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.theme.MiuixTheme

class LogGestureTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.NoActionBar")

    @Test fun largeLiveBufferKeepsLazyRowsAndBothScrollAxesWithHeaderRoundTrip() {
        val source = "--------- beginning of main\n" + (0..3999).joinToString("\n") {
            "10-24 12:00:00.000 12345 12346 I GameCubeUnlock: ROW $it " + "details ".repeat(30)
        }
        var completed = false
        paparazzi.gif(start = 0L, end = 5_000L, fps = 30) {
            MaterialTheme { MiuixTheme {
                val view = LocalView.current
                val density = LocalDensity.current.density
                var text by remember { mutableStateOf(source) }
                ManagerFrame(2, "Logs", detail = true, navigationInsets = WindowInsets(0)) { padding, scroll ->
                    Box(Modifier.fillMaxSize().padding(padding).nestedScroll(scroll.nestedScrollConnection)) {
                        LogBufferView(text, Modifier.fillMaxSize())
                    }
                    LaunchedEffect(Unit) {
                        frames(6)
                        fun nodes() = (view as ViewRootForTest).semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)
                        fun rows() = nodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }
                            .filter { it.text.contains("GameCubeUnlock: ROW") }
                        fun axis(horizontal: Boolean) = nodes().mapNotNull {
                            it.config.getOrNull(if (horizontal) SemanticsProperties.HorizontalScrollAxisRange
                                else SemanticsProperties.VerticalScrollAxisRange)
                        }.first { it.maxValue() > 0f }
                        check(rows().size in 1..100) { "Log viewer composed the entire buffer: ${rows().size}" }
                        check(rows().all { '\n' !in it.text }) { "Log viewer still lays out a full-buffer paragraph" }
                        check(scroll.state.collapsedFraction < .02f)
                        drag(view, density, 190f, 580f, 190f, 280f)
                        frames(8)
                        check(scroll.state.collapsedFraction > .98f) { "Log header did not collapse" }
                        check(axis(false).value() > 0f) { "Log rows did not scroll" }
                        val before = axis(false).value()
                        text += "\n10-24 12:00:01.000 12345 12346 W GameCubeUnlock: new record"
                        frames(6)
                        val after = axis(false).value()
                        check(kotlin.math.abs(after - before) < .001f) {
                            "Live append moved the scroll position: $before -> $after"
                        }
                        drag(view, density, 310f, 400f, 70f, 400f)
                        frames(6)
                        check(axis(true).value() > 0f) { "Horizontal log scrolling was lost" }
                        drag(view, density, 190f, 250f, 190f, 700f)
                        frames(8)
                        check(scroll.state.collapsedFraction < .02f) { "Log header did not reopen" }
                        check(rows().size in 1..100)
                        completed = true
                    }
                }
            } }
        }
        assertTrue("Large-buffer touch scenario did not finish", completed)
    }

    private suspend fun frames(count: Int) { repeat(count) { withFrameNanos { } } }
    private suspend fun drag(view: View, density: Float, x0: Float, y0: Float, x1: Float, y1: Float) {
        val down = SystemClock.uptimeMillis()
        fun send(action: Int, fraction: Float) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                (x0 + (x1 - x0) * fraction) * density, (y0 + (y1 - y0) * fraction) * density, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { view.dispatchTouchEvent(event) } finally { event.recycle() }
        }
        send(MotionEvent.ACTION_DOWN, 0f)
        repeat(18) { frames(1); send(MotionEvent.ACTION_MOVE, (it + 1) / 18f) }
        frames(4)
        send(MotionEvent.ACTION_UP, 1f)
    }
}
