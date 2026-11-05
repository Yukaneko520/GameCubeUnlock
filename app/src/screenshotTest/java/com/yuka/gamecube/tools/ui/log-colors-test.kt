package com.yuka.gamecube.tools.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import app.cash.paparazzi.Paparazzi
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.theme.MiuixTheme

class LogColorsTest {
    @get:Rule val paparazzi = Paparazzi(theme = "android:Theme.Material.NoActionBar")

    @Test fun prioritiesAreColoredWithoutChangingLogText() {
        val source = "--------- beginning of main\n" + "VDIWEF".map {
            "10-23 23:08:53.442 11935 12480 $it GameCubeUnlock: example $it\n"
        }.joinToString("")
        var completed = false
        paparazzi.gif(start = 0, end = 1_000, fps = 10) {
            MiuixTheme {
                val view = LocalView.current
                LogBufferView(source)
                LaunchedEffect(Unit) {
                    repeat(3) { withFrameNanos { } }
                    val rendered = (view as ViewRootForTest).semanticsOwner
                        .getAllSemanticsNodes(mergingEnabled = true).flatMap {
                            it.config.getOrNull(SemanticsProperties.Text).orEmpty()
                        }
                    source.lineSequence().filter { it.isNotEmpty() }.forEach { line ->
                        check(rendered.any { it.text == line }) { "Log row missing: $line" }
                    }
                    check(rendered.flatMap { it.spanStyles }.map { it.item.color }.distinct().size >= 6) {
                        "Log priorities are still monochrome"
                    }
                    completed = true
                }
            }
        }
        assertTrue(completed)
    }
}
