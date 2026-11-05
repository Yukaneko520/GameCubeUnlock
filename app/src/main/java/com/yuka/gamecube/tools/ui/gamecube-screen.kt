package com.yuka.gamecube.tools.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.key
import androidx.compose.ui.unit.dp
import com.yuka.gamecube.tools.core.ConfigSnapshot
import com.yuka.gamecube.tools.core.FrameworkState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.*
import androidx.compose.foundation.layout.WindowInsets
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun GameCubeScreen(config: ConfigSnapshot, status: String, compatibility: String,
    supported: Boolean, refresh: () -> Unit, appearance: Appearance,
    changeAppearance: (Appearance) -> Unit, state: ManagerState,
    language: UiLanguage, changeLanguage: (UiLanguage) -> Unit,
    nativeScopes: Set<String>, scopeError: String, requestNativeScope: (String) -> Unit,
    framework: FrameworkState, save: (ConfigSnapshot) -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<InstalledApp?>(null) }
    var logs by rememberSaveable { mutableStateOf(false) }
    var developerPage by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.developer.unlocked) { if (!state.developer.unlocked) developerPage = false }
    var clearLogs by remember { mutableIntStateOf(0) }
    val pageStates = rememberSaveableStateHolder()
    val titles = listOf("GameCube", tr("Ứng dụng", "Apps"), tr("Cài đặt", "Settings"))
    val detail = selected != null || logs || developerPage
    val appList = page == 1 && !detail
    val goBack = { selected = null; logs = false; developerPage = false }
    BackHandler(detail, onBack = goBack)
    ManagerFrame(page, when {
        logs -> "Logs"
        developerPage -> tr("Chế độ nâng cao", "Advanced mode")
        else -> selected?.label ?: titles[page]
    }, detail, onBack = goBack, onTab = { page = it },
        searchQuery = state.query.takeIf { appList }, onSearch = { state.query = it },
        actions = { when {
            logs -> IconButton(onClick = { clearLogs++ }, minHeight = 48.dp, minWidth = 48.dp) {
                Icon(remember { managerIcon("delete") }, "Clear captured logs")
            }
            selected != null -> SyncStatus(status, refresh)
            page == 0 && !detail -> HomeRefreshAction(refresh)
            page == 1 -> AppListActions(state.sort, state.reversed,
                state.showSystem, state::sorting, state::filters)
        } }) { padding, scroll ->
        if (appList) pageStates.SaveableStateProvider(page) {
            InstalledAppsScreen(config, state, padding, scroll, onSelect = { selected = it })
        } else Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
            .nestedScroll(scroll.nestedScrollConnection)) {
        if (logs) LogScreen(clearLogs) else if (developerPage) DeveloperScreen(state.developer,
            config.demonstrationMode, supported, disableFlaGlobal = config.disableFlaGlobal,
            flaEditable = supported && com.yuka.gamecube.tools.compat.RomFeatures.available(android.os.Build.VERSION.SDK_INT, "fla"),
            changeFla = { save(config.withFlaDisabled(it)) },
            changeDemonstration = { save(config.withDemonstration(it)) }) else selected?.let { app ->
            AppRuleScreen(app, config.rule(app.packageName), supported && app.userId == 0,
                nativeScope = app.packageName in nativeScopes,
                scopeError = scopeError.takeIf { it.startsWith(app.packageName + " · ") }.orEmpty(),
                requestScope = { requestNativeScope(app.packageName) },
                showThermalControls = state.developer.showThermalControls,
                showBenchmarkControls = state.developer.showBenchmarkControls,
                showNativeSrControls = state.developer.showNativeSrControls,
                force144Global = config.force144Global,
                demonstrationMode = config.demonstrationMode,
                onSave = { rule -> save(config.withApp(app.packageName, rule)) })
        } ?: pageStates.SaveableStateProvider(page) { when (page) {
            0 -> DashboardScreen(config, status, compatibility, supported, framework, save)
            else -> SettingsScreen(status, refresh, appearance, changeAppearance,
                language, changeLanguage, { clearLogs = 0; logs = true }, state.developer,
                openDeveloper = { developerPage = true })
        } }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ManagerFrame(page: Int, title: String, detail: Boolean = false, onBack: () -> Unit = {},
    onTab: (Int) -> Unit = {}, navigationInsets: WindowInsets = WindowInsets.navigationBars,
    searchQuery: String? = null, onSearch: (String) -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues, ScrollBehavior) -> Unit) {
    val titles = listOf(tr("Trang chủ", "Home"), tr("Ứng dụng", "Apps"), tr("Cài đặt", "Settings"))
    val icons = remember { listOf(managerIcon("home"), managerIcon("apps"), managerIcon("settings")) }
    val barState = key(page, title) { rememberTopAppBarState() }
    val scrollBehavior = MiuixScrollBehavior(barState)
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.union(navigationInsets),
        topBar = {
            TopAppBar(title = title, scrollBehavior = scrollBehavior,
                // Equal button slots; align the chevron's visible edge with the large title.
                navigationIconPadding = if (detail) 10.dp else TopAppBarDefaults.NavigationIconPadding,
                actionIconPadding = if (detail) 10.dp else TopAppBarDefaults.ActionIconPadding,
                navigationIcon = {
                    if (detail) IconButton(onClick = onBack, minHeight = 48.dp, minWidth = 48.dp) {
                        Icon(remember { managerIcon("back") }, tr("Quay lại", "Back"),
                            tint = MiuixTheme.colorScheme.onSurface)
                    }
                }, actions = actions, color = MaterialTheme.colorScheme.background,
                bottomContent = {
                    if (searchQuery != null) AppSearchField(searchQuery, onSearch,
                        Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp,
                            top = 12.dp * (1f - scrollBehavior.state.collapsedFraction)))
                })
        },
        bottomBar = {
            if (!detail) NavigationBar(windowInsets = navigationInsets,
                containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                titles.forEachIndexed { index, title ->
                    NavigationBarItem(selected = page == index, onClick = { onTab(index) },
                        icon = { androidx.compose.material3.Icon(icons[index], null) }, label = { Text(title) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent,
                            selectedIconColor = MaterialTheme.colorScheme.onBackground,
                            selectedTextColor = MaterialTheme.colorScheme.onBackground,
                            unselectedIconColor = Color(0xFF858585), unselectedTextColor = Color(0xFF858585)))
                }
            }
        }
    ) { padding ->
        content(padding, scrollBehavior)
    }
}

internal fun managerIcon(kind: String) = ImageVector.Builder(kind, 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        when (kind) {
            "cube" -> {
                moveTo(12f, 2f); lineTo(21f, 7f); lineTo(21f, 17f); lineTo(12f, 22f)
                lineTo(3f, 17f); lineTo(3f, 7f); close()
                moveTo(3f, 7f); lineTo(12f, 12f); lineTo(21f, 7f)
                moveTo(12f, 12f); lineTo(12f, 22f)
            }
            "gamepad" -> {
                moveTo(7f, 7f); lineTo(17f, 7f)
                curveTo(20f, 7f, 21f, 11f, 22f, 17f)
                curveTo(22f, 20f, 19f, 20f, 16f, 16f); lineTo(8f, 16f)
                curveTo(5f, 20f, 2f, 20f, 2f, 17f); curveTo(3f, 11f, 4f, 7f, 7f, 7f); close()
                moveTo(7f, 10f); lineTo(7f, 14f); moveTo(5f, 12f); lineTo(9f, 12f)
                moveTo(16f, 11f); lineTo(16.1f, 11f); moveTo(19f, 13f); lineTo(19.1f, 13f)
            }
            "home" -> {
                moveTo(3f, 10f); lineTo(12f, 3f); lineTo(21f, 10f)
                moveTo(5f, 9f); lineTo(5f, 21f); lineTo(10f, 21f); lineTo(10f, 15f)
                lineTo(14f, 15f); lineTo(14f, 21f); lineTo(19f, 21f); lineTo(19f, 9f)
            }
            "apps" -> listOf(4f to 4f, 14f to 4f, 4f to 14f, 14f to 14f).forEach { (x, y) ->
                moveTo(x, y); lineTo(x + 6, y); lineTo(x + 6, y + 6); lineTo(x, y + 6); close()
            }
            "settings" -> {
                moveTo(4f, 6f); lineTo(7f, 6f); moveTo(11f, 6f); lineTo(20f, 6f)
                moveTo(4f, 12f); lineTo(13f, 12f); moveTo(17f, 12f); lineTo(20f, 12f)
                moveTo(4f, 18f); lineTo(7f, 18f); moveTo(11f, 18f); lineTo(20f, 18f)
                moveTo(7f, 4f); lineTo(11f, 4f); lineTo(11f, 8f); lineTo(7f, 8f); close()
                moveTo(13f, 10f); lineTo(17f, 10f); lineTo(17f, 14f); lineTo(13f, 14f); close()
                moveTo(7f, 16f); lineTo(11f, 16f); lineTo(11f, 20f); lineTo(7f, 20f); close()
            }
            "next" -> { moveTo(9f, 6f); lineTo(15f, 12f); lineTo(9f, 18f) }
            "back" -> { moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f) }
            "phone" -> { moveTo(6f, 2f); lineTo(18f, 2f); lineTo(18f, 22f); lineTo(6f, 22f); close(); moveTo(10f, 19f); lineTo(14f, 19f) }
            "android" -> { moveTo(5f, 10f); curveTo(5f, 3f, 19f, 3f, 19f, 10f); lineTo(5f, 10f); lineTo(5f, 19f); lineTo(19f, 19f); lineTo(19f, 10f); moveTo(7f, 5f); lineTo(5f, 2f); moveTo(17f, 5f); lineTo(19f, 2f); moveTo(8f, 19f); lineTo(8f, 22f); moveTo(16f, 19f); lineTo(16f, 22f) }
            "version" -> { moveTo(8f, 3f); lineTo(6f, 21f); moveTo(16f, 3f); lineTo(14f, 21f); moveTo(3f, 8f); lineTo(21f, 8f); moveTo(3f, 16f); lineTo(21f, 16f) }
            "sort" -> { moveTo(5f, 4f); lineTo(5f, 20f); lineTo(2f, 17f); moveTo(5f, 20f); lineTo(8f, 17f); moveTo(11f, 6f); lineTo(22f, 6f); moveTo(11f, 12f); lineTo(19f, 12f); moveTo(11f, 18f); lineTo(16f, 18f) }
            "more" -> { moveTo(5f, 12f); lineTo(5.1f, 12f); moveTo(12f, 12f); lineTo(12.1f, 12f); moveTo(19f, 12f); lineTo(19.1f, 12f) }
            "refresh" -> { moveTo(20f, 10f); curveTo(19f, 3f, 8f, 2f, 4f, 9f); moveTo(20f, 4f); lineTo(20f, 10f); lineTo(14f, 10f); moveTo(4f, 14f); curveTo(5f, 21f, 17f, 22f, 20f, 15f) }
            "delete" -> { moveTo(4f, 6f); lineTo(20f, 6f); moveTo(9f, 6f); lineTo(9f, 3f); lineTo(15f, 3f); lineTo(15f, 6f); moveTo(6f, 6f); lineTo(7f, 21f); lineTo(17f, 21f); lineTo(18f, 6f); moveTo(10f, 10f); lineTo(10f, 17f); moveTo(14f, 10f); lineTo(14f, 17f) }
            "cloud" -> { moveTo(6f, 18f); curveTo(-1f, 18f, -1f, 8f, 6f, 8f); curveTo(7f, 1f, 18f, 1f, 19f, 9f); curveTo(25f, 9f, 25f, 18f, 19f, 18f); moveTo(9f, 13f); lineTo(12f, 10f); lineTo(15f, 13f); moveTo(12f, 10f); lineTo(12f, 21f) }
            "search" -> { moveTo(16f, 16f); lineTo(22f, 22f); moveTo(18f, 10f); curveTo(18f, 20f, 2f, 20f, 2f, 10f); curveTo(2f, 0f, 18f, 0f, 18f, 10f) }
            "close" -> { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) }
            else -> {
                moveTo(5f, 5f); lineTo(19f, 5f); lineTo(19f, 19f); lineTo(5f, 19f); close()
                moveTo(8f, 12f); lineTo(11f, 15f); lineTo(16f, 9f)
            }
        }
    }
}.build()

