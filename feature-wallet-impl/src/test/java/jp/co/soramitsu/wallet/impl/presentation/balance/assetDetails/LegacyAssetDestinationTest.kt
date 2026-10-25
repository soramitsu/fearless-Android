package jp.co.soramitsu.wallet.impl.presentation.balance.assetDetails

import androidx.lifecycle.SavedStateHandle
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import org.junit.Assert.*
import org.junit.Test

class LegacyAssetDestinationTest {
    private fun model(router: WalletRouter, payload: AssetPayload? = AssetPayload("network-A", "0")) =
        AssetDetailsViewModel(router, SavedStateHandle(mapOf(AssetDetailsFragment.KEY_ASSET_PAYLOAD to payload)))

    @Test fun constructionDoesNotNavigateBeforeFragmentResumes() {
        val router = mockk<WalletRouter>(relaxed = true); val model = model(router)
        verify(exactly = 0) { router.openAssetDetailsAndPopUpToBalancesList(any()) }
        assertTrue(model.contentState.value.items.isEmpty()); assertTrue(model.contentState.value.balanceState is LoadingState.Loading)
        assertEquals("", (model.toolbarState.value as LoadingState.Loaded).data.title)
    }
    @Test fun restoredNetworkAndAssetForwardExactlyOnceIncludingZeroAssetId() {
        val router = mockk<WalletRouter>(relaxed = true); val payload = AssetPayload("network-A", "0")
        val model = model(router, payload); repeat(3) { model.onLegacyDestinationResumed() }
        verify(exactly = 1) { router.openAssetDetailsAndPopUpToBalancesList(payload) }
        verify(exactly = 0) { router.back() }
    }
    @Test fun identicalAssetIdsOnDifferentNetworksNeverCollapse() {
        for (network in listOf("network-A", "network-B")) {
            val router = mockk<WalletRouter>(relaxed = true); val payload = AssetPayload(network, "same-id")
            model(router, payload).onLegacyDestinationResumed()
            verify(exactly = 1) { router.openAssetDetailsAndPopUpToBalancesList(payload) }
        }
    }
    @Test fun absentOrBlankRestoredIdentityReturnsWithoutOpeningAnotherAsset() {
        for (payload in listOf(null, AssetPayload(" ", "0"), AssetPayload("network-A", ""))) {
            val router = mockk<WalletRouter>(relaxed = true); val model = model(router, payload)
            repeat(2) { model.onLegacyDestinationResumed() }
            verify(exactly = 1) { router.back() }; verify(exactly = 0) { router.openAssetDetailsAndPopUpToBalancesList(any()) }
        }
    }
    @Test fun backBeforeResumePreventsForwardNavigation() {
        val router = mockk<WalletRouter>(relaxed = true); val model = model(router)
        model.onNavigationBack(); model.onLegacyDestinationResumed(); model.onNavigationBack()
        verify(exactly = 1) { router.back() }; verify(exactly = 0) { router.openAssetDetailsAndPopUpToBalancesList(any()) }
    }
    @Test fun ObsoleteInteractionsCannotChooseForeignNetworkOrOpenSecondaryScreens() {
        val router = mockk<WalletRouter>(relaxed = true); val payload = AssetPayload("network-A", "0"); val model = model(router, payload)
        model.onChainClick(mockk(relaxed = true)); model.onSelectChainClick()
        model.onChainTabClick(AssetDetailsState.Tab.MyChains); model.onSortChainsClick()
        verify(exactly = 1) { router.openAssetDetailsAndPopUpToBalancesList(payload) }
        verify(exactly = 0) { router.openAssetDetails(any()) }
        verify(exactly = 0) { router.openAssetIntermediateDetailsSort() }
    }
    @Test fun failedNavigationDoesNotConsumeTheNextLifecycleAttempt() {
        val router = mockk<WalletRouter>(relaxed = true); var attempts = 0
        every { router.openAssetDetailsAndPopUpToBalancesList(any()) } answers {
            attempts++; if (attempts == 1) throw IllegalStateException("public-fixture-navigation-failure")
        }
        val model = model(router)
        try { model.onLegacyDestinationResumed(); fail("Expected failure") } catch (_: IllegalStateException) { }
        model.onLegacyDestinationResumed(); model.onLegacyDestinationResumed(); assertEquals(2, attempts)
    }
}
