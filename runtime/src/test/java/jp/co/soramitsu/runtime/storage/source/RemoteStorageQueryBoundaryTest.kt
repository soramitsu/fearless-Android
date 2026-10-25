package jp.co.soramitsu.runtime.storage.source

import com.google.gson.Gson
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

/** Real RemoteStorageSource and BulkRetriever with local physical socket callback boundary. */
class RemoteStorageQueryBoundaryTest {
    @Test fun `actual query acknowledges exact requested key and absent value`() = runTest {
        val h = Harness(); h.configure(); h.answer = listOf(h.set(null))
        val value = h.query(); assertTrue(value.isSuccess); assertNull(value.getOrThrow())
        assertEquals(listOf<String?>(null), h.bound)
        assertEquals("state_queryStorageAt", h.requests.single().method)
        assertEquals(listOf(listOf(h.key)), h.requests.single().params)
    }
    @Test fun `actual query preserves valid present bytes`() = runTest {
        val h = Harness(); h.configure(); h.answer = listOf(h.set("0xab"))
        assertEquals("0xab", h.query().getOrThrow())
    }
    @Test fun `duplicate requested key cannot establish absence`() = runTest {
        val h = Harness(); h.configure(); h.answer = listOf(h.set(changes = listOf(listOf(h.key, "0xab"), listOf(h.key, null))))
        assertTrue(h.query().isFailure); assertTrue(h.bound.isEmpty())
    }
    @Test fun `extra response sets cannot establish absence`() = runTest {
        val h = Harness(); h.configure(); h.answer = listOf(h.set(null), h.set("0xab"))
        assertTrue(h.query().isFailure); assertTrue(h.bound.isEmpty())
    }
    @Test fun `malformed query block cannot establish absence`() = runTest {
        val h = Harness(); h.configure(); h.answer = listOf(h.set(null, block = "not-a-block"))
        assertTrue(h.query().isFailure); assertTrue(h.bound.isEmpty())
    }
    @Test fun `missing query block cannot establish absence`() = runTest {
        val h = Harness(); h.configure(); h.answer = listOf(mapOf("changes" to listOf(listOf(h.key, null))))
        assertTrue(h.query().isFailure); assertTrue(h.bound.isEmpty())
    }
    @Test fun `extra change tuple fields cannot establish absence`() = runTest {
        val h = Harness(); h.configure(); h.answer = listOf(h.set(changes = listOf(listOf(h.key, null, "extra"))))
        assertTrue(h.query().isFailure); assertTrue(h.bound.isEmpty())
    }
    @Test fun `numeric query value must not become a string`() = runTest {
        val h = Harness(); h.configure(); h.answer = listOf(h.set(changes = listOf(listOf(h.key, 7))))
        assertTrue(h.query().isFailure); assertTrue(h.bound.isEmpty())
    }
    @Test fun `socket cancellation propagates even when caller job remains active`() = runTest {
        val h = Harness(); h.configure(); h.error = CancellationException("Local callback cancellation")
        var cancelled = false
        try { h.query() } catch (error: CancellationException) { cancelled = true; assertEquals(h.error!!.message, error.message) }
        assertTrue("Socket cancellation must not become ordinary unavailable data", cancelled)
        assertTrue(currentCoroutineContext().isActive); assertTrue(h.bound.isEmpty())
    }
    @Test fun `query must acknowledge the requested historical block`() = runTest {
        val h = Harness(); h.configure(); h.answer = listOf(h.set(null))
        assertTrue(h.query(at = "0x" + "cd".repeat(32)).isFailure); assertTrue(h.bound.isEmpty())
    }
    @Test fun `matching historical block preserves absence and request context`() = runTest {
        val h = Harness(); h.configure(); h.answer = listOf(h.set(null))
        val at = "0x" + "AB".repeat(32)
        assertNull(h.query(at).getOrThrow()); assertEquals(listOf(listOf(h.key), at), h.requests.single().params)
    }
    @Test fun `malformed encoded values never reach the binder`() = runTest {
        val h = Harness(); h.configure()
        for (value in listOf("0x1", "garbage", "0xgg", "")) {
            h.answer = listOf(h.set(value)); assertTrue(h.query().isFailure)
        }
        assertTrue(h.bound.isEmpty())
    }
    @Test fun `empty encoded bytes remain distinct from absence`() = runTest {
        val h = Harness(); h.configure(); h.answer = listOf(h.set("0x"))
        assertEquals("0x", h.query().getOrThrow()); assertEquals(listOf("0x"), h.bound)
    }
    private class Harness {
        val key = "0x0102"
        private val socket = mock<SocketService> { on { jsonMapper } doReturn Gson() }
        private val registry = mock<ChainRegistry>()
        private val runtime = mock<RuntimeSnapshot>()
        private val connection = mock<ChainConnection> { on { socketService } doReturn socket }
        private val source = RemoteStorageSource(registry, BulkRetriever())
        var answer: Any = emptyList<Any>()
        var error: Throwable? = null
        val requests = mutableListOf<RuntimeRequest>()
        val bound = mutableListOf<String?>()
        suspend fun configure() {
            whenever(registry.getRuntime("local")).thenReturn(runtime)
            whenever(registry.awaitConnection("local")).thenReturn(connection)
            whenever(socket.executeRequest(any(), any(), any())).thenAnswer { call ->
                val request = call.getArgument<RuntimeRequest>(0); requests += request
                val listener = call.getArgument<SocketService.ResponseListener<RpcResponse>>(2)
                error?.let { listener.onError(it) } ?: listener.onNext(RpcResponse("2.0", answer, request.id, null))
                object : SocketService.Cancellable { override fun cancel() {} }
            }
        }
        fun set(value: String? = null, block: String = "0x" + "ab".repeat(32), changes: Any = listOf(listOf(key, value))) = mapOf("block" to block, "changes" to changes)
        suspend fun query(at: String? = null) = source.queryResult("local", { key }, at) { value, _ -> bound += value; value }
    }
}
