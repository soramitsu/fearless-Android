package jp.co.soramitsu.wallet.impl.presentation.balance.detail

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.account.api.presentation.exporting.ExportSource
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.compose.component.ActionItemType
import jp.co.soramitsu.common.compose.component.AddressDisplayState
import jp.co.soramitsu.common.compose.component.TextInputViewState
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.common.data.network.send.PendingWalletSend
import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.WalletTestModels
import jp.co.soramitsu.wallet.impl.data.network.blockchain.balance.InMemoryPreferences
import jp.co.soramitsu.wallet.impl.data.repository.tranfser.NativeSendRecovery
import jp.co.soramitsu.wallet.impl.data.repository.tranfser.NativeSendRecoverySnapshot
import jp.co.soramitsu.wallet.impl.domain.ChainInteractor
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.wallet.impl.domain.SendPausePolicy
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.WalletAccount
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.assetActions.buy.BuyMixin
import jp.co.soramitsu.wallet.impl.presentation.balance.detail.frozen.FrozenAssetPayload
import jp.co.soramitsu.wallet.impl.presentation.model.OperationStatusAppearance
import jp.co.soramitsu.wallet.impl.presentation.send.NativeSendRecoveryUiState
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.TransferDetailsState
import jp.co.soramitsu.wallet.impl.presentation.transaction.filter.HistoryFiltersProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BalanceDetailInteractionTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    @get:Rule val main = MainCoroutineRule()

    private val config: jp.co.soramitsu.core.models.Asset = WalletTestModels.asset(priceId = "dot")
    private var chain: jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain = WalletTestModels.chain(assets = listOf(config), explorers = listOf(
        Chain.Explorer(Chain.Explorer.Type.SUBSCAN, listOf("extrinsic", "account"), "https://scan.test/{type}/{value}")
    ))
    private val asset: jp.co.soramitsu.wallet.impl.domain.model.Asset = WalletTestModels.fundedAsset(config, locked = "10")
    private val payload = AssetPayload(config.chainId, config.id)
    private val store = ViewModelStore()
    private val router = mockk<WalletRouter>(relaxed = true) {
        every { chainSelectorPayloadFlow } returns emptyFlow()
    }
    private val clipboard = mockk<ClipboardManager>(relaxed = true)
    private val external = mockk<ExternalAccountActions.Presentation>(relaxed = true)
    private val buy = mockk<BuyMixin.Presentation>(relaxed = true) {
        coEvery { isBuyEnabled(any(), any()) } returns true
    }
    private val resources = mockk<ResourceManager>(relaxed = true) {
        every { getString(any()) } answers { "res:" + firstArg<Int>() }
    }
    private val wallet = mockk<WalletInteractor>(relaxed = true) {
        coEvery { getChain(any()) } answers { chain }
        every { assetFlow(any(), any()) } returns flowOf(asset)
        coEvery { getSelectedMetaAccount() } returns WalletTestModels.metaAccount()
        every { selectedMetaAccountFlow() } returns flowOf(WalletTestModels.metaAccount())
        every { selectedAccountFlow(any()) } returns flowOf(WalletAccount(WalletTestModels.ALICE, "Main"))
        coEvery { getChainAddressForSelectedMetaAccount(any()) } returns WalletTestModels.ALICE
        coEvery { checkClaimSupport(any()) } returns true
        coEvery { getExportSourceTypes(any(), any()) } returns mutableSetOf(ExportSource.Json, ExportSource.Seed, ExportSource.Mnemonic)
        coEvery { syncAssetsRates() } returns Result.success(Unit)
        coEvery { syncOperationsFirstPage(any(), any(), any(), any()) } returns Result.failure(IllegalStateException("History unavailable"))
        every { operationsFirstPageFlow(any(), any()) } returns emptyFlow()
    }
    private val recovery = mockk<NativeSendRecovery>(relaxed = true) {
        coEvery { peek(any(), any()) } returns null
    }

    @After fun dispose() { store.clear(); main.dispatcher.scheduler.runCurrent() }

    @Test fun balanceAndToolbarReflectTheFundedAsset() {
        val vm = model()
        await { vm.state.value.balance is LoadingState.Loaded && vm.toolbarState.value is LoadingState.Loaded }
        val balance = (vm.state.value.balance as LoadingState.Loaded).data
        assertEquals(WalletTestModels.ALICE, balance.address)
        assertEquals("90 DOT", balance.transferableBalance)
        assertEquals("90 DOT", vm.state.value.transferableViewState.value)
        assertEquals("10 DOT", vm.state.value.lockedViewState.value)
        assertEquals("Main", (vm.toolbarState.value as LoadingState.Loaded).data.title)
        val actions = (vm.state.value.actionBarViewState as LoadingState.Loaded).data
        assertTrue(ActionItemType.BUY in actions.actionItems)
        assertTrue(actions.disabledItems.isEmpty())
    }

    @Test fun accountActionsKeepTheSelectedChainAndAddress() {
        val vm = model()
        vm.accountOptionsClicked()
        await { vm.showAccountOptions.value != null }
        assertEquals(AccountOptionsPayload(WalletTestModels.ALICE, true, false), vm.showAccountOptions.value!!.peekContent())
        vm.switchNode(); vm.claimRewardClicked(); vm.backClicked()
        verify { router.openNodes(chain.id); router.openClaimRewards(chain.id); router.back() }
    }

    @Test fun exportChooserOffersExactlyTheAvailableSources() {
        val vm = model()
        vm.exportClicked()
        await { vm.showExportSourceChooser.value != null }
        val chooser = vm.showExportSourceChooser.value!!.peekContent()
        assertEquals(chain.id, chooser.chainId)
        assertEquals(setOf(ExportSource.Json, ExportSource.Seed, ExportSource.Mnemonic), chooser.sources)
    }

    @Test fun exportRoutesAllSourcesThroughPinVerification() {
        val vm = model()
        listOf(ExportSource.Json, ExportSource.Seed, ExportSource.Mnemonic).forEach { vm.exportTypeSelected(it, chain.id) }
        main.dispatcher.scheduler.runCurrent()
        verify { router.openExportJsonPassword(1L, chain.id); router.openExportSeed(1L, chain.id); router.openExportMnemonic(1L, chain.id) }
        verify(exactly = 3) { router.withPinCodeCheckRequired(any(), false, R.string.account_export) }
    }

    @Test fun receiveAndBuyUseTheSelectedWalletAddress() {
        val vm = model()
        vm.actionItemClicked(ActionItemType.RECEIVE, chain.id, config.id)
        vm.actionItemClicked(ActionItemType.BUY, chain.id, config.id)
        main.dispatcher.scheduler.runCurrent()
        verify { router.openReceive(payload) }
        coVerify { buy.buyClicked(chain.id, config.id, WalletTestModels.ALICE) }
    }

    @Test fun tonFiltersAreRestrictedToSupportedOperations() {
        chain = chain.copy(ecosystem = Ecosystem.Ton)
        val vm = model()
        vm.filterClicked()
        main.dispatcher.scheduler.runCurrent()
        verify { router.openFilter(setOf(TransactionFilter.TRANSFER, TransactionFilter.EXTRINSIC)) }
        await { vm.state.value.filtersEnabled }
        assertTrue(vm.state.value.filtersEnabled)
    }

    @Test fun substrateFiltersOfferAllOperationTypes() {
        model().filterClicked()
        main.dispatcher.scheduler.runCurrent()
        verify { router.openFilter(TransactionFilter.entries.toSet()) }
    }

    @Test fun failedRefreshReportsTheRateError() {
        coEvery { wallet.syncAssetsRates() } returns Result.failure(IllegalStateException("Rates unavailable"))
        val vm = model()
        vm.onRefresh()
        await { vm.messageLiveData.value != null }
        assertEquals("Rates unavailable", vm.messageLiveData.value!!.peekContent())
        coVerify(exactly = 1) { wallet.syncAssetsRates() }
    }

    @Test fun addressAndHashCopiesAreAcknowledged() {
        val vm = model()
        await { vm.state.value.balance is LoadingState.Loaded }
        vm.onAddressClick()
        verify { clipboard.addToClipboard(WalletTestModels.ALICE) }
        vm.onSwapDetailsHashClick("0xabc")
        verify { clipboard.addToClipboard("0xabc") }
        assertEquals("res:" + R.string.common_copied, vm.messageLiveData.value!!.peekContent())
    }

    @Test fun lockedAndCrowdloanRowsOpenTheirExactPayloads() {
        val frozen = slot<FrozenAssetPayload>()
        every { router.openFrozenTokens(capture(frozen)) } returns Unit
        val vm = model()
        vm.tableItemClicked(409); vm.tableItemClicked(410)
        await { frozen.isCaptured }
        assertEquals("DOT", frozen.captured.assetSymbol)
        assertEquals(asset.locked, frozen.captured.locked)
        assertEquals(asset.reserved, frozen.captured.reserved)
        assertEquals(asset.redeemable, frozen.captured.redeemable)
        verify { router.openLegacyCrowdloan(payload) }
    }

    @Test fun transactionExplorerActionsUseHashAndBothAddresses() {
        val vm = model()
        val details = transferDetails()
        vm.transactionDetailsBottomSheet.value = details
        vm.transactionHashClicked(details)
        await { vm.externalActionsSelector.value?.value == "0xabc" }
        assertEquals("https://scan.test/extrinsic/0xabc", vm.externalActionsSelector.value!!.explorers[Chain.Explorer.Type.SUBSCAN])
        vm.fromClicked(details)
        await { vm.externalActionsSelector.value?.value == "sender" }
        assertEquals("https://scan.test/account/sender", vm.externalActionsSelector.value!!.explorers[Chain.Explorer.Type.SUBSCAN])
        vm.toClicked(details)
        await { vm.externalActionsSelector.value?.value == "recipient" }
        assertEquals("https://scan.test/account/recipient", vm.externalActionsSelector.value!!.explorers[Chain.Explorer.Type.SUBSCAN])
        vm.onDetailsClose()
        assertNull(vm.transactionDetailsBottomSheet.value)
    }

    @Test fun swapExplorerCanBeOpenedAndShared() {
        val vm = model()
        vm.onSwapDetailsSubscanClicked("0xabc"); vm.onShareSwapClicked("0xabc")
        await { vm.shareUrlEvent.value != null }
        assertEquals("https://scan.test/extrinsic/0xabc", vm.shareUrlEvent.value!!.peekContent())
        verify { external.viewExternalClicked("https://scan.test/extrinsic/0xabc") }
    }

    @Test fun missingExplorerDoesNotOfferAnInvalidShare() {
        chain = chain.copy(explorers = emptyList())
        val vm = model()
        vm.onSwapDetailsSubscanClicked("0xabc"); vm.onShareSwapClicked("0xabc")
        main.dispatcher.scheduler.runCurrent()
        assertNull(vm.shareUrlEvent.value)
        verify(exactly = 0) { external.viewExternalClicked(any()) }
    }

    @Test fun retainedPaymentLoadsAndRefreshesItsStatus() {
        val pending = snapshot()
        val confirmed = pending.copy(state = PendingWalletSend.State.Confirmed)
        coEvery { recovery.peek(chain.id, 1L) } returns pending
        coEvery { recovery.refresh(pending, false) } returns confirmed
        val vm = model()
        await { vm.state.value.nativePaymentAvailable }
        vm.onNativePaymentClick()
        await { vm.nativeRecoveryUi.value?.snapshot?.state == PendingWalletSend.State.Confirmed }
        assertFalse(vm.nativeRecoveryUi.value!!.busy)
        assertNull(vm.nativeRecoveryUi.value!!.error)
        vm.closeNativePayment()
        assertNull(vm.nativeRecoveryUi.value)
    }

    @Test fun retainedPaymentRefreshFailureKeepsTheOriginalReceipt() {
        val pending = snapshot()
        coEvery { recovery.refresh(pending, true) } throws IllegalStateException("offline")
        val vm = model()
        vm.nativeRecoveryUi.value = NativeSendRecoveryUiState(pending)
        vm.refreshNativePayment(true)
        assertTrue(vm.nativeRecoveryUi.value!!.busy)
        await { vm.nativeRecoveryUi.value?.error != null }
        assertEquals(pending, vm.nativeRecoveryUi.value!!.snapshot)
        assertEquals("res:" + R.string.send_recovery_unavailable, vm.nativeRecoveryUi.value!!.error)
        assertFalse(vm.nativeRecoveryUi.value!!.busy)
    }

    @Test fun unavailableRetainedPaymentShowsAnError() {
        coEvery { recovery.peek(any(), any()) } throws IllegalStateException("storage unavailable")
        val vm = model()
        vm.onNativePaymentClick()
        await { vm.errorLiveData.value != null }
        assertEquals("res:" + R.string.send_recovery_unavailable, vm.errorLiveData.value!!.peekContent())
        assertNull(vm.nativeRecoveryUi.value)
    }

    @Test fun busyRecoveryDoesNotStartASecondRequest() {
        val pending = snapshot()
        val vm = model()
        vm.nativeRecoveryUi.value = NativeSendRecoveryUiState(pending, busy = true)
        vm.refreshNativePayment(true)
        main.dispatcher.scheduler.runCurrent()
        assertTrue(vm.nativeRecoveryUi.value!!.busy)
        coVerify(exactly = 0) { recovery.refresh(any(), any()) }
    }

    private fun snapshot() = NativeSendRecoverySnapshot(1L, chain.id, WalletTestModels.ALICE,
        WalletSelectionEpoch.snapshot(), "a".repeat(64), PendingWalletSend.State.Pending)

    private fun transferDetails() = TransferDetailsState(TextInputViewState("0xabc", "Hash"),
        AddressDisplayState("From", "sender", "image"), AddressDisplayState("To", "recipient", "image"),
        OperationStatusAppearance.COMPLETED, emptyList())

    private fun model(): BalanceDetailViewModel {
        val chains = mockk<ChainInteractor> { every { getChainsFlow() } returns flowOf(listOf(chain)) }
        val currentAddress = mockk<CurrentAccountAddressUseCase>().also { useCase -> coEvery { useCase(any()) } returns WalletTestModels.ALICE }
        return BalanceDetailViewModel(wallet, router, buy, external, mockk(relaxed = true), chains,
            HistoryFiltersProvider(), SavedStateHandle(mapOf(KEY_ASSET_PAYLOAD to payload)), resources, clipboard,
            mockk(relaxed = true), currentAddress, mockk(relaxed = true), mockk(relaxed = true),
            ProductFeatureToggleStore(InMemoryPreferences(), mockk(relaxed = true)),
            SendPausePolicy({ true }, { true }, { error("No paused networks") }), recovery).also { store.put("detail", it) }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        do {
            main.dispatcher.scheduler.runCurrent()
            if (condition()) return
            Thread.sleep(10)
        } while (System.currentTimeMillis() < deadline)
        assertTrue("View-model condition did not complete", condition())
    }
}
