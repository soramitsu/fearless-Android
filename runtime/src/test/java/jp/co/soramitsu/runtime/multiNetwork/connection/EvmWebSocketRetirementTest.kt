package jp.co.soramitsu.runtime.multiNetwork.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.web3j.protocol.websocket.WebSocketClient
import java.net.URI

class EvmWebSocketRetirementTest {
    private val uri = URI("wss://localhost")

    @Test
    fun `a retired socket cannot start its handshake`() {
        val socket = RecordingSocket(uri)
        val transport = EVMConnection.WSS(uri.toString(), socket) {}
        transport.close()
        transport.connect()

        assertEquals(0, socket.handshakes)
        assertEquals(0, socket.sends)
    }

    @Test
    fun `a handshake that finishes after retirement closes before probing`() {
        val socket = RecordingSocket(uri)
        val transport = EVMConnection.WSS(uri.toString(), socket) {}
        socket.duringHandshake = transport::close
        transport.connect()

        assertEquals(1, socket.handshakes)
        assertFalse(socket.opened)
        assertEquals(0, socket.sends)
    }

    private class RecordingSocket(uri: URI) : WebSocketClient(uri) {
        var duringHandshake: () -> Unit = {}
        var handshakes = 0
        var sends = 0
        var opened = false

        override fun connectBlocking(): Boolean {
            handshakes++
            duringHandshake()
            opened = true
            return true
        }

        override fun close() {
            opened = false
        }

        override fun send(text: String) {
            sends++
            error("Retired socket received a request")
        }
    }
}
