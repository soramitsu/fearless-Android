package jp.co.soramitsu.common.compose.theme.fw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FwSunTest {
    @Test fun bloomPeaksOnceAndSettlesOnTheRestingGlow() {
        val (start, startScale) = fwBloom(0f)
        val (peak, peakScale) = fwBloom(0.35f)
        val (rest, restScale) = fwBloom(1f)
        assertEquals(0.6f, start, 1e-6f)
        assertEquals(1f, startScale, 1e-6f)
        assertEquals(1f, peak, 1e-6f)
        assertEquals(1.08f, peakScale, 1e-6f)
        assertEquals(0.85f, rest, 1e-6f)
        assertEquals(1f, restScale, 1e-6f)
        assertEquals(fwBloom(1f), fwBloom(3f))
        assertTrue(fwBloom(0.2f).first in 0.6f..1f)
    }

    @Test fun syncArcShowsKnownProgressAndAQuarterTurnOtherwise() {
        assertEquals(90f, fwSyncSweep(null), 0f)
        assertEquals(180f, fwSyncSweep(0.5f), 1e-4f)
        assertEquals(360f, fwSyncSweep(2f), 0f)
        assertEquals(12f, fwSyncSweep(0f), 0f)
    }
}
