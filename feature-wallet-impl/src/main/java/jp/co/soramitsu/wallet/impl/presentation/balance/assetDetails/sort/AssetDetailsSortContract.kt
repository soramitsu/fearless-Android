package jp.co.soramitsu.wallet.impl.presentation.balance.assetDetails.sort

import androidx.compose.runtime.Stable
import jp.co.soramitsu.common.compose.component.ToolbarViewState

@Stable
interface AssetDetailsSortState {

    @JvmInline
    value class Sorting(
        val nameResId: Int
    )

    val toolbarState: ToolbarViewState?

    val items: List<Sorting>

    val selectedSorting: Sorting?

}

data class AssetDetailsSortViewState(
    override val toolbarState: ToolbarViewState,
    override val items: List<AssetDetailsSortState.Sorting>,
    override val selectedSorting: AssetDetailsSortState.Sorting
): AssetDetailsSortState

object EmptyAssetDetailsSortViewState: AssetDetailsSortState {
    override val toolbarState: ToolbarViewState? = null
    override val items: List<AssetDetailsSortState.Sorting> = emptyList()
    override val selectedSorting: AssetDetailsSortState.Sorting? = null
}

@Stable
interface AssetDetailsSortCallback {

    fun onNavigationClose()

    fun onSortingSelected(sorting: AssetDetailsSortState.Sorting)

}