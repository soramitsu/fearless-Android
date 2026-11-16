package jp.co.soramitsu.wallet.impl.presentation.balance.list

import java.math.BigDecimal

/** A network chip on the wallet home: the network's id (the saved filter value) and its display name. */
data class PortfolioNetworkOption(val id: String, val name: String)

/**
 * The one-list wallet home's search and network chips (web 4.2.9 parity). Search narrows exact
 * (network, asset) holdings before they are grouped, so a token found by a network name never borrows
 * another network's balance; the chips are the same per-wallet network filter as the header's button.
 */
object PortfolioDiscovery {

    /** Token symbol, token name or network name, case-insensitive; a blank query matches every row. */
    fun matches(asset: PortfolioAsset, query: String): Boolean {
        val text = query.trim()
        return text.isEmpty() || asset.state.assetSymbol.contains(text, ignoreCase = true) ||
            asset.state.assetName.contains(text, ignoreCase = true) ||
            asset.state.assetChainName.contains(text, ignoreCase = true)
    }

    /**
     * Chips after All networks: every network the wallet holds something on (verified, not hidden,
     * positive balance), largest trusted value first, then most funded assets, then name. A selected
     * network that holds nothing keeps its chip, first, so the active filter is always in sight.
     */
    fun networks(assets: List<PortfolioAsset>, selected: PortfolioNetworkOption?): List<PortfolioNetworkOption> {
        val funded = assets.filter { !it.state.isHidden && it.state.metadataTrust == VERIFIED && it.total?.signum() == 1 }
            .distinctBy { it.key }
        val options = funded.groupBy { it.state.chainId }.values
            .sortedWith(
                compareByDescending<List<PortfolioAsset>> { holdings -> holdings.sumOf { trustedFiat(it) ?: BigDecimal.ZERO } }
                    .thenByDescending { it.size }
                    .thenBy { it.first().state.assetChainName.lowercase() }
                    .thenBy { it.first().state.chainId }
            )
            .map { PortfolioNetworkOption(it.first().state.chainId, it.first().state.assetChainName) }
        return if (selected != null && options.none { it.id == selected.id }) listOf(selected) + options else options
    }

    /** Picking the selected chip again, or All networks (null), shows every network. */
    fun toggleNetwork(selected: String?, picked: String?): String? = picked.takeUnless { it == selected }

    private fun trustedFiat(asset: PortfolioAsset): BigDecimal? = asset.trustedFiat.takeIf {
        !asset.state.isTestnet && asset.state.metadataTrust == VERIFIED && asset.state.priceTrust in TRUSTED_PRICES
    }

    private const val VERIFIED = "verified"
    private val TRUSTED_PRICES = setOf("canonical", "curatedgroup")
}
