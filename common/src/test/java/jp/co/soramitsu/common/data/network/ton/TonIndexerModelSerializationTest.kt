package jp.co.soramitsu.common.data.network.ton

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TonIndexerModelSerializationTest {
    private val gson = Gson()

    @Test
    fun shouldRoundTripServiceMetadataAndCheckEveryIdentityConstraint() {
        val service = TonIndexerServiceInfo(1, "ti.soramitsu.io", "TON indexer", "ton", "ton:mainnet", "mainnet",
            "https://ti.soramitsu.io", true, listOf("account-balances"), mapOf("health" to "/health"))
        assertTrue(service.isExpectedTiServiceInfo())
        assertEquals(service, gson.fromJson(gson.toJson(service), TonIndexerServiceInfo::class.java))
        listOf(service.copy(schemaVersion = 2), service.copy(serviceId = "other"), service.copy(ecosystem = "evm"),
            service.copy(chainId = "ton:testnet"), service.copy(network = "testnet"),
            service.copy(publicBaseUrl = "https://untrusted.invalid"), service.copy(readOnly = false)).forEach {
            assertFalse(it.isExpectedTiServiceInfo())
        }
        val health = TonHealthStatus(123, 0.25, "ready")
        assertEquals(health, gson.fromJson(gson.toJson(health), TonHealthStatus::class.java))
        val contracts = TonContractsResponse("mainnet", 1, mapOf("router" to "address"))
        assertEquals(contracts, gson.fromJson(gson.toJson(contracts), TonContractsResponse::class.java))
        assertTrue(TonContractsResponse(count = 0).contracts.isEmpty())
    }

    @Test
    fun shouldPreserveConfirmedNativeAndJettonBalancesAsStrings() {
        val response = TonBalanceResponse(TonNativeBalance("99999999999999999999", "123", "hash"),
            listOf(TonJettonBalance("master", "wallet", "500", 9, "JET")), true, 1700, "mainnet")
        val wire = gson.toJsonTree(response).asJsonObject
        assertEquals("99999999999999999999", wire["ton"].asJsonObject["balance"].asString)
        assertEquals("123", wire["ton"].asJsonObject["last_tx_lt"].asString)
        assertEquals(response, gson.fromJson(wire, TonBalanceResponse::class.java))
        val balances = TonBalancesResponse("account", "123456789", "0.123456789",
            listOf(TonAssetBalance("jetton", "JET", "master", "wallet", "2500000", "2.5", 6)), true, 1701, "mainnet")
        assertEquals(balances, gson.fromJson(gson.toJson(balances), TonBalancesResponse::class.java))
        val state = TonAccountStateResponse("account", "5", "1", "hash", "active", "code", "data", 1702)
        assertEquals(state, gson.fromJson(gson.toJson(state), TonAccountStateResponse::class.java))
        val payload = TonJettonTransferPayloadResponse("payload", "init")
        assertEquals(payload, gson.fromJson(gson.toJson(payload), TonJettonTransferPayloadResponse::class.java))
        assertEquals(TonJettonTransferPayloadResponse(), gson.fromJson("{}", TonJettonTransferPayloadResponse::class.java))
    }

    @Test
    fun shouldRetainPagingMessagesAndTransferMetadata() {
        val message = TonMessageSummary("sender", "recipient", "99999999999999999", 0x1234, "body")
        val entry = TonTransactionEntry("id", 1700, "failed", "reason", "transfer", "sender", "100", 1,
            mapOf("amount" to "100"), "ton", listOf(mapOf("type" to "TonTransfer")), "99999999999999999", "hash", message, listOf(message))
        val page = TonTransactionsResponse(2, 10, 15, 2, 2, true, listOf(entry), "mainnet")
        val wire = gson.toJsonTree(page).asJsonObject
        assertEquals(2, wire["total_pages_min"].asInt)
        assertTrue(wire["history_complete"].asBoolean)
        assertEquals(page, gson.fromJson(wire, TonTransactionsResponse::class.java))
        assertEquals(TonMessageSummary(), gson.fromJson("{}", TonMessageSummary::class.java))
    }

    @Test
    fun shouldPreserveSwapExecutionAndBatchRunGetFailures() {
        val swap = TonSwapExecution("id", "lt", "hash", 1700, "failed", "slippage", "TON", "JET", "1000", "2000", "42", "router")
        val swaps = TonSwapsResponse("account", listOf(swap), 1, "mainnet")
        assertEquals(swaps, gson.fromJson(gson.toJson(swaps), TonSwapsResponse::class.java))
        val call = TonRunGetMethodRequest("account", "seqno", listOf(listOf("num", "0x1")))
        val request = TonRunGetMethodsRequest(listOf(call))
        assertEquals(request, gson.fromJson(gson.toJson(request), TonRunGetMethodsRequest::class.java))
        val single = TonRunGetMethodResponse(0, 123, listOf(listOf("num", "0xff")))
        assertEquals(single, gson.fromJson(gson.toJson(single), TonRunGetMethodResponse::class.java))
        val results = TonRunGetMethodsResponse(listOf(TonRunGetMethodBatchResult(true, 0, 123, single.stack),
            TonRunGetMethodBatchResult(false, 32, 456, code = "VM_ERROR", error = "out of gas")))
        assertEquals(results, gson.fromJson(gson.toJson(results), TonRunGetMethodsResponse::class.java))
        assertTrue(TonRunGetMethodRequest("account", "seqno").stack.isEmpty())
        assertTrue(TonRunGetMethodsResponse().results.isEmpty())
    }
}
