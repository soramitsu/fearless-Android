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

/** Actual root ViewModel actions with local scenario payloads; no signing or remote transport. */
@OptIn(ExperimentalCoroutinesApi::class)
class StakingRootActionLifetimeTest {
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

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() {
        owned?.viewModelScope?.cancel()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test fun `healthy reward payload publishes from the selected scenario`() = runTest(dispatcher) {
        val scenario = scenario(); whenever(scenario.rewardEstimationPayload(any())).thenReturn(payload)
        val vm = viewModel(MutableStateFlow(scenario)); vm.onEstimatedEarningsInfoClick(); runCurrent()
        assertSame(payload, vm.showRewardEstimationEvent.value?.peekContent())
        verify(scenario).rewardEstimationPayload(any())
    }

    @Test fun `held reward payload cannot publish after screen pause`() = runTest(dispatcher) {
        val vm = heldReward(); vm.onEstimatedEarningsInfoClick(); runCurrent(); assertEquals(listOf("reward"), calls)
        vm.onStakingScreenPaused(); gate.complete(Unit); runCurrent()
        assertNull(vm.showRewardEstimationEvent.value)
    }

    @Test fun `returning to the screen cannot revive held reward payload`() = runTest(dispatcher) {
        val vm = heldReward(); vm.onEstimatedEarningsInfoClick(); runCurrent(); assertEquals(listOf("reward"), calls)
        vm.onStakingScreenPaused(); vm.onStakingScreenResumed(); gate.complete(Unit); runCurrent()
        assertNull(vm.showRewardEstimationEvent.value)
    }

    @Test fun `hidden reward tap cannot start scenario work`() = runTest(dispatcher) {
        val vm = heldReward(); vm.onStakingScreenPaused(); vm.onEstimatedEarningsInfoClick(); runCurrent()
        assertTrue(calls.isEmpty()); gate.complete(Unit)
    }

    @Test fun `pause before queued reward request prevents scenario work`() = runTest(dispatcher) {
        val vm = heldReward(); vm.onEstimatedEarningsInfoClick(); vm.onStakingScreenPaused(); runCurrent()
        assertTrue(calls.isEmpty()); gate.complete(Unit)
    }

    @Test fun `wallet switch during held reward withdraws old payload`() = runTest(dispatcher) {
        val vm = heldReward(); vm.onEstimatedEarningsInfoClick(); runCurrent(); assertEquals(listOf("reward"), calls)
        WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange(); gate.complete(Unit); runCurrent()
        assertNull(vm.showRewardEstimationEvent.value)
    }

    @Test fun `wallet switch before queued reward prevents scenario work`() = runTest(dispatcher) {
        val vm = heldReward(); vm.onEstimatedEarningsInfoClick()
        WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange(); runCurrent()
        assertTrue(calls.isEmpty()); gate.complete(Unit)
    }

    @Test fun `selection intent during held reward withdraws old payload`() = runTest(dispatcher) {
        val vm = heldReward(); vm.onEstimatedEarningsInfoClick(); runCurrent(); assertEquals(listOf("reward"), calls)
        revision.changed(StakingAssetSelection.Pool("other", "native")); gate.complete(Unit); runCurrent()
        assertNull(vm.showRewardEstimationEvent.value)
    }

    @Test fun `selection away and back cannot revive held reward`() = runTest(dispatcher) {
        val vm = heldReward(); vm.onEstimatedEarningsInfoClick(); runCurrent(); assertEquals(listOf("reward"), calls)
        revision.changed(StakingAssetSelection.Pool("other", "native"))
        revision.changed(StakingAssetSelection.Pool("local-pool-chain", "native")); gate.complete(Unit); runCurrent()
        assertNull(vm.showRewardEstimationEvent.value)
    }

    @Test fun `selection intent before queued reward prevents scenario work`() = runTest(dispatcher) {
        val vm = heldReward(); vm.onEstimatedEarningsInfoClick()
        revision.changed(StakingAssetSelection.Pool("other", "native")); runCurrent()
        assertTrue(calls.isEmpty()); gate.complete(Unit)
    }

    @Test fun `repeated held reward tap produces at most one payload lookup`() = runTest(dispatcher) {
        val vm = heldReward(); vm.onEstimatedEarningsInfoClick(); runCurrent(); assertEquals(listOf("reward"), calls)
        vm.onEstimatedEarningsInfoClick(); runCurrent(); assertEquals(listOf("reward"), calls)
        gate.complete(Unit); runCurrent(); assertSame(payload, vm.showRewardEstimationEvent.value?.peekContent())
    }

    @Test fun `fresh reward tap after resume remains usable`() = runTest(dispatcher) {
        val scenario = scenario(); whenever(scenario.rewardEstimationPayload(any())).thenReturn(payload)
        val vm = viewModel(MutableStateFlow(scenario)); vm.onStakingScreenPaused(); vm.onStakingScreenResumed()
        vm.onEstimatedEarningsInfoClick(); runCurrent(); assertSame(payload, vm.showRewardEstimationEvent.value?.peekContent())
    }

    @Test fun `held pool welcome preparation cannot publish after pause`() = runTest(dispatcher) {
        val held = holdAsset(); val vm = viewModel(MutableStateFlow(scenario()))
        vm.startStakingPoolClick(); runCurrent(); assertTrue(held.subscriptionCount.value > 0)
        vm.onStakingScreenPaused(); held.emit(SingleAssetSharedState.AssetWithChain(chain, chainAsset)); runCurrent()
        assertNull(shared.mainState.get()); verify(router, never()).openStakingPoolWelcome()
    }

    @Test fun `held pool manage preparation cannot publish after pause and resume`() = runTest(dispatcher) {
        val held = holdAsset(); val vm = viewModel(MutableStateFlow(scenario()))
        vm.onManagePoolStake(); runCurrent(); assertTrue(held.subscriptionCount.value > 0)
        vm.onStakingScreenPaused(); vm.onStakingScreenResumed()
        held.emit(SingleAssetSharedState.AssetWithChain(chain, chainAsset)); runCurrent()
        assertNull(shared.mainState.get()); verify(router, never()).openManagePoolStake()
    }

    @Test fun `hidden pool welcome tap cannot prepare or navigate`() = runTest(dispatcher) {
        val vm = viewModel(MutableStateFlow(scenario())); vm.onStakingScreenPaused(); vm.startStakingPoolClick(); runCurrent()
        assertNull(shared.mainState.get()); verify(router, never()).openStakingPoolWelcome()
    }

    @Test fun `hidden pool manage tap cannot prepare or navigate`() = runTest(dispatcher) {
        val vm = viewModel(MutableStateFlow(scenario())); vm.onStakingScreenPaused(); vm.onManagePoolStake(); runCurrent()
        assertNull(shared.mainState.get()); verify(router, never()).openManagePoolStake()
    }

    @Test fun `queued pool welcome is rejected by screen pause`() = runTest(dispatcher) {
        val vm = viewModel(MutableStateFlow(scenario())); vm.startStakingPoolClick(); vm.onStakingScreenPaused(); runCurrent()
        assertNull(shared.mainState.get()); verify(router, never()).openStakingPoolWelcome()
    }

    @Test fun `queued pool manage is rejected by screen pause`() = runTest(dispatcher) {
        val vm = viewModel(MutableStateFlow(scenario())); vm.onManagePoolStake(); vm.onStakingScreenPaused(); runCurrent()
        assertNull(shared.mainState.get()); verify(router, never()).openManagePoolStake()
    }

    @Test fun `reentrant pause during welcome publication prevents navigation`() = runTest(dispatcher) {
        val vm = viewModel(MutableStateFlow(scenario()))
        val collector = backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            shared.mainState.process.collect { if (it != null) vm.onStakingScreenPaused() }
        }
        vm.startStakingPoolClick(); runCurrent(); verify(router, never()).openStakingPoolWelcome(); collector.cancel()
    }

    @Test fun `repeated welcome taps before navigation pause cannot push twice`() = runTest(dispatcher) {
        val vm = viewModel(MutableStateFlow(scenario())); vm.startStakingPoolClick(); runCurrent()
        verify(router).openStakingPoolWelcome(); vm.startStakingPoolClick(); runCurrent()
        verify(router, times(1)).openStakingPoolWelcome()
    }

    @Test fun `healthy manage action publishes current context once`() = runTest(dispatcher) {
        val vm = viewModel(MutableStateFlow(scenario())); vm.onManagePoolStake(); runCurrent()
        assertSame(asset, shared.requireMainState.asset); verify(router).openManagePoolStake()
    }

    @Test fun `hidden nonpool tap cannot invoke scenario navigation`() = runTest(dispatcher) {
        val scenario = scenario(); val vm = viewModel(MutableStateFlow(scenario))
        vm.onStakingScreenPaused(); vm.startNonPoolStakingClick(); runCurrent(); verify(scenario, never()).startStaking(any(), any(), any())
    }

    @Test fun `queued nonpool tap cannot survive pause`() = runTest(dispatcher) {
        val scenario = scenario(); val vm = viewModel(MutableStateFlow(scenario))
        vm.startNonPoolStakingClick(); vm.onStakingScreenPaused(); runCurrent(); verify(scenario, never()).startStaking(any(), any(), any())
    }

    @Test fun `held nonpool scenario cannot navigate after pause`() = runTest(dispatcher) {
        val held = MutableSharedFlow<StakingScenarioViewModel>(); val vm = viewModel(held)
        vm.startNonPoolStakingClick(); runCurrent(); assertTrue(held.subscriptionCount.value > 0)
        vm.onStakingScreenPaused(); val scenario = scenario(); held.emit(scenario); runCurrent(); verify(scenario, never()).startStaking(any(), any(), any())
    }

    @Test fun `healthy nonpool tap invokes selected scenario once`() = runTest(dispatcher) {
        val scenario = scenario(); val vm = viewModel(MutableStateFlow(scenario)); vm.startNonPoolStakingClick(); runCurrent()
        verify(scenario).startStaking(any(), any(), any())
    }

    @Test fun `nonpool validation handoff leaves the screen usable before actual navigation`() = runTest(dispatcher) {
        val scenario = scenario(); whenever(scenario.rewardEstimationPayload(any())).thenReturn(payload)
        val vm = viewModel(MutableStateFlow(scenario)); vm.startNonPoolStakingClick(); runCurrent()
        verify(scenario).startStaking(any(), any(), any())
        vm.onEstimatedEarningsInfoClick(); runCurrent()
        assertSame(payload, vm.showRewardEstimationEvent.value?.peekContent())
    }

    @Test fun `rejected welcome navigation reports error and permits a fresh retry`() = runTest(dispatcher) {
        doThrow(IllegalStateException("Local route unavailable")).doNothing().whenever(router).openStakingPoolWelcome()
        val vm = viewModel(MutableStateFlow(scenario())); vm.startStakingPoolClick(); runCurrent()
        verify(router).openStakingPoolWelcome()
        assertEquals("Local route unavailable", vm.errorLiveData.value?.peekContent())
        vm.startStakingPoolClick(); runCurrent(); verify(router, times(2)).openStakingPoolWelcome()
    }

    @Test fun `rejected manage navigation reports error and permits a fresh retry`() = runTest(dispatcher) {
        doThrow(IllegalStateException("Local route unavailable")).doNothing().whenever(router).openManagePoolStake()
        val vm = viewModel(MutableStateFlow(scenario())); vm.onManagePoolStake(); runCurrent()
        verify(router).openManagePoolStake()
        assertEquals("Local route unavailable", vm.errorLiveData.value?.peekContent())
        vm.onManagePoolStake(); runCurrent(); verify(router, times(2)).openManagePoolStake()
    }

    @Test fun `already changed destination prevents reward scenario work`() = runTest(dispatcher) {
        val vm = heldReward(); vm.onEstimatedEarningsInfoClick { false }; runCurrent()
        assertTrue(calls.isEmpty()); gate.complete(Unit)
    }

    @Test fun `destination changes during held reward before pause suppress publication`() = runTest(dispatcher) {
        var current = true; val vm = heldReward(); vm.onEstimatedEarningsInfoClick { current }; runCurrent()
        assertEquals(listOf("reward"), calls); current = false; gate.complete(Unit); runCurrent()
        assertNull(vm.showRewardEstimationEvent.value)
    }

    @Test fun `destination changes during held pool preparation prevents context and route`() = runTest(dispatcher) {
        var current = true; val held = holdAsset(); val vm = viewModel(MutableStateFlow(scenario()))
        vm.startStakingPoolClick { current }; runCurrent(); assertTrue(held.subscriptionCount.value > 0)
        current = false; held.emit(SingleAssetSharedState.AssetWithChain(chain, chainAsset)); runCurrent()
        assertNull(shared.mainState.get()); verify(router, never()).openStakingPoolWelcome()
    }

    @Test fun `destination changes during held manage preparation prevents context and route`() = runTest(dispatcher) {
        var current = true; val held = holdAsset(); val vm = viewModel(MutableStateFlow(scenario()))
        vm.onManagePoolStake { current }; runCurrent(); assertTrue(held.subscriptionCount.value > 0)
        current = false; held.emit(SingleAssetSharedState.AssetWithChain(chain, chainAsset)); runCurrent()
        assertNull(shared.mainState.get()); verify(router, never()).openManagePoolStake()
    }

    @Test fun `destination changes during held nonpool source prevents handoff`() = runTest(dispatcher) {
        var current = true; val held = MutableSharedFlow<StakingScenarioViewModel>(); val vm = viewModel(held)
        vm.startNonPoolStakingClick { current }; runCurrent(); assertTrue(held.subscriptionCount.value > 0)
        current = false; val scenario = scenario(); held.emit(scenario); runCurrent(); verify(scenario, never()).startStaking(any(), any(), any())
    }

    @Test fun `noncooperative reward completion after pause cannot publish`() = runTest(dispatcher) {
        val scenario = scenario(); whenever(scenario.rewardEstimationPayload(any())).doSuspendableAnswer {
            calls += "reward"; kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { gate.await(); payload }
        }
        val vm = viewModel(MutableStateFlow(scenario)); vm.onEstimatedEarningsInfoClick(); runCurrent()
        assertEquals(listOf("reward"), calls); vm.onStakingScreenPaused(); vm.onStakingScreenResumed()
        gate.complete(Unit); runCurrent(); assertNull(vm.showRewardEstimationEvent.value)
    }

    @Test fun `current reward failure reports error and releases request for retry`() = runTest(dispatcher) {
        val scenario = scenario(); whenever(scenario.rewardEstimationPayload(any())).doSuspendableAnswer { throw java.io.IOException("Local estimate unavailable") }
        val vm = viewModel(MutableStateFlow(scenario)); vm.onEstimatedEarningsInfoClick(); runCurrent()
        assertEquals("Local estimate unavailable", vm.errorLiveData.value?.peekContent())
        doReturn(payload).whenever(scenario).rewardEstimationPayload(any())
        vm.onEstimatedEarningsInfoClick(); runCurrent(); assertSame(payload, vm.showRewardEstimationEvent.value?.peekContent())
    }

    @Test fun `paused screen clears an undelivered reward event`() = runTest(dispatcher) {
        val scenario = scenario(); whenever(scenario.rewardEstimationPayload(any())).thenReturn(payload)
        val vm = viewModel(MutableStateFlow(scenario)); vm.onEstimatedEarningsInfoClick(); runCurrent()
        assertSame(payload, vm.showRewardEstimationEvent.value?.peekContent()); vm.onStakingScreenPaused(); vm.onStakingScreenResumed()
        assertNull(vm.showRewardEstimationEvent.value)
    }

    @Test fun `wallet change reentered by pause event clearing prevents final route`() = runTest(dispatcher) {
        val vm = viewModel(MutableStateFlow(scenario())); var armed = true
        val observer = androidx.lifecycle.Observer<jp.co.soramitsu.common.utils.Event<StakingRewardEstimationBottomSheet.Payload>?> {
            if (it == null && armed) { armed = false; WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange() }
        }
        vm.showRewardEstimationEvent.observeForever(observer)
        try {
            vm.startStakingPoolClick(); runCurrent(); assertFalse(armed)
            verify(router, never()).openStakingPoolWelcome()
        } finally { vm.showRewardEstimationEvent.removeObserver(observer) }
    }

    @Test fun `selection change reentered by pause event clearing prevents final route`() = runTest(dispatcher) {
        val vm = viewModel(MutableStateFlow(scenario())); var armed = true
        val observer = androidx.lifecycle.Observer<jp.co.soramitsu.common.utils.Event<StakingRewardEstimationBottomSheet.Payload>?> {
            if (it == null && armed) { armed = false; revision.changed(StakingAssetSelection.Pool("other", "native")) }
        }
        vm.showRewardEstimationEvent.observeForever(observer)
        try {
            vm.onManagePoolStake(); runCurrent(); assertFalse(armed)
            verify(router, never()).openManagePoolStake()
        } finally { vm.showRewardEstimationEvent.removeObserver(observer) }
    }

    @Test fun `reentered pause and resume during event clearing cannot revive original route`() = runTest(dispatcher) {
        val vm = viewModel(MutableStateFlow(scenario())); var armed = true
        val observer = androidx.lifecycle.Observer<jp.co.soramitsu.common.utils.Event<StakingRewardEstimationBottomSheet.Payload>?> {
            if (it == null && armed) { armed = false; vm.onStakingScreenPaused(); vm.onStakingScreenResumed() }
        }
        vm.showRewardEstimationEvent.observeForever(observer)
        try {
            vm.startStakingPoolClick(); runCurrent(); assertFalse(armed)
            verify(router, never()).openStakingPoolWelcome()
        } finally { vm.showRewardEstimationEvent.removeObserver(observer) }
    }

    @Test fun `router moving before an exception cannot reactivate departed source`() = runTest(dispatcher) {
        var current = true; doAnswer { current = false; throw IllegalStateException("Moved before failure") }.whenever(router).openStakingPoolWelcome()
        val scenario = scenario(); whenever(scenario.rewardEstimationPayload(any())).thenReturn(payload)
        val vm = viewModel(MutableStateFlow(scenario)); vm.startStakingPoolClick { current }; runCurrent()
        verify(router).openStakingPoolWelcome(); vm.onEstimatedEarningsInfoClick(); runCurrent()
        verify(scenario, never()).rewardEstimationPayload(any()); assertNull(vm.errorLiveData.value)
    }

    @Test fun `wallet transition in progress denies a tap without scenario work`() = runTest(dispatcher) {
        val vm = heldReward(); WalletSelectionEpoch.beginChange()
        try {
            vm.onEstimatedEarningsInfoClick(); runCurrent(); assertTrue(calls.isEmpty())
        } finally { WalletSelectionEpoch.endChange(); gate.complete(Unit) }
    }

    @Test fun `wallet change during departure leaves visible source available for a fresh reward`() = runTest(dispatcher) {
        val scenario = scenario(); whenever(scenario.rewardEstimationPayload(any())).thenReturn(payload)
        val vm = viewModel(MutableStateFlow(scenario)); var armed = true
        val observer = androidx.lifecycle.Observer<jp.co.soramitsu.common.utils.Event<StakingRewardEstimationBottomSheet.Payload>?> {
            if (it == null && armed) { armed = false; WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange() }
        }
        vm.showRewardEstimationEvent.observeForever(observer)
        try {
            vm.startStakingPoolClick { true }; runCurrent(); assertFalse(armed)
            verify(router, never()).openStakingPoolWelcome()
            vm.onEstimatedEarningsInfoClick { true }; runCurrent()
            assertSame(payload, vm.showRewardEstimationEvent.value?.peekContent())
        } finally { vm.showRewardEstimationEvent.removeObserver(observer) }
    }

    @Test fun `selection change during departure leaves visible source available for fresh actions`() = runTest(dispatcher) {
        val scenario = scenario(); whenever(scenario.rewardEstimationPayload(any())).thenReturn(payload)
        val vm = viewModel(MutableStateFlow(scenario)); var armed = true
        val observer = androidx.lifecycle.Observer<jp.co.soramitsu.common.utils.Event<StakingRewardEstimationBottomSheet.Payload>?> {
            if (it == null && armed) { armed = false; revision.changed(StakingAssetSelection.Pool("other", "native")) }
        }
        vm.showRewardEstimationEvent.observeForever(observer)
        try {
            vm.onManagePoolStake { true }; runCurrent(); assertFalse(armed)
            verify(router, never()).openManagePoolStake()
            vm.onEstimatedEarningsInfoClick { true }; runCurrent()
            assertNull(vm.showRewardEstimationEvent.value)
            verify(scenario, never()).rewardEstimationPayload(any())
            revision.changed(StakingAssetSelection.RelayChainStaking("local-pool-chain", "native"))
            vm.onEstimatedEarningsInfoClick { true }; runCurrent()
            assertSame(payload, vm.showRewardEstimationEvent.value?.peekContent())
        } finally { vm.showRewardEstimationEvent.removeObserver(observer) }
    }

    @Test fun `failed router that changes wallet leaves visible source available without stale error`() = runTest(dispatcher) {
        doAnswer {
            WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange()
            throw IllegalStateException("Old wallet route rejected")
        }.whenever(router).openStakingPoolWelcome()
        val scenario = scenario(); whenever(scenario.rewardEstimationPayload(any())).thenReturn(payload)
        val vm = viewModel(MutableStateFlow(scenario)); vm.startStakingPoolClick { true }; runCurrent()
        verify(router).openStakingPoolWelcome(); assertNull(vm.errorLiveData.value)
        vm.onEstimatedEarningsInfoClick { true }; runCurrent()
        assertSame(payload, vm.showRewardEstimationEvent.value?.peekContent())
    }

    @Test fun `cancelled router leaves unchanged visible source available without an error`() = runTest(dispatcher) {
        doAnswer { throw kotlinx.coroutines.CancellationException("Local route cancelled") }.whenever(router).openStakingPoolWelcome()
        val scenario = scenario(); whenever(scenario.rewardEstimationPayload(any())).thenReturn(payload)
        val vm = viewModel(MutableStateFlow(scenario)); vm.startStakingPoolClick { true }; runCurrent()
        verify(router).openStakingPoolWelcome(); assertNull(vm.errorLiveData.value)
        vm.onEstimatedEarningsInfoClick { true }; runCurrent()
        assertSame(payload, vm.showRewardEstimationEvent.value?.peekContent())
    }

    @Test fun `pause clears events safely through production EventObserver`() = runTest(dispatcher) {
        val vm = viewModel(MutableStateFlow(scenario()))
        val reward = jp.co.soramitsu.common.utils.EventObserver<StakingRewardEstimationBottomSheet.Payload> { fail("No reward expected") }
        val manage = jp.co.soramitsu.common.utils.EventObserver<ManageStakingBottomSheet.Payload> { fail("No management event expected") }
        val status = jp.co.soramitsu.common.utils.EventObserver<StakingViewState.StatusInfo> { fail("No status expected") }
        vm.showRewardEstimationEvent.observeForever(reward)
        vm.showManageStakeEvent.observeForever(manage)
        vm.showStakingStatusEvent.observeForever(status)
        try {
            vm.onStakingScreenPaused(); runCurrent()
            assertNull(vm.showRewardEstimationEvent.value)
            assertNull(vm.showManageStakeEvent.value)
            assertNull(vm.showStakingStatusEvent.value)
        } finally {
            vm.showRewardEstimationEvent.removeObserver(reward)
            vm.showManageStakeEvent.removeObserver(manage)
            vm.showStakingStatusEvent.removeObserver(status)
        }
    }

    @Test fun `current pool route survives event clearing with production observer`() = runTest(dispatcher) {
        val vm = viewModel(MutableStateFlow(scenario()))
        val observer = jp.co.soramitsu.common.utils.EventObserver<StakingRewardEstimationBottomSheet.Payload> { fail("No reward expected") }
        vm.showRewardEstimationEvent.observeForever(observer)
        try {
            vm.startStakingPoolClick { true }; runCurrent()
            verify(router).openStakingPoolWelcome()
            assertNull(vm.errorLiveData.value)
        } finally { vm.showRewardEstimationEvent.removeObserver(observer) }
    }

    private suspend fun heldReward(): StakingViewModel {
        val scenario = scenario(); whenever(scenario.rewardEstimationPayload(any())).doSuspendableAnswer {
            calls += "reward"; gate.await(); payload
        }
        return viewModel(MutableStateFlow(scenario))
    }
    private fun holdAsset(): MutableSharedFlow<SingleAssetSharedState.AssetWithChain> {
        val held = MutableSharedFlow<SingleAssetSharedState.AssetWithChain>()
        whenever(selected.assetWithChain).thenReturn(held); return held
    }
    @Test fun `cleared ViewModel during event clearing cannot navigate`() = runTest(dispatcher) {
        val vm = viewModel(MutableStateFlow(scenario())); var armed = true
        val observer = androidx.lifecycle.Observer<jp.co.soramitsu.common.utils.Event<StakingRewardEstimationBottomSheet.Payload>?> {
            if (it == null && armed) {
                armed = false
                androidx.lifecycle.ViewModelStore().also { store -> store.put("staking", vm); store.clear() }
            }
        }
        vm.showRewardEstimationEvent.observeForever(observer)
        try {
            vm.startStakingPoolClick(); runCurrent()
            verify(router, never()).openStakingPoolWelcome()
        } finally { vm.showRewardEstimationEvent.removeObserver(observer) }
    }

    private fun scenario() = mock<StakingScenarioViewModel> { on { stakingType } doReturn jp.co.soramitsu.staking.api.data.StakingType.RELAYCHAIN; on { enteredAmountFlow } doReturn MutableStateFlow<BigDecimal?>(BigDecimal.ONE) }
    private fun viewModel(source: SharedFlow<StakingScenarioViewModel>) = StakingViewModel(
        interactor, mock(), mock(), router, resources, mock(), updateSystem, selected,
        mock(), mock(), mock(), mock(), mock(), shared, mock(), mock(), mock(), mock(), mock()
    ).also {
        owned = it
        // Only scenario creation is supplied; root action scheduling/guards/publication execute unchanged.
        StakingViewModel::class.java.getDeclaredField("scenarioViewModelFlow").apply { isAccessible = true }.set(it, source)
    }
}
