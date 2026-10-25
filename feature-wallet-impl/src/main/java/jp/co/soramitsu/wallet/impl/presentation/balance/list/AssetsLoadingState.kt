package jp.co.soramitsu.wallet.impl.presentation.balance.list

import jp.co.soramitsu.common.compose.viewstate.AssetListItemShimmerViewState
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState

sealed class AssetsLoadingState {
    class Loading(val shimmerStates: List<AssetListItemShimmerViewState> = emptyList()) : AssetsLoadingState()

    /** [assets] are the visible rows; [exclusions] says what the wallet-wide total leaves out. */
    class Loaded(
        val assets: List<AssetListItemViewState>,
        val allAssetsHidden: Boolean = false,
        val exclusions: PortfolioExclusions = PortfolioExclusions(),
        /**
         * Hidden funded assets the user has not been told about yet: the "N hidden assets hold
         * funds" notice is shown while this is positive.
         */
        val hiddenFundedNoticeCount: Int = 0,
        /** Any shown or automatic verified holding is funded anywhere in the wallet. */
        val walletHasPositiveBalance: Boolean = false,
        val walletId: Long? = null,
        val selectedNetwork: String? = null,
        val portfolioAssets: List<PortfolioAsset> = emptyList(),
        val availableAssets: List<PortfolioAsset> = emptyList(),
        val portfolioItems: List<PortfolioListItem> = emptyList(),
        val portfolioUi: PortfolioUiState = PortfolioUiState(),
        val tokenGroups: jp.co.soramitsu.runtime.multiNetwork.tokengroups.ResolvedTokenGroups? = null
    ) : AssetsLoadingState() {
        fun with(
            assets: List<AssetListItemViewState> = this.assets,
            hiddenFundedNoticeCount: Int = this.hiddenFundedNoticeCount,
            portfolioItems: List<PortfolioListItem> = this.portfolioItems,
            portfolioUi: PortfolioUiState = this.portfolioUi,
            tokenGroups: jp.co.soramitsu.runtime.multiNetwork.tokengroups.ResolvedTokenGroups? = this.tokenGroups
        ) = Loaded(
            assets = assets,
            allAssetsHidden = allAssetsHidden,
            exclusions = exclusions,
            hiddenFundedNoticeCount = hiddenFundedNoticeCount,
            walletHasPositiveBalance = walletHasPositiveBalance,
            walletId = walletId, selectedNetwork = selectedNetwork,
            portfolioAssets = portfolioAssets, availableAssets = availableAssets,
            portfolioItems = portfolioItems, portfolioUi = portfolioUi, tokenGroups = tokenGroups
        )
    }
}

/**
 * What the portfolio total leaves out, labelled under the list (shared portfolio contract §3).
 * Like the total, every value covers the whole wallet whatever network filter the list has, and
 * only verified assets are counted: a detected token is never described as missing value.
 * Testnets are not part of it: they sit behind their own toggle and never count.
 */
data class PortfolioExclusions(
    /**
     * Explicitly hidden assets that hold a balance, priced or not, identified by
     * [PortfolioPresentationPreferences.assetKey].
     */
    val hiddenFundedAssetKeys: Set<String> = emptySet(),
    /** Listed funded assets without a trusted price; they add nothing to the total. */
    val unpricedFundedCount: Int = 0,
    val hiddenAssetCount: Int = 0,
    val hiddenFiat: String? = null,
    /**
     * A counted native holding is on a network with nomination pools. Pool stakes sit outside
     * free + reserved, so the total cannot include them.
     */
    val poolStakesExcluded: Boolean = false
) {
    val hiddenFundedCount: Int
        get() = hiddenFundedAssetKeys.size

    val isEmpty: Boolean
        get() = hiddenAssetCount == 0 && hiddenFundedAssetKeys.isEmpty() && unpricedFundedCount == 0 && !poolStakesExcluded
}

internal fun areAllPortfolioAssetsHidden(enabledStates: List<Boolean?>): Boolean =
    enabledStates.isNotEmpty() && enabledStates.all { it == false }
