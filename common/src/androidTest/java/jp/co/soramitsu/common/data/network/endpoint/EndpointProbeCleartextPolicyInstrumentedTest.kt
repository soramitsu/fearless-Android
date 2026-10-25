package jp.co.soramitsu.common.data.network.endpoint

import android.security.NetworkSecurityPolicy
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class EndpointProbeCleartextPolicyInstrumentedTest {
    @Test fun platformPolicyPermitsOnlyTheTwoExplicitLoopbackTestHosts() {
        val policy = NetworkSecurityPolicy.getInstance()
        assertTrue(policy.isCleartextTrafficPermitted("localhost"))
        assertTrue(policy.isCleartextTrafficPermitted("127.0.0.1"))
        for (host in listOf("127.0.0.2", "example.invalid", "child.localhost", "localhost.example.invalid")) {
            assertFalse(host, policy.isCleartextTrafficPermitted(host))
        }
    }

    @Test fun platformDenialStopsHttpBeforeDnsAndSocketCreation() = denied("http")

    @Test fun platformDenialStopsWebsocketBeforeConnectingToLoopbackPeer() = denied("ws")

    private fun denied(scheme: String) = runBlocking {
        val resolved = AtomicInteger()
        assertFalse(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("127.0.0.2"))
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.2")).use { peer ->
            peer.soTimeout = 1000
            val runtime = EndpointProbeRuntime(dns = object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    resolved.incrementAndGet()
                    return listOf(InetAddress.getByName("127.0.0.2"))
                }
            })
            val request = EndpointProbeRequest(
                if (scheme == "ws") EndpointProbeFamily.SUBSTRATE else EndpointProbeFamily.SOLANA,
                "$scheme://127.0.0.2:${peer.localPort}/",
                if (scheme == "ws") "0x" + "12".repeat(32) else "5eykt4UsFv8P8NJdTREpY1vzqKqZKvdpKuc147dw2N9d"
            )
            assertEquals(EndpointProbeOutcome.TRANSPORT_FAILURE, EndpointGenesisProbe(runtime).probe(request))
            assertEquals(0, resolved.get())
            try { peer.accept().use { fail("Policy-denied probe opened a socket") } }
            catch (_: SocketTimeoutException) { /* Default Android policy prevented the loopback connection. */ }
        }
    }
}
