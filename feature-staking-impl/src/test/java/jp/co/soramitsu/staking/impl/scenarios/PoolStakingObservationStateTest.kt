package jp.co.soramitsu.staking.impl.scenarios

import java.io.IOException
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.domain.model.Nominations
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.data.model.BondedPool
import jp.co.soramitsu.staking.impl.data.model.BondedPoolState
import jp.co.soramitsu.staking.impl.data.model.PoolMember
import jp.co.soramitsu.staking.impl.data.repository.StakingPoolDataSource
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*
import org.mockito.stubbing.OngoingStubbing

/** Actual lower interactor assembly; local data-source emissions and held RPC completions only. */
@OptIn(ExperimentalCoroutinesApi::class)
class PoolStakingObservationStateTest {
    @Test fun `confirmed absent membership permits current nonmember state`() = runTest {
        val h = Harness(this); h.start(); h.member(null)
        assertTrue(h.values.last() is StakingState.Pool.None)
        assertTrue(h.errors.isEmpty())
    }

    @Test fun `healthy pool preserves member values and subsequent updates`() = runTest {
        val h = Harness(this); h.start(); h.healthy(); h.pool(1, h.bonded(200))
        val state = h.values.last() as StakingState.Pool.Member
        assertEquals(BigInteger.valueOf(200), state.pool.totalStakedInPlanks)
        assertEquals(BigInteger.valueOf(7), state.pool.pendingRewards)
        assertEquals(BigInteger.TEN, state.pool.myStakeInPlanks)
        assertEquals("Local pool 1", state.pool.name)
        assertTrue(h.errors.isEmpty())
    }

    @Test fun `missing initial bonded pool is unknown and never nonmember`() = runTest {
        val h = Harness(this); h.start(); h.member(h.membership(1)); h.pool(1, null)
        assertNull(h.values.last())
        assertTrue(h.values.none { it is StakingState.Pool.None })
    }

    @Test fun `lost bonded pool clears existing member without permitting join`() = runTest {
        val h = Harness(this); h.start(); h.healthy(); h.pool(1, null)
        assertNull(h.values.last())
        assertTrue(h.values.none { it is StakingState.Pool.None })
    }

    @Test fun `bonded pool return after absence restores current member`() = runTest {
        val h = Harness(this); h.start(); h.healthy(); h.pool(1, null); h.pool(1, h.bonded(210))
        assertEquals(BigInteger.valueOf(210), (h.values.last() as StakingState.Pool.Member).pool.totalStakedInPlanks)
        assertTrue(h.errors.isEmpty())
    }

    @Test fun `new membership clears old pool while its bonded source is silent`() = runTest {
        val h = Harness(this); h.start(); h.healthy(); h.member(h.membership(2))
        assertNull(h.values.last())
        assertTrue(h.values.none { it is StakingState.Pool.None })
    }

    @Test fun `confirmed membership removal still clears to nonmember`() = runTest {
        val h = Harness(this); h.start(); h.healthy(); h.member(null)
        assertTrue(h.values.last() is StakingState.Pool.None)
    }

    @Test fun `held metadata cannot retain old member after bonded disappearance`() = runTest {
        val h = Harness(this); h.start(); h.healthy()
        h.metadataGate = CompletableDeferred(); h.pool(1, h.bonded(220)); h.pool(1, null)
        assertNull(h.values.last())
        h.metadataGate!!.complete("Stale metadata"); runCurrent()
        assertNull(h.values.last())
    }

    @Test fun `metadata failure withdraws body without inventing nonmembership`() = runTest {
        val h = Harness(this); h.start(); h.healthy(); h.metadataFails = true; h.pool(1, h.bonded(230))
        assertNull(h.values.last())
        assertTrue(h.values.none { it is StakingState.Pool.None })
        assertTrue(h.errors.isEmpty())
    }

    @Test fun `healthy same selection recovers after metadata failure`() = runTest {
        val h = Harness(this); h.start(); h.healthy(); h.metadataFails = true; h.pool(1, h.bonded(230))
        h.metadataFails = false; h.pool(1, h.bonded(240))
        assertEquals(BigInteger.valueOf(240), (h.values.last() as? StakingState.Pool.Member)?.pool?.totalStakedInPlanks)
        assertTrue(h.errors.isEmpty())
    }

    @Test fun `failed reward lookup is unavailable rather than fabricated zero rewards`() = runTest {
        val h = Harness(this); h.start(); h.healthy(); h.rewardsFail = true; h.pool(1, h.bonded(250))
        assertNull(h.values.last())
        assertTrue(h.values.none { it is StakingState.Pool.None })
    }

    @Test fun `held reward lookup cannot restore disappeared bonded pool`() = runTest {
        val h = Harness(this); h.start(); h.healthy()
        h.rewardsGate = CompletableDeferred(); h.pool(1, h.bonded(260)); h.pool(1, null)
        assertNull(h.values.last())
        h.rewardsGate!!.complete(BigInteger.valueOf(99)); runCurrent()
        assertNull(h.values.last())
    }

    @Test fun `held era lookup cannot restore disappeared bonded pool`() = runTest {
        val h = Harness(this); h.start(); h.healthy()
        h.eraGate = CompletableDeferred(); h.pool(1, h.bonded(270)); h.pool(1, null)
        assertNull(h.values.last())
        h.eraGate!!.complete(BigInteger.valueOf(20)); runCurrent()
        assertNull(h.values.last())
    }

    @Test fun `nested pool source failure permits later membership recovery`() = runTest {
        val h = Harness(this); h.start(); h.healthy(); h.failPool(1)
        assertNull(h.values.last())
        h.member(h.membership(2)); h.pool(2, h.bonded(280))
        assertEquals(BigInteger.valueOf(2), (h.values.last() as? StakingState.Pool.Member)?.pool?.poolId)
        assertTrue(h.errors.isEmpty())
    }

    @Test fun `cancelling main collector releases lower bonded subscription`() = runTest {
        val h = Harness(this); val job = h.start(); h.healthy(); job.cancelAndJoin(); runCurrent()
        assertTrue(h.activePools.isEmpty())
    }

    @Test fun `membership transport failure clears old member and healthy same member recovers`() = runTest {
        val h = Harness(this); h.start(); h.healthy(); h.failMembership()
        assertNull(h.values.last()); assertTrue(h.values.none { it is StakingState.Pool.None })
        h.member(h.membership(1))
        assertTrue(h.values.last() is StakingState.Pool.Member)
    }

    @Test fun `bonded transport failure stays unknown and later same source recovers`() = runTest {
        val h = Harness(this); h.start(); h.healthy(); h.unavailablePool(1)
        assertNull(h.values.last()); assertTrue(h.values.none { it is StakingState.Pool.None })
        h.pool(1, h.bonded(290))
        assertEquals(BigInteger.valueOf(290), (h.values.last() as StakingState.Pool.Member).pool.totalStakedInPlanks)
    }

    @Test fun `nomination transport failure cannot become confirmed missing validators`() = runTest {
        val h = Harness(this); h.start(); h.healthy(); h.failNominations()
        assertNull(h.values.last()); assertTrue(h.values.none { it is StakingState.Pool.None })
        h.restoreNominations(); assertTrue(h.values.last() is StakingState.Pool.Member)
    }

    private class Harness(private val scope: TestScope) {
        val account = ByteArray(32) { 1 }
        val chain = mock<Chain> {
            on { id } doReturn "local-observation"
            on { supportStakingPool } doReturn true
            on { ecosystem } doReturn Ecosystem.Substrate
            on { addressPrefix } doReturn 42
        }
        private val wallet = mock<MetaAccount> {
            on { id } doReturn 1L
            on { substrateAccountId } doReturn account
            on { chainAccounts } doReturn emptyMap()
        }
        private val members = MutableSharedFlow<Result<PoolMember?>>(replay = 1)
        private val sources = mutableMapOf<BigInteger, MutableSharedFlow<Pair<Result<BondedPool?>, Boolean>>>()
        private val nominations = MutableStateFlow(Result.success<Nominations?>(null))
        val activePools = mutableSetOf<BigInteger>()
        val values = mutableListOf<StakingState?>()
        val errors = mutableListOf<Throwable>()
        var metadataGate: CompletableDeferred<String?>? = null
        var rewardsGate: CompletableDeferred<BigInteger>? = null
        var eraGate: CompletableDeferred<BigInteger>? = null
        var metadataFails = false
        var rewardsFail = false
        private val data = mock<StakingPoolDataSource>()
        private val relay = mock<StakingRelayChainScenarioRepository>()
        private val selection = mock<StakingInteractor> { on { selectedChainFlow() } doReturn MutableStateFlow(chain) }
        private val accounts = mock<AccountRepository> { on { selectedMetaAccountFlow() } doReturn MutableStateFlow(wallet) }
        private val interactor = StakingPoolInteractor(mock(), data, selection, relay, accounts, mock(), mock(), mock())

        suspend fun start(): kotlinx.coroutines.Job {
            whenever(data.observePoolMembers(any(), any())).thenReturn(members)
            whenever(data.observePool(any(), any())).thenAnswer { call ->
                val id = call.getArgument<BigInteger>(1)
                flow {
                    activePools += id
                    try { source(id).collect { (pool, failure) ->
                        if (failure) throw IOException("Local bonded source failure")
                        emit(pool)
                    } } finally { activePools -= id }
                }
            }
            whenever(data.getPoolMetadata(any(), any())).doSuspendableAnswer { call ->
                if (metadataFails) throw IOException("Local metadata failure")
                metadataGate?.await() ?: "Local pool ${call.getArgument<BigInteger>(1)}"
            }
            // Kotlin suspend Result uses the underlying value at the JVM method boundary.
            @Suppress("UNCHECKED_CAST")
            val rewardsCall = whenever(data.getPendingRewards(any(), any())) as OngoingStubbing<Any?>
            rewardsCall.doSuspendableAnswer {
                val result = if (rewardsFail) Result.failure(IOException("Local rewards failure"))
                else Result.success(rewardsGate?.await() ?: BigInteger.valueOf(7))
                Result::class.java.getDeclaredMethod("unbox-impl").invoke(result)
            }
            whenever(relay.getNominationPoolPalletId(any())).thenReturn("py/nopls".toByteArray())
            whenever(relay.observeRemoteAccountNominationsResult(any(), any())).thenReturn(nominations)
            whenever(relay.getCurrentEraIndex(any())).doSuspendableAnswer { eraGate?.await() ?: BigInteger.TEN }
            return scope.backgroundScope.launch {
                interactor.stakingStateFlow().catch { errors += it }.collect { values += it }
            }.also { scope.runCurrent() }
        }

        suspend fun healthy() {
            member(membership(1)); pool(1, bonded(100))
            if (values.last() !is StakingState.Pool.Member) interactor.observeCurrentPool(chain, account).first()
            check(values.last() is StakingState.Pool.Member) { "Healthy fixture must establish actual member first: $values / $errors" }
        }
        suspend fun member(value: PoolMember?) { members.emit(Result.success(value)); scope.runCurrent() }
        suspend fun pool(id: Int, value: BondedPool?) { source(BigInteger.valueOf(id.toLong())).emit(Result.success(value) to false); scope.runCurrent() }
        suspend fun failPool(id: Int) { source(BigInteger.valueOf(id.toLong())).emit(Result.success<BondedPool?>(null) to true); scope.runCurrent() }
        suspend fun failMembership() { members.emit(Result.failure(IOException("Local membership failure"))); scope.runCurrent() }
        suspend fun unavailablePool(id: Int) { source(BigInteger.valueOf(id.toLong())).emit(Result.failure<BondedPool?>(IOException("Local bonded response failure")) to false); scope.runCurrent() }
        fun failNominations() { nominations.value = Result.failure(IOException("Local nominations failure")); scope.runCurrent() }
        fun restoreNominations() { nominations.value = Result.success(null); scope.runCurrent() }
        fun membership(id: Int) = PoolMember(BigInteger.valueOf(id.toLong()), BigInteger.TEN, BigInteger.ZERO, emptyList())
        fun bonded(points: Long) = BondedPool(BigInteger.valueOf(points), BondedPoolState.Open, BigInteger.ONE, account, account, account, account)
        private fun source(id: BigInteger) = sources.getOrPut(id) { MutableSharedFlow(replay = 1) }
    }
}
