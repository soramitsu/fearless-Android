package jp.co.soramitsu.wallet.impl.di

import android.content.Context
import android.content.res.AssetManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.common.resources.ContextManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_wallet_impl.BuildConfig
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainSyncService
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.toChain
import jp.co.soramitsu.xcm.ReviewedBridgeRuntimeResolver
import jp.co.soramitsu.xcm.ReviewedPolkaswapBridgeProviderIds
import jp.co.soramitsu.xcm.domain.ReviewedWalletXcmRouteProvider
import jp.co.soramitsu.xcm.domain.XcmEntitiesFetcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.File

class CrossChainPausedReasonTest {

    @Test
    fun `cross-chain disabled with user copy when XCM paused`() = runBlocking {
        val resourceManager = mock(ResourceManager::class.java)
        `when`(resourceManager.getString(R.string.paused_cross_chain_message)).thenReturn(LOCALIZED_PAUSED)
        // Remote config cannot grant XCM or bridge authority; the mock reports every switch off.
        val toggles = mock(ProductFeatureToggleStore::class.java)

        val registry = WalletFeatureModule().provideCrossChainRouteProviderRegistry(
            xcmEntitiesFetcher = bundledXcmEntitiesFetcher(),
            reviewedBridgeRuntimeResolver = mock(ReviewedBridgeRuntimeResolver::class.java),
            productFeatureToggleStore = toggles,
            resourceManager = resourceManager
        )
        val capabilities = registry.capabilities().associateBy { it.providerId }

        for (providerId in listOf(
            ReviewedWalletXcmRouteProvider.PROVIDER_ID,
            ReviewedPolkaswapBridgeProviderIds.LIBERLAND
        )) {
            val capability = capabilities.getValue(providerId)
            assertFalse(providerId, capability.canExecute)
            assertEquals(providerId, LOCALIZED_PAUSED, capability.userFacingReason)
        }

        // Every SORA substrate route is retired, so this provider explains that instead of reporting a pause.
        val soraSubstrate = capabilities.getValue(ReviewedPolkaswapBridgeProviderIds.SORA_SUBSTRATE)
        assertFalse(soraSubstrate.canExecute)
        assertTrue(soraSubstrate.userFacingReason!!.contains("SORA parachains"))
        assertEquals(LOCALIZED_PAUSED, registry.bestCapability().userFacingReason)
    }

    @Test
    fun `remote switches alone cannot enable bridge actions in a build that compiles them off`() = runBlocking {
        assumeFalse("this build compiles bridge transfers on", BuildConfig.ENABLE_PRODUCTION_BRIDGE_TRANSFERS)
        val resourceManager = mock(ResourceManager::class.java)
        `when`(resourceManager.getString(R.string.paused_cross_chain_message)).thenReturn(LOCALIZED_PAUSED)
        val toggles = mock(ProductFeatureToggleStore::class.java)
        `when`(toggles.polkaswapBridgeMutationsEnabled).thenReturn(true)

        val registry = WalletFeatureModule().provideCrossChainRouteProviderRegistry(
            xcmEntitiesFetcher = bundledXcmEntitiesFetcher(),
            reviewedBridgeRuntimeResolver = mock(ReviewedBridgeRuntimeResolver::class.java),
            productFeatureToggleStore = toggles,
            resourceManager = resourceManager
        )
        val liberland = registry.capabilities().single { it.providerId == ReviewedPolkaswapBridgeProviderIds.LIBERLAND }

        assertFalse(liberland.canExecute)
        assertEquals(LOCALIZED_PAUSED, liberland.userFacingReason)
    }

    @Test
    fun `paused reason resolves through resources and not a literal`() {
        val resourceManager = mock(ResourceManager::class.java)
        `when`(resourceManager.getString(R.string.paused_cross_chain_message)).thenReturn(LOCALIZED_PAUSED)

        assertEquals(LOCALIZED_PAUSED, crossChainPausedReason(resourceManager)())
    }

    private fun bundledXcmEntitiesFetcher(): XcmEntitiesFetcher {
        val contextManager = mock(ContextManager::class.java)
        val context = mock(Context::class.java)
        val assets = mock(AssetManager::class.java)
        `when`(contextManager.getContext()).thenReturn(context)
        `when`(context.assets).thenReturn(assets)
        for (name in listOf("local_chains.json", "approved_xcm_routes.tsv")) {
            `when`(assets.open(name)).thenReturn(File("../runtime/src/main/assets/$name").inputStream())
        }
        val module = WalletFeatureModule()
        val chainsJson = File("../runtime/src/main/assets/local_chains.json").readText()
        val chainType = object : TypeToken<List<ChainRemote>>() {}.type
        val chains = Gson().fromJson<List<ChainRemote>>(chainsJson, chainType).map(ChainRemote::toChain)
        val discovery = mock(ChainSyncService::class.java)
        runBlocking { `when`(discovery.getCurrentProcessXcmDiscoveryChains()).thenReturn(chains) }
        return module.provideXcmEntitiesFetcher(discovery, module.provideApprovedXcmRouteRegistry(contextManager))
    }

    private companion object {
        const val LOCALIZED_PAUSED = "localized cross-chain paused copy"
    }
}
