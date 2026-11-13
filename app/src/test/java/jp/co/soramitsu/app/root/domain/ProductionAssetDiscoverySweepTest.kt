package jp.co.soramitsu.app.root.domain

import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.model.AssetBalanceUpdateItem
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeProvider
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.wallet.api.data.BalanceLoader
import jp.co.soramitsu.wallet.impl.data.network.blockchain.balance.NetworkScanStateStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.test.runTest
import org.mockito.ArgumentMatchers.anySet
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

class ProductionAssetDiscoverySweepTest {

    @Test
    fun `catalog sweep includes inactive unranked production networks and excludes testnets`() {
        val inactiveUnranked = chain(id = "b-inactive", rank = null, testnet = false)
        val ranked = chain(id = "a-ranked", rank = 1, testnet = false)
        val testnet = chain(id = "c-testnet", rank = 2, testnet = true)

        val selected = ProductionAssetDiscoverySweep.productionChainsAfter(
            chains = listOf(inactiveUnranked, testnet, ranked),
            cursorChainId = null
        )

        assertEquals(listOf("a-ranked", "b-inactive"), selected.map(Chain::id))
    }

    @Test
    fun `catalog cursor resumes strictly after last attempted chain`() {
        val chains = listOf(
            chain("c", rank = null, testnet = false),
            chain("a", rank = null, testnet = false),
            chain("b", rank = null, testnet = false)
        )

        val selected = ProductionAssetDiscoverySweep.productionChainsAfter(chains, "a")

        assertEquals(listOf("b", "c"), selected.map(Chain::id))
    }

    @Test
    fun `wallet registry or refresh trigger resets durable catalog cursor`() {
        val preferences = mock(Preferences::class.java)
        val sweep = ProductionAssetDiscoverySweep(
            chainsRepository = mock(ChainsRepository::class.java),
            accountRepository = mock(AccountRepository::class.java),
            balanceLoaderProvider = mock(BalanceLoader.Provider::class.java),
            assetDao = mock(AssetDao::class.java),
            chainRegistry = mock(ChainRegistry::class.java),
            networkScanStateStore = mock(NetworkScanStateStore::class.java),
            preferences = preferences
        )

        assertTrue(sweep is AssetDiscoveryService)
        sweep.resetForFullSweep()

        verify(preferences).removeField(ProductionAssetDiscoverySweep.PREF_CURSOR_CHAIN_ID)
        verify(preferences).putLong(ProductionAssetDiscoverySweep.PREF_LAST_COMPLETED_AT, 0L)
    }

    @Test
    fun `discovery schedule is independent from presentation rollout switches`() {
        val preferences = mock(Preferences::class.java)
        val sweep: AssetDiscoveryService = ProductionAssetDiscoverySweep(
            chainsRepository = mock(ChainsRepository::class.java),
            accountRepository = mock(AccountRepository::class.java),
            balanceLoaderProvider = mock(BalanceLoader.Provider::class.java),
            assetDao = mock(AssetDao::class.java),
            chainRegistry = mock(ChainRegistry::class.java),
            networkScanStateStore = mock(NetworkScanStateStore::class.java),
            preferences = preferences
        )

        assertTrue(sweep.isDue(now = 1L))
    }

    @Test
    fun `shadow presentation rollout still scans and persists detected balances`() = runTest {
        val rolloutPreferences = mock(Preferences::class.java)
        `when`(
            rolloutPreferences.getBoolean("feature.asset_discovery_shadow.v1", true)
        ).thenReturn(true)
        assertTrue(ProductFeatureToggleStore(rolloutPreferences, mock(jp.co.soramitsu.common.data.network.config.MutationAuthorizationStore::class.java)).assetDiscoveryShadowEnabled)

        val chain = scannableChain("shadow-scan")
        val registry = mock(ChainRegistry::class.java)
        registry.leasesRunTheRead()
        val runtimeProvider = mock(RuntimeProvider::class.java)
        `when`(runtimeProvider.get()).thenReturn(mock(RuntimeSnapshot::class.java))
        `when`(registry.awaitRuntimeProvider(chain.id)).thenReturn(runtimeProvider)
        val chainsRepository = mock(ChainsRepository::class.java)
        `when`(chainsRepository.getChains()).thenReturn(listOf(chain))
        val account = mock(MetaAccount::class.java).also { account ->
            `when`(account.id).thenReturn(7L)
            `when`(account.initialized).thenReturn(true)
        }
        val accountRepository = mock(AccountRepository::class.java)
        `when`(accountRepository.allMetaAccounts()).thenReturn(listOf(account))
        val update = AssetBalanceUpdateItem(
            metaId = account.id,
            chainId = chain.id,
            accountId = byteArrayOf(1),
            id = "detected-mint",
            freeInPlanks = BigInteger.TEN
        )
        val loader = mock(BalanceLoader::class.java)
        `when`(loader.loadBalance(setOf(account))).thenReturn(listOf(update))
        val provider = mock(BalanceLoader.Provider::class.java)
        `when`(provider.invoke(chain)).thenReturn(loader)
        val assetDao = mock(AssetDao::class.java)
        val sweep = ProductionAssetDiscoverySweep(
            chainsRepository = chainsRepository,
            accountRepository = accountRepository,
            balanceLoaderProvider = provider,
            assetDao = assetDao,
            chainRegistry = registry,
            networkScanStateStore = mock(NetworkScanStateStore::class.java),
            preferences = mock(Preferences::class.java)
        )

        sweep.scanNextBatch(batchSize = 1)

        verify(loader).loadBalance(setOf(account))
        verify(assetDao).updateBalanceOrInsertPreservingPreference(update, null)
    }

    @Test
    fun `a substrate network's runtime is awaited within the sweep's budget before it is read`() = runTest {
        val chain = scannableChain("slow-runtime")
        val registry = mock(ChainRegistry::class.java)
        registry.leasesRunTheRead()
        val runtimeProvider = mock(RuntimeProvider::class.java)
        `when`(runtimeProvider.get()).thenReturn(mock(RuntimeSnapshot::class.java))
        `when`(registry.awaitRuntimeProvider(chain.id)).thenReturn(runtimeProvider)
        val (sweep, loader) = sweepOf(chain, registry)

        sweep.scanNextBatch(batchSize = 1)

        val order = inOrder(registry, runtimeProvider, loader)
        order.verify(registry).withDiscoveryConnection(eq(chain), any<suspend () -> Any?>())
        order.verify(registry).awaitRuntimeProvider(chain.id)
        order.verify(runtimeProvider).get()
        order.verify(loader).loadBalance(anySet())
    }

    @Test
    fun `an evm network has no runtime to wait for`() = runTest {
        val chain = scannableChain("evm-network")
        `when`(chain.isEthereumChain).thenReturn(true)
        val registry = mock(ChainRegistry::class.java)
        registry.leasesRunTheRead()
        val (sweep, loader) = sweepOf(chain, registry)

        sweep.scanNextBatch(batchSize = 1)

        verify(registry, never()).awaitRuntimeProvider(chain.id)
        verify(loader).loadBalance(anySet())
    }

    private suspend fun sweepOf(
        chain: Chain,
        registry: ChainRegistry
    ): Pair<ProductionAssetDiscoverySweep, BalanceLoader> {
        val chainsRepository = mock(ChainsRepository::class.java)
        `when`(chainsRepository.getChains()).thenReturn(listOf(chain))
        val account = mock(MetaAccount::class.java).also { account ->
            `when`(account.id).thenReturn(7L)
            `when`(account.initialized).thenReturn(true)
        }
        val accountRepository = mock(AccountRepository::class.java)
        `when`(accountRepository.allMetaAccounts()).thenReturn(listOf(account))
        val loader = mock(BalanceLoader::class.java)
        `when`(loader.loadBalance(setOf(account))).thenReturn(emptyList())
        val provider = mock(BalanceLoader.Provider::class.java)
        `when`(provider.invoke(chain)).thenReturn(loader)
        val sweep = ProductionAssetDiscoverySweep(
            chainsRepository = chainsRepository,
            accountRepository = accountRepository,
            balanceLoaderProvider = provider,
            assetDao = mock(AssetDao::class.java),
            chainRegistry = registry,
            networkScanStateStore = mock(NetworkScanStateStore::class.java),
            preferences = mock(Preferences::class.java)
        )
        return sweep to loader
    }

    @Test
    fun `catalog fingerprint ignores rank filters and active node selection`() {
        val ranked = catalogChain(rank = 1, active = true)
        val unranked = catalogChain(rank = null, active = false)

        assertEquals(
            AppInitializer.canonicalCatalogFingerprint(listOf(ranked)),
            AppInitializer.canonicalCatalogFingerprint(listOf(unranked))
        )
        assertFalse(AppInitializer.catalogChanged(listOf(ranked), listOf(unranked)))
    }

    @Test
    fun `changed registry requests an immediate catalog sweep`() {
        assertTrue(
            AppInitializer.catalogChanged(
                before = listOf(catalogChain(rank = null, active = false, assetId = "old")),
                after = listOf(catalogChain(rank = null, active = false, assetId = "new"))
            )
        )
    }

    @Test
    fun `a network that needs a connection is read through the registry's discovery lease`() = runTest {
        val chain = scannableChain("inactive")
        val registry = mock(ChainRegistry::class.java)
        registry.leasesRunTheRead()
        val sweep = sweepFor(chain, registry)

        sweep.scanNextBatch(batchSize = 1)

        verify(registry).withDiscoveryConnection(eq(chain), any<suspend () -> Any?>())
        // Connecting and disconnecting is the registry's decision (ChainRegistryDiscoveryLeaseTest).
        verify(registry, never()).setupChain(chain)
        verify(registry, never()).stopChain(chain)
    }

    @Test
    fun `a read that stores a funded balance waits for background sync to take the network over`() = runTest {
        val chain = scannableChain("funded")
        val registry = mock(ChainRegistry::class.java)
        registry.leasesRunTheRead()
        val assetDao = mock(AssetDao::class.java)
        val sweep = sweepReturning(chain, registry, assetDao, free = BigInteger.valueOf(28_900))

        sweep.scanNextBatch(batchSize = 1)

        val order = inOrder(assetDao, registry)
        order.verify(assetDao).updateBalanceOrInsertPreservingPreference(any(), eq(null))
        order.verify(registry).awaitBackgroundSync(chain.id, ProductionAssetDiscoverySweep.ADOPTION_WAIT_MILLIS)
    }

    @Test
    fun `a read that finds nothing does not wait for background sync`() = runTest {
        val chain = scannableChain("empty")
        val registry = mock(ChainRegistry::class.java)
        registry.leasesRunTheRead()
        val assetDao = mock(AssetDao::class.java)
        val sweep = sweepReturning(chain, registry, assetDao, free = BigInteger.ZERO)

        sweep.scanNextBatch(batchSize = 1)

        verify(assetDao).updateBalanceOrInsertPreservingPreference(any(), eq(null))
        verify(registry, never()).awaitBackgroundSync(chain.id, ProductionAssetDiscoverySweep.ADOPTION_WAIT_MILLIS)
    }

    @Test
    fun `a network without a registry connection is read without a lease`() = runTest {
        val chain = scannableChain("ton-mainnet")
        `when`(chain.ecosystem).thenReturn(Ecosystem.Ton)
        val registry = mock(ChainRegistry::class.java)
        val (sweep, loader) = sweepOf(chain, registry)

        sweep.scanNextBatch(batchSize = 1)

        verify(loader).loadBalance(anySet())
        verify(registry, never()).withDiscoveryConnection(any(), any<suspend () -> Any?>())
    }

    @Test
    fun `solana is read on its own client, without a lease or a runtime wait`() = runTest {
        // The catalog stores Solana as Substrate with an HTTP node. Waiting for a Substrate runtime
        // there timed out after 45 s on every sweep and marked Solana "Asset discovery timed out".
        val chain = scannableChain(UniversalWalletRegistry.solanaMainnet.id)
        val registry = mock(ChainRegistry::class.java)
        val (sweep, loader) = sweepOf(chain, registry)

        sweep.scanNextBatch(batchSize = 1)

        verify(loader).loadBalance(anySet())
        verify(registry, never()).withDiscoveryConnection(any(), any<suspend () -> Any?>())
        verify(registry, never()).awaitRuntimeProvider(chain.id)
    }

    @Test
    fun `funds follow the background sync rule for a stored row`() {
        fun update(free: BigInteger?, reserved: BigInteger? = null) = AssetBalanceUpdateItem(
            metaId = 1, chainId = "c", accountId = byteArrayOf(1), id = "a", freeInPlanks = free, reservedInPlanks = reserved
        )

        assertTrue(ProductionAssetDiscoverySweep.holdsFunds(update(BigInteger.ONE)))
        assertTrue(ProductionAssetDiscoverySweep.holdsFunds(update(BigInteger.ZERO, reserved = BigInteger.ONE)))
        assertFalse(ProductionAssetDiscoverySweep.holdsFunds(update(BigInteger.ZERO, reserved = BigInteger.ZERO)))
        assertFalse(ProductionAssetDiscoverySweep.holdsFunds(update(null)))
        // A load error is stored as -1 and is never a holding.
        assertFalse(ProductionAssetDiscoverySweep.holdsFunds(update(BigInteger.valueOf(-1))))
    }

    /** The lease runs the read here; who connects and disconnects is ChainRegistryDiscoveryLeaseTest's subject. */
    private suspend fun ChainRegistry.leasesRunTheRead() {
        whenever(withDiscoveryConnection(any(), any<suspend () -> Any?>())).doSuspendableAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            (invocation.arguments[1] as suspend () -> Any?).invoke()
        }
    }

    private suspend fun sweepReturning(
        chain: Chain,
        registry: ChainRegistry,
        assetDao: AssetDao,
        free: BigInteger
    ): ProductionAssetDiscoverySweep {
        val runtimeProvider = mock(RuntimeProvider::class.java)
        `when`(runtimeProvider.get()).thenReturn(mock(RuntimeSnapshot::class.java))
        `when`(registry.awaitRuntimeProvider(chain.id)).thenReturn(runtimeProvider)
        val chainsRepository = mock(ChainsRepository::class.java)
        `when`(chainsRepository.getChains()).thenReturn(listOf(chain))
        val account = mock(MetaAccount::class.java).also { account ->
            `when`(account.id).thenReturn(3L)
            `when`(account.initialized).thenReturn(true)
        }
        val accountRepository = mock(AccountRepository::class.java)
        `when`(accountRepository.allMetaAccounts()).thenReturn(listOf(account))
        val loader = mock(BalanceLoader::class.java)
        // Built before the stubbing: reading a mock inside when(...) leaves the stubbing unfinished.
        val update = AssetBalanceUpdateItem(metaId = 3L, chainId = chain.id, accountId = byteArrayOf(1), id = "xrt", freeInPlanks = free)
        `when`(loader.loadBalance(setOf(account))).thenReturn(listOf(update))
        val provider = mock(BalanceLoader.Provider::class.java)
        `when`(provider.invoke(chain)).thenReturn(loader)
        return ProductionAssetDiscoverySweep(
            chainsRepository = chainsRepository,
            accountRepository = accountRepository,
            balanceLoaderProvider = provider,
            assetDao = assetDao,
            chainRegistry = registry,
            networkScanStateStore = mock(NetworkScanStateStore::class.java),
            preferences = mock(Preferences::class.java)
        )
    }

    private fun chain(id: String, rank: Int?, testnet: Boolean): Chain {
        return mock(Chain::class.java).also { chain ->
            `when`(chain.id).thenReturn(id)
            `when`(chain.rank).thenReturn(rank)
            `when`(chain.isTestNet).thenReturn(testnet)
        }
    }

    private fun catalogChain(rank: Int?, active: Boolean, assetId: String = "canonical-asset-id"): Chain {
        val asset = mock(Asset::class.java).also { asset ->
            `when`(asset.id).thenReturn(assetId)
            `when`(asset.currencyId).thenReturn("currency-id")
            `when`(asset.precision).thenReturn(12)
            `when`(asset.priceId).thenReturn("price-id")
            `when`(asset.isNative).thenReturn(true)
        }
        return mock(Chain::class.java).also { chain ->
            `when`(chain.id).thenReturn("canonical-chain-id")
            `when`(chain.rank).thenReturn(rank)
            `when`(chain.isTestNet).thenReturn(false)
            `when`(chain.ecosystem).thenReturn(Ecosystem.Substrate)
            `when`(chain.assets).thenReturn(listOf(asset))
            `when`(chain.nodes).thenReturn(
                listOf(
                    ChainNode(
                        url = "wss://canonical.example",
                        name = "Canonical",
                        isActive = active,
                        isDefault = true
                    )
                )
            )
        }
    }

    private suspend fun sweepFor(
        chain: Chain,
        registry: ChainRegistry
    ): ProductionAssetDiscoverySweep {
        val chainsRepository = mock(ChainsRepository::class.java)
        `when`(chainsRepository.getChains()).thenReturn(listOf(chain))
        val account = mock(MetaAccount::class.java).also { account ->
            `when`(account.id).thenReturn(1L)
            `when`(account.initialized).thenReturn(true)
        }
        val accountRepository = mock(AccountRepository::class.java)
        `when`(accountRepository.allMetaAccounts()).thenReturn(listOf(account))
        val loader = mock(BalanceLoader::class.java)
        `when`(loader.loadBalance(setOf(account))).thenReturn(emptyList())
        val provider = mock(BalanceLoader.Provider::class.java)
        `when`(provider.invoke(chain)).thenReturn(loader)

        return ProductionAssetDiscoverySweep(
            chainsRepository = chainsRepository,
            accountRepository = accountRepository,
            balanceLoaderProvider = provider,
            assetDao = mock(AssetDao::class.java),
            chainRegistry = registry,
            networkScanStateStore = mock(NetworkScanStateStore::class.java),
            preferences = mock(Preferences::class.java)
        )
    }

    private fun scannableChain(id: String): Chain = mock(Chain::class.java).also { chain ->
        `when`(chain.id).thenReturn(id)
        `when`(chain.isTestNet).thenReturn(false)
        `when`(chain.ecosystem).thenReturn(Ecosystem.Substrate)
        `when`(chain.nodes).thenReturn(
            listOf(ChainNode("wss://$id.example", id, isActive = false, isDefault = true))
        )
        `when`(chain.assets).thenReturn(emptyList())
    }
}
