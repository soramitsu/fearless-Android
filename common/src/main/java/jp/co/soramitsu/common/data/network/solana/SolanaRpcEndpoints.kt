package jp.co.soramitsu.common.data.network.solana

import jp.co.soramitsu.common.model.UniversalWalletRegistry

/**
 * Ordered JSON-RPC endpoints for one Solana cluster. Mainnet asks an optional build provider first,
 * then the node the user selected, then the public mainnet nodes, then any other configured node.
 * Every request stays bound to the cluster's genesis hash, so a fallback can only serve the same
 * cluster. Other clusters (devnet, private validators) never fall back to public mainnet nodes.
 */
object SolanaRpcEndpoints {
    const val PUBLICNODE_MAINNET_RPC_URL = "https://solana-rpc.publicnode.com"
    /**
     * Serves the token-account reads that publicnode refuses, without a key. Its limit is about two requests per
     * second per address, so requests to it are spaced (see [requestSpacingMillis]).
     */
    const val SOLANAVIBESTATION_MAINNET_RPC_URL = "https://public.rpc.solanavibestation.com"
    const val MAX_ENDPOINTS = 8
    val PUBLIC_MAINNET: List<String> = listOf(UniversalWalletRegistry.SOLANA_MAINNET_RPC_URL, PUBLICNODE_MAINNET_RPC_URL,
        SOLANAVIBESTATION_MAINNET_RPC_URL)
    // Keyed by host: the limit applies to the node whatever path, query, case or trailing slash names it.
    private val REQUEST_SPACING_MILLIS = mapOf("public.rpc.solanavibestation.com" to 700L)

    /**
     * The node [url] names: its host in lowercase without a trailing dot, so every spelling of one host compares
     * equal. Null when the URL has no parsable host.
     */
    fun hostOf(url: String): String? =
        runCatching { java.net.URI(url.trim()).host }.getOrNull()?.lowercase()?.removeSuffix(".")?.takeIf { it.isNotEmpty() }

    /** The host whose rate limit governs [url], or null when it accepts bursts or does not parse. */
    fun rateLimitedHost(url: String): String? = hostOf(url)?.takeIf { it in REQUEST_SPACING_MILLIS }

    /** How long after one request to [url]'s host the next may start; 0 for endpoints that accept bursts. */
    fun requestSpacingMillis(url: String): Long = rateLimitedHost(url)?.let { REQUEST_SPACING_MILLIS.getValue(it) } ?: 0L

    /** [selected] must be a valid endpoint; invalid [others] and an invalid [buildProvider] are skipped. */
    fun ordered(genesisHash: String, selected: String, others: List<String> = emptyList(), buildProvider: String? = null): List<String> {
        val mainnet = genesisHash == UniversalWalletRegistry.SOLANA_MAINNET_GENESIS_HASH
        val provider = if (mainnet) providerOrNull(buildProvider) else null
        val configured = others.mapNotNull { runCatching { SolanaRpcRoutes.normalizeRpcUrl(it) }.getOrNull() }
        val public = if (mainnet) PUBLIC_MAINNET else emptyList()
        return (listOfNotNull(provider) + SolanaRpcRoutes.normalizeRpcUrl(selected) + public + configured).distinct().take(MAX_ENDPOINTS)
    }

    /** A build provider must be an https URL. Anything else is ignored, so a bad value cannot block reads. */
    fun providerOrNull(url: String?): String? = url?.trim()?.takeIf { it.startsWith("https://") }
        ?.let { runCatching { SolanaRpcRoutes.normalizeRpcUrl(it) }.getOrNull() }
}
