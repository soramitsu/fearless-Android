package jp.co.soramitsu.staking.impl.presentation.staking.main

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import jp.co.soramitsu.staking.api.data.*
import jp.co.soramitsu.staking.api.domain.model.*
import jp.co.soramitsu.staking.impl.data.repository.datasource.ParachainStakingStoriesDataSourceImpl
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.rewards.PeriodReturns
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.staking.impl.domain.rewards.SubqueryRewardCalculator
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.*
import jp.co.soramitsu.staking.impl.presentation.staking.alerts.model.AlertModel
import jp.co.soramitsu.staking.impl.scenarios.parachain.StakingParachainScenarioInteractor
import jp.co.soramitsu.wallet.impl.domain.interfaces.QuickInputsUseCase
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.mockito.kotlin.*
import jp.co.soramitsu.core.models.Asset as ChainAsset

/** Actual root constructs its real StakingScenario and parachain view model. Only public lower
 * data edges are controlled. No private flow replacement, keys, RPC or native window claim.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StakingParachainAlertRoleAuthorityTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val account = ByteArray(32) { 1 }
    private val collatorA = ByteArray(20) { 2 }
    private val collatorB = ByteArray(20) { 3 }
    private val chainId = "public-local-parachain-alert-role"
    private val config = mock<ChainAsset> {
        on { this.chainId } doReturn this@StakingParachainAlertRoleAuthorityTest.chainId
        on { id } doReturn "native"
        on { symbol } doReturn "LOCAL"
        on { chainName } doReturn "Local public fixture"
        on { iconUrl } doReturn ""
        on { precision } doReturn 2
        on { staking } doReturn ChainAsset.StakingType.PARACHAIN
    }
    private val chain = mock<Chain> {
        on { id } doReturn chainId
        on { ecosystem } doReturn Ecosystem.Substrate
        on { addressPrefix } doReturn 42
    }
    private val asset = Asset.createEmpty(config, 7, account, minSupportedVersion = null)
    private val assets = MutableStateFlow(asset)
    private val metadata = MutableStateFlow(SingleAssetSharedState.AssetWithChain(chain, config))
    private val selection = StakingAssetSelection.ParachainStaking(chainId, config.id)
    private val revision = StakingSelectionRevision().apply { changed(selection) }
    private val selections = MutableStateFlow<StakingAssetSelection>(selection)
    private val selected = mock<StakingSharedState> {
        on { selectionRevision } doReturn revision
        on { selectionItem } doReturn selections
        on { settledSelection } doReturn selections
        on { currentAssetFlow() } doReturn assets
        on { assetWithChain } doReturn metadata
    }
    private val meta = mock<MetaAccount> {
        on { id } doReturn 7L
        on { chainAccounts } doReturn emptyMap()
        on { substrateAccountId } doReturn account
    }
    private val states = MutableStateFlow<StakingState>(delegator(collatorA))
    private val observedStates = mutableListOf<StakingState>()
    private val trackedStates = flow { states.collect { observedStates += it; emit(it) } }
    private class Gate(val nonCancellable: Boolean = false) {
        val release = CompletableDeferred<Unit>()
    }
    private class Query(val collator: String) {
        var returned = false
        var cancelled = false
    }
    private val gates = mutableMapOf<String, Gate>()
    private val queries = mutableListOf<Query>()
    private val candidate = mock<CandidateInfo> {
        on { status } doReturn CandidateInfoStatus.ACTIVE
    }
    private val para = mock<StakingParachainScenarioInteractor> {
        on { stakingStateFlow() } doReturn trackedStates
        onBlocking { observeNetworkInfoState() } doReturn emptyFlow()
        onBlocking { getBottomDelegations(any(), any()) } doSuspendableAnswer {
            val ids = it.getArgument<List<ByteArray>>(1)
            val id = ids.single().toHexString(false)
            val query = Query(id).also(queries::add)
            val gate = gates[id]
            try {
                if (gate?.nonCancellable == true) withContext(NonCancellable) { gate.release.await() }
                else gate?.release?.await()
                query.returned = true
                emptyMap()
            } finally { query.cancelled = !currentCoroutineContext().isActive }
        }
        onBlocking { getIdentities(any()) } doReturn emptyMap()
        onBlocking { getCandidateInfos(any(), any()) } doAnswer {
            it.getArgument<List<ByteArray>>(1).associate { id -> id.toHexString(false) to candidate }
        }
        onBlocking { getCollatorIdsWithReadyToUnlockingTokens(any(), any()) } doAnswer { it.getArgument<List<ByteArray>>(0) }
        on { hoursInRound } doReturn mapOf(chainId to 2)
    }
    private val interactor = mock<StakingInteractor> {
        on { currentAssetFlow() } doReturn assets
        on { selectionStateFlow() } doReturn emptyFlow()
        on { selectedChainFlow() } doReturn flowOf(chain)
        on { selectedAccountProjectionFlow() } doReturn emptyFlow()
        onBlocking { getCurrentMetaAccount() } doReturn meta
        onBlocking { currentBlockNumber() } doReturn BigInteger.TEN
    }
    private val resources = mock<ResourceManager> {
        on { getString(any()) } doAnswer { "Public-${it.getArgument<Int>(0)}" }
        on { getString(any(), anyVararg()) } doAnswer { "Public-${it.getArgument<Int>(0)}" }
        on { getQuantityString(any(), any(), anyVararg()) } doReturn "Public time"
    }
    private val calculator = mock<SubqueryRewardCalculator> {
        onBlocking { calculateReturns(any<BigDecimal>(), any(), any(), any()) } doReturn PeriodReturns(BigDecimal.ONE, BigDecimal.ONE)
        onBlocking { getApy(any()) } doReturn emptyMap()
    }
    private val calculators = mock<RewardCalculatorFactory> { on { createSubquery() } doReturn calculator }
    private val setup = mock<SetupStakingSharedState> {
        on { setupStakingProcess } doReturn MutableStateFlow<SetupStakingProcess>(SetupStakingProcess.Initial(StakingType.PARACHAIN))
    }
    private val stories = mock<ParachainStakingStoriesDataSourceImpl> { on { getStoriesFlow() } doReturn emptyFlow() }
    private val update = mock<UpdateSystem> { on { start() } doReturn emptyFlow() }
    private val quick = mock<QuickInputsUseCase> {
        onBlocking { calculateStakingQuickInputs(any(), any(), any(), any()) } doReturn mapOf(1.0 to BigDecimal.ONE)
    }
    private val router = mock<StakingRouter>()
    private lateinit var vm: StakingViewModel

    @Before fun setupMain() {
        Dispatchers.setMain(dispatcher)
        runTest(dispatcher) {
            whenever(para.getCurrentRound(any())).doSuspendableAnswer {
                coroutineScope {
                    val result = CompletableDeferred<Result<Round>>()
                    launch { yield(); result.complete(Result.success(Round(BigInteger.ONE, BigInteger.ZERO, BigInteger.valueOf(100)))) }
                    result.await()
                }
            }
        }
    }

    @After fun teardown() {
        gates.values.forEach { it.release.complete(Unit) }
        if (::vm.isInitialized) {
            val owner = vm.viewModelScope.coroutineContext[Job]
            owner?.cancel()
            runBlocking(Dispatchers.Default) {
                withTimeout(5_000) {
                    while (owner?.isCompleted == false) { dispatcher.scheduler.runCurrent(); delay(1) }
                    owner?.join()
                }
            }
        }
        Dispatchers.resetMain()
    }

    private fun delegator(collator: ByteArray, owner: ByteArray = account) = StakingState.Parachain.Delegator(
        chain, owner, listOf(CollatorDelegation(collator, BigInteger.TEN, BigInteger.ZERO, DelegatorStateStatus.ACTIVE)), BigDecimal.ONE
    )

    private fun TestScope.settle() { runCurrent(); advanceTimeBy(51); runCurrent() }

    private suspend fun awaitCondition(condition: () -> Boolean) {
        withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                while (!condition()) { dispatcher.scheduler.runCurrent(); delay(1) }
            }
        }
    }

    private fun loadedAlerts(): List<AlertModel>? = (vm.alertsFlow.replayCache.lastOrNull() as? LoadingState.Loaded<List<AlertModel>>)?.data
    private fun action(alert: AlertModel) = (alert.type as AlertModel.Type.CallToAction).action
    private fun bodyCollator(): ByteArray? = (vm.state.value?.stakingViewState as? StakingViewState.Parachain.Delegator)
        ?.delegations?.singleOrNull()?.collatorId

    private suspend fun TestScope.start(waitForAlert: Boolean = true): AlertModel? {
        vm = StakingViewModel(interactor, mock(), mock(), router, resources, ValidationExecutor(resources), update, selected,
            para, mock(), calculators, setup, mock(), StakingPoolSharedStateProvider(), stories, mock(), mock(), quick, mock())
        settle()
        awaitCondition { bodyCollator()?.contentEquals(collatorA) == true }
        assertTrue("Actual root renders the initial selected collator", bodyCollator()!!.contentEquals(collatorA))
        if (!waitForAlert) return null
        awaitCondition { loadedAlerts()?.size == 1 }
        return loadedAlerts()!!.single()
    }

    private suspend fun TestScope.replaceRole(collator: ByteArray, owner: ByteArray = account) {
        val next = delegator(collator, owner)
        states.value = next
        settle()
        awaitCondition { next in observedStates && bodyCollator()?.contentEquals(collator) == true }
        assertTrue("Actual replacement role reached rendered root", bodyCollator()!!.contentEquals(collator))
    }

    @Test fun `healthy actual parachain ready alert routes its current collator`() = runTest(dispatcher) {
        action(requireNotNull(start()))()
        verify(router, times(1)).openStakingBalance(collatorA.toHexString(true))
    }

    @Test fun `retained alert cannot route old collator while replacement query is held`() = runTest(dispatcher) {
        val old = requireNotNull(start())
        gates[collatorB.toHexString(false)] = Gate()
        replaceRole(collatorB)
        awaitCondition { queries.any { it.collator == collatorB.toHexString(false) && !it.returned } }
        action(old)()
        verifyNoInteractions(router)
    }

    @Test fun `healthy equal text replacement alert routes new collator after held query returns`() = runTest(dispatcher) {
        val old = requireNotNull(start())
        val gate = Gate().also { gates[collatorB.toHexString(false)] = it }
        replaceRole(collatorB)
        awaitCondition { queries.any { it.collator == collatorB.toHexString(false) && !it.returned } }
        gate.release.complete(Unit)
        settle()
        awaitCondition { loadedAlerts()?.singleOrNull()?.let { it !== old } == true }
        val fresh = loadedAlerts()!!.single()
        assertEquals(old.title, fresh.title)
        assertEquals(old.extraMessage, fresh.extraMessage)
        action(fresh)()
        verify(router, times(1)).openStakingBalance(collatorB.toHexString(true))
    }

    @Test fun `late old calculation cannot publish actionable old collator after replacement body`() = runTest(dispatcher) {
        val oldGate = Gate().also { gates[collatorA.toHexString(false)] = it }
        gates[collatorB.toHexString(false)] = Gate()
        start(waitForAlert = false)
        awaitCondition { queries.any { it.collator == collatorA.toHexString(false) && !it.returned } }
        replaceRole(collatorB)
        oldGate.release.complete(Unit)
        settle()
        awaitCondition { queries.any { it.collator == collatorB.toHexString(false) } }
        loadedAlerts()?.forEach { action(it)() }
        verifyNoInteractions(router)
    }

    @Test fun `noncooperative old calculation cannot publish actionable old collator after return`() = runTest(dispatcher) {
        val oldGate = Gate(nonCancellable = true).also { gates[collatorA.toHexString(false)] = it }
        gates[collatorB.toHexString(false)] = Gate()
        start(waitForAlert = false)
        awaitCondition { queries.any { it.collator == collatorA.toHexString(false) && !it.returned } }
        replaceRole(collatorB)
        oldGate.release.complete(Unit)
        settle()
        awaitCondition { queries.any { it.collator == collatorB.toHexString(false) } }
        loadedAlerts()?.forEach { action(it)() }
        verifyNoInteractions(router)
    }

    @Test fun `retained alert retires before noncooperative preceding calculation returns`() = runTest(dispatcher) {
        val old = requireNotNull(start())
        val oldGate = Gate(nonCancellable = true).also { gates[collatorA.toHexString(false)] = it }
        val before = queries.size
        states.value = delegator(collatorA)
        settle()
        awaitCondition { queries.size > before && !queries.last().returned }
        replaceRole(collatorB)
        assertFalse("Prior query is still held when old action is exercised", oldGate.release.isCompleted)
        action(old)()
        verifyNoInteractions(router)
    }

    @Test fun `healthy noncooperative current query becomes actionable after release`() = runTest(dispatcher) {
        val gate = Gate(nonCancellable = true).also { gates[collatorA.toHexString(false)] = it }
        start(waitForAlert = false)
        awaitCondition { queries.any { !it.returned } }
        assertTrue(loadedAlerts().isNullOrEmpty())
        gate.release.complete(Unit)
        settle()
        awaitCondition { loadedAlerts()?.size == 1 }
        action(loadedAlerts()!!.single())()
        verify(router, times(1)).openStakingBalance(collatorA.toHexString(true))
    }

    @Test fun `healthy None role clears alerts and fresh delegator recovers`() = runTest(dispatcher) {
        val old = requireNotNull(start())
        states.value = StakingState.Parachain.None(chain, account)
        settle()
        awaitCondition { loadedAlerts()?.isEmpty() == true && vm.state.value?.stakingViewState is StakingViewState.Parachain.Welcome }
        action(old)()
        verifyNoInteractions(router)
        replaceRole(collatorB)
        awaitCondition { loadedAlerts()?.size == 1 }
        action(loadedAlerts()!!.single())()
        verify(router, times(1)).openStakingBalance(collatorB.toHexString(true))
    }

    @Test fun `wrong account parachain role cannot acquire selected wallet authority`() = runTest(dispatcher) {
        val old = requireNotNull(start())
        replaceRole(collatorB, ByteArray(32) { 9 })
        awaitCondition { loadedAlerts()?.singleOrNull()?.let { it !== old } == true }
        action(loadedAlerts()!!.single())()
        verifyNoInteractions(router)
    }

    @Test fun `qualification cooperative cancellation admits fresh role before old gate release`() = runTest(dispatcher) {
        val gate = Gate().also { gates[collatorA.toHexString(false)] = it }
        start(waitForAlert = false)
        awaitCondition { queries.any { it.collator == collatorA.toHexString(false) && !it.returned } }
        replaceRole(collatorB)
        awaitCondition {
            queries.any { it.collator == collatorA.toHexString(false) && it.cancelled } &&
                queries.any { it.collator == collatorB.toHexString(false) && it.returned } && loadedAlerts()?.size == 1
        }
        assertFalse("The obsolete query gate was never released", gate.release.isCompleted)
        action(loadedAlerts()!!.single())()
        verify(router, times(1)).openStakingBalance(collatorB.toHexString(true))
    }

    @Test fun `qualification queued same object ABA cannot revive an earlier role callback`() = runTest(dispatcher) {
        val originalRole = states.value
        val old = requireNotNull(start())
        val middleGate = Gate(nonCancellable = true).also { gates[collatorB.toHexString(false)] = it }
        replaceRole(collatorB)
        awaitCondition { queries.any { it.collator == collatorB.toHexString(false) && !it.returned } }
        val currentGate = Gate().also { gates[collatorA.toHexString(false)] = it }
        states.value = originalRole
        settle()
        awaitCondition { observedStates.lastOrNull() === originalRole && bodyCollator()?.contentEquals(collatorA) == true }
        assertFalse("The middle calculation still holds cancellation while original role object returns", middleGate.release.isCompleted)
        action(old)()
        verifyNoInteractions(router)
        middleGate.release.complete(Unit)
        settle()
        awaitCondition { queries.lastOrNull()?.let { it.collator == collatorA.toHexString(false) && !it.returned } == true }
        action(old)()
        verifyNoInteractions(router)
        currentGate.release.complete(Unit)
        settle()
        awaitCondition { loadedAlerts()?.singleOrNull()?.let { it !== old } == true }
        action(loadedAlerts()!!.single())()
        verify(router, times(1)).openStakingBalance(collatorA.toHexString(true))
    }
}
