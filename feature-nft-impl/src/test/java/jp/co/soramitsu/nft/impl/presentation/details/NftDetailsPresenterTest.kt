package jp.co.soramitsu.nft.impl.presentation.details

import android.graphics.drawable.PictureDrawable
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.nft.domain.NFTInteractor
import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.nft.impl.navigation.InternalNFTRouter
import jp.co.soramitsu.nft.impl.presentation.CoroutinesStore
import jp.co.soramitsu.nft.navigation.NFTNavGraphRoute
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

@OptIn(ExperimentalCoroutinesApi::class)
class NftDetailsPresenterTest {
    @Test
    fun stateMapsTokenAndMultipleOwnersIntoDetails() = runTest {
        val fixture = Fixture(backgroundScope)
        val state = fixture.presenter.createScreenStateFlow(backgroundScope)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect() }
        withContext(Dispatchers.Default) {
            withTimeout(10_000) { while (state.value.name != "Bird") delay(10) }
        }
        assertEquals("Bird", state.value.name)
        assertEquals("Birds", state.value.collectionName)
        assertEquals("Description", state.value.description)
        assertEquals("Multiple owners", state.value.owner)
        assertEquals("42", state.value.tokenId)
        assertEquals("Ethereum", state.value.network)
        assertEquals("ERC721", state.value.tokenType)
        assertEquals("2026-01-01", state.value.dateTime)
        assertEquals("1 ETH", state.value.price)
        assertEquals("1 ETH", state.value.priceFiat)
        assertTrue(state.value.isTokenUserOwned)
        assertSame(fixture.icon, state.value.ownerIcon)
        assertSame(fixture.icon, state.value.creatorIcon)
    }

    @Test
    fun sendWaitsForTokenAndThenNavigatesWithThatToken() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.presenter.sendClicked()
        verify(exactly = 0) { fixture.router.openChooseRecipientScreen(any()) }
        runCurrent()
        fixture.presenter.sendClicked()
        verify { fixture.router.openChooseRecipientScreen(fixture.token) }
    }

    @Test
    fun copyActionsUseFullAddressesAndDecimalTokenId() = runTest {
        val fixture = Fixture(backgroundScope)
        val presenter = fixture.presenter
        runCurrent()
        presenter.creatorClicked()
        runCurrent()
        verify { fixture.clipboard.addToClipboard(CREATOR) }
        presenter.tokenIdClicked()
        runCurrent()
        verify { fixture.clipboard.addToClipboard("42") }
        presenter.ownerClicked()
        runCurrent()
        verify { fixture.clipboard.addToClipboard(OWNER) }
    }

    @Test
    fun shareContainsTokenOwnershipAndNetworkDetails() = runTest {
        val fixture = Fixture(backgroundScope)
        val presenter = fixture.presenter
        runCurrent()
        presenter.shareClicked()
        runCurrent()
        verify {
            fixture.router.shareText(
                match {
                    it.contains(OWNER) && it.contains(CREATOR) && it.contains("Ethereum") && it.contains("Birds") && it.contains("42")
                }
            )
        }
    }

    @Test
    fun missingOwnersAndCreatorProduceEmptyStateValues() = runTest {
        val fixture = Fixture(backgroundScope)
        coEvery { fixture.nfts.getOwnersForNFT(any()) } returns Result.failure(IllegalStateException("offline"))
        every { fixture.token.creatorAddress } returns null
        every { fixture.token.tokenId } returns BigInteger.valueOf(-1)
        val state = fixture.presenter.createScreenStateFlow(backgroundScope)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect() }
        runCurrent()
        assertEquals("", state.value.owner)
        assertEquals("", state.value.creator)
        fixture.presenter.creatorClicked()
        fixture.presenter.tokenIdClicked()
        runCurrent()
        verify(exactly = 0) { fixture.clipboard.addToClipboard(any()) }
    }

    private class Fixture(scope: CoroutineScope) {
        val icon = mockk<PictureDrawable>()
        val token = mockk<NFT> {
            every { title } returns "Bird"
            every { thumbnail } returns ""
            every { collectionName } returns "Birds"
            every { description } returns "Description"
            every { isUserOwnedToken } returns true
            every { creatorAddress } returns CREATOR
            every { tokenId } returns BigInteger.valueOf(42)
            every { chainName } returns "Ethereum"
            every { tokenType } returns "ERC721"
            every { date } returns "2026-01-01"
            every { price } returns "1 ETH"
        }
        val router = mockk<InternalNFTRouter>(relaxed = true) {
            every { createNavGraphRoutesFlow() } returns flowOf(NFTNavGraphRoute.DetailsNFTScreen(token))
        }
        val nfts = mockk<NFTInteractor> {
            coEvery { getOwnersForNFT(token) } returns Result.success(listOf(OWNER, CREATOR))
        }
        val clipboard = mockk<ClipboardManager>(relaxed = true)
        private val resources = mockk<ResourceManager> {
            every { getString(any()) } returns "Label"
            every { getString(any(), any()) } returns "Multiple owners"
        }
        private val icons = mockk<AddressIconGenerator> {
            coEvery { createEthereumAddressIcon(any(), any(), any()) } returns icon
        }
        private val wallet = mockk<WalletInteractor> {
            coEvery { getSelectedMetaAccount() } returns mockk {
                every { ethereumAddress } returns ByteArray(20) { 1 }
            }
        }
        private val store = mockk<CoroutinesStore> {
            every { uiScope } returns scope
        }
        val presenter by lazy { NftDetailsPresenter(nfts, store, wallet, clipboard, resources, router, icons) }
    }

    private companion object {
        val OWNER = "0x" + "01".repeat(20)
        val CREATOR = "0x" + "02".repeat(20)
    }
}
