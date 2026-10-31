package jp.co.soramitsu.staking.impl.presentation.staking.main

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.staking.api.data.StakingAssetSelection
import jp.co.soramitsu.staking.api.data.StakingSelectionRevision
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.data.StakingType
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios.BaseStakingViewModel
import jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios.StakingRelaychainScenarioViewModel
import jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios.StakingScenarioViewModel
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*
import java.io.IOException

/** Actual root, relay scenario and validation executor; only local metadata/boundary dependencies are mocked. */
@OptIn(ExperimentalCoroutinesApi::class)
class StakingNonPoolActionLifetimeTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val revision = StakingSelectionRevision()
    private val actionAsset = MutableStateFlow(makeAsset(jp.co.soramitsu.core.models.Asset.StakingType.RELAYCHAIN))
    private fun makeAsset(type: jp.co.soramitsu.core.models.Asset.StakingType): jp.co.soramitsu.wallet.impl.domain.model.Asset {
        val config = mock<jp.co.soramitsu.core.models.Asset> {
            on { chainId } doReturn "public-local-relay"
            on { id } doReturn "native"
            on { symbol } doReturn "LOCAL"
            on { precision } doReturn 10
            on { staking } doReturn type
        }
        return jp.co.soramitsu.wallet.impl.domain.model.Asset.createEmpty(config, 7, ByteArray(32) { 1 }, minSupportedVersion = null)
    }
    private val meta = mock<jp.co.soramitsu.account.api.domain.model.MetaAccount> {
        on { id } doReturn 7L
        on { chainAccounts } doReturn emptyMap()
        on { substrateAccountId } doReturn ByteArray(32) { 1 }
    }
    private val chainMetadata = actionAsset.map { asset ->
        val chain = mock<jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain> {
            on { id } doReturn "public-local-relay"
            on { ecosystem } doReturn jp.co.soramitsu.core.models.Ecosystem.Substrate
            on { addressPrefix } doReturn 42
        }
        jp.co.soramitsu.runtime.state.SingleAssetSharedState.AssetWithChain(chain, asset.token.configuration)
    }
    private val selected = mock<StakingSharedState> {
        on { selectionRevision } doReturn revision
        on { assetWithChain } doReturn chainMetadata
        on { selectionItem } doReturn emptyFlow()
        on { settledSelection } doReturn emptyFlow()
        on { currentAssetFlow() } doReturn actionAsset
        onBlocking { chainId() } doReturn "public-local-relay"
    }
    private val interactor = mock<StakingInteractor> {
        onBlocking { getCurrentMetaAccount() } doReturn meta
        on { currentAssetFlow() } doReturn actionAsset
        on { selectionStateFlow() } doReturn emptyFlow()
        on { selectedChainFlow() } doReturn emptyFlow()
        on { selectedAccountProjectionFlow() } doReturn emptyFlow()
    }
    private val resources = mock<ResourceManager> {
        on { getString(any()) } doAnswer { "text-${it.getArgument<Int>(0)}" }
        on { getString(any(), anyVararg()) } doAnswer { "text-${it.getArgument<Int>(0)}" }
    }
    private val router = mock<StakingRouter>()
    private val setupProcess = MutableStateFlow<SetupStakingProcess>(SetupStakingProcess.Initial(StakingType.RELAYCHAIN))
    private val setupState = mock<SetupStakingSharedState> { on { setupStakingProcess } doReturn setupProcess }
    private val executor = ValidationExecutor(resources)
    private val relay = mock<StakingRelayChainScenarioInteractor> { on { stakingStateFlow() } doReturn emptyFlow() }
    private val updateSystem = mock<UpdateSystem> { on { start() } doReturn emptyFlow() }
    private val entered = CompletableDeferred<Unit>()
    private val result = CompletableDeferred<Boolean>()
    private val scenarioScopes = mutableListOf<CoroutineScope>()
    private var visible = true
    private lateinit var vm: StakingViewModel
    private lateinit var operation: Job

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() {
        result.cancel()
        if (::vm.isInitialized) vm.viewModelScope.cancel()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test fun `current relay validation may navigate once with reviewed amount`() = runTest(dispatcher) {
        startHeld(); finish(false)
        verify(router).openSetupStaking()
        verify(setupState).set(check { assertTrue(it is SetupStakingProcess.SetupStep.Stash); assertEquals(java.math.BigDecimal.TEN, (it as SetupStakingProcess.SetupStep.Stash).amount) })
        assertNull(executor.validationFailureEvent.value)
    }

    @Test fun `current relay rejection displays validation without setup mutation`() = runTest(dispatcher) {
        startHeld(); finish(true)
        assertNotNull(executor.validationFailureEvent.value)
        verify(router, never()).openSetupStaking(); verify(setupState, never()).set(any())
    }

    @Test fun `current relay lookup failure reports error without navigation`() = runTest(dispatcher) {
        startHeld(); fail(IOException("Local validation unavailable"))
        assertEquals("Local validation unavailable", vm.errorLiveData.value?.peekContent())
        verify(router, never()).openSetupStaking(); verify(setupState, never()).set(any())
    }

    @Test fun `held relay validation cannot navigate after screen pause`() = runTest(dispatcher) {
        startHeld(); vm.onStakingScreenPaused(); finish(false); assertNoSetup()
    }

    @Test fun `screen return cannot revive earlier relay validation`() = runTest(dispatcher) {
        startHeld(); vm.onStakingScreenPaused(); vm.onStakingScreenResumed(); finish(false); assertNoSetup()
    }

    @Test fun `wallet switch cannot authorize earlier relay validation`() = runTest(dispatcher) {
        startHeld(); WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange(); finish(false); assertNoSetup()
    }

    @Test fun `selection intent cannot authorize earlier relay validation`() = runTest(dispatcher) {
        startHeld(); revision.changed(StakingAssetSelection.Pool("another-chain", "native")); finish(false); assertNoSetup()
    }

    @Test fun `departed destination cannot authorize earlier relay validation`() = runTest(dispatcher) {
        startHeld(); visible = false; finish(false); assertNoSetup()
    }

    @Test fun `held relay rejection cannot publish after pause`() = runTest(dispatcher) {
        startHeld(); vm.onStakingScreenPaused(); finish(true)
        assertNull(executor.validationFailureEvent.value); assertNoSetup()
    }

    @Test fun `held relay rejection cannot publish after wallet switch`() = runTest(dispatcher) {
        startHeld(); WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange(); finish(true)
        assertNull(executor.validationFailureEvent.value); assertNoSetup()
    }

    @Test fun `held relay lookup error cannot publish after pause`() = runTest(dispatcher) {
        startHeld(); vm.onStakingScreenPaused(); fail(IOException("Obsolete validation"))
        assertNull(vm.errorLiveData.value); assertNoSetup()
    }

    @Test fun `held relay lookup error cannot publish after wallet switch`() = runTest(dispatcher) {
        startHeld(); WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange(); fail(IOException("Obsolete validation"))
        assertNull(vm.errorLiveData.value); assertNoSetup()
    }

    @Test fun `relay lookup cancellation is not an ordinary displayed error`() = runTest(dispatcher) {
        startHeld(); fail(CancellationException("Cancelled public lookup"))
        assertNull(vm.errorLiveData.value); assertNoSetup()
    }

    @Test fun `setup observer changing wallet prevents final relay route`() = runTest(dispatcher) {
        startHeld()
        doAnswer { WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange(); Unit }.whenever(setupState).set(any())
        finish(false); verify(setupState).set(any()); verify(router, never()).openSetupStaking()
    }

    @Test fun `setup observer changing selection prevents final relay route`() = runTest(dispatcher) {
        startHeld()
        doAnswer { revision.changed(StakingAssetSelection.Pool("another-chain", "native")); Unit }.whenever(setupState).set(any())
        finish(false); verify(setupState).set(any()); verify(router, never()).openSetupStaking()
    }

    @Test fun `setup observer pause and resume cannot revive original relay route`() = runTest(dispatcher) {
        startHeld()
        doAnswer { vm.onStakingScreenPaused(); vm.onStakingScreenResumed(); Unit }.whenever(setupState).set(any())
        finish(false); verify(setupState).set(any()); verify(router, never()).openSetupStaking()
    }

    @Test fun `current retained validation callback can complete its owned action`() = runTest(dispatcher) {
        startHeld(); finish(true)
        executor.validationFailureEvent.value!!.peekContent().confirmWarning(); runCurrent()
        verify(setupState).set(any()); verify(router).openSetupStaking()
    }

    @Test fun `retained validation callback cannot authorize cleared ViewModel`() = runTest(dispatcher) {
        startHeld(); finish(true)
        val callback = executor.validationFailureEvent.value!!.peekContent().confirmWarning
        androidx.lifecycle.ViewModelStore().also { it.put("staking", vm); it.clear() }
        callback(); runCurrent(); assertNoSetup()
    }

    @Test fun `cleared ViewModel cannot complete held noncooperative validation`() = runTest(dispatcher) {
        startHeld()
        androidx.lifecycle.ViewModelStore().also { it.put("staking", vm); it.clear() }
        finish(false); assertNoSetup(); assertNull(vm.errorLiveData.value)
    }

    @Test fun `actual parachain setup preserves reviewed amount and routes once`() = runTest(dispatcher) {
        createParachain(); vm.startNonPoolStakingClick { visible }; runCurrent()
        verify(setupState).set(check {
            assertTrue(it is SetupStakingProcess.SetupStep.Parachain)
            assertEquals(java.math.BigDecimal.TEN, (it as SetupStakingProcess.SetupStep.Parachain).amount)
        })
        verify(router).openSetupStaking()
    }

    @Test fun `queued parachain action cannot begin after pause`() = runTest(dispatcher) {
        createParachain(); vm.startNonPoolStakingClick { visible }; vm.onStakingScreenPaused(); runCurrent()
        assertNoSetup()
    }

    @Test fun `actual parachain scenario rejects obsolete direct ownership`() = runTest(dispatcher) {
        val scenario = createParachain(); var delivered = false
        scenario.startStaking(actionAsset.value, { false }) { delivered = true }; runCurrent()
        assertFalse(delivered); assertNoSetup()
    }

    @Test fun `parachain setup observer clearing owner prevents final route`() = runTest(dispatcher) {
        createParachain()
        doAnswer { androidx.lifecycle.ViewModelStore().also { it.put("staking", vm); it.clear() }; Unit }
            .whenever(setupState).set(any())
        vm.startNonPoolStakingClick { visible }; runCurrent()
        verify(setupState).set(any()); verify(router, never()).openSetupStaking()
    }

    private fun TestScope.createParachain(): jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios.StakingParachainScenarioViewModel {
        actionAsset.value = makeAsset(jp.co.soramitsu.core.models.Asset.StakingType.PARACHAIN)
        vm = StakingViewModel(interactor, mock(), mock(), router, resources, executor, updateSystem, selected,
            mock(), relay, mock(), setupState, mock(), StakingPoolSharedStateProvider(), mock(), mock(), mock(), mock(), mock())
        val parachain = mock<jp.co.soramitsu.staking.impl.scenarios.parachain.StakingParachainScenarioInteractor> {
            on { stakingStateFlow() } doReturn emptyFlow()
        }
        val scenario = jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios.StakingParachainScenarioViewModel(
            interactor, parachain, resources, vm, mock(), mock(), mock(), setupState, router)
        StakingViewModel::class.java.getDeclaredField("scenarioViewModelFlow").apply { isAccessible = true }
            .set(vm, MutableStateFlow<StakingScenarioViewModel>(scenario))
        runCurrent(); return scenario
    }

    private suspend fun TestScope.startHeld() {
        whenever(relay.maxNumberOfStakesIsReached(any())).doSuspendableAnswer {
            entered.complete(Unit)
            // Preserve a late noncooperative result to exercise ownership after the await.
            withContext(NonCancellable) { result.await() }
        }
        vm = StakingViewModel(interactor, mock(), mock(), router, resources, executor, updateSystem, selected,
            mock(), relay, mock(), setupState, mock(), StakingPoolSharedStateProvider(), mock(), mock(), mock(), mock(), mock())
        val base = object : BaseStakingViewModel by vm {
            override val stakingStateScope: CoroutineScope
                get() = vm.stakingStateScope.also { scenarioScopes += it }
        }
        val scenario = StakingRelaychainScenarioViewModel(interactor, relay, resources, base, mock(), mock(), mock(),
            selected, mock(), setupState, router, executor, mock())
        StakingViewModel::class.java.getDeclaredField("scenarioViewModelFlow").apply { isAccessible = true }
            .set(vm, MutableStateFlow<StakingScenarioViewModel>(scenario))
        runCurrent()
        val before = vm.viewModelScope.coroutineContext[Job]!!.children.toSet()
        vm.startNonPoolStakingClick { visible }; runCurrent()
        withContext(Dispatchers.Default) { withTimeout(5_000) { entered.await() } }
        operation = vm.viewModelScope.coroutineContext[Job]!!.children.single { it !in before }
        assertTrue(operation.isActive)
    }

    private suspend fun TestScope.finish(rejected: Boolean) { result.complete(rejected); operation.join(); runCurrent() }
    private suspend fun TestScope.fail(error: Throwable) { result.completeExceptionally(error); operation.join(); runCurrent() }
    private fun assertNoSetup() { verify(router, never()).openSetupStaking(); verify(setupState, never()).set(any()) }
}
