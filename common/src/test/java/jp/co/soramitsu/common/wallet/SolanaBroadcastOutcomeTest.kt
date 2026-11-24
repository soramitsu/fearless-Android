package jp.co.soramitsu.common.wallet

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import jp.co.soramitsu.common.data.network.WalletProviderPool
import jp.co.soramitsu.common.data.network.solana.RetrofitSolanaRpcClient
import jp.co.soramitsu.common.data.network.solana.SolanaBroadcastException
import jp.co.soramitsu.common.data.network.solana.SolanaBroadcastException.Outcome
import jp.co.soramitsu.common.data.network.solana.SolanaRpcApi
import jp.co.soramitsu.common.data.network.solana.SolanaRpcEndpoints
import jp.co.soramitsu.common.data.network.solana.SolanaSignatureOutcome
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.utils.SolanaTransactionSigner
import jp.co.soramitsu.common.data.network.WalletProviderUnavailableException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Base64

/** What a failed broadcast proves decides whether the wallet may release the payment. */
class SolanaBroadcastOutcomeTest {
    private val posts = mutableListOf<Pair<String, JsonObject>>()

    private fun client(genesis: (String) -> String = { MAINNET_GENESIS }, answer: (url: String, body: JsonObject) -> JsonObject): RetrofitSolanaRpcClient {
        val api = object : SolanaRpcApi {
            override suspend fun postJsonRpc(url: String, body: JsonObject): JsonObject {
                val id = body["id"].asLong
                if (body["method"].asString == "getGenesisHash") return result(id, "\"${genesis(url)}\"")
                posts += url to body
                return answer(url, body)
            }
        }
        // Two endpoints are enough to show each verdict and hand-over.
        return RetrofitSolanaRpcClient(api, MAINNET_BETA, listOf(PUBLICNODE), WalletProviderPool { 0L })
    }

    private fun broadcastFailure(client: RetrofitSolanaRpcClient): SolanaBroadcastException =
        assertThrows(SolanaBroadcastException::class.java) { runBlocking { client.sendRawTransaction(TRANSACTION) } }

    @Test
    fun `refusals before relaying and connections never made mean nothing was sent`() {
        for (failure in listOf<() -> Nothing>({ throw refusal(403) }, { throw refusal(401) }, { throw refusal(400) },
            { throw UnknownHostException("no dns") }, { throw ConnectException("refused") })) {
            posts.clear()
            assertEquals(Outcome.NOT_SENT, broadcastFailure(client { _, _ -> failure() }).outcome)
            assertEquals(listOf(MAINNET_BETA, PUBLICNODE), posts.map { it.first })
        }
    }

    @Test
    fun `an endpoint whose cluster cannot be verified is never offered the bytes`() {
        posts.clear()
        val error = broadcastFailure(client(genesis = { UniversalWalletRegistry.SOLANA_DEVNET_GENESIS_HASH }) { _, _ -> error("must not post") })
        assertEquals(Outcome.NOT_SENT, error.outcome)
        assertTrue(posts.isEmpty())
    }

    @Test
    fun `a node's verdict is a refusal and stops while a lagging node hands over to the next`() {
        posts.clear()
        val refused = broadcastFailure(client { _, body -> error(body, -32002, "Transaction simulation failed: insufficient funds") })
        assertEquals(Outcome.REFUSED, refused.outcome)
        assertEquals(listOf(MAINNET_BETA), posts.map { it.first })
        posts.clear()
        val lagging = broadcastFailure(client { url, body ->
            if (url == MAINNET_BETA) error(body, -32005, "Node is behind") else error(body, -32002, "Blockhash not found")
        })
        assertEquals(Outcome.REFUSED, lagging.outcome)
        assertEquals(listOf(MAINNET_BETA, PUBLICNODE), posts.map { it.first })
    }

    @Test
    fun `a JSON-RPC error a node gives only before relaying is final or hands over, any other code may have been relayed`() {
        // A node's own verdict on the transaction stops the broadcast at the first endpoint.
        for (code in listOf(-32002, -32003, -32600, -32602, -32700)) {
            posts.clear()
            assertEquals(Outcome.REFUSED, broadcastFailure(client { _, body -> error(body, code, "verdict") }).outcome)
            assertEquals(listOf(MAINNET_BETA), posts.map { it.first })
        }
        // A node that cannot take it at all hands it to the next endpoint without counting as reached.
        for (code in listOf(-32601, -32004, -32005, -32014, -32016)) {
            posts.clear()
            val error = broadcastFailure(client { url, body -> if (url == MAINNET_BETA) error(body, code, "unavailable") else throw refusal(403) })
            assertEquals(Outcome.NOT_SENT, error.outcome)
            assertEquals(listOf(false, false), posts.map { it.second["params"].asJsonArray[1].asJsonObject["skipPreflight"].asBoolean })
        }
        // A gateway's quota, auth or internal error proves nothing: the next endpoint only relays, and if none
        // accepts the bytes the payment stays possibly sent.
        for (code in listOf(-32603, -32000, -32001, -32007, -32403, -32429, -1)) {
            posts.clear()
            val error = broadcastFailure(client { _, body -> error(body, code, "gateway error") })
            assertEquals("code $code", Outcome.AMBIGUOUS, error.outcome)
            assertEquals(listOf(MAINNET_BETA, PUBLICNODE), posts.map { it.first })
            assertEquals(listOf(false, true), posts.map { it.second["params"].asJsonArray[1].asJsonObject["skipPreflight"].asBoolean })
            posts.clear()
            assertEquals(SIGNATURE, runBlocking {
                client { url, body -> if (url == MAINNET_BETA) error(body, code, "gateway error") else result(body["id"].asLong, "\"$SIGNATURE\"") }
                    .sendRawTransaction(TRANSACTION)
            })
            assertEquals(listOf(false, true), posts.map { it.second["params"].asJsonArray[1].asJsonObject["skipPreflight"].asBoolean })
        }
    }

    @Test
    fun `failed TLS handshakes and missing routes mean the request never left the device`() {
        for (failure in listOf<() -> Nothing>({ throw javax.net.ssl.SSLHandshakeException("handshake") },
            { throw java.net.NoRouteToHostException("no route") })) {
            posts.clear()
            assertEquals(Outcome.NOT_SENT, broadcastFailure(client { _, _ -> failure() }).outcome)
            assertEquals(listOf(false, false), posts.map { it.second["params"].asJsonArray[1].asJsonObject["skipPreflight"].asBoolean })
        }
    }

    @Test
    fun `anything that may have reached a node is ambiguous and later attempts only relay`() {
        for (first in listOf<(JsonObject) -> JsonObject>({ throw SocketTimeoutException("timeout") }, { throw IOException("connection reset") },
            { throw refusal(503) }, { throw refusal(429) }, { throw refusal(408) }, { body -> result(body["id"].asLong + 7, "\"$SIGNATURE\"") })) {
            posts.clear()
            val error = broadcastFailure(client { url, body -> if (url == MAINNET_BETA) first(body) else throw refusal(403) })
            assertEquals(Outcome.AMBIGUOUS, error.outcome)
            assertEquals(listOf(false, true), posts.map { it.second["params"].asJsonArray[1].asJsonObject["skipPreflight"].asBoolean })
            // The same bytes every time.
            assertEquals(listOf(TRANSACTION, TRANSACTION), posts.map { it.second["params"].asJsonArray[0].asString })
        }
    }

    @Test
    fun `a node that already has the transaction answers with its own signature`() = runBlocking {
        val transaction = signedTransaction()
        val processed = client { _, body ->
            JsonParser.parseString("""{"jsonrpc":"2.0","id":${body["id"]},"error":{"code":-32002,""" +
                """"message":"Transaction simulation failed: This transaction has already been processed","data":{"err":"AlreadyProcessed"}}}""").asJsonObject
        }
        assertEquals(SolanaTransactionSigner.firstSignatureBase58(transaction), processed.sendRawTransaction(transaction))
        posts.clear()
        assertEquals(SIGNATURE, client { url, body -> if (url == MAINNET_BETA) throw refusal(403) else result(body["id"].asLong, "\"$SIGNATURE\"") }
            .sendRawTransaction(TRANSACTION))
    }

    /** One endpoint's view: finalized progress from getEpochInfo, then a status lookup answered at [lookupSlot]. */
    private data class Node(val finalizedSlot: Long = 1_000, val finalizedHeight: Long = 201, val lookupSlot: Long = 1_000,
        val status: String = "null", val fails: Boolean = false, val genesis: String = MAINNET_GENESIS, val hangs: Boolean = false)

    /** Asks [nodes] in their configured order; [beforeLookup] runs as an endpoint's status lookup arrives. */
    private suspend fun outcomeAt(nodes: List<Pair<String, Node>>, calls: MutableList<String> = mutableListOf(),
        beforeLookup: suspend (url: String) -> Unit = {}): SolanaSignatureOutcome {
        val byUrl = nodes.toMap()
        val api = object : SolanaRpcApi {
            override suspend fun postJsonRpc(url: String, body: JsonObject): JsonObject {
                val node = byUrl.getValue(url)
                val method = body["method"].asString
                if (method == "getGenesisHash") return result(body["id"].asLong, "\"${node.genesis}\"")
                calls += "$url $method"
                if (node.hangs) awaitCancellation()
                return when (method) {
                    "getEpochInfo" -> {
                        assertEquals("finalized", body["params"].asJsonArray[0].asJsonObject["commitment"].asString)
                        result(body["id"].asLong, """{"absoluteSlot":${node.finalizedSlot},"blockHeight":${node.finalizedHeight},""" +
                            """"epoch":1,"slotIndex":5,"slotsInEpoch":432000,"transactionCount":9}""")
                    }
                    "getSignatureStatuses" -> {
                        beforeLookup(url)
                        if (node.fails) throw IOException("lost")
                        assertTrue(body["params"].asJsonArray[1].asJsonObject["searchTransactionHistory"].asBoolean)
                        result(body["id"].asLong, """{"context":{"slot":${node.lookupSlot}},"value":[${node.status}]}""")
                    }
                    else -> error("unexpected $method")
                }
            }
        }
        return RetrofitSolanaRpcClient(api, nodes.first().first, nodes.drop(1).map { it.first }, WalletProviderPool { 0L })
            .signatureOutcome(SIGNATURE, lastValidBlockHeight = 200)
    }

    private fun outcome(mainnetBeta: Node, publicnode: Node = mainnetBeta, calls: MutableList<String> = mutableListOf()) =
        runBlocking { outcomeAt(listOf(MAINNET_BETA to mainnetBeta, PUBLICNODE to publicnode), calls) }

    /** A user-selected node first, then the two public endpoints. */
    private fun outcome3(custom: Node, mainnetBeta: Node, publicnode: Node) =
        runBlocking { outcomeAt(listOf(CUSTOM to custom, MAINNET_BETA to mainnetBeta, PUBLICNODE to publicnode)) }

    @Test
    fun `expiry is proven by finalized progress past the height and a lookup at least as recent with no trace`() {
        val calls = mutableListOf<String>()
        assertEquals(SolanaSignatureOutcome.Expired(2), outcome(Node(), calls = calls))
        // Progress first, then the lookup, on each endpoint, and both endpoints are asked.
        for (url in listOf(MAINNET_BETA, PUBLICNODE)) {
            assertEquals(listOf("$url getEpochInfo", "$url getSignatureStatuses"), calls.filter { it.startsWith("$url ") })
        }
        assertEquals(4, calls.size)
        assertEquals(SolanaSignatureOutcome.Expired(2), outcome(Node(lookupSlot = 1_001)))
    }

    @Test
    fun `a lookup older than the finalized slot or progress not past the height proves nothing`() {
        // A lagging backend behind the same URL answers the lookup from slot 990, before the finalized slot 1000.
        assertEquals(SolanaSignatureOutcome.Unknown, outcome(Node(lookupSlot = 990)))
        assertEquals(SolanaSignatureOutcome.Unknown, outcome(Node(finalizedHeight = 200)))
        assertEquals(SolanaSignatureOutcome.Unknown, outcome(Node(finalizedHeight = 150, lookupSlot = 5_000)))
    }

    @Test
    fun `a trace that is not confirmed keeps the payment pending and a confirmed one settles it`() {
        val processed = outcome(Node(status = PROCESSED))
        assertTrue(processed is SolanaSignatureOutcome.Seen && !processed.status.isConfirmed)
        val confirmed = outcome(Node(status = """{"slot":5,"confirmations":null,"err":null,"confirmationStatus":"finalized"}"""))
        assertTrue(confirmed is SolanaSignatureOutcome.Seen && confirmed.status.isConfirmed)
    }

    @Test
    fun `endpoints are never mixed and expired stands only when no endpoint disagrees`() {
        // One endpoint proves expiry while another still lags or sees an unconfirmed trace: keep it pending.
        assertEquals(SolanaSignatureOutcome.Unknown, outcome(Node(), Node(lookupSlot = 990)))
        assertEquals(SolanaSignatureOutcome.Unknown, outcome(Node(finalizedHeight = 199), Node()))
        val pending = outcome(Node(), Node(status = PROCESSED))
        assertTrue(pending is SolanaSignatureOutcome.Seen && !pending.status.isConfirmed)
        // A confirmed status anywhere wins over another endpoint's expiry.
        val seen = outcome(Node(), Node(status = LANDED))
        assertTrue(seen is SolanaSignatureOutcome.Seen && seen.status.isConfirmed)
        // An endpoint that cannot answer, or serves another cluster, does not disagree; with no answer at all it fails.
        assertEquals(SolanaSignatureOutcome.Expired(1), outcome(Node(fails = true), Node()))
        val calls = mutableListOf<String>()
        assertEquals(SolanaSignatureOutcome.Expired(1), outcome(Node(genesis = UniversalWalletRegistry.SOLANA_DEVNET_GENESIS_HASH), Node(), calls))
        assertTrue(calls.none { it.startsWith(MAINNET_BETA) })
        assertThrows(IOException::class.java) { outcome(Node(fails = true)) }
    }

    @Test
    fun `expiry reports how many endpoints proved it and only two or more corroborate it`() {
        // One endpoint proves expiry while the others fail or time out: it stands, but on a single proof.
        val lone = runBlocking { outcomeAt(listOf(CUSTOM to Node(), MAINNET_BETA to Node(fails = true), PUBLICNODE to Node(fails = true))) }
        assertEquals(SolanaSignatureOutcome.Expired(1), lone)
        assertTrue(!(lone as SolanaSignatureOutcome.Expired).corroborated)
        val pair = outcome(Node(), Node()) as SolanaSignatureOutcome.Expired
        assertEquals(2, pair.provingHosts)
        assertTrue(pair.corroborated)
        // One proves and another still sees the payment as possibly pending: no release at all.
        assertEquals(SolanaSignatureOutcome.Unknown, outcome(Node(), Node(finalizedHeight = 150)))
        // Endpoints that abstain under the lag cap do not count as proofs.
        val healthy = Node(finalizedSlot = 100_000, lookupSlot = 100_000)
        val stalled = Node(finalizedSlot = 90_000, finalizedHeight = 150, lookupSlot = 90_000)
        assertEquals(SolanaSignatureOutcome.Expired(2), outcome3(stalled, healthy, healthy))
    }

    @Test
    fun `proofs are counted by host so two spellings of one node never corroborate each other`() {
        // A user-added copy of publicnode with a trailing slash and the built-in one: one node, one proof.
        val doubled = runBlocking { outcomeAt(listOf(PUBLICNODE to Node(), "$PUBLICNODE/" to Node())) }
        assertEquals(SolanaSignatureOutcome.Expired(1), doubled)
        assertTrue(!(doubled as SolanaSignatureOutcome.Expired).corroborated)
        // Letter case and a trailing dot in the host name are the same node too.
        assertEquals(SolanaSignatureOutcome.Expired(1), runBlocking {
            outcomeAt(listOf(PUBLICNODE to Node(), "https://Solana-RPC.PublicNode.com." to Node(), "https://solana-rpc.publicnode.com./" to Node()))
        })
        // Two distinct hosts corroborate, whatever extra spellings also answer.
        val distinct = runBlocking { outcomeAt(listOf(PUBLICNODE to Node(), "$PUBLICNODE/" to Node(), MAINNET_BETA to Node())) }
        assertEquals(SolanaSignatureOutcome.Expired(2), distinct)
        assertTrue((distinct as SolanaSignatureOutcome.Expired).corroborated)
        // A spelling that still sees the payment as possibly pending keeps it pending, as for any other endpoint.
        assertEquals(SolanaSignatureOutcome.Unknown, runBlocking {
            outcomeAt(listOf(PUBLICNODE to Node(), "$PUBLICNODE/" to Node(finalizedHeight = 150), MAINNET_BETA to Node()))
        })
    }

    @Test
    fun `an unconfirmed trace on one endpoint never masks a confirmed one on another`() {
        for ((first, second) in listOf(Node(status = PROCESSED) to Node(status = LANDED), Node(status = LANDED) to Node(status = PROCESSED))) {
            val seen = outcome(first, second)
            assertTrue(seen is SolanaSignatureOutcome.Seen && seen.status.isConfirmed)
        }
    }

    @Test
    fun `endpoints are asked at once and each has a bounded time to answer`() = runTest {
        // Each endpoint's lookup waits until the other endpoint's lookup has arrived: only requests made at once
        // finish without reaching the time bound.
        val asked = mapOf(MAINNET_BETA to CompletableDeferred<Unit>(), PUBLICNODE to CompletableDeferred())
        val concurrent = outcomeAt(listOf(MAINNET_BETA to Node(), PUBLICNODE to Node()), beforeLookup = { url ->
            asked.getValue(url).complete(Unit)
            asked.getValue(if (url == MAINNET_BETA) PUBLICNODE else MAINNET_BETA).await()
        })
        assertEquals(SolanaSignatureOutcome.Expired(2), concurrent)
        assertEquals(0L, currentTime)

        // An endpoint that never answers does not count once its time is up, and does not hold a confirmed answer.
        val start = currentTime
        assertEquals(SolanaSignatureOutcome.Expired(1), outcomeAt(listOf(MAINNET_BETA to Node(hangs = true), PUBLICNODE to Node())))
        assertEquals(15_000L, currentTime - start)
        val landed = currentTime
        val seen = outcomeAt(listOf(MAINNET_BETA to Node(hangs = true), PUBLICNODE to Node(status = LANDED)))
        assertTrue(seen is SolanaSignatureOutcome.Seen && seen.status.isConfirmed)
        assertEquals(landed, currentTime)
        val silent = currentTime
        assertTrue(runCatching { outcomeAt(listOf(MAINNET_BETA to Node(hangs = true), PUBLICNODE to Node(hangs = true))) }
            .exceptionOrNull() is WalletProviderUnavailableException)
        assertEquals(15_000L, currentTime - silent)
    }

    @Test
    fun `with three answers an endpoint far behind the median abstains from expiry votes but its trace still counts`() {
        val healthy = Node(finalizedSlot = 100_000, lookupSlot = 100_000)
        // A stalled user-selected node, not yet past the height, no longer holds the payment pending.
        val stalled = Node(finalizedSlot = 90_000, finalizedHeight = 150, lookupSlot = 90_000)
        assertEquals(SolanaSignatureOutcome.Expired(2), outcome3(stalled, healthy, healthy))
        // More than 1,000 slots behind the median abstains; exactly 1,000 still votes.
        assertEquals(SolanaSignatureOutcome.Expired(2), outcome3(stalled.copy(finalizedSlot = 98_999), healthy, healthy))
        assertEquals(SolanaSignatureOutcome.Unknown, outcome3(stalled.copy(finalizedSlot = 99_000), healthy, healthy))
        // A trace from a stalled endpoint still keeps the payment pending.
        val traced = outcome3(stalled.copy(status = PROCESSED), healthy, healthy)
        assertTrue(traced is SolanaSignatureOutcome.Seen && !traced.status.isConfirmed)
        // One absurdly high slot cannot make two healthy endpoints look stalled.
        val absurd = Node(finalizedSlot = 9_000_000_000_000, finalizedHeight = 9_000_000_000, lookupSlot = 9_000_000_000_000)
        val notPast = healthy.copy(finalizedHeight = 199)
        assertEquals(SolanaSignatureOutcome.Unknown, outcome3(absurd, notPast, notPast))
        // Nor can two of four: the lower median keeps a tie from silencing the healthy half.
        assertEquals(SolanaSignatureOutcome.Unknown, runBlocking {
            outcomeAt(listOf(CUSTOM to absurd, CUSTOM_2 to absurd, MAINNET_BETA to notPast, PUBLICNODE to notPast))
        })
        // With fewer than three answers every endpoint keeps its vote.
        assertEquals(SolanaSignatureOutcome.Unknown, runBlocking { outcomeAt(listOf(CUSTOM to stalled)) })
        assertEquals(SolanaSignatureOutcome.Unknown, outcome(stalled, healthy))
        assertEquals(SolanaSignatureOutcome.Unknown, outcome3(stalled, healthy, healthy.copy(fails = true)))
    }

    private fun refusal(status: Int) = HttpException(Response.error<JsonObject>(status, "".toResponseBody(null)))

    /** One signature slot, a legacy message with two keys, a blockhash and one instruction. */
    private fun signedTransaction(): String = Base64.getEncoder().encodeToString(
        byteArrayOf(1) + ByteArray(64) { 7 } + byteArrayOf(1, 0, 1, 2) + ByteArray(32) { 1 } + ByteArray(32) + ByteArray(32) { 9 } +
            byteArrayOf(1, 1, 1, 0, 0)
    )

    private companion object {
        const val MAINNET_GENESIS = UniversalWalletRegistry.SOLANA_MAINNET_GENESIS_HASH
        const val MAINNET_BETA = UniversalWalletRegistry.SOLANA_MAINNET_RPC_URL
        const val PUBLICNODE = SolanaRpcEndpoints.PUBLICNODE_MAINNET_RPC_URL
        const val CUSTOM = "https://solana-node.example.org/rpc"
        const val CUSTOM_2 = "https://solana-node-2.example.org/rpc"
        const val PROCESSED = """{"slot":5,"confirmations":0,"err":null,"confirmationStatus":"processed"}"""
        const val LANDED = """{"slot":5,"confirmations":7,"err":null,"confirmationStatus":"confirmed"}"""
        const val TRANSACTION = "AQIDBA=="
        const val SIGNATURE = "5NfHnqDyzT9qyfxZDq2sSskAMGuFZ3VRqW4EQxghKqrKYdKq6cZNW1J34w7qE6nGx1eDQe5s2eKxB2ZtE1xU9qgN"

        fun result(id: Long, json: String): JsonObject =
            JsonParser.parseString("""{"jsonrpc":"2.0","id":$id,"result":$json}""").asJsonObject

        fun error(body: JsonObject, code: Int, message: String): JsonObject =
            JsonParser.parseString("""{"jsonrpc":"2.0","id":${body["id"]},"error":{"code":$code,"message":"$message"}}""").asJsonObject
    }
}
