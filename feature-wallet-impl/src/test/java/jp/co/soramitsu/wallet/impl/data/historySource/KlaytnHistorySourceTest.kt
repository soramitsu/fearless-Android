package jp.co.soramitsu.wallet.impl.data.historySource

import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.data.network.model.response.KlaytnHistoryItem
import jp.co.soramitsu.wallet.impl.data.network.model.response.KlaytnHistoryResponse
import jp.co.soramitsu.wallet.impl.data.network.subquery.OperationsHistoryApi
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.stubbing.Answer

class KlaytnHistorySourceTest {
    @Test
    fun `provider failure and transport error never become an empty successful page`() = runBlocking {
        val rejected = RecordingApi { KlaytnHistoryResponse(false, listOf(item()), 1, 1) }
        val rejection = runCatching { rejected.source().getOperations(20, null, FILTER, ACCOUNT_ID, CHAIN, ASSET, ADDRESS) }
        assertTrue(rejection.isFailure)

        val transport = RecordingApi { throw IllegalStateException("network unavailable") }
        val failure = runCatching { transport.source().getOperations(20, null, FILTER, ACCOUNT_ID, CHAIN, ASSET, ADDRESS) }
        assertTrue(failure.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `empty successful page terminates pagination and mismatched page fails`() = runBlocking {
        val api = RecordingApi { KlaytnHistoryResponse(true, emptyList(), 1, 0) }
        val page = api.source().getOperations(20, null, FILTER, ACCOUNT_ID, CHAIN, ASSET, ADDRESS)
        assertTrue(page.items.isEmpty())
        assertEquals(null, page.nextCursor)

        val mismatch = RecordingApi { KlaytnHistoryResponse(true, listOf(item()), 2, 1) }
        val result = runCatching { mismatch.source().getOperations(20, null, FILTER, ACCOUNT_ID, CHAIN, ASSET, ADDRESS) }
        assertTrue(result.isFailure)
    }

    @Test
    fun `successful transfer retains cursor and rejects invalid cursor before request`() = runBlocking {
        val api = RecordingApi { KlaytnHistoryResponse(true, listOf(item()), 1, 2) }
        val page = api.source().getOperations(20, null, FILTER, ACCOUNT_ID, CHAIN, ASSET, ADDRESS)
        assertEquals("2", page.nextCursor)
        assertEquals("0xtx", page.items.single().id)

        val invalid = runCatching { api.source().getOperations(20, "bad", FILTER, ACCOUNT_ID, CHAIN, ASSET, ADDRESS) }
        assertTrue(invalid.isFailure)
        assertEquals(1, api.calls)
    }

    private class RecordingApi(private val reply: () -> KlaytnHistoryResponse) {
        var calls = 0
        val client: OperationsHistoryApi = mock(OperationsHistoryApi::class.java, Answer<Any?> { invocation ->
            check(invocation.method.name == "getKlaytnOperationsHistory")
            calls++
            reply()
        })

        fun source() = KlaytnHistorySource(client, "https://history.example/")
    }

    private fun item() = KlaytnHistoryItem(
        createdAt = 1_710_000_000_000,
        txHash = "0xtx",
        blockNumber = BigInteger.ONE,
        fromAddress = ADDRESS,
        toAddress = "0xreceiver",
        amount = BigInteger.ONE,
        txFee = BigInteger.ONE,
        gasLimit = BigInteger.ONE,
        gasUsed = BigInteger.ONE,
        gasPrice = BigInteger.ONE,
        txStatus = 1
    )

    private companion object {
        const val ADDRESS = "0x0102"
        val ACCOUNT_ID = byteArrayOf(1, 2)
        val FILTER = setOf(TransactionFilter.TRANSFER)
        val CHAIN = mock(Chain::class.java)
        val ASSET = mock(Asset::class.java)
    }
}
