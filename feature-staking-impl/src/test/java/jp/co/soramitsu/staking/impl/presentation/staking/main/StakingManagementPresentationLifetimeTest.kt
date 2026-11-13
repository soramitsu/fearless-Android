package jp.co.soramitsu.staking.impl.presentation.staking.main

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.Observer
import jp.co.soramitsu.common.utils.Event
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.math.BigDecimal
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import jp.co.soramitsu.staking.api.data.StakingSelectionRevision
import jp.co.soramitsu.staking.api.data.StakingAssetSelection
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios.StakingScenarioViewModel
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*
import jp.co.soramitsu.core.models.Asset as ChainAsset

/** Actual root management callbacks; baseline65c02543a reproduces11 stale publications/routes. */
@OptIn(ExperimentalCoroutinesApi::class)
class StakingManagementPresentationLifetimeTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val account = ByteArray(32) { (it + 1).toByte() }
    private val chainAsset = mock<ChainAsset> {
        on { chainId } doReturn "local-pool-chain"
        on { id } doReturn "native"
        on { symbol } doReturn "LOCAL"
        on { precision } doReturn 10
        on { staking } doReturn ChainAsset.StakingType.RELAYCHAIN
    }
    private val chain = mock<Chain> {
        on { id } doReturn "local-pool-chain"
        on { ecosystem } doReturn Ecosystem.Substrate
        on { addressPrefix } doReturn 42
    }
    private val asset = Asset.createEmpty(chainAsset, 7, account, minSupportedVersion = null)
    private val meta = mock<MetaAccount> {
        on { id } doReturn 7L
        on { chainAccounts } doReturn emptyMap()
        on { substrateAccountId } doReturn account
    }
    private val revision = StakingSelectionRevision()
    private val selected = mock<StakingSharedState> {
        on { selectionRevision } doReturn revision
        on { currentAssetFlow() } doReturn MutableStateFlow(asset)
        on { assetWithChain } doReturn flowOf(SingleAssetSharedState.AssetWithChain(chain, chainAsset))
        on { selectionItem } doReturn emptyFlow()
        on { settledSelection } doReturn emptyFlow()
    }
    private val interactor = mock<StakingInteractor> {
        onBlocking { getCurrentMetaAccount() } doReturn meta
        on { selectionStateFlow() } doReturn emptyFlow()
        on { selectedChainFlow() } doReturn emptyFlow()
        on { selectedAccountProjectionFlow() } doReturn emptyFlow()
    }
    private val updateSystem = mock<UpdateSystem> { on { start() } doReturn emptyFlow() }
    private val resources = mock<ResourceManager> {
        on { getString(any()) } doAnswer { "text-${it.getArgument<Int>(0)}" }
        on { getString(any(), anyVararg()) } doAnswer { "text-${it.getArgument<Int>(0)}" }
    }
    private val router = mock<StakingRouter>()
    private val shared = StakingPoolSharedStateProvider()
    private val payload = StakingRewardEstimationBottomSheet.Payload("1%", "2%", 1, 2)
    private val gate = CompletableDeferred<Unit>()
    private val calls = mutableListOf<String>()
    private var owned: StakingViewModel? = null
    private var publishedCallback: ((ManageStakeAction) -> Unit)? = null
    private var publicationObserver: Observer<Event<ManageStakingBottomSheet.Payload>?>? = null

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() {
        publicationObserver?.let { observer -> owned?.showManageStakeEvent?.removeObserver(observer) }
        val ownerJob = owned?.viewModelScope?.coroutineContext?.get(Job)
        try {
            ownerJob?.cancel()
            dispatcher.scheduler.runCurrent()
            runBlocking(Dispatchers.Default) { withTimeout(5000) { ownerJob?.join() } }
            dispatcher.scheduler.runCurrent()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `current management action routes once`() = runTest(dispatcher) {
        val vm = viewModel(MutableStateFlow(scenario()))
        vm.onManageRelayChainStake(manageState())
        assertNotNull(vm.showManageStakeEvent.value)
        chosen(vm, ManageStakeAction.PAYOUTS)
        chosen(vm, ManageStakeAction.PAYOUTS)
        verify(router).openPayouts()
    }

    @Test fun `paused root cannot create a new management presentation`() = runTest(dispatcher) {
        val vm = viewModel(MutableStateFlow(scenario()))
        vm.onStakingScreenPaused()
        vm.onManageRelayChainStake(manageState())
        assertNull(vm.showManageStakeEvent.value)
        chosen(vm, ManageStakeAction.PAYOUTS)
        verify(router, never()).openPayouts()
    }

    @Test fun `management callback after wallet ABA cannot navigate`() = runTest(dispatcher) {
        val vm = viewModel(MutableStateFlow(scenario()))
        vm.onManageRelayChainStake(manageState())
        WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange()
        chosen(vm, ManageStakeAction.PAYOUTS)
        verify(router, never()).openPayouts()
    }

    @Test fun `management callback after staking intent change cannot navigate`() = runTest(dispatcher) {
        val vm = viewModel(MutableStateFlow(scenario()))
        vm.onManageRelayChainStake(manageState())
        revision.changed(StakingAssetSelection.RelayChainStaking("another-chain", "native"))
        chosen(vm, ManageStakeAction.PAYOUTS)
        verify(router, never()).openPayouts()
    }

    @Test fun `cleared owner cannot route retained management action`() = runTest(dispatcher) {
        val vm = viewModel(MutableStateFlow(scenario()))
        vm.onManageRelayChainStake(manageState())
        androidx.lifecycle.ViewModelStore().also { it.put("staking", vm); it.clear() }
        chosen(vm, ManageStakeAction.PAYOUTS)
        verify(router, never()).openPayouts()
    }

    @Test fun `pause and resume does not revive old management permission`() = runTest(dispatcher) {
        val vm = viewModel(MutableStateFlow(scenario()))
        vm.onManageRelayChainStake(manageState())
        vm.onStakingScreenPaused(); vm.onStakingScreenResumed()
        chosen(vm, ManageStakeAction.PAYOUTS)
        verify(router, never()).openPayouts()
    }

    @Test fun `fresh current management action after return remains usable`() = runTest(dispatcher) {
        val vm = viewModel(MutableStateFlow(scenario()))
        vm.onManageRelayChainStake(manageState())
        vm.onStakingScreenPaused(); vm.onStakingScreenResumed()
        vm.onManageRelayChainStake(manageState())
        chosen(vm, ManageStakeAction.PAYOUTS)
        verify(router).openPayouts()
    }

    @Test fun `old same-action presentation cannot route or consume replacement`() = runTest(dispatcher) {
        val vm = viewModel(MutableStateFlow(scenario()))
        vm.onManageRelayChainStake(manageState())
        val first = capturedCallback(vm)
        vm.onManageRelayChainStake(manageState())
        val second = capturedCallback(vm)
        first(ManageStakeAction.PAYOUTS)
        verify(router, never()).openPayouts()
        second(ManageStakeAction.PAYOUTS)
        verify(router).openPayouts()
    }

    @Test fun `inline observer pause revokes published management`() = runTest(dispatcher) {
        val vm = viewModel(MutableStateFlow(scenario()))
        observeOnce(vm) { vm.onStakingScreenPaused() }
        chosen(vm, ManageStakeAction.PAYOUTS)
        verify(router, never()).openPayouts()
    }

    @Test fun `inline observer clear revokes published management`() = runTest(dispatcher) {
        val vm = viewModel(MutableStateFlow(scenario()))
        observeOnce(vm) { androidx.lifecycle.ViewModelStore().also { it.put("staking", vm); it.clear() } }
        chosen(vm, ManageStakeAction.PAYOUTS)
        verify(router, never()).openPayouts()
    }

    @Test fun `inline observer wallet change revokes published management`() = runTest(dispatcher) {
        val vm = viewModel(MutableStateFlow(scenario()))
        observeOnce(vm) { WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange() }
        chosen(vm, ManageStakeAction.PAYOUTS)
        verify(router, never()).openPayouts()
    }

    @Test fun `inline observer intent change revokes published management`() = runTest(dispatcher) {
        val vm = viewModel(MutableStateFlow(scenario()))
        observeOnce(vm) { revision.changed(StakingAssetSelection.RelayChainStaking("another-chain", "native")) }
        chosen(vm, ManageStakeAction.PAYOUTS)
        verify(router, never()).openPayouts()
    }

    @Test fun `inline replacement keeps only its own callback usable`() = runTest(dispatcher) {
        val vm = viewModel(MutableStateFlow(scenario()))
        var first: ((ManageStakeAction) -> Unit)? = null
        observeOnce(vm) {
            first = capturedCallback(vm)
            vm.onManageRelayChainStake(manageState())
        }
        val second = capturedCallback(vm)
        requireNotNull(first)(ManageStakeAction.PAYOUTS)
        verify(router, never()).openPayouts()
        second(ManageStakeAction.PAYOUTS)
        verify(router).openPayouts()
    }

    @Test fun `already cleared owner cannot publish management`() = runTest(dispatcher) {
        val vm = viewModel(MutableStateFlow(scenario()))
        androidx.lifecycle.ViewModelStore().also { it.put("staking", vm); it.clear() }
        vm.onManageRelayChainStake(manageState())
        assertNull(vm.showManageStakeEvent.value)
    }

    @Test fun `wallet transition cannot publish management`() = runTest(dispatcher) {
        val vm = viewModel(MutableStateFlow(scenario()))
        WalletSelectionEpoch.beginChange()
        try {
            vm.onManageRelayChainStake(manageState())
            assertNull(vm.showManageStakeEvent.value)
        } finally { WalletSelectionEpoch.endChange() }
    }

    /** Matches the actual Fragment callback wiring with the exact payload captured at publication. */
    private fun capturedCallback(vm: StakingViewModel): (ManageStakeAction) -> Unit {
        val payload = requireNotNull(vm.showManageStakeEvent.value?.peekContent())
        return { action -> vm.onManageStakeActionChosen(payload, action) }
    }

    // Retain the actual last-published callback across a null clear, as an old native dialog can.
    private fun chosen(vm: StakingViewModel, action: ManageStakeAction) {
        assertSame(owned, vm)
        publishedCallback?.invoke(action)
    }

    private fun observeOnce(vm: StakingViewModel, action: () -> Unit) {
        var first = true
        val observer = Observer<Event<ManageStakingBottomSheet.Payload>?> { event ->
            if (event != null && first) { first = false; action() }
        }
        vm.showManageStakeEvent.observeForever(observer)
        try { vm.onManageRelayChainStake(manageState()) }
        finally { vm.showManageStakeEvent.removeObserver(observer) }
        assertFalse("Observer must execute for a real publication", first)
    }

    @Test fun `dismissed management receipt cannot navigate`() = runTest(dispatcher) {
        val vm = viewModel(MutableStateFlow(scenario()))
        vm.onManageRelayChainStake(manageState())
        val payload = requireNotNull(vm.showManageStakeEvent.value?.peekContent())
        val callback = capturedCallback(vm)
        vm.onManagementPresentationDismissed(payload)
        callback(ManageStakeAction.PAYOUTS)
        assertFalse(vm.isManagementPresentationCurrent(payload))
        verify(router, never()).openPayouts()
    }

    @Test fun `late old dismissal cannot clear replacement permission`() = runTest(dispatcher) {
        val vm = viewModel(MutableStateFlow(scenario()))
        vm.onManageRelayChainStake(manageState())
        val old = requireNotNull(vm.showManageStakeEvent.value?.peekContent())
        vm.onManageRelayChainStake(manageState())
        val current = requireNotNull(vm.showManageStakeEvent.value?.peekContent())
        val callback = capturedCallback(vm)
        vm.onManagementPresentationDismissed(old)
        assertTrue(vm.isManagementPresentationCurrent(current))
        callback(ManageStakeAction.PAYOUTS)
        verify(router).openPayouts()
    }

    @Test fun `view replacement invalidates management before lifecycle pause`() = runTest(dispatcher) {
        val vm = viewModel(MutableStateFlow(scenario()))
        var current = true
        vm.onManageRelayChainStake(manageState()) { current }
        val callback = capturedCallback(vm)
        current = false
        callback(ManageStakeAction.PAYOUTS)
        verify(router, never()).openPayouts()
    }

    @Test fun `stale native view cannot publish management`() = runTest(dispatcher) {
        val vm = viewModel(MutableStateFlow(scenario()))
        vm.onManageRelayChainStake(manageState()) { false }
        assertNull(vm.showManageStakeEvent.value)
    }

    @Test fun `mutating source action set cannot add an unreviewed permission`() = runTest(dispatcher) {
        val vm = viewModel(MutableStateFlow(scenario()))
        val actions = mutableSetOf(ManageStakeAction.PAYOUTS)
        val state = publishState(actions)
        vm.onManageRelayChainStake(state)
        val callback = capturedCallback(vm)
        actions.add(ManageStakeAction.BALANCE)
        callback(ManageStakeAction.BALANCE)
        verify(router, never()).openStakingBalance()
        callback(ManageStakeAction.PAYOUTS)
        verify(router).openPayouts()
    }

    @Test fun `wallet epoch signal withdraws an already published event`() = runTest(dispatcher) {
        val vm = viewModel(MutableStateFlow(scenario()))
        vm.onManageRelayChainStake(manageState())
        assertNotNull(vm.showManageStakeEvent.value)
        WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange()
        runCurrent()
        assertNull(vm.showManageStakeEvent.value)
    }

    @Test fun `repeated selection signal withdraws an already published event`() = runTest(dispatcher) {
        val vm = viewModel(MutableStateFlow(scenario()))
        val selected = StakingAssetSelection.RelayChainStaking("local-pool-chain", "native")
        revision.changed(selected)
        vm.onManageRelayChainStake(manageState())
        assertNotNull(vm.showManageStakeEvent.value)
        revision.changed(selected)
        runCurrent()
        assertNull(vm.showManageStakeEvent.value)
    }

    private fun manageState() = publishState(setOf(ManageStakeAction.PAYOUTS))

    private fun publishState(actions: Set<ManageStakeAction>): StakingViewState.RelayChain.Stake {
        val raw = StakingViewState.RelayChain.Stake(mock(), actions, StakingViewState.StatusInfo("fixture", "fixture"))
        // Isolated management-window tests supply the raw publication, not eligibility. The
        // actual-root suite separately exercises the internal scenario and public lower flows.
        return StakingViewModel::class.java.declaredMethods.single { it.name == "publishStakingBody" }
            .apply { isAccessible = true }
            .invoke(requireNotNull(owned), raw, runCatching { WalletSelectionEpoch.snapshot() }.getOrNull(), revision.capture())
            as StakingViewState.RelayChain.Stake
    }

    private fun scenario() = mock<StakingScenarioViewModel> { on { stakingType } doReturn jp.co.soramitsu.staking.api.data.StakingType.RELAYCHAIN; on { enteredAmountFlow } doReturn MutableStateFlow<BigDecimal?>(BigDecimal.ONE) }
    private fun viewModel(source: SharedFlow<StakingScenarioViewModel>) = StakingViewModel(
        interactor, mock(), mock(), router, resources, mock(), updateSystem, selected,
        mock(), mock(), mock(), mock(), mock(), shared, mock(), mock(), mock(), mock(), mock()
    ).also {
        owned = it
        publicationObserver = Observer<Event<ManageStakingBottomSheet.Payload>?> { event ->
            if (event != null) publishedCallback = capturedCallback(it)
        }.also { observer -> it.showManageStakeEvent.observeForever(observer) }
        // Only scenario creation is supplied; root action scheduling/guards/publication execute unchanged.
        StakingViewModel::class.java.getDeclaredField("scenarioViewModelFlow").apply { isAccessible = true }.set(it, source)
    }
}
