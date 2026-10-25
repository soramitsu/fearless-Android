package jp.co.soramitsu.staking.impl.data

import android.util.Log
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.domain.NetworkStateService
import jp.co.soramitsu.coredb.dao.AssetReadOnlyCache
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.model.chain.ChainAssetLocal
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
import jp.co.soramitsu.staking.api.data.StakingAssetSelection
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.data.StakingType
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Real registry + repository + local mapper; only raw DAO emissions and runtime setup are controlled. */
class StakingOfflineCatalogTest {
    private class Fixture(emitInitial: Boolean = true, encoded: String? = "a:0:POOL") : AutoCloseable {
        private val androidLog = mockStatic(Log::class.java)
        val local = JoinedChainInfo(
            ChainLocal("a", null, null, 1, "Local chain", null, "", null, 42,
                false, false, false, true, false, false, false, false, null,
                "Substrate", null, null, null, null),
            listOf(ChainNodeLocal("a", "wss://public.invalid", "Public test node", true, true)),
            listOf(ChainAssetLocal("0", "Local token", "TOK", "a", "", null, "RELAYCHAIN", 12,
                null, true, null, null, null, null, true, null, null)), emptyList()
        )
        val rows = MutableSharedFlow<List<JoinedChainInfo>>(replay = 1)
        val setupStarted = CompletableDeferred<Unit>()
        val releaseSetup = CompletableDeferred<Unit>()
        val failures = java.util.concurrent.CopyOnWriteArrayList<Throwable>()
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob() + CoroutineExceptionHandler { _, t -> failures += t })
        val preferences = mock(Preferences::class.java)
        val registry: ChainRegistry
        val repository: ChainsRepository
        val state: StakingSharedState
        init {
            if (emitInitial) check(rows.tryEmit(listOf(local)))
            val dao = mock(ChainDao::class.java)
            whenever(dao.endpointMutationLane).thenReturn(EndpointMutationLane())
            whenever(dao.joinChainInfoFlow()).thenReturn(rows)
            repository = ChainsRepository(dao)
            val readCache = mock(AssetReadOnlyCache::class.java)
            whenever(readCache.observeChainIdsNeedingSync()).thenReturn(flowOf(emptySet()))
            val environment = object : ChainEnvironmentConfigurator {
                override suspend fun configure(chain: Chain) {
                    setupStarted.complete(Unit)
                    releaseSetup.await()
                }
            }
            val configurators = mock(ChainEnvironmentConfiguratorProvider::class.java)
            whenever(configurators.provide(any())).thenReturn(environment)
            registry = ChainRegistry(mock(RuntimeProviderPool::class.java), mock(ConnectionPool::class.java),
                mock(RuntimeSubscriptionPool::class.java), dao, mock(ChainSyncService::class.java),
                mock(RuntimeSyncService::class.java), mock(NetworkStateService::class.java),
                mock(EthereumConnectionPool::class.java), readCache, repository, configurators,
                mock(PortfolioVisibility::class.java), Dispatchers.Unconfined)
            val account = MetaAccount(42, emptyMap(), emptyMap(), ByteArray(32) { 7 }, null,
                ByteArray(32) { 7 }, null, null, null, true, true, null, "Public test wallet", true)
            val accounts = mock(AccountRepository::class.java)
            whenever(accounts.selectedMetaAccountFlow()).thenReturn(flowOf(account))
            whenever(preferences.stringFlow(any(), anyOrNull())).thenReturn(flowOf(encoded))
            val initial = Asset.createEmpty(mapChainLocalToChain(local).assets.single(), 42, ByteArray(32) { 7 }, minSupportedVersion = null)
            val wallets = object : WalletRepository by mock(WalletRepository::class.java) {
                override suspend fun getAssets(metaId: Long): List<Asset> = listOf(initial)
            }
            state = StakingSharedState(registry, preferences, wallets, accounts, repository, scope)
            registry.syncUp()
        }
        suspend fun assertSetupBlocked() {
            withTimeout(2_000) { setupStarted.await() }
            assertFalse(releaseSetup.isCompleted)
            assertTrue(registry.currentChains.replayCache.isEmpty())
        }
        override fun close() {
            try {
                runBlocking {
                    scope.coroutineContext.job.cancelAndJoin()
                    registry.scope.coroutineContext.job.cancelAndJoin()
                }
                verify(preferences, never()).putString(any(), anyOrNull())
                assertTrue("No hidden coroutine failures: $failures", failures.isEmpty())
            } finally { androidLog.close() }
        }
    }

    @Test fun cachedPoolSelectionIsAvailableBeforeRuntimeSetupCompletes() = runBlocking<Unit> {
        Fixture().use { f ->
            f.assertSetupBlocked()
            assertEquals(StakingAssetSelection.Pool("a", "0"), withTimeoutOrNull(1_000) { f.state.selectionItem.first() })
            assertEquals("Local chain", withTimeoutOrNull(1_000) { f.state.chain().name })
            assertTrue(f.state.selectionRevision.matchesAction(f.state.selectionRevision.capture(), StakingType.POOL, "a", "0"))
            assertTrue(f.registry.currentChains.replayCache.isEmpty())
        }
    }

    @Test fun localRemovalAndRecoveryRevokeOldAuthorityWhileRuntimeSetupRemainsBlocked() = runBlocking<Unit> {
        Fixture().use { f ->
            f.assertSetupBlocked()
            assertNotNull(withTimeoutOrNull(1_000) { f.state.selectionItem.first() })
            val captured = f.state.selectionRevision.capture()
            f.rows.emit(emptyList())
            assertEquals(emptyList<StakingAssetSelection>(), withTimeoutOrNull(1_000) { f.state.currentAssetAvailability.first { it == null }; f.state.availableToSelect() })
            assertFalse(f.state.selectionRevision.matchesAction(captured, StakingType.POOL, "a", "0"))
            assertNull(withTimeoutOrNull(100) { f.state.currentToken() })
            f.rows.emit(listOf(f.local))
            assertEquals(StakingAssetSelection.Pool("a", "0"), withTimeoutOrNull(1_000) { f.state.selectionItem.first() })
            assertFalse(f.state.selectionRevision.matchesAction(captured, StakingType.POOL, "a", "0"))
            assertTrue(f.state.selectionRevision.matchesAction(f.state.selectionRevision.capture(), StakingType.POOL, "a", "0"))
            assertTrue(f.registry.currentChains.replayCache.isEmpty())
        }
    }

    @Test fun localAssetRefreshReplacesConfigurationWhileRuntimeSetupRemainsBlocked() = runBlocking<Unit> {
        Fixture().use { f ->
            f.assertSetupBlocked()
            assertNotNull(withTimeoutOrNull(1_000) { f.state.currentToken() })
            val captured = f.state.selectionRevision.capture()
            f.rows.emit(listOf(f.local.copy(chain = f.local.chain.copy(name = "Changed local chain"),
                assets = f.local.assets.map { it.copy(precision = 8) })))
            val token = withTimeoutOrNull(1_000) { f.state.currentTokenFlow().first { it.configuration.precision == 8 } }
            assertNotNull(token)
            assertEquals("Changed local chain", withTimeoutOrNull(1_000) { f.state.assetWithChain.first { it.chain.name == "Changed local chain" }.chain.name })
            assertFalse(f.state.selectionRevision.matchesAction(captured, StakingType.POOL, "a", "0"))
            assertTrue(f.registry.currentChains.replayCache.isEmpty())
        }
    }

    @Test fun unavailableExplicitChoiceReturnsOptionsWithoutFallbackWhileSetupIsBlocked() = runBlocking<Unit> {
        Fixture(encoded = "missing:0:POOL").use { f ->
            f.assertSetupBlocked()
            assertEquals(2, withTimeoutOrNull(1_000) { f.state.availableToSelect().size })
            assertNull(withTimeoutOrNull(100) { f.state.selectionItem.first() })
            assertFalse(f.state.selectionRevision.matchesAction(f.state.selectionRevision.capture(), StakingType.POOL, "a", "0"))
        }
    }

    @Test fun noDatabaseSnapshotDoesNotInventSelectionOrOptions() = runBlocking<Unit> {
        Fixture(emitInitial = false).use { f ->
            assertNull(withTimeoutOrNull(100) { f.state.availableToSelect() })
            assertNull(withTimeoutOrNull(100) { f.state.selectionItem.first() })
            assertFalse(f.setupStarted.isCompleted)
            assertTrue(f.registry.currentChains.replayCache.isEmpty())
            assertFalse(f.state.selectionRevision.matchesAction(f.state.selectionRevision.capture(), StakingType.POOL, "a", "0"))
        }
    }

    @Test fun releasingRuntimeSetupAllowsTheLegacyRegistryPublication() = runBlocking<Unit> {
        Fixture().use { f ->
            f.assertSetupBlocked()
            f.releaseSetup.complete(Unit)
            assertEquals("a", withTimeoutOrNull(1_000) { f.registry.currentChains.first().single().id })
            assertEquals(StakingAssetSelection.Pool("a", "0"), withTimeoutOrNull(1_000) { f.state.selectionItem.first() })
        }
    }
}
