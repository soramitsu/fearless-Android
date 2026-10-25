package jp.co.soramitsu.wallet.impl.di

import com.google.gson.JsonParser
import java.io.File
import java.math.BigInteger
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_wallet_impl.BuildConfig
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.xcm.ReviewedBridgeRuntimeIdentity
import jp.co.soramitsu.xcm.ReviewedBridgeRuntimeResolution
import jp.co.soramitsu.xcm.ReviewedBridgeRuntimeResolver
import jp.co.soramitsu.xcm.ReviewedPolkaswapBridgeCatalog
import jp.co.soramitsu.xcm.ReviewedPolkaswapBridgeProviderIds
import jp.co.soramitsu.xcm.ReviewedPolkaswapBridgeRouteProvider
import jp.co.soramitsu.xcm.domain.CrossChainProtocol
import jp.co.soramitsu.xcm.domain.CrossChainRouteAvailability
import jp.co.soramitsu.xcm.domain.XcmEntitiesFetcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class XcmBridgeCatalogReleaseContractTest {

    @Test
    fun `release retires every SORA substrate bridge route and keeps the reviewed identities for requalification`() {
        assertTrue(
            ReviewedPolkaswapBridgeCatalog.executableForProvider(ReviewedPolkaswapBridgeProviderIds.SORA_SUBSTRATE)
                .isEmpty()
        )
        val routes = ReviewedPolkaswapBridgeCatalog.retiredRoutes.filter {
            it.providerId == ReviewedPolkaswapBridgeProviderIds.SORA_SUBSTRATE
        }
        val outgoing = routes.filter { it.originChainId == ReviewedPolkaswapBridgeCatalog.SORA_CHAIN_ID }
        val incoming = routes.filter { it.destinationChainId == ReviewedPolkaswapBridgeCatalog.SORA_CHAIN_ID }

        assertEquals(8, routes.size)
        assertEquals(setOf("KSM", "DOT", "ACA", "ASTR"), outgoing.mapTo(linkedSetOf()) { it.symbol })
        assertEquals(setOf("DOT", "KSM", "ACA", "ASTR"), incoming.mapTo(linkedSetOf()) { it.symbol })
        assertEquals(
            mapOf("KSM" to null, "DOT" to "1.1", "ACA" to "1.1", "ASTR" to null),
            outgoing.associate { it.symbol to it.minimumAmount }
        )
        assertEquals(
            mapOf("DOT" to "1.1", "KSM" to "0.05", "ACA" to "56", "ASTR" to "73"),
            incoming.associate { it.symbol to it.minimumAmount }
        )
        assertEquals(
            setOf(
                "5416b261-a759-4ba6-bc83-ea79a83c5101",
                "cd092a5a-4eb6-4318-9f11-4bf8454d67a2",
                "fb88fa55-b8c8-4ff1-afa8-f72a86a238a4",
                "acc32ee0-8fdc-4743-91e1-f70cc4f3069b"
            ),
            outgoing.mapTo(linkedSetOf()) { it.originAssetId }
        )
        assertTrue(outgoing.all { it.execution.runtimeCall.arguments == listOf("networkId", "assetId", "recipient", "amount") })
        assertEquals(
            setOf(
                "887a17c7-1370-4de0-97dd-5422e294fa75",
                "1e0c2ec6-935f-49bd-a854-5e12ee6c9f1b",
                "c801d6c1-3edf-41a9-9aea-da705eab249b",
                "5ab1e8d-81ed-4130-9d29-55b549cc6bab"
            ),
            incoming.mapTo(linkedSetOf()) { it.originAssetId }
        )
        assertEquals(
            8,
            ReviewedPolkaswapBridgeCatalog.unavailableForProvider(ReviewedPolkaswapBridgeProviderIds.SORA_SUBSTRATE).size
        )
    }

    @Test
    fun `release executes only Liberland to SORA and keeps SORA to Liberland retired`() {
        val executable = ReviewedPolkaswapBridgeCatalog.executableForProvider(
            ReviewedPolkaswapBridgeProviderIds.LIBERLAND
        )
        assertEquals(3, executable.size)
        assertTrue(executable.all { it.originChainId == ReviewedPolkaswapBridgeCatalog.LIBERLAND_CHAIN_ID })
        assertEquals(setOf("LLD", "LLM", "XOR"), executable.mapTo(linkedSetOf()) { it.symbol })

        val reverseLld = executable.single { it.symbol == "LLD" }
        assertEquals("1.1", reverseLld.minimumAmount)
        assertEquals("a6b83d39-a488-4b34-8352-280705a792ea", reverseLld.originCanonicalAssetId)
        assertEquals("a6b83d39-a488-4b34-8352-280705a792e", reverseLld.originXcmAssetId)

        val retired = ReviewedPolkaswapBridgeCatalog.unavailableForProvider(
            ReviewedPolkaswapBridgeProviderIds.LIBERLAND
        )
        assertEquals(3, retired.size)
        assertTrue(retired.all { it.originChainId == ReviewedPolkaswapBridgeCatalog.SORA_CHAIN_ID })
        assertTrue(retired.all { it.reason.contains("SORA to Liberland") })
    }

    @Test
    fun `release compiles bridge submissions off whatever the remote switch says`() = runBlocking {
        assertFalse(BuildConfig.ENABLE_PRODUCTION_BRIDGE_TRANSFERS)
        val toggles = mock(ProductFeatureToggleStore::class.java)
        `when`(toggles.polkaswapBridgeMutationsEnabled).thenReturn(true)
        val resourceManager = mock(ResourceManager::class.java)
        `when`(resourceManager.getString(jp.co.soramitsu.feature_wallet_impl.R.string.paused_cross_chain_message))
            .thenReturn("paused")

        val registry = WalletFeatureModule().provideCrossChainRouteProviderRegistry(
            // The compatibility fetcher exposes no XCM route; this test is about the bridge providers.
            xcmEntitiesFetcher = XcmEntitiesFetcher(mock(ChainRegistry::class.java)),
            reviewedBridgeRuntimeResolver = runtimeResolver,
            productFeatureToggleStore = toggles,
            resourceManager = resourceManager
        )

        assertTrue(registry.capabilities().none { it.canExecute })
    }

    @Test
    fun `release inventory survives kill switch but no route can mutate`() = runBlocking {
        val provider = ReviewedPolkaswapBridgeRouteProvider(
            providerId = ReviewedPolkaswapBridgeProviderIds.LIBERLAND,
            protocol = CrossChainProtocol.Liberland,
            runtimeResolver = runtimeResolver,
            actionsEnabled = { false },
            actionsDisabledReason = { "Polkaswap bridge actions are temporarily disabled." }
        )

        val inventory = provider.inventoryCapabilities()
        val executable = inventory.filter { it.providerContext != null }
        assertEquals(6, inventory.size)
        assertEquals(3, executable.size)
        assertTrue(executable.all { it.hasRoute })
        assertTrue(inventory.none { it.canExecute })
        assertTrue(executable.all { it.userFacingReason == "Polkaswap bridge actions are temporarily disabled." })
        assertTrue(
            inventory.filter { it.providerContext == null }.all {
                it.availability == CrossChainRouteAvailability.Unavailable
            }
        )
    }

    @Test
    fun `release keeps both ethereum directions explicitly unavailable`() = runBlocking {
        val provider = ReviewedPolkaswapBridgeRouteProvider(
            providerId = ReviewedPolkaswapBridgeProviderIds.SORA_EVM,
            protocol = CrossChainProtocol.PolkaswapSoraEvm,
            runtimeResolver = runtimeResolver,
            actionsEnabled = { true },
            actionsDisabledReason = { "unavailable" }
        )
        val inventory = provider.inventoryCapabilities()

        assertEquals(2, inventory.size)
        assertTrue(inventory.none { it.canExecute || it.hasRoute })
        assertTrue(inventory.any { it.userFacingReason!!.contains("second claim transaction") })
        assertTrue(inventory.any { it.userFacingReason!!.contains("gas and allowance checks") })
        assertFalse(inventory.any { it.providerContext != null })
    }

    @Test
    fun `every executable route matches the bundled V3 chain and asset identities`() {
        val chains = JsonParser.parseString(localChainsFile().readText()).asJsonArray
            .associateBy { it.asJsonObject["chainId"].asString }

        ReviewedPolkaswapBridgeCatalog.executableRoutes.forEach { route ->
            val origin = requireNotNull(chains[route.originChainId]).asJsonObject
            val destination = requireNotNull(chains[route.destinationChainId]).asJsonObject
            assertEquals("v3", origin["xcm"].asJsonObject["xcmVersion"].asString.lowercase())
            assertEquals("v3", destination["xcm"].asJsonObject["xcmVersion"].asString.lowercase())

            val originAsset = origin["assets"].asJsonArray
                .map { it.asJsonObject }
                .single { it["id"].asString == route.originAssetId }
            val currencyId = originAsset["currencyId"]
                ?.takeUnless { it.isJsonNull }
                ?.asString
                ?: route.originAssetId
            assertEquals(route.originCanonicalAssetId, currencyId)
            assertEquals(route.symbol, originAsset["symbol"].asString.uppercase())
            assertEquals(route.precision, originAsset["precision"].asInt)

            val xcm = origin["xcm"].asJsonObject
            assertEquals(
                1,
                xcm["availableAssets"].asJsonArray.count {
                    it.asJsonObject["id"].asString == route.originXcmAssetId &&
                        it.asJsonObject["symbol"].asString.uppercase() == route.symbol
                }
            )
            val reviewedDestinations = xcm["availableDestinations"].asJsonArray
                .map { it.asJsonObject }
                .filter { it["chainId"].asString == route.destinationChainId }
            assertEquals(1, reviewedDestinations.size)
            assertEquals(
                1,
                reviewedDestinations.single()["assets"].asJsonArray.count {
                    it.asJsonObject["id"].asString == route.destinationXcmAssetId &&
                        it.asJsonObject["symbol"].asString.uppercase() == route.symbol
                }
            )
        }
    }

    private companion object {
        fun localChainsFile(): File = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) {
            it.parentFile
        }.map { File(it, "runtime/src/main/assets/local_chains.json") }
            .firstOrNull(File::isFile)
            ?: error("runtime/src/main/assets/local_chains.json not found")

        val runtimeResolver = ReviewedBridgeRuntimeResolver { route ->
            ReviewedBridgeRuntimeResolution(
                originRuntime = ReviewedBridgeRuntimeIdentity(
                    genesisHash = "0x${route.originChainId}",
                    specName = "release-origin",
                    specVersion = 1,
                    transactionVersion = 1
                ),
                destinationRuntime = ReviewedBridgeRuntimeIdentity(
                    genesisHash = "0x${route.destinationChainId}",
                    specName = "release-destination",
                    specVersion = 1,
                    transactionVersion = 1
                ),
                moduleName = route.execution.runtimeCall.pallet,
                callName = route.execution.runtimeCall.call,
                rawArgumentNames = route.execution.runtimeCall.arguments,
                registrationFingerprint = "release-registration-v1",
                minimumFingerprint = "release-minimum-v1",
                runtimeMinimumInPlanks = BigInteger.ONE
            )
        }
    }
}
