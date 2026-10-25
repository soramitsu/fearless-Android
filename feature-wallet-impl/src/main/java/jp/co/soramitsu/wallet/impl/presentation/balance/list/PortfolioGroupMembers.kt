package jp.co.soramitsu.wallet.impl.presentation.balance.list

import java.math.BigDecimal
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.runtime.ext.assetKey
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.ResolvedTokenGroups
import jp.co.soramitsu.wallet.impl.data.repository.isSupported

/** Receive can choose an approved account/network even before its first balance row exists. */
object PortfolioGroupMembers {
    fun includeUnfunded(
        holdings: List<PortfolioAsset>,
        chains: List<Chain>,
        groups: ResolvedTokenGroups,
        hasAccount: (Chain) -> Boolean,
        accountLabel: (Chain) -> String?
    ): List<PortfolioAsset> {
        val existing = holdings.mapTo(mutableSetOf()) { it.state.chainId to it.state.chainAssetId }
        val byChain = chains.groupBy { it.id }
        val missing = groups.groups.flatMap { it.members }.distinct().mapNotNull { member ->
            if ((member.chainId to member.assetId) in existing) return@mapNotNull null
            val chain = byChain[member.chainId]?.singleOrNull()?.takeIf(hasAccount) ?: return@mapNotNull null
            val asset = chain.assetsById[member.assetId] ?: return@mapNotNull null
            PortfolioAsset(AssetListItemViewState(
                index = null, assetIconUrl = asset.iconUrl, assetName = asset.name.orEmpty(), assetChainName = chain.name,
                assetSymbol = asset.symbol, assetTokenFiat = null, assetTokenRate = null,
                assetTransferableBalance = "—", assetTransferableBalanceFiat = null,
                assetChainUrls = mapOf(chain.id to chain.icon), chainId = chain.id, chainAssetId = asset.id,
                isSupported = chain.isSupported, isHidden = false, isTestnet = false,
                ecosystemId = chain.ecosystem.name.lowercase(), canonicalAssetKey = chain.assetKey(asset).serialized,
                networkAccountLabel = accountLabel(chain), assetTotalBalance = "—"
            ), null, null, holdings.firstOrNull()?.fiatSymbol, chain.rank)
        }
        return holdings + missing
    }
}
