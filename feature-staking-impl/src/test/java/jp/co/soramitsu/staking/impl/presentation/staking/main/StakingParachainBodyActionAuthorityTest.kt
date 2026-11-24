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
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.CollatorStakeInfoViewState
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.CollatorDetailsParcelModel
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
 * data edges are controlled. Callbacks retain only actual rendered root models and mirror the production options-sheet public calls.
 * No injected action metadata, private flow replacement, keys, RPC or native window claim.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StakingParachainBodyActionAuthorityTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val account = ByteArray(32) { 1 }
    private val collatorA = ByteArray(20) { 2 }
    private val collatorB = ByteArray(20) { 3 }
    private val chainId = "public-local-parachain-body-action"
    private val config = mock<ChainAsset> {
        on { this.chainId } doReturn this@StakingParachainBodyActionAuthorityTest.chainId
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
    private val candidate = CandidateInfo(
        BigInteger.valueOf(11), BigInteger.valueOf(2), BigInteger.valueOf(40), BigInteger.valueOf(3),
        BigInteger.ZERO, BigInteger.ZERO, CandidateCapacity.Partial, CandidateCapacity.Empty,
        "public fixture request", CandidateInfoStatus.ACTIVE
    )
    private suspend fun detailQuery(stage: String, id: ByteArray) {
        val key = stage + ":" + id.toHexString(false)
        val query = Query(key).also(queries::add)
        val gate = gates[key]
        try {
            if (gate?.nonCancellable == true) withContext(NonCancellable) { gate.release.await() }
            else gate?.release?.await()
            query.returned = true
        } finally { query.cancelled = !currentCoroutineContext().isActive }
    }
    private val para = mock<StakingParachainScenarioInteractor> {
        on { stakingStateFlow() } doReturn trackedStates
        onBlocking { observeNetworkInfoState() } doReturn emptyFlow()
        onBlocking { getBottomDelegations(any(), any()) } doReturn emptyMap()
        onBlocking { getIdentity(any()) } doSuspendableAnswer {
            detailQuery("identity", it.getArgument(0))
            null
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
        onBlocking { getApyFor(any()) } doSuspendableAnswer {
            detailQuery("apy", it.getArgument(0))
            BigDecimal("0.125")
        }
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

    private fun body(): CollatorStakeInfoViewState? =
        (vm.state.value?.stakingViewState as? StakingViewState.Parachain.Delegator)?.delegations?.singleOrNull()
    private fun bodyCollator(): ByteArray? = body()?.collatorId
    private fun details(): List<CollatorDetailsParcelModel> = mockingDetails(router).invocations
        .filter { it.method.name == "openCollatorDetails" }.map { it.arguments.single() as CollatorDetailsParcelModel }
    private fun balanceAction(model: CollatorStakeInfoViewState): () -> Unit = { vm.onStakingBalance(model) }
    private fun detailsAction(model: CollatorStakeInfoViewState): () -> Unit = { vm.openCollatorInfo(model) }

    private suspend fun TestScope.start(): CollatorStakeInfoViewState {
        vm = StakingViewModel(interactor, mock(), mock(), router, resources, ValidationExecutor(resources), update, selected,
            para, mock(), calculators, setup, mock(), StakingPoolSharedStateProvider(), stories, mock(), mock(), quick, mock())
        settle()
        awaitCondition { bodyCollator()?.contentEquals(collatorA) == true }
        assertTrue("Actual root renders the initial selected collator", bodyCollator()!!.contentEquals(collatorA))
        return requireNotNull(body())
    }

    private suspend fun TestScope.replaceRole(collator: ByteArray, owner: ByteArray = account) {
        val next = delegator(collator, owner)
        states.value = next
        settle()
        awaitCondition { next in observedStates && bodyCollator()?.contentEquals(collator) == true }
        assertTrue("Actual replacement role reached rendered root", bodyCollator()!!.contentEquals(collator))
    }

    private suspend fun TestScope.heldDetails(stage: String, nonCancellable: Boolean): Pair<CollatorStakeInfoViewState, Gate> {
        val model = start()
        val key = stage + ":" + collatorA.toHexString(false)
        val gate = Gate(nonCancellable).also { gates[key] = it }
        detailsAction(model)()
        settle()
        awaitCondition { queries.any { it.collator == key && !it.returned } }
        assertFalse("The actual details request reached the controlled lower query", gate.release.isCompleted)
        assertTrue("Held current request has not routed", details().isEmpty())
        return model to gate
    }

    private suspend fun TestScope.releaseDetails(stage: String, gate: Gate) {
        gate.release.complete(Unit)
        settle()
        val key = stage + ":" + collatorA.toHexString(false)
        awaitCondition { queries.any { it.collator == key && (it.returned || it.cancelled) } }
        settle()
    }

    @Test fun `healthy actual rendered delegation balance routes current collator`() = runTest(dispatcher) {
        balanceAction(start())()
        verify(router, times(1)).openStakingBalance(collatorA.toHexString(true))
    }

    @Test fun `retained balance callback cannot route old rendered delegation after replacement body`() = runTest(dispatcher) {
        val old = balanceAction(start())
        replaceRole(collatorB)
        old()
        verifyNoInteractions(router)
    }

    @Test fun `healthy replacement body balance callback routes fresh collator`() = runTest(dispatcher) {
        start()
        replaceRole(collatorB)
        balanceAction(requireNotNull(body()))()
        verify(router, times(1)).openStakingBalance(collatorB.toHexString(true))
    }

    @Test fun `same object role ABA cannot revive original rendered balance callback`() = runTest(dispatcher) {
        val originalRole = states.value
        val oldModel = start()
        val old = balanceAction(oldModel)
        replaceRole(collatorB)
        states.value = originalRole
        settle()
        awaitCondition { observedStates.lastOrNull() === originalRole && bodyCollator()?.contentEquals(collatorA) == true }
        assertNotSame("Fresh root publication exists before exercising the old model", oldModel, body())
        old()
        verifyNoInteractions(router)
    }

    @Test fun `wrong account rendered delegation cannot acquire selected wallet balance authority`() = runTest(dispatcher) {
        start()
        replaceRole(collatorB, ByteArray(32) { 9 })
        balanceAction(requireNotNull(body()))()
        verifyNoInteractions(router)
    }

    @Test fun `healthy actual rendered collator details preserve current payload`() = runTest(dispatcher) {
        detailsAction(start())()
        settle()
        awaitCondition { details().size == 1 }
        val actual = details().single()
        assertEquals(collatorA.toHexString(true), actual.accountIdHex)
        assertEquals(BigInteger.valueOf(11), actual.stake.selfBonded)
        assertEquals(2, actual.stake.delegations)
        assertEquals(BigInteger.valueOf(40), actual.stake.totalStake)
        assertEquals(BigInteger.valueOf(3), actual.stake.minBond)
        assertEquals(BigDecimal("0.125"), actual.stake.estimatedRewards)
        assertEquals("public fixture request", actual.request)
    }

    @Test fun `retained details callback cannot query or route old rendered delegation after replacement body`() = runTest(dispatcher) {
        val old = detailsAction(start())
        replaceRole(collatorB)
        old()
        settle()
        assertTrue("Obsolete model must not start identity or APY work", queries.isEmpty())
        verifyNoInteractions(router)
    }

    @Test fun `cooperative held identity cannot route after fresh replacement body`() = runTest(dispatcher) {
        val (_, gate) = heldDetails("identity", false)
        replaceRole(collatorB)
        assertFalse(gate.release.isCompleted)
        releaseDetails("identity", gate)
        verifyNoInteractions(router)
    }

    @Test fun `noncooperative held identity cannot route after fresh replacement body`() = runTest(dispatcher) {
        val (_, gate) = heldDetails("identity", true)
        replaceRole(collatorB)
        assertFalse(gate.release.isCompleted)
        releaseDetails("identity", gate)
        verifyNoInteractions(router)
    }

    @Test fun `noncooperative held APY cannot route after fresh replacement body`() = runTest(dispatcher) {
        val (_, gate) = heldDetails("apy", true)
        replaceRole(collatorB)
        assertFalse(gate.release.isCompleted)
        releaseDetails("apy", gate)
        verifyNoInteractions(router)
    }

    @Test fun `healthy held current identity and APY route only after both returns`() = runTest(dispatcher) {
        val (_, identity) = heldDetails("identity", true)
        val key = "apy:" + collatorA.toHexString(false)
        val apy = Gate(true).also { gates[key] = it }
        releaseDetails("identity", identity)
        awaitCondition { queries.any { it.collator == key && !it.returned } }
        assertTrue(details().isEmpty())
        releaseDetails("apy", apy)
        awaitCondition { details().size == 1 }
        assertEquals(collatorA.toHexString(true), details().single().accountIdHex)
        assertEquals(BigDecimal("0.125"), details().single().stake.estimatedRewards)
    }

    @Test fun `healthy None then fresh delegator details recover exact replacement`() = runTest(dispatcher) {
        start()
        states.value = StakingState.Parachain.None(chain, account)
        settle()
        awaitCondition { vm.state.value?.stakingViewState is StakingViewState.Parachain.Welcome }
        replaceRole(collatorB)
        detailsAction(requireNotNull(body()))()
        settle()
        awaitCondition { details().size == 1 }
        assertEquals(collatorB.toHexString(true), details().single().accountIdHex)
    }

    @Test fun `qualification pause resume cannot revive retained balance callback and fresh publication works`() = runTest(dispatcher) {
        val old = start()
        vm.onStakingScreenPaused()
        balanceAction(old)()
        verifyNoInteractions(router)
        vm.onStakingScreenResumed()
        settle()
        awaitCondition { body()?.let { it !== old } == true }
        balanceAction(old)()
        verifyNoInteractions(router)
        balanceAction(requireNotNull(body()))()
        verify(router, times(1)).openStakingBalance(collatorA.toHexString(true))
    }

    @Test fun `qualification invalid caller screen cannot start balance or details actions`() = runTest(dispatcher) {
        val current = start()
        vm.onStakingBalance(current) { false }
        vm.openCollatorInfo(current) { false }
        settle()
        verifyNoInteractions(router)
        assertTrue(queries.isEmpty())
        vm.openCollatorInfo(current) { true }
        settle()
        awaitCondition { details().size == 1 }
        assertEquals(collatorA.toHexString(true), details().single().accountIdHex)
    }

    @Test fun `qualification observed replacement retires callback before rendering debounce`() = runTest(dispatcher) {
        val emitted = java.util.concurrent.atomic.AtomicReference<StakingState>()
        whenever(para.stakingStateFlow()).doReturn(flow {
            trackedStates.collect { emit(it); emitted.set(it) }
        })
        val old = start()
        val next = delegator(collatorB)
        val before = testScheduler.currentTime
        states.value = next
        runCurrent()
        // Keep this test call synchronous so runTest cannot advance the render debounce
        // while the Default source collector observes the role. Drain only runnable Main work.
        runBlocking(Dispatchers.Default) {
            withTimeout(5_000) {
                while (emitted.get() !== next) { dispatcher.scheduler.runCurrent(); delay(1) }
            }
        }
        assertEquals("Observation wait must not advance virtual rendering time", before, testScheduler.currentTime)
        assertSame("No virtual render debounce has advanced", old, body())
        balanceAction(old)()
        detailsAction(old)()
        settle()
        verifyNoInteractions(router)
        assertTrue(queries.isEmpty())
        awaitCondition { bodyCollator()?.contentEquals(collatorB) == true }
        balanceAction(requireNotNull(body()))()
        verify(router, times(1)).openStakingBalance(collatorB.toHexString(true))
    }

    @Test fun `qualification observed replacement retires callback while fresh body query is held`() = runTest(dispatcher) {
        val old = start()
        val gate = Gate().also { gates["body-replacement"] = it }
        val requested = CompletableDeferred<Unit>()
        whenever(para.getIdentities(any())).doSuspendableAnswer {
            val ids = it.getArgument<List<ByteArray>>(0)
            if (ids.any { id -> id.contentEquals(collatorB) }) { requested.complete(Unit); gate.release.await() }
            emptyMap()
        }
        val next = delegator(collatorB)
        states.value = next
        settle()
        awaitCondition { observedStates.lastOrNull() === next && requested.isCompleted }
        assertFalse(gate.release.isCompleted)
        assertSame("Old rendered body remains while new lower data is held", old, body())
        balanceAction(old)()
        detailsAction(old)()
        settle()
        verifyNoInteractions(router)
        assertTrue(queries.isEmpty())
        gate.release.complete(Unit)
        settle()
        awaitCondition { bodyCollator()?.contentEquals(collatorB) == true }
        balanceAction(requireNotNull(body()))()
        verify(router, times(1)).openStakingBalance(collatorB.toHexString(true))
    }

    @Test fun `qualification same collator amount refresh retires older publication`() = runTest(dispatcher) {
        val old = start()
        vm.onQuickAmountInput(1.0)
        settle()
        awaitCondition { body()?.let { it !== old } == true }
        assertTrue(bodyCollator()!!.contentEquals(collatorA))
        val currentState = vm.state.value?.stakingViewState as StakingViewState.Parachain.Delegator
        assertEquals(BigDecimal("1.00000"), currentState.estimatedEarnings.amountInputViewState.tokenAmount)
        balanceAction(old)()
        verifyNoInteractions(router)
        balanceAction(requireNotNull(body()))()
        verify(router, times(1)).openStakingBalance(collatorA.toHexString(true))
    }

    @Test fun `qualification current details preserve every optional identity field`() = runTest(dispatcher) {
        val current = start()
        val identity = RootIdentity("Public Display", "Public Legal", "https://example.invalid", "public riot", "public@example.invalid", "public fingerprint", "public image", "public twitter")
        whenever(para.getIdentity(any())).doReturn(identity)
        detailsAction(current)()
        settle()
        awaitCondition { details().size == 1 }
        val actual = requireNotNull(details().single().identity)
        assertEquals(identity.display, actual.display)
        assertEquals(identity.legal, actual.legal)
        assertEquals(identity.web, actual.web)
        assertEquals(identity.riot, actual.riot)
        assertEquals(identity.email, actual.email)
        assertEquals(identity.pgpFingerprint, actual.pgpFingerprint)
        assertEquals(identity.image, actual.image)
        assertEquals(identity.twitter, actual.twitter)
    }

    @Test fun `qualification noncooperative details cannot return across pause resume and fresh action recovers`() = runTest(dispatcher) {
        val (old, gate) = heldDetails("identity", true)
        vm.onStakingScreenPaused()
        vm.onStakingScreenResumed()
        settle()
        awaitCondition { body()?.let { it !== old } == true }
        releaseDetails("identity", gate)
        verifyNoInteractions(router)
        assertFalse("Obsolete identity cannot start downstream APY", queries.any { it.collator.startsWith("apy:") })
        detailsAction(requireNotNull(body()))()
        settle()
        awaitCondition { details().size == 1 }
        assertEquals(collatorA.toHexString(true), details().single().accountIdHex)
    }

    @Test fun `qualification source metadata exception denies lease and fresh capture recovers`() = runTest(dispatcher) {
        start()
        whenever(interactor.getCurrentMetaAccount()).doSuspendableAnswer { throw java.io.IOException("Controlled public source metadata unavailable") }
        val denied = vm.captureAlertSource(states.value, StakingType.PARACHAIN)
        assertFalse(denied())
        doReturn(meta).whenever(interactor).getCurrentMetaAccount()
        val current = vm.captureAlertSource(states.value, StakingType.PARACHAIN)
        assertTrue(current())
    }

    @Test fun `qualification source admission preserves coroutine cancellation`() = runTest(dispatcher) {
        start()
        whenever(interactor.getCurrentMetaAccount()).doSuspendableAnswer { throw CancellationException("Controlled source cancellation") }
        try {
            vm.captureAlertSource(states.value, StakingType.PARACHAIN)
            fail("Cancellation must propagate rather than become a denied metadata lease")
        } catch (error: CancellationException) {
            assertEquals("Controlled source cancellation", error.message)
        }
    }

    @Test fun `qualification actual root denies failed metadata body and recovers after fresh role observation`() = runTest(dispatcher) {
        val original = start()
        val unavailable = java.util.concurrent.atomic.AtomicBoolean(true)
        val rejected = java.util.concurrent.atomic.AtomicInteger()
        // Failure persists for the entire B publication phase, so another public metadata caller
        // cannot consume a one-shot failure intended for source admission.
        whenever(interactor.getCurrentMetaAccount()).doSuspendableAnswer {
            if (unavailable.get()) {
                rejected.incrementAndGet()
                throw java.io.IOException("Controlled metadata unavailable throughout B admission")
            }
            meta
        }
        replaceRole(collatorB)
        val denied = requireNotNull(body())
        assertNotSame(original, denied)
        assertTrue("Actual B source admission encountered unavailable metadata", rejected.get() > 0)
        unavailable.set(false)
        balanceAction(denied)()
        detailsAction(denied)()
        settle()
        verifyNoInteractions(router)
        assertTrue("Denied rendered source cannot start details work", queries.isEmpty())
        replaceRole(collatorA)
        val recovered = requireNotNull(body())
        assertNotSame(original, recovered)
        assertNotSame(denied, recovered)
        detailsAction(recovered)()
        settle()
        awaitCondition { details().size == 1 }
        assertEquals(collatorA.toHexString(true), details().single().accountIdHex)
    }
}
