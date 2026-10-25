package jp.co.soramitsu.core.runtime

import com.google.gson.Gson
import com.neovisionaries.ws.client.WebSocket
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.contains
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService

class ChainConnectionConfigurationTest {
    private val a = ChainNode("wss://a.invalid/", "A", true, true)
    private val b = ChainNode("wss://b.invalid/", "B", true, false)
    private class Peer(val url: String, val socket: WebSocket) { lateinit var listener: WebSocketListener }
    private class Harness(nodes: List<ChainNode>, val transform: (ChainNode) -> String = { it.url }) {
        val peers = mutableListOf<Peer>()
        val factory = mock(WebSocketFactory::class.java)
        var permission = ExternalRequirement.ALLOWED
        var current = true
        var mappings = 0
        val service: SocketService
        val connection: ChainConnection
        init {
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
            val logger = object : Logger { override fun log(message: String?) {}
            override fun log(throwable: Throwable?) {} }
            service = SocketService(Gson(), logger, factory, Reconnector(executor = mock(ScheduledExecutorService::class.java)), RequestExecutor(mock(ExecutorService::class.java)))
            connection = ChainConnection(chain(nodes), service, {
                mappings++
            transform(it)
            }, { permission })
        }
        fun chain(nodes: List<ChainNode>, id: String = "a"): IChain = mock(IChain::class.java).also {
            `when`(it.id).thenReturn(id)
            `when`(it.nodes).thenReturn(nodes)
        }
        fun reconcile(nodes: List<ChainNode>) = connection.reconcile(chain(nodes), permission) { current }
        fun connected(peer: Peer = peers.last()) = peer.listener.onConnected(peer.socket, mutableMapOf())
        fun disconnected(peer: Peer = peers.last()) = peer.listener.onStateChanged(peer.socket, WebSocketState.CLOSED)
    }

    @Test fun activeSecondRowIsTheLogicalAndMappedConnectionTarget() {
        val nodes = listOf(a.copy(isActive = false), b)
        val h = Harness(nodes) { it.url + "public-test-credential" }
        h.reconcile(nodes)
        assertEquals(listOf(b.url + "public-test-credential"), h.peers.map { it.url })
        assertEquals(1, h.mappings)
        assertEquals(EndpointRuntimeStatus.Phase.CONNECTING, h.connection.runtime.value.phase)
        h.connected()
        val status = h.connection.runtime.value
        assertEquals(EndpointRuntimeStatus.Phase.CONNECTED, status.phase)
        assertTrue(status.applied)
        assertTrue(status.isCurrentFor(b))
        assertFalse(status.toString().contains("public-test-credential"))
        assertFalse(status.toString().contains(b.url))
    }

    @Test fun missingSelectionStopsAnExistingConnectionWithoutFallback() {
        val h = Harness(listOf(a))
        h.reconcile(listOf(a))
        h.connected()
        val peer = h.peers.single()
        h.reconcile(listOf(a.copy(isActive = false), b.copy(isActive = false)))
        assertEquals(EndpointRuntimeStatus.Phase.UNAVAILABLE, h.connection.runtime.value.phase)
        assertNull(h.connection.selectedNode)
        verify(peer.socket).disconnect()
        assertEquals(1, h.peers.size)
    }

    @Test fun ambiguousSelectionNeverOpensTransport() {
        val h = Harness(listOf(a, b))
        h.reconcile(listOf(a, b))
        assertEquals(EndpointRuntimeStatus.Phase.UNAVAILABLE, h.connection.runtime.value.phase)
        assertTrue(h.peers.isEmpty())
    }

    @Test fun stoppedAndForbiddenConfigureWithoutConnectingUntilAllowed() {
        for (permission in listOf(ExternalRequirement.STOPPED, ExternalRequirement.FORBIDDEN)) {
            val h = Harness(listOf(a))
            h.permission = permission
            h.reconcile(listOf(a))
            assertTrue(h.peers.isEmpty())
            assertEquals(EndpointRuntimeStatus.Phase.PAUSED, h.connection.runtime.value.phase)
            h.reconcile(listOf(b))
            assertTrue(h.peers.isEmpty())
            h.permission = ExternalRequirement.ALLOWED
            h.connection.applyExternalRequirement(h.permission)
            assertEquals(listOf(b.url), h.peers.map { it.url })
            h.connected()
            assertTrue(h.connection.runtime.value.isCurrentFor(b))
        }
    }

    @Test fun permissionRevocationImmediatelyExpiresHeldConnectedReceipt() {
        val h = Harness(listOf(a))
        h.reconcile(listOf(a))
        h.connected()
        val old = h.connection.runtime.value
        h.permission = ExternalRequirement.FORBIDDEN
        assertEquals(EndpointRuntimeStatus.Phase.PENDING, old.phase)
        assertFalse(old.applied)
        h.connection.applyExternalRequirement(h.permission)
        assertEquals(EndpointRuntimeStatus.Phase.PAUSED, h.connection.runtime.value.phase)
        verify(h.peers.single().socket).disconnect()
    }

    @Test fun writerRevocationPreventsHeldSocketSuccessFromPublishingCurrent() {
        val h = Harness(listOf(a))
        h.reconcile(listOf(a))
        val old = h.connection.runtime.value
        h.current = false
        h.connected()
        assertEquals(EndpointRuntimeStatus.Phase.PENDING, old.phase)
        assertEquals(EndpointRuntimeStatus.Phase.PENDING, h.connection.runtime.value.phase)
        assertFalse(h.connection.runtime.value.isCurrentFor(a))
    }

    @Test fun distinctLogicalRowsSharingTransportKeepTheirOwnReceipt() {
        val h = Harness(listOf(a)) { "wss://same.invalid/public" }
        h.reconcile(listOf(a))
        h.connected()
        val old = h.connection.runtime.value
        h.reconcile(listOf(b))
        assertEquals(1, h.peers.size)
        assertEquals(EndpointRuntimeStatus.Phase.PENDING, old.phase)
        assertFalse(h.connection.runtime.value.isCurrentFor(a))
        assertTrue(h.connection.runtime.value.isCurrentFor(b))
        assertEquals(EndpointRuntimeStatus.Phase.CONNECTED, h.connection.runtime.value.phase)
    }

    @Test fun activeNameChangeAndInactiveAdditionPublishFreshSnapshot() {
        val h = Harness(listOf(a))
        h.reconcile(listOf(a))
        h.connected()
        val renamed = a.copy(name = "Renamed")
        val nodes = listOf(renamed, b.copy(isActive = false))
        h.reconcile(nodes)
        assertEquals(nodes, h.connection.nodes)
        assertEquals(nodes, h.connection.chain.nodes)
        assertEquals(1, h.peers.size)
        assertTrue(h.connection.runtime.value.isCurrentFor(renamed))
        assertFalse(h.connection.runtime.value.isCurrentFor(a))
    }

    @Test fun oldPhysicalConnectionCannotCompleteReplacement() {
        val h = Harness(listOf(a))
        h.reconcile(listOf(a))
        val old = h.peers.single()
        h.reconcile(listOf(b))
        h.connected(old)
        assertEquals(EndpointRuntimeStatus.Phase.CONNECTING, h.connection.runtime.value.phase)
        h.connected()
        assertEquals(EndpointRuntimeStatus.Phase.CONNECTED, h.connection.runtime.value.phase)
        assertTrue(h.connection.runtime.value.isCurrentFor(b))
    }

    @Test fun heldConnectedReceiptExpiresOnPhysicalDisconnect() {
        val h = Harness(listOf(a))
        h.reconcile(listOf(a))
        h.connected()
        val old = h.connection.runtime.value
        h.disconnected()
        assertEquals(EndpointRuntimeStatus.Phase.PENDING, old.phase)
        assertFalse(old.applied)
        assertEquals(EndpointRuntimeStatus.Phase.CONNECTING, h.connection.runtime.value.phase)
    }

    @Test fun retiredConnectionCannotReconcileOrPublishOldEvents() {
        val h = Harness(listOf(a))
        h.reconcile(listOf(a))
        val peer = h.peers.single()
        h.connection.finish()
        h.connected(peer)
        assertEquals(EndpointRuntimeStatus.Phase.NOT_IN_USE, h.connection.runtime.value.phase)
        assertThrows(IllegalStateException::class.java) { h.reconcile(listOf(b)) }
        assertEquals(1, h.peers.size)
    }

    @Test fun mismatchedChainCannotReplaceOwner() {
        val h = Harness(listOf(a))
        assertThrows(IllegalArgumentException::class.java) {
            h.connection.reconcile(h.chain(listOf(b), "other"), h.permission) { true }
        }
        assertTrue(h.peers.isEmpty())
    }

    @Test fun mappingFailurePropagatesWithoutOpeningTransport() {
        val h = Harness(listOf(a)) { error("synthetic mapping failure") }
        assertThrows(IllegalStateException::class.java) { h.reconcile(listOf(a)) }
        assertTrue(h.peers.isEmpty())
    }

    @Test fun repeatedAllowedPermissionDoesNotReplaceConnectingAttempt() {
        val h = Harness(listOf(a))
        h.reconcile(listOf(a))
        val before = h.connection.runtime.value
        h.connection.applyExternalRequirement(ExternalRequirement.ALLOWED)
        assertEquals(1, h.peers.size)
        assertTrue(before.isCurrentFor(a))
        h.connected()
        assertEquals(EndpointRuntimeStatus.Phase.CONNECTED, h.connection.runtime.value.phase)
    }

    @Test fun repeatedAllowedPermissionPreservesConnectedReceipt() {
        val h = Harness(listOf(a))
        h.reconcile(listOf(a))
        h.connected()
        val before = h.connection.runtime.value
        h.connection.applyExternalRequirement(ExternalRequirement.ALLOWED)
        assertTrue(before.isCurrentFor(a))
        assertEquals(1, h.peers.size)
    }

    @Test fun repeatedStoppedPermissionPreservesPausedReceiptUntilRealPermissionChange() {
        val h = Harness(listOf(a))
        h.permission = ExternalRequirement.STOPPED
        h.reconcile(listOf(a))
        val before = h.connection.runtime.value
        h.connection.applyExternalRequirement(ExternalRequirement.STOPPED)
        assertTrue(before.isCurrentFor(a))
        assertTrue(h.peers.isEmpty())
        h.permission = ExternalRequirement.ALLOWED
        h.connection.applyExternalRequirement(ExternalRequirement.ALLOWED)
        assertEquals(1, h.peers.size)
        assertFalse(before.isCurrentFor(a))
    }

    @Test fun unchangedAllowedPermissionStillRepairsAnExplicitPhysicalStop() {
        val h = Harness(listOf(a))
        h.reconcile(listOf(a))
        h.connected()
        h.service.stop()
        h.connection.applyExternalRequirement(ExternalRequirement.ALLOWED)
        assertEquals(2, h.peers.size)
        h.connected()
        assertEquals(EndpointRuntimeStatus.Phase.CONNECTED, h.connection.runtime.value.phase)
    }

    @Test fun repeatedAllowedDuringNewIntentDoesNotPauseOldPhysicalConnection() {
        val h = Harness(listOf(a))
        h.reconcile(listOf(a))
        h.connected()
        val old = h.connection.runtime.value
        h.current = false
        h.connection.applyExternalRequirement(ExternalRequirement.ALLOWED)
        assertFalse(old.isCurrentFor(a))
        verify(h.peers.single().socket, never()).disconnect()
        h.current = true
        h.reconcile(listOf(a))
        assertEquals(1, h.peers.size)
        assertEquals(EndpointRuntimeStatus.Phase.CONNECTED, h.connection.runtime.value.phase)
    }

    @Test fun intentRetirementBetweenDuplicatePermissionCurrencyChecksCannotPauseTransport() {
        val h = Harness(listOf(a))
        var armed = false
        var checks = 0
        h.connection.reconcile(h.chain(listOf(a)), ExternalRequirement.ALLOWED) { !armed || ++checks == 1 }
        h.connected()
        armed = true
        h.connection.applyExternalRequirement(ExternalRequirement.ALLOWED)
        verify(h.peers.single().socket, never()).disconnect()
        assertEquals(1, h.peers.size)
    }
}
