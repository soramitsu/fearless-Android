package jp.co.soramitsu.core.runtime

import com.google.gson.Gson
import com.neovisionaries.ws.client.WebSocket
import com.neovisionaries.ws.client.WebSocketError
import com.neovisionaries.ws.client.WebSocketException
import com.neovisionaries.ws.client.WebSocketFactory
import com.neovisionaries.ws.client.WebSocketListener
import com.neovisionaries.ws.client.WebSocketState
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.core.runtime.ChainConnection.ExternalRequirement
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.logging.Logger
import jp.co.soramitsu.fearless_utils.wsrpc.recovery.Reconnector
import jp.co.soramitsu.fearless_utils.wsrpc.request.RequestExecutor
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** Failed attempts belong to one logical binding and come only from socket transitions. */
class ChainConnectionFailedAttemptsTest {
    private val a = ChainNode("wss://a.invalid/", "A", true, true)
    private val b = ChainNode("wss://b.invalid/", "B", true, true)
    private class Peer(val url: String, val socket: WebSocket) { lateinit var listener: WebSocketListener }
    private class Harness(nodes: List<ChainNode>) {
        val peers = mutableListOf<Peer>()
        val reconnects = mutableListOf<Runnable>()
        var current = true
        val service: SocketService
        val connection: ChainConnection
        init {
            val factory = mock(WebSocketFactory::class.java)
            `when`(factory.createSocket(anyString())).thenAnswer { call ->
                val peer = Peer(call.getArgument(0), mock(WebSocket::class.java))
                peers += peer
                `when`(peer.socket.addListener(any(WebSocketListener::class.java))).thenAnswer {
                    peer.listener = it.getArgument(0)
                    peer.socket
                }
                `when`(peer.socket.connectAsynchronously()).thenReturn(peer.socket)
                peer.socket
            }
            // The backoff timer only fires when a test calls retry().
            val timer = mock(ScheduledExecutorService::class.java)
            `when`(timer.schedule(any(Runnable::class.java), anyLong(), any(TimeUnit::class.java))).thenAnswer {
                reconnects += it.getArgument<Runnable>(0)
                mock(ScheduledFuture::class.java)
            }
            val logger = object : Logger { override fun log(message: String?) {}
            override fun log(throwable: Throwable?) {} }
            service = SocketService(Gson(), logger, factory, Reconnector(executor = timer), RequestExecutor(mock(ExecutorService::class.java)))
            connection = ChainConnection(chain(nodes), service, { it.url }, { ExternalRequirement.ALLOWED })
        }
        val failedAttempts: Int get() = connection.runtime.value.failedAttempts
        fun chain(nodes: List<ChainNode>): IChain = mock(IChain::class.java).also {
            `when`(it.id).thenReturn("a")
            `when`(it.nodes).thenReturn(nodes)
        }
        fun reconcile(nodes: List<ChainNode>) = connection.reconcile(chain(nodes), ExternalRequirement.ALLOWED) { current }
        fun connected(peer: Peer = peers.last()) = peer.listener.onConnected(peer.socket, mutableMapOf())
        fun failed(peer: Peer = peers.last()) =
            peer.listener.onConnectError(peer.socket, WebSocketException(WebSocketError.SOCKET_CONNECT_ERROR, "synthetic refusal"))
        fun closed(peer: Peer = peers.last()) = peer.listener.onStateChanged(peer.socket, WebSocketState.CLOSED)
        fun retry() = reconnects.last().run()
        fun request() = service.executeRequest(RuntimeRequest("system_health", emptyList()), callback = object :
            SocketService.ResponseListener<RpcResponse> {
            override fun onNext(response: RpcResponse) {}
            override fun onError(throwable: Throwable) {}
        })
    }

    @Test fun everyFailedConnectionToTheConfiguredNodeCounts() {
        val h = Harness(listOf(a))
        h.reconcile(listOf(a))
        assertEquals(0, h.failedAttempts)
        h.failed()
        assertEquals(1, h.failedAttempts)
        assertEquals(EndpointRuntimeStatus.Phase.CONNECTING, h.connection.runtime.value.phase)
        h.retry()
        // Dialing again is not itself a failure.
        assertEquals(1, h.failedAttempts)
        h.failed()
        h.retry()
        h.failed()
        assertEquals(3, h.failedAttempts)
        assertEquals(EndpointRuntimeStatus.Phase.CONNECTING, h.connection.runtime.value.phase)
        assertEquals(listOf(a.url, a.url, a.url), h.peers.map { it.url })
    }

    @Test fun aRequestThatRedialsAtOnceKeepsEarlierFailures() {
        val h = Harness(listOf(a))
        h.reconcile(listOf(a))
        h.failed()
        // The state machine restarts its own attempt number for a new request; the node still failed before.
        h.request()
        assertEquals(2, h.peers.size)
        h.failed()
        assertEquals(2, h.failedAttempts)
    }

    @Test fun oneFailureObservedTwiceCountsOnce() {
        val h = Harness(listOf(a))
        h.reconcile(listOf(a))
        val pending = h.request()
        h.failed()
        assertEquals(1, h.failedAttempts)
        // Cancelling the queued request republishes the same waiting state.
        pending.cancel()
        assertEquals(1, h.failedAttempts)
        assertEquals(1, h.peers.size)
    }

    @Test fun aConnectionClearsTheCountAndALaterDropCountsAgain() {
        val h = Harness(listOf(a))
        h.reconcile(listOf(a))
        h.failed()
        h.retry()
        h.failed()
        assertEquals(2, h.failedAttempts)
        h.retry()
        h.connected()
        assertEquals(0, h.failedAttempts)
        assertEquals(EndpointRuntimeStatus.Phase.CONNECTED, h.connection.runtime.value.phase)
        h.closed()
        assertEquals(1, h.failedAttempts)
    }

    @Test fun anotherSelectedNodeStartsFromZero() {
        val h = Harness(listOf(a))
        h.reconcile(listOf(a))
        h.failed()
        h.retry()
        h.failed()
        h.retry()
        h.failed()
        assertEquals(3, h.failedAttempts)
        h.reconcile(listOf(a.copy(isActive = false), b))
        assertEquals(0, h.failedAttempts)
        assertEquals(b.url, h.peers.last().url)
        assertTrue(h.connection.runtime.value.isCurrentFor(b))
        h.failed()
        assertEquals(1, h.failedAttempts)
    }

    @Test fun anExpiredStatusReportsNoFailures() {
        val h = Harness(listOf(a))
        h.reconcile(listOf(a))
        h.failed()
        val held = h.connection.runtime.value
        assertEquals(1, held.failedAttempts)
        h.current = false
        assertEquals(0, held.failedAttempts)
        assertEquals(EndpointRuntimeStatus.Phase.PENDING, held.phase)
    }
}
