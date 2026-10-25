package jp.co.soramitsu.wallet.impl.di

import android.content.Context
import android.content.res.AssetManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import jp.co.soramitsu.common.resources.ContextManager
import jp.co.soramitsu.feature_wallet_impl.BuildConfig
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainSyncService
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.toChain
import jp.co.soramitsu.xcm.XcmExtrinsicSubmitter
import jp.co.soramitsu.xcm.XcmTransferRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.File

class XcmReleaseConfigurationTest {

    @Test
    fun `compiled disable keeps the empty approved registry loadable and exposes no retired route`() = runBlocking<Unit> {
        assertFalse(BuildConfig.ENABLE_PRODUCTION_XCM_TRANSFERS)
        val contextManager = mock(ContextManager::class.java)
        val context = mock(Context::class.java)
        val assets = mock(AssetManager::class.java)
        `when`(contextManager.getContext()).thenReturn(context)
        `when`(context.assets).thenReturn(assets)
        for (name in listOf("local_chains.json", "approved_xcm_routes.tsv")) {
            `when`(assets.open(name)).thenReturn(File("../runtime/src/main/assets/$name").inputStream())
        }

        val module = WalletFeatureModule()
        val approved = module.provideApprovedXcmRouteRegistry(contextManager)
        val chainsJson = File("../runtime/src/main/assets/local_chains.json").readText()
        val chainType = object : TypeToken<List<ChainRemote>>() {}.type
        val chains = Gson().fromJson<List<ChainRemote>>(chainsJson, chainType).map(ChainRemote::toChain)
        val freshDiscovery = mock(ChainSyncService::class.java)
        `when`(freshDiscovery.getCurrentProcessXcmDiscoveryChains()).thenReturn(chains)
        val fetcher = module.provideXcmEntitiesFetcher(freshDiscovery, approved)
        val routes = File("../runtime/src/main/assets/approved_xcm_routes.tsv").readLines()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .map { it.split(Regex("\\s+")) }
        // The allowlist is intentionally empty: the former relay-native routes are retired (see approved_xcm_routes.tsv).
        assertEquals(0, routes.size)
        for ((origin, destination, symbol) in RETIRED_ROUTES) {
            assertNull("Retired $symbol route $origin -> $destination must not be exposed", fetcher.getRouteAsset(origin, destination, symbol))
        }

        verify(assets).open("local_chains.json")
        verify(assets).open("approved_xcm_routes.tsv")
    }

    @Test
    fun `release preserves quote engine but rejects direct submit despite enabled remote switch`() {
        assertFalse(BuildConfig.ENABLE_PRODUCTION_XCM_TRANSFERS)
        val submitter = mock(XcmExtrinsicSubmitter::class.java)
        val toggles = mock(ProductFeatureToggleStore::class.java)
        `when`(toggles.xcmMutationsEnabled).thenReturn(true)
        val engine = WalletFeatureModule().provideXcmTransferEngine(submitter, toggles)

        assertTrue(engine.isAvailable)
        val failure = assertThrows(IllegalStateException::class.java) {
            runBlocking { engine.transfer(mock(XcmTransferRequest::class.java)) }
        }
        assertEquals("Reviewed XCM actions are unavailable in this build.", failure.message)
        verifyNoInteractions(submitter)
    }

    private companion object {
        const val POLKADOT = "91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3"
        const val POLKADOT_ASSET_HUB = "68d56f15f85d3136970ec16946040bc1752654e906147f7e43e9d539d7c3de2f"
        const val ACALA = "fc41b9bd8ef8fe53d58c7ea67c794c7ec9a73daf05e6d54b14ff6342c99ba64c"
        const val MOONBEAM = "fe58ea77779b7abda7da4ec526d14db9b1e9cd40a217c34892af80a9b332b76d"

        // Former approved routes: a dead destination, a relay reserve transfer that now reverts on chain, and the
        // relay to Asset Hub pair that the engine cannot execute (the relay's XCM pallet is XcmPallet, not PolkadotXcm).
        val RETIRED_ROUTES = listOf(
            Triple(POLKADOT, MOONBEAM, "DOT"),
            Triple(POLKADOT, ACALA, "DOT"),
            Triple(POLKADOT, POLKADOT_ASSET_HUB, "DOT"),
            Triple(POLKADOT_ASSET_HUB, POLKADOT, "DOT")
        )
    }
}
