package com.yuka.gamecube.tools.ui

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.yuka.gamecube.tools.core.ConfigSnapshot
import com.yuka.gamecube.tools.core.AppRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState

/** Real host touch events exercise the production list, overscroll, refresh and toolbar together. */
class AppListGestureTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.NoActionBar")

    @Test fun headerReopensBeforeRefreshAndRefreshCanFinish() {
        var completed = false
        paparazzi.gif(start = 0L, end = 8_000L, fps = 30) {
            MaterialTheme { MiuixTheme {
                val view = LocalView.current
                val density = LocalDensity.current.density
                var loading by remember { mutableStateOf(false) }
                var refreshes by remember { mutableIntStateOf(0) }
                val pull = rememberPullToRefreshState()
                val apps = remember { (1..50).map { InstalledApp("test.app$it", "App $it", false) } }
                ManagerFrame(1, "Apps", searchQuery = "", navigationInsets = WindowInsets(0)) { padding, scroll ->
                    InstalledAppsContent(apps, ConfigSnapshot.defaults(), InstalledApps(paparazzi.context),
                        loading, false, padding, scroll, { refreshes++; loading = true }, {}, pull)
                    LaunchedEffect(Unit) {
                        frames(4)
                        drag(view, density, 560f, 280f)
                        frames(8)
                        check(scroll.state.collapsedFraction > .98f) { "upward touch did not collapse title" }
                        check(refreshes == 0) { "upward scroll refreshed" }
                        drag(view, density, 260f, 620f)
                        frames(8)
                        check(scroll.state.collapsedFraction < .02f) { "title stuck collapsed after downward touch" }
                        check(refreshes == 0) { "header expansion incorrectly refreshed" }
                        drag(view, density, 220f, 740f) {
                            check(pull.dragOffset > 0f) { "pull never reached the refresh indicator" }
                        }
                        // MIUIX invokes refresh after its release spring reaches the resting threshold.
                        repeat(60) { if (refreshes == 0) frames(1) }
                        check(refreshes == 1) { "full pull must refresh exactly once, got $refreshes" }
                        loading = false
                        frames(35)
                        drag(view, density, 560f, 280f)
                        frames(10)
                        check(scroll.state.collapsedFraction > .98f) { "refresh completion left scrolling blocked" }
                        check(refreshes == 1) { "refresh repeated after completion" }
                        drag(view, density, 280f, 330f)
                        frames(5)
                        check(scroll.state.collapsedFraction > .98f) { "mid-list drag expanded the title" }
                        check(refreshes == 1) { "mid-list drag triggered a refresh" }
                        completed = true
                    }
                }
            } }
        }
        assertTrue("touch scenario did not complete within the rendered frames", completed)
    }

    @Test fun childHeaderCollapsesReopensAndKeepsBackUsable() = childHeaderRoundTrip(false)
    @Test fun shortDeveloperPageHeaderAlsoCollapsesAndReopens() = childHeaderRoundTrip(true)

    private fun childHeaderRoundTrip(developer: Boolean) {
        var completed = false
        paparazzi.gif(start = 0L, end = 5_000L, fps = 30) {
            CompositionLocalProvider(LocalLanguage provides UiLanguage.ENGLISH) {
                MaterialTheme { MiuixTheme {
                    val view = LocalView.current
                    val density = LocalDensity.current.density
                    val syncLabel = connectionLabel("Configuration sent #1")
                    var backs by remember { mutableIntStateOf(0) }
                    ManagerFrame(1, if (developer) "Advanced mode" else "Rotaeno", detail = true, onBack = { backs++ },
                        navigationInsets = WindowInsets(0),
                        actions = { if (!developer) SyncStatus("Configuration sent #1", {}) }) { padding, scroll ->
                        Box(Modifier.fillMaxSize().padding(padding).nestedScroll(scroll.nestedScrollConnection)) {
                            if (developer) {
                                val settings = remember { DeveloperSettings(hostDeveloperPreferences()).apply { unlock() } }
                                DeveloperScreen(settings)
                            } else AppRuleScreen(InstalledApp("com.xd.rotaeno.googleplay", "Rotaeno", false),
                                    AppRule.defaults().withEnabled(true), true) {}
                        }
                        LaunchedEffect(Unit) {
                            frames(5)
                            check(scroll.state.heightOffsetLimit < -1f) { "child title is pinned; large title is missing" }
                            check(scroll.state.collapsedFraction < .02f) { "child title did not start expanded" }
                            fun back() = (view as ViewRootForTest).semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)
                                .first { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Back") == true }
                            check(back().boundsInRoot.height >= 48f * density - 1f)
                            if (!developer) {
                                val cloud = (view as ViewRootForTest).semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)
                                    .first { it.config.getOrNull(SemanticsProperties.StateDescription) == syncLabel }
                                val left = back().boundsInRoot
                                val right = cloud.boundsInRoot
                                check(kotlin.math.abs(left.center.x + right.center.x - view.width) <= 1f) {
                                    "Back/cloud button centers are not symmetric: $left / $right"
                                }
                                check(kotlin.math.abs(left.center.y - right.center.y) <= 1f)
                                check(kotlin.math.abs(left.width - right.width) <= 1f)
                            }
                            drag(view, density, 560f, 280f)
                            frames(8)
                            check(scroll.state.collapsedFraction > .98f) { "child title did not collapse" }
                            back()
                            drag(view, density, 240f, 700f)
                            frames(8)
                            check(scroll.state.collapsedFraction < .02f) { "child large title did not reopen" }
                            check(back().config[SemanticsActions.OnClick].action!!.invoke())
                            frames(2)
                            check(backs == 1) { "Back callback was lost after scrolling" }
                            completed = true
                        }
                    }
                } }
            }
        }
        assertTrue("child header gesture scenario did not complete", completed)
    }

    private suspend fun frames(count: Int) { repeat(count) { withFrameNanos { } } }

    private suspend fun drag(view: View, density: Float, from: Float, to: Float, beforeRelease: () -> Unit = {}) {
        val down = SystemClock.uptimeMillis()
        fun send(action: Int, y: Float) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                view.width / 2f, y * density, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { view.dispatchTouchEvent(event) } finally { event.recycle() }
        }
        send(MotionEvent.ACTION_DOWN, from)
        repeat(18) { step ->
            frames(1)
            send(MotionEvent.ACTION_MOVE, from + (to - from) * (step + 1) / 18f)
        }
        // Pause before release to distinguish dragging from a high-velocity fling.
        frames(4)
        beforeRelease()
        send(MotionEvent.ACTION_UP, to)
    }
}
