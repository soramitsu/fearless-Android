package jp.co.soramitsu.staking.impl.presentation.common

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.mockito.kotlin.*
import java.io.IOException
import java.math.BigInteger
import jp.co.soramitsu.staking.api.domain.model.RoleInPool

@OptIn(ExperimentalCoroutinesApi::class)
class PoolUnstakePreparationSuspensionTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val owned = mutableListOf<PoolManagePreparationHarness>()
    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() {
        owned.forEach { it.minimumResume.complete(Unit) }
        runBlocking { withTimeout(5_000) { owned.flatMap { it.vms }.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } } }
        Dispatchers.resetMain()
    }
    private fun harness() = PoolManagePreparationHarness("unbond", holdMinimum = true).also(owned::add)
    @Test fun `Back while minimum is suspended prevents navigation`() {
        val h = harness(); h.vm.onNextClick(); assertTrue(h.minimumEntered.isCompleted)
        h.back(); h.minimumResume.complete(Unit); assertTrue(h.navigations.isEmpty())
    }
    @Test fun `replacement draft during minimum is preserved`() {
        val h = harness(); h.vm.onNextClick(); assertTrue(h.minimumEntered.isCompleted)
        h.replaceManage(); val replacement = h.shared.requireManageState
        h.minimumResume.complete(Unit); assertTrue(h.navigations.isEmpty()); assertSame(replacement, h.shared.manageState.get())
    }
    @Test fun `clearing while minimum ignores cancellation prevents navigation`() {
        val h = harness(); h.vm.onNextClick(); assertTrue(h.minimumEntered.isCompleted)
        ViewModelStore().also { it.put("unbond", h.vm); it.clear() }
        h.minimumResume.complete(Unit); assertTrue(h.navigations.isEmpty())
    }
    @Test fun `resuming while old minimum is held invalidates that callback`() {
        val h = harness(); h.vm.onNextClick(); assertTrue(h.minimumEntered.isCompleted)
        h.resume(); h.minimumResume.complete(Unit); assertTrue(h.navigations.isEmpty())
        h.vm.onNextClick(); assertEquals(listOf("unbond"), h.navigations)
    }
    @Test fun `duplicate continue while minimum is held performs one lookup`() = runTest {
        val h = harness(); h.vm.onNextClick(); assertTrue(h.minimumEntered.isCompleted); h.vm.onNextClick()
        verify(h.pool, times(1)).getMinToCreate(any()); h.minimumResume.complete(Unit)
        assertEquals(listOf("unbond"), h.navigations)
    }
    @Test fun `minimum network failure is presented without an uncaught coroutine error`() = runTest {
        val h = harness(); whenever(h.pool.getMinToCreate(any())).doSuspendableAnswer { throw IOException("local minimum unavailable") }
        h.vm.onNextClick(); verify(h.router, times(1)).openAlert(any()); assertTrue(h.navigations.isEmpty())
    }    @Test fun `late minimum failure after Back cannot alert or navigate`() = runTest {
        val h = harness(); h.minimumFailure = IOException("late local minimum failure")
        h.vm.onNextClick(); assertTrue(h.minimumEntered.isCompleted); h.back(); h.minimumResume.complete(Unit)
        verify(h.router, never()).openAlert(any()); assertTrue(h.navigations.isEmpty())
    }
    @Test fun `depositor alert result is owned and consumed once`() {
        val h = PoolManagePreparationHarness("unbond", holdMinimum = true, role = RoleInPool.Depositor).also(owned::add)
        h.minimumValue = BigInteger.TEN
        assertTrue(h.alertResults.tryEmit(Result.success(Unit))); verify(h.router, never()).openWebViewer(any(), any())
        h.vm.onNextClick(); h.minimumResume.complete(Unit)
        verify(h.router, times(1)).openPoolFullUnstakeDepositorAlertFragment(any())
        h.resume(); assertTrue(h.alertResults.tryEmit(Result.success(Unit))); assertTrue(h.alertResults.tryEmit(Result.success(Unit)))
        verify(h.router, times(1)).openWebViewer(any(), any()); assertTrue(h.navigations.isEmpty())
    }
    @Test fun `departed depositor alert result cannot open a web viewer`() {
        val h = PoolManagePreparationHarness("unbond", holdMinimum = true, role = RoleInPool.Depositor).also(owned::add)
        h.minimumValue = BigInteger.TEN; h.vm.onNextClick(); h.minimumResume.complete(Unit)
        verify(h.router, times(1)).openPoolFullUnstakeDepositorAlertFragment(any())
        h.dismiss(); assertTrue(h.alertResults.tryEmit(Result.success(Unit)))
        verify(h.router, never()).openWebViewer(any(), any())
    }

    @Test fun `changing amount during minimum invalidates the old continue`() {
        val h = harness(); h.vm.onNextClick(); assertTrue(h.minimumEntered.isCompleted)
        h.vm.onAmountInput(java.math.BigDecimal("0.0000000002")); h.minimumResume.complete(Unit)
        assertTrue(h.navigations.isEmpty()); assertEquals(BigInteger.ONE, h.shared.requireManageState.amountInPlanks)
    }
    @Test fun `repeating same amount preserves the current continue`() {
        val h = harness(); h.vm.onNextClick(); assertTrue(h.minimumEntered.isCompleted)
        h.vm.onAmountInput(java.math.BigDecimal("0.0000000001")); h.minimumResume.complete(Unit)
        assertEquals(listOf("unbond"), h.navigations)
    }
    @Test fun `new amount continue supersedes held older lookup`() {
        val h = harness(); h.vm.onNextClick(); assertTrue(h.minimumEntered.isCompleted)
        h.vm.onAmountInput(java.math.BigDecimal("0.0000000002")); h.vm.onNextClick(); h.minimumResume.complete(Unit)
        assertEquals(listOf("unbond"), h.navigations); assertEquals(BigInteger.TWO, h.shared.requireManageState.amountInPlanks)
    }
    @Test fun `quick amount during minimum invalidates the old continue`() = runTest {
        val h = harness()
        whenever(h.quick.calculateStakingQuickInputs(any(), any(), any(), any())).thenReturn(mapOf(1.0 to java.math.BigDecimal("0.0000000002")))
        val vm = h.newVm(); vm.onNextClick(); assertTrue(h.minimumEntered.isCompleted)
        vm.onQuickAmountInput(1.0); h.minimumResume.complete(Unit)
        assertTrue(h.navigations.isEmpty()); assertEquals(BigInteger.ONE, h.shared.requireManageState.amountInPlanks)
    }

}
