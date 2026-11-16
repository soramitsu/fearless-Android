package jp.co.soramitsu.wallet.impl.presentation.balance.assetDetails.sort

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.awaitFirst
import jp.co.soramitsu.wallet.impl.domain.interfaces.AssetSorting
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssetDetailsSortViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    @Test
    fun sortingStateFollowsTheStoredChoiceAndSelectionsAreSaved() {
        val sorting = MutableStateFlow(AssetSorting.Name)
        val interactor = mockk<WalletInteractor>(relaxed = true) { every { observeAssetSorting() } returns sorting }
        val router = mockk<WalletRouter>(relaxed = true)
        val resources = mockk<ResourceManager> { every { getString(R.string.common_sort_by) } returns "Sort by" }
        val viewModel = AssetDetailsSortViewModel(resources, interactor, router)

        val state = viewModel.contentFlow.awaitFirst { it is AssetDetailsSortViewState }
        assertEquals("Sort by", state.toolbarState!!.title)
        assertEquals(3, state.items.size)
        assertEquals(AssetDetailsSortState.Sorting(R.string.common_assets_filters_name), state.selectedSorting)
        sorting.value = AssetSorting.Popularity
        assertEquals(
            AssetDetailsSortState.Sorting(R.string.common_assets_filters_popularity),
            viewModel.contentFlow.awaitFirst { it.selectedSorting?.nameResId == R.string.common_assets_filters_popularity }.selectedSorting
        )

        viewModel.onSortingSelected(AssetDetailsSortState.Sorting(R.string.common_assets_filters_fiat))
        viewModel.onSortingSelected(AssetDetailsSortState.Sorting(R.string.common_assets_filters_name))
        viewModel.onSortingSelected(AssetDetailsSortState.Sorting(R.string.common_assets_filters_popularity))
        viewModel.onSortingSelected(AssetDetailsSortState.Sorting(R.string.common_cancel))
        state.toolbarState!!.menuItems!!.single().onClick()

        verify(exactly = 2) { interactor.applyAssetSorting(AssetSorting.FiatBalance) }
        verify { interactor.applyAssetSorting(AssetSorting.Name) }
        verify { interactor.applyAssetSorting(AssetSorting.Popularity) }
        verify(exactly = 5) { router.back() }
    }
}
