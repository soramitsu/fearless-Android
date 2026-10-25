package jp.co.soramitsu.common.wallet

import jp.co.soramitsu.common.model.UniversalWalletRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import jp.co.soramitsu.common.data.network.solana.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SolanaRpcPreflightTest {
    @Test
    fun `preflight reads native balance account owner token accounts height priority samples and signature history from rpc`() = runTest {
        val calls = mutableListOf<JsonObject>()
        val api = object : SolanaRpcApi {
            override suspend fun postJsonRpc(url: String, body: JsonObject): JsonObject {
                if (body["method"].asString == "getGenesisHash") {
                    return JsonParser.parseString("""{"jsonrpc":"2.0","id":${body["id"]},"result":"${UniversalWalletRegistry.SOLANA_MAINNET_GENESIS_HASH}"}""").asJsonObject
                }
                calls += body
                assertEquals("http://127.0.0.1:8899", url)
                val value = when (body["method"].asString) {
                    "getBalance" -> """{"context":{"slot":12},"value":2000000000}"""
                    "getAccountInfo" -> """{"context":{"slot":12},"value":{"owner":"$SYSTEM","lamports":2000000000,"executable":false,"data":["","base64"]}}"""
                    "getTokenAccountsByOwner" -> """{"context":{"slot":12},"value":[]}"""
                    "getBlockHeight" -> "1234"
                    "getRecentPrioritizationFees" -> """[{"slot":11,"prioritizationFee":25},{"slot":12,"prioritizationFee":50}]"""
                    "getSignatureStatuses" -> """{"context":{"slot":12},"value":[{"slot":11,"confirmations":null,"confirmationStatus":"finalized","err":null}]}"""
                    else -> error("Unexpected RPC")
                }
                return JsonParser.parseString("""{"jsonrpc":"2.0","id":${body["id"]},"result":$value}""").asJsonObject
            }
        }
        val client = RetrofitSolanaRpcClient(api, "http://127.0.0.1:8899")
        assertEquals(2000000000L, client.balance(WALLET))
        assertEquals(SYSTEM, client.accountInfo(WALLET)!!.ownerProgram)
        assertEquals(emptyList<SolanaRpcTokenAccount>(), client.tokenAccounts(WALLET, SolanaTransferTransactionBuilder.SPL_TOKEN_PROGRAM_ADDRESS))
        assertEquals(1234L, client.blockHeight())
        assertEquals(listOf(25L, 50L), client.recentPrioritizationFees())
        val status = client.signatureStatus("1".repeat(64))!!
        assertTrue(status.isConfirmed)
        assertEquals(null, status.errorJson)
        assertTrue(calls.last()["params"].asJsonArray[1].asJsonObject["searchTransactionHistory"].asBoolean)
        assertEquals(165, calls[1]["params"].asJsonArray[1].asJsonObject["dataSlice"].asJsonObject["length"].asInt)
        assertEquals(listOf(1L,3L,5L,7L,9L,11L), calls.map { it["id"].asLong })
    }

    @Test
    fun `processed signatures never settle while confirmed legacy or finalized observations do`() {
        assertFalse(SolanaRpcSignatureStatus(1, "processed", 0, null).isConfirmed)
        assertFalse(SolanaRpcSignatureStatus(1, null, null, null).isConfirmed)
        assertTrue(SolanaRpcSignatureStatus(1, "confirmed", 1, null).isConfirmed)
        assertTrue(SolanaRpcSignatureStatus(1, null, 2, null).isConfirmed)
    }

    @Test
    fun `missing account and signature are explicit absence while malformed scalar balances fail closed`() = runTest {
        var result = """{"context":{"slot":12},"value":null}"""
        val client = RetrofitSolanaRpcClient(object : SolanaRpcApi {
            override suspend fun postJsonRpc(url: String, body: JsonObject): JsonObject {
                val value = if (body["method"].asString == "getGenesisHash") "\"${UniversalWalletRegistry.SOLANA_MAINNET_GENESIS_HASH}\"" else result
                return JsonParser.parseString("""{"jsonrpc":"2.0","id":${body["id"]},"result":$value}""").asJsonObject
            }
        }, "http://127.0.0.1:8899")
        assertEquals(null, client.accountInfo(WALLET))
        result = """{"context":{"slot":12},"value":[null]}"""
        assertEquals(null, client.signatureStatus("1".repeat(64)))
        for (number in listOf("-1", "1.5", "9223372036854775808", "\"123\"")) {
            result = """{"context":{"slot":12},"value":$number}"""
            try { client.balance(WALLET); error("Expected invalid RPC balance") }
            catch (error: SolanaRpcException) { assertEquals(SolanaRpcException.Code.INVALID_BALANCE_RESPONSE, error.code) }
        }
    }

    private companion object {
        const val SYSTEM = "11111111111111111111111111111111"
        const val WALLET = "AKnL4NNf3DGWZJS6cPknBuEGnVsV4A4m5tgebLHaRSZ9"
    }
}
