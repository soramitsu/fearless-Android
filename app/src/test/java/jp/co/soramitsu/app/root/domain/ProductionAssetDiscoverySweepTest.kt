package jp.co.soramitsu.app.root.domain

import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.model.AssetBalanceUpdateItem
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.api.data.BalanceLoader
import jp.co.soramitsu.wallet.impl.data.network.blockchain.balance.NetworkScanStateStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

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
        `when`(registry.syncedChains).thenReturn(MutableStateFlow(emptyList()))
        `when`(registry.checkChainSyncedUp(chain)).thenReturn(false)
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
    fun `inactive network is scanned with a temporary connection then disconnected`() = runTest {
        val chain = scannableChain("inactive")
        val registry = mock(ChainRegistry::class.java)
        `when`(registry.syncedChains).thenReturn(MutableStateFlow(emptyList()))
        `when`(registry.checkChainSyncedUp(chain)).thenReturn(false)
        val sweep = sweepFor(chain, registry)

        sweep.scanNextBatch(batchSize = 1)

        verify(registry).setupChain(chain)
        verify(registry).stopChain(chain)
    }

    @Test
    fun `pre-existing registry network remains connected after scan`() = runTest {
        val chain = scannableChain("managed")
        val registry = mock(ChainRegistry::class.java)
        `when`(registry.syncedChains).thenReturn(MutableStateFlow(listOf(chain)))
        val sweep = sweepFor(chain, registry)

        sweep.scanNextBatch(batchSize = 1)

        verify(registry, never()).setupChain(chain)
        verify(registry, never()).stopChain(chain)
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
