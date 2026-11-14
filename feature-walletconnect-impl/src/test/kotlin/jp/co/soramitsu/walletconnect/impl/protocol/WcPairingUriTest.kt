package jp.co.soramitsu.walletconnect.impl.protocol

import co.jp.soramitsu.walletconnect.model.WalletConnectPairingExpiredException
import co.jp.soramitsu.walletconnect.model.WalletConnectUriException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class WcPairingUriTest {
    private val topic = "7f6e504bfad60b485450578e05678ed3e8e8c4751d3c6160be17160d63ec90f9"
    private val symKey = "587d5484ce2a2a6ee3ba1962fdd7e8588e06200c46823bd18fbd67def96ad303"
    private val now = 1_700_000_000L

    @Test
    fun `a v2 uri is read with its key, relay and expiry`() {
        val uri = WcPairingUri.parse("wc:$topic@2?relay-protocol=irn&symKey=$symKey&expiryTimestamp=${now + 300}", now)

        assertEquals(topic, uri.topic)
        assertEquals(symKey, WcEncoding.hex(uri.symKey))
        assertEquals("irn", uri.relayProtocol)
        assertEquals(now + 300, uri.expiryTimestamp)
        assertNull(uri.relayData)
    }

    @Test
    fun `the android scheme form and extra parameters are accepted`() {
        val uri = WcPairingUri.parse("wc://$topic@2?symKey=$symKey&methods=%5Bwc_sessionPropose%5D&relay-protocol=irn", now)

        assertEquals(topic, uri.topic)
        assertNull(uri.expiryTimestamp)
    }

    @Test
    fun `a v1 uri is refused as unsupported`() {
        assertThrows(WalletConnectUriException::class.java) {
            WcPairingUri.parse(
                "wc:8a5e5bdc-a0e4-4702-ba63-8f1a5655744f@1?bridge=https%3A%2F%2Fbridge.walletconnect.org&key=41791102999c339c844880b23950704cc43aa840f3739e365323cda4dfa89e7a",
                now
            )
        }
    }

    @Test
    fun `an expired uri is refused as expired`() {
        assertThrows(WalletConnectPairingExpiredException::class.java) {
            WcPairingUri.parse("wc:$topic@2?relay-protocol=irn&symKey=$symKey&expiryTimestamp=$now", now)
        }
    }

    @Test
    fun `malformed uris are refused`() {
        val malformed = listOf(
            "https://example.org",
            "wc:$topic?symKey=$symKey",
            "wc:${topic.drop(2)}@2?symKey=$symKey",
            "wc:$topic@2?symKey=${symKey.drop(2)}",
            "wc:$topic@2?symKey=$symKey&relay-protocol=waku",
            "wc:$topic@2?symKey=$symKey&symKey=$symKey",
            "wc:$topic@2?symKey=$symKey&expiryTimestamp=soon",
            "wc:$topic@3?symKey=$symKey",
            "wc:$topic@2?symKey=$symKey&relay-data=" + "a".repeat(600),
            "wc:$topic@2?symKey=$symKey&x=" + "a".repeat(2100)
        )
        malformed.forEach { uri ->
            assertThrows(uri, WalletConnectUriException::class.java) { WcPairingUri.parse(uri, now) }
        }
    }
}
