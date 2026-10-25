package jp.co.soramitsu.core.rpc

import com.google.gson.Gson
import jp.co.soramitsu.core.extrinsic.MutationExecutionGuard
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.core.runtime.IChainRegistry
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.request.RequestSendGuard
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

class AuthorizedRpcAdmissionTest {
    @Test fun `actual RPC forwards final writer callback to submission admission after queueing`() = runBlocking {
        val socket = mock<SocketService> { on { jsonMapper } doReturn Gson() }
        val connection = mock<ChainConnection> { on { socketService } doReturn socket }
        val registry = mock<IChainRegistry> { on { getConnection("chain") } doReturn connection }
        lateinit var queuedGuard: RequestSendGuard
        lateinit var callback: SocketService.ResponseListener<RpcResponse>
        var admissions = 0
        var writes = 0
        val guard = object : MutationExecutionGuard {
            override fun <T> runIfAuthorized(intentSha256: String, operation: () -> T): T = error("Wrong physical hook")
            override fun <T> runSubmissionIfAuthorized(intentSha256: String, operation: () -> T): T {
                assertEquals("intent", intentSha256)
                admissions++
                return operation()
            }
        }
        whenever(socket.executeGuardedRequest(any(), any(), any())).thenAnswer { call ->
            val request = call.getArgument<RuntimeRequest>(0)
            assertEquals("author_submitExtrinsic", request.method)
            assertEquals(listOf("0xfrozen"), request.params)
            queuedGuard = call.getArgument(1)
            callback = call.getArgument(2)
            mock<SocketService.Cancellable>()
        }
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            RpcCalls(registry).submitAuthorizedExtrinsic("chain", "0xfrozen", guard, "intent")
        }
        assertEquals(0, admissions)
        queuedGuard.sendIfAuthorized { writes++ }
        assertEquals(1, admissions)
        assertEquals(1, writes)
        callback.onNext(RpcResponse("2.0", "0xacknowledged", 7, null))
        assertEquals("0xacknowledged", pending.await())
    }

    @Test fun `default submission hook preserves existing guard policy`() {
        var allowed = true
        var writes = 0
        val guard = object : MutationExecutionGuard {
            override fun <T> runIfAuthorized(intentSha256: String, operation: () -> T): T {
                check(allowed && intentSha256 == "intent")
                return operation()
            }
        }
        assertEquals(42, guard.runSubmissionIfAuthorized("intent") { writes++; 42 })
        allowed = false
        assertThrows(IllegalStateException::class.java) { guard.runSubmissionIfAuthorized("intent") { writes++ } }
        assertEquals(1, writes)
    }
}
