package jp.co.soramitsu.fearless_utils.wsrpc

import com.google.gson.Gson
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.string
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.nonNull
import com.neovisionaries.ws.client.WebSocket
import com.neovisionaries.ws.client.WebSocketListener
import com.neovisionaries.ws.client.WebSocketWriteGuard
import com.neovisionaries.ws.client.GuardedWriteHandle
import com.neovisionaries.ws.client.GuardedWriteCallback
import com.neovisionaries.ws.client.GuardedWriteFailure
import java.util.concurrent.atomic.AtomicInteger
import com.neovisionaries.ws.client.WebSocketFactory
import com.neovisionaries.ws.client.WebSocketState
import jp.co.soramitsu.fearless_utils.wsrpc.logging.Logger
import jp.co.soramitsu.fearless_utils.wsrpc.recovery.Reconnector
import jp.co.soramitsu.fearless_utils.wsrpc.request.RequestExecutor
import jp.co.soramitsu.fearless_utils.wsrpc.request.RequestSendGuard
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class GuardedSocketRequestTest {
    @Test fun `revocation while executor is queued prevents actual send and errors once`() {
        val h = Harness(); h.connect()
        h.submit(); h.allowed = false; h.executor.drain()
        verify(h.ws, never()).sendText(anyString())
        assertEquals(1, h.errors)
        h.closed()
        h.service.switchUrl("wss://test.invalid"); h.connected(); h.executor.drain()
        assertEquals(1, h.errors)
        verify(h.ws, never()).sendText(anyString())
    }

    @Test fun `connecting queue rechecks expiry before first send`() {
        val h = Harness(); h.service.start("wss://test.invalid")
        h.submit(); h.allowed = false; h.connected(); h.executor.drain()
        verify(h.ws, never()).sendText(anyString()); assertEquals(1, h.errors)
    }

    @Test fun `paused queue rechecks permission when resumed`() {
        val h = Harness(); h.service.start("wss://test.invalid", remainPaused = true)
        h.submit(); h.allowed = false; h.service.resume(); h.connected(); h.executor.drain()
        verify(h.ws, never()).sendText(anyString()); assertEquals(1, h.errors)
    }

    @Test fun `failed initial connection cannot replay guarded request`() {
        val h = Harness(); h.service.start("wss://test.invalid"); h.submit()
        h.closed(); h.service.switchUrl("wss://test.invalid"); h.connected(); h.executor.drain()
        assertEquals(1, h.errors); verify(h.ws, never()).sendText(anyString())
    }

    @Test fun `successful send receives one response and never replays after reconnect`() {
        val h = Harness(); h.connect(); h.submit(); h.executor.drain()
        verify(h.ws, times(1)).sendText(anyString())
        h.response(RpcResponse("2.0", "hash", 7, null))
        h.response(RpcResponse("2.0", "hash", 7, null))
        h.closed(); h.service.switchUrl("wss://test.invalid"); h.connected(); h.executor.drain()
        assertEquals(1, h.responses); assertEquals(0, h.errors)
        verify(h.ws, times(1)).sendText(anyString())
    }

    @Test fun `unknown result after send is failure once with no retry`() {
        val h = Harness(); h.connect(); h.submit(); h.executor.drain()
        h.closed(); h.service.switchUrl("wss://test.invalid"); h.connected(); h.executor.drain()
        assertEquals(1, h.errors); verify(h.ws, times(1)).sendText(anyString())
    }

    @Test fun `cancellation before executor handoff prevents send and subsequent callbacks`() {
        val h = Harness(); h.connect(); val pending = h.submit()
        pending.cancel(); pending.cancel(); h.executor.drain()
        h.response(RpcResponse("2.0", "hash", 7, null))
        verify(h.ws, never()).sendText(anyString()); assertEquals(0, h.errors); assertEquals(0, h.responses)
    }

    @Test fun `stop switch and pause terminate queued guarded requests once`() {
        for (action in listOf<(SocketService) -> Unit>({ it.stop() }, { it.switchUrl("wss://other.invalid") }, { it.pause() })) {
            val h = Harness(); h.connect(); h.submit(); action(h.service); h.executor.drain()
            assertEquals(1, h.errors); verify(h.ws, never()).sendText(anyString())
        }
    }

    @Test fun `disconnected guard fails immediately rather than leaving a suspended caller`() {
        val h = Harness(); h.submit(); h.executor.drain()
        assertEquals(1, h.errors); verify(h.ws, never()).sendText(anyString())
    }

    @Test fun `waiting on authority never holds socket monitor`() {
        val h = Harness(); h.connect()
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        h.submit(RequestSendGuard { send -> entered.countDown(); check(release.await(3, TimeUnit.SECONDS)); send() })
        val sender = thread { h.executor.drain() }
        assertTrue(entered.await(3, TimeUnit.SECONDS))
        val stopped = CountDownLatch(1)
        thread { h.service.stop(); stopped.countDown() }
        assertTrue("Socket monitor is not held while authorization waits", stopped.await(3, TimeUnit.SECONDS))
        release.countDown(); sender.join(3_000)
        assertFalse(sender.isAlive); assertEquals(1, h.errors); verify(h.ws, never()).sendText(anyString())
    }

    @Test fun `legacy reads retain reconnect delivery`() {
        val h = Harness(); h.connect()
        h.service.executeRequest(RuntimeRequest("legacy_read", emptyList(), 8), callback = h.listener)
        h.executor.drain(); h.closed(); h.service.switchUrl("wss://test.invalid"); h.connected(); h.executor.drain()
        verify(h.ws, times(2)).sendText(anyString()); assertEquals(0, h.errors)
    }

    @Test fun `failure callback can enqueue a new request without losing it during state transition`() {
        val h = Harness(); h.connect()
        h.service.executeGuardedRequest(RuntimeRequest("mutation", emptyList(), 9), RequestSendGuard { it() },
            object : SocketService.ResponseListener<RpcResponse> {
                override fun onNext(response: RpcResponse) = Unit
                override fun onError(throwable: Throwable) {
                    h.service.executeRequest(RuntimeRequest("new_read", emptyList(), 10), callback = h.listener)
                }
            })
        h.service.switchUrl("wss://other.invalid")
        h.connected(); h.executor.drain()
        verify(h.ws, times(1)).sendText(anyString())
        h.response(RpcResponse("2.0", "read", 10, null))
        assertEquals(1, h.responses)
    }

    @Test fun `coroutine adapter maps one response and ignores duplicate delivery`() = runBlocking {
        val h = Harness(); h.connect()
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            h.service.executeGuardedAsync(RuntimeRequest("mutation", emptyList(), 7), RequestSendGuard { it() }, string().nonNull())
        }
        h.executor.drain()
        h.response(RpcResponse("2.0", "hash", 7, null))
        h.response(RpcResponse("2.0", "duplicate", 7, null))
        assertEquals("hash", pending.await())
        verify(h.ws, times(1)).sendText(anyString())
        Unit
    }

    @Test fun `coroutine cancellation removes queued request before transport handoff`() = runBlocking {
        val h = Harness(); h.connect()
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            h.service.executeGuardedAsync(RuntimeRequest("mutation", emptyList(), 7), RequestSendGuard { it() }, string().nonNull())
        }
        pending.cancel(); pending.join(); h.executor.drain()
        verify(h.ws, never()).sendText(anyString())
        h.response(RpcResponse("2.0", "late", 7, null))
        assertTrue(pending.isCancelled)
    }

    @Test fun `expiry during pre-send logging is checked at actual WebSocket handoff`() {
        val h = Harness(); h.connect()
        doAnswer { invocation ->
            if (invocation.getArgument<String>(0).contains("SENDING GUARDED REQUEST")) h.allowed = false
            null
        }.`when`(h.logger).log(anyString())
        h.submit(); h.executor.drain()
        verify(h.ws, never()).sendText(anyString())
        assertEquals(1, h.errors)
    }

    @Test fun `authorization follows the request through the internal writer queue`() {
        val h = Harness(); h.connect(); h.queueWriter = true
        h.submit(); h.executor.drain()
        h.allowed = false; h.writer.drain()
        verify(h.ws, never()).sendText(anyString()); assertEquals(1, h.errors)
    }

    @Test fun `guarded failure and response callbacks run outside socket monitor`() {
        val h = Harness(); h.connect()
        h.callbackProbe = { assertFalse(Thread.holdsLock(h.service)) }
        h.allowed = false; h.submit(); h.executor.drain(); assertEquals(1, h.errors)
        h.allowed = true; h.submit(); h.executor.drain()
        h.response(RpcResponse("2.0", "hash", 7, null)); assertEquals(1, h.responses)
    }

    private class Harness {
        val executor = ManualExecutor()
        val writer = ManualExecutor()
        var queueWriter = false
        var callbackProbe: (() -> Unit)? = null
        val ws = mock(WebSocket::class.java)
        lateinit var transportListener: WebSocketListener
        val factory = mock(WebSocketFactory::class.java).also { `when`(it.createSocket(anyString())).thenReturn(ws) }
        val logger = mock(Logger::class.java)
        val service = SocketService(Gson(), logger, factory, mock(Reconnector::class.java), RequestExecutor(executor))
        @Volatile var allowed = true
        var errors = 0; var responses = 0
        val listener = object : SocketService.ResponseListener<RpcResponse> {
            override fun onNext(response: RpcResponse) { callbackProbe?.invoke(); responses++ }
            override fun onError(throwable: Throwable) { callbackProbe?.invoke(); errors++ }
        }
        init {
            doAnswer { transportListener = it.getArgument(0); ws }.`when`(ws).addListener(any(WebSocketListener::class.java))
            doAnswer { invocation ->
                val text = invocation.getArgument<String>(0)
                val guard = invocation.getArgument<WebSocketWriteGuard>(1)
                val callback = invocation.getArgument<GuardedWriteCallback>(2)
                val handle = mock(GuardedWriteHandle::class.java)
                val phase = AtomicInteger(0)
                doAnswer {
                    val cancelled = phase.compareAndSet(0, 2)
                    if (cancelled) callback.onFailure(mock(GuardedWriteFailure::class.java))
                    cancelled
                }.`when`(handle).cancel()
                val run = Runnable {
                    try {
                        guard.writeIfAuthorized {
                            check(phase.compareAndSet(0, 1))
                            ws.sendText(text)
                        }
                    } catch (failure: Exception) {
                        if (phase.getAndSet(2) != 2) callback.onFailure(mock(GuardedWriteFailure::class.java))
                    }
                }
                if (queueWriter) writer.execute(run) else run.run()
                handle
            }.`when`(ws).sendTextGuarded(anyString(), any(WebSocketWriteGuard::class.java), any(GuardedWriteCallback::class.java))
        }
        fun connected() = transportListener.onConnected(ws, mutableMapOf())
        fun closed() = transportListener.onStateChanged(ws, WebSocketState.CLOSED)
        fun response(response: RpcResponse) = transportListener.onTextMessage(ws, Gson().toJson(response))
        fun connect() { service.start("wss://test.invalid"); connected() }
        fun submit(guard: RequestSendGuard = RequestSendGuard { send -> check(allowed); send() }) =
            service.executeGuardedRequest(RuntimeRequest("author_submitExtrinsic", listOf("0x00"), 7), guard, listener)
    }

    private class ManualExecutor : AbstractExecutorService() {
        private val tasks = mutableListOf<Runnable>()
        override fun execute(command: Runnable) { synchronized(tasks) { tasks += command } }
        fun drain() {
            while (true) {
                val task = synchronized(tasks) { if (tasks.isEmpty()) null else tasks.removeAt(0) } ?: return
                task.run()
            }
        }
        override fun shutdown() = Unit
        override fun shutdownNow(): MutableList<Runnable> = mutableListOf()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
    }
}
