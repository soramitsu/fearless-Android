package jp.co.soramitsu.account.impl.domain

import jp.co.soramitsu.common.model.UniversalWalletRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import jp.co.soramitsu.common.data.network.solana.SolanaRpcApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeNodeConnectionCheckTest {
    @Test fun `checks only the requested endpoint with a public cluster identity read`() = runBlocking {
        val calls = mutableListOf<Pair<String, JsonObject>>()
        val check = NativeNodeConnectionCheck(object : SolanaRpcApi {
            override suspend fun postJsonRpc(url: String, body: JsonObject): JsonObject {
                calls += url to body
                return valid()
            }
        })
        val endpoint = "https://rpc.example.invalid/Case%2fPath?api-key=public-fixture&part=%2F&part=%2f"
        check.checkSolana(endpoint, UniversalWalletRegistry.SOLANA_MAINNET_GENESIS_HASH)
        assertEquals(endpoint, calls.single().first)
        assertEquals(setOf("jsonrpc", "id", "method", "params"), calls.single().second.keySet())
        assertEquals("getGenesisHash", calls.single().second["method"].asString)
        assertEquals(0, calls.single().second["params"].asJsonArray.size())
    }

    @Test fun `invalid endpoints never reach the API and retain existing local development policy`() = runBlocking {
        val endpoints = mutableListOf<String>()
        val check = NativeNodeConnectionCheck(object : SolanaRpcApi {
            override suspend fun postJsonRpc(url: String, body: JsonObject): JsonObject {
                endpoints += url; return valid()
            }
        })
        for (invalid in listOf("wss://rpc.example.invalid", "http://rpc.example.invalid", "https://user:password@rpc.example.invalid", "file:///tmp/rpc",
            " https://rpc.example.invalid/path?api-key=public-fixture",
            "https://rpc.example.invalid/path?api-key=public-fixture ",
            "https://rpc.example.invalid/path?api-key=public-fixture#unused",
            " https://rpc.example.invalid/path?api-key=public-fixture#unused "
        )) {
            assertTrue(runCatching { check.checkSolana(invalid, UniversalWalletRegistry.SOLANA_MAINNET_GENESIS_HASH) }.isFailure)
        }
        assertTrue(endpoints.isEmpty())
        check.checkSolana("http://localhost:8899", UniversalWalletRegistry.SOLANA_MAINNET_GENESIS_HASH)
        assertEquals(listOf("http://localhost:8899"), endpoints)
    }

    @Test fun `RPC errors wrong response identity and malformed genesis are rejected without fallback`() = runBlocking {
        for (payload in listOf(
            "{}", "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"solana-core\":\"2.1.13\"}}",
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32005},\"result\":{\"solana-core\":\"2.1.13\"}}",
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"solana-core\":12}}",
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"solana-core\":\"\"}}"
        )) {
            var calls = 0
            val check = NativeNodeConnectionCheck(object : SolanaRpcApi {
                override suspend fun postJsonRpc(url: String, body: JsonObject): JsonObject {
                    calls++; return JsonParser.parseString(payload).asJsonObject
                }
            })
            assertTrue(payload, runCatching { check.checkSolana("https://rpc.example.invalid", UniversalWalletRegistry.SOLANA_MAINNET_GENESIS_HASH) }.isFailure)
            assertEquals(1, calls)
        }
    }

    @Test fun `cancellation propagates without changing endpoint or retrying`() = runBlocking {
        var calls = 0
        val check = NativeNodeConnectionCheck(object : SolanaRpcApi {
            override suspend fun postJsonRpc(url: String, body: JsonObject): JsonObject {
                calls++; throw CancellationException("cancelled")
            }
        })
        assertTrue(runCatching { check.checkSolana("https://rpc.example.invalid", UniversalWalletRegistry.SOLANA_MAINNET_GENESIS_HASH) }.exceptionOrNull() is CancellationException)
        assertEquals(1, calls)
    }

    private fun valid() = JsonObject().apply {
        addProperty("jsonrpc", "2.0"); addProperty("id", 1)
        addProperty("result", UniversalWalletRegistry.SOLANA_MAINNET_GENESIS_HASH)
    }
}
