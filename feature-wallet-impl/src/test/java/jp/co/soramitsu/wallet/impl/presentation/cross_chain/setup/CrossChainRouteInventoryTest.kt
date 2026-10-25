package jp.co.soramitsu.wallet.impl.presentation.cross_chain.setup

import jp.co.soramitsu.feature_wallet_impl.R
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
    fun `an inventory row shows its reviewed minimum or why it has none`() = runBlocking {
        val inventory = buildCrossChainRouteInventory(
            capabilities = listOf(
                capability(
                    providerId = "with-minimum",
                    protocol = CrossChainProtocol.Liberland,
                    reason = "paused",
                    origin = "liberland",
                    destination = "sora",
                    assetId = "lld-id",
                    symbol = "LLD",
                    minimum = "1"
                ),
                capability(providerId = "uncovered", protocol = CrossChainProtocol.PolkaswapSoraEvm, reason = "paused"),
                capability(
                    providerId = "without-minimum",
                    protocol = CrossChainProtocol.PolkaswapSoraSubstrate,
                    reason = "paused",
                    origin = "polkadot",
                    destination = "sora",
                    assetId = "dot-id",
                    symbol = "DOT"
                )
            ),
            networkName = { it }
        )
        val severalRoutes = inventory.single { it.providerId == "without-minimum" }.let { row ->
            row.copy(destinationNetworks = row.destinationNetworks + CrossChainRouteInventoryNetwork("kusama", "kusama"))
        }
        val text = inventory.associate { it.providerId to routeInventoryMinimumText(it) }

        assertEquals(R.string.cross_chain_route_minimum_value to listOf("1", "LLD"), text["with-minimum"])
        assertEquals(R.string.cross_chain_route_inventory_minimum_no_reviewed_value to emptyList<String>(), text["uncovered"])
        assertEquals(R.string.cross_chain_route_inventory_minimum_not_supplied to emptyList<String>(), text["without-minimum"])
        assertEquals(
            R.string.cross_chain_route_inventory_minimum_select_route to emptyList<String>(),
            routeInventoryMinimumText(severalRoutes)
        )
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
}
