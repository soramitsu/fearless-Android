package audit.selection

import com.google.gson.Gson
import com.neovisionaries.ws.client.WebSocket
import com.neovisionaries.ws.client.WebSocketFactory
import com.neovisionaries.ws.client.WebSocketListener
import jp.co.soramitsu.common.data.network.rpc.SocketSingleRequestExecutor
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.fearless_utils.wsrpc.logging.Logger
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.socket.SocketTransportPolicy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.contains
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.calls
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class SingleRequestPolicyTest {
    private class Harness(policy: SocketTransportPolicy = SocketTransportPolicy.Platform) {
        val ws = mock(WebSocket::class.java)
        val factory = mock(WebSocketFactory::class.java)
        lateinit var listener: WebSocketListener
        val logger = mock(Logger::class.java)
        val executor = SocketSingleRequestExecutor(Gson(), logger, factory, mock(ResourceManager::class.java), policy)
        init {
            `when`(factory.createSocket(anyString())).thenReturn(ws)
            `when`(ws.addListener(any(WebSocketListener::class.java))).thenAnswer {
                listener = it.getArgument(0)

            ws
            }
            `when`(ws.connect()).thenReturn(ws)
        }
        fun execute(url: String) =
            runBlocking { executor.executeRequest(RuntimeRequest("chain_getBlockHash", emptyList(), 61), url) }
    }

    @Test fun actualSingleRequestDefaultDeniesBeforeFactory() {
        val h = Harness()

        val failed = runCatching { h.execute("ws://localhost") }.exceptionOrNull()
        assertTrue(failed?.cause is SecurityException)

        verify(h.factory, never()).createSocket(anyString())
    }

    @Test fun changedPolicyBeforeConnectClosesAndFailsWithoutWire() {
        var calls = 0

        val h = Harness(
            SocketTransportPolicy {
            calls++

        calls == 1
        }
        )
        val failed = runCatching { h.execute("ws://localhost") }.exceptionOrNull()
        assertTrue(failed?.cause is SecurityException)

        assertEquals(2, calls)
        verify(h.factory).createSocket("ws://localhost")

        verify(h.ws, never()).connect()

        verify(h.ws, never()).sendText(anyString())
        verify(h.ws).clearListeners()

        verify(h.ws).disconnect()
    }

    @Test fun actualSingleRequestTlsStillDeliversOneResponse() {
        val h = Harness(SocketTransportPolicy { throw AssertionError("TLS consulted cleartext policy") })
        `when`(h.ws.sendText(anyString())).thenAnswer {
            h.listener.onTextMessage(h.ws, """{"jsonrpc":"2.0","id":61,"result":"ok"}""")

            h.ws
        }
        val response = h.execute("wss://secure.invalid/rpc")

        assertEquals(61, response.id)

        assertEquals("ok", response.result)
        verify(h.ws).connect()

        verify(h.ws).sendText(anyString())

        verify(h.ws).disconnect()
    }

    @Test fun actualSingleRequestResponseLoggingDoesNotExposeCredentialMarker() {
        val marker = "synthetic-single-response-credential-marker"

        val h = Harness()
        `when`(h.ws.sendText(anyString())).thenAnswer {
            h.listener.onTextMessage(h.ws, """{"jsonrpc":"2.0","id":61,"result":"$marker"}""")

            h.ws
        }
        val url = "wss://secure.invalid/$marker?key=$marker"
        assertEquals(marker, h.execute(url).result)

        verify(h.factory).createSocket(url)
        val logs = org.mockito.ArgumentCaptor.forClass(String::class.java)
        verify(h.logger, atLeastOnce()).log(logs.capture())

        assertTrue(logs.allValues.none { it.contains(marker) })
    }
}
