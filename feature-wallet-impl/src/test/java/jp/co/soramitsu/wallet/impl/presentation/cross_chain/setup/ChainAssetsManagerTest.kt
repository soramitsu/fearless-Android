package jp.co.soramitsu.wallet.impl.presentation.cross_chain.setup

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.api.presentation.WalletRouter as WalletRouterApi
import jp.co.soramitsu.wallet.impl.WalletTestModels
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.xcm.domain.CrossChainAssetIdentity
import jp.co.soramitsu.xcm.domain.CrossChainProtocol
import jp.co.soramitsu.xcm.domain.CrossChainRouteAvailability
import jp.co.soramitsu.xcm.domain.CrossChainRouteCapability
import jp.co.soramitsu.xcm.domain.CrossChainRouteProviderRegistry
import jp.co.soramitsu.xcm.domain.XcmAsset
import jp.co.soramitsu.xcm.domain.XcmEntitiesFetcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChainAssetsManagerTest {

    private val dot = WalletTestModels.asset(id = "dot", chainId = "polkadot")
    private val glmr = WalletTestModels.asset(id = "glmr", chainId = "polkadot", isUtility = false)
    private val ksm = WalletTestModels.asset(id = "ksm", chainId = "kusama")
    private val chains = mapOf(
        "polkadot" to WalletTestModels.chain(id = "polkadot", assets = listOf(dot, glmr)),
        "kusama" to WalletTestModels.chain(id = "kusama", assets = listOf(ksm)),
        "hub" to WalletTestModels.chain(id = "hub", name = "Asset Hub")
    )
    private val walletAsset = mockk<Asset>(relaxed = true) { every { token.configuration } returns dot }
    private val interactor = mockk<WalletInteractor>(relaxed = true) {
        chains.forEach { (id, chain) -> coEvery { getChain(id) } returns chain }
        coEvery { getCurrentAsset("polkadot", "dot") } returns walletAsset
        every { assetsFlow() } returns flowOf(
            listOf(withStatus(dot), withStatus(glmr), withStatus(ksm))
        )
    }
    private val resources = mockk<ResourceManager> {
        every { getString(R.string.common_origin_network) } returns "From"
        every { getString(R.string.common_destination_network) } returns "To"
    }
    private val chainResults = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val assetResults = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val router = mockk<WalletRouter>(relaxed = true) {
        every { observeResult<String>(WalletRouterApi.KEY_CHAIN_ID) } returns chainResults
        every { observeResult<String>(WalletRouterApi.KEY_ASSET_ID) } returns assetResults
    }
    private val xcm = mockk<XcmEntitiesFetcher> {
        coEvery { getAvailableAssets("polkadot", null, any()) } returns listOf(XcmAsset("dot", "DOT", null, "polkadot", "dot", 10))
        coEvery { getAvailableAssets("kusama", null, any()) } returns emptyList()
        coEvery { getAvailableDestinationChains(any(), any(), any()) } returns listOf("kusama")
    }
    private val bridge = CrossChainRouteCapability(
        providerId = "bridge", protocol = CrossChainProtocol.PolkaswapSoraSubstrate, availability = CrossChainRouteAvailability.Available,
        actionsEnabled = true, requiresAccount = true, requiresSigning = true,
        supportedOriginNetworkIds = setOf("polkadot"), supportedDestinationNetworkIds = setOf("hub"),
        supportedAssets = setOf(CrossChainAssetIdentity("polkadot", "glmr", "GLMR"))
    )
    private val registry = mockk<CrossChainRouteProviderRegistry> {
        coEvery { capabilities(any()) } returns listOf(bridge)
        coEvery { bestCapability(any()) } returns bridge
        coEvery { inventoryCapabilities() } returns listOf(bridge)
    }
    private val manager = ChainAssetsManager(interactor, resources, router, xcm, registry)

    @Test
    fun initialIdsPickTheReachableAssetAndDestination(): Unit = runBlocking {
        manager.setInitialIds("polkadot", "dot")

        assertEquals("polkadot", manager.originChainIdFlow.value)
        assertEquals("dot", manager.assetIdFlow.value)
        assertEquals("kusama", manager.destinationChainId)
        assertEquals(walletAsset, manager.assetFlow.first())
        assertEquals("DOT", manager.assetSymbol)
        assertEquals("Polkadot", manager.originChainSelectorStateFlow.first().subTitle)
        assertEquals("From", manager.originChainSelectorStateFlow.first().title)
        assertEquals("Kusama", manager.destinationChainSelectorStateFlow.first().subTitle)
        assertEquals("Kusama", manager.destinationSelectedChainFlow.first()!!.name)
        assertTrue(manager.hasAvailableRoute())
        assertEquals(bridge, manager.routeCapability())
        val inventory = manager.routeInventory()
        assertEquals(listOf("Asset Hub"), inventory.single().destinationNetworks.map { it.name })
    }

    @Test
    fun unsupportedAssetFallsBackToAReachableOneAndChosenDestinationsStay(): Unit = runBlocking {
        manager.setInitialIds("polkadot", "unknown")
        manager.setInitialIds("polkadot", "glmr")
        manager.destinationChainIdFlow.value = "hub"
        manager.setInitialIds("kusama", "ksm")

        assertEquals("ksm", manager.assetIdFlow.value)
        assertEquals("kusama", manager.originChainIdFlow.value)
        assertEquals("hub", manager.destinationChainId)
    }

    @Test
    fun pickerResultsUpdateTheOriginOrDestination(): Unit = runBlocking {
        val scope = TestScope(UnconfinedTestDispatcher())
        val errors = mutableListOf<Throwable>()
        manager.observeChainIdAndAssetIdResult(scope, ChainType.Destination) { errors += it }
        scope.runCurrent()
        chainResults.emit("hub")
        assetResults.emit("glmr")
        scope.runCurrent()

        assertEquals("hub", manager.destinationChainId)
        assertEquals("glmr", manager.assetIdFlow.value)

        manager.observeChainIdAndAssetIdResult(scope, ChainType.Origin) { errors += it }
        scope.runCurrent()
        chainResults.emit("polkadot")
        scope.runCurrent()
        assertEquals("polkadot", manager.originChainIdFlow.value)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun failingPickerResultsReachTheErrorHandler(): Unit = runBlocking {
        every { router.observeResult<String>(WalletRouterApi.KEY_CHAIN_ID) } returns flow { throw IllegalStateException("picker") }
        val scope = TestScope(UnconfinedTestDispatcher())
        val errors = mutableListOf<Throwable>()

        manager.observeChainIdAndAssetIdResult(scope, ChainType.Origin) { errors += it }
        scope.runCurrent()
        scope.runCurrent()

        assertEquals("picker", errors.single().message)
    }

    private fun withStatus(asset: jp.co.soramitsu.core.models.Asset) = mockk<AssetWithStatus>(relaxed = true) {
        every { this@mockk.asset.token.configuration } returns asset
    }
}
