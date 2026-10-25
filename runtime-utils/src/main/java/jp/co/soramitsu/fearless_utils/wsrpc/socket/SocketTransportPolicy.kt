package jp.co.soramitsu.fearless_utils.wsrpc.socket

import android.security.NetworkSecurityPolicy
import com.neovisionaries.ws.client.WebSocket
import com.neovisionaries.ws.client.WebSocketFactory
import java.net.URI
import java.util.Locale

/** Platform policy is the default; a synthetic policy must be explicitly supplied by host tests. */
fun interface SocketTransportPolicy {
    fun isCleartextPermitted(host: String): Boolean

    object Platform : SocketTransportPolicy {
        override fun isCleartextPermitted(host: String): Boolean =
            NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted(host)
    }

    companion object {
        fun requireAllowed(url: String, policy: SocketTransportPolicy = Platform) {
            val uri = try {
                URI(url)
            } catch (_: Exception) {
                throw SecurityException("Invalid socket endpoint")
            }
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            val host = uri.host?.removeSurrounding("[", "]")?.lowercase(Locale.ROOT)
            if (uri.isOpaque || scheme !in setOf("ws", "wss", "http", "https") ||
                host.isNullOrEmpty() || uri.rawUserInfo != null || uri.rawFragment != null ||
                uri.port !in -1..65535 || uri.port == 0
            ) {
                throw SecurityException("Invalid socket endpoint")
            }
            if (scheme == "ws" || scheme == "http") {
                val allowed = try {
                    policy.isCleartextPermitted(host)
                } catch (_: Exception) {
                    throw SecurityException("Socket cleartext policy unavailable")
                }
                if (!allowed) throw SecurityException("Socket cleartext policy denied endpoint")
            }
        }

        fun createSocket(
            factory: WebSocketFactory,
            url: String,
            policy: SocketTransportPolicy = Platform
        ): WebSocket {
            requireAllowed(url, policy)
            return factory.createSocket(url)
        }
    }
}
