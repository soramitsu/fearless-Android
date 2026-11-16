package jp.co.soramitsu.nft.impl.presentation

import androidx.lifecycle.SavedStateHandle
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.compose.models.TextModel
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.feature_nft_impl.R
import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.nft.impl.navigation.InternalNFTRouter
import jp.co.soramitsu.nft.impl.navigation.NavAction
import jp.co.soramitsu.nft.impl.presentation.chooserecipient.ChooseNFTRecipientPresenter
import jp.co.soramitsu.nft.navigation.NFTNavGraphRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.isActive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NFTFlowViewModelStateTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val internalRouter = mockk<InternalNFTRouter>(relaxed = true) {
        every { createNavGraphRoutesFlow() } returns emptyFlow<NFTNavGraphRoute>()
        every { createNavGraphActionsFlow() } returns emptyFlow<NavAction>()
    }
    private val recipientPresenter = mockk<ChooseNFTRecipientPresenter>(relaxed = true)
    private val coroutinesStore = mockk<CoroutinesStore> { every { uiScope } returns scope }

    private fun viewModel(arguments: Map<String, Any?> = ARGUMENTS) = NFTFlowViewModel(
        savedStateHandle = SavedStateHandle(arguments),
        collectionNFTsPresenter = mockk(relaxed = true),
        nftDetailsPresenter = mockk(relaxed = true),
        confirmNFTSendPresenter = mockk(relaxed = true),
        coroutinesStore = coroutinesStore,
        chooseNFTRecipientPresenter = recipientPresenter,
        internalNFTRouter = internalRouter,
        nftRouter = mockk(relaxed = true)
    )

    @Test
    fun opensTheRequestedCollectionAndStartsWithALoadingToolbar() {
        val model = viewModel()

        verify(exactly = 1) { internalRouter.openCollectionNFTsScreen("chain", "contract") }
        assertTrue(model.toolbarStateFlow.value is LoadingState.Loading)
        assertEquals(NFTNavGraphRoute.Loading, model.navGraphRoutesFlow.value)
    }

    @Test
    fun missingArgumentsAreRejected() {
        for (missing in ARGUMENTS.keys) {
            val failure = runCatching { viewModel(ARGUMENTS - missing) }.exceptionOrNull()
            assertTrue(failure is IllegalStateException)
            assertTrue(failure!!.message!!.contains(missing))
        }
    }

    @Test
    fun toolbarFollowsEachDestination() {
        val token = mockk<NFT> { every { title } returns "Bird #1" }
        every { internalRouter.destination(NFTNavGraphRoute.DetailsNFTScreen::class.java) } returns
            NFTNavGraphRoute.DetailsNFTScreen(token)
        val model = viewModel()

        model.onDestinationChanged(NFTNavGraphRoute.CollectionNFTsScreen.routeName)
        assertEquals(TextModel.SimpleString("collection") to R.drawable.ic_cross_24, loaded(model))

        model.onDestinationChanged(NFTNavGraphRoute.DetailsNFTScreen.routeName)
        assertEquals(TextModel.SimpleString("Bird #1") to R.drawable.ic_arrow_left_24, loaded(model))

        model.onDestinationChanged(NFTNavGraphRoute.ChooseNFTRecipientScreen.routeName)
        assertEquals(TextModel.ResId(R.string.nft_choose_recipient_title) to R.drawable.ic_arrow_left_24, loaded(model))

        model.onDestinationChanged(NFTNavGraphRoute.ConfirmNFTSendScreen.routeName)
        assertEquals(TextModel.ResId(R.string.common_preview) to R.drawable.ic_arrow_left_24, loaded(model))

        model.onDestinationChanged(NFTNavGraphRoute.Loading.routeName)
        assertTrue(model.toolbarStateFlow.value is LoadingState.Loading)

        model.onDestinationChanged(NFTNavGraphRoute.CollectionNFTsScreen.routeName)
        model.onDestinationChanged("UnknownScreen")
        assertTrue(model.toolbarStateFlow.value is LoadingState.Loading)
    }

    @Test
    fun detailsWithoutADestinationShowAnEmptyTitle() {
        every { internalRouter.destination(NFTNavGraphRoute.DetailsNFTScreen::class.java) } returns null
        val model = viewModel()

        model.onDestinationChanged(NFTNavGraphRoute.DetailsNFTScreen.routeName)

        assertEquals(TextModel.SimpleString("") to R.drawable.ic_arrow_left_24, loaded(model))
    }

    @Test
    fun onlyScannedCodesReachTheRecipientPresenter() {
        val model = viewModel()

        model.onQRCodeScannerResult(null)
        verify(exactly = 0) { recipientPresenter.handleQRCodeResult(any()) }

        model.onQRCodeScannerResult("0xabc")
        verify(exactly = 1) { recipientPresenter.handleQRCodeResult("0xabc") }
    }

    @Test
    fun clearingTheModelCancelsTheFlowScope() {
        val model = viewModel()
        assertTrue(scope.isActive)

        val onCleared = NFTFlowViewModel::class.java.getDeclaredMethod("onCleared")
        onCleared.isAccessible = true
        onCleared.invoke(model)

        assertFalse(scope.isActive)
    }

    private fun loaded(model: NFTFlowViewModel): Pair<TextModel, Int> {
        val state = model.toolbarStateFlow.value
        assertTrue(state is LoadingState.Loaded)
        return (state as LoadingState.Loaded).data
    }

    private companion object {
        val ARGUMENTS = mapOf<String, Any?>(
            NFTFlowFragment.SELECTED_CHAIN_ID to "chain",
            NFTFlowFragment.CONTRACT_ADDRESS_KEY to "contract",
            NFTFlowFragment.COLLECTION_NAME to "collection"
        )
    }
}
