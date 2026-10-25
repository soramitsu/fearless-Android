package jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios

import java.io.IOException
import java.math.BigInteger
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.presentation.staking.main.model.StakingNetworkInfoModel
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*
import jp.co.soramitsu.core.models.Asset as ChainAsset

/** Actual pool scenario network-info collection with local held/failing lookups only. */
@OptIn(ExperimentalCoroutinesApi::class)
class PoolNetworkInfoLifetimeTest {
    @Test fun `healthy info uses the current network denomination`() = runTest {
        val h = Harness(this); h.start()
        val info = h.loaded()
        assertTrue(info.minToJoin.contains("AAA")); assertTrue(info.minToCreate.contains("AAA"))
        assertEquals("3", info.existingPools); assertEquals("4", info.possiblePools)
        assertEquals("5", info.maxMembersInPool); assertEquals("6", info.maxPoolsMembers)
        assertEquals(listOf("a"), h.requests); assertTrue(h.errors.isEmpty())
    }

    @Test fun `unsupported asset withdraws previously loaded network info`() = runTest {
        val h = Harness(this); h.start(); h.loaded()
        h.assets.emit(h.asset("b", supported = false)); runCurrent()
        assertTrue(h.values.last() is LoadingState.Loading<*>)
        assertEquals(listOf("a"), h.requests)
    }

    @Test fun `new network withdraws old values while its first lookup is held`() = runTest {
        val h = Harness(this); h.start(); h.loaded(); h.heldChain = "b"
        h.assets.emit(h.asset("b")); runCurrent()
        assertTrue(h.values.last() is LoadingState.Loading<*>)
        assertEquals(listOf("a", "b"), h.requests)
        h.gate.complete(Unit); runCurrent(); assertTrue(h.loaded().minToJoin.contains("BBB"))
    }

    @Test fun `unsupported selection cancels held values before they can publish`() = runTest {
        val h = Harness(this); h.heldChain = "a"; h.start()
        assertEquals(listOf("a"), h.requests)
        h.assets.emit(h.asset("b", supported = false)); runCurrent()
        h.gate.complete(Unit); runCurrent()
        assertTrue(h.values.none { it is LoadingState.Loaded<*> }); assertTrue(h.values.last() is LoadingState.Loading<*>)
        assertTrue(h.errors.isEmpty())
    }

    @Test fun `new network starts before obsolete held lookup completes`() = runTest {
        val h = Harness(this); h.heldChain = "a"; h.start()
        assertEquals(listOf("a"), h.requests)
        h.assets.emit(h.asset("b")); runCurrent()
        assertEquals(listOf("a", "b"), h.requests)
        assertTrue(h.loaded().minToJoin.contains("BBB"))
        h.gate.complete(Unit); runCurrent()
        assertTrue(h.values.filterIsInstance<LoadingState.Loaded<StakingNetworkInfoModel>>().all {
            (it.data as StakingNetworkInfoModel.Pool).minToJoin.contains("BBB")
        })
    }

    @Test fun `lookup failure withdraws old info and later healthy asset recovers`() = runTest {
        val h = Harness(this); h.start(); h.loaded(); h.failure = IOException("Local unavailable lookup")
        h.assets.emit(h.asset("b")); runCurrent()
        assertTrue(h.errors.isEmpty()); assertTrue(h.values.last() is LoadingState.Loading<*>)
        h.failure = null; h.assets.emit(h.asset("b")); runCurrent()
        assertTrue(h.loaded().minToJoin.contains("BBB"))
    }

    @Test fun `missing lookup data cannot retain old network values`() = runTest {
        val h = Harness(this); h.start(); h.loaded(); h.failure = NullPointerException("Local missing metadata")
        h.assets.emit(h.asset("b")); runCurrent()
        assertTrue(h.errors.isEmpty()); assertTrue(h.values.last() is LoadingState.Loading<*>)
        h.failure = null; h.assets.emit(h.asset("b")); runCurrent()
        assertTrue(h.loaded().minToJoin.contains("BBB"))
    }

    @Test fun `collector cancellation prevents held lookup publication`() = runTest {
        val h = Harness(this); h.heldChain = "a"; val job = h.start()
        assertEquals(listOf("a"), h.requests)
        job.cancelAndJoin(); h.gate.complete(Unit); runCurrent()
        assertTrue(h.values.none { it is LoadingState.Loaded<*> }); assertTrue(h.errors.isEmpty())
    }

    private class Harness(private val scope: TestScope) {
        val assets = MutableSharedFlow<Asset>(replay = 1).apply { tryEmit(asset("a")) }
        val values = mutableListOf<LoadingState<StakingNetworkInfoModel>>()
        val errors = mutableListOf<Throwable>()
        val requests = mutableListOf<String>()
        val gate = CompletableDeferred<Unit>()
        var heldChain: String? = null
        var failure: Exception? = null
        private val pools = mock<StakingPoolInteractor> { on { stakingStateFlow() } doReturn emptyFlow() }
        private val interactor = mock<StakingInteractor> { on { currentAssetFlow() } doReturn assets }
        private val base = mock<BaseStakingViewModel> {
            on { stakingStateScope } doReturn scope.backgroundScope
            on { enteredAmountEvent } doReturn emptyFlow()
        }
        suspend fun start(): kotlinx.coroutines.Job {
            whenever(pools.getMinToJoinPool(any())).doSuspendableAnswer { call ->
                val chain = call.getArgument<String>(0); requests += chain
                if (heldChain == chain) gate.await()
                failure?.let { throw it }
                BigInteger.valueOf(100)
            }
            whenever(pools.getMinToCreate(any())).thenReturn(BigInteger.valueOf(200))
            whenever(pools.getExistingPools(any())).thenReturn(BigInteger.valueOf(3))
            whenever(pools.getPossiblePools(any())).thenReturn(BigInteger.valueOf(4))
            whenever(pools.getMaxMembersInPool(any())).thenReturn(BigInteger.valueOf(5))
            whenever(pools.getMaxPoolsMembers(any())).thenReturn(BigInteger.valueOf(6))
            val vm = StakingPoolViewModel(pools, interactor, mock(), mock(), base)
            return scope.backgroundScope.launch {
                vm.networkInfo().catch { errors += it }.collect { values += it }
            }.also { scope.runCurrent() }
        }
        fun loaded() = (values.last() as LoadingState.Loaded<*>).data as StakingNetworkInfoModel.Pool
        fun asset(chain: String, supported: Boolean = true): Asset {
            val symbol = if (chain == "a") "AAA" else "BBB"
            val configuration = mock<ChainAsset> {
                on { chainId } doReturn chain
                on { id } doReturn "native"
                on { this.symbol } doReturn symbol
                on { precision } doReturn 2
                on { supportStakingPool } doReturn supported
                on { iconUrl } doReturn ""
            }
            return Asset.createEmpty(configuration, 1, ByteArray(32) { 1 }, minSupportedVersion = null)
        }
    }
}
