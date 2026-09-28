package jp.co.soramitsu.common.wallet

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import jp.co.soramitsu.common.data.network.solana.RetrofitSolanaRpcClient
import jp.co.soramitsu.common.data.network.solana.SolanaBroadcastOptions
import jp.co.soramitsu.common.data.network.solana.SolanaRpcApi
import jp.co.soramitsu.common.data.network.solana.SolanaRpcCommitment
import jp.co.soramitsu.common.data.network.solana.SolanaRpcException
import jp.co.soramitsu.common.data.network.solana.SolanaRpcRoutes
import jp.co.soramitsu.common.data.network.solana.SolanaSimulationOptions
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SolanaRpcClientTest {

    @Test
    fun `posts validated json rpc requests to configured solana rpc endpoints`() = runBlocking {
        val api = FakeSolanaRpcApi()
        val client = RetrofitSolanaRpcClient(api, defaultRpcUrl = DEVNET_RPC)

        val blockhash = client.latestBlockhash(SolanaRpcCommitment.Finalized)
        val fee = client.feeForMessage(TRANSACTION, SolanaRpcCommitment.Confirmed)
        val rent = client.minimumBalanceForRentExemption(165, SolanaRpcCommitment.Processed)
        val accountExists = client.accountExists(ACCOUNT_ADDRESS, SolanaRpcCommitment.Finalized)
        val simulation = client.simulateTransaction(TRANSACTION, SolanaSimulationOptions(commitment = SolanaRpcCommitment.Processed))
        val signature = client.sendRawTransaction(
            TRANSACTION,
            SolanaBroadcastOptions(
                maxRetries = 3,
                preflightCommitment = SolanaRpcCommitment.Confirmed,
                skipPreflight = true
            )
        )

        assertEquals(BLOCKHASH, blockhash.value.blockhash)
        assertEquals(456L, blockhash.value.lastValidBlockHeight)
        assertEquals(5000L, fee.value)
        assertEquals(890_880L, rent)
        assertEquals(true, accountExists)
        assertEquals(listOf("Program log: ok"), simulation.value.logs)
        assertEquals(BLOCKHASH, simulation.value.replacementBlockhash?.blockhash)
        assertEquals(321L, simulation.value.unitsConsumed)
        assertEquals(SIGNATURE, signature)
        assertEquals(
            listOf(
                "getLatestBlockhash",
                "getFeeForMessage",
                "getMinimumBalanceForRentExemption",
                "getAccountInfo",
                "simulateTransaction",
                "sendTransaction"
            ),
            api.bodies.map { it["method"].asString }
        )
        assertEquals(List(6) { DEVNET_RPC }, api.urls)
        assertEquals("finalized", api.bodies[0]["params"].asJsonArray[0].asJsonObject["commitment"].asString)
        assertEquals(TRANSACTION, api.bodies[1]["params"].asJsonArray[0].asString)
        assertEquals(165, api.bodies[2]["params"].asJsonArray[0].asInt)
        assertEquals(ACCOUNT_ADDRESS, api.bodies[3]["params"].asJsonArray[0].asString)
        assertEquals("base64", api.bodies[3]["params"].asJsonArray[1].asJsonObject["encoding"].asString)
        assertEquals(false, api.bodies[4]["params"].asJsonArray[1].asJsonObject["sigVerify"].asBoolean)
        assertEquals(3, api.bodies[5]["params"].asJsonArray[1].asJsonObject["maxRetries"].asInt)
    }

    @Test
    fun `rejects unsafe urls malformed payloads and invalid options before fetch`() {
        assertRouteError(SolanaRpcException.Code.INVALID_RPC_URL) {
            SolanaRpcRoutes.normalizeRpcUrl("http://api.mainnet-beta.solana.com")
        }
        assertEquals("http://localhost:8899", SolanaRpcRoutes.normalizeRpcUrl("http://localhost:8899/?api-key=secret#frag"))

        val api = FakeSolanaRpcApi()
        val client = RetrofitSolanaRpcClient(api)

        assertRpcError(SolanaRpcException.Code.INVALID_TRANSACTION) {
            runBlocking { client.feeForMessage("not base64") }
        }
        assertRpcError(SolanaRpcException.Code.INVALID_DATA_LENGTH) {
            runBlocking { client.minimumBalanceForRentExemption(-1) }
        }
        assertRpcError(SolanaRpcException.Code.INVALID_DATA_LENGTH) {
            runBlocking { client.minimumBalanceForRentExemption(10_000_001) }
        }
        assertRpcError(SolanaRpcException.Code.INVALID_ACCOUNT_ADDRESS) {
            runBlocking { client.accountExists("not-base58") }
        }
        assertRpcError(SolanaRpcException.Code.INVALID_SIMULATION_OPTIONS) {
            runBlocking {
                client.simulateTransaction(
                    TRANSACTION,
                    SolanaSimulationOptions(replaceRecentBlockhash = true, sigVerify = true)
                )
            }
        }
        assertRpcError(SolanaRpcException.Code.INVALID_MAX_RETRIES) {
            runBlocking { client.sendRawTransaction(TRANSACTION, SolanaBroadcastOptions(maxRetries = 11)) }
        }
        assertEquals(0, api.bodies.size)
    }

    @Test
    fun `maps rpc errors malformed envelopes and bad result shapes to typed exceptions`() {
        runBlocking {
            assertRpcError(SolanaRpcException.Code.RPC_ERROR) {
                runBlocking {
                    RetrofitSolanaRpcClient(
                        FakeSolanaRpcApi(JsonParser.parseString("""{"jsonrpc":"2.0","id":1,"error":{"code":-32002,"message":"simulation failed","data":{"logs":[]}}}""").asJsonObject)
                    ).latestBlockhash()
                }
            }.also {
                assertEquals(-32002, it.rpcError?.code)
                assertEquals("simulation failed", it.rpcError?.message)
                assertEquals("""{"logs":[]}""", it.rpcError?.dataJson)
            }

            assertRpcError(SolanaRpcException.Code.INVALID_RPC_RESPONSE) {
                runBlocking {
                    RetrofitSolanaRpcClient(
                        FakeSolanaRpcApi(JsonParser.parseString("""{"jsonrpc":"2.0","id":2,"result":{}}""").asJsonObject)
                    ).latestBlockhash()
                }
            }

            assertRpcError(SolanaRpcException.Code.INVALID_SIMULATION_RESPONSE) {
                runBlocking {
                    RetrofitSolanaRpcClient(
                        FakeSolanaRpcApi(rpcResponse(1, """{"context":{"slot":1},"value":{"err":null,"logs":[1]}}"""))
                    ).simulateTransaction(TRANSACTION)
                }
            }

            assertRpcError(SolanaRpcException.Code.INVALID_FEE_RESPONSE) {
                runBlocking {
                    RetrofitSolanaRpcClient(FakeSolanaRpcApi(rpcResponse(1, """{"context":{"slot":1},"value":-1}""")))
                        .feeForMessage(TRANSACTION)
                }
            }

            val unavailableFee = RetrofitSolanaRpcClient(FakeSolanaRpcApi(rpcResponse(1, """{"context":{"slot":1},"value":null}""")))
                .feeForMessage(TRANSACTION)
            assertNull(unavailableFee.value)

            assertRpcError(SolanaRpcException.Code.INVALID_RENT_RESPONSE) {
                runBlocking {
                    RetrofitSolanaRpcClient(FakeSolanaRpcApi(rpcResponse(1, """"890880"""")))
                        .minimumBalanceForRentExemption(165)
                }
            }

            val missingAccount = RetrofitSolanaRpcClient(
                FakeSolanaRpcApi(rpcResponse(1, """{"context":{"slot":1},"value":null}"""))
            ).accountExists(ACCOUNT_ADDRESS)
            assertEquals(false, missingAccount)

            assertRpcError(SolanaRpcException.Code.INVALID_ACCOUNT_INFO_RESPONSE) {
                runBlocking {
                    RetrofitSolanaRpcClient(
                        FakeSolanaRpcApi(rpcResponse(1, """{"context":{"slot":1},"value":[]}"""))
                    ).accountExists(ACCOUNT_ADDRESS)
                }
            }

            assertRpcError(SolanaRpcException.Code.INVALID_SIGNATURE_RESPONSE) {
                runBlocking {
                    RetrofitSolanaRpcClient(FakeSolanaRpcApi(rpcResponse(1, """"not-a-signature"""")))
                        .sendRawTransaction(TRANSACTION)
                }
            }
        }
    }

    @Test
    fun `accepts only signed int range rpc error codes`() {
        val cases = listOf(
            Int.MIN_VALUE.toString() to Int.MIN_VALUE,
            Int.MAX_VALUE.toString() to Int.MAX_VALUE,
            "-2147483649" to -1,
            "2147483648" to -1,
            "-0" to 0,
            "1.0" to -1,
            "1e2" to -1,
            "999999999999999999999999999999999999999999999999" to -1
        )

        cases.forEach { (wireCode, expectedCode) ->
            val error = assertRpcError(SolanaRpcException.Code.RPC_ERROR) {
                runBlocking {
                    RetrofitSolanaRpcClient(
                        FakeSolanaRpcApi(
                            JsonParser.parseString(
                                """{"jsonrpc":"2.0","id":1,"error":{"code":$wireCode,"message":"failure"}}"""
                            ).asJsonObject
                        )
                    ).latestBlockhash()
                }
            }

            assertEquals("wire code $wireCode", expectedCode, error.rpcError?.code)
        }
    }

    private class FakeSolanaRpcApi(
        private val fixedResponse: JsonObject? = null
    ) : SolanaRpcApi {
        val bodies = mutableListOf<JsonObject>()
        val urls = mutableListOf<String>()

        override suspend fun postJsonRpc(url: String, body: JsonObject): JsonObject {
            urls += url
            bodies += body
            fixedResponse?.let { return it }

            return when (body["method"].asString) {
                "getLatestBlockhash" -> rpcResponse(
                    body["id"].asLong,
                    """{"context":{"apiVersion":"2.0.0","slot":123},"value":{"blockhash":"$BLOCKHASH","lastValidBlockHeight":456}}"""
                )
                "getFeeForMessage" -> rpcResponse(body["id"].asLong, """{"context":{"slot":124},"value":5000}""")
                "getMinimumBalanceForRentExemption" -> rpcResponse(body["id"].asLong, "890880")
                "getAccountInfo" -> rpcResponse(body["id"].asLong, """{"context":{"slot":124},"value":{"lamports":2039280,"owner":"TokenzQdBNbLqP5VEkvhsuKq2G6Z1Lx8pGMyrEKHKzJ"}}""")
                "simulateTransaction" -> rpcResponse(
                    body["id"].asLong,
                    """{"context":{"slot":124},"value":{"err":null,"logs":["Program log: ok"],"replacementBlockhash":{"blockhash":"$BLOCKHASH","lastValidBlockHeight":789},"unitsConsumed":321}}"""
                )
                "sendTransaction" -> rpcResponse(body["id"].asLong, """"$SIGNATURE"""")
                else -> error("Unexpected RPC method")
            }
        }
    }

    private fun assertRpcError(
        expected: SolanaRpcException.Code,
        block: () -> Unit
    ): SolanaRpcException {
        val error = assertThrows(SolanaRpcException::class.java) { block() }
        assertEquals(expected, error.code)
        return error
    }

    private fun assertRouteError(
        expected: SolanaRpcException.Code,
        block: () -> Unit
    ): SolanaRpcException = assertRpcError(expected, block)

    private companion object {
        const val DEVNET_RPC = "https://api.devnet.solana.com"
        const val TRANSACTION = "AQIDBA=="
        const val ACCOUNT_ADDRESS = "So11111111111111111111111111111111111111112"
        const val BLOCKHASH = "7GjNiPun3AzEazTZoFEjZgcBMeuaXdpjHq2raZTmTrfs"
        const val SIGNATURE = "5NfHnqDyzT9qyfxZDq2sSskAMGuFZ3VRqW4EQxghKqrKYdKq6cZNW1J34w7qE6nGx1eDQe5s2eKxB2ZtE1xU9qgN"

        fun rpcResponse(id: Long, resultJson: String): JsonObject {
            return JsonParser.parseString("""{"jsonrpc":"2.0","id":$id,"result":$resultJson}""").asJsonObject
        }
    }
}
