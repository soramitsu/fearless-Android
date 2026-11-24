package jp.co.soramitsu.nft.impl.navigation

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.nft.navigation.NFTNavGraphRoute
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.walletselector.light.WalletSelectionMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InternalNFTRouterImplTest {
    private val walletRouter = mockk<WalletRouter>(relaxed = true)
    private val router = InternalNFTRouterImpl(walletRouter)
    private val token = mockk<NFT>()

    @Test
    fun routesPreservePayloadsAndBackPopsOnlyTheCurrentDestination() = runTest {
        val routes = mutableListOf<NFTNavGraphRoute>()
        backgroundScope.launch { router.createNavGraphRoutesFlow().collect { routes += it } }
        backgroundScope.launch { router.createNavGraphActionsFlow().collect() }
        runCurrent()
        router.openCollectionNFTsScreen("1", "contract")
        runCurrent()
        router.openDetailsNFTScreen(token)
        runCurrent()
        router.openChooseRecipientScreen(token)
        runCurrent()
        router.openNFTSendScreen(token, "recipient")
        runCurrent()
        val expectedRoutes = listOf("CollectionNFTsScreen", "DetailsNFTScreen", "ChooseNFTRecipientScreen", "ConfirmNFTSendScreen")
        assertEquals(expectedRoutes, routes.map { it.routeName })
        val collection = routes[0] as NFTNavGraphRoute.CollectionNFTsScreen
        assertEquals("1", collection.chainId)
        assertEquals("contract", collection.contractAddress)
        assertSame(token, router.destination(NFTNavGraphRoute.DetailsNFTScreen::class.java)?.token)
        assertSame(token, router.destination(NFTNavGraphRoute.ChooseNFTRecipientScreen::class.java)?.token)
        val confirmation = router.destination(NFTNavGraphRoute.ConfirmNFTSendScreen::class.java)!!
        assertSame(token, confirmation.token)
        assertEquals("recipient", confirmation.receiver)
        assertFalse(confirmation.isReceiverKnown)
        router.back()
        runCurrent()
        assertNull(router.destination(NFTNavGraphRoute.ConfirmNFTSendScreen::class.java))
        assertSame(token, router.destination(NFTNavGraphRoute.ChooseNFTRecipientScreen::class.java)?.token)
    }

    @Test
    fun transientActionsRetainMessagesAndBackOnEmptyStackIsSafe() = runTest {
        val actions = mutableListOf<NavAction>()
        backgroundScope.launch { router.createNavGraphActionsFlow().collect { actions += it } }
        runCurrent()
        router.openQRCodeScanner()
        runCurrent()
        router.openErrorsScreen("Failure", "offline")
        runCurrent()
        router.showToast("Copied")
        runCurrent()
        router.shareText("Bird #1")
        runCurrent()
        router.back()
        runCurrent()
        assertSame(NavAction.QRCodeScanner, actions[0])
        assertEquals("Failure", (actions[1] as NavAction.ShowError).errorTitle)
        assertEquals("offline", (actions[1] as NavAction.ShowError).errorText)
        assertEquals(NavAction.ShowToast("Copied"), actions[2])
        assertEquals(NavAction.ShareText("Bird #1"), actions[3])
        assertSame(NavAction.BackPressed, actions[4])
        assertNull(router.destination(NFTNavGraphRoute.DetailsNFTScreen::class.java))
    }

    @Test
    fun contactsWalletSelectionAndSuccessDelegateTheirResults() = runTest {
        every { walletRouter.openContactsWithResult("1") } returns flowOf("contact")
        every { walletRouter.openWalletSelectorForResult(7, WalletSelectionMode.ExternalSelectedWallet) } returns flowOf(8)
        assertEquals("contact", router.openContacts("1").first())
        assertEquals(8L, router.openWalletSelectionScreen(7).first())
        router.openSuccessScreen("hash", "1")
        verify { walletRouter.openOperationSuccess("hash", "1") }
    }
}
