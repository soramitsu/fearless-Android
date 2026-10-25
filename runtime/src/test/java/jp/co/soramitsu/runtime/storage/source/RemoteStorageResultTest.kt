package jp.co.soramitsu.runtime.storage.source

import java.io.IOException
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.subscription.response.SubscriptionChange
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

/** Actual RemoteStorageSource, real callbackFlow, public local socket responses; no network. */
@OptIn(ExperimentalCoroutinesApi::class)
class RemoteStorageResultTest {
    @Test fun `silent subscription cannot establish absent storage`() = runTest {
        val h = Harness(this); h.start()
        assertTrue(h.values.isEmpty()); assertTrue(h.bound.isEmpty())
    }

    @Test fun `acknowledged null value establishes absence`() = runTest {
        val h = Harness(this); h.start(); h.respond(null)
        assertTrue(h.values.single().isSuccess); assertNull(h.values.single().getOrThrow())
        assertEquals(listOf<String?>(null), h.bound)
    }

    @Test fun `transport failure is distinct from absent storage and later response recovers`() = runTest {
        val h = Harness(this); h.start(); val error = IOException("Local offline")
        h.listener.onError(error); runCurrent()
        assertSame(error, h.values.single().exceptionOrNull()); assertTrue(h.bound.isEmpty())
        h.respond("0xab"); assertEquals("0xab", h.values.last().getOrThrow())
    }

    @Test fun `wrong subscription key cannot establish absence`() = runTest {
        val h = Harness(this); h.start(); h.raw(listOf(listOf("0xwrong", null)))
        assertTrue(h.values.single().isFailure); assertTrue(h.bound.isEmpty())
    }

    @Test fun `empty subscription changes are unavailable not absent`() = runTest {
        val h = Harness(this); h.start(); h.raw(emptyList<List<String?>>())
        assertTrue(h.values.single().isFailure); assertTrue(h.bound.isEmpty())
    }

    @Test fun `duplicate or malformed subscription entries cannot establish absence`() = runTest {
        val h = Harness(this); h.start()
        for (changes in listOf(listOf(listOf(h.key, null), listOf(h.key, null)), listOf(listOf(h.key)), listOf(listOf(h.key, 7)))) {
            h.raw(changes); assertTrue(h.values.last().isFailure)
        }
        assertTrue(h.bound.isEmpty())
    }

    @Test fun `malformed block context cannot establish absence`() = runTest {
        val h = Harness(this); h.start(); h.raw(listOf(listOf(h.key, null)), block = "not-a-block")
        assertTrue(h.values.single().isFailure); assertTrue(h.bound.isEmpty())
    }

    @Test fun `binder failure remains unavailable and later healthy value recovers`() = runTest {
        val h = Harness(this); h.start(); h.binderFails = true; h.respond("0xab")
        assertTrue(h.values.single().isFailure)
        h.binderFails = false; h.respond("0xcd"); assertEquals("0xcd", h.values.last().getOrThrow())
    }

    @Test fun `missing storage key cannot subscribe or establish absence`() = runTest {
        val h = Harness(this); h.configure()
        val result = h.source.observeResult<String?>("local", { null }) { value, _ -> value }.first()
        assertTrue(result.isFailure); verify(h.socket, never()).subscribe(any(), any(), any())
    }

    @Test fun `collector cancellation removes owned subscription`() = runTest {
        val h = Harness(this); val job = h.start(); job.cancelAndJoin(); runCurrent()
        assertEquals(1, h.cancelled); assertTrue(h.values.isEmpty())
    }

    @Test fun `cancellation callback is not published as ordinary failure`() = runTest {
        val h = Harness(this); val job = h.start()
        h.listener.onError(CancellationException("Local cancellation")); runCurrent()
        assertTrue(job.isCancelled); assertTrue(h.values.isEmpty()); assertEquals(1, h.cancelled)
    }

    @Test fun `query preserves acknowledged absence and present value`() = runTest {
        val h = Harness(this); h.configure(); h.queryValues = mapOf(h.key to null)
        val absent = h.query(); assertTrue(absent.isSuccess); assertNull(absent.getOrThrow())
        h.queryValues = mapOf(h.key to "0xab"); assertEquals("0xab", h.query().getOrThrow())
    }

    @Test fun `failed or incomplete query cannot establish absence`() = runTest {
        val h = Harness(this); h.configure()
        for (values in listOf(emptyMap(), mapOf("0xwrong" to null), mapOf(h.key to null, "0xother" to null))) {
            h.queryValues = values; assertTrue(h.query().isFailure)
        }
        assertTrue(h.bound.isEmpty())
        h.queryError = IOException("Local query failure"); val actual = h.query().exceptionOrNull(); assertTrue(actual === h.queryError || actual?.cause === h.queryError)
    }

    @Test fun `query decoding failure cannot establish absence`() = runTest {
        val h = Harness(this); h.configure(); h.queryValues = mapOf(h.key to "0xab"); h.binderFails = true
        assertTrue(h.query().isFailure)
    }

    @Test fun `query cancellation is rethrown`() = runTest {
        val h = Harness(this); h.configure(); val expected = CancellationException("Local cancelled query"); h.queryError = expected
        try { h.query(); fail("Cancellation must propagate") } catch (actual: CancellationException) { assertTrue(actual === expected || actual.cause === expected) }
    }

    @Test fun `legacy source cannot silently claim acknowledged storage support`() = runTest {
        val source = LocalStorageSource(mock(), mock())
        assertTrue(source.observeResult<String?>("local", { "0x01" }) { value, _ -> value }.first().isFailure)
        assertTrue(source.queryResult<String?>("local", { "0x01" }) { value, _ -> value }.isFailure)
    }

    @Test fun `subscription rejects malformed bytes and recovers on empty encoded bytes`() = runTest {
        val h = Harness(this); h.start()
        for (value in listOf("0x1", "garbage", "0xgg", "")) {
            h.respond(value); assertTrue(h.values.last().isFailure)
        }
        assertTrue(h.bound.isEmpty())
        h.respond("0x"); assertEquals("0x", h.values.last().getOrThrow()); assertEquals(listOf("0x"), h.bound)
    }

    private class Harness(private val scope: TestScope) {
        val key = "0x0102"
        val socket = mock<SocketService>()
        private val registry = mock<ChainRegistry>()
        private val runtime = mock<RuntimeSnapshot>()
        private val connection = mock<ChainConnection> { on { socketService } doReturn socket }
        private val bulk = BulkRetriever()
        val source = RemoteStorageSource(registry, bulk)
        lateinit var listener: SocketService.ResponseListener<SubscriptionChange>
        val values = mutableListOf<Result<String?>>()
        val bound = mutableListOf<String?>()
        var cancelled = 0
        var binderFails = false
        var queryValues: Map<String, String?> = emptyMap()
        var queryError: Exception? = null
        suspend fun configure() {
            whenever(registry.getRuntime("local")).thenReturn(runtime)
            whenever(registry.awaitConnection("local")).thenReturn(connection)
            whenever(socket.subscribe(any(), any(), any())).thenAnswer { call ->
                listener = call.getArgument(1)
                object : SocketService.Cancellable { override fun cancel() { cancelled++ } }
            }
            whenever(socket.executeRequest(any(), any(), any())).thenAnswer { call ->
                val request = call.getArgument<RuntimeRequest>(0)
                val callback = call.getArgument<SocketService.ResponseListener<RpcResponse>>(2)
                val answer = listOf(mapOf("block" to "0x" + "ab".repeat(32), "changes" to queryValues.map { listOf(it.key, it.value) }))
                queryError?.let { callback.onError(it) } ?: callback.onNext(RpcResponse("2.0", answer, request.id, null))
                object : SocketService.Cancellable { override fun cancel() {} }
            }
        }
        suspend fun start(): kotlinx.coroutines.Job {
            configure()
            return scope.backgroundScope.launch {
                source.observeResult("local", { key }) { value, _ -> bind(value) }.collect { values += it }
            }.also { scope.runCurrent() }
        }
        suspend fun query() = source.queryResult("local", { key }) { value, _ -> bind(value) }
        private fun bind(value: String?): String? {
            if (binderFails) throw IllegalArgumentException("Local malformed value")
            bound += value; return value
        }
        fun respond(value: String?) = raw(listOf(listOf(key, value)))
        fun raw(changes: Any, block: String = "0x" + "ab".repeat(32)) {
            listener.onNext(SubscriptionChange("2.0", "state_storage", SubscriptionChange.Params(mapOf("block" to block, "changes" to changes), "local-subscription")))
            scope.runCurrent()
        }
    }
}
