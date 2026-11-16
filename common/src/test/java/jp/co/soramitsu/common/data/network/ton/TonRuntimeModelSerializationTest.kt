package jp.co.soramitsu.common.data.network.ton

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TonRuntimeModelSerializationTest {
    private val gson = Gson()

    @Test
    fun shouldPreserveRawBalancesWithoutRoundingOrInventingMissingAmounts() {
        val asset = TonIndexerAssetBalance("jetton", "JET", "master", "wallet", "999999999999999999999", 9)
        val response = TonIndexerBalancesResponse("account", "12345678901234567890", listOf(asset), 1234, "mainnet")
        val json = gson.toJsonTree(response).asJsonObject
        assertEquals("12345678901234567890", json["ton_raw"].asString)
        assertEquals("999999999999999999999", json["assets"].asJsonArray[0].asJsonObject["balance_raw"].asString)
        assertEquals(response, gson.fromJson(json, TonIndexerBalancesResponse::class.java))
        val missing = gson.fromJson("{}", TonIndexerBalancesResponse::class.java)
        assertNull(missing.tonRaw)
        assertTrue(missing.assets.isEmpty())
        assertNull(gson.fromJson("{}", TonIndexerAssetBalance::class.java).balanceRaw)
        assertEquals(TonIndexerBalancesResponse(), missing)
    }

    @Test
    fun shouldAcceptStateAliasesAndIgnoreIndexerSequenceNumbers() {
        val expected = TonIndexerStateResponse("account", "active", 1700, "mainnet")
        listOf("account_state" to "last_seen_utime", "accountState" to "lastSeenUtime").forEach { (state, time) ->
            val json = """{"address":"account","$state":"active","$time":1700,"network":"mainnet","last_confirmed_seqno":999}"""
            assertEquals(expected, gson.fromJson(json, TonIndexerStateResponse::class.java))
        }
        val wire = gson.toJsonTree(expected).asJsonObject
        assertTrue(wire.has("account_state"))
        assertFalse(wire.has("last_confirmed_seqno"))
        assertEquals(TonIndexerStateResponse(), gson.fromJson("{}", TonIndexerStateResponse::class.java))
    }

    @Test
    fun shouldKeepRunGetMethodStackShapeAndExitStatus() {
        val request = TonIndexerRunGetMethodRequest("account", "seqno", listOf(listOf("num", "0x10"), listOf("null", null)))
        assertEquals(request, gson.fromJson(gson.toJson(request), TonIndexerRunGetMethodRequest::class.java))
        val response = TonIndexerRunGetMethodResponse(32, 123456, listOf(listOf("num", "0xff")))
        val wire = gson.toJsonTree(response).asJsonObject
        assertEquals(32, wire["exit_code"].asInt)
        assertEquals(123456, wire["gas_used"].asLong)
        assertEquals(response, gson.fromJson(wire, TonIndexerRunGetMethodResponse::class.java))
        assertEquals(emptyList<List<Any?>>(), TonIndexerRunGetMethodRequest("account", "seqno").stack)
        assertEquals(TonIndexerRunGetMethodResponse(), gson.fromJson("{}", TonIndexerRunGetMethodResponse::class.java))
    }

    @Test
    fun shouldRoundTripTransactionMessagesFeesAndIncompleteHistory() {
        val input = TonIndexerMessage("source", "account", "1000000000")
        val output = TonIndexerMessage("account", "recipient", "900000000")
        val detail = TonIndexerTransactionDetail("transfer", "TON", "900000000")
        val transaction = TonIndexerTransaction("id", 1711, "failed", "no gas", "ordinary", "source", "1000000000",
            detail, "12345678901234567890", "hash", "4000", "5000", input, listOf(output))
        val page = TonIndexerTransactionsResponse(2, 20, 21, 2, false, listOf(transaction), "mainnet")
        val wire = gson.toJsonTree(page).asJsonObject
        assertEquals(20, wire["page_size"].asInt)
        assertFalse(wire["history_complete"].asBoolean)
        assertEquals("12345678901234567890", wire["txs"].asJsonArray[0].asJsonObject["lt"].asString)
        assertEquals(page, gson.fromJson(wire, TonIndexerTransactionsResponse::class.java))
        assertEquals(TonIndexerTransactionsResponse(), gson.fromJson("{}", TonIndexerTransactionsResponse::class.java))
        assertEquals(TonIndexerTransaction(), gson.fromJson("{}", TonIndexerTransaction::class.java))
        assertEquals(TonIndexerTransactionDetail(), gson.fromJson("{}", TonIndexerTransactionDetail::class.java))
        assertEquals(TonIndexerMessage(), gson.fromJson("{}", TonIndexerMessage::class.java))
    }

    @Test
    fun shouldPreserveJsonRpcIdsErrorsAndNullParameters() {
        val request = TonIndexerJsonRpcRequest(method = "runGetMethod", params = mapOf("address" to "account"))
        val wire = gson.toJsonTree(request).asJsonObject
        assertEquals(1, wire["id"].asInt)
        assertEquals("2.0", wire["jsonrpc"].asString)
        assertEquals(request, gson.fromJson(wire, TonIndexerJsonRpcRequest::class.java))
        val error = TonIndexerJsonRpcError(-32000, "unavailable", JsonParser.parseString("{\"retry\":true}"))
        val response = TonIndexerJsonRpcResponse(JsonParser.parseString("\"request-7\""), "2.0", null, error)
        assertEquals(response, gson.fromJson(gson.toJson(response), TonIndexerJsonRpcResponse::class.java))
        val success = response.copy(error = null, result = JsonParser.parseString("[\"0x10\"]"))
        assertEquals(success, gson.fromJson(gson.toJson(success), TonIndexerJsonRpcResponse::class.java))
        assertEquals(TonIndexerJsonRpcResponse(), gson.fromJson("{}", TonIndexerJsonRpcResponse::class.java))
        assertEquals(TonIndexerJsonRpcError(), gson.fromJson("{}", TonIndexerJsonRpcError::class.java))
        assertTrue(TonIndexerJsonRpcRequest(method = "getStatus").params.isEmpty())
    }
}
