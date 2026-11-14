package jp.co.soramitsu.runtime.multiNetwork.chain

import jp.co.soramitsu.core.models.Ecosystem
import java.net.URI
import java.util.Locale

/**
 * Which node URLs from the remote chain catalog a build may connect to.
 *
 * Release builds take only encrypted endpoints: `wss://` for Substrate nodes, and `wss://` or `https://` for EVM and
 * TON nodes, whose JSON-RPC and HTTP APIs the catalog serves over HTTPS. An edit of the remote catalog can then never
 * point a release build at a plaintext endpoint. Debug builds keep every node, so that local development chains
 * (`ws://127.0.0.1:9944`) stay reachable.
 */
class RemoteNodeUrlPolicy private constructor(private val encryptedOnly: Boolean) {

    fun admits(url: String?, ecosystem: Ecosystem): Boolean {
        if (!encryptedOnly) return true
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        val allowed = when (ecosystem) {
            Ecosystem.Substrate, Ecosystem.EthereumBased -> SUBSTRATE_SCHEMES
            Ecosystem.Ethereum, Ecosystem.Ton -> HTTP_API_SCHEMES
        }
        return scheme in allowed && !uri.host.isNullOrBlank()
    }

    companion object {
        private val SUBSTRATE_SCHEMES = setOf("wss")
        private val HTTP_API_SCHEMES = setOf("wss", "https")

        val Release = RemoteNodeUrlPolicy(encryptedOnly = true)
        val Debug = RemoteNodeUrlPolicy(encryptedOnly = false)

        fun forBuild(isDebug: Boolean): RemoteNodeUrlPolicy = if (isDebug) Debug else Release
    }
}
