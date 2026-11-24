package jp.co.soramitsu.nft.impl.presentation.confirmsend

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.nft.domain.NFTTransferInteractor
import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.nft.impl.domain.usecase.transfer.ValidateNFTTransferUseCase
import jp.co.soramitsu.nft.impl.navigation.InternalNFTRouter
import jp.co.soramitsu.nft.impl.presentation.CoroutinesStore
import jp.co.soramitsu.nft.navigation.NFTNavGraphRoute
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ethereumChainId
import jp.co.soramitsu.wallet.api.domain.TransferValidationResult
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset as ChainAsset

@OptIn(ExperimentalCoroutinesApi::class)
class ConfirmNFTSendPresenterTest {
    @Test
    fun stateShowsBoundWalletRecipientCollectionAndNetworkFee() = runTest {
        val fixture = Fixture(backgroundScope)
        val state = fixture.presenter.createScreenStateFlow(backgroundScope)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect() }
        runCurrent()
        assertEquals("Sending wallet", state.value.fromInfoItem?.value)
        assertEquals("recipient", state.value.toInfoItem?.value)
        assertEquals("Birds", state.value.collectionInfoItem?.value)
        assertTrue(state.value.feeInfoItem!!.value!!.contains("ETH"))
        assertTrue(state.value.buttonState.enabled)
        assertFalse(state.value.isLoading)
        assertEquals(4, state.value.tableItems.size)
    }

    @Test
    fun unavailableFeeDisablesConfirmation() = runTest {
        val fixture = Fixture(backgroundScope)
        coEvery { fixture.transfers.networkFeeFlow(any(), any(), any()) } returns
            flowOf(Result.failure(IllegalStateException("fee unavailable")))
        val state = fixture.presenter.createScreenStateFlow(backgroundScope)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect() }
        runCurrent()
        assertFalse(state.value.buttonState.enabled)
        assertEquals(null, state.value.feeInfoItem?.value)
    }

    @Test
    fun validConfirmationSendsTokenAndNavigatesToTransactionSuccess() = runTest {
        val fixture = Fixture(backgroundScope)
        val presenter = fixture.presenter
        runCurrent()
        presenter.onConfirmClick()
        runCurrent()
        coVerify { fixture.transfers.send(fixture.token, "recipient", true) }
        verify { fixture.router.openSuccessScreen("transaction-hash", ethereumChainId) }
    }

    @Test
    fun failedBroadcastShowsErrorInsteadOfSuccess() = runTest {
        val fixture = Fixture(backgroundScope)
        coEvery {
            fixture.transfers.send(any(), any(), any())
        } returns Result.failure(IllegalStateException("broadcast failed"))
        val presenter = fixture.presenter
        runCurrent()
        presenter.onConfirmClick()
        runCurrent()
        verify { fixture.router.openErrorsScreen(message = "broadcast failed") }
        verify(exactly = 0) { fixture.router.openSuccessScreen(any(), any()) }
    }

    @Test
    fun failedValidationPreventsBroadcastAndShowsFailure() = runTest {
        val fixture = Fixture(backgroundScope)
        every { fixture.validation(any(), any(), any(), any(), any(), any(), any(), any()) } returns
            Result.failure(IllegalArgumentException("invalid recipient"))
        val presenter = fixture.presenter
        runCurrent()
        presenter.onConfirmClick()
        runCurrent()
        coVerify(exactly = 0) { fixture.transfers.send(any(), any(), any()) }
        verify { fixture.router.openErrorsScreen(message = "invalid recipient") }
    }

    private class Fixture(scope: CoroutineScope) {
        val token = mockk<NFT> {
            every { chainId } returns ethereumChainId
            every { tokenId } returns BigInteger.ONE
            every { thumbnail } returns ""
            every { collectionName } returns "Birds"
        }
        val router = mockk<InternalNFTRouter>(relaxed = true) {
            every { createNavGraphRoutesFlow() } returns flowOf(NFTNavGraphRoute.ConfirmNFTSendScreen(token, "recipient", true))
        }
        val transfers = mockk<NFTTransferInteractor> {
            coEvery { balance(token) } returns Result.success(BigInteger.ONE)
            coEvery { networkFeeFlow(any(), any(), any()) } returns flowOf(Result.success(BigDecimal.ONE))
            coEvery { send(any(), any(), any()) } returns Result.success("transaction-hash")
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
        }
        private val selected = mockk<MetaAccount> {
            every { name } returns "Sending wallet"
            every { chainAccounts } returns emptyMap()
            every { ethereumAddress } returns ByteArray(20) { 1 }
        }
        private val utility = mockk<Asset> {
            every { token } returns mockk {
                every { configuration } returns chainAsset
                every { fiatRate } returns BigDecimal.TEN
                every { fiatSymbol } returns "$"
            }
        }
        private val account = mockk<AccountInteractor> {
            every { selectedMetaAccountFlow() } returns flowOf(selected)
            coEvery { selectedMetaAccount() } returns selected
        }
        private val wallet = mockk<WalletInteractor> {
            coEvery { assetFlow(any(), any()) } returns flowOf(utility)
            coEvery { getCurrentAsset(any(), any()) } returns utility
        }
        private val chains = mockk<ChainsRepository> {
            coEvery { getChain(any()) } returns chain
        }
        private val resources = mockk<ResourceManager> {
            every { getString(any()) } returns "Label"
        }
        private val store = mockk<CoroutinesStore> {
            every { uiScope } returns scope
        }
        val presenter by lazy {
            ConfirmNFTSendPresenter(account, resources, store, wallet, chains, transfers, validation, router)
        }
    }
}
