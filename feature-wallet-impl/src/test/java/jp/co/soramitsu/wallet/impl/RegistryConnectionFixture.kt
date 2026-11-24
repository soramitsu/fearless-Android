package jp.co.soramitsu.wallet.impl

import android.util.Log
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import jp.co.soramitsu.common.domain.NetworkStateService
import jp.co.soramitsu.coredb.dao.AssetReadOnlyCache
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.model.chain.ChainLocal
import jp.co.soramitsu.coredb.model.chain.ChainNodeLocal
import jp.co.soramitsu.coredb.model.chain.JoinedChainInfo
import jp.co.soramitsu.coredb.recovery.selection.EndpointMutationLane
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainSyncService
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.mapChainLocalToChain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.configurator.ChainEnvironmentConfigurator
import jp.co.soramitsu.runtime.multiNetwork.configurator.ChainEnvironmentConfiguratorProvider
import jp.co.soramitsu.runtime.multiNetwork.connection.ConnectionPool
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumConnectionPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeProvider
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeProviderPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSubscriptionPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSyncService
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.PortfolioVisibility
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking

/**
 * A real [ChainRegistry] over mocked pools, for callers that must leave connections to the registry: "a" is a
 * ranked network background sync keeps, "b" an unranked one it does not (until [needing] lists it). Every setup
 * is recorded in [configured] and every disconnect in [disconnected].
 */
internal class RegistryConnectionFixture(chainIdsNeedingSync: Set<String> = emptySet()) : AutoCloseable {
    private val ranked = joined("a", rank = 1)
    private val unranked = joined("b", rank = null)
    val rankedChain: Chain = mapChainLocalToChain(ranked)
    val unrankedChain: Chain = mapChainLocalToChain(unranked)
    val needing = MutableStateFlow(chainIdsNeedingSync)
    val configured: MutableList<String> = CopyOnWriteArrayList()
    val disconnected: MutableList<String> = CopyOnWriteArrayList()
    val registry: ChainRegistry

    init {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        val connections = mockk<ConnectionPool>(relaxed = true) {
            every { getConnectionOrNull(any()) } returns null
            every { removeConnection(any()) } answers { disconnected += firstArg<String>() }
        }
        val runtime = mockk<RuntimeSnapshot>()
        val providers = mockk<RuntimeProviderPool>(relaxed = true) {
            coEvery { awaitRuntimeProvider(any()) } returns mockk<RuntimeProvider> { coEvery { get() } returns runtime }
            every { getRuntimeProviderOrNull(any()) } returns null
        }
        val rows = MutableSharedFlow<List<JoinedChainInfo>>(replay = 1).also { it.tryEmit(listOf(ranked, unranked)) }
        val dao = mockk<ChainDao> {
            every { endpointMutationLane } returns EndpointMutationLane()
            every { joinChainInfoFlow() } returns rows
        }
        val assets = mockk<AssetReadOnlyCache> { every { observeChainIdsNeedingSync() } returns needing }
        val environment = object : ChainEnvironmentConfigurator {
            override suspend fun configure(chain: Chain) {
                configured += chain.id
            }
        }
        val configurators = mockk<ChainEnvironmentConfiguratorProvider> { every { provide(any()) } returns environment }
        registry = ChainRegistry(
            providers, connections, mockk<RuntimeSubscriptionPool>(relaxed = true), dao, mockk<ChainSyncService>(relaxed = true),
            mockk<RuntimeSyncService>(relaxed = true), mockk<NetworkStateService>(relaxed = true),
            mockk<EthereumConnectionPool>(relaxed = true), assets, ChainsRepository(dao), configurators,
            mockk<PortfolioVisibility>(relaxed = true),
            Dispatchers.Unconfined
        )
        registry.syncUp()
    }

    override fun close() {
        try {
            runBlocking { registry.scope.coroutineContext.job.cancelAndJoin() }
        } finally {
            unmockkStatic(Log::class)
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
