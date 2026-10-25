package jp.co.soramitsu.common.compose.theme.fw

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class FwEffectsTest {
    @Test fun contrastAndReducedChoicesTakePrecedenceOverEveryAutomaticSignal() {
        for (sdk in listOf(26, 30, 31, 33, 34, 36)) {
            for (lowRam in listOf(false, true)) for (powerSave in listOf(false, true)) {
                for (choice in FwTierChoice.entries) for (userContrast in listOf(false, true)) {
                    for (systemContrast in listOf(0f, 0.49f, 0.5f, 1f)) {
                        val appearance = FwAppearance(choice, userContrast)
                        val fx = resolveFwEffects(sdk, lowRam, powerSave, systemContrast, true, appearance)
                        val hc = userContrast || (sdk >= 34 && systemContrast >= 0.5f)
                        val expected = when {
                            hc || choice == FwTierChoice.Reduced -> FwTier.Reduced
                            sdk < 31 || lowRam || powerSave || choice == FwTierChoice.Lite -> FwTier.Lite
                            else -> FwTier.Full
                        }
                        assertEquals(expected, fx.tier)
                        assertEquals(hc, fx.highContrast)
                        assertEquals(expected != FwTier.Reduced, fx.glass)
                        assertFalse(fx.liveBlur)
                    }
                }
            }
        }
    }

    @Test fun motionIsIndependentOfTierAndCalmPreservesParentPreferences() {
        for (tier in FwTier.entries) for (hc in listOf(false, true)) for (motion in listOf(false, true)) {
            val parent = FwEffects(tier, hc, motion)
            val calm = parent.copy(calm = true)
            assertFalse(parent.calm)
            assertTrue(calm.paintReduced)
            assertFalse(calm.glass || calm.aurora || calm.glow || calm.neuShadows || calm.liveBlur)
            assertEquals(tier, calm.tier)
            assertEquals(hc, calm.highContrast)
            assertEquals(motion, calm.reduceMotion)
        }
        val moving = resolveFwEffects(34, false, false, 0f, true)
        val still = resolveFwEffects(34, false, false, 0f, false)
        assertEquals(moving.copy(reduceMotion = true), still)
    }

    @Test fun missingUnknownAndWrongTypeDisplayPreferencesCannotBreakThemeStartup() {
        val raw = mock(SharedPreferences::class.java)
        val preferences = FwAppearancePreferences(raw)
        for (values in listOf(emptyMap(), mapOf("visual_effects" to "future", "increase_contrast" to "true"),
            mapOf("visual_effects" to 4, "increase_contrast" to 1))) {
            `when`(raw.all).thenReturn(values)
            assertEquals(FwAppearance(), preferences.read())
        }
        `when`(raw.all).thenReturn(mapOf("visual_effects" to "Lite", "increase_contrast" to true))
        assertEquals(FwAppearance(FwTierChoice.Lite, true), preferences.read())
    }

    @Test fun displayPreferencePersistenceFailureIsReported() {
        val raw = mock(SharedPreferences::class.java)
        val editor = mock(SharedPreferences.Editor::class.java)
        `when`(raw.edit()).thenReturn(editor)
        `when`(editor.putString("visual_effects", "Reduced")).thenReturn(editor)
        `when`(editor.putBoolean("increase_contrast", true)).thenReturn(editor)
        val preferences = FwAppearancePreferences(raw)
        `when`(editor.commit()).thenReturn(false, true)
        assertFalse(preferences.save(FwAppearance(FwTierChoice.Reduced, true)))
        assertTrue(preferences.save(FwAppearance(FwTierChoice.Reduced, true)))
    }
}
