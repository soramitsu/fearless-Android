package jp.co.soramitsu.tonconnect.api.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class TonConnectSignRequestTest {

    @Test
    fun shouldKeepAnAbsentExpiryAbsent_soTheWalletCanCapIt() {
        assertNull(TonConnectSignRequest.normalizeValidUntil(null))
    }

    @Test
    fun shouldReadExpiriesInSecondsOrMilliseconds() {
        assertEquals(1_800_000_000L, TonConnectSignRequest.normalizeValidUntil(1_800_000_000))
        assertEquals(1_800_000_000L, TonConnectSignRequest.normalizeValidUntil(1_800_000_000L))
        assertEquals(1_800_000_000L, TonConnectSignRequest.normalizeValidUntil(1_800_000_000_123L))
        assertEquals(1_800_000_000L, TonConnectSignRequest.normalizeValidUntil(" 1800000000 "))
        assertEquals(1_800_000_000L, TonConnectSignRequest.normalizeValidUntil("1.8e9"))
        assertEquals(1_800_000_000L, TonConnectSignRequest.normalizeValidUntil(BigDecimal("1800000000.7")))
    }

    @Test
    fun shouldKeepSmallAndNegativeExpiries_soThatTheyAreRejectedAsPast() {
        assertEquals(5L, TonConnectSignRequest.normalizeValidUntil(5))
        assertEquals(-1L, TonConnectSignRequest.normalizeValidUntil(-1))
    }

    @Test
    fun shouldMarkAnUnreadableExpiryAsExpired_insteadOfDroppingIt() {
        listOf("", "soon", true, listOf(1)).forEach { raw ->
            assertEquals(raw.toString(), TonConnectSignRequest.INVALID_VALID_UNTIL, TonConnectSignRequest.normalizeValidUntil(raw))
        }
    }

    @Test
    fun shouldKeepTheRawNetwork_soOtherNetworksCanBeRejected() {
        assertNull(TonConnectSignRequest.normalizeNetwork(null))
        assertEquals("-239", TonConnectSignRequest.normalizeNetwork(-239))
        assertEquals("-239", TonConnectSignRequest.normalizeNetwork(" -239 "))
        assertEquals("-3", TonConnectSignRequest.normalizeNetwork("-3"))
        assertEquals("1", TonConnectSignRequest.normalizeNetwork(1))
    }
}
