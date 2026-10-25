package jp.co.soramitsu.staking.impl.presentation.staking.main

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
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
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolState
import jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios.StakingScenarioViewModel
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import jp.co.soramitsu.core.models.Asset as ChainAsset

/** Actual main ViewModel preparation/publication; only the unrelated scenario source is supplied locally. */
@OptIn(ExperimentalCoroutinesApi::class)
class StakingPoolPreparationOwnershipTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val account = ByteArray(32) { (it + 1).toByte() }
    private val chainAsset = mock<ChainAsset> {
        on { chainId } doReturn "local-pool-chain"
        on { id } doReturn "native"
        on { symbol } doReturn "LOCAL"
        on { precision } doReturn 10
    }
    private val chain = mock<Chain> {
        on { id } doReturn "local-pool-chain"
        on { ecosystem } doReturn Ecosystem.Substrate
        on { addressPrefix } doReturn 42
    }
    private val asset = Asset.createEmpty(chainAsset, 7, account, minSupportedVersion = null)
        .copy(freeInPlanksState = jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState.Known(java.math.BigInteger.ZERO))
    private val meta = mock<MetaAccount> {
        on { id } doReturn 7L
        on { chainAccounts } doReturn emptyMap()
        on { substrateAccountId } doReturn account
    }
    private val selectionRevision = StakingSelectionRevision()
    private val selected = mock<StakingSharedState> {
        on { selectionRevision } doReturn this@StakingPoolPreparationOwnershipTest.selectionRevision
        on { currentAssetFlow() } doReturn MutableStateFlow(asset)
        on { assetWithChain } doReturn flowOf(SingleAssetSharedState.AssetWithChain(chain, chainAsset))
        on { selectionItem } doReturn emptyFlow()
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
    private var owned: StakingViewModel? = null

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() {
        owned?.viewModelScope?.cancel()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test fun `healthy main action publishes its coherent context and opens welcome`() = runTest(dispatcher) {
        val vm = viewModel(MutableStateFlow(scenario(BigDecimal.ONE)))
        vm.startStakingPoolClick()
        runCurrent()
        assertSame(asset, shared.requireMainState.asset)
        assertEquals(BigDecimal.ONE, shared.requireMainState.amount)
        verify(router).openStakingPoolWelcome()
    }

    @Test fun `older amount preparation cannot overwrite a newer published context`() = runTest(dispatcher) {
        val held = MutableSharedFlow<StakingScenarioViewModel>()
        val vm = viewModel(held)
        vm.startStakingPoolClick()
        runCurrent()
        assertTrue(held.subscriptionCount.value > 0)
        val newer = StakingPoolState(chain = chain, asset = asset, address = "other-public-context", amount = BigDecimal.TEN)
        shared.mainState.set(newer)
        held.emit(scenario(BigDecimal.ONE))
        runCurrent()
        assertSame(newer, shared.mainState.get())
        verify(router, never()).openStakingPoolWelcome()
    }

    @Test fun `newer preparation completes first and cannot be overwritten by the older request`() = runTest(dispatcher) {
        val held = MutableSharedFlow<StakingScenarioViewModel>()
        val vm = viewModel(held)
        vm.startStakingPoolClick()
        runCurrent()
        assertTrue(held.subscriptionCount.value > 0)
        supplyScenario(vm, MutableStateFlow(scenario(BigDecimal.TEN)))
        vm.startStakingPoolClick()
        runCurrent()
        val newer = shared.requireMainState
        assertEquals(BigDecimal.TEN, newer.amount)
        held.emit(scenario(BigDecimal.ONE))
        runCurrent()
        assertSame(newer, shared.mainState.get())
        verify(router, times(1)).openStakingPoolWelcome()
    }

    @Test fun `wallet switch before queued preparation runs cannot adopt the new epoch`() = runTest(dispatcher) {
        val previous = StakingPoolState(chain = chain, asset = asset, address = "previous-public-context", amount = BigDecimal.TEN)
        shared.mainState.set(previous)
        val vm = viewModel(MutableStateFlow(scenario(BigDecimal.ONE)))
        vm.startStakingPoolClick()
        WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange()
        runCurrent()
        assertSame(previous, shared.mainState.get())
        verify(router, never()).openStakingPoolWelcome()
    }

    @Test fun `same wallet network change while amount is held cannot publish old selection`() = runTest(dispatcher) {
        val held = MutableSharedFlow<StakingScenarioViewModel>()
        val vm = viewModel(held)
        vm.startStakingPoolClick()
        runCurrent()
        assertTrue(held.subscriptionCount.value > 0)
        selectionRevision.changed(StakingAssetSelection.Pool("other-chain", "native"))
        held.emit(scenario(BigDecimal.ONE))
        runCurrent()
        org.junit.Assert.assertNull(shared.mainState.get())
        verify(router, never()).openStakingPoolWelcome()
    }

    @Test fun `same wallet network change and return cannot revive held preparation`() = runTest(dispatcher) {
        val held = MutableSharedFlow<StakingScenarioViewModel>()
        val vm = viewModel(held)
        vm.startStakingPoolClick()
        runCurrent()
        assertTrue(held.subscriptionCount.value > 0)
        selectionRevision.changed(StakingAssetSelection.Pool("other-chain", "native"))
        selectionRevision.changed(StakingAssetSelection.Pool("local-pool-chain", "native"))
        held.emit(scenario(BigDecimal.ONE))
        runCurrent()
        org.junit.Assert.assertNull(shared.mainState.get())
        verify(router, never()).openStakingPoolWelcome()
    }

    @Test fun `selection change before queued work begins cannot be adopted by that request`() = runTest(dispatcher) {
        val vm = viewModel(MutableStateFlow(scenario(BigDecimal.ONE)))
        vm.startStakingPoolClick()
        selectionRevision.changed(StakingAssetSelection.Pool("local-pool-chain", "native"))
        runCurrent()
        org.junit.Assert.assertNull(shared.mainState.get())
        verify(router, never()).openStakingPoolWelcome()
    }

    @Test fun `fresh request cannot adopt cached prior network while preference flow catches up`() = runTest(dispatcher) {
        selectionRevision.changed(StakingAssetSelection.Pool("other-chain", "native"))
        val vm = viewModel(MutableStateFlow(scenario(BigDecimal.ONE)))
        vm.startStakingPoolClick()
        runCurrent()
        org.junit.Assert.assertNull(shared.mainState.get())
        verify(router, never()).openStakingPoolWelcome()
    }

    @Test fun `healthy pool management still prepares and opens its bound context`() = runTest(dispatcher) {
        val vm = viewModel(MutableStateFlow(scenario(BigDecimal.ONE)))
        vm.onManagePoolStake()
        runCurrent()
        assertSame(asset, shared.requireMainState.asset)
        verify(router).openManagePoolStake()
    }

    @Test fun `queued pool management cannot adopt a changed wallet epoch`() = runTest(dispatcher) {
        val vm = viewModel(MutableStateFlow(scenario(BigDecimal.ONE)))
        vm.onManagePoolStake()
        WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange()
        runCurrent()
        org.junit.Assert.assertNull(shared.mainState.get())
        verify(router, never()).openManagePoolStake()
    }

    @Test fun `older preparation finishing first stays rejected once a newer request exists`() = runTest(dispatcher) {
        val first = MutableSharedFlow<StakingScenarioViewModel>()
        val second = MutableSharedFlow<StakingScenarioViewModel>()
        val vm = viewModel(first)
        vm.startStakingPoolClick()
        runCurrent()
        assertTrue(first.subscriptionCount.value > 0)
        supplyScenario(vm, second)
        vm.startStakingPoolClick()
        runCurrent()
        assertTrue(second.subscriptionCount.value > 0)
        first.emit(scenario(BigDecimal.ONE))
        runCurrent()
        org.junit.Assert.assertNull(shared.mainState.get())
        verify(router, never()).openStakingPoolWelcome()
        second.emit(scenario(BigDecimal.TEN))
        runCurrent()
        assertEquals(BigDecimal.TEN, shared.requireMainState.amount)
        verify(router, times(1)).openStakingPoolWelcome()
    }

    @Test fun `explicit matching pool selection still allows a fresh healthy request`() = runTest(dispatcher) {
        selectionRevision.changed(StakingAssetSelection.Pool("local-pool-chain", "native"))
        val vm = viewModel(MutableStateFlow(scenario(BigDecimal.ONE)))
        vm.startStakingPoolClick()
        runCurrent()
        assertEquals(BigDecimal.ONE, shared.requireMainState.amount)
        verify(router).openStakingPoolWelcome()
    }

    @Test fun `relay selection for the same asset does not authorize pool preparation`() = runTest(dispatcher) {
        selectionRevision.changed(StakingAssetSelection.RelayChainStaking("local-pool-chain", "native"))
        val vm = viewModel(MutableStateFlow(scenario(BigDecimal.ONE)))
        vm.startStakingPoolClick()
        runCurrent()
        org.junit.Assert.assertNull(shared.mainState.get())
        verify(router, never()).openStakingPoolWelcome()
    }

    @Test fun `reentrant context replacement during publication prevents stale navigation`() = runTest(dispatcher) {
        val newer = StakingPoolState(chain = chain, asset = asset, address = "observer-public-context", amount = BigDecimal.TEN)
        var observed = false
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            shared.mainState.process.collect { value ->
                if (value != null && !observed) {
                    observed = true
                    shared.mainState.set(newer)
                }
            }
        }
        val vm = viewModel(MutableStateFlow(scenario(BigDecimal.ONE)))
        vm.startStakingPoolClick()
        runCurrent()
        assertTrue(observed)
        assertSame(newer, shared.mainState.get())
        verify(router, never()).openStakingPoolWelcome()
    }

    @Test fun `reentrant new preparation owns navigation after earlier publication`() = runTest(dispatcher) {
        var newer: StakingPoolSharedStateProvider.PoolPreparation? = null
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            shared.mainState.process.collect { value ->
                if (value != null && newer == null) {
                    newer = shared.beginPoolPreparation(selectionRevision.capture())
                }
            }
        }
        val vm = viewModel(MutableStateFlow(scenario(BigDecimal.ONE)))
        vm.startStakingPoolClick()
        runCurrent()
        assertNotNull(newer)
        verify(router, never()).openStakingPoolWelcome()
        val next = shared.requireMainState.copy(amount = BigDecimal.TEN)
        assertTrue(shared.publishPoolPreparation(requireNotNull(newer), next, selectionRevision))
        assertSame(next, shared.mainState.get())
    }

    @Test fun `direct recovery opens with zero balance and no scenario or join draft`() = runTest(dispatcher) {
        val held = MutableSharedFlow<StakingScenarioViewModel>()
        val vm = viewModel(held)
        vm.onPoolJoinRecovery()
        runCurrent()
        assertSame(asset, shared.requireMainState.asset)
        assertEquals(0, requireNotNull(asset.transferable).signum())
        org.junit.Assert.assertNull(shared.requireMainState.amount)
        org.junit.Assert.assertNull(shared.joinFlowState.get())
        assertEquals(0, held.subscriptionCount.value)
        verify(router).openPoolJoinRecovery()
        verify(router, never()).openStakingPoolWelcome()
    }

    @Test fun `recovery does not overwrite an existing join draft`() = runTest(dispatcher) {
        val draft = jp.co.soramitsu.staking.impl.presentation.common.StakingPoolJoinFlowState(BigDecimal.TEN, mock(), shared.mainState.capture())
        shared.joinFlowState.set(draft)
        val vm = viewModel(MutableSharedFlow())
        vm.onPoolJoinRecovery()
        runCurrent()
        assertSame(draft, shared.joinFlowState.get())
        org.junit.Assert.assertNull(shared.requireMainState.amount)
        verify(router).openPoolJoinRecovery()
    }

    @Test fun `direct recovery queued before wallet change cannot navigate`() = runTest(dispatcher) {
        val vm = viewModel(MutableSharedFlow())
        vm.onPoolJoinRecovery()
        WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange()
        runCurrent()
        org.junit.Assert.assertNull(shared.mainState.get())
        verify(router, never()).openPoolJoinRecovery()
    }

    @Test fun `direct recovery rejects network change and return during held local metadata`() = runTest(dispatcher) {
        val held = MutableSharedFlow<SingleAssetSharedState.AssetWithChain>()
        org.mockito.kotlin.whenever(selected.assetWithChain).thenReturn(held)
        val vm = viewModel(MutableSharedFlow())
        vm.onPoolJoinRecovery()
        runCurrent()
        assertTrue(held.subscriptionCount.value > 0)
        selectionRevision.changed(StakingAssetSelection.Pool("other", "native"))
        selectionRevision.changed(StakingAssetSelection.Pool("local-pool-chain", "native"))
        held.emit(SingleAssetSharedState.AssetWithChain(chain, chainAsset))
        runCurrent()
        org.junit.Assert.assertNull(shared.mainState.get())
        verify(router, never()).openPoolJoinRecovery()
    }

    @Test fun `direct recovery cannot replace a newer context while local asset is held`() = runTest(dispatcher) {
        val held = MutableSharedFlow<Asset>()
        org.mockito.kotlin.whenever(selected.currentAssetFlow()).thenReturn(held)
        val vm = viewModel(MutableSharedFlow())
        vm.onPoolJoinRecovery()
        runCurrent()
        val newer = StakingPoolState(chain = chain, asset = asset, address = "newer-public-context")
        shared.mainState.set(newer)
        held.emit(asset)
        runCurrent()
        assertSame(newer, shared.mainState.get())
        verify(router, never()).openPoolJoinRecovery()
    }

    @Test fun `direct recovery repeated before execution opens only newest preparation`() = runTest(dispatcher) {
        val vm = viewModel(MutableSharedFlow())
        vm.onPoolJoinRecovery()
        vm.onPoolJoinRecovery()
        runCurrent()
        verify(router, times(1)).openPoolJoinRecovery()
    }

    @Test fun `direct recovery rejects relay selection for the same asset`() = runTest(dispatcher) {
        selectionRevision.changed(StakingAssetSelection.RelayChainStaking("local-pool-chain", "native"))
        val vm = viewModel(MutableSharedFlow())
        vm.onPoolJoinRecovery()
        runCurrent()
        org.junit.Assert.assertNull(shared.mainState.get())
        verify(router, never()).openPoolJoinRecovery()
    }

    @Test fun `reentrant recovery context replacement prevents stale route`() = runTest(dispatcher) {
        val newer = StakingPoolState(chain = chain, asset = asset, address = "observer-public-context")
        var changed = false
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            shared.mainState.process.collect {
                if (it != null && !changed) { changed = true; shared.mainState.set(newer) }
            }
        }
        val vm = viewModel(MutableSharedFlow())
        vm.onPoolJoinRecovery()
        runCurrent()
        assertSame(newer, shared.mainState.get())
        verify(router, never()).openPoolJoinRecovery()
    }

    @Test fun `direct recovery reports local metadata failure only for current request`() = runTest(dispatcher) {
        org.mockito.kotlin.whenever(selected.assetWithChain).thenReturn(kotlinx.coroutines.flow.flow { error("local metadata unavailable") })
        val vm = viewModel(MutableSharedFlow())
        vm.onPoolJoinRecovery()
        runCurrent()
        assertNotNull(vm.errorLiveData.value)
        verify(router, never()).openPoolJoinRecovery()
    }

    @Test fun `direct recovery suppresses stale local metadata failure after wallet change`() = runTest(dispatcher) {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        org.mockito.kotlin.whenever(selected.assetWithChain).thenReturn(kotlinx.coroutines.flow.flow { gate.await(); error("late metadata") })
        val vm = viewModel(MutableSharedFlow())
        vm.onPoolJoinRecovery()
        runCurrent()
        WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange()
        gate.complete(Unit)
        runCurrent()
        org.junit.Assert.assertNull(vm.errorLiveData.value)
        verify(router, never()).openPoolJoinRecovery()
    }

    @Test fun `wallet picker departure cancels held recovery without requiring wallet change`() = runTest(dispatcher) {
        val held = MutableSharedFlow<SingleAssetSharedState.AssetWithChain>()
        org.mockito.kotlin.whenever(selected.assetWithChain).thenReturn(held)
        val vm = viewModel(MutableSharedFlow())
        vm.onPoolJoinRecovery()
        runCurrent()
        assertTrue(held.subscriptionCount.value > 0)
        vm.avatarClicked()
        verify(router).openSelectWallet()
        held.emit(SingleAssetSharedState.AssetWithChain(chain, chainAsset))
        runCurrent()
        org.junit.Assert.assertNull(shared.mainState.get())
        verify(router, never()).openPoolJoinRecovery()
    }

    @Test fun `screen pause cancels held recovery and resume does not revive old tap`() = runTest(dispatcher) {
        val held = MutableSharedFlow<SingleAssetSharedState.AssetWithChain>()
        org.mockito.kotlin.whenever(selected.assetWithChain).thenReturn(held)
        val vm = viewModel(MutableSharedFlow())
        vm.onPoolJoinRecovery(); runCurrent()
        assertTrue(held.subscriptionCount.value > 0)
        vm.onStakingScreenPaused(); vm.onStakingScreenResumed()
        held.emit(SingleAssetSharedState.AssetWithChain(chain, chainAsset)); runCurrent()
        org.junit.Assert.assertNull(shared.mainState.get())
        verify(router, never()).openPoolJoinRecovery()
    }

    @Test fun `screen pause before queued work rejects it and hidden taps`() = runTest(dispatcher) {
        val vm = viewModel(MutableSharedFlow())
        vm.onPoolJoinRecovery(); vm.onStakingScreenPaused(); vm.onPoolJoinRecovery(); runCurrent()
        org.junit.Assert.assertNull(shared.mainState.get())
        verify(router, never()).openPoolJoinRecovery()
    }

    @Test fun `fresh recovery tap after returning to screen remains usable`() = runTest(dispatcher) {
        val vm = viewModel(MutableSharedFlow())
        vm.onStakingScreenPaused(); vm.onStakingScreenResumed(); vm.onPoolJoinRecovery(); runCurrent()
        assertSame(asset, shared.requireMainState.asset)
        verify(router).openPoolJoinRecovery()
    }

    @Test fun `navigation owner change before fragment pause rejects held recovery`() = runTest(dispatcher) {
        val held = MutableSharedFlow<SingleAssetSharedState.AssetWithChain>()
        org.mockito.kotlin.whenever(selected.assetWithChain).thenReturn(held)
        var current = true
        val vm = viewModel(MutableSharedFlow())
        vm.onPoolJoinRecovery { current }; runCurrent()
        assertTrue(held.subscriptionCount.value > 0)
        current = false
        held.emit(SingleAssetSharedState.AssetWithChain(chain, chainAsset)); runCurrent()
        org.junit.Assert.assertNull(shared.mainState.get())
        verify(router, never()).openPoolJoinRecovery()
    }

    @Test fun `navigation owner change suppresses late metadata failure before fragment pause`() = runTest(dispatcher) {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        org.mockito.kotlin.whenever(selected.assetWithChain).thenReturn(kotlinx.coroutines.flow.flow { gate.await(); error("late metadata") })
        var current = true
        val vm = viewModel(MutableSharedFlow())
        vm.onPoolJoinRecovery { current }; runCurrent()
        current = false; gate.complete(Unit); runCurrent()
        org.junit.Assert.assertNull(vm.errorLiveData.value)
        verify(router, never()).openPoolJoinRecovery()
    }

    @Test fun `reentrant departure during context publication prevents route`() = runTest(dispatcher) {
        val vm = viewModel(MutableSharedFlow())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            shared.mainState.process.collect { if (it != null) vm.onStakingScreenPaused() }
        }
        vm.onPoolJoinRecovery(); runCurrent()
        verify(router, never()).openPoolJoinRecovery()
    }

    @Test fun `callback whose screen owner already changed cannot start local preparation`() = runTest(dispatcher) {
        val held = MutableSharedFlow<SingleAssetSharedState.AssetWithChain>()
        org.mockito.kotlin.whenever(selected.assetWithChain).thenReturn(held)
        val vm = viewModel(MutableSharedFlow())
        vm.onPoolJoinRecovery { false }; runCurrent()
        assertEquals(0, held.subscriptionCount.value)
        org.junit.Assert.assertNull(shared.mainState.get())
        verify(router, never()).openPoolJoinRecovery()
    }

    private fun scenario(amount: BigDecimal) = mock<StakingScenarioViewModel> {
        on { enteredAmountFlow } doReturn MutableStateFlow<BigDecimal?>(amount)
    }

    private fun viewModel(source: SharedFlow<StakingScenarioViewModel>): StakingViewModel = StakingViewModel(
        interactor, mock(), mock(), router, resources, mock(), updateSystem, selected,
        mock(), mock(), mock(), mock(), mock(), shared, mock(), mock(), mock(), mock(), mock()
    ).also { owned = it; supplyScenario(it, source) }

    private fun supplyScenario(vm: StakingViewModel, source: SharedFlow<StakingScenarioViewModel>) {
        // This isolates reward/UI scenario creation; production preparation and publication execute unchanged.
        StakingViewModel::class.java.getDeclaredField("scenarioViewModelFlow").apply { isAccessible = true }.set(vm, source)
    }
}
