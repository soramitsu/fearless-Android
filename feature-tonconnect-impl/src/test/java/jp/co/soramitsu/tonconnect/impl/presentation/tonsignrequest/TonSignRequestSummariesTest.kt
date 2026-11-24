package jp.co.soramitsu.tonconnect.impl.presentation.tonsignrequest

import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.tonconnect.api.model.TonConnectRawMessage
import jp.co.soramitsu.tonconnect.api.model.TonConnectSignRequest
import jp.co.soramitsu.tonconnect.impl.domain.TonConnectMessageSummaries
import jp.co.soramitsu.tonconnect.impl.domain.TonConnectPayloadSummary
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ton.block.AddrStd
import java.math.BigInteger

class TonSignRequestSummariesTest {
    private val resources = mockk<ResourceManager> {
        every { getString(any()) } answers { "string:${firstArg<Int>()}" }
        every {
            getString(any(), *anyVararg())
        } answers { "string:${firstArg<Int>()}:${secondArg<Array<Any>>().joinToString()}" }
    }
    private val chain = mockk<Chain> { every { assets } returns emptyList() }
    private val summaries = TonSignRequestSummaries(resources) { _, _ -> emptyMap() }
    private val address = AddrStd(0, ByteArray(32) { 5 })
    private val message = TonConnectRawMessage(address.toString(userFriendly = true), 1_000_000_001L, null, null)

    @Test
    fun `native message shows exact TON amount and complete address`() = runBlocking {
        val result = summaries.summary(chain, request(listOf(message)), byteArrayOf())
        assertFalse(result.showsRawData)
        assertTrue(result.sections.single().rows.any { it.value == "1.000000001 TON" })
        assertTrue(result.sections.single().rows.any { it.value == TonConnectMessageSummaries.summary(message).destination })
    }

    @Test
    fun `multiple messages show a separate total without losing destinations`() = runBlocking {
        val result = summaries.summary(chain, request(listOf(message, message.copy(amount = 2))), byteArrayOf())
        assertEquals(3, result.sections.size)
        assertEquals("1.000000003 TON", result.sections.last().rows.single().value)
    }

    @Test
    fun `state init and empty requests preserve fallback`() = runBlocking {
        val stateInit = summaries.summary(chain, request(listOf(message.copy(stateInitValue = "encoded-state-init"))), byteArrayOf())
        assertTrue(stateInit.showsRawData)
        assertTrue(stateInit.warnings.isNotEmpty())
        assertTrue(summaries.summary(chain, request(emptyList()), byteArrayOf()).showsRawData)
    }

    @Test
    fun `malformed address displays unknown rather than throwing`() = runBlocking {
        assertTrue(summaries.summary(chain, request(listOf(message.copy(addressValue = "bad address"))), byteArrayOf()).showsRawData)
    }

    @Test
    fun `known jetton uses token precision and unknown jetton uses integer units`() {
        val decoded = TonConnectMessageSummaries.summary(message).copy(
            payload = TonConnectPayloadSummary.JettonTransfer(
            BigInteger("1000001"), address, address, BigInteger.ONE, TonConnectPayloadSummary.ForwardPayload.Comment("memo")
        )
        )
        val known = summaries.present(chain, listOf(decoded), mapOf(address.toString(userFriendly = false).lowercase() to TonJetton("USDC", 6)))
        assertTrue(known.sections.single().rows.any { it.value == "1.000001 USDC" })
        assertTrue(known.sections.single().rows.any { it.value == TonSignRequestSummaries.ownerAddress(address) })
        assertTrue(known.sections.single().rows.any { it.value == "memo" })
        assertTrue(
            summaries.present(chain, listOf(decoded), emptyMap()).sections.single().rows.any {
            it.value.endsWith(":1000001")
        }
        )
    }

    private fun request(messages: List<TonConnectRawMessage>) =
        TonConnectSignRequest(null, 1_800_000_000L, messages, "-239")
}
