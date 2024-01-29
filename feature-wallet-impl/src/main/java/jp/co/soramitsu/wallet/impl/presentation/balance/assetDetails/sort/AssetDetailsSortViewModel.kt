package jp.co.soramitsu.wallet.impl.presentation.balance.assetDetails.sort

import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.MenuIconItem
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.domain.interfaces.AssetSorting
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AssetDetailsSortViewModel @Inject constructor(
    private val resourceManager: ResourceManager,
    private val walletInteractor: WalletInteractor,
    private val walletRouter: WalletRouter
): BaseViewModel(), AssetDetailsSortCallback {

    val contentFlow: StateFlow<AssetDetailsSortState> = createContentFlow()

    private fun createContentFlow(): StateFlow<AssetDetailsSortState> {
        return walletInteractor.observeAssetSorting().map { assetSorting ->
            AssetDetailsSortViewState(
                toolbarState = ToolbarViewState(
                    resourceManager.getString(R.string.common_sort_by),
                    null,
                    listOf(
                        MenuIconItem(icon = R.drawable.ic_cross_24, ::onNavigationClose)
                    )
                ),
                items = AssetSorting.values().map { it.mapToState() },
                selectedSorting = assetSorting.mapToState()
            )
        }.stateIn(this, SharingStarted.WhileSubscribed(5_000L), EmptyAssetDetailsSortViewState)
    }

    private fun AssetSorting.mapToState() = when (this) {
        AssetSorting.FiatBalance -> AssetDetailsSortState.Sorting(R.string.common_assets_filters_fiat)
        AssetSorting.Name -> AssetDetailsSortState.Sorting(R.string.common_assets_filters_name)
        AssetSorting.Popularity -> AssetDetailsSortState.Sorting(R.string.common_assets_filters_popularity)
    }

    private fun AssetDetailsSortState.Sorting.mapToModel() = when(this.nameResId) {
        R.string.common_assets_filters_fiat -> AssetSorting.FiatBalance
        R.string.common_assets_filters_name -> AssetSorting.Name
        R.string.common_assets_filters_popularity -> AssetSorting.Popularity
        else -> AssetSorting.FiatBalance
    }

    override fun onNavigationClose() {
        walletRouter.back()
    }

    override fun onSortingSelected(sorting: AssetDetailsSortState.Sorting) {
        walletInteractor.applyAssetSorting(sorting.mapToModel())
        walletRouter.back()
    }

}