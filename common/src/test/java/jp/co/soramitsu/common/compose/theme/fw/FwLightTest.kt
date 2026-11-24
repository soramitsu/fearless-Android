package jp.co.soramitsu.common.compose.theme.fw

import kotlin.math.abs
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FwLightTest {
    @Test fun keyLightIsAUnitVectorFromTheTopLeft() {
        assertEquals(1f, hypot(FwLight.KEY_X, FwLight.KEY_Y), 1e-6f)
        assertTrue(FwLight.KEY_X < 0f && FwLight.KEY_Y < 0f)
    }

    @Test fun raisedShadowsNeverMixDirectionsAndKeepTheSpecMagnitudes() {
        val pair = requireNotNull(fwShadowPair(FwElevation.Raised2))
        // Highlights toward the light (up-left), shadows away from it (down-right), along the key vector.
        assertTrue(pair.highlight.dx < 0f && pair.highlight.dy < 0f)
        assertTrue(pair.shadow.dx > 0f && pair.shadow.dy > 0f)
        assertEquals(FwLight.KEY_Y / FwLight.KEY_X, pair.highlight.dy / pair.highlight.dx, 1e-5f)
        assertEquals(FwLight.KEY_Y / FwLight.KEY_X, pair.shadow.dy / pair.shadow.dx, 1e-5f)
        assertEquals(hypot(5f, 5f), hypot(pair.highlight.dx, pair.highlight.dy), 1e-4f)
        assertEquals(hypot(6f, 6f), hypot(pair.shadow.dx, pair.shadow.dy), 1e-4f)
        assertEquals(12f, pair.highlight.blur, 0f)
        assertEquals(16f, pair.shadow.blur, 0f)
    }

    @Test fun raisedOneIsSixtyPercentOfRaisedTwo() {
        val two = requireNotNull(fwShadowPair(FwElevation.Raised2))
        val one = requireNotNull(fwShadowPair(FwElevation.Raised1))
        for ((a, b) in listOf(one.highlight to two.highlight, one.shadow to two.shadow)) {
            assertEquals(b.dx * 0.6f, a.dx, 1e-5f)
            assertEquals(b.dy * 0.6f, a.dy, 1e-5f)
            assertEquals(b.blur * 0.6f, a.blur, 1e-5f)
        }
    }

    @Test fun insetMirrorsTheLightAndEmissiveCastsNoKeyShadow() {
        val inset = requireNotNull(fwShadowPair(FwElevation.Inset))
        // The dark inner shadow is offset away from the light, so it lines the rim nearest the light.
        assertTrue(inset.shadow.dx > 0f && inset.shadow.dy > 0f)
        assertTrue(inset.highlight.dx < 0f && inset.highlight.dy < 0f)
        assertNull(fwShadowPair(FwElevation.Emissive))
    }

    @Test fun sunRimFallsOffWithDistanceAndVanishesNearTheBottom() {
        val width = 390f
        assertEquals(FwLight.SUN_RIM_MAX, fwSunRimAlpha(0f, width), 1e-6f)
        val nearCard = fwSunRimAlpha(150f, width)
        val listRow = fwSunRimAlpha(300f, width)
        val tabBar = fwSunRimAlpha(600f, width)
        assertTrue(nearCard > listRow && listRow > tabBar)
        assertEquals(0.12f, listRow, 0.02f)
        assertEquals(0f, tabBar, 0f)
        // Symmetric and monotonic.
        assertEquals(fwSunRimAlpha(-200f, width), fwSunRimAlpha(200f, width), 0f)
        var last = Float.MAX_VALUE
        for (d in 0..800 step 10) {
            val alpha = fwSunRimAlpha(d.toFloat(), width)
            assertTrue(alpha <= last)
            last = alpha
        }
    }

    @Test fun sunRimFailsClosedOnUnknownGeometry() {
        assertEquals(0f, fwSunRimAlpha(10f, 0f), 0f)
        assertEquals(0f, fwSunRimAlpha(Float.NaN, 390f), 0f)
        assertEquals(0f, fwSunRimAlpha(10f, Float.NaN), 0f)
    }

    @Test fun sunDistanceIsMeasuredToTheNearestPointOfTheTopEdge() {
        assertEquals(100f, fwSunDistance(200f, 100f, 0f, 200f, 400f), 0f)
        assertEquals(hypot(50f, 100f), fwSunDistance(200f, 100f, 250f, 200f, 400f), 1e-4f)
        assertEquals(hypot(30f, 40f), fwSunDistance(200f, 100f, 0f, 60f, 170f), 1e-4f)
    }

    @Test fun quantisedRimChangesOnlyInVisibleSteps() {
        assertEquals(0f, fwQuantizedRim(0.01f), 0f)
        assertEquals(fwQuantizedRim(0.2f), fwQuantizedRim(0.2001f), 0f)
        assertTrue(abs(fwQuantizedRim(0.33f) - 0.33f) <= 1f / 64f)
        assertEquals(1f, fwQuantizedRim(2f), 0f)
    }
}
