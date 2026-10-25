package jp.co.soramitsu.wallet.impl.presentation.balance.detail

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.base.models.ErrorDialogState
import jp.co.soramitsu.common.compose.component.ActionItemType
import jp.co.soramitsu.common.data.network.config.FeatureToggleConfig
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.data.network.blockchain.balance.InMemoryPreferences
import jp.co.soramitsu.wallet.impl.domain.SendPausePolicy
import jp.co.soramitsu.wallet.impl.domain.SendPausedException
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.xcm.XcmService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BalanceDetailPausedActionsTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val bitcoin = chain(UniversalWalletRegistry.bitcoinMainnet.id, "Bitcoin")
    private val polkadot = chain(POLKADOT_ID, "Polkadot")
    private val sora = chain(SORA_ID, "SORA")

    private val externalActions = mockk<jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions.Presentation>(relaxed = true)
    private val currentAddress = mockk<jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase>(relaxed = true)
    private val router = mockk<WalletRouter>(relaxed = true)
    private val xcmService = mockk<XcmService>(relaxed = true)
    private val interactor = mockk<WalletInteractor>(relaxed = true).also { interactor ->
        listOf(bitcoin, polkadot, sora).forEach { chain ->
            // Read the id outside the stubbing block: calls on another mock inside it are recorded.
            val chainId = chain.id
            coEvery { interactor.getChain(chainId) } returns chain
        }
        coEvery { interactor.syncOperationsFirstPage(any(), any(), any(), any()) } returns
            Result.failure(IllegalStateException("history is not part of this test"))
    }
    private val resourceManager = mockk<ResourceManager>(relaxed = true).also { resources ->
        every { resources.getString(R.string.paused_cross_chain_title) } returns CROSS_CHAIN_PAUSED_TITLE
        every { resources.getString(R.string.paused_cross_chain_message) } returns CROSS_CHAIN_PAUSED_MESSAGE
        every { resources.getString(R.string.paused_cross_chain_send_on_network, "Polkadot") } returns "Send on Polkadot"
        every { resources.getString(R.string.paused_swap_title) } returns SWAP_PAUSED_TITLE
    }
    private val featureToggles = ProductFeatureToggleStore(InMemoryPreferences(), mockk(relaxed = true))
    private var bitcoinSendEnabled = true
    private var nativeRecovery: jp.co.soramitsu.wallet.impl.data.repository.tranfser.NativeSendRecovery? = null

    // Clearing the store runs onCleared, which stops the history provider's background work
    // before Dispatchers.Main is reset for the next test class.
    private val viewModelStore = ViewModelStore()

    @After
    fun tearDown() {
        viewModelStore.clear()
    }

    @Test
    fun `bitcoin SEND disabled with reason when switch off`() {
        bitcoinSendEnabled = false
        val payload = AssetPayload(bitcoin.id, "BTC")
        val viewModel = viewModel(payload)

        viewModel.actionItemClicked(ActionItemType.SEND, payload.chainId, payload.chainAssetId)

        val dialog = viewModel.lastDialog()
        assertEquals(SEND_PAUSED_TITLE, dialog.title)
        assertEquals("Sending on Bitcoin is paused", dialog.message)
        verify(exactly = 0) { router.openSend(any(), any(), any(), any()) }

        // The sheet offers Receive as the working next step.
        dialog.positiveClick()
        verify { router.openReceive(payload) }
    }

    @Test
    fun `bitcoin SEND opens the send flow without a veto`() {
        val payload = AssetPayload(bitcoin.id, "BTC")
        val viewModel = viewModel(payload)

        viewModel.actionItemClicked(ActionItemType.SEND, payload.chainId, payload.chainAssetId)

        assertNull(viewModel.errorDialogStateLiveData.value)
        verify { router.openSend(payload, any(), any(), any()) }
    }

    @Test
    fun `cross-chain paused explains and offers a same-network send`() {
        every { xcmService.canSubmitNow } returns false
        val payload = AssetPayload(polkadot.id, "DOT")
        val viewModel = viewModel(payload)

        viewModel.actionItemClicked(ActionItemType.CROSS_CHAIN, payload.chainId, payload.chainAssetId)

        val dialog = viewModel.lastDialog()
        assertEquals(CROSS_CHAIN_PAUSED_TITLE, dialog.title)
        assertEquals(CROSS_CHAIN_PAUSED_MESSAGE, dialog.message)
        assertEquals("Send on Polkadot", dialog.positiveButtonText)
        verify(exactly = 0) { router.openCrossChainSend(any()) }

        dialog.positiveClick()
        verify { router.openSend(payload, any(), any(), any()) }
    }

    @Test
    fun `cross-chain opens when submission is allowed`() {
        every { xcmService.canSubmitNow } returns true
        val payload = AssetPayload(polkadot.id, "DOT")
        val viewModel = viewModel(payload)

        viewModel.actionItemClicked(ActionItemType.CROSS_CHAIN, payload.chainId, payload.chainAssetId)

        assertNull(viewModel.errorDialogStateLiveData.value)
        verify { router.openCrossChainSend(payload) }
    }

    @Test
    fun `SORA swap enabled with no remote key`() {
        featureToggles.update(FeatureToggleConfig())
        val payload = AssetPayload(sora.id, "XOR")
        val viewModel = viewModel(payload)

        viewModel.actionItemClicked(ActionItemType.SWAP, payload.chainId, payload.chainAssetId)

        assertNull(viewModel.errorDialogStateLiveData.value)
        verify { router.openSwapTokensScreen(payload.chainId, payload.chainAssetId, null) }
    }

    @Test
    fun `SORA swap shows the paused sheet after a remote veto`() {
        featureToggles.update(FeatureToggleConfig(polkaswapMutationsEnabled = false))
        val payload = AssetPayload(sora.id, "XOR")
        val viewModel = viewModel(payload)

        viewModel.actionItemClicked(ActionItemType.SWAP, payload.chainId, payload.chainAssetId)

        assertEquals(SWAP_PAUSED_TITLE, viewModel.lastDialog().title)
        verify(exactly = 0) { router.openSwapTokensScreen(any(), any(), any()) }
    }

    @Test fun `recovery explorer rechecks wallet after delayed chain resolution`() {
        val payload = AssetPayload(bitcoin.id, "BTC")
        val recovery = mockk<jp.co.soramitsu.wallet.impl.data.repository.tranfser.NativeSendRecovery>()
        nativeRecovery = recovery
        coEvery { interactor.selectedMetaAccountFlow() } returns kotlinx.coroutines.flow.emptyFlow()
        val vm = viewModel(payload)
        val wallet = mockk<jp.co.soramitsu.account.api.domain.model.MetaAccount> { every { id } returns 1L }
        coEvery { interactor.getSelectedMetaAccount() } returns wallet
        val snapshot = jp.co.soramitsu.wallet.impl.data.repository.tranfser.NativeSendRecoverySnapshot(1, bitcoin.id, "sender",
            jp.co.soramitsu.common.data.storage.WalletSelectionEpoch.snapshot(), "a".repeat(64), jp.co.soramitsu.common.data.network.send.PendingWalletSend.State.Pending)
        vm.nativeRecoveryUi.value = jp.co.soramitsu.wallet.impl.presentation.send.NativeSendRecoveryUiState(snapshot)
        coEvery { recovery.explorerChain(snapshot) } coAnswers {
            repeat(2) { jp.co.soramitsu.common.data.storage.WalletSelectionEpoch.beginChange(); jp.co.soramitsu.common.data.storage.WalletSelectionEpoch.endChange() }
            bitcoin
        }
        vm.openNativePaymentExplorer()
        coVerify(exactly = 1) { recovery.explorerChain(snapshot) }
        verify(exactly = 0) { externalActions.viewExternalClicked(any()) }
        vm.closeNativePayment(); assertNull(vm.nativeRecoveryUi.value)
    }

    @Test fun `history explorer rejects account A B A during address lookup`() {
        val payload = AssetPayload(bitcoin.id, "BTC"); val vm = viewModel(payload)
        val wallet = mockk<jp.co.soramitsu.account.api.domain.model.MetaAccount> { every { id } returns 1L }
        coEvery { interactor.getSelectedMetaAccount() } returns wallet
        coEvery { currentAddress(payload.chainId) } coAnswers {
            repeat(2) { jp.co.soramitsu.common.data.storage.WalletSelectionEpoch.beginChange(); jp.co.soramitsu.common.data.storage.WalletSelectionEpoch.endChange() }
            "bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu"
        }
        vm.onNativeHistoryClick()
        verify(exactly = 0) { externalActions.viewExternalClicked(any()) }
    }

    @Test fun `detail balances revoke unknown numbers and recover known zero`() {
        val config = mockk<jp.co.soramitsu.core.models.Asset>(relaxed = true) {
            every { chainId } returns POLKADOT_ID
            every { id } returns "DOT"
            every { symbol } returns "DOT"
            every { precision } returns 2
            every { priceId } returns "dot"
            every { priceProvider } returns null
        }
        val zero = jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState.Known(java.math.BigInteger.ZERO)
        val token = jp.co.soramitsu.wallet.impl.domain.model.Token(config,
            jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState.Known(java.math.BigDecimal("2")),
            jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState.Missing,
            jp.co.soramitsu.wallet.impl.domain.model.FiatValuationContext("usd"), "usd", "dot")
        val known = jp.co.soramitsu.wallet.impl.domain.model.Asset.createEmpty(config, 1, ByteArray(32), minSupportedVersion = null).copy(
            token = token, freeInPlanksState = jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState.Known(java.math.BigInteger("10000")),
            reservedInPlanksState = zero, miscFrozenInPlanksState = zero, feeFrozenInPlanksState = zero,
            bondedInPlanksState = zero, redeemableInPlanksState = zero, unbondingInPlanksState = zero)
        val rows = kotlinx.coroutines.flow.MutableStateFlow(known)
        every { interactor.assetFlow(POLKADOT_ID, "DOT") } returns rows
        every { resourceManager.getString(R.string.portfolio_balance_unavailable) } returns "Unavailable"
        val vm = viewModel(AssetPayload(POLKADOT_ID, "DOT"))
        fun awaitValue(predicate: () -> Boolean) = kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.withTimeout(10_000) { while (!predicate()) kotlinx.coroutines.yield() }
        }
        awaitValue { vm.state.value.transferableViewState.additionalValue != null }
        rows.value = known.copy(freeInPlanksState = jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState.Missing,
            miscFrozenInPlanksState = jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState.Unavailable("damaged-lock"))
        awaitValue { vm.state.value.transferableViewState.value == "Unavailable" }
        assertNull(vm.state.value.transferableViewState.additionalValue)
        assertEquals("Unavailable", vm.state.value.lockedViewState.value)
        assertNull(vm.state.value.lockedViewState.additionalValue)
        assertNull(vm.state.value.lockedViewState.clickState)
        val balance = (vm.state.value.balance as jp.co.soramitsu.common.presentation.LoadingState.Loaded).data
        assertEquals("Unavailable", balance.transferableBalance)
        assertEquals("", balance.changeViewState?.fiatChange)
        rows.value = known.copy(freeInPlanksState = zero)
        awaitValue { vm.state.value.transferableViewState.additionalValue != null }
        org.junit.Assert.assertNotEquals("Unavailable", vm.state.value.transferableViewState.value)
        org.junit.Assert.assertNotEquals("Unavailable", vm.state.value.lockedViewState.value)
    }

    private fun viewModel(payload: AssetPayload): BalanceDetailViewModel {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = createViewModel(payload) as T
        }
        return ViewModelProvider(viewModelStore, factory)[BalanceDetailViewModel::class.java]
    }

    private fun createViewModel(payload: AssetPayload) = BalanceDetailViewModel(
        interactor = interactor,
        router = router,
        buyMixin = mockk(relaxed = true),
        externalAccountActions = externalActions,
        addressIconGenerator = mockk(relaxed = true),
        chainInteractor = mockk(relaxed = true),
        historyFiltersProvider = mockk(relaxed = true),
        savedStateHandle = SavedStateHandle(mapOf(KEY_ASSET_PAYLOAD to payload)),
        resourceManager = resourceManager,
        clipboardManager = mockk(relaxed = true),
        addressDisplayUseCase = mockk(relaxed = true),
        currentAccountAddress = currentAddress,
        xcmService = xcmService,
        legacyCrowdloanRecovery = mockk(relaxed = true),
        featureToggleStore = featureToggles,
        nativeRecovery = nativeRecovery,
        sendPausePolicy = SendPausePolicy(
            bitcoinSendEnabled = { bitcoinSendEnabled },
            solanaSendEnabled = { true },
            pausedErrorFactory = { chain -> SendPausedException(SEND_PAUSED_TITLE, "Sending on ${chain.name} is paused") }
        )
    )

    private fun BalanceDetailViewModel.lastDialog(): ErrorDialogState =
        requireNotNull(errorDialogStateLiveData.value) { "No paused sheet was shown" }.peekContent()

    private fun chain(id: String, name: String) = mockk<Chain>(relaxed = true).also { chain ->
        every { chain.id } returns id
        every { chain.name } returns name
        every { chain.externalApi } returns null
    }

    private companion object {
        const val POLKADOT_ID = "91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3"
        const val SORA_ID = "7e4e32d0feafd4f9c9414b0be86373f9a1efa904809b683453a9af6856d38ad5"
        const val SEND_PAUSED_TITLE = "Sending paused"
        const val CROSS_CHAIN_PAUSED_TITLE = "Cross-chain transfers paused"
        const val CROSS_CHAIN_PAUSED_MESSAGE = "Cross-chain transfers are paused"
        const val SWAP_PAUSED_TITLE = "Swaps paused"
    }
}
