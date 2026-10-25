package jp.co.soramitsu.core.rpc

import com.google.gson.Gson
import com.google.gson.JsonParser
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.core.runtime.IChainRegistry
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b256
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcError
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*
import java.math.BigInteger

class FinalizedExtrinsicReaderTest {
    @Test fun `original anchor checks canonical finalized identity using only public reads`() = runBlocking {
        val f = Fixture()
        assertEquals(FinalizedExtrinsicReader.Anchor(hash(0), BigInteger.TEN, hash(10)), f.reader.anchor(CHAIN))
        assertTrue(f.blockRequests.isEmpty())
        assertTrue(f.methods.all { it.startsWith("chain_get") })
    }

    @Test fun `identical old bytes at or before original anchor are never accepted`() = runBlocking {
        val f = Fixture()
        f.bodies[7] = listOf(SIGNED)
        val anchor = FinalizedExtrinsicReader.Anchor(hash(0), BigInteger.valueOf(7), hash(7))
        val result = f.reader.search(CHAIN, SIGNED, after = anchor)
        assertNull(result.inclusion)
        assertNull(result.nextBeforeHeight)
        assertEquals(3, result.checkedBlocks)
        assertEquals(listOf(hash(10), hash(9), hash(8)), f.blockRequests)
        assertEquals(0, f.reader.search(CHAIN, SIGNED, beforeHeight = BigInteger.valueOf(6), after = anchor).checkedBlocks)
    }

    @Test fun `changed original anchor fails even if exact bytes are in current finalized head`() {
        val f = Fixture()
        f.bodies[10] = listOf(SIGNED)
        for (anchor in listOf(FinalizedExtrinsicReader.Anchor(hash(99), BigInteger.ONE, hash(1)),
            FinalizedExtrinsicReader.Anchor(hash(0), BigInteger.ONE, hash(2)),
            FinalizedExtrinsicReader.Anchor(hash(0), BigInteger.valueOf(11), hash(11)))) {
            assertThrows(IllegalStateException::class.java) { runBlocking { f.reader.search(CHAIN, SIGNED, after = anchor) } }
        }
        assertTrue(f.blockRequests.isEmpty())
    }

    @Test fun `new latest search after a miss can discover later finalized original`() = runBlocking {
        val f = Fixture()
        val anchor = FinalizedExtrinsicReader.Anchor(hash(0), BigInteger.valueOf(7), hash(7))
        assertNull(f.reader.search(CHAIN, SIGNED, after = anchor).inclusion)
        f.override = { method, _, result -> if (method == "chain_getFinalizedHead") hash(11) else result }
        f.bodies[11] = listOf(SIGNED)
        assertEquals(hash(11), f.reader.search(CHAIN, SIGNED, after = anchor).inclusion?.blockHash)
    }

    @Test fun `exact bytes in canonical finalized block return public inclusion and never submit`() = runBlocking {
        val f = Fixture()
        f.bodies[8] = listOf("0x05", SIGNED.uppercase().replace("0X", "0x"))
        val result = f.reader.search(CHAIN, SIGNED)
        val inclusion = result.inclusion!!
        assertEquals(hash(8), inclusion.blockHash)
        assertEquals(BigInteger.valueOf(8), inclusion.blockNumber)
        assertEquals(1, inclusion.extrinsicIndex)
        assertEquals(SIGNED.fromHex().blake2b256().toHexString(withPrefix = true), inclusion.transactionHash)
        assertEquals(hash(10), inclusion.finalizedHeadHash)
        assertEquals(3, result.checkedBlocks)
        assertNull(result.nextBeforeHeight)
        assertTrue(f.methods.all { it.startsWith("chain_get") })
    }

    @Test fun `bounded miss returns an older cursor but no execution decision`() = runBlocking {
        val f = Fixture()
        f.bodies[7] = listOf(SIGNED)
        val miss = f.reader.search(CHAIN, SIGNED, limit = 2)
        assertNull(miss.inclusion)
        assertEquals(BigInteger.valueOf(8), miss.nextBeforeHeight)
        assertEquals(2, miss.checkedBlocks)
        val found = f.reader.search(CHAIN, SIGNED, beforeHeight = miss.nextBeforeHeight, limit = 2)
        assertEquals(BigInteger.valueOf(7), found.inclusion?.blockNumber)
    }

    @Test fun `scanning through genesis still does not establish nonexecution`() = runBlocking {
        val f = Fixture()
        val result = f.reader.search(CHAIN, SIGNED)
        assertNull(result.inclusion)
        assertNull(result.nextBeforeHeight)
        assertEquals(11, result.checkedBlocks)
    }

    @Test fun `transaction hash alone or different bytes cannot satisfy inclusion`() = runBlocking {
        val f = Fixture()
        f.bodies[10] = listOf(SIGNED.fromHex().blake2b256().toHexString(withPrefix = true), "0xaabbce")
        assertNull(f.reader.search(CHAIN, SIGNED, limit = 1).inclusion)
    }

    @Test fun `best unfinalized block is never searched`() = runBlocking {
        val f = Fixture()
        f.bodies[11] = listOf(SIGNED)
        assertNull(f.reader.search(CHAIN, SIGNED, beforeHeight = BigInteger.valueOf(100), limit = 1).inclusion)
        assertEquals(listOf(hash(10)), f.blockRequests)
    }

    @Test fun `wrong genesis rejects before searching`() {
        val f = Fixture()
        f.override = { method, params, result -> if (method == "chain_getBlockHash" && params == listOf(0L)) hash(99) else result }
        rejects(f)
        assertTrue(f.blockRequests.isEmpty())
    }

    @Test fun `reconnect to another genesis after apparent inclusion rejects`() {
        val f = Fixture()
        f.bodies[10] = listOf(SIGNED)
        f.override = { method, params, result ->
            if (method == "chain_getBlockHash" && params == listOf(0L) && f.blockRequests.isNotEmpty()) hash(99) else result
        }
        rejects(f)
    }

    @Test fun `noncanonical finalized head and changing finalized chain reject`() {
        for (atEnd in listOf(false, true)) {
            val f = Fixture()
            f.bodies[10] = listOf(SIGNED)
            var reads = 0
            f.override = { method, params, result ->
                if (method == "chain_getBlockHash" && params == listOf(10L) && (++reads > 2 || !atEnd)) hash(98) else result
            }
            rejects(f)
        }
    }

    @Test fun `regressing finalized height rejects apparent inclusion`() {
        val f = Fixture()
        f.bodies[10] = listOf(SIGNED)
        var reads = 0
        f.override = { method, _, result -> if (method == "chain_getFinalizedHead" && ++reads > 1) hash(9) else result }
        rejects(f)
    }

    @Test fun `wrong block height or broken parent link reject`() {
        for (badNumber in listOf(true, false)) {
            val f = Fixture()
            f.override = { method, _, result ->
                if (method == "chain_getBlock") mapOf("block" to mapOf(
                    "header" to mapOf("number" to if (badNumber) "0x1" else "0xa", "parentHash" to hash(98)),
                    "extrinsics" to emptyList<String>())) else result
            }
            rejects(f)
        }
    }

    @Test fun `pruned block rpc error missing header and malformed bytes reject`() {
        for (response in listOf(null, emptyMap<String, Any>(), mapOf("block" to emptyMap<String, Any>()),
            mapOf("block" to mapOf("header" to header(10), "extrinsics" to listOf("not-bytes"))))) {
            val f = Fixture()
            f.override = { method, _, result -> if (method == "chain_getBlock") response else result }
            rejects(f)
        }
        val f = Fixture()
        f.rpcError = RpcError(-32603, "local fixture pruned")
        rejects(f)
    }

    @Test fun `duplicate exact inclusion is inconsistent data`() {
        val f = Fixture()
        f.bodies[10] = listOf(SIGNED, SIGNED)
        rejects(f)
    }

    @Test fun `matching bytes under a different same-height block header cannot resolve payment`() {
        val f = Fixture()
        f.override = { method, _, result ->
            if (method == "chain_getBlock") mapOf("block" to mapOf(
                "header" to (header(10) + ("parentHash" to hash(98))), "extrinsics" to listOf(SIGNED))) else result
        }
        rejects(f)
    }

    @Test fun `independent polkadot js fixture includes scale digest logs in header hash`() = runBlocking {
        val f = Fixture()
        f.bodies[5] = listOf(SIGNED)
        val result = f.reader.search(CHAIN, SIGNED, beforeHeight = BigInteger.valueOf(5), limit = 1)
        assertEquals(hash(5), result.inclusion?.blockHash)
    }

    @Test fun `altered roots or digest cannot match the canonical hash`() {
        for (field in listOf("stateRoot", "extrinsicsRoot", "digest")) {
            val f = Fixture()
            f.override = { method, _, result ->
                if (method == "chain_getBlock") mapOf("block" to mapOf(
                    "header" to (header(10) + (field to if (field == "digest") mapOf("logs" to listOf("0x00")) else hash(98))),
                    "extrinsics" to listOf(SIGNED))) else result
            }
            rejects(f)
        }
    }

    @Test fun `cancellation propagates without returning a missing transaction`() {
        val f = Fixture()
        f.failure = CancellationException("local read cancelled")
        assertThrows(CancellationException::class.java) { runBlocking { f.reader.search(CHAIN, SIGNED) } }
    }

    @Test fun `invalid search bounds and malformed original bytes do not call rpc`() {
        val f = Fixture()
        for (limit in listOf(0, 65)) assertThrows(IllegalArgumentException::class.java) {
            runBlocking { f.reader.search(CHAIN, SIGNED, limit = limit) }
        }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { f.reader.search(CHAIN, "0x1") } }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { f.reader.search(CHAIN, SIGNED, beforeHeight = BigInteger.valueOf(-1)) }
        }
        assertTrue(f.methods.isEmpty())
    }

    private fun rejects(f: Fixture) {
        assertThrows(Exception::class.java) { runBlocking { f.reader.search(CHAIN, SIGNED) } }
        assertTrue(f.methods.all { it.startsWith("chain_get") })
    }

    private class Fixture {
        val methods = mutableListOf<String>()
        val blockRequests = mutableListOf<String>()
        val bodies = mutableMapOf<Int, List<String>>()
        var override: (String, List<Any?>, Any?) -> Any? = { _, _, result -> result }
        var rpcError: RpcError? = null
        var failure: Exception? = null
        val socket = mock<SocketService> { on { jsonMapper } doReturn Gson() }
        init {
            whenever(socket.executeRequest(any(), any(), any())).thenAnswer { call ->
                val request = call.getArgument<RuntimeRequest>(0)
                val callback = call.getArgument<SocketService.ResponseListener<RpcResponse>>(2)
                methods += request.method
                if (failure != null) callback.onError(failure!!) else {
                    val params = request.params
                    val result: Any? = when (request.method) {
                        "chain_getBlockHash" -> hash((params[0] as Long).toInt())
                        "chain_getFinalizedHead" -> hash(10)
                        "chain_getHeader" -> header(height(params[0] as String))
                        "chain_getBlock" -> {
                            val requested = params[0] as String
                            blockRequests += requested
                            val number = height(requested)
                            mapOf("block" to mapOf("header" to header(number), "extrinsics" to bodies[number].orEmpty()))
                        }
                        else -> error("Unexpected mutating RPC ${request.method}")
                    }
                    callback.onNext(RpcResponse("2.0", override(request.method, params, result), request.id, rpcError))
                }
                mock<SocketService.Cancellable>()
            }
        }
        private val connection = mock<ChainConnection> { on { socketService } doReturn socket }
        private val registry = mock<IChainRegistry> { on { getConnection(CHAIN) } doReturn connection }
        val reader = FinalizedExtrinsicReader(RpcCalls(registry))
    }

    companion object {
        private const val SIGNED = "0xaabbcd"
        private val fixtures = JsonParser.parseReader(checkNotNull(FinalizedExtrinsicReaderTest::class.java
            .getResourceAsStream("/pool-finalized-headers-v1.json")).reader()).asJsonObject.getAsJsonArray("blocks")
        private val CHAIN = hash(0).removePrefix("0x")
        private fun hash(number: Int) = fixtures[number].asJsonObject.get("hash").asString
        private fun height(hash: String) = fixtures.indexOfFirst { it.asJsonObject.get("hash").asString == hash }
        @Suppress("UNCHECKED_CAST")
        private fun header(number: Int): Map<String, Any> = Gson().fromJson(fixtures[number].asJsonObject.get("header"), Map::class.java) as Map<String, Any>
    }
}
