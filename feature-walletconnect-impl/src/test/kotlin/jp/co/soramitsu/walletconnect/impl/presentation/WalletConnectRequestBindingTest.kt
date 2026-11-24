package jp.co.soramitsu.walletconnect.impl.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletConnectRequestBindingTest {

    private val shown = sessionRequest(id = 5, method = "personal_sign", params = """["0x01", "0xab"]""", chainId = "eip155:1")

    @Test
    fun `pending request is found by its exact id only`() {
        val newer = sessionRequest(id = 9, method = "personal_sign", params = """["0x02", "0xab"]""", chainId = "eip155:1")

        assertSame(shown, listOf(newer, shown).pendingRequest(5))
        assertNull(listOf(newer).pendingRequest(5))
    }

    @Test
    fun `an ambiguous id matches no request`() {
        val sameId = sessionRequest(id = 5, method = "personal_sign", params = """["0x02", "0xab"]""", chainId = "eip155:1")

        assertNull(listOf(shown, sameId).pendingRequest(5))
    }

    @Test
    fun `request is the same only if network, method and params are unchanged`() {
        assertTrue(shown.isSameRequestAs(shown))
        assertTrue(sessionRequest(id = 5, method = "personal_sign", params = """["0x01", "0xab"]""", chainId = "eip155:1").isSameRequestAs(shown))

        assertFalse(sessionRequest(id = 5, method = "personal_sign", params = """["0x02", "0xab"]""", chainId = "eip155:1").isSameRequestAs(shown))
        assertFalse(sessionRequest(id = 5, method = "eth_sign", params = """["0x01", "0xab"]""", chainId = "eip155:1").isSameRequestAs(shown))
        assertFalse(sessionRequest(id = 5, method = "personal_sign", params = """["0x01", "0xab"]""", chainId = "eip155:56").isSameRequestAs(shown))
        assertFalse(sessionRequest(id = 6, method = "personal_sign", params = """["0x01", "0xab"]""", chainId = "eip155:1").isSameRequestAs(shown))
        assertFalse(
            sessionRequest(id = 5, method = "personal_sign", params = """["0x01", "0xab"]""", chainId = "eip155:1", topic = "other")
                .isSameRequestAs(shown)
        )
    }
}
