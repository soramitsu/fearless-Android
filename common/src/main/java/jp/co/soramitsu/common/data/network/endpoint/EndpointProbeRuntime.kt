package jp.co.soramitsu.common.data.network.endpoint

import android.security.NetworkSecurityPolicy

import com.neovisionaries.ws.client.WebSocket
import com.neovisionaries.ws.client.WebSocketAdapter
import com.neovisionaries.ws.client.WebSocketError
import com.neovisionaries.ws.client.WebSocketException
import com.neovisionaries.ws.client.WebSocketFactory
import com.neovisionaries.ws.client.WebSocketFrame
import okhttp3.Dns
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

internal class EndpointProbeRuntime(
    val deadlineMillis: Long = 3000,
    private val dns: Dns = Dns.SYSTEM,
    private val cleartextPermitted: (String) -> Boolean = { NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted(it) }
) {
    private val permits = Semaphore(2)
    private val workers = Executors.newFixedThreadPool(2) { Thread(it, "endpoint-probe").apply { isDaemon = true } }
    private val closer = Executors.newFixedThreadPool(2) { Thread(it, "endpoint-probe-close").apply { isDaemon = true } }

    fun acquire(): ProbeSocketLease? = if (permits.tryAcquire()) ProbeSocketLease(closer) { permits.release() } else null

    fun execute(request: EndpointProbeRequest, lease: ProbeSocketLease, complete: (EndpointProbeOutcome) -> Unit) {
        workers.execute task@ {
            try {
                lease.requireActive()
                if (request.isCleartext && !cleartextPermitted(request.host)) {
                    complete(EndpointProbeOutcome.TRANSPORT_FAILURE)
                    return@task
                }
                lease.requireActive()
                val result = when (request.family) {
                    EndpointProbeFamily.SUBSTRATE -> websocket(request, lease)
                    EndpointProbeFamily.SOLANA -> BoundedGenesisHttp.exchange(request, lease, dns, deadlineMillis.toInt())
                }
                complete(result)
            } catch (error: WebSocketException) {
                complete(if (error.error == WebSocketError.TOO_LONG_PAYLOAD) EndpointProbeOutcome.RESPONSE_LIMIT else EndpointProbeOutcome.TRANSPORT_FAILURE)
            } catch (_: ProbeProtocolException) {
                complete(EndpointProbeOutcome.MALFORMED_RESPONSE)
            } catch (_: ProbeByteLimitException) {
                complete(EndpointProbeOutcome.RESPONSE_LIMIT)
            } catch (_: java.io.InterruptedIOException) {
                complete(EndpointProbeOutcome.TIMED_OUT)
            } catch (_: Exception) {
                complete(EndpointProbeOutcome.TRANSPORT_FAILURE)
            } finally {
                lease.finished()
            }
        }
    }

    private fun websocket(request: EndpointProbeRequest, lease: ProbeSocketLease): EndpointProbeOutcome {
        val response = ArrayBlockingQueue<EndpointProbeOutcome>(1)
        val socket = WebSocketFactory().setConnectionTimeout(deadlineMillis.toInt()).setSocketTimeout(deadlineMillis.toInt())
            .setMaximumConnectionAttempts(1).setSocketFactory(lease.plainFactory()).setSSLSocketFactory(lease.tlsFactory())
            .createSocket(request.candidateUrl)
            .clearExtensions().setExtended(false).setDirectTextMessage(true)
            .setMaximumIncomingFramePayloadSize(GenesisProbeProtocol.RESPONSE_BYTES)
            .setMaximumIncomingStreamBytes(GenesisProbeProtocol.STREAM_BYTES)
            .setFrameQueueSize(1)
        socket.addListener(object : WebSocketAdapter() {
            override fun onTextMessage(websocket: WebSocket, data: ByteArray) {
                response.offer(GenesisProbeProtocol.inspect(request, data))
            }
            override fun onBinaryMessage(websocket: WebSocket, binary: ByteArray) {
                response.offer(EndpointProbeOutcome.MALFORMED_RESPONSE)
            }
            override fun onError(websocket: WebSocket, cause: WebSocketException) {
                response.offer(if (cause.error == WebSocketError.TOO_LONG_PAYLOAD) EndpointProbeOutcome.RESPONSE_LIMIT else EndpointProbeOutcome.TRANSPORT_FAILURE)
            }
            override fun onDisconnected(websocket: WebSocket, server: WebSocketFrame?, client: WebSocketFrame?, byServer: Boolean) {
                response.offer(EndpointProbeOutcome.TRANSPORT_FAILURE)
            }
        })
        try {
            lease.requireActive()
            socket.connect()
            require(socket.agreedExtensions.isNullOrEmpty())
            lease.requireActive()
            socket.sendText(GenesisProbeProtocol.request(request.family))
            return response.poll(deadlineMillis, TimeUnit.MILLISECONDS) ?: EndpointProbeOutcome.TIMED_OUT
        } finally {
            socket.clearListeners()
            lease.abort()
        }
    }

    companion object {
        val shared = EndpointProbeRuntime()
    }
}
