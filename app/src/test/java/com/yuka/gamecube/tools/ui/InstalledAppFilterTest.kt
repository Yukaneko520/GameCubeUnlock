package com.yuka.gamecube.tools.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstalledAppFilterTest {
    @Test fun persistedAppearanceRestoresModesAndFallsBackToSystem() {
        Appearance.entries.forEach { assertEquals(it, Appearance.fromStored(it.name)) }
        assertEquals(Appearance.SYSTEM, Appearance.fromStored(null))
        assertEquals(Appearance.SYSTEM, Appearance.fromStored("unknown"))
    }

    private val personal = InstalledApp("com.example.game", "My Game", false)
    private val system = InstalledApp("com.vivo.gamecube", "GameCube", true)
    private val apps = listOf(personal, system)

    @Test fun searchMatchesLabelAndPackageIgnoringCaseAndWhitespace() {
        assertEquals(listOf(personal), filterInstalledApps(apps, "  MY GAME  ", true))
        assertEquals(listOf(system), filterInstalledApps(apps, "COM.VIVO", true))
        assertEquals(apps, filterInstalledApps(apps, "", true))
    }

    @Test fun systemFilterAppliesEvenWhenSearchMatches() {
        assertEquals(emptyList<InstalledApp>(), filterInstalledApps(apps, "GameCube", false))
        assertEquals(listOf(personal), filterInstalledApps(apps, "", false))
    }

    @Test fun optionalDonorsAcceptEmptyAndRejectIntermediateOrOverlongPackages() {
        assertTrue(validDonorPackage(""))
        assertTrue(validDonorPackage("com.example.game"))
        assertFalse(validDonorPackage("com."))
        assertFalse(validDonorPackage("com.example game"))
        assertFalse(validDonorPackage("com." + "a".repeat(252)))
    }
}
