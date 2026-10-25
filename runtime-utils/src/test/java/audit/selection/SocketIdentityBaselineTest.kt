package audit.selection

import com.google.gson.Gson
import com.neovisionaries.ws.client.WebSocket
import com.neovisionaries.ws.client.WebSocketError
import com.neovisionaries.ws.client.WebSocketException
import com.neovisionaries.ws.client.WebSocketFactory
import com.neovisionaries.ws.client.WebSocketListener
import com.neovisionaries.ws.client.WebSocketState
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Delayed
import java.util.concurrent.FutureTask
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.logging.Logger
import jp.co.soramitsu.fearless_utils.wsrpc.recovery.Reconnector
import jp.co.soramitsu.fearless_utils.wsrpc.request.RequestExecutor
import jp.co.soramitsu.fearless_utils.wsrpc.request.RequestSendGuard
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse
import jp.co.soramitsu.fearless_utils.wsrpc.socket.SocketTransportPolicy
import jp.co.soramitsu.fearless_utils.wsrpc.state.SocketStateMachine.State
import jp.co.soramitsu.fearless_utils.wsrpc.subscription.response.SubscriptionChange
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.contains
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.timeout
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions

class SocketIdentityBaselineTest {
    private val a = "wss://a.invalid"

    private val b = "wss://b.invalid"
    private class ManualExecutor : AbstractExecutorService() {
        val tasks = mutableListOf<Runnable>()
        override fun execute(r: Runnable) { tasks += r }
        override fun shutdown() {}
        override fun shutdownNow(): MutableList<Runnable> = mutableListOf()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
        fun drain() { while (tasks.isNotEmpty()) tasks.removeAt(0).run() }
    }
    private class Scheduled(val task: Runnable) : FutureTask<Unit>(task, Unit), ScheduledFuture<Unit> {
        override fun getDelay(unit: TimeUnit) = 0L
        override fun compareTo(other: Delayed) = 0
    }
    private class Gate(val topic: String) {
        val entered = CountDownLatch(1)

        val release = CountDownLatch(1)
        fun awaitEntry() { check(entered.await(3, TimeUnit.SECONDS)) { "Callback did not reach hold point" } }
    }
    private class Peer(val url: String, val ws: WebSocket) { lateinit var listener: WebSocketListener }
    private class Harness(policy: SocketTransportPolicy = SocketTransportPolicy.Platform) {
        val peers = mutableListOf<Peer>()

        val executor = ManualExecutor()

        val scheduled = mutableListOf<Scheduled>()
        val scheduler = mock(ScheduledExecutorService::class.java)
        var gate: Gate? = null
        var onConnect: ((Peer) -> Unit)? = null
        val failures = AtomicReference<Throwable?>()
        val messages = java.util.Collections.synchronizedList(mutableListOf<String>())
        val logger = object : Logger {
            override fun log(message: String?) {
                if (message != null) messages += message
                val g = gate
                if (g != null && Thread.currentThread().name == "held-old-callback" && message?.contains(g.topic) == true) {
                    g.entered.countDown()

                    check(g.release.await(3, TimeUnit.SECONDS)) { "Held callback not released" }
                }
            }
            override fun log(throwable: Throwable?) { if (throwable != null) failures.set(throwable) }
        }
        val factory = mock(WebSocketFactory::class.java)
        lateinit var service: SocketService
        var state: State = State.Disconnected
        init {
            `when`(scheduler.schedule(any(Runnable::class.java), anyLong(), any(TimeUnit::class.java))).thenAnswer {
                Scheduled(it.getArgument(0)).also { f -> scheduled += f }
            }
            `when`(factory.createSocket(anyString())).thenAnswer { call ->
                val ws = mock(WebSocket::class.java)

                val p = Peer(call.getArgument(0), ws)

                peers += p
                `when`(ws.addListener(any(WebSocketListener::class.java))).thenAnswer {
                    p.listener = it.getArgument(0)

                ws
                }
                `when`(ws.connectAsynchronously()).thenAnswer {
                    onConnect?.invoke(p)

                ws
                }
                ws
            }
            service = SocketService(Gson(), logger, factory, Reconnector(executor = scheduler), RequestExecutor(executor), policy)
            service.addStateObserver { state = it }
        }
        fun connected(p: Peer = peers.last()) { p.listener.onConnected(p.ws, mutableMapOf()) }
        fun closed(p: Peer = peers.last()) { p.listener.onStateChanged(p.ws, WebSocketState.CLOSED) }
        fun response(p: Peer, id: Int) {
            p.listener.onTextMessage(p.ws, "{\"jsonrpc\":\"2.0\",\"id\":$id,\"result\":\"from-old-socket\"}")
        }
        fun hold(topic: String, action: () -> Unit): Pair<Gate, Thread> {
            val g = Gate(topic)

            gate = g
            val t = thread(name = "held-old-callback", isDaemon = true) {
                try {
                    action()
                } catch (
                t: Throwable
            ) {
                    failures.set(t)
                }
            }
            g.awaitEntry()

            return g to t
        }
        fun release(held: Pair<Gate, Thread>) {
            held.first.release.countDown()

            held.second.join(3000)
            check(!held.second.isAlive) { "Held callback failed to finish" }

            failures.get()?.let { throw it }
        }
    }

    @Test(timeout = 10000)
    fun oldConnectedCannotQualifyReplacement() {
        val h = Harness()

        h.service.start(a)

        val old = h.peers.last()

        val held = h.hold("[SOCKET][CONNECTED]") { h.connected(old) }
        try {
            h.service.switchUrl(b)
        } finally {
            h.release(held)
        }
        assertTrue("Old A callback qualified B: ${h.state}", h.state is State.Connecting)
    }

    @Test(timeout = 10000)
    fun oldClosedCannotDisconnectReplacement() {
        val h = Harness()

        h.service.start(a)

        h.connected()

        val old = h.peers.last()
        val held = h.hold("[SOCKET][STATE] CLOSED") { h.closed(old) }
        try {
            h.service.switchUrl(b)

        h.connected()
        } finally {
            h.release(held)
        }
        assertTrue("Old A close changed live B: ${h.state}", h.state is State.Connected)
    }

    @Test(timeout = 10000)
    fun oldResponseCannotCompleteNewRequestWithSameId() {
        val h = Harness()

        h.service.start(a)

        h.connected()

        val old = h.peers.last()
        val held = h.hold("[SOCKET][RECEIVED]") { h.response(old, 17) }

        var received = 0
        try {
            h.service.switchUrl(b)

            h.connected()
            h.service.executeRequest(
                RuntimeRequest("chain_getBlockHash", emptyList(), 17),
                callback = object : SocketService.ResponseListener<RpcResponse> {
                override fun onNext(response: RpcResponse) { received++ }
                override fun onError(throwable: Throwable) { throw throwable }
            }
            )

            h.executor.drain()
        } finally {
            h.release(held)
        }
        assertEquals("Old A response completed new B request", 0, received)
    }

    @Test(timeout = 10000)
    fun oldSubscriptionCannotNotifyReplacementWithSameSubscriptionId() {
        val h = Harness()

        h.service.start(a)

        h.connected()

        val old = h.peers.last()
        val held = h.hold("[SOCKET][RECEIVED]") {
            old.listener.onTextMessage(old.ws, """{"jsonrpc":"2.0","method":"chain_subscription","params":{"subscription":"same-id","result":"old-update"}}""")
        }
        var received = 0
        try {
            h.service.switchUrl(b)

            h.connected()
            h.service.subscribe(
                RuntimeRequest("chain_subscribeNewHeads", emptyList(), 23),
                callback = object : SocketService.ResponseListener<SubscriptionChange> {
                    override fun onNext(response: SubscriptionChange) { received++ }
                    override fun onError(throwable: Throwable) { throw throwable }
                },
                    unsubscribeMethod = "chain_unsubscribeNewHeads"
            )
            h.executor.drain()
            val current = h.peers.last()
            current.listener.onTextMessage(current.ws, """{"jsonrpc":"2.0","id":23,"result":"same-id"}""")
        } finally {
            h.release(held)
        }
        assertEquals("Old A subscription notified a new B subscription", 0, received)
    }

    @Test(timeout = 10000)
    fun oldConnectedCannotQualifyAtoBtoANewIncarnation() {
        val h = Harness()

        h.service.start(a)

        val old = h.peers.last()

        val held = h.hold("[SOCKET][CONNECTED]") { h.connected(old) }
        try {
            h.service.switchUrl(b)

        h.service.switchUrl(a)
        } finally {
            h.release(held)
        }
        assertEquals(3, h.peers.size)
        assertTrue("URL equality is insufficient across A to B to A", h.state is State.Connecting)
    }

    @Test(timeout = 10000)
    fun currentConnectedAndResponseRemainFunctional() {
        val h = Harness()

        h.service.start(a)

        h.service.switchUrl(b)

        h.connected()

        assertTrue(h.state is State.Connected)
        var received = 0
        h.service.executeRequest(
            RuntimeRequest("chain_getBlockHash", emptyList(), 19),
            callback = object : SocketService.ResponseListener<RpcResponse> {
            override fun onNext(response: RpcResponse) { received++ }
            override fun onError(throwable: Throwable) { throw throwable }
        }
        )

        h.executor.drain()

        h.response(h.peers.last(), 19)
        assertEquals(1, received)
    }

    @Test(timeout = 10000)
    fun alreadyEnteredOldReconnectCannotAdvanceNewWaitingConnection() {
        val h = Harness()

        h.service.start(a)

        h.closed()

        val oldTask = h.scheduled.single()

        lateinit var oldThread: Thread
        synchronized(h.service) {
            oldThread = thread(name = "held-old-reconnect", isDaemon = true) { oldTask.run() }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            while (oldThread.state != Thread.State.BLOCKED && System.nanoTime() < deadline) Thread.yield()
            check(oldThread.state == Thread.State.BLOCKED) { "Old reconnect did not enter service monitor" }
            h.service.switchUrl(b)

            h.closed()
            assertEquals(2, h.scheduled.size)

            assertEquals(2, h.peers.size)

            assertTrue(h.state is State.WaitingForReconnect)
        }
        oldThread.join(3000)

        check(!oldThread.isAlive)
        assertEquals("Already-entered A reconnect advanced B before B's task", 2, h.peers.size)
        assertTrue(h.state is State.WaitingForReconnect)
    }

    @Test(timeout = 10000)
    fun oldErrorCannotInvalidateReplacementOrGuardedRequest() {
        val h = Harness()

        h.service.start(a)

        h.connected()

        val old = h.peers.last()
        val held = h.hold("[SOCKET][ERROR]") {
            old.listener.onError(old.ws, WebSocketException(WebSocketError.SOCKET_INPUT_STREAM_FAILURE, "old"))
        }
        var errors = 0
        try {
            h.service.switchUrl(b)

            h.connected()
            h.service.executeGuardedRequest(
                RuntimeRequest("author_submitExtrinsic", emptyList(), 31), RequestSendGuard { it() },
                object : SocketService.ResponseListener<RpcResponse> {
                    override fun onNext(response: RpcResponse) {}
                    override fun onError(throwable: Throwable) { errors++ }
                }
            )
        } finally {
            h.release(held)
        }
        assertTrue(h.state is State.Connected)

        assertEquals(0, errors)
    }

    @Test(timeout = 10000)
    fun oldConnectErrorCannotInvalidateReplacement() {
        val h = Harness()

        h.service.start(a)

        val old = h.peers.last()
        val held = h.hold("[SOCKET][FAILED TO CONNECT]") {
            old.listener.onConnectError(old.ws, WebSocketException(WebSocketError.SOCKET_INPUT_STREAM_FAILURE, "old"))
        }
        try {
            h.service.switchUrl(b)

        h.connected()
        } finally {
            h.release(held)
        }
        assertTrue(h.state is State.Connected)
    }

    @Test fun disconnectedSwitchStoresPausedIntentUntilExplicitStart() {
        val h = Harness()

        h.service.switchUrl(a)

        assertEquals(State.Paused(a), h.state)

        assertTrue(h.peers.isEmpty())
        h.service.start(b)

        assertEquals(1, h.peers.size)

        assertEquals(b, h.peers.single().url)
        assertTrue(h.state is State.Connecting)
    }

    @Test fun disconnectedSwitchCanExplicitlyResume() {
        val h = Harness()

        h.service.switchUrl(a)

        h.service.resume()

        assertEquals(a, h.peers.single().url)
    }

    @Test fun pausedSwitchAndPausedStartNeverCreateSocket() {
        val h = Harness()

        h.service.start(a, true)

        h.service.switchUrl(b)

        h.service.start(a, true)
        assertEquals(State.Paused(a), h.state)

        assertTrue(h.peers.isEmpty())
        h.service.resume()

        assertEquals(a, h.peers.single().url)
    }

    @Test fun activePauseThenSwitchDoesNotResumeOrReplayGuardedMutation() {
        val h = Harness()

        h.service.start(a)

        h.connected()

        var errors = 0
        h.service.executeGuardedRequest(
            RuntimeRequest("author_submitExtrinsic", emptyList(), 37), RequestSendGuard { it() },
            object : SocketService.ResponseListener<RpcResponse> {
                override fun onNext(response: RpcResponse) { fail("Unexpected response") }
                override fun onError(throwable: Throwable) {
                    assertFalse(Thread.holdsLock(h.service))

                errors++
                }
            }
        )
        h.service.pause()

        h.service.switchUrl(b)

        h.executor.drain()
        assertEquals(1, errors)

        assertEquals(1, h.peers.size)

        assertTrue(h.state is State.Paused)
        h.service.resume()

        h.connected()

        h.executor.drain()

        assertEquals(1, errors)
        verify(h.peers.last().ws, never()).sendText(anyString())
    }

    @Test fun deniedCleartextPreventsFactoryOnStartSwitchAndReconnect() {
        val hosts = mutableListOf<String>()

        val h = Harness(
            SocketTransportPolicy {
            hosts += it

        false
        }
        )
        h.service.start("ws://Denied.Example/rpc?x=1")

        assertTrue(h.peers.isEmpty())

        assertEquals(listOf("denied.example"), hosts)
        h.scheduled.last().run()

        assertTrue(h.peers.isEmpty())

        assertEquals(2, hosts.size)
        h.service.switchUrl("http://second.invalid/rpc")

        assertTrue(h.peers.isEmpty())

        assertEquals("second.invalid", hosts.last())
        assertTrue(h.state is State.WaitingForReconnect)
    }

    @Test fun policyExceptionFailsClosedBeforeFactory() {
        var checks = 0

        val h = Harness(
            SocketTransportPolicy {
            checks++

        error("test denial")
        }
        )
        h.service.start("ws://unavailable.invalid")

        assertEquals(1, checks)

        assertTrue(h.peers.isEmpty())
        assertTrue(h.state is State.WaitingForReconnect)
    }

    @Test fun policyIsRecheckedBeforeConnectAndOnNextAttempt() {
        val hosts = mutableListOf<String>()

        val h = Harness(
            SocketTransportPolicy {
            hosts += it

        hosts.size != 2
        }
        )
        h.service.start("ws://localhost/rpc")

        assertEquals(1, h.peers.size)
        verify(h.peers.single().ws, never()).connectAsynchronously()

        verify(h.peers.single().ws).disconnect()
        assertEquals(listOf("localhost", "localhost"), hosts)
        h.scheduled.last().run()

        assertEquals(4, hosts.size)

        assertEquals(2, h.peers.size)
        verify(h.peers.last().ws).connectAsynchronously()

        h.connected()

        assertTrue(h.state is State.Connected)
    }

    @Test fun tlsDoesNotConsultCleartextPermission() {
        val h = Harness(SocketTransportPolicy { throw AssertionError("TLS queried cleartext policy") })
        h.service.start(a)

        h.connected()

        h.service.switchUrl("https://secure.invalid/rpc")

        h.connected()
        assertEquals(2, h.peers.size)

        assertTrue(h.state is State.Connected)
    }

    @Test fun malformedOrConfusedAuthorityNeverReachesFactoryOrPolicy() {
        val urls = listOf("ftp://host.invalid", "ws:opaque", "ws://", "ws://allowed.invalid@denied.invalid", "ws://host.invalid#fragment", "ws://%68ost.invalid", "ws://host.invalid:0", "ws://host.invalid:65536")
        val h = Harness(SocketTransportPolicy { throw AssertionError("Malformed URL queried policy") })
        for (url in urls) {
            h.service.stop()

        h.service.start(url)

        assertTrue("Factory used for $url", h.peers.isEmpty())
        }
    }

    @Test fun defaultPlatformPolicyCannotSilentlyPermitCleartextOnHost() {
        // Android's host stub throws: production default must reject before factory, never fall back to allow.
        val h = Harness()

        h.service.start("ws://localhost")

        assertTrue(h.peers.isEmpty())

        assertTrue(h.state is State.WaitingForReconnect)
    }

    @Test fun directFactoryConsumerUsesTheSameDefaultGate() {
        val factory = mock(WebSocketFactory::class.java)
        try {
            SocketTransportPolicy.createSocket(factory, "ws://localhost")

            fail("Host Android policy unavailable must fail closed")
        } catch (
            expected: SecurityException
        ) {
            }
        verifyNoInteractions(factory)
    }

    @Test fun obsoleteSchedulerRunnableIsRejectedEvenIfExecutorDeliversIt() {
        val h = Harness()

        h.service.start(a)

        h.closed()

        val old = h.scheduled.single()
        h.service.switchUrl(b)

        val before = h.peers.size

        old.task.run()
        assertEquals(before, h.peers.size)

        assertTrue(h.state is State.Connecting)
    }

    @Test fun synchronousConnectionFailureDefersGuardedCallbackOutsideMonitor() {
        val h = Harness()

        h.service.start(a, true)

        var errors = 0
        h.service.executeGuardedRequest(
            RuntimeRequest("author_submitExtrinsic", emptyList(), 43), RequestSendGuard { it() },
            object : SocketService.ResponseListener<RpcResponse> {
                override fun onNext(response: RpcResponse) { fail("Unexpected success") }
                override fun onError(throwable: Throwable) {
                    assertFalse(Thread.holdsLock(h.service))

                errors++
                }
            }
        )
        h.onConnect = { p -> p.listener.onConnectError(p.ws, WebSocketException(WebSocketError.SOCKET_INPUT_STREAM_FAILURE, "synthetic sync failure")) }
        h.service.resume()

        assertEquals(1, errors)

        assertTrue(h.state is State.WaitingForReconnect)
    }

    @Test fun differentServicesDoNotCancelEachOthersReconnectOrReadQueue() {
        val first = Harness()

        val second = Harness()

        first.service.start(a)

        second.service.start(b)

        second.connected()
        var received = 0
        second.service.executeRequest(
            RuntimeRequest("chain_getBlockHash", emptyList(), 41),
            callback = object : SocketService.ResponseListener<RpcResponse> {
            override fun onNext(response: RpcResponse) { received++ }
            override fun onError(throwable: Throwable) { throw throwable }
        }
        )
        first.service.stop()

        second.executor.drain()

        verify(second.peers.single().ws).sendText(anyString())
        second.response(second.peers.last(), 41)

        assertEquals(1, received)
        second.closed()

        first.service.start(a)

        first.service.stop()

        second.scheduled.last().run()
        assertEquals(2, second.peers.size)
    }

    @Test fun oldCallbackDuringReplacementPublicationIsAlreadyRetired() {
        val h = Harness()

        h.service.start(a)

        val old = h.peers.last()

        var called = false
        h.service.addStateObserver { state ->
            if (!called && state is State.Connecting && state.url == b) {
                called = true

            h.connected(old)
            }
        }
        h.service.switchUrl(b)

        assertTrue(called)

        assertTrue(h.state is State.Connecting)
        assertEquals(b, h.peers.last().url)
    }

    @Test fun newerReentrantSwitchWinsOverOlderConnectSideEffect() {
        val h = Harness()

        h.service.start(a)

        val c = "wss://newest.invalid"

        var switched = false
        h.service.addStateObserver { state ->
            if (!switched && state is State.Connecting && state.url == b) {
                switched = true

            h.service.switchUrl(c)
            }
        }
        h.service.switchUrl(b)

        assertEquals(c, (h.state as State.Connecting).url)
        assertEquals(c, h.peers.last().url)

        assertEquals(listOf(a, c), h.peers.map { it.url })
    }

    @Test fun reentrantReadDoesNotSuppressRequiredConnect() {
        val h = Harness()

        var queued = false
        h.service.addStateObserver { state ->
            if (!queued && state is State.Connecting) {
                queued = true

                h.service.executeRequest(
                    RuntimeRequest("chain_getBlockHash", emptyList(), 47),
                    callback = object : SocketService.ResponseListener<RpcResponse> {
                    override fun onNext(response: RpcResponse) {}
                    override fun onError(throwable: Throwable) { throw throwable }
                }
                )
            }
        }
        h.service.start(a)

        assertEquals(1, h.peers.size)

        h.connected()

        h.executor.drain()
        verify(h.peers.single().ws).sendText(anyString())
    }

    @Test fun reentrantReplacementCannotRegisterOldSubscriptionOnNewSocket() {
        val h = Harness()

        h.service.start(a)

        h.connected()

        val old = h.peers.last()

        var received = 0
        h.service.subscribe(
            RuntimeRequest("chain_subscribeNewHeads", emptyList(), 53),
            callback = object : SocketService.ResponseListener<SubscriptionChange> {
                override fun onNext(response: SubscriptionChange) { received++ }
                override fun onError(throwable: Throwable) { throw throwable }
            },
                unsubscribeMethod = "chain_unsubscribeNewHeads"
        )
        h.executor.drain()

        var armed = false

        var switched = false
        h.service.addStateObserver { state ->
            if (armed && !switched && state is State.Connected) {
                switched = true

            h.service.switchUrl(b)

            h.connected()
            }
        }
        armed = true
        old.listener.onTextMessage(old.ws, """{"jsonrpc":"2.0","id":53,"result":"same-id"}""")
        assertTrue(switched)

        val current = h.peers.last()

        h.executor.drain()
        current.listener.onTextMessage(current.ws, """{"jsonrpc":"2.0","method":"chain_subscription","params":{"subscription":"same-id","result":"new-update"}}""")
        assertEquals("B has not received its own subscription acknowledgement", 0, received)
        current.listener.onTextMessage(current.ws, """{"jsonrpc":"2.0","id":53,"result":"same-id"}""")
        assertEquals("B acknowledgement delivers its buffered update", 1, received)
    }

    @Test(timeout = 10000)
    fun currentScheduledReconnectRemainsFunctional() {
        val h = Harness()

        h.service.start(a)

        h.closed()

        assertEquals(1, h.peers.size)
        h.scheduled.single().run()

        assertEquals(2, h.peers.size)

        assertTrue(h.state is State.Connecting)
    }

    @Test fun endpointAndRpcPayloadMarkersNeverReachLogger() {
        val marker = "synthetic-credential-logging-marker"
        val url = "wss://Mixed.Example/$marker?key=$marker"
        val h = Harness()

        h.service.start(url)

        h.connected()

        var delivered: Any? = null
        h.service.executeRequest(
            RuntimeRequest("chain_getBlockHash", listOf(marker), 67),
            callback = object : SocketService.ResponseListener<RpcResponse> {
            override fun onNext(response: RpcResponse) { delivered = response.result }
            override fun onError(throwable: Throwable) { throw throwable }
        }
        )

        h.executor.drain()
        h.peers.last().listener.onTextMessage(h.peers.last().ws, """{"jsonrpc":"2.0","id":67,"result":"$marker"}""")
        h.service.pause()

        h.service.switchUrl(url)

        h.service.resume()

        h.connected()

        h.service.stop()
        assertEquals(marker, delivered)

        verify(h.factory, times(2)).createSocket(url)
        assertTrue(h.messages.any { it.contains("[CONNECTED]") })

        assertTrue(h.messages.any { it.contains("[DISCONNECTED]") })
        assertTrue(h.messages.none { it.contains(marker) || it.contains("Mixed.Example") })
    }

    @Test fun transportExceptionMarkersNeverReachLogger() {
        val marker = "synthetic-transport-exception-marker"

        val h = Harness()
        h.service.start(a)

        h.connected()
        h.peers.last().listener.onError(h.peers.last().ws, WebSocketException(WebSocketError.SOCKET_INPUT_STREAM_FAILURE, marker))
        h.scheduled.last().run()
        h.peers.last().listener.onConnectError(h.peers.last().ws, WebSocketException(WebSocketError.SOCKET_INPUT_STREAM_FAILURE, marker))
        assertTrue(h.state is State.WaitingForReconnect)
        assertTrue(h.messages.any { it.contains("[ERROR]") })

        assertTrue(h.messages.any { it.contains("[FAILED TO CONNECT]") })
        assertTrue(h.messages.none { it.contains(marker) })
    }

    @Test fun policyAndInvalidUriFailuresDoNotRetainEndpointCausesOrLogMarkers() {
        val marker = "synthetic-policy-credential-marker"

        val policy = SocketTransportPolicy { throw IllegalStateException(marker) }
        for (url in listOf("ws://denied.invalid/$marker?key=$marker", "ws://bad host/$marker")) {
            val failure = runCatching { SocketTransportPolicy.requireAllowed(url, policy) }.exceptionOrNull()
            assertTrue(failure is SecurityException)

            assertNull(failure?.cause)

            assertFalse(failure.toString().contains(marker))
            val h = Harness(policy)

            h.service.start(url)

            assertTrue(h.peers.isEmpty())

            assertTrue(h.messages.isNotEmpty())
            assertTrue(h.messages.none { it.contains(marker) })
        }
    }

    private fun subscriptionPublicationAfterReentry(buffered: Boolean, retire: (SocketService) -> Unit) {
        val h = Harness()

        h.service.start(a)

        h.connected()

        val p = h.peers.last()

        var received = 0
        h.service.subscribe(
            RuntimeRequest("chain_subscribeNewHeads", emptyList(), 79),
            callback = object : SocketService.ResponseListener<SubscriptionChange> {
                override fun onNext(response: SubscriptionChange) { received++ }
                override fun onError(throwable: Throwable) { throw throwable }
            },
                unsubscribeMethod = "chain_unsubscribeNewHeads"
        )
        h.executor.drain()
        val update = """{"jsonrpc":"2.0","method":"chain_subscription","params":{"subscription":"reentrant-id","result":"update"}}"""
        val acknowledgement = """{"jsonrpc":"2.0","id":79,"result":"reentrant-id"}"""
        if (buffered) p.listener.onTextMessage(p.ws, update) else p.listener.onTextMessage(p.ws, acknowledgement)
        var armed = false

        var retired = false
        h.service.addStateObserver { state ->
            if (armed && !retired && state is State.Connected && state.subscriptions.isNotEmpty()) {
                retired = true

                retire(h.service)
            }
        }
        armed = true
        p.listener.onTextMessage(p.ws, if (buffered) acknowledgement else update)
        assertTrue("Observer did not retire the old subscription publication", retired)
        assertEquals("Old subscription published after observer retired its socket", 0, received)
    }

    @Test fun liveSubscriptionDoesNotPublishAfterReentrantSwitch() = subscriptionPublicationAfterReentry(false) {
        it.switchUrl(b)
    }

    @Test fun liveSubscriptionDoesNotPublishAfterReentrantPause() = subscriptionPublicationAfterReentry(false) {
        it.pause()
    }

    @Test fun liveSubscriptionDoesNotPublishAfterReentrantStop() = subscriptionPublicationAfterReentry(false) { it.stop() }

    @Test fun bufferedSubscriptionDoesNotPublishAfterReentrantSwitch() = subscriptionPublicationAfterReentry(true) {
        it.switchUrl(b)
    }

    @Test fun bufferedSubscriptionDoesNotPublishAfterReentrantPause() = subscriptionPublicationAfterReentry(true) {
        it.pause()
    }

    @Test fun bufferedSubscriptionDoesNotPublishAfterReentrantStop() = subscriptionPublicationAfterReentry(true) {
        it.stop()
    }

    @Test fun currentSubscriptionStillPublishesWithReadOnlyObserver() {
        for (buffered in listOf(false, true)) {
            val h = Harness()

            h.service.start(a)

            h.connected()

            val p = h.peers.last()

            var received = 0
            h.service.subscribe(
                RuntimeRequest("chain_subscribeNewHeads", emptyList(), 83),
                callback = object : SocketService.ResponseListener<SubscriptionChange> {
                    override fun onNext(response: SubscriptionChange) { received++ }
                    override fun onError(throwable: Throwable) { throw throwable }
                },
                    unsubscribeMethod = "chain_unsubscribeNewHeads"
            )
            h.executor.drain()

            h.service.addStateObserver { assertTrue(h.service.started()) }
            val update = """{"jsonrpc":"2.0","method":"chain_subscription","params":{"subscription":"current-id","result":"update"}}"""
            val acknowledgement = """{"jsonrpc":"2.0","id":83,"result":"current-id"}"""
            p.listener.onTextMessage(p.ws, if (buffered) update else acknowledgement)
            p.listener.onTextMessage(p.ws, if (buffered) acknowledgement else update)
            assertEquals(1, received)
        }
    }

    @Test(timeout = 10000)
    fun concurrentObserverInitialCallbackAndStartDoNotDeadlock() {
        val h = Harness()

        val entered = CountDownLatch(1)

        val release = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()

        var initial = true
        val observer = thread(name = "observer-registration", isDaemon = true) {
            try {
                h.service.addStateObserver {
                    if (initial) {
                        initial = false

                    entered.countDown()

                    check(release.await(3, TimeUnit.SECONDS))

                    h.service.pause()
                    }
                }
            } catch (error: Throwable) {
                failure.set(error)
            }
        }
        check(entered.await(3, TimeUnit.SECONDS))
        val starter = thread(name = "concurrent-start", isDaemon = true) {
            try {
                h.service.start(a)
            } catch (error: Throwable) {
                failure.set(error)
            }
        }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        while (starter.state != Thread.State.BLOCKED && System.nanoTime() < deadline) Thread.yield()
        check(starter.state == Thread.State.BLOCKED) { "Start did not reach held registration" }
        release.countDown()

        observer.join(1000)

        starter.join(1000)
        assertFalse("Observer/start lock inversion: observer=${observer.state}, start=${starter.state}", observer.isAlive || starter.isAlive)
        failure.get()?.let { throw it }

        assertTrue(h.state is State.Connecting)
    }

    @Test fun initialObserverGuardedFailureIsDispatchedOutsideServiceMonitor() {
        val h = Harness()

        var failures = 0
        h.service.addStateObserver {
            h.service.executeGuardedRequest(
                RuntimeRequest("author_submitExtrinsic", emptyList(), 89), RequestSendGuard { it() },
                object : SocketService.ResponseListener<RpcResponse> {
                    override fun onNext(response: RpcResponse) { fail("Unexpected response") }
                    override fun onError(throwable: Throwable) {
                        assertFalse(Thread.holdsLock(h.service))

                    failures++
                    }
                }
            )
        }
        assertEquals(1, failures)
    }
}
