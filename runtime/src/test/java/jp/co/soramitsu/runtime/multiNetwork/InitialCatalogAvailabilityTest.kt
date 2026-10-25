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
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.configurator.ChainEnvironmentConfigurator
import jp.co.soramitsu.runtime.multiNetwork.configurator.ChainEnvironmentConfiguratorProvider
import jp.co.soramitsu.runtime.multiNetwork.connection.ConnectionPool
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumConnectionPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeProviderPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSubscriptionPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSyncService
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.PortfolioVisibility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

class InitialCatalogAvailabilityTest {
    private class Fixture : AutoCloseable {
        private val androidLog = mockStatic(Log::class.java)
        val local =
            JoinedChainInfo(
                ChainLocal(
                    "a",
                    null,
                    null,
                    1,
                    "A",
                    null,
                    "",
                    null,
                    42,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    null,
                    "Substrate",
                    null,
                    null,
                    null,
                    null
                ),
                listOf(ChainNodeLocal("a", "wss://a.invalid/", "A", true, true)),
                emptyList(),
                emptyList()
            )
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
            registry = ChainRegistry(
                providers,
                connection,
                subscriptions,
                dao,
                mock(ChainSyncService::class.java),
                sync,
                network,
                mock(EthereumConnectionPool::class.java),
                assets,
                ChainsRepository(dao),
                configurators,
                mock(PortfolioVisibility::class.java),
                Dispatchers.Unconfined
            )
        }

        override fun close() {
            try {
                runBlocking { registry.scope.coroutineContext.job.cancelAndJoin() }
            } finally {
                androidLog.close()
            }
        }
    }

    @Test
    fun initialEmptyCatalogPublishesKnownEmptySnapshot() = runBlocking<Unit> {
        Fixture().use { f ->
            f.rows.emit(emptyList())
            f.registry.syncUp()
            assertEquals(emptyList<Chain>(), withTimeoutOrNull(250) { f.registry.currentChains.first() })
            verify(f.environment, never()).configure(any())
        }
    }

    @Test
    fun initialUnsupportedCatalogPublishesKnownEmptySnapshot() = runBlocking<Unit> {
        Fixture().use { f ->
            f.rows.emit(listOf(f.local.copy(chain = f.local.chain.copy(ecosystem = "FUTURE_UNSUPPORTED"))))
            f.registry.syncUp()
            assertEquals(emptyList<Chain>(), withTimeoutOrNull(250) { f.registry.currentChains.first() })
            verify(f.environment, never()).configure(any())
        }
    }

    @Test
    fun initialHealthyCatalogPublishesExactChain() = runBlocking<Unit> {
        Fixture().use { f ->
            f.registry.syncUp()
            assertEquals(listOf(f.chain), withTimeoutOrNull(250) { f.registry.currentChains.first() })
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun beforeFirstDatabaseSnapshotDoesNotPublishPrematureEmpty() = runBlocking<Unit> {
        Fixture().use { f ->
            f.rows.resetReplayCache()
            f.registry.syncUp()
            assertNull(withTimeoutOrNull(250) { f.registry.currentChains.first() })
        }
    }

    @Test
    fun emptySnapshotLaterBecomesHealthyWithoutRestart() = runBlocking<Unit> {
        Fixture().use { f ->
            f.rows.emit(emptyList())
            f.registry.syncUp()
            assertEquals(emptyList<Chain>(), withTimeoutOrNull(250) { f.registry.currentChains.first() })
            f.rows.emit(listOf(f.local))
            assertEquals(listOf(f.chain), withTimeoutOrNull(250) { f.registry.currentChains.first() })
            verify(f.environment, times(1)).configure(f.chain)
        }
    }
}
