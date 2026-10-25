package jp.co.soramitsu.staking.impl.presentation.staking.main

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import jp.co.soramitsu.staking.api.data.StakingAssetSelection
import jp.co.soramitsu.staking.api.data.StakingSelectionRevision
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.data.StakingType
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.data.repository.datasource.StakingStoriesDataSourceImpl
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.alerts.AlertsInteractor
import jp.co.soramitsu.staking.impl.domain.model.StakeSummary
import jp.co.soramitsu.staking.impl.domain.model.StakeSummarySource
import jp.co.soramitsu.staking.impl.domain.model.StashNoneStatus
import jp.co.soramitsu.staking.impl.domain.model.ValidatorStatus
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.wallet.impl.domain.interfaces.QuickInputsUseCase
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*
import jp.co.soramitsu.core.models.Asset as ChainAsset

/** Test-only baseline: actual root, its actual internal StakingScenario/relay VM, and
 * actual combined/debounced root state. Only lower public observation dependencies
 * are mocked; no private flow replacement, Fragment, renderer, wallet keys or RPC.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StakingRenderedRelayActionContextTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val account = ByteArray(32) { 1 }
    private val chainId = "public-local-relay-rendered"
    private val config = mock<ChainAsset> {
        on { this.chainId } doReturn this@StakingRenderedRelayActionContextTest.chainId
        on { id } doReturn "native"
        on { symbol } doReturn "LOCAL"
        on { chainName } doReturn "Local public fixture"
        on { iconUrl } doReturn ""
        on { precision } doReturn 2
        on { staking } doReturn ChainAsset.StakingType.RELAYCHAIN
    }
    private val chain = mock<Chain> {
        on { id } doReturn chainId
        on { ecosystem } doReturn Ecosystem.Substrate
        on { addressPrefix } doReturn 42
    }
    private val asset = Asset.createEmpty(config, 7, account, minSupportedVersion = null)
    private val assets = MutableStateFlow(asset)
    private val selection = StakingAssetSelection.RelayChainStaking(chainId, config.id)
    private val revision = StakingSelectionRevision().apply { changed(selection) }
    private val selectionFlow = MutableStateFlow<StakingAssetSelection>(selection)
    private val meta = mock<MetaAccount> {
        on { id } doReturn 7L
        on { chainAccounts } doReturn emptyMap()
        on { substrateAccountId } doReturn account
    }
    private val stash = StakingState.Stash.None(chain, account, account, account)
    private val stakingStates = MutableStateFlow<StakingState>(stash)
    private val source = StakeSummarySource.from(asset)
    private val summary = StakeSummary(StashNoneStatus.INACTIVE, BigDecimal.ONE, BigDecimal.ZERO, 12, source)
    private val summaries = MutableStateFlow<StakeSummary<StashNoneStatus>?>(summary)
    private val validatorSummary = StakeSummary(ValidatorStatus.ACTIVE, BigDecimal.ONE, BigDecimal.ZERO, 12, source)
    private val relay = mock<StakingRelayChainScenarioInteractor> {
        on { stakingStateFlow() } doReturn stakingStates
        on { observeStashSummary(any()) } doReturn summaries
        on { observeValidatorSummary(any()) } doReturn flowOf(validatorSummary)
        onBlocking { observeNetworkInfoState() } doReturn emptyFlow()
    }
    private val selected = mock<StakingSharedState> {
        on { selectionRevision } doReturn revision
        on { selectionItem } doReturn selectionFlow
        on { currentAssetFlow() } doReturn assets
        on { assetWithChain } doReturn flowOf(SingleAssetSharedState.AssetWithChain(chain, config))
    }
    private var currentMeta = meta
    private val interactor = mock<StakingInteractor> {
        on { currentAssetFlow() } doReturn assets
        on { selectionStateFlow() } doReturn emptyFlow()
        on { selectedChainFlow() } doReturn emptyFlow()
        on { selectedAccountProjectionFlow() } doReturn emptyFlow()
        onBlocking { getCurrentMetaAccount() } doAnswer { currentMeta }
        onBlocking { syncStakingRewards(any(), any()) } doReturn Result.success(Unit)
    }
    private val resources = mock<ResourceManager> {
        on { getString(any()) } doAnswer { "Public-${it.getArgument<Int>(0)}" }
        on { getString(any(), anyVararg()) } doAnswer { "Public-${it.getArgument<Int>(0)}" }
    }
    private val update = mock<UpdateSystem> { on { start() } doReturn emptyFlow() }
    private val alerts = mock<AlertsInteractor> { on { getAlertsFlow(any()) } doReturn emptyFlow() }
    private val stories = mock<StakingStoriesDataSourceImpl> { on { getStoriesFlow() } doReturn emptyFlow() }
    private val setupFlow = MutableStateFlow<SetupStakingProcess>(SetupStakingProcess.Initial(StakingType.RELAYCHAIN))
    private val setup = mock<SetupStakingSharedState> { on { setupStakingProcess } doReturn setupFlow }
    private val quick = mock<QuickInputsUseCase> {
        onBlocking { calculateStakingQuickInputs(any(), any(), any(), any()) } doReturn mapOf(1.0 to BigDecimal.ONE)
    }
    private val router = mock<StakingRouter>()
    private lateinit var vm: StakingViewModel

    @Before fun setupMain() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() {
        if (!::vm.isInitialized) { Dispatchers.resetMain(); return }
        val owner = vm.viewModelScope.coroutineContext[Job]
        try {
            owner?.cancel()
            dispatcher.scheduler.runCurrent()
            runBlocking(Dispatchers.Default) {
                withTimeout(5000) {
                    while (owner?.isCompleted == false) {
                        dispatcher.scheduler.runCurrent()
                        kotlinx.coroutines.delay(1)
                    }
                    owner?.join()
                }
            }
            dispatcher.scheduler.runCurrent()
        } finally { Dispatchers.resetMain() }
    }

    private fun TestScope.start(): StakingViewState.RelayChain.Stake {
        vm = StakingViewModel(
            interactor, alerts, mock(), router, resources, ValidationExecutor(resources), update, selected,
            mock(), relay, mock(), setup, mock(), StakingPoolSharedStateProvider(), mock(), stories,
            mock(), quick, mock()
        )
        settle()
        val state = vm.state.value?.stakingViewState
        assertTrue("Coherent actual root must render relay stake before every test", state is StakingViewState.RelayChain.Stake)
        return state as StakingViewState.RelayChain.Stake
    }

    private fun TestScope.settle() { runCurrent(); advanceTimeBy(51); runCurrent() }
    private fun payload() = requireNotNull(vm.showManageStakeEvent.value?.peekContent())
    private fun choose(value: ManageStakingBottomSheet.Payload, action: ManageStakeAction = ManageStakeAction.BALANCE) {
        vm.onManageStakeActionChosen(value, action)
    }

    @Test fun `healthy actual root rendered management routes once`() = runTest(dispatcher) {
        val rendered = start()
        vm.onManageRelayChainStake(rendered) { true }
        val current = payload(); choose(current); choose(current)
        verify(router, times(1)).openStakingBalance()
    }

    @Test fun `healthy actual root status uses current rendered message`() = runTest(dispatcher) {
        val rendered = start()
        vm.onRelayChainStatusClick(rendered)
        assertEquals(rendered.statusInfo, vm.showStakingStatusEvent.value?.peekContent())
    }

    @Test fun `retained rendered body cannot open management after root becomes unavailable`() = runTest(dispatcher) {
        val rendered = start()
        summaries.value = null; settle()
        assertNull("Precondition: actual root has withdrawn the body", vm.state.value?.stakingViewState)
        vm.onManageRelayChainStake(rendered) { true }
        assertNull(vm.showManageStakeEvent.value)
        verifyNoInteractions(router)
    }

    @Test fun `rendered action cannot be admitted during root unavailable debounce`() = runTest(dispatcher) {
        val rendered = start()
        summaries.value = null; runCurrent()
        // Do not assume the old body must remain for a fixed number of frames: a correction
        // may remove the debounce. This tests admission before advancing virtual time.
        vm.onManageRelayChainStake(rendered) { true }
        assertNull(vm.showManageStakeEvent.value)
        verifyNoInteractions(router)
    }

    @Test fun `published management is revoked when actual summary becomes unavailable`() = runTest(dispatcher) {
        val rendered = start()
        vm.onManageRelayChainStake(rendered) { true }; val original = payload()
        summaries.value = null; settle()
        assertNull(vm.state.value?.stakingViewState)
        choose(original)
        verify(router, never()).openStakingBalance()
        assertFalse(vm.isManagementPresentationCurrent(original))
        assertNull(vm.showManageStakeEvent.value)
    }

    @Test fun `role replacement cannot reopen validators from old rendered permissions`() = runTest(dispatcher) {
        val rendered = start()
        assertTrue(ManageStakeAction.VALIDATORS in rendered.manageActions)
        stakingStates.value = StakingState.Stash.Validator(chain, account, account, account, mock())
        settle()
        val fresh = vm.state.value?.stakingViewState as StakingViewState.RelayChain.Stake
        assertFalse("Actual replacement role removes validators", ManageStakeAction.VALIDATORS in fresh.manageActions)
        vm.onManageRelayChainStake(rendered) { true }
        vm.showManageStakeEvent.value?.peekContent()?.let { choose(it, ManageStakeAction.VALIDATORS) }
        verify(router, never()).openCurrentValidators()
        assertNull(vm.showManageStakeEvent.value)
    }

    @Test fun `published validators permission expires when current role removes it`() = runTest(dispatcher) {
        val rendered = start()
        vm.onManageRelayChainStake(rendered) { true }; val original = payload()
        stakingStates.value = StakingState.Stash.Validator(chain, account, account, account, mock())
        settle()
        val fresh = vm.state.value?.stakingViewState as StakingViewState.RelayChain.Stake
        assertFalse(ManageStakeAction.VALIDATORS in fresh.manageActions)
        choose(original, ManageStakeAction.VALIDATORS)
        verify(router, never()).openCurrentValidators()
        assertFalse(vm.isManagementPresentationCurrent(original))
    }

    @Test fun `unavailable and equal recovery cannot revive old dialog but fresh dialog works`() = runTest(dispatcher) {
        val rendered = start()
        vm.onManageRelayChainStake(rendered) { true }; val old = payload()
        summaries.value = null; settle(); assertNull(vm.state.value?.stakingViewState)
        summaries.value = summary; settle()
        val fresh = vm.state.value?.stakingViewState as StakingViewState.RelayChain.Stake
        choose(old)
        verify(router, never()).openStakingBalance()
        vm.onManageRelayChainStake(fresh) { true }; choose(payload())
        verify(router, times(1)).openStakingBalance()
    }

    @Test fun `current fresh role management remains usable after summary return`() = runTest(dispatcher) {
        start()
        summaries.value = null; settle(); assertNull(vm.state.value?.stakingViewState)
        summaries.value = summary; settle()
        val fresh = vm.state.value?.stakingViewState as StakingViewState.RelayChain.Stake
        vm.onManageRelayChainStake(fresh) { true }; choose(payload())
        verify(router, times(1)).openStakingBalance()
    }

    @Test fun `retained status cannot publish after actual root withdraws body`() = runTest(dispatcher) {
        val rendered = start()
        summaries.value = null; settle(); assertNull(vm.state.value?.stakingViewState)
        vm.onRelayChainStatusClick(rendered)
        assertNull(vm.showStakingStatusEvent.value)
    }

    @Test fun `retained status cannot publish after visible owner is paused`() = runTest(dispatcher) {
        val rendered = start()
        vm.onStakingScreenPaused()
        vm.onRelayChainStatusClick(rendered)
        assertNull(vm.showStakingStatusEvent.value)
    }

    @Test fun `equal recovery inside debounce cannot revive old body or dialog`() = runTest(dispatcher) {
        val oldBody = start()
        vm.onManageRelayChainStake(oldBody) { true }; val oldDialog = payload()
        summaries.value = null; runCurrent()
        summaries.value = summary; runCurrent()
        vm.onManageRelayChainStake(oldBody) { true }
        choose(oldDialog)
        assertNull(vm.showManageStakeEvent.value)
        verifyNoInteractions(router)
        settle()
        val fresh = vm.state.value?.stakingViewState as StakingViewState.RelayChain.Stake
        assertNotSame(oldBody, fresh)
        vm.onManageRelayChainStake(fresh) { true }; choose(payload())
        verify(router, times(1)).openStakingBalance()
    }

    @Test fun `resume publishes fresh body while retained status remains revoked`() = runTest(dispatcher) {
        val oldBody = start()
        vm.onStakingScreenPaused(); vm.onStakingScreenResumed(); settle()
        vm.onRelayChainStatusClick(oldBody)
        assertNull(vm.showStakingStatusEvent.value)
        val fresh = vm.state.value?.stakingViewState as StakingViewState.RelayChain.Stake
        assertNotSame(oldBody, fresh)
        vm.onRelayChainStatusClick(fresh)
        assertEquals(fresh.statusInfo, vm.showStakingStatusEvent.value?.peekContent())
    }

    @Test fun `status requires current native view and permits fresh visible owner`() = runTest(dispatcher) {
        val rendered = start()
        vm.onRelayChainStatusClick(rendered) { false }
        assertNull(vm.showStakingStatusEvent.value)
        vm.onRelayChainStatusClick(rendered) { true }
        assertEquals(rendered.statusInfo, vm.showStakingStatusEvent.value?.peekContent())
    }

    @Test fun `lagging old scenario emission cannot adopt new synchronous selection intent`() = runTest(dispatcher) {
        start()
        revision.changed(StakingAssetSelection.RelayChainStaking("different-local-chain", "native"))
        summaries.value = summary.copy(totalStaked = BigDecimal.TEN); settle()
        val lagging = vm.state.value?.stakingViewState as? StakingViewState.RelayChain.Stake
        lagging?.let { vm.onManageRelayChainStake(it) { true } }
        assertNull(vm.showManageStakeEvent.value)
        verifyNoInteractions(router)
    }

    @Test fun `resume cannot reauthorize cached body under changed selection intent`() = runTest(dispatcher) {
        start(); vm.onStakingScreenPaused()
        revision.changed(StakingAssetSelection.RelayChainStaking("different-local-chain", "native"))
        vm.onStakingScreenResumed(); settle()
        val cached = vm.state.value?.stakingViewState as? StakingViewState.RelayChain.Stake
        cached?.let { vm.onManageRelayChainStake(it) { true } }
        assertNull(vm.showManageStakeEvent.value)
        verifyNoInteractions(router)
    }

    @Test fun `fresh coherent original selection after intent return remains usable`() = runTest(dispatcher) {
        start()
        revision.changed(StakingAssetSelection.RelayChainStaking("different-local-chain", "native"))
        revision.changed(selection)
        summaries.value = summary.copy(totalStaked = BigDecimal.TEN); settle()
        val fresh = vm.state.value?.stakingViewState as StakingViewState.RelayChain.Stake
        vm.onManageRelayChainStake(fresh) { true }; choose(payload())
        verify(router, times(1)).openStakingBalance()
    }

    @Test fun `lagging old wallet body cannot adopt new wallet epoch`() = runTest(dispatcher) {
        start()
        WalletSelectionEpoch.beginChange()
        currentMeta = mock { on { id } doReturn 9L; on { chainAccounts } doReturn emptyMap(); on { substrateAccountId } doReturn account }
        WalletSelectionEpoch.endChange()
        summaries.value = summary.copy(totalStaked = BigDecimal.TEN); settle()
        val lagging = vm.state.value?.stakingViewState as? StakingViewState.RelayChain.Stake
        lagging?.let { vm.onManageRelayChainStake(it) { true } }
        assertNull(vm.showManageStakeEvent.value)
        verifyNoInteractions(router)
    }

    @Test fun `fresh coherent replacement wallet body remains usable`() = runTest(dispatcher) {
        start()
        WalletSelectionEpoch.beginChange()
        currentMeta = mock { on { id } doReturn 9L; on { chainAccounts } doReturn emptyMap(); on { substrateAccountId } doReturn account }
        WalletSelectionEpoch.endChange()
        val freshAsset = Asset.createEmpty(config, 9, account, minSupportedVersion = null)
        assets.value = freshAsset
        summaries.value = summary.copy(totalStaked = BigDecimal.TEN, source = StakeSummarySource.from(freshAsset)); settle()
        val fresh = vm.state.value?.stakingViewState as StakingViewState.RelayChain.Stake
        vm.onManageRelayChainStake(fresh) { true }; choose(payload())
        verify(router, times(1)).openStakingBalance()
    }

    @Test fun `held metadata admission loses authority on selection change and fresh return recovers`() = runTest(dispatcher) {
        val old = start()
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        whenever(interactor.getCurrentMetaAccount()).doSuspendableAnswer {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { gate.await() }
            currentMeta
        }
        summaries.value = summary.copy(totalStaked = BigDecimal.TEN); runCurrent()
        vm.onManageRelayChainStake(old) { true }; assertNull(vm.showManageStakeEvent.value)
        revision.changed(StakingAssetSelection.RelayChainStaking("different-local-chain", "native")); runCurrent()
        gate.complete(Unit); settle()
        assertNull(vm.state.value?.stakingViewState)
        vm.onManageRelayChainStake(old) { true }; assertNull(vm.showManageStakeEvent.value)
        revision.changed(selection); settle()
        val fresh = vm.state.value?.stakingViewState as StakingViewState.RelayChain.Stake
        vm.onManageRelayChainStake(fresh) { true }; choose(payload())
        verify(router, times(1)).openStakingBalance()
    }

    @Test fun `metadata failure withdraws old body and later coherent update recovers`() = runTest(dispatcher) {
        val old = start()
        whenever(interactor.getCurrentMetaAccount()).doSuspendableAnswer { throw java.io.IOException("Public fixture metadata unavailable") }
        summaries.value = summary.copy(totalStaked = BigDecimal.TEN); settle()
        assertNull(vm.state.value?.stakingViewState)
        vm.onManageRelayChainStake(old) { true }; assertNull(vm.showManageStakeEvent.value)
        doReturn(currentMeta).whenever(interactor).getCurrentMetaAccount()
        summaries.value = summary.copy(totalStaked = BigDecimal("11")); settle()
        val fresh = vm.state.value?.stakingViewState as StakingViewState.RelayChain.Stake
        vm.onManageRelayChainStake(fresh) { true }; choose(payload())
        verify(router, times(1)).openStakingBalance()
    }

    @Test fun `registry denomination mismatch cannot authorize body and coherent registry recovers`() = runTest(dispatcher) {
        val old = start()
        val changedConfig = mock<ChainAsset> {
            on { chainId } doReturn this@StakingRenderedRelayActionContextTest.chainId
            on { id } doReturn "native"; on { precision } doReturn 3; on { symbol } doReturn "LOCAL"
            on { staking } doReturn ChainAsset.StakingType.RELAYCHAIN
        }
        whenever(selected.assetWithChain).doReturn(flowOf(SingleAssetSharedState.AssetWithChain(chain, changedConfig)))
        summaries.value = summary.copy(totalStaked = BigDecimal.TEN); settle()
        assertNull(vm.state.value?.stakingViewState)
        vm.onManageRelayChainStake(old) { true }; assertNull(vm.showManageStakeEvent.value)
        whenever(selected.assetWithChain).doReturn(flowOf(SingleAssetSharedState.AssetWithChain(chain, config)))
        summaries.value = summary.copy(totalStaked = BigDecimal("11")); settle()
        val fresh = vm.state.value?.stakingViewState as StakingViewState.RelayChain.Stake
        vm.onManageRelayChainStake(fresh) { true }; choose(payload())
        verify(router, times(1)).openStakingBalance()
    }
}
