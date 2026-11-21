package jp.co.soramitsu.wallet.impl.presentation.balance.searchAssets

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeableState
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.compose.component.ActionItemType
import jp.co.soramitsu.common.compose.component.SwipeState
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.common.model.AssetMetadataDescriptorStore
import jp.co.soramitsu.wallet.impl.WalletTestModels
import jp.co.soramitsu.wallet.impl.data.network.blockchain.balance.InMemoryPreferences
import jp.co.soramitsu.wallet.impl.domain.ChainInteractor
import jp.co.soramitsu.wallet.impl.domain.SendPausePolicy
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.list.AssetsLoadingState
import jp.co.soramitsu.wallet.impl.presentation.balance.list.PortfolioPresentationPreferences
import jp.co.soramitsu.wallet.impl.presentation.bundledPortfolioVisibility
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Search's row actions and buttons, and the loading state while the asset rows belong to another wallet. */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalMaterialApi::class)
class SearchAssetsViewModelCoverageTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val router = mockk<WalletRouter>(relaxed = true)
    private val interactor = mockk<WalletInteractor>(relaxed = true)
    private val swipeable = mockk<SwipeableState<SwipeState>>(relaxed = true)

    @Test
    fun `row actions send, receive, hide and show the exact asset and close the swiped row`() = runTest {
        val vm = viewModel()

        listOf(ActionItemType.SEND, ActionItemType.RECEIVE, ActionItemType.HIDE, ActionItemType.SHOW, ActionItemType.BUY)
            .forEach { vm.actionItemClicked(it, CHAIN_ID, ASSET_ID, swipeable) }
        advanceUntilIdle()

        verify(exactly = 1) { router.openSend(AssetPayload(CHAIN_ID, ASSET_ID)) }
        verify(exactly = 1) { router.openReceive(AssetPayload(CHAIN_ID, ASSET_ID)) }
        coVerify(exactly = 1) { interactor.markAssetAsHidden(CHAIN_ID, ASSET_ID) }
        coVerify(exactly = 1) { interactor.markAssetAsShown(CHAIN_ID, ASSET_ID) }
        coVerify(exactly = 5) { swipeable.snapTo(SwipeState.INITIAL) }
    }

    @Test
    fun `back closes, update opens the store and an unsupported network only warns`() = runTest {
        val vm = viewModel()
        assertNull(vm.openPlayMarket.value)

        vm.backClicked()
        vm.updateAppClicked()
        vm.assetClicked(ComposeFixtures.assetRow(isSupported = false))

        verify { router.back() }
        assertNotNull(vm.openPlayMarket.value)
        assertNotNull(vm.showUnsupportedChainAlert.value)
        verify(exactly = 0) { router.openAssetDetails(any()) }
    }

    @Test
    fun `rows of another wallet keep the search loading`() = runTest {
        val vm = viewModel(assetsWalletId = OTHER_WALLET_ID)

        vm.onAssetSearchEntered("dot")
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("dot", state.searchQuery)
        val loading = state.assets as AssetsLoadingState.Loading
        assertTrue(loading.shimmerStates.isNotEmpty())
        assertEquals("asset_0", loading.shimmerStates.first().assetIconUrl)
        assertTrue(state.portfolioAssets.isEmpty())
    }

    private fun viewModel(assetsWalletId: Long = WALLET_ID): SearchAssetsViewModel {
        val chainInteractor = mockk<ChainInteractor>()
        every { interactor.assetsFlowAndAccount() } returns flowOf(assetsWalletId to emptyList<AssetWithStatus>())
        every { interactor.selectedMetaAccountFlow() } returns flowOf(WalletTestModels.metaAccount(id = WALLET_ID))
        coEvery { interactor.getSavedChainId(WALLET_ID) } returns null
        every { chainInteractor.getChainsFlow() } returns flowOf(listOf(WalletTestModels.chain()))
        val metadataDescriptorStore = mockk<AssetMetadataDescriptorStore>()
        every { metadataDescriptorStore.get(any()) } returns null

        return SearchAssetsViewModel(
            savedStateHandle = SavedStateHandle(),
            interactor = interactor,
            chainInteractor = chainInteractor,
            assetMetadataDescriptorStore = metadataDescriptorStore,
            router = router,
            portfolioVisibility = bundledPortfolioVisibility(),
            sendPausePolicy = SendPausePolicy.Unrestricted,
            portfolioPreferences = PortfolioPresentationPreferences(InMemoryPreferences()),
            featureToggleStore = mockk<ProductFeatureToggleStore> { every { assetDiscoveryShadowEnabled } returns true }
        )
    }

    private companion object {
        const val WALLET_ID = 1L
        const val OTHER_WALLET_ID = 2L
        const val CHAIN_ID = "polkadot"
        const val ASSET_ID = "dot"
    }
}
