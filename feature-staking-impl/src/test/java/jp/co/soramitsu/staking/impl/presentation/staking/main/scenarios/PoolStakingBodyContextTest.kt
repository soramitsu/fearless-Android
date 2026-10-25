package jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios

import java.io.IOException
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.domain.model.NominationPoolState
import jp.co.soramitsu.staking.api.domain.model.OwnPool
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.rewards.PeriodReturns
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculator
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.staking.impl.presentation.staking.main.StakingViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.StakeInfoViewState
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import jp.co.soramitsu.core.models.Asset as ChainAsset

/** Actual pool scenario body with local assets, calculator and selection observations. */
@OptIn(ExperimentalCoroutinesApi::class)
class PoolStakingBodyContextTest {
    @Test fun `healthy current member renders its own asset denomination`() = runTest {
        val h = Harness(this); h.start()
        val member = h.values.last() as StakingViewState.Pool.PoolMember
        assertTrue((member.stakeInfoViewState as StakeInfoViewState.PoolStakeInfoViewState).staked.value!!.contains("AAA"))
        assertTrue(h.errors.isEmpty())
    }

    @Test fun `healthy current nonmember retains amount and asset input`() = runTest {
        val h = Harness(this); h.state.value = StakingState.Pool.None(h.a, h.account); h.start()
        val welcome = h.values.last() as StakingViewState.Pool.Welcome
        assertEquals("AAA", welcome.estimatedEarnings.amountInputViewState.tokenName)
        assertEquals(BigDecimal.TEN, welcome.estimatedEarnings.amountInputViewState.tokenAmount)
        assertTrue(h.errors.isEmpty())
    }

    @Test fun `different chain asset withdraws current member body`() = runTest {
        val h = Harness(this); h.start(); h.assets.emit(h.asset(h.b)); runCurrent()
        assertNull(h.values.last())
    }

    @Test fun `different public account asset withdraws current member body`() = runTest {
        val h = Harness(this); h.start(); h.assets.emit(h.asset(h.a, ByteArray(32) { 2 })); runCurrent()
        assertNull(h.values.last())
    }

    @Test fun `unsupported current asset withdraws old member body`() = runTest {
        val h = Harness(this); h.start(); h.assets.emit(h.asset(h.a, supports = false)); runCurrent()
        assertNull(h.values.last())
    }

    @Test fun `new member is withheld until its matching asset arrives`() = runTest {
        val h = Harness(this); h.start(); h.state.value = StakingState.Pool.Member(h.b, h.account, h.pool); runCurrent()
        assertNull(h.values.last())
        h.assets.emit(h.asset(h.b)); runCurrent()
        val member = h.values.last() as StakingViewState.Pool.PoolMember
        assertTrue((member.stakeInfoViewState as StakeInfoViewState.PoolStakeInfoViewState).staked.value!!.contains("BBB"))
    }

    @Test fun `unavailable pool state removes prior member body`() = runTest {
        val h = Harness(this); h.start(); h.state.value = null; runCurrent()
        assertNull(h.values.last())
        assertTrue(h.errors.isEmpty())
    }

    @Test fun `unavailable pool state cancels a held asset lookup`() = runTest {
        val h = Harness(this); h.start(); h.assets.resetReplayCache()
        h.state.value = StakingState.Pool.Member(h.a, h.account, h.pool.copy(myStakeInPlanks = BigInteger.valueOf(200)))
        runCurrent(); h.state.value = null; runCurrent()
        assertNull(h.values.last())
        assertTrue(h.errors.isEmpty())
    }

    @Test fun `nonmember on new chain is withheld while old asset remains`() = runTest {
        val h = Harness(this); h.start(); h.state.value = StakingState.Pool.None(h.b, h.account); runCurrent()
        assertNull(h.values.last())
    }

    @Test fun `reward lookup failure keeps healthy nonmember available without uncaught error`() = runTest {
        val h = Harness(this); h.failRewards = true; h.state.value = StakingState.Pool.None(h.a, h.account); h.start()
        assertTrue(h.values.last() is StakingViewState.Pool.Welcome)
        assertTrue(h.errors.isEmpty())
    }

    @Test fun `new chain starts its reward lookup while old quote remains held`() = runTest {
        val h = Harness(this); h.heldChain = h.a.id; h.state.value = StakingState.Pool.None(h.a, h.account); h.start()
        assertEquals(listOf(h.a.id), h.rewardRequests)
        h.assets.emit(h.asset(h.b)); h.state.value = StakingState.Pool.None(h.b, h.account); runCurrent()
        assertTrue(h.rewardRequests.contains(h.b.id))
        h.rewardGate.complete(Unit); runCurrent()
        val welcome = h.values.last() as StakingViewState.Pool.Welcome
        assertEquals("BBB", welcome.estimatedEarnings.amountInputViewState.tokenName)
        assertTrue(h.errors.isEmpty())
    }

    @Test fun `member presentation does not require or start a reward calculator`() = runTest {
        val h = Harness(this); h.start()
        assertTrue(h.values.last() is StakingViewState.Pool.PoolMember)
        assertTrue(h.rewardRequests.isEmpty())
    }

    @Test fun `unsupported asset cannot start reward dialog lookup and supported asset recovers`() = runTest {
        val h = Harness(this); h.start(); h.assets.emit(h.asset(h.a, supports = false)); runCurrent()
        var completed = false
        backgroundScope.launch {
            h.vm.rewardEstimationPayload()
            completed = true
        }
        runCurrent()
        assertTrue(h.rewardRequests.isEmpty())
        assertTrue(!completed)
        h.assets.emit(h.asset(h.a)); runCurrent()
        assertEquals(listOf(h.a.id), h.rewardRequests)
        assertTrue(completed)
        assertTrue(h.errors.isEmpty())
    }

    private class Harness(private val scope: TestScope) {
        val account = ByteArray(32) { 1 }
        val a = chain("local-body-a")
        val b = chain("local-body-b")
        val pool = OwnPool(BigInteger.ONE, "Local public pool", BigInteger.valueOf(100), BigInteger.valueOf(1000),
            BigInteger.ZERO, NominationPoolState.Open, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO,
            BigInteger.ONE, account, account, account, account)
        val state = MutableStateFlow<StakingState?>(StakingState.Pool.Member(a, account, pool))
        val assets = MutableSharedFlow<Asset>(replay = 1).apply { tryEmit(asset(a)) }
        val values = mutableListOf<StakingViewState?>()
        val errors = mutableListOf<Throwable>()
        lateinit var vm: StakingPoolViewModel
        val rewardRequests = mutableListOf<String>()
        val rewardGate = CompletableDeferred<Unit>()
        var heldChain: String? = null
        var failRewards = false
        private val owned = CoroutineScope(scope.backgroundScope.coroutineContext +
            SupervisorJob(scope.backgroundScope.coroutineContext[Job]) + CoroutineExceptionHandler { _, e -> errors += e })
        private val resource = mock<ResourceManager> {
            on { getString(any()) } doReturn "Public label"
            on { getString(any(), any<String>()) } doAnswer { "Balance " + it.getArgument<Any>(1) }
        }
        private val calculator = mock<RewardCalculator> {
            onBlocking { calculateMaxAPY(any()) } doReturn BigDecimal.ONE
            onBlocking { calculateAvgAPY() } doReturn BigDecimal.ONE
            onBlocking { calculateReturns(any<BigDecimal>(), any(), any(), any<String>()) } doReturn
                PeriodReturns(BigDecimal.ONE, BigDecimal.ONE)
        }
        private val factory = mock<RewardCalculatorFactory>().also {
            scope.launch {
                whenever(it.create(any())).doSuspendableAnswer { call ->
                    val asset = call.getArgument<ChainAsset>(0); rewardRequests += asset.chainId
                    if (failRewards) throw IOException("Local reward lookup failure")
                    if (heldChain == asset.chainId) rewardGate.await()
                    calculator
                }
            }
        }
        private val interactor = mock<StakingInteractor> { on { currentAssetFlow() } doReturn assets }
        private val pools = mock<StakingPoolInteractor> { on { stakingStateFlow() } doReturn state }
        private val base = mock<BaseStakingViewModel> {
            on { stakingStateScope } doReturn owned
            on { enteredAmountEvent } doReturn emptyFlow()
        }
        suspend fun start() {
            scope.runCurrent()
            vm = StakingPoolViewModel(pools, interactor, resource, factory, base)
            scope.backgroundScope.launch { vm.getStakingViewStateFlow().catch { errors += it }.collect { values += it } }
            scope.runCurrent()
        }
        fun asset(chain: Chain, id: ByteArray = account, supports: Boolean = true): Asset {
            val selectedChainId = chain.id
            val symbol = if (selectedChainId == a.id) "AAA" else "BBB"
            val config = mock<ChainAsset> {
                on { chainId } doReturn selectedChainId
                on { this.id } doReturn "native"
                on { this.symbol } doReturn symbol
                on { precision } doReturn 2
                on { supportStakingPool } doReturn supports
                on { iconUrl } doReturn ""
            }
            return Asset.createEmpty(config, 1, id, minSupportedVersion = null)
        }
        private fun chain(id: String) = mock<Chain> {
            on { this.id } doReturn id
            on { addressPrefix } doReturn 42
            on { ecosystem } doReturn Ecosystem.Substrate
        }
    }
}
