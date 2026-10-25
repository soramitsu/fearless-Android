package jp.co.soramitsu.wallet.impl.data.network.blockchain.updaters

import java.math.BigInteger
import android.util.Log
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.model.AssetBalanceUpdateItem
import jp.co.soramitsu.coredb.model.ChainAccountLocal
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import jp.co.soramitsu.coredb.model.RelationJoinedMetaAccountInfo
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.api.data.BalanceLoader
import jp.co.soramitsu.wallet.impl.data.repository.tranfser.BitcoinTransferServiceProviderTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class, InternalCoroutinesApi::class)
class BalanceDemandConvergenceTest {
    private class Fixture(scheduler: TestCoroutineScheduler) {
        val chain = BitcoinTransferServiceProviderTest.solanaChain()
        val chains = MutableStateFlow(listOf(chain))
        val wallet = MetaAccountLocal(
            ByteArray(32) { 1 }, CryptoType.ED25519, ByteArray(32) { 1 }, null, null, null,
            "Public fixture", true, 0, false, null, true
        ).also { it.id = 1 }
        val info = RelationJoinedMetaAccountInfo(
            wallet,
            listOf(
            ChainAccountLocal(1, chain.id, ByteArray(32) { 1 }, ByteArray(32) { 1 }, CryptoType.ED25519, "SOL", true)
        ),
            emptyList()
        )
        val accounts = MutableStateFlow<RelationJoinedMetaAccountInfo?>(info)
        val release = CompletableDeferred<Unit>()
        var started = 0
        var stopped = 0
        val observed = mutableListOf<Chain>()
        val writes = mutableListOf<BigInteger>()
        var action: (Chain) -> Flow<BalanceLoader.BalanceLoaderAction> = {
            flow {
                started++
                try {
                    release.await()
                } finally {
                    stopped++
                }
            }
        }
        val system: BalancesUpdateSystem
        init {
            val registry = mock(ChainRegistry::class.java)

            whenever(registry.syncedChains).thenReturn(chains)
            val dao = mock(MetaAccountDao::class.java)

            whenever(dao.selectedMetaAccountInfoFlow()).thenReturn(accounts)
            val assets = mock(AssetDao::class.java)
            runBlocking<Unit> {
                whenever(assets.updateAsset(any<AssetBalanceUpdateItem>())).thenAnswer {
                    writes.add(checkNotNull(it.getArgument<AssetBalanceUpdateItem>(0).freeInPlanks))

                    1
                }
            }
            val provider = object : BalanceLoader.Provider {
                override fun invoke(chain: Chain) = object : BalanceLoader(chain) {
                    override suspend fun loadBalance(metaAccounts: Set<MetaAccount>) = error("Unexpected batch")
                    override fun subscribeBalance(metaAccount: MetaAccount) = action(chain).also { observed += chain }
                }
            }
            system = BalancesUpdateSystem(registry, dao, provider, assets, StandardTestDispatcher(scheduler))
        }
        fun newer() =
            chain.copy(nodes = chain.nodes.map { it.copy(url = "https://new.invalid/rpc?key=public-fixture") })
        fun update(value: Int) = BalanceLoader.BalanceLoaderAction.UpdateBalance(
            AssetBalanceUpdateItem(
            metaId = 1, chainId = chain.id,
                accountId = ByteArray(32) {
                1
            },
                id = "SOL", freeInPlanks = BigInteger.valueOf(value.toLong()),
            miscFrozenInPlanks = BigInteger.ZERO, feeFrozenInPlanks = BigInteger.ZERO
            )
        )
    }

    @Test fun sameCountSelectedEndpointSnapshotStartsFreshLoaderAndCancelsPreviousDemand() = runTest {
        val f = Fixture(testScheduler)

        val job = f.system.start().launchIn(backgroundScope)
        try {
            runCurrent()

            assertEquals(1, f.started)
            f.chains.value = listOf(f.newer())

            runCurrent()
            assertEquals(2, f.started)

            assertEquals(1, f.stopped)
            assertEquals(f.newer().nodes, f.observed.last().nodes)
        } finally {
            f.release.complete(Unit)

        job.cancel()

        runCurrent()
        }
    }

    @Test fun removingSelectedWalletStopsItsBalanceDemand() = runTest {
        val f = Fixture(testScheduler)

        val job = f.system.start().launchIn(backgroundScope)
        try {
            runCurrent()

            assertEquals(1, f.started)
            f.accounts.value = null

            runCurrent()
            assertEquals(1, f.stopped)

            assertEquals(1, f.started)
        } finally {
            f.release.complete(Unit)

        job.cancel()

        runCurrent()
        }
    }

    @Test fun stoppingUpdateCollectorStopsItsBalanceDemand() = runTest {
        val f = Fixture(testScheduler)

        val job = f.system.start().launchIn(backgroundScope)
        try {
            runCurrent()

            assertEquals(1, f.started)
            job.cancel()

            runCurrent()

            assertEquals(1, f.stopped)
        } finally {
            f.release.complete(Unit)

        job.cancel()

        runCurrent()
        }
    }

    @Test fun removedNetworkStopsDemandWithoutCreatingAnotherLoader() = runTest {
        val f = Fixture(testScheduler)

        val job = f.system.start().launchIn(backgroundScope)
        try {
            runCurrent()

            f.chains.value = emptyList()

            runCurrent()
            assertEquals(1, f.stopped)

            assertEquals(1, f.started)
        } finally {
            f.release.complete(Unit)

        job.cancel()

        runCurrent()
        }
    }

    @Test fun heldOldCallbackCannotOverwriteBalanceAfterFreshEndpointDemand() = runTest {
        val f = Fixture(testScheduler)

        val oldReply = CompletableDeferred<Unit>()

        var oldEntered = false
        f.action = { chain ->
            if (chain.nodes == f.chain.nodes) {
                object : Flow<BalanceLoader.BalanceLoaderAction> {
                override suspend fun collect(collector: FlowCollector<BalanceLoader.BalanceLoaderAction>) {
                    oldEntered = true
                    withContext(NonCancellable) { oldReply.await() }
                    collector.emit(f.update(1))
                }
            }
            } else {
                flowOf(f.update(2))
            }
        }
        val job = f.system.start().launchIn(backgroundScope)
        try {
            runCurrent()

            assertTrue(oldEntered)
            f.chains.value = listOf(f.newer())

            runCurrent()

            assertEquals(listOf(BigInteger.valueOf(2)), f.writes)
            oldReply.complete(Unit)

            runCurrent()

            assertEquals(listOf(BigInteger.valueOf(2)), f.writes)
        } finally {
            oldReply.complete(Unit)

        f.release.complete(Unit)

        job.cancel()

        runCurrent()
        }
    }

    @Test fun oneFailingChainDoesNotCancelHealthySiblingDemand() = mockStatic(Log::class.java).use { logger ->
        runTest {
        val f = Fixture(testScheduler)
        val healthy = f.chain.copy(id = "public-healthy-other-chain")
        f.chains.value = listOf(f.chain, healthy)
        f.accounts.value = RelationJoinedMetaAccountInfo(
            f.wallet,
            f.info.chainAccounts + ChainAccountLocal(
            1, healthy.id, ByteArray(32) { 2 }, ByteArray(32) { 2 }, CryptoType.ED25519, "SOL", true
        ),
            emptyList()
        )
        val fail = CompletableDeferred<Unit>()

        var healthyStarted = false

        var healthyStopped = false
        f.action = { chain ->
            flow {
            if (chain.id == f.chain.id) {
                fail.await()

                error("public fixture failure")
            } else {
                healthyStarted = true

            try {
                f.release.await()
            } finally {
                healthyStopped = true
            }
            }
        }
        }
        val job = f.system.start().launchIn(backgroundScope)
        try {
            runCurrent()

            assertTrue(healthyStarted)
            fail.complete(Unit)

            runCurrent()

            logger.verify { Log.e("BalancesUpdateSystem", "Balance subscription failed: IllegalStateException") }
            logger.verifyNoMoreInteractions()
            assertFalse("Sibling demand remains active", healthyStopped)
            f.accounts.value = null

            runCurrent()

            assertTrue(healthyStopped)
        } finally {
            fail.complete(Unit)

        f.release.complete(Unit)

        job.cancel()

        runCurrent()
        }
        }
    }
}
