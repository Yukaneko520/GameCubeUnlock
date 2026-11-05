package com.yuka.gamecube.tools.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.yuka.gamecube.tools.core.ConfigSnapshot
import com.yuka.gamecube.tools.diagnostics.AppDiagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.Collator

internal data class InstalledApp(val packageName: String, val label: String, val system: Boolean,
    val userId: Int = 0, val uid: Int = 0, val installedAt: Long = 0, val updatedAt: Long = 0) {
    val key get() = "$userId/$packageName"
}
internal enum class AppSort { NAME, PACKAGE, INSTALLED, UPDATED }

/** Kept by ManagerState; serializes concurrent first-load/refresh requests. */
internal class AppListCache<T> {
    private val mutex = Mutex()
    private var items = emptyList<T>()
    suspend fun get(refresh: Boolean = false, loader: suspend () -> List<T>): List<T> = mutex.withLock {
        if (refresh || items.isEmpty()) items = loader().toList()
        items
    }
}

internal class InstalledApps(private val context: Context) {
    private val icons = LruCache<String, ImageBitmap>(96)
    private val cache = AppListCache<InstalledApp>()
    suspend fun load(refresh: Boolean = false): List<InstalledApp> = cache.get(refresh) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val packages = pm.getInstalledPackages(0)
            val apps = packages.mapNotNull { p -> p.applicationInfo?.let { info ->
                InstalledApp(p.packageName, runCatching { pm.getApplicationLabel(info).toString() }
                    .getOrDefault(p.packageName), info.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    info.uid / 100000, info.uid, p.firstInstallTime, p.lastUpdateTime)
            } }.associateBy { it.key }.toMutableMap()
            AppDiagnostics.log("apps", "loaded=${apps.size} refresh=$refresh")
            icons.evictAll()
            apps.values.filter { it.userId == 0 }
        }
    }
    suspend fun icon(app: InstalledApp): ImageBitmap? = withContext(Dispatchers.IO) {
        icons.get(app.key) ?: runCatching {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val drawable = pm.getApplicationIcon(pm.getApplicationInfo(app.packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES))
            val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, 96, 96)
            drawable.draw(Canvas(bitmap))
            bitmap.asImageBitmap().also { icons.put(app.key, it) }
        }.getOrNull()
    }
}

@Composable
internal fun rememberAppIcon(repository: InstalledApps, app: InstalledApp) =
    produceState<ImageBitmap?>(null, repository, app.key) { value = repository.icon(app) }

internal fun filterInstalledApps(apps: List<InstalledApp>, query: String, includeSystem: Boolean): List<InstalledApp> {
    val text = query.trim()
    return apps.filter { app -> (includeSystem || !app.system) && (app.userId == 0) &&
        (text.isEmpty() || app.label.contains(text, true) || app.packageName.contains(text, true)) }
}
internal fun sortInstalledApps(apps: List<InstalledApp>, config: ConfigSnapshot,
    sort: AppSort, reversed: Boolean = false): List<InstalledApp> {
    val collator = Collator.getInstance()
    val enabledPackages = config.apps.filterValues { it.enabled }.keys
    val comparator = Comparator<InstalledApp> { a, b -> when (sort) {
        AppSort.NAME -> collator.compare(a.label, b.label)
        AppSort.PACKAGE -> a.packageName.compareTo(b.packageName)
        AppSort.INSTALLED -> b.installedAt.compareTo(a.installedAt)
        AppSort.UPDATED -> b.updatedAt.compareTo(a.updatedAt)
    }.let { if (reversed) -it else it } }
    return apps.sortedWith(compareByDescending<InstalledApp> {
        it.userId == 0 && it.packageName in enabledPackages
    }.then(comparator).thenBy { it.key })
}
