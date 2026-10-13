package jp.co.soramitsu.wallet.impl.presentation.cross_chain.setup

import java.io.File
import jp.co.soramitsu.xcm.domain.CrossChainAssetIdentity
import jp.co.soramitsu.xcm.domain.CrossChainProtocol
import jp.co.soramitsu.xcm.domain.CrossChainRouteAvailability
import jp.co.soramitsu.xcm.domain.CrossChainRouteCapability
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossChainRouteInventoryTest {

    @Test
    fun `Cross-chain root renders inventory as read-only UI below the unchanged builder`() {
        val source = File(
            repositoryRoot(),
            "feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/presentation/" +
                "cross_chain/setup/CrossChainSetupContent.kt"
        ).readText()
        val inventoryUi = source
            .substringAfter("internal fun RouteProviderInventory")
            .substringBefore("private fun RouteInventoryCoverage")

        assertTrue(source.contains("RouteProviderInventory(state.routeInventory)"))
        assertTrue(source.indexOf("SelectorWithBorder(") < source.indexOf("RouteProviderInventory("))
        assertTrue(inventoryUi.contains("item.userFacingReason"))
        assertTrue(inventoryUi.contains("routeInventoryMinimum(item)"))
        assertTrue(inventoryUi.contains("item.warnings.forEach"))
        assertFalse(inventoryUi.contains("onClick"))
    }

    @Test
    fun `UI state keeps killed reviewed XCM and unsupported providers visible`() = runBlocking {
        val inventory = buildCrossChainRouteInventory(
            capabilities = listOf(
                capability(
                    providerId = "wallet-reviewed-xcm",
                    protocol = CrossChainProtocol.ReviewedXcm,
                    availability = CrossChainRouteAvailability.Available,
                    actionsEnabled = false,
                    reason = "Reviewed XCM actions are temporarily disabled.",
                    origin = "polkadot",
                    destination = "asset-hub",
                    assetId = "dot-id",
                    symbol = "DOT"
                ),
                capability(
                    providerId = "polkaswap-sora-evm",
                    protocol = CrossChainProtocol.PolkaswapSoraEvm,
                    reason = "SORA/EVM bridge actions need a reviewed Android executor."
                ),
                capability(
                    providerId = "polkaswap-sora-substrate",
                    protocol = CrossChainProtocol.PolkaswapSoraSubstrate,
                    reason = "SORA/Substrate bridge actions need a reviewed Android executor.",
                    origin = "polkadot",
                    destination = "sora",
                    assetId = "canonical-dot-id",
                    symbol = "DOT",
                    minimum = "1.1",
                    warnings = listOf("Cataloged only; no reviewed Android bridge executor exists.")
                ),
                capability(
                    providerId = "liberland",
                    protocol = CrossChainProtocol.Liberland,
                    reason = "Liberland bridge actions need a reviewed Android executor.",
                    origin = "liberland",
                    destination = "sora",
                    assetId = "canonical-lld-id",
                    symbol = "LLD",
                    minimum = "1",
                    warnings = listOf("The route id differs; execution is blocked.")
                )
            ),
            networkName = { id ->
                mapOf(
                    "polkadot" to "Polkadot",
                    "asset-hub" to "Polkadot Asset Hub",
                    "sora" to "SORA",
                    "liberland" to "Liberland"
                ).getValue(id)
            }
        )

        assertEquals(
            listOf(
                "wallet-reviewed-xcm",
                "polkaswap-sora-evm",
                "polkaswap-sora-substrate",
                "liberland"
            ),
            inventory.map(CrossChainRouteInventoryItem::providerId)
        )
        assertEquals(CrossChainRouteInventoryStatus.ActionsPaused, inventory.first().status)
        assertEquals(
            "Reviewed XCM actions are temporarily disabled.",
            inventory.first().userFacingReason
        )
        assertTrue(inventory.drop(1).all {
            it.status == CrossChainRouteInventoryStatus.Unavailable
        })
        assertTrue(inventory.none { it.status == CrossChainRouteInventoryStatus.Available })
        assertFalse(inventory.single { it.providerId == "polkaswap-sora-evm" }.hasCatalogCoverage)

        val dot = inventory.single { it.providerId == "polkaswap-sora-substrate" }
        assertTrue(dot.isExactRoute)
        assertEquals("Polkadot", dot.originNetworks.single().name)
        assertEquals("SORA", dot.destinationNetworks.single().name)
        assertEquals("canonical-dot-id", dot.assets.single().originAssetId)
        assertEquals("1.1", dot.minimumAmount)
        assertEquals("DOT", dot.minimumAssetSymbol)
        assertEquals(
            listOf("Cataloged only; no reviewed Android bridge executor exists."),
            dot.warnings
        )

        val lld = inventory.single { it.providerId == "liberland" }
        assertEquals("1", lld.minimumAmount)
        assertEquals(listOf("The route id differs; execution is blocked."), lld.warnings)
        assertEquals(
            "Liberland bridge actions need a reviewed Android executor.",
            lld.userFacingReason
        )
    }

    private fun capability(
        providerId: String,
        protocol: CrossChainProtocol,
        availability: CrossChainRouteAvailability = CrossChainRouteAvailability.Unavailable,
        actionsEnabled: Boolean = false,
        reason: String,
        origin: String? = null,
        destination: String? = null,
        assetId: String? = null,
        symbol: String? = null,
        minimum: String? = null,
        warnings: List<String> = emptyList()
    ) = CrossChainRouteCapability(
        providerId = providerId,
        protocol = protocol,
        availability = availability,
        actionsEnabled = actionsEnabled,
        requiresAccount = true,
        requiresSigning = true,
        supportedOriginNetworkIds = origin?.let { setOf(it) }.orEmpty(),
        supportedDestinationNetworkIds = destination?.let { setOf(it) }.orEmpty(),
        supportedAssets = if (origin != null && assetId != null && symbol != null) {
            setOf(CrossChainAssetIdentity(origin, assetId, symbol))
        } else {
            emptySet()
        },
        minimumAmount = minimum,
        minimumAssetSymbol = minimum?.let { requireNotNull(symbol) },
        warnings = warnings,
        userFacingReason = reason
    )

    private fun repositoryRoot(): File =
        generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .firstOrNull { root ->
                File(root, "settings.gradle").isFile && File(root, "feature-wallet-impl").isDirectory
            } ?: error("Cannot locate the Fearless Android repository root")
}
