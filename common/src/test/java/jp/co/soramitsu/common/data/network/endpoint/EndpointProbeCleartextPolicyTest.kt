package jp.co.soramitsu.common.data.network.endpoint

import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger

class EndpointProbeCleartextPolicyTest {
    @Test fun policyInputsComeFromTheValidatedCanonicalHostAndScheme() {
        for ((scheme, cleartext) in listOf("http" to true, "https" to false, "ws" to true, "wss" to false)) {
            for ((authority, host) in listOf("example.invalid:9443" to "example.invalid", "[::1]:9443" to "::1")) {
                val request = request(scheme, "$scheme://$authority/path?opaque=1")
                assertEquals(host, request.host)
                assertEquals(cleartext, request.isCleartext)
            }
        }
    }

    @Test fun deniedHttpDoesNotResolveOrOpenASocket() = denied("http", false)

    @Test fun deniedWebsocketDoesNotOpenASocket() = denied("ws", false)

    @Test fun policyFailureFailsClosedForBothTransports() {
        denied("http", true)
        denied("ws", true)
    }

    @Test fun policyIsRecheckedOnEveryAttemptInsteadOfCachingPriorPermission() = runBlocking {
        val checked = AtomicInteger()
        val resolved = AtomicInteger()
        var permitted = false
        val runtime = EndpointProbeRuntime(dns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                resolved.incrementAndGet()
                throw IOException("Test DNS stops all network access")
            }
        }, cleartextPermitted = { host ->
            assertEquals("example.invalid", host)
            checked.incrementAndGet()
            permitted
        })
        val candidate = request("http", "http://example.invalid/")
        val probe = EndpointGenesisProbe(runtime)
        assertEquals(EndpointProbeOutcome.TRANSPORT_FAILURE, probe.probe(candidate))
        assertEquals(0, resolved.get())
        permitted = true
        assertEquals(EndpointProbeOutcome.TRANSPORT_FAILURE, probe.probe(candidate))
        assertEquals(1, resolved.get())
        permitted = false
        assertEquals(EndpointProbeOutcome.TRANSPORT_FAILURE, probe.probe(candidate))
        assertEquals(1, resolved.get())
        assertEquals(3, checked.get())
    }

    @Test fun tlsCandidatesNeverConsultTheCleartextPermission() = runBlocking {
        val checked = AtomicInteger()
        val port = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val runtime = EndpointProbeRuntime(cleartextPermitted = { checked.incrementAndGet(); false })
        for (scheme in listOf("https", "wss")) {
            assertEquals(EndpointProbeOutcome.TRANSPORT_FAILURE,
                EndpointGenesisProbe(runtime).probe(request(scheme, "$scheme://127.0.0.1:$port/")))
        }
        assertEquals(0, checked.get())
    }

    private fun denied(scheme: String, throws: Boolean) = runBlocking {
        val checked = AtomicInteger()
        val resolved = AtomicInteger()
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { peer ->
            peer.soTimeout = 150
            val runtime = EndpointProbeRuntime(dns = object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    resolved.incrementAndGet()
                    return listOf(InetAddress.getByName("127.0.0.1"))
                }
            }, cleartextPermitted = { host ->
                assertEquals("127.0.0.1", host)
                checked.incrementAndGet()
                if (throws) throw IllegalStateException("Test policy unavailable")
                false
            })
            assertEquals(EndpointProbeOutcome.TRANSPORT_FAILURE,
                EndpointGenesisProbe(runtime).probe(request(scheme, "$scheme://127.0.0.1:${peer.localPort}/")))
            assertEquals(1, checked.get())
            assertEquals(0, resolved.get())
            try { peer.accept().use { fail("Policy-denied probe opened a socket") } }
            catch (_: SocketTimeoutException) { /* No connection reached the listening loopback peer. */ }
        }
    }

    private fun request(scheme: String, url: String) = EndpointProbeRequest(
        if (scheme.startsWith("ws")) EndpointProbeFamily.SUBSTRATE else EndpointProbeFamily.SOLANA,
        url, if (scheme.startsWith("ws")) "0x" + "12".repeat(32) else "5eykt4UsFv8P8NJdTREpY1vzqKqZKvdpKuc147dw2N9d"
    )
}
