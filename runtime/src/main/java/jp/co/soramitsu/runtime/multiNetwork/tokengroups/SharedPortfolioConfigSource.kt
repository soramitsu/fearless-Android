package jp.co.soramitsu.runtime.multiNetwork.tokengroups

/**
 * Loads the shared portfolio files bundled in the APK. They are the only source in 4.2.0: no
 * remote copy is fetched, because a remote map could otherwise group a look-alike token with a
 * real one for display. A future override needs a signature.
 */
class SharedPortfolioConfigSource(
    private val readAsset: (String) -> ByteArray,
    private val onLoadFailure: (assetName: String, error: Throwable) -> Unit = { _, _ -> }
) {

    val config: SharedPortfolioConfig by lazy {
        val tokenGroupsFile = load(TOKEN_GROUPS_ASSET) { it }
        val tokenGroups = tokenGroupsFile?.let { bytes -> parse(TOKEN_GROUPS_ASSET, bytes, TokenGroupCatalog::parse) }

        SharedPortfolioConfig(
            tokenGroups = tokenGroups,
            testnetChainIds = load(TESTNET_CHAIN_IDS_ASSET, TestnetChainIds::parse),
            defaultVisibleAtZero = load(DEFAULT_VISIBLE_AT_ZERO_ASSET, DefaultVisibleAtZero::parse),
            // Price trust must not depend on the groups: a rejected catalog still yields its list.
            untrustedAssets = tokenGroups?.untrustedAssets
                ?: tokenGroupsFile?.let { bytes -> runCatching { UntrustedAssets.salvage(bytes) }.getOrNull() }
        )
    }

    private fun <T> load(assetName: String, parse: (ByteArray) -> T): T? {
        return runCatching { parse(readAsset(assetName)) }
            .onFailure { onLoadFailure(assetName, it) }
            .getOrNull()
    }

    private fun <T> parse(assetName: String, bytes: ByteArray, parse: (ByteArray) -> T): T? {
        return runCatching { parse(bytes) }
            .onFailure { onLoadFailure(assetName, it) }
            .getOrNull()
    }

    companion object {
        const val TOKEN_GROUPS_ASSET = "canonical-asset-groups.v1.json"
        const val TESTNET_CHAIN_IDS_ASSET = "testnet-chain-ids.v1.json"
        const val DEFAULT_VISIBLE_AT_ZERO_ASSET = "default-visible-at-zero.v1.json"
    }
}
