package jp.co.soramitsu.wallet.impl.presentation.balance.assetDetails

import androidx.compose.runtime.Stable
import jp.co.soramitsu.common.compose.component.AssetBalanceViewState
import jp.co.soramitsu.common.compose.component.MultiToggleButtonState
import jp.co.soramitsu.common.compose.component.MultiToggleItem
import jp.co.soramitsu.common.compose.component.NetworkIssueType
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.domain.interfaces.AssetSorting

@Stable // all descendants should be either data class or override equalsTo()
data class AssetDetailsState(
    val assetSorting: AssetSorting,
    val balanceState: LoadingState<AssetBalanceViewState>,
    val tabState: MultiToggleButtonState<Tab>?,
    val items: List<ItemState>
) {
    companion object {
        val empty =  AssetDetailsState(
            assetSorting = AssetSorting.FiatBalance,
            balanceState = LoadingState.Loading(),
            tabState = null,
            items = emptyList()
        )
    }

    enum class Tab(override val titleResId: Int): MultiToggleItem {
        AvailableChains(R.string.common_available_networks),
        MyChains(R.string.common_my_networks)
    }

    @Stable // all descendants should be either data class or override equalsTo()
    interface ItemState {

        val assetId: String?

        val chainId: String

        val iconUrl: String

        val chainName: String?

        val assetRepresentation: String?

        val fiatRepresentation: String?

        val networkIssueType: NetworkIssueType?
    }
}

data class AssetDetailsItemViewState(
    override val assetId: String?,
    override val chainId: String,
    override val iconUrl: String,
    override val chainName: String,
    override val assetRepresentation: String?,
    override val fiatRepresentation: String?,
    override val networkIssueType: NetworkIssueType?
) : AssetDetailsState.ItemState

@Stable // callbacks always produce Unit, so they're stable
interface AssetDetailsCallback {

    fun onNavigationBack()

    fun onSelectChainClick()

    fun onChainTabClick(tab: AssetDetailsState.Tab)

    fun onSortChainsClick()

    fun onChainClick(itemState: AssetDetailsState.ItemState)

}