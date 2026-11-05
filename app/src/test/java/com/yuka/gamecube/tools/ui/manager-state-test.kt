package com.yuka.gamecube.tools.ui

import com.yuka.gamecube.tools.core.AppRule
import com.yuka.gamecube.tools.core.ConfigSnapshot
import com.yuka.gamecube.tools.diagnostics.boundedLogTail
import com.yuka.gamecube.tools.diagnostics.AppDiagnostics
import android.content.ContextWrapper
import java.nio.file.Files
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import org.junit.Assert.*
import org.junit.Test

class ManagerStateTest {
    @Test fun cacheLoadsOnceAcrossConsumersAndRefreshesOnlyWhenRequested() = runBlocking {
        val cache = AppListCache<Int>()
        var loads = 0
        suspend fun load(): List<Int> { delay(5); return listOf(++loads) }
        val first = (1..5).map { async { cache.get(loader = ::load) } }.awaitAll()
        assertTrue(first.all { it == listOf(1) })
        assertEquals(listOf(1), cache.get(loader = ::load))
        assertEquals(listOf(2), cache.get(refresh = true, loader = ::load))
        assertEquals(2, loads)
    }
    @Test fun emptyCacheRetriesAndFailedRefreshPreservesPreviousData() = runBlocking {
        val cache = AppListCache<Int>()
        assertEquals(emptyList<Int>(), cache.get { emptyList() })
        assertEquals(listOf(1), cache.get { listOf(1) })
        try { cache.get(refresh = true) { error("package scan failed") }; fail() }
        catch (_: IllegalStateException) { }
        assertEquals(listOf(1), cache.get { fail("must keep old cache"); emptyList() })
    }
    @Test fun cancelledRefreshDoesNotPublishPartialResultsAndNextRefreshCanComplete() = runBlocking {
        val cache = AppListCache<Int>()
        cache.get { listOf(1) }
        val entered = CompletableDeferred<Unit>()
        val task = launch { cache.get(refresh = true) { entered.complete(Unit); delay(100_000); listOf(2) } }
        entered.await()
        task.cancelAndJoin()
        assertEquals(listOf(1), cache.get { error("old cache must be retained") })
        assertEquals(listOf(3), cache.get(refresh = true) { listOf(3) })
    }
    @Test fun rulesAlwaysSortFirstEvenWhenDirectionIsReversedAndMasterIsOff() {
        val enabled = InstalledApp("com.example.z", "Z", false)
        val other = InstalledApp("com.example.a", "A", false)
        val config = ConfigSnapshot.defaults().withApp(enabled.packageName, AppRule.defaults().withEnabled(true))
        AppSort.entries.forEach { sort -> listOf(false, true).forEach { reverse ->
            assertEquals(enabled, sortInstalledApps(listOf(other, enabled), config, sort, reverse).first())
        } }
    }
    @Test fun secondaryUsersAreAlwaysExcluded() {
        val primary = InstalledApp("com.example.game", "Game", false)
        val clone = primary.copy(userId = 999)
        val system = InstalledApp("android", "Android", true)
        assertNotEquals(primary.key, clone.key)
        assertEquals(listOf(primary), filterInstalledApps(listOf(primary, clone, system), "", false))
        assertEquals(listOf(primary, system), filterInstalledApps(listOf(primary, clone, system), "", true))
        val cfg = ConfigSnapshot.defaults().withApp(primary.packageName, AppRule.defaults().withEnabled(true))
        assertEquals(primary, sortInstalledApps(listOf(clone, primary), cfg, AppSort.NAME).first())
    }
    @Test fun sortModesUsePackageAndTimestampsRatherThanInputOrder() {
        val a = InstalledApp("com.z", "A", false, installedAt = 3, updatedAt = 1)
        val b = InstalledApp("com.a", "Z", false, installedAt = 1, updatedAt = 4)
        val cfg = ConfigSnapshot.defaults()
        assertEquals(b, sortInstalledApps(listOf(a,b), cfg, AppSort.PACKAGE).first())
        assertEquals(a, sortInstalledApps(listOf(b,a), cfg, AppSort.INSTALLED).first())
        assertEquals(b, sortInstalledApps(listOf(a,b), cfg, AppSort.UPDATED).first())
    }
    @Test fun languageTagsAndLogTailPreserveContentAndBounds() {
        assertEquals(UiLanguage.ENGLISH, UiLanguage.fromStored("en"))
        assertEquals(UiLanguage.VIETNAMESE, UiLanguage.fromStored("vi"))
        assertEquals("English", UiLanguage.ENGLISH.text("Việt", "English"))
        assertEquals("Việt", UiLanguage.VIETNAMESE.text("Việt", "English"))
        assertEquals("small\n", boundedLogTail("small\n", 20))
        assertEquals("new\n", boundedLogTail("old line\nnew\n", 8))
        assertEquals(128_000, boundedLogTail("a".repeat(300_000)).length)
    }
    @Test fun deliveryStatesDoNotTreatSavedOrFailedAsSent() {
        assertEquals(DeliveryState.SENT, deliveryState("Configuration sent #9"))
        assertEquals(DeliveryState.SYNCING, deliveryState("Saving and syncing..."))
        assertEquals(DeliveryState.WAITING, deliveryState("Saved locally; waiting for LSPosed"))
        assertEquals(DeliveryState.ERROR, deliveryState("Sync failed; configuration saved locally: disconnected"))
        assertEquals(DeliveryState.ERROR, deliveryState("Configuration error; using disabled defaults"))
    }
    @Test fun audioChoicesReferToRealDomesticPresetsAndCanBeSelected() {
        val options = audioProfiles()
        assertEquals(14, options.size)
        assertEquals(options.size, options.map { it.packageName }.distinct().size)
        assertTrue(options.all { validDonorPackage(it.packageName) })
        assertEquals("com.miHoYo.Yuanshen", options.first { it.title == "Genshin Impact" }.packageName)
        assertEquals("Preset 4", options.first { it.title == "Honkai: Star Rail" }.details)
    }
    @Test fun clearingPrivateLogRemovesSavedContentAndLoggingCanResume() = runBlocking {
        val folder = Files.createTempDirectory("manager-log-clear").toFile()
        val file = java.io.File(folder, "manager-threadtime.log")
        file.writeText("previous session\n")
        try {
            AppDiagnostics.start(object : ContextWrapper(null) { override fun getFilesDir() = folder })
            AppDiagnostics.log("test", "before-clear-marker")
            withTimeout(3000) { while (!file.readText().contains("before-clear-marker")) delay(5) }
            AppDiagnostics.clear()
            withTimeout(3000) { while (AppDiagnostics.lines.value.isNotEmpty() || file.readText().isNotEmpty()) delay(5) }
            AppDiagnostics.log("test", "after-clear-marker")
            withTimeout(3000) { while (!file.readText().contains("after-clear-marker")) delay(5) }
            assertFalse(file.readText().contains("before-clear-marker"))
            assertTrue(AppDiagnostics.lines.value.contains("after-clear-marker"))
        } finally { folder.deleteRecursively() }
    }
}
