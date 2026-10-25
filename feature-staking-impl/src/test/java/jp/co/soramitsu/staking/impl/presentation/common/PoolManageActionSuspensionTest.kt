package jp.co.soramitsu.staking.impl.presentation.common

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch
import kotlinx.coroutines.*
import jp.co.soramitsu.staking.api.domain.model.toPoolInfo
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.mockito.kotlin.*
import java.io.IOException
import java.math.BigInteger
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class PoolManageActionSuspensionTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val owned = mutableListOf<PoolManageActionHarness>()
    private val inline = object : CoroutineDispatcher() {
        override fun isDispatchNeeded(context: CoroutineContext) = true
        override fun dispatch(context: CoroutineContext, block: Runnable) = block.run()
    }
    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() {
        runBlocking { withTimeout(5_000) { owned.forEach { it.vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } } }
        Dispatchers.resetMain()
    }
    private fun harness() = PoolManageActionHarness("nominations").also(owned::add)
    private suspend fun hold(h: PoolManageActionHarness): CompletableDeferred<List<ByteArray>> {
        val gate = CompletableDeferred<List<ByteArray>>()
        whenever(h.pool.getValidatorsIds(any(), any())).doSuspendableAnswer { gate.await() }
        return gate
    }
    private fun assertNoPublication(h: PoolManageActionHarness) {
        assertTrue(h.navigations.isEmpty()); assertNull(h.shared.selectedValidatorsState.get())
    }
    @Test fun `current nominations action uses only its original pool lookup`() = runTest {
        val h = harness(); val gate = hold(h); verify(h.pool, never()).getValidatorsIds(any(), any())
        h.act(); assertNoPublication(h); val ids = listOf(ByteArray(32) { 2 }); gate.complete(ids)
        assertEquals(listOf("nominations"), h.navigations); assertSame(ids, h.shared.requireSelectedValidatorsState.selectedValidators)
        verify(h.pool).getValidatorsIds(h.chain, BigInteger.valueOf(7))
    }
    @Test fun `Back during nominations lookup suppresses result`() = runTest {
        val h = harness(); val gate = hold(h); h.act(); h.vm.onBackClick(); gate.complete(emptyList()); assertNoPublication(h)
    }
    @Test fun `pause during nominations lookup suppresses result`() = runTest {
        val h = harness(); val gate = hold(h); h.act(); h.vm.onPaused(); gate.complete(emptyList()); assertNoPublication(h)
    }
    @Test fun `dismiss during nominations lookup suppresses result`() = runTest {
        val h = harness(); val gate = hold(h); h.act(); h.vm.onDismissed(); gate.complete(emptyList()); assertNoPublication(h)
    }
    @Test fun `main replacement during nominations lookup suppresses result`() = runTest {
        val h = harness(); val gate = hold(h); h.act(); h.replaceMain(); gate.complete(emptyList()); assertNoPublication(h)
    }
    @Test fun `wallet round trip during nominations lookup suppresses result`() = runTest {
        val h = harness(); val gate = hold(h); h.act(); repeat(2) { WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange() }
        gate.complete(emptyList()); assertNoPublication(h)
    }
    @Test fun `pool replacement during nominations lookup suppresses result`() = runTest {
        val h = harness(); val gate = hold(h); h.act(); h.deliverUpdatedPool(); gate.complete(emptyList()); assertNoPublication(h)
    }
    @Test fun `clear during nominations lookup suppresses result`() = runTest {
        val h = harness(); val gate = hold(h); h.act(); ViewModelStore().also { it.put("manage", h.vm); it.clear() }
        gate.complete(emptyList()); assertNoPublication(h)
    }
    @Test fun `newer selected validators publication survives held result`() = runTest {
        val h = harness(); val gate = hold(h); h.act()
        val newer = SelectedValidatorsFlowState(poolId = BigInteger.valueOf(99)); h.shared.selectedValidatorsState.set(newer)
        gate.complete(emptyList()); assertTrue(h.navigations.isEmpty()); assertSame(newer, h.shared.selectedValidatorsState.get())
    }
    @Test fun `resumed new action owns response while older lookup finishes`() = runTest {
        val h = harness(); val old = CompletableDeferred<List<ByteArray>>(); val current = CompletableDeferred<List<ByteArray>>(); var calls = 0
        whenever(h.pool.getValidatorsIds(any(), any())).doSuspendableAnswer { if (++calls == 1) old.await() else current.await() }
        h.act(); h.vm.onPaused(); h.vm.onResumed(); h.act(); assertEquals(2, calls)
        old.complete(listOf(ByteArray(32) { 1 })); assertNoPublication(h)
        val ids = listOf(ByteArray(32) { 2 }); current.complete(ids)
        assertEquals(listOf("nominations"), h.navigations); assertSame(ids, h.shared.requireSelectedValidatorsState.selectedValidators)
    }
    @Test fun `current nominations error is presented without navigation and can retry`() = runTest {
        val h = harness(); val gate = hold(h); h.act(); gate.completeExceptionally(IOException("public fixture unavailable"))
        assertNoPublication(h); assertNotNull(h.vm.errorLiveData.value)
        whenever(h.pool.getValidatorsIds(any(), any())).doSuspendableAnswer { emptyList() }
        h.act(); assertEquals(listOf("nominations"), h.navigations)
    }
    @Test fun `departed nominations error cannot present on replacement screen`() = runTest {
        val h = harness(); val gate = hold(h); h.act(); h.vm.onBackClick(); gate.completeExceptionally(IOException("late public fixture"))
        assertNoPublication(h); assertNull(h.vm.errorLiveData.value)
    }
    @Test fun `synchronous publication main replacement prevents navigation`() = runTest {
        val h = harness(); var observed = false
        val observer = launch(inline, start = CoroutineStart.UNDISPATCHED) {
            h.shared.selectedValidatorsState.process.filterNotNull().first(); assertTrue("Observer must run before navigation", h.navigations.isEmpty()); observed = true; h.replaceMain()
        }
        h.act(); observer.join(); assertTrue(observed); assertTrue(h.navigations.isEmpty())
    }
    @Test fun `synchronous publication pause prevents navigation`() = runTest {
        val h = harness(); var observed = false
        val observer = launch(inline, start = CoroutineStart.UNDISPATCHED) {
            h.shared.selectedValidatorsState.process.filterNotNull().first(); assertTrue("Observer must run before navigation", h.navigations.isEmpty()); observed = true; h.vm.onPaused()
        }
        h.act(); observer.join(); assertTrue(observed); assertTrue(h.navigations.isEmpty())
    }
    @Test fun `synchronous publication cannot admit duplicate navigation`() = runTest {
        val h = harness(); var observed = false
        val observer = launch(inline, start = CoroutineStart.UNDISPATCHED) {
            h.shared.selectedValidatorsState.process.filterNotNull().first(); assertTrue("Observer must run before navigation", h.navigations.isEmpty()); observed = true; h.act()
        }
        h.act(); observer.join(); assertTrue(observed); assertEquals(listOf("nominations"), h.navigations)
    }
    @Test fun `replaced pool suppresses older nominations failure`() = runTest {
        val h = harness(); val gate = hold(h); h.act(); h.deliverUpdatedPool()
        gate.completeExceptionally(IOException("old pool lookup")); assertNoPublication(h); assertNull(h.vm.errorLiveData.value)
    }
    @Test fun `synchronous cache replacement suppresses old info navigation`() = runTest {
        val h = PoolManageActionHarness("info").also(owned::add); var observed = false
        val newer = mapOf(99 to h.ownPool.copy(poolId = BigInteger.valueOf(99)).toPoolInfo())
        val observer = launch(inline, start = CoroutineStart.UNDISPATCHED) {
            h.shared.poolsCache.filter { it.isNotEmpty() }.first(); assertTrue("Observer must run before navigation", h.navigations.isEmpty()); observed = true; h.shared.poolsCache.value = newer
        }
        h.act(); observer.join(); assertTrue(observed); assertTrue(h.navigations.isEmpty()); assertSame(newer, h.shared.poolsCache.value)
    }

}
