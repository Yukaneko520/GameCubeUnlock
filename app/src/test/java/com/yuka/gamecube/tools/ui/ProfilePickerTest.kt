package com.yuka.gamecube.tools.ui

import com.yuka.gamecube.tools.core.NativeProfileCatalog.FrameProfile
import com.yuka.gamecube.tools.core.NativeProfileCatalog.FrameValues
import com.yuka.gamecube.tools.core.NativeProfileCatalog.SrProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePickerTest {
    private val known = ProfileOption("com.example.game", "Game · vivo", "Hồ sơ gốc")

    @Test fun englishProfilesTranslateLabelsAndKeepOriginalNumericMappings() {
        assertEquals("Automatic", selectedProfile("", emptyList(), UiLanguage.ENGLISH).title)
        assertEquals("Custom", selectedProfile("com.unknown.game", emptyList(), UiLanguage.ENGLISH).title)
        val frame = FrameProfile("com.example.game", "Game", "official", 42,
            false, 0, emptyList(), listOf(FrameValues(144, 60, 120)))
        val text = frameDetails(frame, UiLanguage.ENGLISH)
        assertTrue(text.contains("Power saving: Game 144 / input 60 / output 120 FPS"))
        assertTrue(text.contains("SDK: No"))
        assertFalse(text.contains("output 144"))
    }

    @Test fun selectionDistinguishesAutoKnownAndCustomWithoutChangingThePackage() {
        assertEquals("", selectedProfile("", listOf(known)).packageName)
        assertEquals("Tự động", selectedProfile("", emptyList()).title)
        assertEquals(known, selectedProfile(known.packageName, listOf(known)))
        val custom = selectedProfile("com.example.unknown", listOf(known))
        assertEquals("com.example.unknown", custom.packageName)
        assertEquals("Tùy chỉnh", custom.title)
    }

    @Test fun editingKeepsUnknownAndInvalidInputButHonorsDisabledState() {
        assertEquals("", applyProfileSelection(known.packageName, "", true))
        assertEquals("com.custom.game", applyProfileSelection("", " com.custom.game ", true))
        assertEquals("com.", applyProfileSelection("", "com.", true))
        assertFalse(validDonorPackage("com."))
        assertEquals(known.packageName, applyProfileSelection(known.packageName, "", false))
    }

    @Test fun emptyFrameValuesAreAbsentAndCod144IsGameFpsNotOutput() {
        assertEquals("Không có", frameTuples(emptyList()))
        val cod = FrameProfile("com.activision.callofduty.shooter", "Call of Duty", "official", 42,
            false, 0, emptyList(), listOf(FrameValues(144, 60, 120)))
        val details = frameDetails(cod)
        assertTrue(details.contains("Boost: Không có"))
        assertTrue(details.contains("Tối ưu điện: Game 144 / hạ 60 / nội suy 120 FPS"))
        assertFalse(details.contains("nội suy 144"))
        assertTrue(details.contains("SDK: Không"))
    }

    @Test fun srStateOneShowsHighAndAbsentAssociationWithoutExtreme() {
        val sr = SrProfile("com.tencent.tmgp.cf", "CrossFire", "vivo", -1, 1, 0, -1, -1, -1, -1)
        val details = srDetails(sr)
        assertTrue(details.contains("HIGH (state 1)"))
        assertTrue(details.contains("Index: -1"))
        assertTrue(details.contains("Không có (-1 / -1)"))
        assertFalse(details.contains("EXTREME"))
    }
}
