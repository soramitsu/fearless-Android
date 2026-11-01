package jp.co.soramitsu.staking.impl.presentation.staking.main

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.domain.model.StoryGroup
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.staking.impl.domain.model.NetworkInfo
import jp.co.soramitsu.staking.impl.domain.rewards.PeriodReturns
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculator
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.staking.impl.presentation.staking.main.model.StakingNetworkInfoModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.filterNotNull
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
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

/** Actual root and internally constructed scenarios. Lower public streams expose
 * controlled subscription/work counts and cancellable reward-sync requests. No
 * private flow replacement, Fragment, native rendering, wallet keys or RPC.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StakingScenarioScopeLifetimeTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val account = ByteArray(32) { 1 }
    private val chainId = "public-local-relay-rendered"
    private val config = mock<ChainAsset> {
        on { this.chainId } doReturn this@StakingScenarioScopeLifetimeTest.chainId
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
    private val assetWithChain = MutableStateFlow(SingleAssetSharedState.AssetWithChain(chain, config))
    private val relayNetwork = MutableStateFlow<NetworkInfo.RelayChain?>(null)
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
    private var activeRelaySubscriptions = 0
    private val observedRelayStates = mutableListOf<StakingState>()
    private val trackedRelayStates = flow {
        activeRelaySubscriptions++
        try {
            stakingStates.collect { observedRelayStates += it; emit(it) }
        } finally { activeRelaySubscriptions-- }
    }
    private val poolStates = MutableStateFlow<StakingState?>(null)
    private val pool = mock<StakingPoolInteractor> {
        on { stakingStateFlow() } doReturn poolStates
    }
    private val relay = mock<StakingRelayChainScenarioInteractor> {
        on { stakingStateFlow() } doReturn trackedRelayStates
        on { observeStashSummary(any()) } doReturn summaries
        on { observeValidatorSummary(any()) } doReturn flowOf(validatorSummary)
        onBlocking { observeNetworkInfoState() } doReturn relayNetwork.filterNotNull()
    }
    private val selected = mock<StakingSharedState> {
        on { selectionRevision } doReturn revision
        on { selectionItem } doReturn selectionFlow
        on { settledSelection } doReturn selectionFlow
        on { currentAssetFlow() } doReturn assets
        on { assetWithChain } doReturn this@StakingScenarioScopeLifetimeTest.assetWithChain
    }
    private var currentMeta = meta
    private class SyncRequest {
        val result = CompletableDeferred<Result<Unit>>()
        var cancelled = false
    }
    private val syncRequests = mutableListOf<SyncRequest>()
    private var holdFirstSyncNonCancellable = false
    private val interactor = mock<StakingInteractor> {
        on { currentAssetFlow() } doReturn assets
        on { selectionStateFlow() } doReturn emptyFlow()
        on { selectedChainFlow() } doReturn emptyFlow()
        on { selectedAccountProjectionFlow() } doReturn emptyFlow()
        onBlocking { getCurrentMetaAccount() } doAnswer { currentMeta }
        onBlocking { syncStakingRewards(any(), any()) } doSuspendableAnswer {
            val request = SyncRequest().also(syncRequests::add)
            try {
                if (holdFirstSyncNonCancellable && request === syncRequests.first()) {
                    withContext(NonCancellable) { request.result.await() }
                } else { request.result.await() }
            }
            finally { request.cancelled = !currentCoroutineContext().isActive }
        }
    }
    private val resources = mock<ResourceManager> {
        on { getString(any()) } doAnswer { "Public-${it.getArgument<Int>(0)}" }
        on { getString(any(), anyVararg()) } doAnswer { "Public-${it.getArgument<Int>(0)}" }
        on { getQuantityString(any(), any(), anyVararg()) } doAnswer { "Public quantity ${it.getArgument<Int>(1)}" }
    }
    private val update = mock<UpdateSystem> { on { start() } doReturn emptyFlow() }
    private val alerts = mock<AlertsInteractor> { on { getAlertsFlow(any()) } doReturn emptyFlow() }
    private var useRealStories = false
    private var useHotStoryContractProbe = false
    private val storyRequests = mutableListOf<MutableStateFlow<List<StoryGroup.Staking>>>()
    private val stories = mock<StakingStoriesDataSourceImpl> {
        on { getStoriesFlow() } doAnswer {
            when {
                useHotStoryContractProbe -> MutableStateFlow(listOf(StoryGroup.Staking(1, "owner${storyRequests.size}", emptyList())))
                    .also(storyRequests::add)
                useRealStories -> StakingStoriesDataSourceImpl().getStoriesFlow()
                else -> emptyFlow()
            }
        }
    }
    private val calculator = mock<RewardCalculator> {
        onBlocking { calculateReturns(any<BigDecimal>(), any(), any(), any()) } doReturn PeriodReturns(BigDecimal.ONE, BigDecimal.ONE)
    }
    private val calculators = mock<RewardCalculatorFactory> { onBlocking { create(any()) } doReturn calculator }
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
            mock(), relay, calculators, setup, pool, StakingPoolSharedStateProvider(), mock(), stories,
            mock(), quick, mock()
        )
        settle()
        val state = vm.state.value?.stakingViewState
        assertTrue("Coherent actual root must render relay stake before every test", state is StakingViewState.RelayChain.Stake)
        assertEquals("Actual relay lower observation must be active", 1, activeRelaySubscriptions)
        assertEquals("Actual body must start one controlled reward sync", 1, syncRequests.size)
        return state as StakingViewState.RelayChain.Stake
    }

    private fun TestScope.settle() { runCurrent(); advanceTimeBy(51); runCurrent() }

    private fun TestScope.select(value: StakingAssetSelection) {
        revision.changed(value)
        selectionFlow.value = value
        settle()
    }

    private fun TestScope.selectPool() {
        select(StakingAssetSelection.Pool(chainId, config.id))
        assertEquals(StakingType.POOL, selectionFlow.value.type)
        assertNull("Actual pool scenario has no compatible synthetic stake", vm.state.value?.stakingViewState)
    }

    private fun TestScope.replaceRelayRole() {
        stakingStates.value = StakingState.Stash.Validator(chain, account, account, account, mock())
        settle()
        val fresh = vm.state.value?.stakingViewState as StakingViewState.RelayChain.Stake
        assertFalse("Actual replacement role must reach the rendered root", ManageStakeAction.VALIDATORS in fresh.manageActions)
        assertEquals("Replacement body starts its own reward refresh", 2, syncRequests.size)
    }

    @Test fun `healthy root renders while rewards wait and current failure remains visible`() = runTest(dispatcher) {
        val rendered = start()
        assertNotNull(rendered.source)
        assertNull(vm.errorLiveData.value)
        syncRequests.single().result.complete(Result.failure(IllegalStateException("current public reward failure")))
        settle()
        assertEquals("current public reward failure", vm.errorLiveData.value?.peekContent())
        assertTrue(vm.state.value?.stakingViewState is StakingViewState.RelayChain.Stake)
    }

    @Test fun `healthy root observations survive scenario round trip and fresh summary`() = runTest(dispatcher) {
        start()
        selectPool()
        select(selection)
        summaries.value = summary.copy(totalStaked = BigDecimal.TEN)
        settle()
        val fresh = vm.state.value?.stakingViewState as StakingViewState.RelayChain.Stake
        assertTrue(fresh.stakeInfoViewState.staked.value.orEmpty().contains("10"))
        assertEquals(1, activeRelaySubscriptions)
        vm.onManageRelayChainStake(fresh) { true }
        val payload = requireNotNull(vm.showManageStakeEvent.value?.peekContent())
        vm.onManageStakeActionChosen(payload, ManageStakeAction.BALANCE)
        verify(router, times(1)).openStakingBalance()
    }

    @Test fun `inactive relay scenario stops consuming lower staking observations`() = runTest(dispatcher) {
        start()
        selectPool()
        val consumedBefore = observedRelayStates.size
        stakingStates.value = StakingState.Stash.Validator(chain, account, account, account, mock())
        settle()
        assertEquals("An inactive relay owner must stop background lower-stream work", consumedBefore, observedRelayStates.size)
        assertEquals(0, activeRelaySubscriptions)
    }

    @Test fun `switching scenario cancels the old relay reward refresh`() = runTest(dispatcher) {
        start()
        val obsolete = syncRequests.single()
        selectPool()
        assertTrue("Old relay reward work must leave its owner on scenario replacement", obsolete.cancelled)
    }

    @Test fun `obsolete relay reward failure cannot surface in replacement scenario`() = runTest(dispatcher) {
        start()
        val obsolete = syncRequests.single()
        selectPool()
        obsolete.result.complete(Result.failure(IllegalStateException("obsolete relay reward failure")))
        settle()
        assertNull("An old relay result must not display an error over the pool scenario", vm.errorLiveData.value)
        verifyNoInteractions(router)
    }

    @Test fun `replacing relay role cancels the old body reward refresh`() = runTest(dispatcher) {
        start()
        val obsolete = syncRequests.single()
        replaceRelayRole()
        assertTrue("The replaced relay body must cancel its own detached refresh", obsolete.cancelled)
    }

    @Test fun `old body reward failure cannot replace current relay status`() = runTest(dispatcher) {
        start()
        val obsolete = syncRequests.single()
        replaceRelayRole()
        obsolete.result.complete(Result.failure(IllegalStateException("obsolete body reward failure")))
        settle()
        assertNull("An old body result must not publish a current-root error", vm.errorLiveData.value)
        val current = syncRequests.last()
        current.result.complete(Result.failure(IllegalStateException("fresh validator reward failure")))
        settle()
        assertEquals("fresh validator reward failure", vm.errorLiveData.value?.peekContent())
    }

    @Test fun `healthy clearing root owner cancels subscriptions and pending refresh`() = runTest(dispatcher) {
        start()
        val current = syncRequests.single()
        vm.viewModelScope.coroutineContext[Job]?.cancel()
        settle()
        assertEquals(0, activeRelaySubscriptions)
        assertTrue(current.cancelled)
        current.result.complete(Result.failure(IllegalStateException("closed root reward failure")))
        settle()
        assertNull(vm.errorLiveData.value)
    }

    @Test fun `healthy same type chain replacement refreshes actual body and network`() = runTest(dispatcher) {
        relayNetwork.value = NetworkInfo.RelayChain(24, BigInteger.ONE, BigInteger.TEN, 11, false)
        start()
        val obsolete = syncRequests.single()
        val initialNetwork = (vm.networkInfo.replayCache.last() as LoadingState.Loaded).data as StakingNetworkInfoModel.RelayChain
        assertEquals("11", initialNetwork.nominatorsCount)
        val newConfig = mock<ChainAsset> {
            on { chainId } doReturn "public-local-relay-second"
            on { id } doReturn "native"
            on { symbol } doReturn "SECOND"
            on { chainName } doReturn "Second public fixture"
            on { iconUrl } doReturn ""
            on { precision } doReturn 3
            on { staking } doReturn ChainAsset.StakingType.RELAYCHAIN
        }
        val newChain = mock<Chain> {
            on { id } doReturn "public-local-relay-second"
            on { ecosystem } doReturn Ecosystem.Substrate
            on { addressPrefix } doReturn 42
        }
        val newAsset = Asset.createEmpty(newConfig, 7, account, minSupportedVersion = null)
        assets.value = newAsset
        assetWithChain.value = SingleAssetSharedState.AssetWithChain(newChain, newConfig)
        stakingStates.value = StakingState.Stash.None(newChain, account, account, account)
        summaries.value = summary.copy(totalStaked = BigDecimal.TEN, source = StakeSummarySource.from(newAsset))
        relayNetwork.value = NetworkInfo.RelayChain(48, BigInteger.TEN, BigInteger.TEN, 22, false)
        select(StakingAssetSelection.RelayChainStaking(newConfig.chainId, newConfig.id))
        val fresh = vm.state.value?.stakingViewState as StakingViewState.RelayChain.Stake
        assertEquals(newConfig.chainId, fresh.source?.chainId)
        assertTrue(fresh.stakeInfoViewState.staked.value.orEmpty().contains("SECOND"))
        val freshNetwork = (vm.networkInfo.replayCache.last() as LoadingState.Loaded).data as StakingNetworkInfoModel.RelayChain
        assertEquals("22", freshNetwork.nominatorsCount)
        assertTrue(freshNetwork.minimumStake.contains("SECOND"))
        assertEquals(1, activeRelaySubscriptions)
        assertTrue(obsolete.cancelled)
    }

    @Test fun `healthy relay pool relay keeps network body and public amount input live`() = runTest(dispatcher) {
        relayNetwork.value = NetworkInfo.RelayChain(24, BigInteger.ONE, BigInteger.TEN, 11, false)
        start()
        whenever(config.supportStakingPool).thenReturn(true)
        whenever(pool.getMinToJoinPool(any())).thenReturn(BigInteger.ONE)
        whenever(pool.getMinToCreate(any())).thenReturn(BigInteger.TEN)
        whenever(pool.getExistingPools(any())).thenReturn(BigInteger.valueOf(3))
        whenever(pool.getPossiblePools(any())).thenReturn(BigInteger.TEN)
        whenever(pool.getMaxMembersInPool(any())).thenReturn(BigInteger.TEN)
        whenever(pool.getMaxPoolsMembers(any())).thenReturn(BigInteger.TEN)
        poolStates.value = StakingState.Pool.None(chain, account)
        select(StakingAssetSelection.Pool(chainId, config.id))
        assertTrue(vm.state.value?.stakingViewState is StakingViewState.Pool.Welcome)
        val poolNetwork = (vm.networkInfo.replayCache.last() as LoadingState.Loaded).data as StakingNetworkInfoModel.Pool
        assertEquals("3", poolNetwork.existingPools)
        vm.onPoolsAmountInput(BigDecimal("2"))
        settle()
        val poolBody = vm.state.value?.stakingViewState as StakingViewState.Pool.Welcome
        assertEquals(0, BigDecimal("2").compareTo(poolBody.estimatedEarnings.amountInputViewState.tokenAmount))
        stakingStates.value = StakingState.NonStash(chain, account)
        relayNetwork.value = NetworkInfo.RelayChain(48, BigInteger.TEN, BigInteger.TEN, 33, false)
        select(selection)
        assertTrue(vm.state.value?.stakingViewState is StakingViewState.RelayChain.Welcome)
        vm.onQuickAmountInput(1.0)
        settle()
        val relayBody = vm.state.value?.stakingViewState as StakingViewState.RelayChain.Welcome
        assertEquals(0, BigDecimal.ONE.compareTo(relayBody.estimatedEarnings.amountInputViewState.tokenAmount))
        val relayInfo = (vm.networkInfo.replayCache.last() as LoadingState.Loaded).data as StakingNetworkInfoModel.RelayChain
        assertEquals("33", relayInfo.nominatorsCount)
        assertEquals(1, activeRelaySubscriptions)
    }

    @Test fun `healthy current relay publishes real finite story datasource`() = runTest(dispatcher) {
        useRealStories = true
        start()
        assertTrue(vm.stories.replayCache.last().isNotEmpty())
        assertEquals(4, vm.stories.replayCache.last().size)
    }

    @Test fun `switching to pool clears real relay stories from root replay`() = runTest(dispatcher) {
        useRealStories = true
        start()
        assertTrue(vm.stories.replayCache.last().isNotEmpty())
        selectPool()
        assertEquals("Pool has no stories and must not retain relay content", emptyList<Any>(), vm.stories.replayCache.last())
    }

    @Test fun `healthy relay stories are available after finite datasource round trip`() = runTest(dispatcher) {
        useRealStories = true
        start()
        val initial = vm.stories.replayCache.last()
        selectPool()
        select(selection)
        verify(stories, times(2)).getStoriesFlow()
        assertEquals(initial, vm.stories.replayCache.last())
    }

    @Test fun `qualification hot story contract switches to fresh scenario stream`() = runTest(dispatcher) {
        useHotStoryContractProbe = true
        start()
        val old = storyRequests.single()
        selectPool()
        select(selection)
        assertEquals("A long-lived lower story flow must not block the current scenario", 2, storyRequests.size)
        old.value = listOf(StoryGroup.Staking(1, "obsolete", emptyList()))
        storyRequests.last().value = listOf(StoryGroup.Staking(1, "fresh", emptyList()))
        settle()
        assertEquals("fresh", vm.stories.replayCache.last().single().iconSymbol)
    }

    @Test fun `noncooperative old reward return cannot surface after scenario replacement`() = runTest(dispatcher) {
        holdFirstSyncNonCancellable = true
        start()
        val old = syncRequests.single()
        try {
            selectPool()
        } finally {
            old.result.complete(Result.failure(IllegalStateException("noncooperative old scenario failure")))
        }
        settle()
        assertNull(vm.errorLiveData.value)
        assertTrue(old.cancelled)
        select(selection)
        assertTrue(vm.state.value?.stakingViewState is StakingViewState.RelayChain.Stake)
        syncRequests.last().result.complete(Result.failure(IllegalStateException("fresh scenario failure")))
        settle()
        assertEquals("fresh scenario failure", vm.errorLiveData.value?.peekContent())
    }

    @Test fun `noncooperative old reward return cannot surface after role replacement`() = runTest(dispatcher) {
        holdFirstSyncNonCancellable = true
        start()
        val old = syncRequests.single()
        try {
            stakingStates.value = StakingState.Stash.Validator(chain, account, account, account, mock())
            runCurrent()
        } finally {
            old.result.complete(Result.failure(IllegalStateException("noncooperative old role failure")))
        }
        settle()
        assertNull(vm.errorLiveData.value)
        assertTrue(old.cancelled)
        val fresh = vm.state.value?.stakingViewState as StakingViewState.RelayChain.Stake
        assertFalse(ManageStakeAction.VALIDATORS in fresh.manageActions)
        assertEquals(2, syncRequests.size)
        syncRequests.last().result.complete(Result.failure(IllegalStateException("fresh role failure")))
        settle()
        assertEquals("fresh role failure", vm.errorLiveData.value?.peekContent())
    }

    @Test fun `reward cancellation returned as result stays silent and fresh role recovers`() = runTest(dispatcher) {
        start()
        syncRequests.single().result.complete(Result.failure(CancellationException("controlled reward cancellation")))
        settle()
        assertNull(vm.errorLiveData.value)
        replaceRelayRole()
        syncRequests.last().result.complete(Result.failure(IllegalStateException("fresh after cancellation")))
        settle()
        assertEquals("fresh after cancellation", vm.errorLiveData.value?.peekContent())
    }
}
