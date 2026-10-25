package jp.co.soramitsu.staking.impl.presentation.staking.main

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.staking.impl.domain.alerts.Alert
import jp.co.soramitsu.staking.impl.presentation.staking.alerts.model.AlertModel
import kotlinx.coroutines.delay
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

/** Actual root and its internally constructed scenario publish the retained alert models.
 * Lower public data and account-required validation are controlled; no private root-flow
 * replacement, native row renderer, keys or public RPC are used in this JVM fixture.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StakingRootAlertAuthorityTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val account = ByteArray(32) { 1 }
    private val chainId = "public-local-relay-rendered"
    private val config = mock<ChainAsset> {
        on { this.chainId } doReturn this@StakingRootAlertAuthorityTest.chainId
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
    private var heldValidation: CompletableDeferred<Boolean>? = null
    private var validationCalls = 0
    private var holdValidationNonCancellable = false
    private var validationReturns = 0
    private val relay = mock<StakingRelayChainScenarioInteractor> {
        onBlocking { checkAccountRequiredValidation(anyOrNull()) } doSuspendableAnswer {
            validationCalls++
            val result = if (holdValidationNonCancellable) {
                withContext(NonCancellable) { heldValidation?.await() ?: true }
            } else { heldValidation?.await() ?: true }
            validationReturns++
            result
        }
        on { stakingStateFlow() } doReturn trackedRelayStates
        on { observeStashSummary(any()) } doReturn summaries
        on { observeValidatorSummary(any()) } doReturn flowOf(validatorSummary)
        onBlocking { observeNetworkInfoState() } doReturn relayNetwork.filterNotNull()
    }
    private val selected = mock<StakingSharedState> {
        on { selectionRevision } doReturn revision
        on { selectionItem } doReturn selectionFlow
        on { currentAssetFlow() } doReturn assets
        on { assetWithChain } doReturn this@StakingRootAlertAuthorityTest.assetWithChain
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
    private val lowerAlerts = MutableStateFlow<List<Alert>>(emptyList())
    private val alertSourceStates = java.util.Collections.synchronizedList(mutableListOf<StakingState>())
    private val alerts = mock<AlertsInteractor> {
        on { getAlertsFlow(any()) } doAnswer {
            alertSourceStates += it.getArgument<StakingState>(0)
            lowerAlerts
        }
    }
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

    private fun loadedAlerts(): List<AlertModel>? =
        (vm.alertsFlow.replayCache.lastOrNull() as? LoadingState.Loaded<List<AlertModel>>)?.data

    private suspend fun awaitCondition(condition: () -> Boolean) {
        withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                while (!condition()) {
                    dispatcher.scheduler.runCurrent()
                    delay(1)
                }
            }
        }
    }

    private suspend fun TestScope.publishAlert(alert: Alert, previous: AlertModel? = null): AlertModel {
        lowerAlerts.value = listOf(alert)
        settle()
        awaitCondition { loadedAlerts()?.singleOrNull()?.let { it !== previous } == true }
        return requireNotNull(loadedAlerts()).single()
    }

    private fun action(alert: AlertModel): () -> Unit = (alert.type as AlertModel.Type.CallToAction).action

    private fun captureValidatedAlert(alert: AlertModel): Job {
        val owner = requireNotNull(vm.viewModelScope.coroutineContext[Job])
        val previous = owner.children.toSet()
        action(alert)()
        // Main is a StandardTestDispatcher: capture the queued real child before
        // pumping it, because an unheld validation may complete during settle().
        return owner.children.single { it !in previous }
    }

    private suspend fun TestScope.startValidatedAlert(alert: AlertModel): Job {
        val request = captureValidatedAlert(alert)
        settle()
        awaitCondition { validationCalls == 1 }
        return request
    }

    private fun replaceWalletAndReturn() {
        WalletSelectionEpoch.beginChange()
        currentMeta = mock { on { id } doReturn 9L; on { chainAccounts } doReturn emptyMap(); on { substrateAccountId } doReturn account }
        WalletSelectionEpoch.endChange()
        WalletSelectionEpoch.beginChange()
        currentMeta = meta
        WalletSelectionEpoch.endChange()
    }

    @Test fun `healthy actual current change validators alert routes once`() = runTest(dispatcher) {
        start()
        val alert = publishAlert(Alert.ChangeValidators)
        action(alert)()
        verify(router, times(1)).openCurrentValidators()
    }

    @Test fun `healthy actual current set validators alert updates setup and routes`() = runTest(dispatcher) {
        start()
        val alert = publishAlert(Alert.SetValidators)
        action(alert)()
        verify(router, times(1)).openStartChangeValidators()
        verify(setup).set(any<SetupStakingProcess.SelectBlockProducersStep.Validators>())
    }

    @Test fun `queued actual alert job remains observable after immediate completion`() = runTest(dispatcher) {
        start()
        val alert = publishAlert(Alert.RedeemTokens(BigDecimal.ONE, asset.token))
        val owner = requireNotNull(vm.viewModelScope.coroutineContext[Job])
        val request = captureValidatedAlert(alert)
        assertFalse("The captured real Main child has not run before pumping", request.isCompleted)
        assertEquals(0, validationCalls)
        verifyNoInteractions(router)

        settle()
        awaitCondition { request.isCompleted }
        assertFalse("Completion removes the real child from its owner", owner.children.any { it === request })
        assertEquals(1, validationCalls)
        assertEquals(1, validationReturns)
        verify(router, times(1)).openRedeem(any())
    }

    @Test fun `healthy actual current redeem alert validates and routes`() = runTest(dispatcher) {
        start()
        val alert = publishAlert(Alert.RedeemTokens(BigDecimal.ONE, asset.token))
        val request = startValidatedAlert(alert)
        awaitCondition { request.isCompleted }
        assertEquals(1, validationCalls)
        verify(router, times(1)).openRedeem(any())
    }

    @Test fun `healthy actual current bond more alert validates and routes`() = runTest(dispatcher) {
        start()
        val alert = publishAlert(Alert.BondMoreTokens(BigDecimal.ONE, asset.token))
        val request = startValidatedAlert(alert)
        awaitCondition { request.isCompleted }
        assertEquals(1, validationCalls)
        verify(router, times(1)).openBondMore(any())
    }

    @Test fun `healthy fresh equal text alert after role replacement remains routable`() = runTest(dispatcher) {
        start()
        val old = publishAlert(Alert.ChangeValidators)
        stakingStates.value = StakingState.Stash.Validator(chain, account, account, account, mock())
        settle()
        awaitCondition { loadedAlerts()?.singleOrNull()?.let { it !== old } == true }
        val fresh = requireNotNull(loadedAlerts()).single()
        assertEquals(old.title, fresh.title)
        assertEquals(old.extraMessage, fresh.extraMessage)
        action(fresh)()
        verify(router, times(1)).openCurrentValidators()
    }

    @Test fun `old change validators alert cannot route after selecting pool`() = runTest(dispatcher) {
        start()
        val old = publishAlert(Alert.ChangeValidators)
        selectPool()
        action(old)()
        verifyNoInteractions(router)
    }

    @Test fun `old set validators alert cannot replace pool setup or route`() = runTest(dispatcher) {
        start()
        val old = publishAlert(Alert.SetValidators)
        selectPool()
        clearInvocations(setup)
        action(old)()
        verifyNoInteractions(router)
        verify(setup, never()).set(any<SetupStakingProcess.SelectBlockProducersStep.Validators>())
    }

    @Test fun `removed alert cannot route after empty current publication`() = runTest(dispatcher) {
        start()
        val old = publishAlert(Alert.ChangeValidators)
        lowerAlerts.value = emptyList()
        settle()
        awaitCondition { loadedAlerts()?.isEmpty() == true }
        action(old)()
        verifyNoInteractions(router)
    }

    @Test fun `old alert cannot revive after screen pause and resume`() = runTest(dispatcher) {
        start()
        val old = publishAlert(Alert.ChangeValidators)
        vm.onStakingScreenPaused()
        vm.onStakingScreenResumed()
        settle()
        action(old)()
        verifyNoInteractions(router)
    }

    @Test fun `old alert cannot revive after public wallet epoch round trip`() = runTest(dispatcher) {
        start()
        val old = publishAlert(Alert.ChangeValidators)
        replaceWalletAndReturn()
        settle()
        action(old)()
        verifyNoInteractions(router)
    }

    @Test fun `old alert cannot adopt new selection intent before preference stream catches up`() = runTest(dispatcher) {
        start()
        val old = publishAlert(Alert.ChangeValidators)
        revision.changed(StakingAssetSelection.Pool(chainId, config.id))
        settle()
        assertEquals(StakingType.RELAYCHAIN, selectionFlow.value.type)
        action(old)()
        verifyNoInteractions(router)
    }

    @Test fun `pending redeem alert validation cannot route after scenario replacement`() = runTest(dispatcher) {
        start()
        val alert = publishAlert(Alert.RedeemTokens(BigDecimal.ONE, asset.token))
        val validation = CompletableDeferred<Boolean>()
        heldValidation = validation
        val request = startValidatedAlert(alert)
        assertEquals(1, validationCalls)
        selectPool()
        validation.complete(true)
        awaitCondition { request.isCompleted }
        settle()
        verifyNoInteractions(router)
    }

    @Test fun `pending bond more alert validation cannot adopt returning wallet epoch`() = runTest(dispatcher) {
        start()
        val alert = publishAlert(Alert.BondMoreTokens(BigDecimal.ONE, asset.token))
        val validation = CompletableDeferred<Boolean>()
        heldValidation = validation
        val request = startValidatedAlert(alert)
        assertEquals(1, validationCalls)
        replaceWalletAndReturn()
        validation.complete(true)
        awaitCondition { request.isCompleted }
        settle()
        verifyNoInteractions(router)
    }

    @Test fun `healthy fresh equal text alert after scenario round trip remains routable`() = runTest(dispatcher) {
        start()
        val old = publishAlert(Alert.ChangeValidators)
        selectPool()
        select(selection)
        awaitCondition { loadedAlerts()?.singleOrNull()?.let { it !== old } == true }
        val fresh = requireNotNull(loadedAlerts()).single()
        assertEquals(old.title, fresh.title)
        assertEquals(old.extraMessage, fresh.extraMessage)
        action(fresh)()
        verify(router, times(1)).openCurrentValidators()
    }
    @Test fun `healthy held redeem validation routes after release in current context`() = runTest(dispatcher) {
        start()
        val alert = publishAlert(Alert.RedeemTokens(BigDecimal.ONE, asset.token))
        val validation = CompletableDeferred<Boolean>()
        heldValidation = validation
        val request = startValidatedAlert(alert)
        assertEquals(0, validationReturns)
        verifyNoInteractions(router)
        validation.complete(true)
        awaitCondition { request.isCompleted }
        assertEquals(1, validationReturns)
        verify(router, times(1)).openRedeem(any())
    }

    @Test fun `healthy held bond more validation routes after release in current context`() = runTest(dispatcher) {
        start()
        val alert = publishAlert(Alert.BondMoreTokens(BigDecimal.ONE, asset.token))
        val validation = CompletableDeferred<Boolean>()
        heldValidation = validation
        val request = startValidatedAlert(alert)
        assertEquals(0, validationReturns)
        verifyNoInteractions(router)
        validation.complete(true)
        awaitCondition { request.isCompleted }
        assertEquals(1, validationReturns)
        verify(router, times(1)).openBondMore(any())
    }

    @Test fun `noncooperative redeem validation cannot route after paused screen returns`() = runTest(dispatcher) {
        start()
        val alert = publishAlert(Alert.RedeemTokens(BigDecimal.ONE, asset.token))
        val validation = CompletableDeferred<Boolean>()
        heldValidation = validation
        holdValidationNonCancellable = true
        val request = startValidatedAlert(alert)
        assertEquals(0, validationReturns)
        vm.onStakingScreenPaused()
        vm.onStakingScreenResumed()
        validation.complete(true)
        awaitCondition { request.isCompleted }
        assertEquals("Lower noncooperative work really returned", 1, validationReturns)
        verifyNoInteractions(router)
    }

    @Test fun `healthy fresh alert after pause resumes current route`() = runTest(dispatcher) {
        start()
        publishAlert(Alert.ChangeValidators)
        vm.onStakingScreenPaused()
        lowerAlerts.value = emptyList()
        settle()
        awaitCondition { loadedAlerts()?.isEmpty() == true }
        vm.onStakingScreenResumed()
        val fresh = publishAlert(Alert.ChangeValidators)
        action(fresh)()
        verify(router, times(1)).openCurrentValidators()
    }

    @Test fun `healthy fresh observed state after wallet round trip publishes usable alert`() = runTest(dispatcher) {
        start()
        val old = publishAlert(Alert.ChangeValidators)
        replaceWalletAndReturn()
        stakingStates.value = StakingState.Stash.Validator(chain, account, account, account, mock())
        settle()
        awaitCondition { loadedAlerts()?.singleOrNull()?.let { it !== old } == true }
        action(requireNotNull(loadedAlerts()).single())()
        verify(router, times(1)).openCurrentValidators()
    }

    @Test fun `old role alert cannot route after fresh equal text publication`() = runTest(dispatcher) {
        start()
        val old = publishAlert(Alert.ChangeValidators)
        stakingStates.value = StakingState.Stash.Validator(chain, account, account, account, mock())
        settle()
        awaitCondition { loadedAlerts()?.singleOrNull()?.let { it !== old } == true }
        action(old)()
        verifyNoInteractions(router)
    }

    @Test fun `late old source alert publication cannot adopt newer selection intent`() = runTest(dispatcher) {
        start()
        val old = publishAlert(Alert.ChangeValidators)
        revision.changed(StakingAssetSelection.Pool(chainId, config.id))
        val late = publishAlert(Alert.SetValidators, old)
        assertEquals(StakingType.RELAYCHAIN, selectionFlow.value.type)
        action(late)()
        verifyNoInteractions(router)
    }

    @Test fun `late old source alert publication cannot adopt returning wallet epoch`() = runTest(dispatcher) {
        start()
        val old = publishAlert(Alert.ChangeValidators)
        replaceWalletAndReturn()
        val late = publishAlert(Alert.SetValidators, old)
        action(late)()
        verifyNoInteractions(router)
    }

    @Test fun `fresh state observed during wallet transition becomes usable after settle`() = runTest(dispatcher) {
        start()
        val old = publishAlert(Alert.ChangeValidators)
        val freshState = StakingState.Stash.Validator(chain, account, account, account, mock())
        WalletSelectionEpoch.beginChange()
        try {
            stakingStates.value = freshState
            settle()
            awaitCondition { freshState in observedRelayStates }
            // Actual public lower alert-source creation proves the scenario entered this state.
            awaitCondition { freshState in alertSourceStates }
        } finally { WalletSelectionEpoch.endChange() }
        settle()
        awaitCondition { loadedAlerts()?.singleOrNull()?.let { it !== old } == true }
        action(requireNotNull(loadedAlerts()).single())()
        verify(router, times(1)).openCurrentValidators()
    }

    @Test fun `new old-scenario state cannot acquire newer pool selection authority`() = runTest(dispatcher) {
        start()
        val old = publishAlert(Alert.ChangeValidators)
        revision.changed(StakingAssetSelection.Pool(chainId, config.id))
        stakingStates.value = StakingState.Stash.Validator(chain, account, account, account, mock())
        settle()
        awaitCondition { loadedAlerts()?.singleOrNull()?.let { it !== old } == true }
        assertEquals(StakingType.RELAYCHAIN, selectionFlow.value.type)
        action(requireNotNull(loadedAlerts()).single())()
        verifyNoInteractions(router)
    }

}
