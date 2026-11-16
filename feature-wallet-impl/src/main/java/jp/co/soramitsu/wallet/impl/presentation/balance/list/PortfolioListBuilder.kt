package jp.co.soramitsu.wallet.impl.presentation.balance.list

import java.math.BigDecimal
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.ResolvedTokenGroups

/**
 * One token list (the network lens is retired): curated groups only. Hidden/testnet/untrusted-price values never
 * enter trusted grouped fiat.
 */
object PortfolioListBuilder {
    fun build(
        visible: List<PortfolioAsset>,
        hidden: List<PortfolioAsset>,
        groups: ResolvedTokenGroups,
        ui: PortfolioUiState,
        selectedNetwork: String?,
        walletHasPositiveBalance: Boolean,
        walletHasUnknownBalance: Boolean = false,
        searching: Boolean = false
    ): List<PortfolioListItem> {
        val rows = visible.filterNot { it.state.isHidden }.distinctBy { it.key }
        if (selectedNetwork != null) return rows.sortedWith(assetOrder).map { PortfolioListItem.AssetRow(it) }
        val result = mutableListOf<PortfolioListItem>()
        // A search lists what it found; whether the wallet is empty is not its answer.
        if (!searching && !walletHasPositiveBalance && !walletHasUnknownBalance && rows.all { it.total != null }) {
            result += PortfolioListItem.EmptyWallet
        }
        val grouped = rows.groupBy { asset ->
            groupId(asset, groups)?.let { "group:$it" } ?: "asset:${asset.key}"
        }.values.sortedWith(
            compareByDescending<List<PortfolioAsset>> { it.sumOf { asset -> trustedFiat(asset) ?: BigDecimal.ZERO } }
                .thenByDescending { it.any { asset -> asset.total?.signum() == 1 } }
                .thenBy { it.minOf { asset -> asset.rank ?: Int.MAX_VALUE } }
                .thenBy { it.first().state.assetSymbol }
                .thenBy { it.first().key }
        )
        val (zero, inline) = grouped.partition { list -> list.all { it.isAutomaticZero } }
        inline.forEach { appendGroup(result, it, hidden, groups, ui, searching) }
        // Zero balances wait behind their toggle until a search asks for them; then they are listed, toggle-free.
        if (zero.isNotEmpty()) {
            if (!searching) {
                result += PortfolioListItem.ZeroSummary(zero.flatten().map { it.state.chainId }.distinct().size, ui.zeroSummaryExpanded)
            }
            if (searching || ui.zeroSummaryExpanded) zero.forEach { appendGroup(result, it, hidden, groups, ui, searching) }
        }
        return result
    }

    private fun appendGroup(
        result: MutableList<PortfolioListItem>, members: List<PortfolioAsset>, hidden: List<PortfolioAsset>,
        groups: ResolvedTokenGroups, ui: PortfolioUiState, searching: Boolean
    ) {
        val first = members.first()
        val group = groupId(first, groups)?.let { id -> groups.groups.firstOrNull { it.id == id } }
        if (group == null || members.size < 2) {
            result += PortfolioListItem.AssetRow(first)
            return
        }
        val sorted = members.sortedWith(assetOrder)
        val priced = sorted.mapNotNull(::trustedFiat)
        val expanded = group.id in ui.expandedGroups
        result += PortfolioListItem.TokenGroup(group.id, group.symbol, group.name, sorted,
            sorted.map { it.total }.takeIf { amounts -> amounts.all { it != null } }
                ?.fold(BigDecimal.ZERO) { sum, amount -> sum + requireNotNull(amount) }, priced.takeIf { it.isNotEmpty() }?.fold(BigDecimal.ZERO, BigDecimal::add),
            sorted.any { it.total == null || (it.total?.signum() == 1 && trustedFiat(it) == null) },
            hidden.count { it.state.isHidden && groupId(it, groups) == group.id }, expanded)
        if (!expanded) return
        val (zero, inline) = sorted.partition { it.isAutomaticZero }
        result += inline.map { PortfolioListItem.AssetRow(it, nested = true) }
        if (zero.isNotEmpty()) {
            val showZero = searching || group.id in ui.expandedZeroGroups
            if (!searching) result += PortfolioListItem.ZeroSummary(zero.map { it.state.chainId }.distinct().size, showZero, group.id)
            if (showZero) result += zero.map { PortfolioListItem.AssetRow(it, nested = true) }
        }
    }

    private fun groupId(asset: PortfolioAsset, groups: ResolvedTokenGroups): String? =
        if (asset.state.metadataTrust == "verified" && !asset.state.isTestnet)
            groups.groupOf(asset.state.chainId, asset.state.chainAssetId)?.id else null

    private fun trustedFiat(asset: PortfolioAsset): BigDecimal? =
        asset.trustedFiat.takeIf { !asset.state.isTestnet && asset.state.metadataTrust == "verified" && asset.state.priceTrust in setOf("canonical", "curatedgroup") }

    private val assetOrder = compareByDescending<PortfolioAsset> { trustedFiat(it) ?: BigDecimal.ZERO }
        .thenByDescending { it.total?.signum() == 1 }.thenBy { it.rank ?: Int.MAX_VALUE }
        .thenBy { it.state.assetChainName }.thenBy { it.key }
}
