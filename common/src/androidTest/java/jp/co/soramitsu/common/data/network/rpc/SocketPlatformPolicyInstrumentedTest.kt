package jp.co.soramitsu.common.data.network.rpc

import android.security.NetworkSecurityPolicy
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import com.neovisionaries.ws.client.WebSocket
import com.neovisionaries.ws.client.WebSocketFactory
import com.neovisionaries.ws.client.WebSocketState
import java.lang.reflect.Proxy
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.fearless_utils.wsrpc.logging.Logger
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse
import jp.co.soramitsu.fearless_utils.wsrpc.socket.RpcSocket
import jp.co.soramitsu.fearless_utils.wsrpc.socket.RpcSocketListener
import jp.co.soramitsu.fearless_utils.wsrpc.socket.SocketTransportPolicy
import jp.co.soramitsu.fearless_utils.wsrpc.subscription.response.SubscriptionChange
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** Uses Android's actual per-host policy. All peers are owned loopback; no policy override is supplied. */
@RunWith(AndroidJUnit4::class)
class SocketPlatformPolicyInstrumentedTest {
    @Test
    fun rawRpcDefaultPolicyDeniesBeforeFactoryOrPeer() {
        denied { url, factory ->
            val callbacks = AtomicInteger()
            val listener = object : RpcSocketListener {
                override fun onResponse(rpcResponse: RpcResponse) { callbacks.incrementAndGet() }
                override fun onResponse(subscriptionChange: SubscriptionChange) { callbacks.incrementAndGet() }
                override fun onStateChanged(newState: WebSocketState) { callbacks.incrementAndGet() }
                override fun onConnected() { callbacks.incrementAndGet() }
                override fun onError(error: Throwable) { callbacks.incrementAndGet() }
            }
            val failure = assertThrows(SecurityException::class.java) {
                RpcSocket(url, listener, factory = factory, gson = Gson()).connectAsync()
            }
            assertEquals("Socket cleartext policy denied endpoint", failure.message)
            assertEquals(0, callbacks.get())
        }
    }

    @Test
    fun directFactoryDefaultPolicyDeniesBeforeFactoryOrPeer() {
        denied { url, factory ->
            val failure = assertThrows(SecurityException::class.java) {
                SocketTransportPolicy.createSocket(factory, url)
            }
            assertEquals("Socket cleartext policy denied endpoint", failure.message)
        }
    }

    @Test
    fun singleRequestDefaultPolicyDeniesBeforeFactoryOrPeer() {
        denied { url, factory ->
            val logger = object : Logger {
                override fun log(message: String?) = Unit
                override fun log(throwable: Throwable?) = Unit
            }
            val resources = Proxy.newProxyInstance(
                ResourceManager::class.java.classLoader,
                arrayOf(ResourceManager::class.java)
            ) { _, _, _ -> error("Policy denial must not read resources") } as ResourceManager
            val executor = SocketSingleRequestExecutor(Gson(), logger, factory, resources)
            val failure = runCatching {
                runBlocking { executor.executeRequest(RuntimeRequest("chain_getBlockHash", emptyList(), 61), url) }
            }.exceptionOrNull()
            assertTrue(failure?.cause is SecurityException)
            assertEquals("Socket cleartext policy denied endpoint", failure?.cause?.message)
        }
    }

    private fun denied(action: (String, CountingFactory) -> Unit) {
        assertFalse(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("127.0.0.2"))
        assertTrue(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("127.0.0.1"))
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.2")).use { peer ->
            peer.soTimeout = 500
            val factory = CountingFactory()
            action("ws://127.0.0.2:${peer.localPort}/public-fixture", factory)
            assertEquals(0, factory.calls.get())
            try {
                peer.accept().use { fail("Policy-denied path opened a loopback connection") }
            } catch (_: SocketTimeoutException) {
                // The real Android policy denied the request before a transport was created.
            }
        }
    }

    private class CountingFactory : WebSocketFactory() {
        val calls = AtomicInteger()
        override fun createSocket(uri: String): WebSocket {
            calls.incrementAndGet()
            return super.createSocket(uri)
        }
    }
}
