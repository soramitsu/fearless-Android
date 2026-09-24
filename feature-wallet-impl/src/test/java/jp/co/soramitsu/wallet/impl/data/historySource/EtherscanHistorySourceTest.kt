package jp.co.soramitsu.wallet.impl.data.historySource

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.runtime.multiNetwork.chain.model.BSCChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.goerliChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polygonTestnetChainId
import jp.co.soramitsu.testshared.whenever
import jp.co.soramitsu.wallet.impl.data.network.model.response.EtherscanHistoryElement
import jp.co.soramitsu.wallet.impl.data.network.model.response.EtherscanHistoryResponse
import jp.co.soramitsu.wallet.impl.data.network.subquery.HistoryNotSupportedException
import jp.co.soramitsu.wallet.impl.data.network.subquery.OperationsHistoryApi
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.stubbing.Answer

class EtherscanHistorySourceTest {
    @Test
    fun `uses unified V2 key and chain ID for BNB native history with a page cursor`() = runBlocking {
        val fullPage = response(
            "1",
            "OK",
            Gson().toJsonTree(List(1000) { index -> transaction().copy(hash = "tx-$index") })
        )
        val api = RecordingApi { fullPage }
        val source = EtherscanHistorySource(api.client, LEGACY_BSC_URL, "unified-key")

        val first = source.getOperations(1, null, TRANSFER_FILTER, ACCOUNT_ID, chain(BSCChainId), asset(), ADDRESS)
        val second = source.getOperations(1, first.nextCursor, TRANSFER_FILTER, ACCOUNT_ID, chain(BSCChainId), asset(), ADDRESS)

        assertEquals(1000, first.items.size)
        assertEquals("tx-0", first.items.first().id)
        assertEquals("2", first.nextCursor)
        assertEquals("3", second.nextCursor)
        assertEquals(2, api.calls.size)
        assertEquals(V2_URL, api.calls[0][0])
        assertEquals(BSCChainId, api.calls[0][1])
        assertEquals("txlist", api.calls[0][3])
        assertEquals(null, api.calls[0][4])
        assertEquals("0x0102", api.calls[0][5])
        assertEquals(1, api.calls[0][6])
        assertEquals(1000, api.calls[0][7])
        assertEquals("unified-key", api.calls[0][9])
        assertEquals(2, api.calls[1][6])
        val transfer = first.items.first().type as Operation.Type.Transfer
        assertEquals(BigInteger.valueOf(7), transfer.amount)
        assertEquals(BigInteger.valueOf(42), transfer.fee)
    }

    @Test
    fun `uses token transfer query and filters unrelated contract`() = runBlocking {
        val api = RecordingApi {
            response(
                "1",
                "OK",
                Gson().toJsonTree(listOf(transaction(contract = TOKEN), transaction(contract = "0xunrelated")))
            )
        }
        val page = EtherscanHistorySource(api.client, LEGACY_BSC_URL, "unified-key")
            .getOperations(25, null, TRANSFER_FILTER, ACCOUNT_ID, chain(BSCChainId), asset(ChainAssetType.BEP20), ADDRESS)

        assertEquals(1, page.items.size)
        assertEquals("tokentx", api.calls.single()[3])
        assertEquals(TOKEN, api.calls.single()[4])
        assertEquals(1000, api.calls.single()[7])
    }

    @Test
    fun `accepts only explicit no-transactions response as an empty page`() = runBlocking {
        val noRecords = RecordingApi { response("0", "No transactions found", JsonParser.parseString("[]")) }
        val page = EtherscanHistorySource(noRecords.client, LEGACY_BSC_URL, "unified-key")
            .getOperations(25, null, TRANSFER_FILTER, ACCOUNT_ID, chain(BSCChainId), asset(), ADDRESS)

        assertTrue(page.items.isEmpty())
        assertEquals(null, page.nextCursor)
        assertEquals(1, noRecords.calls.size)
    }

    @Test
    fun `provider errors and malformed success never become an empty successful page`() = runBlocking {
        val responses = listOf(
            response("0", "NOTOK", JsonParser.parseString("\"Invalid API Key\"")),
            response("0", "NOTOK", JsonParser.parseString("\"Max rate limit reached\"")),
            response("1", "OK", JsonParser.parseString("\"unexpected string\"")),
            response("0", "No transactions found", JsonParser.parseString("\"rate limited\""))
        )
        responses.forEach { providerResponse ->
            val api = RecordingApi { providerResponse }
            val result = runCatching {
                EtherscanHistorySource(api.client, LEGACY_BSC_URL, "unified-key")
                    .getOperations(25, null, TRANSFER_FILTER, ACCOUNT_ID, chain(BSCChainId), asset(), ADDRESS)
            }
            assertTrue(result.isFailure)
            assertEquals(1, api.calls.size)
            assertTrue(result.exceptionOrNull()?.message?.contains("Invalid API Key") != true)
        }
    }

    @Test
    fun `parses quoted V2 numeric fields and refuses missing native transaction status`() = runBlocking {
        val quotedProviderJson = """{
            "status":"1","message":"OK","result":[{
                "blockNumber":"100","timeStamp":"1710000000","hash":"tx-hash",
                "nonce":"1","blockHash":"block-hash","from":"0xfrom","to":"0xto",
                "contractAddress":"0xtoken","value":"7","gas":"10",
                "gasPrice":"6","gasUsed":"7"
            }]
        }"""
        val api = RecordingApi { Gson().fromJson(quotedProviderJson, EtherscanHistoryResponse::class.java) }
        val tokenPage = EtherscanHistorySource(api.client, LEGACY_BSC_URL, "unified-key")
            .getOperations(25, null, TRANSFER_FILTER, ACCOUNT_ID, chain(BSCChainId), asset(ChainAssetType.BEP20), ADDRESS)
        val transfer = tokenPage.items.single().type as Operation.Type.Transfer
        assertEquals(BigInteger.valueOf(7), transfer.amount)
        assertEquals(BigInteger.valueOf(42), transfer.fee)

        val missingNativeStatus = runCatching {
            EtherscanHistorySource(api.client, LEGACY_BSC_URL, "unified-key")
                .getOperations(25, null, TRANSFER_FILTER, ACCOUNT_ID, chain(BSCChainId), asset(), ADDRESS)
        }
        assertTrue(missingNativeStatus.isFailure)
    }

    @Test
    fun `missing unified key and retired testnets fail before any request`() = runBlocking {
        val api = RecordingApi { response("0", "No transactions found", JsonParser.parseString("[]")) }
        val missingKey = runCatching {
            EtherscanHistorySource(api.client, LEGACY_BSC_URL, "")
                .getOperations(25, null, TRANSFER_FILTER, ACCOUNT_ID, chain(BSCChainId), asset(), ADDRESS)
        }
        assertTrue(missingKey.isFailure)
        listOf(goerliChainId, polygonTestnetChainId).forEach { retiredChain ->
            val result = runCatching {
                EtherscanHistorySource(api.client, LEGACY_BSC_URL, "unified-key")
                    .getOperations(25, null, TRANSFER_FILTER, ACCOUNT_ID, chain(retiredChain), asset(), ADDRESS)
            }
            assertTrue(result.exceptionOrNull() is HistoryNotSupportedException)
        }
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `wrong official host and transport failure cannot be mistaken for empty history`() = runBlocking {
        val api = RecordingApi { throw IllegalStateException("network unavailable") }
        val mismatchedHost = runCatching {
            EtherscanHistorySource(api.client, "https://api.polygonscan.com/api", "unified-key")
                .getOperations(25, null, TRANSFER_FILTER, ACCOUNT_ID, chain(BSCChainId), asset(), ADDRESS)
        }
        assertTrue(mismatchedHost.exceptionOrNull() is HistoryNotSupportedException)
        assertTrue(api.calls.isEmpty())

        val transportFailure = runCatching {
            EtherscanHistorySource(api.client, LEGACY_BSC_URL, "unified-key")
                .getOperations(25, null, TRANSFER_FILTER, ACCOUNT_ID, chain(BSCChainId), asset(), ADDRESS)
        }
        assertTrue(transportFailure.exceptionOrNull() is IllegalStateException)
        assertEquals(1, api.calls.size)
    }

    @Test
    fun `independent Etherscan-compatible explorers keep their own URL without V2 key`() = runBlocking {
        val api = RecordingApi { response("0", "No transactions found", JsonParser.parseString("[]")) }
        EtherscanHistorySource(api.client, OASYS_URL, "unified-key")
            .getOperations(25, null, TRANSFER_FILTER, ACCOUNT_ID, chain("248"), asset(), ADDRESS)

        assertEquals(OASYS_URL, api.calls.single()[0])
        assertEquals(null, api.calls.single()[1])
        assertEquals(null, api.calls.single()[9])
    }

    @Test
    fun `unreviewed chain IDs cannot use official endpoint or cleartext explorer`() = runBlocking {
        val api = RecordingApi { response("0", "No transactions found", JsonParser.parseString("[]")) }
        listOf(V2_URL, "http://explorer.oasys.games/api").forEach { url ->
            val result = runCatching {
                EtherscanHistorySource(api.client, url, "unified-key")
                    .getOperations(25, null, TRANSFER_FILTER, ACCOUNT_ID, chain("999999"), asset(), ADDRESS)
            }
            assertTrue(result.exceptionOrNull() is HistoryNotSupportedException)
        }
        assertTrue(api.calls.isEmpty())
    }

    private class RecordingApi(private val reply: () -> EtherscanHistoryResponse) {
        val calls = mutableListOf<List<Any?>>()
        val client: OperationsHistoryApi = mock(OperationsHistoryApi::class.java, Answer<Any?> { invocation ->
            check(invocation.method.name == "getEtherscanOperationsHistory")
            calls += invocation.arguments.toList()
            reply()
        })
    }

    private fun chain(id: String): Chain = mock(Chain::class.java).also { whenever(it.id).thenReturn(id) }

    private fun asset(type: ChainAssetType = ChainAssetType.Normal): Asset = mock(Asset::class.java).also {
        whenever(it.type).thenReturn(type)
        whenever(it.id).thenReturn(TOKEN)
    }

    private fun response(status: String, message: String, result: com.google.gson.JsonElement) =
        EtherscanHistoryResponse(status, message, result)

    private fun transaction(contract: String = "") = EtherscanHistoryElement(
        blockNumber = "100",
        timeStamp = 1_710_000_000,
        hash = "tx-hash",
        nonce = "1",
        blockHash = "block-hash",
        from = "0xfrom",
        to = "0xto",
        contractAddress = contract,
        value = BigInteger.valueOf(7),
        gas = BigInteger.valueOf(10),
        gasPrice = BigInteger.valueOf(6),
        gasUsed = BigInteger.valueOf(7),
        isError = 0
    )

    private companion object {
        const val V2_URL = "https://api.etherscan.io/v2/api"
        const val LEGACY_BSC_URL = "https://api.bscscan.com/api"
        const val OASYS_URL = "https://explorer.oasys.games/api"
        const val TOKEN = "0xtoken"
        const val ADDRESS = "0x0102"
        val ACCOUNT_ID = byteArrayOf(1, 2)
        val TRANSFER_FILTER = setOf(TransactionFilter.TRANSFER)
    }
}
