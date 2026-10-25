package jp.co.soramitsu.staking.impl.presentation.staking.balance

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.Asset as ChainAsset
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.presentation.staking.balance.model.StakingBalanceModel
import jp.co.soramitsu.staking.impl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.wallet.api.presentation.model.AmountModel
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class StakingBalancePublicationTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val owned = mutableListOf<Fixture>()
    private class SourceCancelled(val marker: Any) : CancellationException("balance observer cancelled")
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() {
        try { settleOwned() } finally { Dispatchers.resetMain() }
    }

    private fun settleOwned() {
        owned.forEach { it.close() }
        runBlocking {
            withTimeout(5_000) {
                while (owned.any { !it.vm.viewModelScope.coroutineContext.job.isCompleted }) {
                    dispatcher.scheduler.runCurrent()
                    delay(1)
                }
            }
        }
    }

    private inner class Fixture(scope: TestScope, val source: () -> Flow<StakingBalanceModel>) {
        var subscriptions = 0
        val config = mock<ChainAsset> {
            on { chainId } doReturn "fixture"
            on { id } doReturn "native"
            on { precision } doReturn 2
            on { staking } doReturn ChainAsset.StakingType.RELAYCHAIN
        }
        val asset = Asset.createEmpty(config, 7, byteArrayOf(1), minSupportedVersion = null)
        val resources = mock<ResourceManager> { on { getString(any()) } doReturn "Unavailable" }
        val scenario = mock<StakingScenarioInteractor> {
            on { getStakingBalanceFlow(anyOrNull()) } doAnswer { subscriptions++; source() }
            onBlocking { currentUnbondingsFlow(anyOrNull()) } doReturn flowOf(emptyList())
            onBlocking { getRebondingUnbondings(anyOrNull()) } doReturn emptyList()
        }
        val interactor = mock<StakingInteractor> { on { currentAssetFlow() } doReturn flowOf(asset) }
        val vm = StakingBalanceViewModel(mock(), mock(), mock(), resources, interactor, scenario, SavedStateHandle())
        val publications = mutableListOf<StakingBalanceModel>()
        val observer = Observer<Boolean> { }
        init {
            owned += this
            vm.shouldBlockStakeMore.observeForever(observer)
            vm.shouldBlockUnstake.observeForever(observer)
            vm.redeemEnabledLiveData.observeForever(observer)
            scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) {
                vm.stakingBalanceModelFlow.collect { publications += it }
            }
        }
        fun close() {
            vm.shouldBlockStakeMore.removeObserver(observer)
            vm.shouldBlockUnstake.removeObserver(observer)
            vm.redeemEnabledLiveData.removeObserver(observer)
            vm.viewModelScope.cancel()
        }
        fun assertUnavailable() {
            val model = publications.last()
            listOf(model.staked, model.unstaking, model.redeemable).forEach {
                assertNull(it.amount); assertNull(it.fiat); assertEquals("Unavailable", it.token)
            }
            assertEquals(true, vm.shouldBlockStakeMore.value)
            assertEquals(true, vm.shouldBlockUnstake.value)
            assertEquals(false, vm.redeemEnabledLiveData.value)
            assertNotNull(vm.hideLoadingEvent.value)
        }
    }

    private fun healthy() = StakingBalanceModel(
        AmountModel(BigDecimal.TEN, "10 UNIT", "$20"),
        AmountModel(BigDecimal.ZERO, "0 UNIT", "$0"),
        AmountModel(BigDecimal.ONE, "1 UNIT", "$2")
    )

    @Test fun failureClearsAllAmountsAndActionsThenRefreshRecoversOneSubscription() = runTest(dispatcher) {
        val channel = Channel<StakingBalanceModel>(Channel.UNLIMITED)
        var healthyAgain = false
        val f = Fixture(this) { if (healthyAgain) flowOf(healthy()) else channel.receiveAsFlow() }
        channel.send(healthy()); runCurrent()
        assertEquals(false, f.vm.shouldBlockStakeMore.value)
        assertEquals(true, f.vm.redeemEnabledLiveData.value)
        channel.close(IllegalStateException("read failed")); runCurrent()
        f.assertUnavailable()
        healthyAgain = true; f.vm.refresh(); runCurrent()
        assertEquals(2, f.subscriptions)
        assertEquals(BigDecimal.TEN, f.publications.last().staked.amount)
        assertEquals(false, f.vm.shouldBlockStakeMore.value)
        assertEquals(false, f.vm.shouldBlockUnstake.value)
        assertEquals(true, f.vm.redeemEnabledLiveData.value)
    }

    @Test fun synchronousFactoryFailureIsAnUnavailablePublication() = runTest(dispatcher) {
        val f = Fixture(this) { throw IllegalStateException("factory failed") }
        runCurrent(); f.assertUnavailable(); assertEquals(1, f.subscriptions)
    }

    @Test fun streamFailureBeforeFirstValueStillFinishesLoadingWithUnavailable() = runTest(dispatcher) {
        val f = Fixture(this) { flow { throw IllegalStateException("initial read failed") } }
        runCurrent(); f.assertUnavailable(); assertEquals(1, f.publications.size)
    }

    @Test fun sourceCancellationTerminatesCurrentObserverWithoutInventingUnavailableSuccess() = runTest(dispatcher) {
        val release = CompletableDeferred<Unit>()
        val reason = SourceCancelled(Any())
        var cleaned = false
        val f = Fixture(this) { flow {
            try { emit(healthy()); release.await(); throw reason } finally { cleaned = true }
        } }
        runCurrent()
        val jobs = f.vm.viewModelScope.coroutineContext.job.children.toList()
        val completions = mutableListOf<Throwable?>()
        jobs.forEach { it.invokeOnCompletion { cause -> completions += cause } }
        release.complete(Unit); runCurrent()
        assertTrue(cleaned)
        assertEquals(1, completions.size)
        assertSame(reason, completions.single())
        assertEquals(1, f.publications.size)
        f.vm.refresh(); runCurrent()
        assertEquals(1, f.subscriptions)
    }

    @Test fun refreshCancellationOfPreviousObserverDoesNotCancelItsReplacement() = runTest(dispatcher) {
        val cleanup = CompletableDeferred<Unit>()
        var attempt = 0
        val f = Fixture(this) {
            if (++attempt == 1) flow {
                try { emit(healthy()); awaitCancellation() }
                finally { withContext(NonCancellable) { cleanup.await() } }
            } else flowOf(healthy())
        }
        try {
            runCurrent(); f.vm.refresh(); runCurrent()
            assertEquals(1, f.subscriptions)
            cleanup.complete(Unit); runCurrent()
            assertEquals(2, f.subscriptions)
            assertEquals(2, f.publications.size)
            assertEquals(BigDecimal.TEN, f.publications.last().staked.amount)
        } finally { cleanup.complete(Unit) }
    }

    @Test fun callerCancellationCleansSourceAndRetainsItsExactReason() = runTest(dispatcher) {
        val reason = SourceCancelled(Any())
        var observedCancellation: Throwable? = null
        var cleaned = false
        val f = Fixture(this) { flow {
            try { emit(healthy()); awaitCancellation() }
            catch (cancelled: CancellationException) { observedCancellation = cancelled; throw cancelled }
            finally { cleaned = true }
        } }
        runCurrent()
        f.vm.viewModelScope.cancel(reason); runCurrent()
        assertTrue(cleaned)
        assertSame(reason, observedCancellation)
        assertEquals(1, f.publications.size)
    }

    @Test fun cleanupSettlesBackgroundChildrenBeforeTheMainDispatcherCanBeReset() = runTest(dispatcher) {
        var cleaned = false
        val f = Fixture(this) { flow {
            try { emit(healthy()); awaitCancellation() }
            finally {
                withContext(NonCancellable + Dispatchers.Default) {
                    delay(25)
                    withContext(Dispatchers.Main) { cleaned = true }
                }
            }
        } }
        runCurrent()
        settleOwned()
        assertTrue(cleaned)
        assertTrue(f.vm.viewModelScope.coroutineContext.job.isCompleted)
    }
}
