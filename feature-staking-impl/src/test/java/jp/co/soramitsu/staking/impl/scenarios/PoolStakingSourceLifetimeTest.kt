package jp.co.soramitsu.staking.impl.scenarios

import java.io.IOException
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.domain.model.OwnPool
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

/** Actual interactor selection pipeline; only remote pool observation is a local controllable source. */
@OptIn(ExperimentalCoroutinesApi::class)
class PoolStakingSourceLifetimeTest {
    @Test fun `healthy nonmember retains exact chain and public account`() = runTest {
        val h = Harness(this)
        h.start(); h.publish(h.a, 1, null)
        val value = h.values.last() as StakingState.Pool.None
        assertSame(h.a, value.chain)
        assertTrue(value.accountId.contentEquals(h.account(1)))
        assertTrue(h.errors.isEmpty())
    }

    @Test fun `healthy member retains exact pool and live updates`() = runTest {
        val h = Harness(this)
        h.start(); val first = h.pool(1); val second = h.pool(2)
        h.publish(h.a, 1, first); h.publish(h.a, 1, second)
        assertSame(second, (h.values.last() as StakingState.Pool.Member).pool)
        assertEquals(listOf(first, second), h.values.filterIsInstance<StakingState.Pool.Member>().map { it.pool })
    }

    @Test fun `chain switch cancels previous remote pool subscription`() = runTest {
        val h = Harness(this); h.start(); h.publish(h.a, 1, h.pool(1))
        h.chain.value = h.b; runCurrent()
        assertEquals(setOf(h.key(h.b, 1)), h.active)
    }

    @Test fun `wallet switch cancels previous remote pool subscription`() = runTest {
        val h = Harness(this); h.start(); h.publish(h.a, 1, h.pool(1))
        h.wallet.value = h.wallet(2); runCurrent()
        assertEquals(setOf(h.key(h.a, 2)), h.active)
    }

    @Test fun `late old chain pool cannot replace current chain membership`() = runTest {
        val h = Harness(this); h.start(); h.publish(h.a, 1, h.pool(1))
        h.chain.value = h.b; runCurrent(); val current = h.pool(2)
        h.publish(h.b, 1, current); h.publish(h.a, 1, h.pool(3))
        assertSame(h.b, h.values.last()?.chain)
        assertSame(current, (h.values.last() as StakingState.Pool.Member).pool)
    }

    @Test fun `late old wallet pool cannot replace current wallet membership`() = runTest {
        val h = Harness(this); h.start(); h.publish(h.a, 1, h.pool(1))
        h.wallet.value = h.wallet(2); runCurrent(); val current = h.pool(2)
        h.publish(h.a, 2, current); h.publish(h.a, 1, h.pool(3))
        assertTrue(h.values.last()?.accountId.contentEquals(h.account(2)))
        assertSame(current, (h.values.last() as StakingState.Pool.Member).pool)
    }

    @Test fun `chain switch clears old membership while new source has no value`() = runTest {
        val h = Harness(this); h.start(); h.publish(h.a, 1, h.pool(1))
        h.chain.value = h.b; runCurrent()
        assertNull(h.values.last())
        assertTrue(h.errors.isEmpty())
    }

    @Test fun `wallet switch clears old membership while new source has no value`() = runTest {
        val h = Harness(this); h.start(); h.publish(h.a, 1, h.pool(1))
        h.wallet.value = h.wallet(2); runCurrent()
        assertNull(h.values.last())
        assertTrue(h.errors.isEmpty())
    }

    @Test fun `unsupported chain cancels old pool subscription`() = runTest {
        val h = Harness(this); h.start(); h.publish(h.a, 1, h.pool(1))
        h.chain.value = h.unsupported; runCurrent()
        assertTrue(h.active.isEmpty())
        assertEquals(listOf(h.key(h.a, 1)), h.started)
    }

    @Test fun `unsupported chain clears prior membership without inventing nonmembership`() = runTest {
        val h = Harness(this); h.start(); h.publish(h.a, 1, h.pool(1))
        h.chain.value = h.unsupported; runCurrent()
        assertNull(h.values.last())
        assertTrue(h.errors.isEmpty())
    }

    @Test fun `missing account clears prior membership without terminating selection`() = runTest {
        val h = Harness(this); h.start(); h.publish(h.a, 1, h.pool(1))
        h.wallet.value = h.wallet(3, hasAccount = false); runCurrent()
        assertNull(h.values.last())
        assertTrue(h.errors.isEmpty())
        assertTrue(h.active.isEmpty())
    }

    @Test fun `healthy wallet after missing account restores current membership`() = runTest {
        val h = Harness(this); h.start(); h.publish(h.a, 1, h.pool(1))
        h.wallet.value = h.wallet(3, hasAccount = false); runCurrent()
        h.wallet.value = h.wallet(2); runCurrent(); val current = h.pool(2)
        h.publish(h.a, 2, current)
        assertSame(current, (h.values.last() as? StakingState.Pool.Member)?.pool)
        assertTrue(h.errors.isEmpty())
    }

    @Test fun `remote collector failure clears membership without escaping`() = runTest {
        val h = Harness(this); h.start(); h.publish(h.a, 1, h.pool(1))
        h.fail(h.a, 1)
        assertNull(h.values.last())
        assertTrue(h.errors.isEmpty())
    }

    @Test fun `new selection recovers after remote collector failure`() = runTest {
        val h = Harness(this); h.start(); h.publish(h.a, 1, h.pool(1)); h.fail(h.a, 1)
        h.chain.value = h.b; runCurrent(); val current = h.pool(2); h.publish(h.b, 1, current)
        assertSame(current, (h.values.last() as? StakingState.Pool.Member)?.pool)
        assertTrue(h.errors.isEmpty())
    }

    @Test fun `remote source construction failure clears membership without escaping`() = runTest {
        val h = Harness(this); h.start(); h.publish(h.a, 1, h.pool(1))
        h.throwOnStart += h.key(h.b, 1); h.chain.value = h.b; runCurrent()
        assertNull(h.values.last())
        assertTrue(h.errors.isEmpty())
    }

    @Test fun `new selection recovers after remote source construction failure`() = runTest {
        val h = Harness(this); h.start(); h.publish(h.a, 1, h.pool(1))
        h.throwOnStart += h.key(h.b, 1); h.chain.value = h.b; runCurrent()
        h.wallet.value = h.wallet(2); runCurrent(); val current = h.pool(2); h.publish(h.b, 2, current)
        assertSame(current, (h.values.last() as? StakingState.Pool.Member)?.pool)
        assertTrue(h.errors.isEmpty())
    }

    @Test fun `many wallet switches retain only latest subscription and never stall admission`() = runTest {
        val h = Harness(this); h.start(); h.publish(h.a, 1, h.pool(1))
        for (id in 2..20) { h.wallet.value = h.wallet(id); runCurrent() }
        val current = h.pool(20); h.publish(h.a, 20, current)
        assertEquals(setOf(h.key(h.a, 20)), h.active)
        assertSame(current, (h.values.last() as? StakingState.Pool.Member)?.pool)
    }

    @Test fun `cancelling outer collector releases its remote subscription`() = runTest {
        val h = Harness(this); val job = h.start(); h.publish(h.a, 1, h.pool(1))
        job.cancelAndJoin(); runCurrent()
        assertTrue(h.active.isEmpty())
        assertTrue(h.errors.isEmpty())
    }

    private class Harness(private val scope: TestScope) {
        val a = chain("local-pool-a")
        val b = chain("local-pool-b")
        val unsupported = chain("local-no-pools", supports = false)
        val chain = MutableStateFlow(a)
        val wallet = MutableStateFlow(wallet(1))
        val active = mutableSetOf<String>()
        val started = mutableListOf<String>()
        val throwOnStart = mutableSetOf<String>()
        val values = mutableListOf<StakingState?>()
        val errors = mutableListOf<Throwable>()
        private val sources = mutableMapOf<String, MutableSharedFlow<Pair<OwnPool?, Boolean>>>()
        private val selection = mock<StakingInteractor> { on { selectedChainFlow() } doReturn chain }
        private val accounts = mock<AccountRepository> { on { selectedMetaAccountFlow() } doReturn wallet }
        private val interactor = spy(StakingPoolInteractor(mock(), mock(), selection, mock(), accounts, mock(), mock(), mock())).also { target ->
            doAnswer { call ->
                val chain = call.getArgument<Chain>(0); val id = call.getArgument<ByteArray>(1)[0].toInt()
                val key = key(chain, id)
                if (key in throwOnStart) throw IOException("local source construction failure")
                flow {
                    check(active.add(key)); started += key
                    try {
                        source(key).collect { (pool, failure) ->
                            if (failure) throw IOException("local remote collector failure")
                            emit(Result.success(pool))
                        }
                    } finally { active.remove(key) }
                }
            }.whenever(target).observeCurrentPool(any(), any())
        }

        fun start() = scope.backgroundScope.launch {
            interactor.stakingStateFlow().catch { errors += it }.collect { values += it }
        }.also { scope.runCurrent() }

        suspend fun publish(chain: Chain, id: Int, pool: OwnPool?) {
            source(key(chain, id)).emit(pool to false); scope.runCurrent()
        }
        suspend fun fail(chain: Chain, id: Int) {
            source(key(chain, id)).emit(null to true); scope.runCurrent()
        }
        private fun source(key: String) = sources.getOrPut(key) { MutableSharedFlow(replay = 1) }
        fun key(chain: Chain, id: Int) = chain.id + ":" + id
        fun account(id: Int) = ByteArray(32) { id.toByte() }
        fun wallet(id: Int, hasAccount: Boolean = true) = mock<MetaAccount> {
            on { this.id } doReturn id.toLong()
            on { chainAccounts } doReturn emptyMap()
            on { substrateAccountId } doReturn if (hasAccount) account(id) else null
        }
        fun pool(id: Int) = mock<OwnPool> { on { poolId } doReturn BigInteger.valueOf(id.toLong()) }
        private fun chain(id: String, supports: Boolean = true) = mock<Chain> {
            on { this.id } doReturn id
            on { supportStakingPool } doReturn supports
            on { ecosystem } doReturn Ecosystem.Substrate
            on { addressPrefix } doReturn 42
        }
    }
}
