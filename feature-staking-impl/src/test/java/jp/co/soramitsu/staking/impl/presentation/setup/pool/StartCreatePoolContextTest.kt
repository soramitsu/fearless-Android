package jp.co.soramitsu.staking.impl.presentation.setup.pool

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolCreateFlowState
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolState
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Answers
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.math.BigInteger

/** Actual welcome create action and limit lookup; unrelated rewards wait until teardown. */
@OptIn(ExperimentalCoroutinesApi::class)
class StartCreatePoolContextTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val owned = mutableListOf<StartStakingPoolViewModel>()
    private val limit = CompletableDeferred<BigInteger>()
    private val entered = CompletableDeferred<Unit>()
    private val router = mock<StakingRouter>()
    private val resources = mock<ResourceManager> {
        on { getString(any()) } doAnswer { "fixture-${it.getArgument<Int>(0)}" }
        on { getQuantityString(any(), any(), anyVararg()) } doReturn "fixture period"
    }
    private val pool = mock<StakingPoolInteractor> {
        onBlocking { getPossiblePools(any()) } doSuspendableAnswer { entered.complete(Unit); limit.await() }
        onBlocking { getPoolsCount(any()) } doReturn BigInteger.ONE
    }
    private val rewards = mock<RewardCalculatorFactory> {
        onBlocking { create(any()) } doSuspendableAnswer { awaitCancellation() }
    }
    private val relay = mock<StakingRelayChainScenarioInteractor> {
        onBlocking { stakePeriodInHours() } doReturn 1
        onBlocking { unstakingPeriod() } doReturn 1
    }
    private val shared = StakingPoolSharedStateProvider().apply {
        mainState.set(StakingPoolState(
            chain = mock<Chain> { on { id } doReturn "public-fixture-chain" },
            asset = mock<Asset>(defaultAnswer = Answers.RETURNS_DEEP_STUBS),
            address = "public-root", amount = BigDecimal.ONE
        ))
    }
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() {
        owned.forEach { it.viewModelScope.cancel() }
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test fun `healthy welcome creates a draft bound to its original main context`() {
        val vm = vm()
        vm.onCreatePool()
        allow()
        assertSame(shared.requireMainState, shared.requireCreateState.context.value)
        assertTrue(shared.isPoolContextCurrent(shared.requireCreateState.context))
        verify(router).openCreatePoolSetup()
    }

    @Test fun `return to current welcome preserves an edited current draft`() {
        val draft = StakingPoolCreateFlowState(poolName = "Edited", context = shared.mainState.capture())
        shared.createFlowState.set(draft)
        vm().onCreatePool()
        allow()
        assertSame(draft, shared.requireCreateState)
        verify(router).openCreatePoolSetup()
    }

    @Test fun `new welcome replaces a draft from an earlier main context`() {
        val previous = StakingPoolCreateFlowState(poolName = "Old", context = shared.mainState.capture())
        shared.createFlowState.set(previous)
        shared.mainState.set(shared.requireMainState.copy())
        vm().onCreatePool()
        allow()
        assertNotSame(previous, shared.requireCreateState)
        assertNull(shared.requireCreateState.poolName)
        assertSame(shared.requireMainState, shared.requireCreateState.context.value)
    }

    @Test fun `retained welcome cannot create after main replacement`() {
        val vm = vm()
        shared.mainState.set(shared.requireMainState.copy())
        vm.onCreatePool()
        allow()
        assertNull(shared.createFlowState.get())
        verify(router, never()).openCreatePoolSetup()
    }

    @Test fun `wallet round trip invalidates retained welcome`() {
        val vm = vm()
        repeat(2) { WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange() }
        vm.onCreatePool()
        allow()
        assertNull(shared.createFlowState.get())
        verify(router, never()).openCreatePoolSetup()
    }

    @Test fun `main replacement during limit lookup prevents draft publication`() {
        val vm = vm()
        vm.onCreatePool()
        dispatcher.scheduler.runCurrent()
        assertTrue(entered.isCompleted)
        shared.mainState.set(shared.requireMainState.copy())
        allow()
        assertNull(shared.createFlowState.get())
        verify(router, never()).openCreatePoolSetup()
    }

    @Test fun `wallet change during limit lookup suppresses stale limit alert`() {
        val vm = vm()
        vm.onCreatePool()
        dispatcher.scheduler.runCurrent()
        assertTrue(entered.isCompleted)
        WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange()
        allow(BigInteger.ZERO)
        assertNull(shared.createFlowState.get())
        verify(router, never()).openAlert(any())
        verify(router, never()).openCreatePoolSetup()
    }

    @Test fun `current reached limit still explains why creation is blocked`() {
        vm().onCreatePool()
        allow(BigInteger.ONE)
        assertNull(shared.createFlowState.get())
        verify(router).openAlert(any())
        verify(router, never()).openCreatePoolSetup()
    }

    @Test fun `Back during limit lookup prevents departing welcome from opening setup`() {
        val vm = vm()
        vm.onCreatePool()
        dispatcher.scheduler.runCurrent()
        assertTrue(entered.isCompleted)
        vm.onBackClick()
        allow()
        assertNull(shared.createFlowState.get())
        verify(router, never()).openCreatePoolSetup()
    }

    @Test fun `reentrant replacement during create publication prevents stale setup navigation`() {
        val vm = vm()
        var replaced = false
        val observerScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.test.UnconfinedTestDispatcher())
        val observer = observerScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            shared.createFlowState.process.collect { state ->
                if (state != null && !replaced) {
                    replaced = true
                    shared.createFlowState.set(state.copy(poolName = "Newer draft"))
                }
            }
        }
        try {
            vm.onCreatePool()
            allow()
            assertTrue(replaced)
            assertEquals("Newer draft", sharedStateName())
            verify(router, never()).openCreatePoolSetup()
        } finally { observer.cancel(); observerScope.cancel() }
    }

    @Test fun `dismissed welcome cannot finish its held create action`() {
        val vm = vm()
        vm.onCreatePool()
        dispatcher.scheduler.runCurrent()
        assertTrue(entered.isCompleted)
        vm.onDismissed()
        allow()
        assertNull(shared.createFlowState.get())
        verify(router, never()).openCreatePoolSetup()
        verify(router, never()).back()
    }

    @Test fun `cleared welcome cannot finish its held create action`() {
        val vm = vm()
        vm.onCreatePool()
        dispatcher.scheduler.runCurrent()
        assertTrue(entered.isCompleted)
        androidx.lifecycle.ViewModelStore().also { it.put("welcome", vm); it.clear() }
        allow()
        assertNull(shared.createFlowState.get())
        verify(router, never()).openCreatePoolSetup()
    }

    @Test fun `welcome Back is idempotent`() {
        val vm = vm()
        vm.onBackClick()
        vm.onBackClick()
        verify(router).back()
    }

    @Test fun `draft replacement while limit is held prevents unrelated setup navigation`() {
        val vm = vm()
        vm.onCreatePool()
        dispatcher.scheduler.runCurrent()
        assertTrue(entered.isCompleted)
        val replacement = StakingPoolCreateFlowState(poolName = "Newer", context = shared.mainState.capture())
        shared.createFlowState.set(replacement)
        allow()
        assertEquals(replacement, shared.createFlowState.get())
        verify(router, never()).openCreatePoolSetup()
    }

    @Test fun `ordinary current limit lookup failure is explained without creating a draft`() {
        val vm = vm()
        vm.onCreatePool()
        dispatcher.scheduler.runCurrent()
        assertTrue(entered.isCompleted)
        limit.completeExceptionally(java.io.IOException("local limit unavailable"))
        dispatcher.scheduler.runCurrent()
        assertEquals("local limit unavailable", vm.errorLiveData.value?.peekContent())
        assertNull(shared.createFlowState.get())
        verify(router, never()).openCreatePoolSetup()
    }

    @Test fun `Back revoked welcome cannot open join setup`() {
        val vm = vm()
        vm.onBackClick()
        vm.onJoinPool()
        assertNull(shared.joinFlowState.get())
        verify(router, never()).openSetupStakingPool()
    }

    @Test fun `Back revoked welcome cannot open instructions`() {
        val vm = vm()
        vm.onBackClick()
        vm.onInstructionsClick()
        verify(router, never()).openWebViewer(any(), any())
    }

    @Test fun `reentrant join draft replacement prevents stale join navigation`() {
        val vm = vm()
        var replaced = false
        val observerScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.test.UnconfinedTestDispatcher())
        val observer = observerScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            shared.joinFlowState.process.collect { state ->
                if (state != null && !replaced) {
                    replaced = true
                    shared.joinFlowState.set(state.copy(amount = BigDecimal.TEN))
                }
            }
        }
        try {
            vm.onJoinPool()
            assertTrue(replaced)
            assertEquals(BigDecimal.TEN, shared.requireJoinState.amount)
            verify(router, never()).openSetupStakingPool()
        } finally { observer.cancel(); observerScope.cancel() }
    }

    private fun sharedStateName() = shared.requireCreateState.poolName

    private fun vm() = StartStakingPoolViewModel(relay, mock(), pool, resources, rewards, router, shared)
        .also { owned += it }
    private fun allow(value: BigInteger = BigInteger.TEN) {
        limit.complete(value)
        dispatcher.scheduler.runCurrent()
    }
}
