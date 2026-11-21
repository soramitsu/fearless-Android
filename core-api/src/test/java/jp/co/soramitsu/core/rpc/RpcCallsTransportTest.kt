package jp.co.soramitsu.core.rpc

import com.google.gson.Gson
import java.math.BigInteger
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.core.runtime.IChainRegistry
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RpcCallsTransportTest {
    private val socket = mock<SocketService>()
    private val connection = mock<ChainConnection>()
    private val registry = mock<IChainRegistry>()
    private val cancellable = mock<SocketService.Cancellable>()
    private val calls = RpcCalls(registry)
    private val requests = mutableListOf<RuntimeRequest>()
    private val responses = mutableMapOf<String, Any>()
    private val hash = "0x" + "ab".repeat(32)

    @Before
    fun setup() {
        whenever(socket.jsonMapper).thenReturn(Gson())
        whenever(connection.socketService).thenReturn(socket)
        whenever(registry.getConnection("chain")).thenReturn(connection)
        whenever(socket.executeRequest(any(), any(), any())).thenAnswer { invocation ->
            val request = invocation.getArgument<RuntimeRequest>(0)
            requests += request
            invocation.getArgument<SocketService.ResponseListener<RpcResponse>>(2)
                .onNext(RpcResponse("2.0", responses.getValue(request.method), request.id, null))
            cancellable
        }
    }

    @Test
    fun runtimeIdentityCombinesValidatedVersionAndGenesisHash() = runBlocking<Unit> {
        responses["chain_getRuntimeVersion"] = mapOf("specName" to "polkadot", "specVersion" to 17, "transactionVersion" to 3)
        responses["chain_getBlockHash"] = hash
        assertEquals(RpcRuntimeIdentity(hash, "polkadot", 17, 3), calls.getRuntimeIdentity("chain"))
        assertEquals(listOf(0L), requests.last().params)
        assertEquals(17, calls.getRuntimeVersion("chain").specVersion)
        val runtime = mock<RuntimeSnapshot>()
        whenever(registry.getRuntime("chain")).thenReturn(runtime)
        assertSame(runtime, calls.getRuntime("chain"))
        assertSame(socket, calls.socketFor("chain"))
    }

    @Test
    fun incompleteRuntimeIdentityFailsBeforeRequestingAGenesisHash() = runBlocking<Unit> {
        val invalid = listOf(
            mapOf("specName" to "", "specVersion" to 1, "transactionVersion" to 1),
            mapOf("specName" to "chain", "specVersion" to -1, "transactionVersion" to 1),
            mapOf("specName" to "chain", "specVersion" to 1),
            mapOf("specName" to "chain", "specVersion" to 1, "transactionVersion" to -1)
        )
        for (response in invalid) {
            responses["chain_getRuntimeVersion"] = response
            assertTrue(runCatching { calls.getRuntimeIdentity("chain") }.exceptionOrNull() is IllegalArgumentException)
        }
        assertTrue(requests.none { it.method == "chain_getBlockHash" })
    }

    @Test
    fun nonceFeeBlockAndSubmissionUseTheExactRpcParameters() = runBlocking<Unit> {
        val chain = mock<IChain>()
        whenever(chain.id).thenReturn("chain")
        whenever(chain.addressPrefix).thenReturn(42)
        val accountId = ByteArray(32) { 2 }
        responses["system_accountNextIndex"] = "0x10"
        responses["payment_queryInfo"] = mapOf("partialFee" to "12345678901234567890")
        responses["chain_getBlockHash"] = hash
        responses["author_submitExtrinsic"] = "submitted"
        assertEquals(BigInteger.valueOf(16), calls.getAccountNonce(chain, accountId))
        assertEquals(listOf(accountId.toAddress(42)), requests.last().params)
        assertEquals(BigInteger("12345678901234567890"), calls.estimateExtrinsicFee("chain", "0xextrinsic"))
        assertEquals(listOf("0xextrinsic"), requests.last().params)
        assertEquals(hash, calls.getBlockHash("chain", BigInteger.valueOf(23)))
        assertEquals(listOf(23L), requests.last().params)
        assertEquals("submitted", calls.submitExtrinsic("chain", "0xsigned"))
        assertEquals(listOf("0xsigned"), requests.last().params)
    }

    @Test
    fun malformedFeeAndBlockResultsAreNotAccepted() = runBlocking<Unit> {
        responses["payment_queryInfo"] = mapOf("partialFee" to -1)
        assertTrue(runCatching { calls.estimateExtrinsicFee("chain", "0xcall") }.exceptionOrNull() is IllegalArgumentException)
        responses["chain_getBlockHash"] = "not-a-hash"
        assertTrue(runCatching { calls.getBlockHash("chain", BigInteger.ZERO) }.exceptionOrNull() is IllegalArgumentException)
        val count = requests.size
        assertTrue(runCatching { calls.getBlockHash("chain", BigInteger.ONE.shiftLeft(64)) }.isFailure)
        assertEquals(count, requests.size)
    }

}
