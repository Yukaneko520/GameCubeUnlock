package com.yuka.gamecube.tools.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.yuka.gamecube.tools.core.AppRule
import com.yuka.gamecube.tools.core.ConfigSnapshot
import com.yuka.gamecube.tools.core.FrameworkState
import com.yuka.gamecube.tools.core.GraphicsTuning
import com.yuka.gamecube.tools.core.VisualTuning
import com.yuka.gamecube.tools.diagnostics.threadtimeEntry
import com.yuka.gamecube.tools.diagnostics.logBufferSnapshot
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/** Controlled UI inputs only; screenshots are host renders, not device/backend verification. */
class ManagerScreenshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.NoActionBar", showSystemUi = true)
    private val apps = listOf(InstalledApp("com.xd.rotaeno.googleplay", "Rotaeno", false),
        InstalledApp("com.HoYoverse.hkrpgoversea", "Honkai: Star Rail", false),
        InstalledApp("com.miHoYo.Yuanshen", "Genshin Impact", false))
    private val config = ConfigSnapshot(true, 1, true, 1,
        apps.associate { it.packageName to AppRule.defaults().withEnabled(true) })
    // Layoutlib publishes zero insets despite drawing the three-button bar. These are explicit UI test inputs.
    private val navigationInsets get() = WindowInsets(bottom = 48.dp)

    private fun snapshot(content: @Composable () -> Unit) {
        // Advance frames so MIUIX title/icon transitions are included, not just their first frame.
        paparazzi.gif(start = 0L, end = 1_000L, fps = 10) {
            // Layoutlib has zero status insets; reserve the displayed status bar explicitly.
            Box(Modifier.padding(top = 24.dp)) { content() }
        }
    }

    @Composable private fun PreviewFrame(page: Int, title: String, detail: Boolean = false,
        navigationInsets: WindowInsets = WindowInsets(0),
        actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
            if (page == 0 && !detail) HomeRefreshAction({})
        },
        content: @Composable () -> Unit) {
        ManagerFrame(page, title, detail, navigationInsets = navigationInsets, actions = actions) { padding, scroll ->
            Box(Modifier.fillMaxSize().padding(padding).nestedScroll(scroll.nestedScrollConnection)) { content() }
        }
    }

    @Composable private fun Theme(language: UiLanguage = UiLanguage.VIETNAMESE, dark: Boolean = true,
        content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalLanguage provides language) {
            MaterialTheme(colorScheme = if (dark) androidx.compose.material3.darkColorScheme(background = Color.Black,
                surface = Color(0xFF242424), primary = Color(0xFF89B4FF), onBackground = Color(0xFFEDEDED))
                else androidx.compose.material3.lightColorScheme(background = Color(0xFFF5F5F5), surface = Color.White)) {
                MiuixTheme(controller = ThemeController(colorSchemeMode = if (dark)
                    top.yukonga.miuix.kmp.theme.ColorSchemeMode.Dark else top.yukonga.miuix.kmp.theme.ColorSchemeMode.Light,
                    lightColors = top.yukonga.miuix.kmp.theme.lightColorScheme(background = Color(0xFFF5F5F5), surface = Color.White),
                    darkColors = top.yukonga.miuix.kmp.theme.darkColorScheme(background = Color.Black,
                        surface = Color(0xFF242424), surfaceContainer = Color(0xFF242424)))) { content() }
            }
        }
    }
    @Test fun visualIrisVietnamese() { snapshot {
        Theme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
            VisualTuningEditor(VisualTuning.defaults().withEnabled(true), true) {}
        } }
    } }
    @Test fun visualHawkeyeEnglish() { snapshot {
        Theme(UiLanguage.ENGLISH, dark = false) { Column(Modifier.fillMaxSize().padding(12.dp)) {
            VisualTuningEditor(VisualTuning(true, 1, -1, 0, 13, 0, 128, 50, 0, 271), true) {}
        } }
    } }
    @Test fun homeVietnamese() { snapshot {
        Theme { PreviewFrame(0, "GameCube", navigationInsets = navigationInsets) {
            DashboardContent(config, "Configuration sent #1", true,
                DeviceSummary("V2352GA · PD2352", "16 (API 36)", "14.0.10.00_de (1401000)", "25.0.0.06 (250006)"),
                framework = healthyFramework(), save = {})
        } }
    } }
    @Test fun homeOs5English() { snapshot {
        Theme(UiLanguage.ENGLISH, dark = false) { PreviewFrame(0, "GameCube", navigationInsets = navigationInsets) {
            DashboardContent(config, "Configuration sent #1", true,
                DeviceSummary("V2352A · PD2352", "15 (API 35)", "13.0.05.02 (1300502)", "25.0.0.03 (250003)"),
                framework = healthyFramework(), save = {})
        } }
    } }
    @Test fun settingsEnglish() { snapshot {
        Theme(UiLanguage.ENGLISH) { PreviewFrame(2, "Settings", navigationInsets = navigationInsets) {
            val developer = remember { DeveloperSettings(hostDeveloperPreferences()).apply { unlock() } }
            SettingsScreen("Đã gửi cấu hình", {}, Appearance.DARK, {}, UiLanguage.ENGLISH, {}, {}, developer)
        } }
    } }
    @Composable private fun AppListPreview(query: String = "", loading: Boolean = false,
        failed: Boolean = false, collapsed: Boolean = false) {
        ManagerFrame(1, "Apps", navigationInsets = navigationInsets, searchQuery = query, actions = {
            AppListActions(AppSort.NAME, false, false, { _, _ -> }, {})
        }) { padding, scroll ->
            LaunchedEffect(collapsed) {
                if (collapsed) {
                    withFrameNanos { }
                    check(scroll.nestedScrollConnection.onPreScroll(Offset(0f, -400f), NestedScrollSource.UserInput).y < 0f)
                }
            }
            InstalledAppsContent(if (failed) emptyList() else apps, config, InstalledApps(paparazzi.context),
                loading, failed, padding, scroll, {}, {})
        }
    }
    @Test fun applicationList() { snapshot { Theme { AppListPreview() } } }
    @Test fun applicationSearchWithClearAction() { snapshot { Theme(UiLanguage.ENGLISH) {
        AppListPreview(query = "Rotaeno")
    } } }
    @Test fun applicationListRefreshing() { snapshot { Theme(UiLanguage.ENGLISH) {
        AppListPreview(loading = true)
    } } }
    @Test fun applicationListFailedScan() { snapshot { Theme(UiLanguage.ENGLISH) {
        AppListPreview(failed = true)
    } } }
    @Test fun applicationListCollapsedHeader() { snapshot { Theme(UiLanguage.ENGLISH) {
        AppListPreview(collapsed = true)
    } } }
    @Test fun ruleEditorEnglish() { snapshot {
        Theme(UiLanguage.ENGLISH) { PreviewFrame(1, "Rotaeno", detail = true, navigationInsets = navigationInsets,
            actions = { SyncStatus("Đã gửi cấu hình", {}) }) {
            AppRuleScreen(apps[0], config.rule(apps[0].packageName), true) {}
        } }
    } }
    @Test fun logViewerEnglish() { snapshot {
        Theme(UiLanguage.ENGLISH) { PreviewFrame(2, "Logs", detail = true, navigationInsets = navigationInsets,
            actions = { top.yukonga.miuix.kmp.basic.IconButton(onClick = {}) {
                top.yukonga.miuix.kmp.basic.Icon(managerIcon("delete"), "Clear captured logs")
            } }) { LogScreen() } }
    } }
    @Test fun referenceProfilesVietnamese() { snapshot {
        Theme { PreviewFrame(1, "Rotaeno", detail = true, navigationInsets = navigationInsets,
            actions = { SyncStatus("Đã gửi cấu hình", {}) }) {
            AppRuleScreen(apps[0], config.rule(apps[0].packageName), true,
                scrollState = rememberScrollState(Int.MAX_VALUE)) {}
        } }
    } }
    @Test fun pressedPreferenceAndSelectorSpacing() { snapshot {
        Theme { PreviewFrame(0, "GameCube", navigationInsets = navigationInsets) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SectionCard(inset = false) {
                    PreferenceRow("Bật mô-đun", "Áp dụng các quy tắc đã lưu.", holdDownState = true, onClick = {}) {
                        top.yukonga.miuix.kmp.basic.Switch(true, null)
                    }
                }
                GlobalControls(config, true, {})
            }
        } }
    } }
    private fun tunedRule() = AppRule.defaults().withEnabled(true).withFeature("frame", true).withFeature("sr", true)
        .withFeature("force144", true).withTuning(GraphicsTuning(true, GraphicsTuning.parseTuples("60/48/144"),
            GraphicsTuning.parseTuples("60/30/60"), true, 4, 1260, 0, 2.5, -40.0, 20.0))
    @Test fun customFrameAndSrVietnamese() { snapshot {
        Theme { PreviewFrame(1, "Rotaeno", detail = true, navigationInsets = navigationInsets) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
                GraphicsTuningEditor(apps[0].packageName, tunedRule().tuning, true, true,
                    false, "", {}, {}, showNativeSrControls = true) {}
            }
        } }
    } }
    @Test fun customSrEnglish() { snapshot {
        Theme(UiLanguage.ENGLISH) { PreviewFrame(1, "Honkai: Star Rail", detail = true, navigationInsets = navigationInsets,
            actions = { SyncStatus("Đã gửi cấu hình", {}) }) {
            AppRuleScreen(apps[1], tunedRule(), true, scrollState = rememberScrollState(Int.MAX_VALUE),
                showNativeSrControls = true) {}
        } }
    } }
    @Test fun detailHeaderCollapsesWithContent() { snapshot {
        Theme(UiLanguage.ENGLISH) { PreviewFrame(1, "Honkai: Star Rail", detail = true,
            navigationInsets = navigationInsets, actions = { SyncStatus("Đã gửi cấu hình", {}) }) {
            val dispatcher = remember { NestedScrollDispatcher() }
            val connection = remember { object : NestedScrollConnection {} }
            LaunchedEffect(dispatcher) {
                withFrameNanos { }
                check(dispatcher.dispatchPreScroll(Offset(0f, -400f), NestedScrollSource.UserInput).y < 0f)
            }
            Box(Modifier.fillMaxSize().nestedScroll(connection, dispatcher)) {
                AppRuleScreen(apps[1], tunedRule(), true) {}
            }
        } }
    } }
    @Test fun threadtimeBuffersEnglish() { snapshot {
        Theme(UiLanguage.ENGLISH) { PreviewFrame(2, "Logs", detail = true, navigationInsets = navigationInsets) {
            val manager = threadtimeEntry(0, 12000, 12014, 'I', "GC/config", "revision=7 identity=compatible", ZoneId.of("UTC")) +
                "VDWEF".mapIndexed { index, priority -> threadtimeEntry(100L + index, 12000, 12014,
                    priority, "GC/test", "Example priority $priority", ZoneId.of("UTC")) }.joinToString("")
            val android = "--------- beginning of main\n01-01 00:00:00.200  2204  2453 I GameCubeUnlock: example entry\n" +
                "--------- beginning of system\n01-01 00:00:00.300  2204  2453 I GameCubeUnlock: request released\n" +
                "--------- beginning of crash\n"
            LogBufferView(logBufferSnapshot(manager, android), Modifier.fillMaxSize().padding(12.dp))
        } }
    } }
    @Test fun coloredLogsLight() { snapshot {
        Theme(UiLanguage.ENGLISH, false) { PreviewFrame(2, "Logs", detail = true, navigationInsets = navigationInsets) {
            LogBufferView("--------- beginning of main\n" + "VDIWEF".mapIndexed { index, priority ->
                threadtimeEntry(index.toLong(), 12000, 12014, priority, "GC/test", "Example priority $priority", ZoneId.of("UTC"))
            }.joinToString(""), Modifier.fillMaxSize().padding(12.dp))
        } }
    } }
    @Test fun developerSettingsEnglish() { snapshot {
        Theme(UiLanguage.ENGLISH) { PreviewFrame(2, "Advanced mode", detail = true, navigationInsets = navigationInsets) {
            val developer = remember { DeveloperSettings(hostDeveloperPreferences()).apply {
                unlock(); showThermalControls(true)
            } }
            DeveloperScreen(developer)
        } }
    } }
    @Test fun homeEnglishLight() {
        paparazzi.unsafeUpdateConfig(theme = "android:Theme.Material.Light.NoActionBar")
        snapshot {
        Theme(UiLanguage.ENGLISH, false) { PreviewFrame(0, "GameCube", navigationInsets = navigationInsets) {
            DashboardContent(config, "Configuration sent #1", true,
                DeviceSummary("V2352GA · PD2352", "16 (API 36)", "14.0.10.00_de (1401000)"),
                framework = healthyFramework(), save = {})
        } }
    } }

    private fun healthyFramework() = FrameworkState(true, setOf("com.vivo.gamecube", "com.vivo.gamewatch", "system"),
        setOf("com.vivo.gamecube:assistantui", "system_server"), "", "")

    @Test fun homeFrameworkDisconnected() { snapshot {
        Theme(UiLanguage.ENGLISH) { PreviewFrame(0, "GameCube", navigationInsets = navigationInsets) {
            DashboardContent(config, "Saved locally; waiting for LSPosed", true,
                DeviceSummary("V2352GA · PD2352", "16 (API 36)", "14.0.10.00_de"), save = {})
        } }
    } }
    @Test fun homeMissingScopeLight() { snapshot {
        Theme(UiLanguage.ENGLISH, false) { PreviewFrame(0, "GameCube", navigationInsets = navigationInsets) {
            DashboardContent(config, "Configuration sent #1", true,
                DeviceSummary("V2352GA · PD2352", "16 (API 36)", "14.0.10.00_de"),
                framework = FrameworkState(true, setOf("com.vivo.gamecube", "com.vivo.gamewatch"), emptySet(), "", ""), save = {})
        } }
    } }
    @Test fun homeBeforeOpeningGame() { snapshot {
        Theme(UiLanguage.ENGLISH) { PreviewFrame(0, "GameCube", navigationInsets = navigationInsets) {
            DashboardContent(config, "Configuration sent #1", true,
                DeviceSummary("V2352GA · PD2352", "16 (API 36)", "14.0.10.00_de"),
                framework = FrameworkState(true, healthyFramework().scopes(), emptySet(), "", ""), save = {})
        } }
    } }
    @Test fun homeOptionalTargetQueryUnavailableLight() { snapshot {
        Theme(UiLanguage.ENGLISH, false) { PreviewFrame(0, "GameCube", navigationInsets = navigationInsets) {
            DashboardContent(config, "Configuration sent #1", true,
                DeviceSummary("V2352GA · PD2352", "16 (API 36)", "14.0.10.00_de"),
                framework = FrameworkState(true, healthyFramework().scopes(), emptySet(), "", "unavailable"), save = {})
        } }
    } }
    @Test fun homeScopeQueryWarning() { snapshot {
        Theme(UiLanguage.ENGLISH) { PreviewFrame(0, "GameCube", navigationInsets = navigationInsets) {
            DashboardContent(config, "Configuration sent #1", true,
                DeviceSummary("V2352GA · PD2352", "16 (API 36)", "14.0.10.00_de"),
                framework = FrameworkState(true, emptySet(), emptySet(), "unavailable", ""), save = {})
        } }
    } }
    @Test fun homeSyncPendingWarningLight() { snapshot {
        Theme(UiLanguage.ENGLISH, false) { PreviewFrame(0, "GameCube", navigationInsets = navigationInsets) {
            DashboardContent(config, "Saving and syncing...", true,
                DeviceSummary("V2352GA · PD2352", "16 (API 36)", "14.0.10.00_de"),
                framework = healthyFramework(), save = {})
        } }
    } }
}
