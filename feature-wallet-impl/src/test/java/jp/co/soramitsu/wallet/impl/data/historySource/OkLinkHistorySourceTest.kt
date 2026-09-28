package jp.co.soramitsu.wallet.impl.data.historySource

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.testshared.whenever
import jp.co.soramitsu.wallet.impl.data.network.model.response.OkLinkHistoryItem
import jp.co.soramitsu.wallet.impl.data.network.model.response.OkLinkHistoryPage
import jp.co.soramitsu.wallet.impl.data.network.model.response.OkLinkHistoryResponse
import jp.co.soramitsu.wallet.impl.data.network.subquery.OperationsHistoryApi
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.stubbing.Answer

class OkLinkHistorySourceTest {
    @Test
    fun `rejects provider errors even when they contain data and never reveals provider message`() = runBlocking {
        val api = RecordingApi { response(500, listOf(item()), "private account detail") }
        val result = runCatching { api.source().getOperations(20, null, FILTER, ACCOUNT_ID, chain(), nativeAsset(), ADDRESS) }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("private account detail") != true)
        assertEquals(1, api.calls)
    }

    @Test
    fun `transport and missing page fail instead of clearing history`() = runBlocking {
        val transport = RecordingApi { throw IllegalStateException("network unavailable") }
        val transportResult = runCatching {
            transport.source().getOperations(20, null, FILTER, ACCOUNT_ID, chain(), nativeAsset(), ADDRESS)
        }
        assertTrue(transportResult.exceptionOrNull() is IllegalStateException)

        val malformed = RecordingApi { OkLinkHistoryResponse(0, "", emptyList()) }
        val malformedResult = runCatching {
            malformed.source().getOperations(20, null, FILTER, ACCOUNT_ID, chain(), nativeAsset(), ADDRESS)
        }
        assertTrue(malformedResult.isFailure)
    }

    @Test
    fun `native history ignores token rows and token history binds exact contract`() = runBlocking {
        val api = RecordingApi {
            response(0, listOf(item(), item(contract = TOKEN, symbol = "USDC"), item(contract = "0xother", symbol = "USDC")))
        }
        val utility = nativeAsset()
        val chain = chain(utility)
        val native = api.source().getOperations(20, null, FILTER, ACCOUNT_ID, chain, utility, ADDRESS)
        val token = api.source().getOperations(20, null, FILTER, ACCOUNT_ID, chain, tokenAsset(), ADDRESS)

        assertEquals(1, native.items.size)
        assertEquals(1, token.items.size)
        assertEquals(BigInteger("1000000000000000000"), (native.items.single().type as Operation.Type.Transfer).amount)
        assertEquals(BigInteger("1000000"), (token.items.single().type as Operation.Type.Transfer).amount)
        assertEquals(BigInteger("1000000000000000"), (token.items.single().type as Operation.Type.Transfer).fee)
    }

    @Test
    fun `accepts explicit empty list but rejects imprecise token amount`() = runBlocking {
        val emptyApi = RecordingApi { response(0, emptyList()) }
        val emptyPage = emptyApi.source().getOperations(20, null, FILTER, ACCOUNT_ID, chain(), nativeAsset(), ADDRESS)
        assertTrue(emptyPage.items.isEmpty())
        assertEquals(null, emptyPage.nextCursor)

        val impreciseApi = RecordingApi { response(0, listOf(item(contract = TOKEN, symbol = "USDC", amount = "0.0000001"))) }
        val result = runCatching {
            impreciseApi.source().getOperations(20, null, FILTER, ACCOUNT_ID, chain(nativeAsset()), tokenAsset(), ADDRESS)
        }
        assertTrue(result.isFailure)
    }

    private class RecordingApi(private val reply: () -> OkLinkHistoryResponse) {
        var calls = 0
        val client: OperationsHistoryApi = mock(OperationsHistoryApi::class.java, Answer<Any?> { invocation ->
            check(invocation.method.name == "getOkLinkOperationsHistory")
            calls++
            reply()
        })

        fun source() = OkLinkHistorySource(client, "https://history.example/api")
    }

    private fun chain(vararg assets: Asset): Chain = mock(Chain::class.java).also {
        whenever(it.assets).thenReturn(assets.toList())
    }

    private fun nativeAsset(): Asset = mock(Asset::class.java).also {
        whenever(it.type).thenReturn(ChainAssetType.Normal)
        whenever(it.symbol).thenReturn("OKB")
        whenever(it.precision).thenReturn(18)
        whenever(it.isUtility).thenReturn(true)
    }

    private fun tokenAsset(): Asset = mock(Asset::class.java).also {
        whenever(it.type).thenReturn(ChainAssetType.ERC20)
        whenever(it.id).thenReturn(TOKEN)
        whenever(it.symbol).thenReturn("USDC")
        whenever(it.precision).thenReturn(6)
    }

    private fun response(code: Int, items: List<OkLinkHistoryItem>, message: String = "") = OkLinkHistoryResponse(
        code = code,
        msg = message,
        data = listOf(OkLinkHistoryPage(1, 20, 1, "X Layer", "XLAYER", items))
    )

    private fun item(contract: String = "", symbol: String = "OKB", amount: String = "1") = OkLinkHistoryItem(
        txId = "0xtx",
        methodId = "",
        blockHash = "0xblock",
        height = BigInteger.ONE,
        transactionTime = 1_710_000_000_000,
        from = ADDRESS,
        to = "0xreceiver",
        isFromContract = false,
        isToContract = false,
        amount = BigDecimal(amount),
        transactionSymbol = symbol,
        txFee = BigDecimal("0.001"),
        state = "success",
        tokenId = "",
        tokenContractAddress = contract,
        challengeStatus = "",
        l1OriginHash = ""
    )

    private companion object {
        const val TOKEN = "0xtoken"
        const val ADDRESS = "0x0102"
        val ACCOUNT_ID = byteArrayOf(1, 2)
        val FILTER = setOf(TransactionFilter.TRANSFER)
    }
}
