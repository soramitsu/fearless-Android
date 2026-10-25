package jp.co.soramitsu.core.rpc.calls

import com.google.gson.Gson
import jp.co.soramitsu.core.rpc.RpcCalls
import jp.co.soramitsu.core.rpc.RuntimeCall
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.core.runtime.IChainRegistry
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.exception.RpcException
import jp.co.soramitsu.fearless_utils.wsrpc.request.DeliveryType
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcError
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigInteger

/**
 * The four helpers threw "unavailable in the public compatibility layer" in the RC. These tests pin the
 * request each one sends to what shared-features core 1.1.1.44-FLW (7508bc83, shipped in 4.2.0) sent, and
 * that a failed call never comes back as a default value.
 */
class RpcCallExtensionsTest {

    @Test
    fun `existential deposit asks the orml tokens rpc with the 4_2_0 currency argument`() {
        val socket = FakeSocket(result = "0x3b9aca00")

        val deposit = runBlocking { socket.rpcCalls.getExistentialDeposit(CHAIN_ID, "token2" to BigInteger.ZERO) }

        assertEquals(BigInteger("1000000000"), deposit)
        assertEquals("tokens_queryExistentialDeposit", socket.request.method)
        assertEquals(listOf(mapOf("token2" to BigInteger.ZERO)), socket.request.params)
        assertEquals("""[{"token2":0}]""", Gson().toJson(socket.request.params))
        assertEquals(DeliveryType.AT_LEAST_ONCE, socket.deliveryType)
    }

    @Test
    fun `existential deposit decodes the odd length hexes 4_2_0 padded`() {
        // The BrokenSubstrateHex examples in shared-features core FeeResponse.kt.
        listOf("0x3b9aca00", "0x3486ced00", "0xb320334").forEach { hex ->
            val deposit = runBlocking {
                FakeSocket(result = hex).rpcCalls.getExistentialDeposit(CHAIN_ID, "token" to "KSM")
            }

            assertEquals(BigInteger(hex.removePrefix("0x"), 16), deposit)
        }
    }

    @Test
    fun `existential deposit accepts a plain number`() {
        val deposit = runBlocking {
            FakeSocket(result = 100000000.0).rpcCalls.getExistentialDeposit(CHAIN_ID, "foreignAsset" to BigInteger.ONE)
        }

        assertEquals(BigInteger("100000000"), deposit)
    }

    @Test
    fun `existential deposit rpc error is a failure, not zero`() {
        val socket = FakeSocket(error = RpcError(-32601, "Method not found"))

        val error = assertThrows(RpcException::class.java) {
            runBlocking { socket.rpcCalls.getExistentialDeposit(CHAIN_ID, "token" to "KSM") }
        }
        assertTrue(error.message!!.contains("Method not found"))
    }

    @Test
    fun `existential deposit without a result is a failure, not zero`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { FakeSocket(result = null).rpcCalls.getExistentialDeposit(CHAIN_ID, "token" to "KSM") }
        }
    }

    @Test
    fun `block is read with chain_getBlock for the given hash`() {
        val blockHash = "0x" + "ab".repeat(32)
        val socket = FakeSocket(
            result = mapOf(
                "block" to mapOf(
                    "header" to mapOf("number" to "0x1b4", "parentHash" to "0x" + "cd".repeat(32)),
                    "extrinsics" to listOf("0x280403000b", "0x450284")
                ),
                "justifications" to null
            )
        )

        val block = runBlocking { socket.rpcCalls.getBlock(CHAIN_ID, blockHash) }

        assertEquals("chain_getBlock", socket.request.method)
        assertEquals(listOf(blockHash), socket.request.params)
        assertEquals(listOf("0x280403000b", "0x450284"), block.block.extrinsics)
    }

    @Test
    fun `unknown block is a failure`() {
        assertThrows(RpcException::class.java) {
            runBlocking { FakeSocket(result = null).rpcCalls.getBlock(CHAIN_ID, "0x" + "ab".repeat(32)) }
        }
    }

    @Test
    fun `runtime call goes through state_call once with prefixed hex arguments`() {
        val accountId = ByteArray(32) { it.toByte() }
        val socket = FakeSocket(result = "0x00e40b54020000000000000000000000")

        val result = runBlocking {
            socket.rpcCalls.executeRuntimeCall(CHAIN_ID, RuntimeCall.NominationPoolsApi.PendingRewards(accountId))
        }

        assertEquals("state_call", socket.request.method)
        assertEquals(
            listOf("NominationPoolsApi_pending_rewards", "0x" + accountId.joinToString("") { "%02x".format(it) }),
            socket.request.params
        )
        assertEquals(DeliveryType.AT_MOST_ONCE, socket.deliveryType)
        assertArrayEquals(
            byteArrayOf(0x00, 0xe4.toByte(), 0x0b, 0x54, 0x02, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            result.getOrThrow()
        )
    }

    @Test
    fun `runtime call error is a failure`() {
        val socket = FakeSocket(error = RpcError(1002, "Execution failed: Other: Exported method not found"))

        val result = runBlocking {
            socket.rpcCalls.executeRuntimeCall(CHAIN_ID, RuntimeCall.NominationPoolsApi.PendingRewards(ByteArray(32)))
        }

        assertTrue(result.exceptionOrNull() is RpcException)
    }

    @Test
    fun `runtime call socket failure is a failure`() {
        val socket = FakeSocket(failure = IllegalStateException("socket closed"))

        val result = runBlocking {
            socket.rpcCalls.executeRuntimeCall(CHAIN_ID, RuntimeCall.NominationPoolsApi.PendingRewards(ByteArray(32)))
        }

        assertEquals("socket closed", result.exceptionOrNull()?.message)
    }

    @Test
    fun `pending rewards decode as a little endian u128`() {
        val call = RuntimeCall.NominationPoolsApi.PendingRewards(ByteArray(32))
        val tenBillion = byteArrayOf(0x00, 0xe4.toByte(), 0x0b, 0x54, 0x02, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        val max = ByteArray(16) { 0xff.toByte() }

        assertEquals(BigInteger("10000000000"), call.parseResult(tenBillion))
        assertEquals(BigInteger.TWO.pow(128) - BigInteger.ONE, call.parseResult(max))
        assertThrows(IllegalArgumentException::class.java) { call.parseResult(ByteArray(8)) }
    }

    @Test
    fun `liquidity proxy quote sends the 4_2_0 parameters in order`() {
        val socket = FakeSocket(
            result = mapOf("amount" to "123456789", "fee" to "3000", "rewards" to emptyList<Any>(), "route" to listOf("XYKPool"))
        )

        val quote = runBlocking {
            socket.rpcCalls.liquidityProxyQuote(
                chainId = CHAIN_ID,
                inputAssetId = XOR,
                outputAssetId = VAL,
                amount = BigInteger.TEN.pow(18),
                swapVariant = "WithDesiredInput",
                selectedSourceTypes = emptyList(),
                filterMode = "Disabled",
                dexId = 0
            )
        }

        assertEquals("liquidityProxy_quote", socket.request.method)
        assertEquals(
            listOf(0, XOR, VAL, "1000000000000000000", "WithDesiredInput", emptyList<String>(), "Disabled"),
            socket.request.params
        )
        assertEquals(BigInteger("123456789"), quote?.amount)
        assertEquals(listOf("XYKPool"), quote?.route)
    }

    @Test
    fun `liquidity proxy quote without a route is null`() {
        val quote = runBlocking {
            FakeSocket(result = null).rpcCalls.liquidityProxyQuote(
                CHAIN_ID, XOR, VAL, BigInteger.ONE, "WithDesiredInput", emptyList(), "Disabled", 0
            )
        }

        assertNull(quote)
    }

    @Test
    fun `liquidity quote rpc error takes precedence over absent or numeric result`() {
        for (result in listOf(null, mapOf("amount" to "123", "route" to listOf("XYKPool")))) {
            val socket = FakeSocket(result = result, error = RpcError(-32601, "Quote unavailable"))
            val failure = assertThrows(RpcException::class.java) {
                runBlocking {
                    socket.rpcCalls.liquidityProxyQuote(CHAIN_ID, XOR, VAL, BigInteger.ONE,
                        "WithDesiredInput", emptyList(), "Disabled", 0)
                }
            }
            assertEquals("Rpc error. code: -32601, message: Quote unavailable", failure.message)
            assertEquals(DeliveryType.AT_LEAST_ONCE, socket.deliveryType)
        }
    }

    @Test
    fun `liquidity quote preserves transport failure and cancellation identity`() {
        class TransportFailure(val marker: Any) : RuntimeException("transport failed")
        class QuoteCancelled(val marker: Any) : kotlinx.coroutines.CancellationException("quote cancelled")
        for (failure in listOf(TransportFailure(Any()), QuoteCancelled(Any()))) {
            val socket = FakeSocket(failure = failure)
            val actual = runCatching {
                runBlocking {
                    socket.rpcCalls.liquidityProxyQuote(CHAIN_ID, XOR, VAL, BigInteger.ONE,
                        "WithDesiredInput", emptyList(), "Disabled", 0)
                }
            }.exceptionOrNull()
            org.junit.Assert.assertSame(failure, actual)
        }
    }

    @Test
    fun `liquidity quote preserves an explicitly returned zero for caller validation`() {
        val socket = FakeSocket(result = mapOf("amount" to "0", "route" to emptyList<String>()))
        val quote = runBlocking {
            socket.rpcCalls.liquidityProxyQuote(CHAIN_ID, XOR, VAL, BigInteger.ONE,
                "WithDesiredInput", emptyList(), "Disabled", 0)
        }
        assertEquals(BigInteger.ZERO, quote?.amount)
        assertEquals(emptyList<String>(), quote?.route)
    }

    private class FakeSocket(
        result: Any? = null,
        error: RpcError? = null,
        failure: Throwable? = null
    ) {
        lateinit var request: RuntimeRequest
        lateinit var deliveryType: DeliveryType

        private val socket: SocketService = mock {
            on { jsonMapper } doReturn Gson()
        }

        init {
            whenever(socket.executeRequest(any(), any(), any())).thenAnswer { invocation ->
                request = invocation.getArgument(0)
                deliveryType = invocation.getArgument(1)
                val callback = invocation.getArgument<SocketService.ResponseListener<RpcResponse>>(2)
                if (failure != null) {
                    callback.onError(failure)
                } else {
                    callback.onNext(RpcResponse("2.0", result, request.id, error))
                }
                mock<SocketService.Cancellable>()
            }
        }

        private val connection: ChainConnection = mock {
            on { socketService } doReturn socket
        }

        private val chainRegistry: IChainRegistry = mock {
            on { getConnection(CHAIN_ID) } doReturn connection
        }

        val rpcCalls = RpcCalls(chainRegistry)
    }

    private companion object {
        const val CHAIN_ID = "262e1b2ad728475fd6fe88e62d34c200abe6fd693931ddad144059b1eb884e5b"
        const val XOR = "0x0200000000000000000000000000000000000000000000000000000000000000"
        const val VAL = "0x0200040000000000000000000000000000000000000000000000000000000000"
    }
}
