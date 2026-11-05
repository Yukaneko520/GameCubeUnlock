package com.yuka.gamecube.tools.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.Snapshot
import app.cash.paparazzi.SnapshotHandler
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Contrasting content exposes any transparent gap under the shared bottom navigation surface. */
class BottomBarInsetsTest {
    private var expected = 0
    private var frames = 0
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.NoActionBar", snapshotHandler = object : SnapshotHandler {
            override fun close() = Unit
            override fun newFrameHandler(snapshot: Snapshot, frameCount: Int, fps: Int) =
                object : SnapshotHandler.FrameHandler {
                    override fun close() = Unit
                    override fun handle(image: BufferedImage) {
                        val output = File("build/reports/bottom-bar-insets/${snapshot.name}.png")
                        output.parentFile?.mkdirs()
                        ImageIO.write(image, "png", output)
                        frames++
                        for (x in image.width / 10 until image.width * 9 / 10 step 10) {
                            assertEquals("Content leaked below navigation at x=$x", expected,
                                image.getRGB(x, image.height - 3))
                        }
                    }
                }
        })

    private fun render(name: String, bottom: Int, dark: Boolean) {
        val background = if (dark) Color.Black else Color.White
        expected = if (dark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        paparazzi.snapshot(name) {
            CompositionLocalProvider(LocalLanguage provides UiLanguage.ENGLISH) {
                MaterialTheme(colorScheme = if (dark)
                    androidx.compose.material3.darkColorScheme(background = background)
                else androidx.compose.material3.lightColorScheme(background = background)) {
                    MiuixTheme {
                        ManagerFrame(1, "Apps", navigationInsets = WindowInsets(bottom = bottom.dp)) { _, _ ->
                            Box(Modifier.fillMaxSize().background(Color.Magenta))
                        }
                    }
                }
            }
        }
        assertEquals("Expected one rendered frame", 1, frames)
    }

    @Test fun gestureDark() = render("gesture-dark", 24, true)
    @Test fun gestureLight() = render("gesture-light", 24, false)
    @Test fun threeButtonDark() = render("three-button-dark", 48, true)
    @Test fun threeButtonLight() = render("three-button-light", 48, false)
    @Test fun noInsetDark() = render("no-inset-dark", 0, true)
    @Test fun noInsetLight() = render("no-inset-light", 0, false)
}
