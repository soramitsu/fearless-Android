package jp.co.soramitsu.xcm.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossChainRouteProviderTest {

    @Test
    fun `registry prefers an executable reviewed provider without hiding disabled providers`() = runBlocking {
        val unavailableBridge = UnavailableCrossChainRouteProvider(
            providerId = "polkaswap-sora-evm",
            protocol = CrossChainProtocol.PolkaswapSoraEvm,
            reason = "No reviewed Android executor is available."
        )
        val reviewed = fixedProvider(
            id = ReviewedWalletXcmRouteProvider.PROVIDER_ID,
            protocol = CrossChainProtocol.ReviewedXcm,
            available = true,
            actionsEnabled = true
        )
        val registry = CrossChainRouteProviderRegistry(listOf(unavailableBridge, reviewed))

        val capabilities = registry.capabilities()

        assertEquals(2, capabilities.size)
        assertFalse(capabilities.first().canExecute)
        val selected = registry.bestCapability()
        assertEquals(ReviewedWalletXcmRouteProvider.PROVIDER_ID, selected.providerId)
        assertEquals(2, selected.supportedAssets.size)
        assertEquals(1, selected.supportedAssetSymbols.size)
    }

    @Test
    fun `known bridge provider remains visible and honestly unavailable`() = runBlocking {
        val provider = UnavailableCrossChainRouteProvider(
            providerId = "liberland",
            protocol = CrossChainProtocol.Liberland,
            reason = "Liberland routes remain disabled until a reviewed executor exists.",
            routeDescriptors = listOf(
                CrossChainRouteDescriptor(
                    originNetworkId = "liberland",
                    destinationNetworkId = "sora",
                    asset = CrossChainAssetIdentity("liberland", "lld-id", "LLD"),
                    minimumAmount = "1",
                    warnings = listOf("Catalog only.")
                )
            )
        )

        val capability = provider.capability()

        assertFalse(capability.hasRoute)
        assertFalse(capability.actionsEnabled)
        assertTrue(capability.userFacingReason!!.contains("reviewed executor"))
        assertEquals(setOf("liberland"), capability.supportedOriginNetworkIds)
        assertEquals(setOf("sora"), capability.supportedDestinationNetworkIds)
        assertEquals(setOf("lld-id"), capability.supportedAssets.map { it.originAssetId }.toSet())

        val exact = provider.capability(
            CrossChainRouteQuery(
                originNetworkId = "liberland",
                destinationNetworkId = "sora",
                originAssetId = "lld-id"
            )
        )
        assertEquals("1", exact.minimumAmount)
        assertEquals("LLD", exact.minimumAssetSymbol)
        assertEquals(listOf("Catalog only."), exact.warnings)
        assertFalse(exact.canExecute)
    }

    @Test
    fun `inventory keeps killed XCM and every unsupported bridge destination visible`() = runBlocking {
        val reviewedButKilled = fixedProvider(
            id = ReviewedWalletXcmRouteProvider.PROVIDER_ID,
            protocol = CrossChainProtocol.ReviewedXcm,
            available = true,
            actionsEnabled = false
        )
        val soraEvm = UnavailableCrossChainRouteProvider(
            providerId = "polkaswap-sora-evm",
            protocol = CrossChainProtocol.PolkaswapSoraEvm,
            reason = "SORA/EVM bridge actions need a reviewed Android executor."
        )
        val soraSubstrate = UnavailableCrossChainRouteProvider(
            providerId = "polkaswap-sora-substrate",
            protocol = CrossChainProtocol.PolkaswapSoraSubstrate,
            reason = "SORA/Substrate bridge actions need a reviewed Android executor.",
            routeDescriptors = listOf(
                CrossChainRouteDescriptor(
                    originNetworkId = "polkadot",
                    destinationNetworkId = "sora",
                    asset = CrossChainAssetIdentity("polkadot", "dot-id", "DOT"),
                    minimumAmount = "1.1",
                    warnings = listOf("Catalog only; execution is blocked.")
                ),
                CrossChainRouteDescriptor(
                    originNetworkId = "kusama",
                    destinationNetworkId = "sora",
                    asset = CrossChainAssetIdentity("kusama", "ksm-id", "KSM"),
                    minimumAmount = "0.05",
                    warnings = listOf("Catalog only; execution is blocked.")
                )
            )
        )
        val liberland = UnavailableCrossChainRouteProvider(
            providerId = "liberland",
            protocol = CrossChainProtocol.Liberland,
            reason = "Liberland bridge actions need a reviewed Android executor.",
            routeDescriptors = listOf(
                CrossChainRouteDescriptor(
                    originNetworkId = "liberland",
                    destinationNetworkId = "sora",
                    asset = CrossChainAssetIdentity("liberland", "lld-id", "LLD"),
                    minimumAmount = "1",
                    warnings = listOf("The route id differs; execution is blocked.")
                )
            )
        )
        val inventory = CrossChainRouteProviderRegistry(
            listOf(reviewedButKilled, soraEvm, soraSubstrate, liberland)
        ).inventoryCapabilities()

        assertEquals(
            setOf(
                ReviewedWalletXcmRouteProvider.PROVIDER_ID,
                "polkaswap-sora-evm",
                "polkaswap-sora-substrate",
                "liberland"
            ),
            inventory.mapTo(linkedSetOf(), CrossChainRouteCapability::providerId)
        )
        assertEquals(5, inventory.size)
        assertTrue(inventory.none(CrossChainRouteCapability::canExecute))
        assertEquals(
            listOf("1.1", "0.05"),
            inventory.filter { it.providerId == "polkaswap-sora-substrate" }
                .map(CrossChainRouteCapability::minimumAmount)
        )
        val lld = inventory.single { it.providerId == "liberland" }
        assertEquals("1", lld.minimumAmount)
        assertEquals(listOf("The route id differs; execution is blocked."), lld.warnings)
    }

    @Test
    fun `symbol-only reviewed query cannot claim an asset route`() = runBlocking {
        val provider = ReviewedWalletXcmRouteProvider(
            entitiesFetcher = XcmEntitiesFetcher(
                chainsProvider = { emptyList() },
                approvedRoutes = ApprovedXcmRouteRegistry.unavailable()
            ),
            actionsEnabled = true,
            actionsDisabledReason = "Actions disabled."
        )

        val capability = provider.capability(CrossChainRouteQuery(assetSymbol = "DOT"))

        assertEquals(CrossChainRouteAvailability.SetupRequired, capability.availability)
        assertFalse(capability.hasRoute)
        assertTrue(capability.userFacingReason!!.contains("exact asset id"))
    }

    @Test
    fun `reviewed provider observes kill switch changes without reconstruction`() = runBlocking {
        var actionsEnabled = false
        val provider = ReviewedWalletXcmRouteProvider(
            entitiesFetcher = XcmEntitiesFetcher(
                chainsProvider = { emptyList() },
                approvedRoutes = ApprovedXcmRouteRegistry.unavailable()
            ),
            actionsEnabled = { actionsEnabled },
            actionsDisabledReason = { "Reviewed XCM actions are temporarily disabled." }
        )

        val disabled = provider.capability()
        assertEquals("Reviewed XCM actions are temporarily disabled.", disabled.userFacingReason)

        actionsEnabled = true
        val enabled = provider.capability()
        assertEquals("No reviewed XCM route matches the selected networks and asset.", enabled.userFacingReason)
    }

    @Test
    fun `provider ids must be unique`() {
        val provider = fixedProvider("duplicate", CrossChainProtocol.ReviewedXcm, false, false)
        assertThrows(IllegalArgumentException::class.java) {
            CrossChainRouteProviderRegistry(listOf(provider, provider))
        }
    }

    @Test
    fun `fee and minimum disclosures reject lossy or incomplete values`() {
        assertThrows(IllegalArgumentException::class.java) {
            CrossChainFeeQuote(amount = "not-a-number", assetSymbol = "DOT", live = true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CrossChainRouteCapability(
                providerId = "fixture",
                protocol = CrossChainProtocol.ReviewedXcm,
                availability = CrossChainRouteAvailability.Available,
                actionsEnabled = true,
                requiresAccount = true,
                requiresSigning = true,
                minimumAmount = "1.25"
            )
        }
    }

    private fun fixedProvider(
        id: String,
        protocol: CrossChainProtocol,
        available: Boolean,
        actionsEnabled: Boolean
    ) = object : CrossChainRouteProvider {
        override val providerId: String = id
        override val protocol: CrossChainProtocol = protocol

        override suspend fun capability(query: CrossChainRouteQuery) = CrossChainRouteCapability(
            providerId = id,
            protocol = protocol,
            availability = if (available) {
                CrossChainRouteAvailability.Available
            } else {
                CrossChainRouteAvailability.Unavailable
            },
            actionsEnabled = actionsEnabled,
            requiresAccount = true,
            requiresSigning = true,
            supportedOriginNetworkIds = setOf("origin"),
            supportedDestinationNetworkIds = setOf("destination"),
            supportedAssets = setOf(
                CrossChainAssetIdentity("origin", "dot-contract-a", "DOT"),
                CrossChainAssetIdentity("origin", "dot-contract-b", "DOT")
            ),
            userFacingReason = if (available && actionsEnabled) null else "Unavailable in this fixture."
        )
    }
}
