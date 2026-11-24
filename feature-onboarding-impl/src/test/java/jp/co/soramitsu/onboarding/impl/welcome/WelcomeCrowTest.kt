package jp.co.soramitsu.onboarding.impl.welcome

import android.media.AudioManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WelcomeCrowTest {
    @Test fun callIsSilentWhenTheRingerIsSilentOrVibrate() {
        assertFalse(crowCallMuted(AudioManager.RINGER_MODE_NORMAL))
        assertTrue(crowCallMuted(AudioManager.RINGER_MODE_VIBRATE))
        assertTrue(crowCallMuted(AudioManager.RINGER_MODE_SILENT))
    }

    @Test fun portraitPhoneCropsTheSidesEquallyAndKeepsTheTop() {
        // 1080 x 2400 px page, 720 x 1560 video: the height decides, the sides are cut evenly.
        val crop = crowCropTransform(1080f, 2400f, 720f, 1560f)
        val (sx, sy) = crop[0] to crop[1]
        val (tx, ty) = crop[2] to crop[3]
        val scale = 2400f / 1560f
        assertEquals(720f * scale / 1080f, sx, 1e-4f)
        assertEquals(1f, sy, 1e-4f)
        assertEquals((1080f - 720f * scale) / 2f, tx, 1e-3f)
        assertEquals(0f, ty, 0f)
        assertTrue("The video covers the page", 1080f * sx >= 1080f && 2400f * sy >= 2400f)
    }

    @Test fun widePageCoversTheWidthAndCutsTheBottomNotTheTop() {
        // A tablet-like page: the width decides, the crow stays anchored to the top.
        val crop = crowCropTransform(1600f, 2000f, 720f, 1560f)
        val (sx, sy) = crop[0] to crop[1]
        val (tx, ty) = crop[2] to crop[3]
        val scale = 1600f / 720f
        assertEquals(1f, sx, 1e-4f)
        assertEquals(1560f * scale / 2000f, sy, 1e-4f)
        assertEquals(0f, tx, 1e-3f)
        assertEquals(0f, ty, 0f)
    }

    @Test fun unknownSizesLeaveTheViewUntransformed() {
        assertArrayEquals(floatArrayOf(1f, 1f, 0f, 0f), crowCropTransform(0f, 2400f, 720f, 1560f), 0f)
        assertArrayEquals(floatArrayOf(1f, 1f, 0f, 0f), crowCropTransform(1080f, 2400f, 0f, 0f), 0f)
    }

    @Test fun crossfadeIsTheSpecifiedQuarterSecond() {
        assertEquals(250L, CROW_CROSSFADE_MS)
    }
}
