// SPDX-License-Identifier: GPL-3.0-only
// App row adapted from SukiSU-Ultra SuperUserMaterial SimpleAppItem (v4.2.1).
package com.yuka.gamecube.tools.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.lazy.rememberLazyListState
import top.yukonga.miuix.kmp.utils.overScrollVertical
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.yuka.gamecube.tools.core.ConfigSnapshot
import com.yuka.gamecube.tools.diagnostics.AppDiagnostics
import kotlinx.coroutines.CancellationException
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun InstalledAppsScreen(config: ConfigSnapshot, state: ManagerState,
    contentPadding: PaddingValues, scrollBehavior: ScrollBehavior, onSelect: (InstalledApp) -> Unit) {
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(state.refreshToken) {
        val requested = state.refreshToken
        loading = true; failed = false
        try {
            apps = state.apps.load(refresh = state.loadedRefreshToken != requested)
            state.loadedRefreshToken = requested
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            AppDiagnostics.error("apps_load", error)
            failed = true
        } finally { loading = false }
    }
    val language = LocalLanguage.current
    val visible = remember(apps, state.query, state.showSystem, state.sort, state.reversed, config, language) {
        sortInstalledApps(filterInstalledApps(apps.orEmpty(), state.query, state.showSystem),
            config, state.sort, state.reversed)
    }
    InstalledAppsContent(visible, config, state.apps, loading, failed, contentPadding, scrollBehavior,
        { if (!loading) { loading = true; state.refreshToken++ } }, onSelect)
}

@Composable
internal fun AppSearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    TextField(value = query, onValueChange = onQuery, label = tr("Tìm ứng dụng", "Search apps"),
        useLabelAsPlaceholder = true, singleLine = true, cornerRadius = 32.dp,
        modifier = modifier.fillMaxWidth(),
        leadingIcon = { Icon(remember { managerIcon("search") }, null, Modifier.padding(start = 16.dp, end = 8.dp)) },
        trailingIcon = { if (query.isNotEmpty()) {
            IconButton(onClick = { onQuery("") }) {
                Icon(remember { managerIcon("close") }, tr("Xóa tìm kiếm", "Clear search"))
            }
        } })
}

@Composable
internal fun InstalledAppsContent(visible: List<InstalledApp>, config: ConfigSnapshot,
    repository: InstalledApps, loading: Boolean, failed: Boolean,
    contentPadding: PaddingValues, scrollBehavior: ScrollBehavior,
    retry: () -> Unit, onSelect: (InstalledApp) -> Unit,
    pullState: PullToRefreshState = rememberPullToRefreshState()) {
    val listState = rememberLazyListState()
    val direction = LocalLayoutDirection.current
    val refreshLabel = tr("Làm mới danh sách ứng dụng", "Refresh app list")
    val top = contentPadding.calculateTopPadding() + 6.dp
    PullToRefresh(isRefreshing = loading, pullToRefreshState = pullState, onRefresh = { if (!loading) retry() },
        contentPadding = PaddingValues(top = top),
        refreshTexts = listOf(tr("Kéo xuống để làm mới", "Pull to refresh"),
            tr("Thả để làm mới", "Release to refresh"), tr("Đang làm mới…", "Refreshing…"),
            tr("Đã làm mới", "Refresh complete")),
        modifier = Modifier.fillMaxSize().semantics {
            customActions = listOf(CustomAccessibilityAction(refreshLabel) {
                if (!loading) { retry(); true } else false
            })
        }) {
        // App-bar expansion consumes downward scroll before refresh sees the remainder, as in KernelSU MIUIX.
        LazyColumn(state = listState,
            modifier = Modifier.fillMaxSize().overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            overscrollEffect = null,
            contentPadding = PaddingValues(top = top, bottom = contentPadding.calculateBottomPadding() + 12.dp,
                start = contentPadding.calculateStartPadding(direction) + 12.dp,
                end = contentPadding.calculateEndPadding(direction) + 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (failed) item(key = "load-error") { SectionCard {
                Text(tr("Không đọc được danh sách ứng dụng.", "Unable to load installed apps."))
                TextButton(tr("Thử lại", "Retry"), retry, enabled = !loading)
            } }
            if (visible.isEmpty() && !loading && !failed) item(key = "empty") { SectionCard {
                Text(tr("Không tìm thấy ứng dụng", "No apps found"), fontWeight = FontWeight.Medium)
                Caption(tr("Thử từ khóa khác hoặc bật hiển thị ứng dụng hệ thống.",
                    "Try another search or show system apps in the menu."))
            } }
            items(visible, key = { it.key }, contentType = { "app" }) { app ->
                ManagerAppRow(app, app.userId == 0 && config.rule(app.packageName).enabled, repository, onSelect)
            }
        }
    }
}

@Composable
internal fun ManagerAppRow(app: InstalledApp, enabled: Boolean, repository: InstalledApps,
    onSelect: (InstalledApp) -> Unit) {
    val icon by rememberAppIcon(repository, app)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MiuixTheme.colorScheme.surface)
        .clickable(onClickLabel = tr("Cấu hình ứng dụng", "Configure app")) { onSelect(app) }
        .heightIn(min = 76.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
            icon?.let { Image(it, null, Modifier.fillMaxSize()) } ?: Text(app.label.take(1), fontSize = 24.sp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(app.label, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(app.packageName, fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (enabled) Text(tr("BẬT", "ON"), fontSize = 9.sp, color = MiuixTheme.colorScheme.primary,
            modifier = Modifier.background(MiuixTheme.colorScheme.primary.copy(alpha = .12f), RoundedCornerShape(8.dp))
                .padding(horizontal = 5.dp, vertical = 2.dp))
        Chevron()
    }
}
