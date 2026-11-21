package jp.co.soramitsu.walletconnect.impl.presentation.summary

import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.walletconnect.impl.presentation.WalletConnectEvmTransaction
import jp.co.soramitsu.walletconnect.impl.presentation.ethereumChain
import jp.co.soramitsu.walletconnect.impl.presentation.sessionRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.web3j.crypto.TransactionEncoder
import java.math.BigInteger

class EvmSigningSummaryTest {
    @Test
    fun `transfer decodes full address and unsigned 256 bit amount`() {
        val amount = BigInteger.ONE.shiftLeft(255) + BigInteger.ONE
        val data = ("a9059cbb" + addressWord(RECIPIENT) + word(amount)).fromHex()
        assertEquals(EvmTokenCall.Transfer(checksumAddress(RECIPIENT), amount), EvmCalldata.decode(data))
        assertEquals("0xa9059cbb", EvmCalldata.selector(data))
    }

    @Test
    fun `approve and increase allowance preserve spender and exact amount`() {
        val amount = BigInteger.ONE.shiftLeft(256) - BigInteger.ONE
        assertEquals(
            EvmTokenCall.Approve(checksumAddress(RECIPIENT), amount),
            EvmCalldata.decode(("095ea7b3" + addressWord(RECIPIENT) + word(amount)).fromHex())
        )
        assertEquals(
            EvmTokenCall.Approve(checksumAddress(RECIPIENT), BigInteger.ONE, true),
            EvmCalldata.decode(("39509351" + addressWord(RECIPIENT) + word(BigInteger.ONE)).fromHex())
        )
        assertTrue(amount.isUnlimitedAllowance())
        assertFalse((unlimitedAllowanceThreshold - BigInteger.ONE).isUnlimitedAllowance())
    }

    @Test
    fun `transfer from preserves owner separately from recipient`() {
        assertEquals(
            EvmTokenCall.TransferFrom(checksumAddress(OWNER), checksumAddress(RECIPIENT), BigInteger.TEN),
            EvmCalldata.decode(("23b872dd" + addressWord(OWNER) + addressWord(RECIPIENT) + word(BigInteger.TEN)).fromHex())
        )
    }

    @Test
    fun `approval for all accepts only canonical booleans`() {
        for (value in 0..1) {
            assertEquals(
                EvmTokenCall.ApprovalForAll(checksumAddress(RECIPIENT), value == 1),
                EvmCalldata.decode(("a22cb465" + addressWord(RECIPIENT) + word(value.toBigInteger())).fromHex())
            )
        }
        assertNull(EvmCalldata.decode(("a22cb465" + addressWord(RECIPIENT) + word(BigInteger.TWO)).fromHex()))
    }

    @Test
    fun `noncanonical and truncated calls stay undecoded`() {
        val valid = "a9059cbb" + addressWord(RECIPIENT) + word(BigInteger.ONE)
        for (hex in listOf(
            "", "a905", "deadbeef", valid.dropLast(2), valid + "00", valid + word(BigInteger.ZERO),
            "a9059cbb" + "01" + addressWord(RECIPIENT).drop(2) + word(BigInteger.ONE)
        )) {
            assertNull(hex, EvmCalldata.decode(hex.fromHex()))
        }
        assertNull(EvmCalldata.selector(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `native transfer summary agrees with the RLP transaction value and bytes`() = runBlocking {
        val json = """{"from":"$OWNER","to":"$RECIPIENT","value":"0xde0b6b3a7640001","nonce":"0x1","gasPrice":"0x2","gasLimit":"0x5208","data":"0x","chainId":"0x1"}"""
        val parsed = WalletConnectEvmTransaction.parse(json)
        val summary = decode(json) as WalletConnectRequestSummary.EvmTransaction
        assertEquals(BigInteger("1000000000000000001"), summary.value)
        assertEquals(parsed.rawTransaction().value, summary.value)
        assertEquals(checksumAddress(parsed.rawTransaction().to), summary.to)
        assertEquals(BigInteger.ONE, parsed.chainId)
        assertArrayEquals(byteArrayOf(), parsed.data)
        assertTrue(TransactionEncoder.encode(parsed.rawTransaction()).isNotEmpty())
        assertNull(summary.selector)
        assertTrue(summary.isComplete)
    }

    @Test
    fun `unknown calldata preserves attached native value and needs raw review`() = runBlocking {
        val summary = decode("""{"to":"$RECIPIENT","value":"0x7","data":"0xdeadbeef0102"}""") as WalletConnectRequestSummary.EvmTransaction
        assertEquals(BigInteger.valueOf(7), summary.value)
        assertEquals("0xdeadbeef", summary.selector)
        assertFalse(summary.isComplete)
    }

    @Test
    fun `malformed recipient or data cannot produce a transfer summary`() = runBlocking {
        for (json in listOf(
            "{}", """{"to":"0x1234"}""", """{"to":"$RECIPIENT","data":"0x1"}""",
            """{"to":"$RECIPIENT","data":"0xzz"}"""
        )) {
            assertEquals(WalletConnectRequestSummary.Undecoded, decode(json))
        }
    }

    @Test
    fun `fees selected later by signer retain raw fallback`() = runBlocking {
        val result = decode("""{"to":"$RECIPIENT","value":"0x1"}""") as WalletConnectRequestSummary.EvmTransaction
        assertFalse(result.isComplete)
        assertNull(result.gasPrice)
        assertNull(result.gasLimit)
    }

    @Test
    fun `supplied unreadable quantities are rejected instead of guessing zero or an estimate`() = runBlocking {
        for (field in listOf("value", "nonce", "gasPrice", "gasLimit")) {
            for (invalid in listOf("null", "123", "\"not-a-quantity\"", "\"-0x1\"", "{}")) {
                val json = """{"to":"$RECIPIENT","$field":$invalid}"""
                assertEquals("$field=$invalid", WalletConnectRequestSummary.Undecoded, decode(json))
                assertTrue(runCatching { WalletConnectEvmTransaction.parse(json) }.isFailure)
            }
        }
    }

    private suspend fun decode(json: String) = WalletConnectRequestDecoder { null }.summary(
        ethereumChain, sessionRequest(1, "eth_sendTransaction", "[$json]", "eip155:1")
    )

    private companion object {
        const val OWNER = "0x1111111111111111111111111111111111111111"
        const val RECIPIENT = "0x52908400098527886e0f7030069857d2e4169ee7"
        fun addressWord(address: String) = address.removePrefix("0x").padStart(64, '0')
        fun word(value: BigInteger) = value.toString(16).padStart(64, '0')
    }
}
