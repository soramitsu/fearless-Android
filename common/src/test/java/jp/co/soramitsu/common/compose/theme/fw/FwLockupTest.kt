package jp.co.soramitsu.common.compose.theme.fw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Brand rollout spec, item 4: the stacked lockup takes about 56 % of the screen width, at most 280 dp. */
class FwLockupTest {
    @Test fun phonesGetAboutHalfTheirWidth() {
        assertEquals(201.6f, FwLockup.widthDp(360f), 0.01f)
        assertEquals(230.16f, FwLockup.widthDp(411f), 0.01f)
    }

    @Test fun tabletsStopAt280Dp() {
        assertEquals(280f, FwLockup.widthDp(600f), 0f)
        assertEquals(280f, FwLockup.widthDp(1280f), 0f)
    }

    @Test fun aShortPageShrinksTheLockupToTheHeightItCanGive() {
        val width = FwLockup.widthDp(411f, maxHeightDp = 100f)
        assertEquals(100f * FwLockup.ASPECT, width, 0.01f)
        assertTrue(width / FwLockup.ASPECT <= 100f + 1e-3f)
    }

    @Test fun noRoomMeansNoLockupRatherThanANegativeSize() {
        assertEquals(0f, FwLockup.widthDp(411f, maxHeightDp = -20f), 0f)
    }

    @Test fun aspectIsTheKitMastersViewBox() {
        assertEquals(640.0 / 505.3032355154251, FwLockup.ASPECT.toDouble(), 1e-6)
    }
}
