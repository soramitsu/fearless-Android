package jp.co.soramitsu.nft.impl.presentation

import androidx.lifecycle.SavedStateHandle
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.nft.impl.navigation.InternalNFTRouter
import jp.co.soramitsu.nft.impl.navigation.NavAction
import jp.co.soramitsu.nft.navigation.NFTNavGraphRoute
import jp.co.soramitsu.nft.navigation.NFTRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NFTFlowBackTest {

    private val nftRouter = mockk<NFTRouter>(relaxed = true)
    private val internalRouter = mockk<InternalNFTRouter>(relaxed = true) {
        every { createNavGraphRoutesFlow() } returns emptyFlow<NFTNavGraphRoute>()
        every { createNavGraphActionsFlow() } returns emptyFlow<NavAction>()
    }
    private val coroutinesStore = mockk<CoroutinesStore> {
        every { uiScope } returns CoroutineScope(Dispatchers.Unconfined)
    }

    private fun viewModel() = NFTFlowViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(
                NFTFlowFragment.SELECTED_CHAIN_ID to "chain",
                NFTFlowFragment.CONTRACT_ADDRESS_KEY to "contract",
                NFTFlowFragment.COLLECTION_NAME to "collection"
            )
        ),
        collectionNFTsPresenter = mockk(relaxed = true),
        nftDetailsPresenter = mockk(relaxed = true),
        confirmNFTSendPresenter = mockk(relaxed = true),
        coroutinesStore = coroutinesStore,
        chooseNFTRecipientPresenter = mockk(relaxed = true),
        internalNFTRouter = internalRouter,
        nftRouter = nftRouter
    )

    @Test
    fun `back on a page goes through the flow's router, like the toolbar`() {
        viewModel().onNavigationClick()

        verify(exactly = 1) { internalRouter.back() }
        verify(exactly = 0) { nftRouter.back() }
    }

    @Test
    fun `back presses queued as the flow closes leave only the sheet`() {
        val viewModel = viewModel()

        repeat(3) { viewModel.exitFlow() }

        verify(exactly = 1) { nftRouter.back() }
    }

    @Test
    fun `system back reaches the flow before the NavHost and the sheet`() {
        val source = File("src/main/java/jp/co/soramitsu/nft/impl/presentation/NFTFlowFragment.kt").readText()

        // The dispatcher runs the callback added last, and composition adds them in source order.
        val navHost = source.indexOf("NavHost(")
        val backHandler = source.indexOf("BackHandler(onBack = viewModel::onNavigationClick)")
        assertTrue(navHost >= 0)
        assertTrue(backHandler > navHost)
        // On Android 13+ a dialog's onBackPressed never runs: the sheet's own callback takes Back first.
        assertFalse(source.contains("onBackPressed"))
        assertTrue(source.contains("popFlowPageOrExit(NFTNavGraphRoute.Loading.routeName, viewModel::exitFlow)"))
    }
}
