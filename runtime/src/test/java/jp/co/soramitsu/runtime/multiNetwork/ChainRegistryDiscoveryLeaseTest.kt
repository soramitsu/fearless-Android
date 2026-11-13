package jp.co.soramitsu.runtime.multiNetwork

import android.util.Log
import jp.co.soramitsu.common.domain.NetworkStateService
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.core.models.Ecosystem
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

/**
 * Discovery reads a chain that background sync may not keep. The registry alone decides who
 * disconnects it, so a read never tears down a connection the selection keeps, including one the
 * selection takes over during the read because the read stored a funded balance.
 */
class ChainRegistryDiscoveryLeaseTest {

    private class RecordingConfigurator : ChainEnvironmentConfigurator {
        val configured = mutableListOf<String>()
        var active = 0
        var maxActive = 0
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun configure(chain: Chain) {
            active++
            maxActive = maxOf(maxActive, active)
            try {
                gate?.await()
                configured += chain.id
            } finally {
                active--
            }
        }
    }

    private class Fixture(chainIdsNeedingSync: Set<String> = emptySet()) : AutoCloseable {
        private val androidLog = mockStatic(Log::class.java)
        private val ranked = joined("a", rank = 1)
        private val unranked = joined("b", rank = null)
        val rankedChain = mapChainLocalToChain(ranked)
        val unrankedChain = mapChainLocalToChain(unranked)
        val needing = MutableStateFlow(chainIdsNeedingSync)
        val rows = MutableSharedFlow<List<JoinedChainInfo>>(replay = 1).also { it.tryEmit(listOf(ranked, unranked)) }
        val connection = mock(ConnectionPool::class.java)
        val providers = mock(RuntimeProviderPool::class.java)
        val subscriptions = mock(RuntimeSubscriptionPool::class.java)
        val sync = mock(RuntimeSyncService::class.java)
        val environment = RecordingConfigurator()
        val registry: ChainRegistry

        init {
            val dao = mock(ChainDao::class.java)
            whenever(dao.endpointMutationLane).thenReturn(EndpointMutationLane())
            whenever(dao.joinChainInfoFlow()).thenReturn(rows)
            val assets = mock(AssetReadOnlyCache::class.java)
            whenever(assets.observeChainIdsNeedingSync()).thenReturn(needing)
            val configurators = mock(ChainEnvironmentConfiguratorProvider::class.java)
            whenever(configurators.provide(any())).thenReturn(environment)
            registry = ChainRegistry(
                providers, connection, subscriptions, dao, mock(ChainSyncService::class.java), sync,
                mock(NetworkStateService::class.java), mock(EthereumConnectionPool::class.java), assets,
                ChainsRepository(dao), configurators, mock(PortfolioVisibility::class.java), Dispatchers.Unconfined
            )
        }

        fun verifyStopped(chainId: String) {
            verify(connection, times(1)).removeConnection(chainId)
            verify(providers, times(1)).removeRuntimeProvider(chainId)
            verify(subscriptions, times(1)).removeSubscription(chainId)
            verify(sync, times(1)).unregisterChain(chainId)
        }

        fun verifyNeverStopped(chainId: String) {
            verify(connection, never()).removeConnection(chainId)
            verify(providers, never()).removeRuntimeProvider(chainId)
        }

        override fun close() {
            try {
                runBlocking { registry.scope.coroutineContext.job.cancelAndJoin() }
            } finally {
                androidLog.close()
            }
        }

        private fun joined(id: String, rank: Int?) = JoinedChainInfo(
            ChainLocal(
                id, null, null, rank, id.uppercase(), null, "", null, 42, false, false, false, false, false,
                false, false, false, null, "Substrate", null, null, null, null
            ),
            listOf(ChainNodeLocal(id, "wss://$id.invalid/", id.uppercase(), true, true)),
            emptyList(),
            emptyList()
        )
    }

    @Test
    fun `read of a chain outside background sync connects it for the read and disconnects it after`() = runBlocking<Unit> {
        Fixture().use { f ->
            f.registry.syncUp()

            val readResult = f.registry.withDiscoveryConnection(f.unrankedChain) {
                assertEquals(listOf("a", "b"), f.environment.configured)
                f.verifyNeverStopped("b")
                "balances"
            }

            assertEquals("balances", readResult)
            f.verifyStopped("b")
            f.verifyNeverStopped("a")
        }
    }

    @Test
    fun `chain that background sync takes over during the read stays connected`() = runBlocking<Unit> {
        Fixture().use { f ->
            f.registry.syncUp()

            f.registry.withDiscoveryConnection(f.unrankedChain) {
                // The read stores a funded balance, so the chain now needs background sync.
                f.needing.value = setOf("b")
                assertTrue(f.registry.awaitBackgroundSync("b", timeoutMillis = 1_000))
            }

            f.verifyNeverStopped("b")
            assertEquals(listOf("a", "b"), f.registry.syncedChains.value.map(Chain::id))
            // Once by the read, once (a no-op reconcile) by background sync, never at the same time.
            assertEquals(listOf("a", "b", "b"), f.environment.configured)
            assertEquals(1, f.environment.maxActive)
        }
    }

    @Test
    fun `chain the selection drops during a read is disconnected only when the read ends`() = runBlocking<Unit> {
        Fixture(chainIdsNeedingSync = setOf("b")).use { f ->
            f.registry.syncUp()

            f.registry.withDiscoveryConnection(f.unrankedChain) {
                f.needing.value = emptySet()
                assertEquals(listOf("a"), f.registry.syncedChains.value.map(Chain::id))
                f.verifyNeverStopped("b")
            }

            f.verifyStopped("b")
            // Selected when the read began: read on background sync's connection, not a new one.
            assertEquals(listOf("a", "b"), f.environment.configured)
        }
    }

    @Test
    fun `chain in background sync is read on its connection and left connected`() = runBlocking<Unit> {
        Fixture().use { f ->
            f.registry.syncUp()

            f.registry.withDiscoveryConnection(f.rankedChain) { }

            assertEquals(listOf("a"), f.environment.configured)
            f.verifyNeverStopped("a")
        }
    }

    @Test
    fun `failed read still disconnects the chain it connected`() = runBlocking<Unit> {
        Fixture().use { f ->
            f.registry.syncUp()

            val failure = runCatching {
                f.registry.withDiscoveryConnection(f.unrankedChain) { error("node refused the read") }
            }

            assertTrue(failure.isFailure)
            f.verifyStopped("b")
        }
    }

    @Test
    fun `setups of one chain run one at a time`() = runBlocking<Unit> {
        Fixture().use { f ->
            val gate = CompletableDeferred<Unit>()
            f.environment.gate = gate

            val first = launch { f.registry.setupChain(f.unrankedChain) }
            val second = launch { f.registry.setupChain(f.unrankedChain) }
            repeat(3) { yield() }

            assertEquals(1, f.environment.active)
            gate.complete(Unit)
            first.join()
            second.join()

            assertEquals(listOf("b", "b"), f.environment.configured)
            assertEquals(1, f.environment.maxActive)
        }
    }

    @Test
    fun `only substrate and evm networks with a node are read through a registry connection`() {
        fun chain(id: String, ecosystem: Ecosystem, nodes: Int = 1): Chain = mock(Chain::class.java).also { chain ->
            whenever(chain.id).thenReturn(id)
            whenever(chain.ecosystem).thenReturn(ecosystem)
            whenever(chain.nodes).thenReturn(List(nodes) { ChainNode("wss://$id.invalid", id, true, true) })
        }

        assertTrue(chain("polkadot", Ecosystem.Substrate).needsRegistryConnection())
        assertTrue(chain("1", Ecosystem.Ethereum).needsRegistryConnection())
        assertTrue(chain("moonbeam", Ecosystem.EthereumBased).needsRegistryConnection())
        assertFalse(chain("-239", Ecosystem.Ton).needsRegistryConnection())
        assertFalse(chain("no-node", Ecosystem.Substrate, nodes = 0).needsRegistryConnection())
        // The catalog stores the app-owned networks as Substrate; Solana even carries an HTTP node.
        assertFalse(chain(UniversalWalletRegistry.solanaMainnet.id, Ecosystem.Substrate).needsRegistryConnection())
        assertFalse(chain(UniversalWalletRegistry.bitcoinMainnet.id, Ecosystem.Substrate).needsRegistryConnection())
        assertFalse(chain(UniversalWalletRegistry.taira.id, Ecosystem.Substrate).needsRegistryConnection())
    }

    @Test
    fun `background sync wait gives up when the chain is not selected`() = runBlocking<Unit> {
        Fixture().use { f ->
            f.registry.syncUp()

            assertFalse(f.registry.awaitBackgroundSync("b", timeoutMillis = 50))
            assertTrue(f.registry.awaitBackgroundSync("a", timeoutMillis = 50))
        }
    }
}
