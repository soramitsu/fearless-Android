package jp.co.soramitsu.app.root.domain

import android.util.Log
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.domain.NetworkStateService
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.dao.AssetReadOnlyCache
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.model.AssetBalanceUpdateItem
import jp.co.soramitsu.coredb.model.chain.ChainLocal
import jp.co.soramitsu.coredb.model.chain.ChainNodeLocal
import jp.co.soramitsu.coredb.model.chain.JoinedChainInfo
import jp.co.soramitsu.coredb.recovery.selection.EndpointMutationLane
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
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
import jp.co.soramitsu.wallet.api.data.BalanceLoader
import jp.co.soramitsu.wallet.impl.data.network.blockchain.balance.NetworkScanStateStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.whenever

/**
 * The daily sweep or a pull-to-refresh finds a balance on a network background sync does not keep
 * (a deposit to a network the wallet held nothing on). Storing the funded row makes background sync
 * select the network while the sweep still holds its read connection. That connection must survive
 * the handover: torn down here, the network would stay "synced" without a connection, and its
 * balance would stop updating until the app restarts.
 */
class DiscoveryHandoverTest {

    /** Background sync's own setup of the network is held, so its selection is made before it publishes. */
    private class HeldSecondSetup : ChainEnvironmentConfigurator {
        val calls = mutableListOf<String>()
        val release = CompletableDeferred<Unit>()

        override suspend fun configure(chain: Chain) {
            calls += chain.id
            if (calls.count { it == chain.id } > 1) release.await()
        }
    }

    @Test
    fun `network found funded by the sweep stays connected when background sync takes it over`() = runBlocking<Unit> {
        val androidLog = mockStatic(Log::class.java)
        val local = JoinedChainInfo(
            ChainLocal(
                "hydration-like", null, null, null, "Unranked", null, "", null, 42, false, false, false, false,
                true, false, false, false, null, "Ethereum", null, null, null, null
            ),
            listOf(ChainNodeLocal("hydration-like", "wss://unranked.invalid/", "Unranked", true, true)),
            emptyList(),
            emptyList()
        )
        val chain = mapChainLocalToChain(local)
        val needing = MutableStateFlow(emptySet<String>())
        val dao = mock(ChainDao::class.java)
        whenever(dao.endpointMutationLane).thenReturn(EndpointMutationLane())
        whenever(dao.joinChainInfoFlow()).thenReturn(MutableSharedFlow<List<JoinedChainInfo>>(replay = 1).also { it.tryEmit(listOf(local)) })
        val assets = mock(AssetReadOnlyCache::class.java)
        whenever(assets.observeChainIdsNeedingSync()).thenReturn(needing)
        val environment = HeldSecondSetup()
        val configurators = mock(ChainEnvironmentConfiguratorProvider::class.java)
        whenever(configurators.provide(any())).thenReturn(environment)
        val evmPool = mock(EthereumConnectionPool::class.java)
        val registry = ChainRegistry(
            mock(RuntimeProviderPool::class.java), mock(ConnectionPool::class.java), mock(RuntimeSubscriptionPool::class.java),
            dao, mock(ChainSyncService::class.java), mock(RuntimeSyncService::class.java), mock(NetworkStateService::class.java),
            evmPool, assets, ChainsRepository(dao), configurators, mock(PortfolioVisibility::class.java), Dispatchers.Unconfined
        )
        try {
            registry.syncUp()
            assertEquals(emptyList<String>(), registry.syncedChains.value.map(Chain::id))

            val account = mock(MetaAccount::class.java).also { account ->
                whenever(account.id).thenReturn(1L)
                whenever(account.initialized).thenReturn(true)
            }
            val accountRepository = mock(AccountRepository::class.java)
            whenever(accountRepository.allMetaAccounts()).thenReturn(listOf(account))
            val funded = AssetBalanceUpdateItem(
                metaId = 1L, chainId = chain.id, accountId = byteArrayOf(1), id = "dot", freeInPlanks = BigInteger.valueOf(18_084_223)
            )
            val loader = mock(BalanceLoader::class.java)
            whenever(loader.loadBalance(setOf(account))).thenReturn(listOf(funded))
            val loaders = mock(BalanceLoader.Provider::class.java)
            whenever(loaders.invoke(chain)).thenReturn(loader)
            val assetDao = mock(AssetDao::class.java)
            // Room's invalidation: the stored funded row makes the network need background sync.
            whenever(assetDao.updateBalanceOrInsertPreservingPreference(funded, null)).doSuspendableAnswer {
                needing.value = setOf(chain.id)
            }
            val catalog = mock(ChainsRepository::class.java)
            whenever(catalog.getChains()).thenReturn(listOf(chain))
            val sweep = ProductionAssetDiscoverySweep(
                chainsRepository = catalog,
                accountRepository = accountRepository,
                balanceLoaderProvider = loaders,
                assetDao = assetDao,
                chainRegistry = registry,
                networkScanStateStore = mock(NetworkScanStateStore::class.java),
                preferences = mock(Preferences::class.java)
            )

            sweep.scanNextBatch(batchSize = 1)
            environment.release.complete(Unit)

            verify(evmPool, never()).stop(chain.id)
            assertEquals(listOf(chain.id), registry.syncedChains.value.map(Chain::id))
            assertEquals(listOf(chain.id, chain.id), environment.calls)
        } finally {
            registry.scope.coroutineContext.job.cancelAndJoin()
            androidLog.close()
        }
    }
}
