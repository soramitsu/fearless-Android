package jp.co.soramitsu.staking.impl.presentation.common

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.staking.api.domain.model.RoleInPool
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.mockito.kotlin.*
import java.math.BigInteger

@OptIn(ExperimentalCoroutinesApi::class)
class PoolManageLiveValueTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val owned = mutableListOf<PoolManageActionHarness>()
    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() {
        runBlocking { withTimeout(5_000) { owned.forEach { it.vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } } }
        Dispatchers.resetMain()
    }
    private fun harness(action: String) = PoolManageActionHarness(action).also(owned::add)
    @Test fun `unavailable pool withdraws displayed values notifications and action authority`() = runTest {
        val h = harness("claim"); assertNotNull(h.vm.state.value.total)
        h.withdrawPool()
        assertNull(h.vm.state.value.total); assertNull(h.vm.state.value.claimNotification)
        assertNull(h.vm.state.value.redeemNotification); assertNull(h.vm.state.value.noValidatorsNotification)
        assertNull(h.vm.state.value.poolInfo.value)
        h.act(); assertTrue(h.navigations.isEmpty())
    }
    @Test fun `healthy pool after unavailable state restores displayed body and action`() = runTest {
        val h = harness("claim"); h.withdrawPool(); h.deliverPool()
        assertNotNull(h.vm.state.value.total); assertNotNull(h.vm.state.value.claimNotification)
        h.act(); assertEquals(listOf("claim"), h.navigations)
    }
    @Test fun `same pool update refreshes displayed amounts and role before member action`() = runTest {
        val h = harness("claim")
        h.deliverSamePoolUpdate(h.ownPool.copy(myStakeInPlanks = BigInteger.valueOf(90), redeemable = BigInteger.valueOf(4),
            pendingRewards = BigInteger.valueOf(3), depositor = ByteArray(32) { 9 }))
        assertEquals(BigInteger.valueOf(3), h.shared.requireManageState.claimableInPlanks)
        assertEquals(BigInteger.valueOf(4), h.shared.requireManageState.redeemInPlanks)
        assertEquals(BigInteger.valueOf(90), h.shared.requireManageState.stakedInPlanks)
        assertEquals(RoleInPool.Root, h.shared.requireManageState.userRole)
        h.act(); assertEquals(listOf("claim"), h.navigations)
        assertEquals(BigInteger.valueOf(3), h.shared.requireManageState.claimableInPlanks)
    }
    @Test fun `same pool role change withdraws held nominations result`() = runTest {
        val h = harness("nominations"); val gate = CompletableDeferred<List<ByteArray>>()
        whenever(h.pool.getValidatorsIds(any(), any())).doSuspendableAnswer { gate.await() }
        h.act(); h.deliverSamePoolUpdate(h.ownPool.copy(root = null, nominator = null)); gate.complete(emptyList())
        assertTrue(h.navigations.isEmpty()); assertNull(h.shared.selectedValidatorsState.get())
    }
    @Test fun `returning info action refreshes same pool cache metadata`() = runTest {
        val h = harness("info"); h.act(); assertEquals("Public fixture pool", h.shared.poolsCache.value[7]?.name)
        h.deliverSamePoolUpdate(h.ownPool.copy(name = "Updated public pool", totalStakedInPlanks = BigInteger.valueOf(999), root = null))
        h.vm.onPaused(); h.vm.onResumed(); h.act()
        assertEquals(listOf("info", "info"), h.navigations)
        assertEquals("Updated public pool", h.shared.poolsCache.value[7]?.name)
        assertEquals(BigInteger.valueOf(999), h.shared.poolsCache.value[7]?.stakedInPlanks)
        assertNull(h.shared.poolsCache.value[7]?.root)
    }
    @Test fun `equal public account byte content does not replace visible draft`() = runTest {
        val h = harness("claim"); val original = h.shared.manageState.capture()
        val identical = h.ownPool.copy(depositor = h.ownPool.depositor.copyOf(), root = h.ownPool.root?.copyOf(),
            nominator = h.ownPool.nominator?.copyOf(), stateToggler = h.ownPool.stateToggler?.copyOf())
        assertEquals(h.ownPool, identical); assertEquals(h.ownPool.hashCode(), identical.hashCode())
        h.deliverSamePoolUpdate(identical); assertTrue(h.shared.manageState.isCurrent(original))
        h.act(); assertEquals(listOf("claim"), h.navigations)
    }
}
