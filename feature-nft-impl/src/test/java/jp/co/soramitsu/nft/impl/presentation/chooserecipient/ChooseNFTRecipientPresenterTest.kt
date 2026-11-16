package jp.co.soramitsu.nft.impl.presentation.chooserecipient

import android.graphics.drawable.PictureDrawable
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.nft.domain.NFTTransferInteractor
import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.nft.impl.domain.usecase.transfer.ValidateNFTTransferUseCase
import jp.co.soramitsu.nft.impl.navigation.InternalNFTRouter
import jp.co.soramitsu.nft.impl.navigation.NavAction
import jp.co.soramitsu.nft.impl.presentation.CoroutinesStore
import jp.co.soramitsu.nft.impl.presentation.chooserecipient.contract.ChooseNFTRecipientScreenState
import jp.co.soramitsu.nft.navigation.NFTNavGraphRoute
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ethereumChainId
import jp.co.soramitsu.wallet.api.domain.TransferValidationResult
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset as ChainAsset

@OptIn(ExperimentalCoroutinesApi::class)
class ChooseNFTRecipientPresenterTest {
    @Test
    fun inputIsTrimmedAndPreviewRequiresRecipientAndFee() = runTest {
        val fixture = Fixture(backgroundScope)
        val state = observe(fixture)
        awaitInput(state, "")
        assertFalse(state.value.buttonState.enabled)
        assertTrue(state.value.feeInfoState.feeAmount!!.contains("ETH"))
        fixture.presenter.onAddressInput("  recipient  ")
        awaitInput(state, "recipient")
        assertTrue(state.value.buttonState.enabled)
        fixture.presenter.onAddressInputClear()
        awaitInput(state, "")
        assertFalse(state.value.buttonState.enabled)
    }

    @Test
    fun pasteQrAndContactsUpdateRecipientAndScannerCallbackIsForwarded() = runTest {
        val fixture = Fixture(backgroundScope)
        val state = observe(fixture)
        fixture.presenter.onPasteClick()
        awaitInput(state, "clipboard")
        fixture.presenter.handleQRCodeResult("raw-address")
        awaitInput(state, "raw-address")
        every { fixture.wallet.tryReadAddressFromSoraFormat("encoded") } returns "decoded"
        fixture.presenter.handleQRCodeResult("encoded")
        awaitInput(state, "decoded")
        fixture.presenter.onContactsClick()
        runCurrent()
        awaitInput(state, "contact")
        verify { fixture.router.openContacts(ethereumChainId) }
        fixture.presenter.onQrClick()
        verify { fixture.router.openQRCodeScanner() }
    }

    @Test
    fun backOnRecipientPageClearsEnteredAddress() = runTest {
        val fixture = Fixture(backgroundScope)
        val state = observe(fixture)
        fixture.presenter.onAddressInput("recipient")
        awaitInput(state, "recipient")
        fixture.actions.emit(NavAction.BackPressed)
        awaitInput(state, "")
        assertFalse(state.value.buttonState.enabled)
    }

    @Test
    fun validRecipientMovesToConfirmationWithTrimmedAddress() = runTest {
        val fixture = Fixture(backgroundScope)
        val presenter = fixture.presenter
        runCurrent()
        presenter.onAddressInput(" recipient ")
        presenter.onNextClick()
        runCurrent()
        verify { fixture.router.openNFTSendScreen(fixture.token, "recipient") }
    }

    @Test
    fun validationFailurePreventsNavigationAndReportsError() = runTest {
        val fixture = Fixture(backgroundScope)
        every { fixture.validation(any(), any(), any(), any(), any(), any(), any(), any()) } returns
            Result.failure(IllegalArgumentException("validation failed"))
        val presenter = fixture.presenter
        runCurrent()
        presenter.onAddressInput("recipient")
        presenter.onNextClick()
        runCurrent()
        verify { fixture.router.openErrorsScreen(message = "validation failed") }
        verify(exactly = 0) { fixture.router.openNFTSendScreen(any(), any()) }
    }

    @Test
    fun unownedTokenCannotAdvanceEvenAfterValidation() = runTest {
        val fixture = Fixture(backgroundScope)
        every { fixture.token.isUserOwnedToken } returns false
        val presenter = fixture.presenter
        runCurrent()
        presenter.onAddressInput("recipient")
        presenter.onNextClick()
        runCurrent()
        verify(exactly = 0) { fixture.router.openNFTSendScreen(any(), any()) }
    }

    private fun TestScope.observe(fixture: Fixture): StateFlow<ChooseNFTRecipientScreenState> {
        val state = fixture.presenter.createScreenStateFlow(backgroundScope)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect() }
        runCurrent()
        return state
    }

    private suspend fun TestScope.awaitInput(state: StateFlow<ChooseNFTRecipientScreenState>, input: String) {
        advanceTimeBy(301)
        runCurrent()
        withContext(Dispatchers.Default) {
            withTimeout(10_000) {
                while (state.value.isLoading || state.value.addressInputState.input != input) delay(10)
            }
        }
        assertEquals(input, state.value.addressInputState.input)
    }

    private class Fixture(scope: CoroutineScope) {
        val token = mockk<NFT> {
            every { chainId } returns ethereumChainId
            every { tokenId } returns BigInteger.ONE
            every { isUserOwnedToken } returns true
        }
        val actions = MutableSharedFlow<NavAction>()
        val router = mockk<InternalNFTRouter>(relaxed = true) {
            every { createNavGraphRoutesFlow() } returns flowOf(NFTNavGraphRoute.ChooseNFTRecipientScreen(token))
            every { createNavGraphActionsFlow() } returns actions
            every { openContacts(any()) } returns flowOf("contact")
        }
        val validation = mockk<ValidateNFTTransferUseCase> {
            every { this@mockk(any(), any(), any(), any(), any(), any(), any(), any()) } returns
                Result.success(TransferValidationResult.Valid)
        }
        private val chainAsset = mockk<ChainAsset> {
            every { id } returns "ETH"
            every { isUtility } returns true
            every { symbol } returns "ETH"
        }
        private val chain = mockk<Chain> {
            every { id } returns ethereumChainId
            every { assets } returns listOf(chainAsset)
            every { ecosystem } returns Ecosystem.Ethereum
            every { externalApi } returns null
            every { isEthereumBased } returns true
        }
        private val selected = mockk<MetaAccount> {
            every { chainAccounts } returns emptyMap()
            every { ethereumAddress } returns ByteArray(20) { 1 }
        }
        private val utility = mockk<Asset> {
            every { token } returns mockk {
                every { configuration } returns chainAsset
                every { fiatRate } returns null
                every { fiatSymbol } returns "$"
            }
        }
        val wallet = mockk<WalletInteractor> {
            every { tryReadAddressFromSoraFormat(any()) } returns null
            coEvery { validateSendAddress(any(), any()) } answers { secondArg<String>().isNotBlank() }
            every { selectedAccountFlow(any()) } returns flowOf(mockk { every { address } returns "owner" })
            every { assetFlow(any(), any()) } returns flowOf(utility)
            coEvery { getCurrentAsset(any(), any()) } returns utility
        }
        private val transfers = mockk<NFTTransferInteractor> {
            coEvery { balance(token) } returns Result.success(BigInteger.ONE)
            coEvery { networkFeeFlow(any(), any(), any()) } returns flowOf(Result.success(BigDecimal.ONE))
        }
        private val icons = mockk<AddressIconGenerator> {
            coEvery { createEthereumAddressIcon(any(), any(), any()) } returns mockk<PictureDrawable>()
        }
        private val clipboard = mockk<ClipboardManager> {
            every { getFromClipboard() } returns "clipboard"
        }
        private val accounts = mockk<AccountInteractor> {
            coEvery { selectedMetaAccount() } returns selected
        }
        private val chains = mockk<ChainsRepository> {
            coEvery { getChain(any()) } returns chain
        }
        private val resources = mockk<ResourceManager> {
            every { getString(any()) } returns "Label"
        }
        private val ownAddress = mockk<CurrentAccountAddressUseCase> {
            coEvery { this@mockk(any()) } returns "owner"
        }
        private val store = mockk<CoroutinesStore> {
            every { uiScope } returns scope
            every { ioScope } returns scope
        }
        val presenter by lazy {
            ChooseNFTRecipientPresenter(
                icons, clipboard, chains, store, accounts, wallet, resources, transfers, validation, ownAddress, router
            )
        }
    }
}
