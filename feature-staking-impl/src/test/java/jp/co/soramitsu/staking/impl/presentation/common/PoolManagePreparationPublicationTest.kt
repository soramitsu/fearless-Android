package jp.co.soramitsu.staking.impl.presentation.common

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class PoolManagePreparationPublicationTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val owned = mutableListOf<PoolManagePreparationHarness>()
    private val observers = mutableListOf<Job>()
    private val inline = object : CoroutineDispatcher() {
        override fun isDispatchNeeded(context: CoroutineContext) = true
        override fun dispatch(context: CoroutineContext, block: Runnable) = block.run()
    }
    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() {
        observers.forEach { it.cancel() }
        runBlocking { withTimeout(5_000) { owned.flatMap { it.vms }.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } } }
        Dispatchers.resetMain()
    }
    private fun harness() = PoolManagePreparationHarness("bondmore").also(owned::add)
    private fun observe(h: PoolManagePreparationHarness, block: () -> Unit) {
        observers += CoroutineScope(inline).launch { h.shared.manageState.process.drop(1).collect { block() } }
    }
    @Test fun `reentrant draft replacement is never adopted as this edit`() {
        val h = harness(); var observed = false
        observe(h) { if (!observed) { observed = true; h.replaceManage() } }
        h.vm.onAmountInput(BigDecimal("0.0000000002")); h.vm.onNextClick()
        assertTrue(observed); assertEquals(BigInteger.valueOf(99), h.shared.requireManageState.amountInPlanks)
        assertTrue(h.navigations.isEmpty()); h.resume(); h.vm.onNextClick(); assertTrue(h.navigations.isEmpty())
    }
    @Test fun `reentrant main replacement prevents forward navigation after edit`() {
        val h = harness(); var observed = false
        observe(h) { observed = true; h.replaceMain() }
        h.vm.onAmountInput(BigDecimal("0.0000000002")); h.vm.onNextClick()
        assertTrue(observed); assertTrue(h.navigations.isEmpty())
    }
    @Test fun `reentrant claim on edited draft prevents forward navigation`() {
        val h = harness(); var claim: StakingPoolSharedState.Claim<StakingPoolManageFlowState>? = null
        observe(h) { claim = h.shared.manageState.tryClaim(h.shared.manageState.capture()) }
        h.vm.onAmountInput(BigDecimal("0.0000000002")); h.vm.onNextClick()
        assertNotNull(claim); assertTrue(h.shared.manageState.owns(requireNotNull(claim))); assertTrue(h.navigations.isEmpty())
    }
    @Test fun `reentrant dismissal prevents forward navigation after edit`() {
        val h = harness(); var observed = false
        observe(h) { observed = true; h.dismiss() }
        h.vm.onAmountInput(BigDecimal("0.0000000002")); h.vm.onNextClick()
        assertTrue(observed); assertTrue(h.navigations.isEmpty())
    }
    @Test fun `reentrant amount input withdraws old navigation and permits explicit latest continue`() {
        val h = harness(); var observed = false
        observe(h) {
            if (!observed) { observed = true; h.vm.onAmountInput(BigDecimal("0.0000000003")) }
        }
        h.vm.onAmountInput(BigDecimal("0.0000000002")); h.vm.onNextClick()
        assertTrue(observed); assertTrue(h.navigations.isEmpty())
        h.vm.onNextClick(); assertEquals(listOf("bondmore"), h.navigations)
        assertEquals(BigInteger.valueOf(3), h.shared.requireManageState.amountInPlanks)
    }

}
