package jp.co.soramitsu.fearless_utils.wsrpc

import com.google.gson.Gson
import com.neovisionaries.ws.client.WebSocket
import com.neovisionaries.ws.client.WebSocketFactory
import com.neovisionaries.ws.client.WebSocketListener
import jp.co.soramitsu.fearless_utils.wsrpc.logging.Logger
import jp.co.soramitsu.fearless_utils.wsrpc.recovery.Reconnector
import jp.co.soramitsu.fearless_utils.wsrpc.request.RequestExecutor
import jp.co.soramitsu.fearless_utils.wsrpc.request.RequestSendGuard
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse
import jp.co.soramitsu.fearless_utils.wsrpc.state.SocketStateMachine.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class SocketConfigurationTest {
    private val a = "wss://a.invalid/public"
    private val b = "wss://b.invalid/public"
    private class Peer(val url: String, val socket: WebSocket) { lateinit var listener: WebSocketListener }
    private class Harness {
        val peers = mutableListOf<Peer>()
        val factory = mock(WebSocketFactory::class.java)
        val scheduler = mock(ScheduledExecutorService::class.java)
        val scheduled = mutableListOf<Runnable>()
        val logger = object : Logger {
            override fun log(message: String?) {}
            override fun log(throwable: Throwable?) {}
        }
        val service: SocketService
        init {
            `when`(scheduler.schedule(any(Runnable::class.java), anyLong(), any(TimeUnit::class.java))).thenAnswer {
                scheduled += it.getArgument<Runnable>(0)
                mock(ScheduledFuture::class.java)
            }
            `when`(factory.createSocket(anyString())).thenAnswer { call ->
                val socket = mock(WebSocket::class.java)
                val peer = Peer(call.getArgument(0), socket)
                peers += peer
                `when`(socket.addListener(any(WebSocketListener::class.java))).thenAnswer {
                    peer.listener = it.getArgument(0)

                socket
                }
                `when`(socket.connectAsynchronously()).thenReturn(socket)
                socket
            }
            service = SocketService(Gson(), logger, factory, Reconnector(executor = scheduler), RequestExecutor(mock(ExecutorService::class.java)))
        }
        fun connected(peer: Peer = peers.last()) = peer.listener.onConnected(peer.socket, mutableMapOf())
        fun configure(url: String, connect: Boolean = true): SocketService.Configuration =
            service.admitConfiguration { true }.also { service.configure(it, url, connect) }
    }

    @Test fun connectedObservationExpiresAcrossSameConfigurationDisconnectAndReconnect() {
        val h = Harness()

        h.configure(a)

        h.connected()
        val connected = h.service.configurationSnapshot()
        assertTrue(connected.isCurrent())
        h.peers.single().listener.onStateChanged(h.peers.single().socket, com.neovisionaries.ws.client.WebSocketState.CLOSED)
        assertFalse(connected.isCurrent())
        val waiting = h.service.configurationSnapshot()
        assertTrue(waiting.isCurrent())
        h.scheduled.single().run()
        assertFalse(waiting.isCurrent())
        h.connected()
        assertFalse(connected.isCurrent())
        assertTrue(h.service.configurationSnapshot().isCurrent())
    }

    @Test fun ordinaryRequestDoesNotExpireSamePhysicalConnectedObservation() {
        val h = Harness()

        h.configure(a)

        h.connected()
        val connected = h.service.configurationSnapshot()
        h.service.executeRequest(
            RuntimeRequest("chain_getHeader", emptyList(), 37),
            callback =
            object : SocketService.ResponseListener<RpcResponse> {
                override fun onNext(response: RpcResponse) {}
                override fun onError(throwable: Throwable) { fail("Unexpected error") }
            }
        )
        assertTrue(connected.isCurrent())
    }

    @Test fun changedPredicateExpiresRetainedConnectedObservation() {
        val h = Harness()

        var current = true
        val ticket = h.service.admitConfiguration { current }
        h.service.configure(ticket, a, true)

        h.connected()
        val connected = h.service.configurationSnapshot()
        assertTrue(connected.isCurrent())
        current = false
        assertFalse(connected.isCurrent())
    }

    @Test fun observedConnectingReceiptRemainsCurrentAfterItsTransportOpens() {
        val h = Harness()

        var observation: SocketService.ConfigurationSnapshot? = null
        h.service.addStateObserver { if (it is State.Connecting) observation = h.service.configurationSnapshot() }
        h.configure(a)
        assertEquals(1, h.peers.size)
        assertTrue(checkNotNull(observation).isCurrent())
    }

    @Test fun connectingObservationCannotReviveAcrossAttemptPhaseAba() {
        val h = Harness()

        h.configure(a)
        val old = h.service.configurationSnapshot()
        h.peers.single().listener.onStateChanged(h.peers.single().socket, com.neovisionaries.ws.client.WebSocketState.CLOSED)
        h.scheduled.single().run()
        assertTrue(h.service.configurationSnapshot().state is State.Connecting)
        assertFalse(old.isCurrent())
        assertTrue(h.service.configurationSnapshot().isCurrent())
    }

    @Test fun supersededTicketCannotOpenTransport() {
        val h = Harness()
        val old = h.service.admitConfiguration { true }
        val latest = h.service.admitConfiguration { true }
        assertNull(h.service.configure(old, a, true))
        assertTrue(h.peers.isEmpty())
        assertNotNull(h.service.configure(latest, b, true))
        assertEquals(listOf(b), h.peers.map { it.url })
    }

    @Test fun changedPurePredicateRevokesAppliedObservationImmediately() {
        val h = Harness()

        var current = true
        val configuration = h.service.admitConfiguration { current }
        h.service.configure(configuration, a, true)

        h.connected()
        assertTrue(h.service.configurationSnapshot().applied)
        current = false
        assertFalse(h.service.configurationSnapshot().applied)
        assertNull(h.service.configure(configuration, a, true))
        assertEquals(1, h.peers.size)
    }

    @Test fun failedPredicateCannotOpenTransport() {
        val h = Harness()
        val configuration = h.service.admitConfiguration { error("public test stale predicate") }
        assertNull(h.service.configure(configuration, a, true))
        assertTrue(h.peers.isEmpty())
        assertFalse(h.service.configurationSnapshot().applied)
    }

    @Test fun foreignServiceConfigurationIsRejected() {
        val first = Harness()

        val second = Harness()
        assertThrows(IllegalArgumentException::class.java) {
            second.service.configure(first.service.admitConfiguration { true }, a, true)
        }
        assertTrue(second.peers.isEmpty())
    }

    @Test fun stoppedConfigurationStoresDesiredUrlUntilAllowed() {
        val h = Harness()

        h.configure(a, false)
        assertEquals(State.Paused(a), h.service.configurationSnapshot().state)
        assertTrue(h.peers.isEmpty())
        h.configure(b, false)
        assertEquals(State.Paused(b), h.service.configurationSnapshot().state)
        assertTrue(h.peers.isEmpty())
        h.configure(b, true)
        assertEquals(listOf(b), h.peers.map { it.url })
    }

    @Test fun sameTicketReapplicationDoesNotRestartConnectingAttempt() {
        val h = Harness()

        val configuration = h.configure(a)
        h.service.configure(configuration, a, true)
        assertEquals(1, h.peers.size)
    }

    @Test fun freshSameUrlConnectingTicketRequiresNewPhysicalAttempt() {
        val h = Harness()

        h.configure(a)

        val old = h.peers.single()
        h.service.admitConfiguration { true } // intervening durable B intent, not yet applied
        val latest = h.configure(a)
        assertEquals(2, h.peers.size)
        h.connected(old)
        assertTrue(h.service.configurationSnapshot().state is State.Connecting)
        h.connected()
        val result = h.service.configurationSnapshot()
        assertSame(latest, result.configuration)
        assertTrue(result.applied)
        assertTrue(result.state is State.Connected)
    }

    @Test fun connectedAfterTicketAdmissionCannotQualifyEarlierAttempt() {
        val h = Harness()

        h.configure(a)

        val old = h.peers.single()
        val next = h.service.admitConfiguration { true }
        h.connected(old)
        h.service.configure(next, a, true)
        assertEquals(2, h.peers.size)
        assertTrue(h.service.configurationSnapshot().state is State.Connecting)
        h.connected(old)
        assertTrue(h.service.configurationSnapshot().state is State.Connecting)
        h.connected()
        assertTrue(h.service.configurationSnapshot().state is State.Connected)
    }

    @Test fun newTicketRetiresWaitingAttemptEvenWhenUrlMatches() {
        val h = Harness()

        h.configure(a)
        h.peers.single().listener.onStateChanged(h.peers.single().socket, com.neovisionaries.ws.client.WebSocketState.CLOSED)
        assertTrue(h.service.configurationSnapshot().state is State.WaitingForReconnect)
        val old = h.scheduled.single()
        h.configure(a)
        assertEquals(2, h.peers.size)
        assertTrue(h.service.configurationSnapshot().state is State.Connecting)
        old.run()
        assertEquals(2, h.peers.size)
        h.connected()
        assertTrue(h.service.configurationSnapshot().state is State.Connected)
    }

    @Test fun sameTicketRetainsCurrentWaitingAttempt() {
        val h = Harness()

        val ticket = h.configure(a)
        h.peers.single().listener.onStateChanged(h.peers.single().socket, com.neovisionaries.ws.client.WebSocketState.CLOSED)
        h.service.configure(ticket, a, true)
        assertEquals(1, h.peers.size)
        h.scheduled.single().run()
        assertEquals(2, h.peers.size)
        h.connected()
        assertTrue(h.service.configurationSnapshot().state is State.Connected)
    }

    @Test fun alreadyObservedSameEndpointConnectionMayBeRevalidated() {
        val h = Harness()

        h.configure(a)

        h.connected()
        val next = h.configure(a)
        assertEquals(1, h.peers.size)
        assertSame(next, h.service.configurationSnapshot().configuration)
        assertTrue(h.service.configurationSnapshot().state is State.Connected)
        assertTrue(h.service.configurationSnapshot().applied)
    }

    @Test fun pendingReentrantConfigurationSuppressesOlderConnectEffect() {
        val h = Harness()

        var next: SocketService.Configuration? = null
        h.service.addStateObserver { state ->
            if (state is State.Connecting && next == null) next = h.service.admitConfiguration { true }
        }
        h.configure(a)
        assertTrue(h.peers.isEmpty())
        assertFalse(h.service.configurationSnapshot().applied)
        h.service.configure(checkNotNull(next), b, true)
        assertEquals(listOf(b), h.peers.map { it.url })
    }

    @Test fun pendingReentrantConfigurationCannotSuppressRequiredDisconnect() {
        val h = Harness()

        h.configure(a)

        h.connected()

        val old = h.peers.single()
        var next: SocketService.Configuration? = null
        h.service.addStateObserver { state ->
            if (state is State.Paused && next == null) next = h.service.admitConfiguration { true }
        }
        h.configure(b, false)
        verify(old.socket).disconnect()
        assertFalse(h.service.configurationSnapshot().applied)
        h.service.configure(checkNotNull(next), b, false)
        assertEquals(State.Paused(b), h.service.configurationSnapshot().state)
        assertEquals(1, h.peers.size)
    }

    @Test fun reentrantNewerAppliedConfigurationWinsOverOuterIntent() {
        val h = Harness()

        var replaced = false
        h.service.addStateObserver { state ->
            if (state is State.Connecting && !replaced) {
                replaced = true

            h.configure(b)
            }
        }
        h.configure(a)
        assertEquals(listOf(b), h.peers.map { it.url })
        assertEquals(b, (h.service.configurationSnapshot().state as State.Connecting).url)
    }

    @Test fun pausingConfigurationKeepsGuardedFailureCallbackOutsideMonitor() {
        val h = Harness()

        h.configure(a)

        h.connected()

        var errors = 0
        h.service.executeGuardedRequest(
            RuntimeRequest("author_submitExtrinsic", emptyList(), 31), RequestSendGuard { it() },
            object : SocketService.ResponseListener<RpcResponse> {
                override fun onNext(response: RpcResponse) { fail("Unexpected response") }
                override fun onError(throwable: Throwable) {
                    assertFalse(Thread.holdsLock(h.service))

                errors++
                }
            }
        )
        h.configure(b, false)
        assertEquals(1, errors)
        assertEquals(State.Paused(b), h.service.configurationSnapshot().state)
    }
}
