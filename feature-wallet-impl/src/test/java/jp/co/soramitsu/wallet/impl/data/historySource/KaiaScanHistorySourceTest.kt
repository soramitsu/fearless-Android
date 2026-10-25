package jp.co.soramitsu.wallet.impl.data.historySource

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.testshared.whenever
import jp.co.soramitsu.wallet.impl.data.network.subquery.OperationsHistoryApi
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.stubbing.Answer

class KaiaScanHistorySourceTest {
    @Test
    fun `mainnet native request binds host account bearer and exact fee units`() = runBlocking {
        val api = RecordingApi { json(page(nativeRow(), totalCount = 2, totalPage = 2, last = false)) }
        val result = api.source().getOperations(2000, null, FILTER, ACCOUNT_ID, chain("8217"), nativeAsset(), ADDRESS)

        assertEquals("https://mainnet-oapi.kaiascan.io/api/v1/accounts/$ADDRESS/transactions", api.arguments.single()[0])
        assertEquals("Bearer test-key", api.arguments.single()[1])
        assertEquals(1, api.arguments.single()[2])
        assertEquals(500, api.arguments.single()[3])
        assertEquals("2", result.nextCursor)
        val transfer = result.items.single().type as Operation.Type.Transfer
        assertEquals(BigInteger("1000000000000000001"), transfer.amount)
        assertEquals(BigInteger("21"), transfer.fee)
        assertEquals(Operation.Status.COMPLETED, transfer.status)
        assertEquals(1_710_000_000_000L, result.items.single().time)
    }

    @Test
    fun `kairos token request binds contract and has no fabricated network fee`() = runBlocking {
        val api = RecordingApi { json(page(tokenRow(), current = 2, totalCount = 2, totalPage = 2)) }
        val result = api.source(KAIROS_URL).getOperations(20, "2", FILTER, ACCOUNT_ID, chain("1001"), tokenAsset(), ADDRESS)

        assertEquals("https://kairos-oapi.kaiascan.io/api/v1/accounts/$ADDRESS/token-transfers", api.arguments.single()[0])
        assertEquals("Bearer test-key", api.arguments.single()[1])
        assertEquals(TOKEN, api.arguments.single()[2])
        assertEquals(2, api.arguments.single()[3])
        assertEquals(20, api.arguments.single()[4])
        assertEquals(null, result.nextCursor)
        val transfer = result.items.single().type as Operation.Type.Transfer
        assertEquals(BigInteger("1234567"), transfer.amount)
        assertEquals(null, transfer.fee)
        assertEquals(Operation.Status.COMPLETED, transfer.status)
    }

    @Test
    fun `retired exact Scope URL reroutes to mainnet but untrusted endpoints never get bearer`() = runBlocking {
        val accepted = RecordingApi { json(page(nativeRow())) }
        accepted.source("https://scope.klaytn.com/api/v1").getOperations(20, null, FILTER, ACCOUNT_ID, chain("8217"), nativeAsset(), ADDRESS)
        assertTrue((accepted.arguments.single()[0] as String).startsWith("https://mainnet-oapi.kaiascan.io/"))
        val alternate = RecordingApi { json(page(nativeRow())) }
        alternate.source("https://scope.kaia.io/api/v1").getOperations(20, null, FILTER, ACCOUNT_ID, chain("8217"), nativeAsset(), ADDRESS)
        assertTrue((alternate.arguments.single()[0] as String).startsWith("https://mainnet-oapi.kaiascan.io/"))

        val rejectedUrls = listOf(
            "https://scope.klaytn.com.evil.example/api/v1",
            "https://scope.kaia.io.evil.example/api/v1",
            "https://mainnet-oapi.kaiascan.io:8443/api/v1",
            "https://user@mainnet-oapi.kaiascan.io/api/v1",
            "http://mainnet-oapi.kaiascan.io/api/v1",
            "https://mainnet-oapi.kaiascan.io/api/v1?other=1",
            KAIROS_URL
        )
        rejectedUrls.forEach { url ->
            val api = RecordingApi { json(page(nativeRow())) }
            val result = runCatching { api.source(url).getOperations(20, null, FILTER, ACCOUNT_ID, chain("8217"), nativeAsset(), ADDRESS) }
            assertTrue(result.isFailure)
            assertTrue(api.arguments.isEmpty())
        }
    }

    @Test
    fun `missing key wrong chain and account mismatch fail before network`() = runBlocking {
        val api = RecordingApi { json(page(nativeRow())) }
        val missing = runCatching { api.source(key = "").getOperations(20, null, FILTER, ACCOUNT_ID, chain("8217"), nativeAsset(), ADDRESS) }
        val wrongChain = runCatching { api.source().getOperations(20, null, FILTER, ACCOUNT_ID, chain("1"), nativeAsset(), ADDRESS) }
        val wrongAccount = runCatching { api.source().getOperations(20, null, FILTER, ACCOUNT_ID, chain("8217"), nativeAsset(), OTHER) }
        assertTrue(missing.isFailure)
        assertTrue(wrongChain.isFailure)
        assertTrue(wrongAccount.isFailure)
        assertTrue(api.arguments.isEmpty())
    }

    @Test
    fun `provider transport error missing fields and stale page fail closed`() = runBlocking {
        val transport = RecordingApi { throw IllegalStateException("offline") }
        assertTrue(runCatching { transport.source().getOperations(20, null, FILTER, ACCOUNT_ID, chain("8217"), nativeAsset(), ADDRESS) }.isFailure)

        val malformed = listOf("{}", "{\"results\":[]}", page(nativeRow(), current = 2), page(last = false, totalPage = 2))
        malformed.forEach { body ->
            val api = RecordingApi { json(body) }
            assertTrue(runCatching { api.source().getOperations(20, null, FILTER, ACCOUNT_ID, chain("8217"), nativeAsset(), ADDRESS) }.isFailure)
        }
    }

    @Test
    fun `empty completed page is valid and invalid cursor is rejected before network`() = runBlocking {
        val api = RecordingApi { json(page()) }
        val result = api.source().getOperations(20, null, FILTER, ACCOUNT_ID, chain("8217"), nativeAsset(), ADDRESS)
        assertTrue(result.items.isEmpty())
        assertEquals(null, result.nextCursor)
        val invalid = runCatching { api.source().getOperations(20, "01", FILTER, ACCOUNT_ID, chain("8217"), nativeAsset(), ADDRESS) }
        assertTrue(invalid.isFailure)
        assertEquals(1, api.arguments.size)
    }

    @Test
    fun `fee payer only row never becomes an incoming transfer`() = runBlocking {
        val api = RecordingApi { json(page(nativeRow(from = OTHER, to = THIRD, feePayer = ADDRESS))) }
        val result = api.source().getOperations(20, null, FILTER, ACCOUNT_ID, chain("8217"), nativeAsset(), ADDRESS)
        assertTrue(result.items.isEmpty())
        assertEquals(null, result.nextCursor)
    }

    @Test
    fun `wrong token contract unrelated account and imprecise values fail closed`() = runBlocking {
        val tokenBodies = listOf(
            page(tokenRow(contract = THIRD)),
            page(tokenRow(from = OTHER, to = THIRD)),
            page(tokenRow(amount = "0.0000001")),
            page(tokenRow(amount = "-1"))
        )
        tokenBodies.forEach { body ->
            val api = RecordingApi { json(body) }
            assertTrue(runCatching { api.source().getOperations(20, null, FILTER, ACCOUNT_ID, chain("8217"), tokenAsset(), ADDRESS) }.isFailure)
        }
        val badFee = RecordingApi { json(page(nativeRow(fee = "0.0000000000000000001"))) }
        assertTrue(runCatching { badFee.source().getOperations(20, null, FILTER, ACCOUNT_ID, chain("8217"), nativeAsset(), ADDRESS) }.isFailure)
        val hugeExponent = RecordingApi { json(page(nativeRow(amount = "1e1000000"))) }
        assertTrue(runCatching { hugeExponent.source().getOperations(20, null, FILTER, ACCOUNT_ID, chain("8217"), nativeAsset(), ADDRESS) }.isFailure)
    }

    @Test
    fun `unknown native status and nonterminal unrepresentable page fail closed`() = runBlocking {
        val unknownStatus = RecordingApi { json(page(nativeRow(status = "Pending"))) }
        assertTrue(runCatching { unknownStatus.source().getOperations(20, null, FILTER, ACCOUNT_ID, chain("8217"), nativeAsset(), ADDRESS) }.isFailure)

        val onlySponsor = RecordingApi { json(page(nativeRow(from = OTHER, to = THIRD, feePayer = ADDRESS), totalCount = 2, totalPage = 2, last = false)) }
        assertTrue(runCatching { onlySponsor.source().getOperations(20, null, FILTER, ACCOUNT_ID, chain("8217"), nativeAsset(), ADDRESS) }.isFailure)
    }

    private class RecordingApi(private val reply: () -> JsonObject) {
        val arguments = mutableListOf<List<Any?>>()
        val client: OperationsHistoryApi = mock(OperationsHistoryApi::class.java, Answer<Any?> { invocation ->
            check(invocation.method.name in setOf("getKaiaScanNativeHistory", "getKaiaScanTokenHistory"))
            arguments.add(invocation.arguments.toList())
            reply()
        })

        fun source(url: String = MAINNET_URL, key: String = "test-key") = KaiaScanHistorySource(client, url, key)
    }

    private fun chain(id: String): Chain = mock(Chain::class.java).also { whenever(it.id).thenReturn(id) }

    private fun nativeAsset(): Asset = mock(Asset::class.java).also {
        whenever(it.type).thenReturn(ChainAssetType.Normal)
        whenever(it.isUtility).thenReturn(true)
        whenever(it.precision).thenReturn(18)
    }

    private fun tokenAsset(): Asset = mock(Asset::class.java).also {
        whenever(it.type).thenReturn(ChainAssetType.ERC20)
        whenever(it.id).thenReturn(TOKEN)
        whenever(it.precision).thenReturn(6)
    }

    private fun page(
        vararg rows: String,
        current: Int = if (rows.isEmpty()) 0 else 1,
        totalCount: Int = rows.size,
        totalPage: Int = if (rows.isEmpty()) 0 else 1,
        last: Boolean = true
    ) = """{"results":[${rows.joinToString()}],"paging":{"total_count":$totalCount,"current_page":$current,"total_page":$totalPage,"last":$last}}"""

    private fun nativeRow(
        from: String = ADDRESS,
        to: String = OTHER,
        feePayer: String = from,
        amount: String = "1.000000000000000001",
        fee: String = "0.000000000000000021",
        status: String = "Success"
    ) = """{"transaction_hash":"$TX_HASH","transaction_index":0,"block_id":1,"datetime":"2024-03-09T16:00:00Z","from":"$from","to":"$to","fee_payer":"$feePayer","transaction_type":"Value Transfer","amount":$amount,"transaction_fee":$fee,"effective_gas_price":1,"status":{"status":"$status"}}"""

    private fun tokenRow(
        contract: String = TOKEN,
        from: String = ADDRESS,
        to: String = OTHER,
        amount: String = "1.234567"
    ) = """{"contract":{"contract_address":"$contract","contract_type":"ERC20"},"block_id":1,"transaction_hash":"$TX_HASH","datetime":"2024-03-09T16:00:00Z","from":"$from","to":"$to","amount":$amount}"""

    private fun json(value: String) = JsonParser.parseString(value).asJsonObject

    private companion object {
        const val MAINNET_URL = "https://mainnet-oapi.kaiascan.io/api/v1"
        const val KAIROS_URL = "https://kairos-oapi.kaiascan.io/api/v1"
        const val ADDRESS = "0x1111111111111111111111111111111111111111"
        const val OTHER = "0x2222222222222222222222222222222222222222"
        const val THIRD = "0x3333333333333333333333333333333333333333"
        const val TOKEN = "0x4444444444444444444444444444444444444444"
        const val TX_HASH = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val ACCOUNT_ID = ByteArray(20) { 0x11 }
        val FILTER = setOf(TransactionFilter.TRANSFER)
    }
}
