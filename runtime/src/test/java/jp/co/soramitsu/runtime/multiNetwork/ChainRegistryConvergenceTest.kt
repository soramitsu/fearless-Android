package jp.co.soramitsu.runtime.multiNetwork

import android.util.Log
import jp.co.soramitsu.common.domain.NetworkStateService
import jp.co.soramitsu.coredb.dao.AssetReadOnlyCache
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.model.chain.ChainLocal
import jp.co.soramitsu.coredb.model.chain.ChainNodeLocal
import jp.co.soramitsu.coredb.model.chain.JoinedChainInfo
import jp.co.soramitsu.coredb.recovery.selection.EndpointMutationLane
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainSyncService
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.mapChainLocalToChain
import jp.co.soramitsu.runtime.multiNetwork.configurator.ChainEnvironmentConfigurator
import jp.co.soramitsu.runtime.multiNetwork.configurator.ChainEnvironmentConfiguratorProvider
import jp.co.soramitsu.runtime.multiNetwork.connection.ConnectionPool
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumConnectionPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeProviderPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSubscriptionPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSyncService
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.PortfolioVisibility
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

class ChainRegistryConvergenceTest {
    private class Fixture : AutoCloseable {
        private val androidLog = mockStatic(Log::class.java)
        val local = JoinedChainInfo(ChainLocal("a", null, null, 1, "A", null, "", null, 42, false, false, false, false, false, false, false, false, null, "Substrate", null, null, null, null), listOf(ChainNodeLocal("a", "wss://a.invalid/", "A", true, true)), emptyList(), emptyList())
        val chain = mapChainLocalToChain(local)
        val rows = MutableSharedFlow<List<JoinedChainInfo>>(replay = 1).also { it.tryEmit(listOf(local)) }
        val dao = mock(ChainDao::class.java)
        val connection = mock(ConnectionPool::class.java)
        val providers = mock(RuntimeProviderPool::class.java)
        val subscriptions = mock(RuntimeSubscriptionPool::class.java)
        val sync = mock(RuntimeSyncService::class.java)
        val network = mock(NetworkStateService::class.java)
        val environment = mock(ChainEnvironmentConfigurator::class.java)
        val registry: ChainRegistry
        init {
            whenever(dao.endpointMutationLane).thenReturn(EndpointMutationLane())
            whenever(dao.joinChainInfoFlow()).thenReturn(rows)
            val assets = mock(AssetReadOnlyCache::class.java)
            whenever(assets.observeChainIdsNeedingSync()).thenReturn(flowOf(emptySet()))
            val configurators = mock(ChainEnvironmentConfiguratorProvider::class.java)
            whenever(configurators.provide(any())).thenReturn(environment)
            registry = ChainRegistry(providers, connection, subscriptions, dao, mock(ChainSyncService::class.java), sync, network, mock(EthereumConnectionPool::class.java), assets, ChainsRepository(dao), configurators, mock(PortfolioVisibility::class.java), Dispatchers.Unconfined)
        }
        override fun close() {
            try {
                runBlocking { registry.scope.coroutineContext.job.cancelAndJoin() }
            } finally {
                androidLog.close()
            }
        }
    }

    @Test fun repeatedSyncUpMaintainsOneDatabaseObserver() = runBlocking<Unit> {
        Fixture().use { f ->
            f.registry.syncUp()

            f.registry.syncUp()
            assertEquals(1, f.rows.subscriptionCount.value)
            verify(f.environment, times(1)).configure(f.chain)
        }
    }

    @Test fun actualDemandRemovalRetiresConnectionAndRuntimeResources() = runBlocking<Unit> {
        Fixture().use { f ->
            f.registry.syncUp()

            f.rows.emit(emptyList())
            verify(f.connection).removeConnection("a")
            verify(f.providers).removeRuntimeProvider("a")
            verify(f.subscriptions).removeSubscription("a")
            verify(f.sync).unregisterChain("a")
        }
    }

    @Test fun directSetupFailureReachesCaller() = runBlocking<Unit> {
        Fixture().use { f ->
            val expected = IllegalStateException("synthetic public failure")
            doThrow(expected).`when`(f.environment).configure(f.chain)
            try {
                f.registry.setupChain(f.chain)

            fail("setup must fail")
            } catch (
                e: IllegalStateException
            ) {
                assertSame(expected, e)
            }
        }
    }

    @Test fun directSetupCancellationReachesCaller() = runBlocking<Unit> {
        Fixture().use { f ->
            val expected = CancellationException("synthetic public cancellation")
            doThrow(expected).`when`(f.environment).configure(f.chain)
            try {
                f.registry.setupChain(f.chain)

            fail("setup must cancel")
            } catch (
                e: CancellationException
            ) {
                assertSame(expected, e)
            }
        }
    }

    @Test fun backgroundSetupFailureIsReportedWithoutFalseSuccess() = runBlocking<Unit> {
        Fixture().use { f ->
            doThrow(IllegalStateException("synthetic failure")).`when`(f.environment).configure(f.chain)
            f.registry.syncUp()
            verify(f.network).notifyChainSyncProblem("a")
            verify(f.network, never()).notifyChainSyncSuccess("a")
        }
    }

    @Test
    fun currentChainsWaitsForFirstCatalogButPublishesLaterEmptyRemoval() = runBlocking<Unit> {
        Fixture().use { f ->
            assertEquals(emptyList<List<jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain>>(), f.registry.currentChains.replayCache)
            f.registry.syncUp()
            assertEquals(listOf(f.chain), f.registry.currentChains.replayCache.single())
            f.rows.emit(emptyList())
            assertEquals(emptyList<jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain>(), f.registry.currentChains.replayCache.single())
        }
    }

    @Test
    fun explicitUnsupportedTransitionRetiresOnlyThatChainAndPublishesEmpty() = runBlocking<Unit> {
        for (ecosystem in listOf("FUTURE_UNSUPPORTED", ChainLocal.UNAVAILABLE_CATALOG_PREFIX + "Substrate")) {
            Fixture().use { f ->
                f.registry.syncUp()
                f.rows.emit(listOf(f.local.copy(chain = f.local.chain.copy(ecosystem = ecosystem))))
                verify(f.connection).removeConnection("a")
                verify(f.providers).removeRuntimeProvider("a")
                verify(f.subscriptions).removeSubscription("a")
                verify(f.sync).unregisterChain("a")
                verify(f.environment, times(1)).configure(f.chain)
                assertEquals(emptyList<jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain>(), f.registry.currentChains.replayCache.single())
            }
        }
    }
}
