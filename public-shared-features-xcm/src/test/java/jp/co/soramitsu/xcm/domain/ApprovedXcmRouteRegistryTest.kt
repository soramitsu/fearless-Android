package jp.co.soramitsu.xcm.domain

import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import jp.co.soramitsu.core.models.Asset as CoreAsset

class ApprovedXcmRouteRegistryTest {

    @Test
    fun `bundled reviewed registry and allowlist load all approved routes`() {
        val registry = ApprovedXcmRouteRegistryLoader.load(
            bundledChainsJson = bundledFile("local_chains.json").readText(),
            approvedRoutesTsv = bundledFile("approved_xcm_routes.tsv").readText()
        )

        assertEquals(15, registry.approvedRouteCount())
    }

    @Test
    fun `exact discovery match resolves reviewed execution authority`() {
        val reviewed = reviewedChains()

        val route = registry(reviewed).effectiveRoutes(reviewed).single()

        assertEquals("origin", route.originChainId)
        assertEquals("destination", route.destinationChainId)
        assertEquals("DOT", route.asset.symbol)
        assertEquals("asset-dot", route.asset.id)
        assertEquals("core-dot", route.asset.originAssetId)
        assertEquals(10, route.asset.originAssetPrecision)
        assertEquals("limitedReserveTransferAssets", route.executionSpec.callName)
    }

    @Test
    fun `remote per-asset execution removal injection and mutation cannot change reviewed authority`() {
        val reviewed = reviewedChains()
        val registry = registry(reviewed)
        val injected = execution(
            palletName = "AttackerPallet",
            callName = "drainEverything",
            transferType = "teleportAssets"
        )
        val remoteVariants = listOf(
            reviewed.withRouteAsset { it.copy(execution = null) },
            reviewed.withRouteAsset { it.copy(execution = injected) },
            reviewed.withDestination { it.copy(execution = injected) }
        )

        remoteVariants.forEach { remote ->
            val route = registry.effectiveRoutes(remote).single()
            assertEquals("PolkadotXcm", route.executionSpec.palletName)
            assertEquals("limitedReserveTransferAssets", route.executionSpec.callName)
            assertEquals(XcmTransferType.LIMITED_RESERVE_TRANSFER_ASSETS, route.executionSpec.transferType)
        }
    }

    @Test
    fun `remote removal disables route while unapproved destination injection grants no authority`() {
        val reviewed = reviewedChains()
        val registry = registry(reviewed)
        val removed = reviewed.withOriginXcm { it.copy(availableDestinations = emptyList()) }
        val injectedDestination = destination(
            chainId = "attacker",
            asset = asset(id = "asset-dot", symbol = "DOT", minAmount = "10"),
            execution = execution(palletName = "AttackerPallet", callName = "drainEverything")
        )
        val injected = reviewed.withOriginXcm {
            it.copy(availableDestinations = it.availableDestinations.orEmpty() + injectedDestination)
        } + chain("attacker")

        assertTrue(registry.effectiveRoutes(removed).isEmpty())
        assertEquals(listOf("destination"), registry.effectiveRoutes(injected).map { it.destinationChainId })
    }

    @Test
    fun `remote sibling asset cannot change exact matching route`() {
        val reviewed = reviewedChains()
        val remote = reviewed.withDestination {
            it.copy(
                assets = it.assets.orEmpty() + asset(
                    id = "asset-ksm",
                    symbol = "KSM",
                    minAmount = "10"
                )
            )
        }

        val route = registry(reviewed).effectiveRoutes(remote).single()
        assertEquals("DOT", route.asset.symbol)
        assertEquals("limitedReserveTransferAssets", route.executionSpec.callName)
    }

    @Test
    fun `asset id symbol and min amount drift disable route`() {
        val reviewed = reviewedChains()
        val registry = registry(reviewed)
        val driftingAssets = listOf(
            asset(id = "attacker-id", symbol = "DOT", minAmount = "10"),
            asset(id = "asset-dot", symbol = "KSM", minAmount = "10"),
            asset(id = "asset-dot", symbol = "DOT", minAmount = "11"),
            asset(id = "asset-dot", symbol = "DOT", minAmount = "-1"),
            asset(id = null, symbol = "DOT", minAmount = "10")
        )

        driftingAssets.forEach { remoteAsset ->
            val remote = reviewed.withDestination { it.copy(assets = listOf(remoteAsset)) }
            assertTrue("drift should disable $remoteAsset", registry.effectiveRoutes(remote).isEmpty())
        }
    }

    @Test
    fun `origin core asset id symbol precision absence and duplication disable route`() {
        val reviewed = reviewedChains()
        val registry = registry(reviewed)
        val origin = reviewed.first { it.id == "origin" }
        val destination = reviewed.first { it.id == "destination" }
        val driftedOrigins = listOf(
            origin.copy(assets = listOf(coreAsset(id = "attacker", symbol = "DOT", precision = 10))),
            origin.copy(assets = listOf(coreAsset(id = "core-dot", symbol = "KSM", precision = 10))),
            origin.copy(assets = listOf(coreAsset(id = "core-dot", symbol = "DOT", precision = 11))),
            origin.copy(assets = emptyList()),
            origin.copy(assets = origin.assets + origin.assets.single())
        )

        driftedOrigins.forEach { driftedOrigin ->
            assertTrue(registry.effectiveRoutes(listOf(driftedOrigin, destination)).isEmpty())
        }
    }

    @Test
    fun `xcm version chain location and chain identity drift disable route`() {
        val reviewed = reviewedChains()
        val registry = registry(reviewed)
        val origin = reviewed.first { it.id == "origin" }
        val destination = reviewed.first { it.id == "destination" }
        val driftedRegistries = listOf(
            listOf(origin.copy(paraId = "1001"), destination),
            listOf(origin.copy(parentId = "other-relay"), destination),
            listOf(origin.copy(addressPrefix = 42), destination),
            listOf(origin.copy(ecosystem = Ecosystem.Ethereum), destination),
            listOf(origin.copy(isTestNet = true), destination),
            listOf(origin, destination.copy(paraId = "2001")),
            listOf(origin, destination.copy(parentId = "other-relay")),
            listOf(origin, destination.copy(addressPrefix = 42)),
            listOf(origin, destination.copy(ecosystem = Ecosystem.Ethereum)),
            listOf(origin, destination.copy(isTestNet = true)),
            reviewed.withOriginXcm { it.copy(chainId = "attacker-location") },
            reviewed.withOriginXcm { it.copy(xcmVersion = "v4") },
            reviewed.withOriginXcm { it.copy(xcmVersion = null) }
        )

        driftedRegistries.forEach { remote ->
            assertTrue(registry.effectiveRoutes(remote).isEmpty())
        }
    }

    @Test
    fun `bridge and duplicate discovery records disable route`() {
        val reviewed = reviewedChains()
        val registry = registry(reviewed)
        val bridgeDrift = reviewed.withDestination { it.copy(bridgeParachainId = "3000") }
        val duplicateDestination = reviewed.withOriginXcm {
            it.copy(availableDestinations = it.availableDestinations.orEmpty() + it.availableDestinations.orEmpty().single())
        }
        val duplicateChain = reviewed + reviewed.last()

        assertTrue(registry.effectiveRoutes(bridgeDrift).isEmpty())
        assertTrue(registry.effectiveRoutes(duplicateDestination).isEmpty())
        assertTrue(registry.effectiveRoutes(duplicateChain).isEmpty())
    }

    @Test
    fun `loader rejects malformed duplicate and missing allowlist routes`() {
        val bundledChains = bundledFile("local_chains.json").readText()

        assertThrows(IllegalArgumentException::class.java) {
            ApprovedXcmRouteRegistryLoader.load(bundledChains, "origin destination")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ApprovedXcmRouteRegistryLoader.load(
                bundledChains,
                "origin destination DOT\norigin destination xcDOT"
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ApprovedXcmRouteRegistryLoader.load(bundledChains, "missing destination DOT")
        }
    }

    @Test
    fun `loader rejects malformed empty and duplicate chain registries`() {
        val malformedRegistries = listOf(
            "not-json",
            "null",
            "{}",
            "[]",
            "[null]",
            "[{}, {}]",
            "[{\"chainId\":\"origin\"}]"
        )

        malformedRegistries.forEach { malformed ->
            assertThrows("should reject $malformed", IllegalArgumentException::class.java) {
                ApprovedXcmRouteRegistryLoader.load(malformed, "origin destination DOT")
            }
        }
    }

    @Test
    fun `loader rejects blank allowlist`() {
        val bundledChains = bundledFile("local_chains.json").readText()

        assertThrows(IllegalArgumentException::class.java) {
            ApprovedXcmRouteRegistryLoader.load(bundledChains, "   \n# comment only")
        }
    }

    @Test
    fun `multi-asset destination selects only the exact asset execution spec`() {
        val reviewed = reviewedChains().withDestination { destination ->
            destination.copy(
                assets = destination.assets.orEmpty() + asset(
                    id = "asset-ksm",
                    symbol = "KSM",
                    minAmount = "20",
                    execution = execution(
                        callName = "limitedTeleportAssets",
                        transferType = "limitedTeleportAssets",
                        destinationFeeAsset = "KSM"
                    )
                )
            )
        }

        val route = registry(reviewed).effectiveRoutes(reviewed).single()
        assertEquals("DOT", route.asset.symbol)
        assertEquals("limitedReserveTransferAssets", route.executionSpec.callName)
        assertEquals(XcmTransferType.LIMITED_RESERVE_TRANSFER_ASSETS, route.executionSpec.transferType)
    }

    @Test
    fun `missing exact asset execution does not borrow a sibling asset spec`() {
        val reviewed = reviewedChains().withDestination { destination ->
            destination.copy(
                assets = listOf(
                    asset("asset-dot", "DOT", "10", execution = null),
                    asset(
                        "asset-ksm",
                        "KSM",
                        "20",
                        execution = execution(destinationFeeAsset = "KSM")
                    )
                )
            )
        }

        val error = assertThrows(IllegalArgumentException::class.java) { registry(reviewed) }
        assertTrue(error.message.orEmpty().contains("execution spec missing for DOT"))
    }

    @Test
    fun `legacy destination execution and mixed legacy per-asset authority are rejected`() {
        val reviewed = reviewedChains()
        val destinationExecution = execution()
        val legacyOnly = reviewed.withDestination { destination ->
            destination.copy(
                assets = destination.assets.orEmpty().map { it.copy(execution = null) },
                execution = destinationExecution
            )
        }
        val mixed = reviewed.withDestination { it.copy(execution = destinationExecution) }

        listOf(legacyOnly, mixed).forEach { ambiguous ->
            val error = assertThrows(IllegalArgumentException::class.java) { registry(ambiguous) }
            assertTrue(error.message.orEmpty().contains("destination-scoped XCM execution"))
        }
    }

    @Test
    fun `duplicate normalized asset symbols are rejected even when only one has execution`() {
        val reviewed = reviewedChains().withDestination { destination ->
            destination.copy(
                assets = destination.assets.orEmpty() + asset(
                    id = "asset-xcdot",
                    symbol = "xcDOT",
                    minAmount = "10",
                    execution = null
                )
            )
        }

        val error = assertThrows(IllegalArgumentException::class.java) { registry(reviewed) }
        assertTrue(error.message.orEmpty().contains("duplicate or ambiguous route assets"))
    }

    @Test
    fun `reviewed execution rejects estimated fee mode`() {
        val reviewed = reviewedChains(
            execution = execution(destinationFeeMode = "Estimated")
        )

        val error = assertThrows(IllegalArgumentException::class.java) { registry(reviewed) }
        assertTrue(error.message.orEmpty().contains("estimated destination fee is unsupported"))
    }

    @Test
    fun `reviewed execution rejects bridge even when route and execution agree`() {
        val bridge = Chain.Xcm.Bridge(
            parachainId = "3000",
            feeAssetLocation = Chain.Xcm.MultiLocation(parents = 1, interior = "Here"),
            feeAssetItem = 0
        )
        val reviewed = reviewedChains(
            bridgeParachainId = "3000",
            execution = execution(bridge = bridge)
        )

        val error = assertThrows(IllegalArgumentException::class.java) { registry(reviewed) }
        assertTrue(
            error.message.orEmpty().contains(
                "XCM bridge execution is unsupported until the production transfer engine consumes bridge fee semantics"
            )
        )
    }

    @Test
    fun `reviewed execution rejects noncanonical version fee index and fee location`() {
        val invalidReviewedRegistries = listOf(
            reviewedChains(xcmVersion = "three"),
            reviewedChains(xcmVersion = " v3 "),
            reviewedChains(execution = execution(feeAssetItem = 1)),
            reviewedChains(
                execution = execution(
                    feeAssetLocation = Chain.Xcm.MultiLocation(parents = 0, interior = "Here")
                )
            )
        )

        invalidReviewedRegistries.forEach { reviewed ->
            assertThrows(IllegalArgumentException::class.java) { registry(reviewed) }
        }
    }

    @Test
    fun `reviewed fee asset mismatch and malformed min amount fail closed`() {
        val wrongFeeAsset = reviewedChains(execution = execution(destinationFeeAsset = "KSM"))
        val malformedMin = reviewedChains().withDestination {
            it.copy(assets = listOf(asset("asset-dot", "DOT", "01")))
        }

        assertThrows(IllegalArgumentException::class.java) { registry(wrongFeeAsset) }
        assertThrows(IllegalArgumentException::class.java) { registry(malformedMin) }
    }

    @Test
    fun `reviewed execution requires exact beneficiary recipient authority and forbids recipient route authority`() {
        val noRecipient = reviewedChains(
            execution = execution(
                beneficiaryLocation = Chain.Xcm.MultiLocation(parents = 0, interior = "Here")
            )
        )
        val duplicateRecipient = reviewedChains(
            execution = execution(
                beneficiaryLocation = Chain.Xcm.MultiLocation(
                    parents = 0,
                    interior = "X2(AccountId32({network: Any, id: <account>}), AccountId32({network: Any, id: <account>}))"
                )
            )
        )
        val prefixedRecipient = reviewedChains(
            execution = execution(
                beneficiaryLocation = Chain.Xcm.MultiLocation(
                    parents = 0,
                    interior = "X1(AccountId32({network: Any, id: prefix<account>}))"
                )
            )
        )
        val suffixedRecipient = reviewedChains(
            execution = execution(
                beneficiaryLocation = Chain.Xcm.MultiLocation(
                    parents = 0,
                    interior = "X1(AccountId32({network: Any, id: <account>suffix}))"
                )
            )
        )
        val recipientControlledDestination = reviewedChains(
            execution = execution(
                destinationLocation = Chain.Xcm.MultiLocation(
                    parents = 0,
                    interior = "X1(AccountId32({network: Any, id: <account>}))"
                )
            )
        )
        val recipientControlledAsset = reviewedChains(
            execution = execution(
                assetLocation = Chain.Xcm.MultiLocation(
                    parents = 0,
                    interior = "X1(AccountId32({network: Any, id: <account>}))"
                ),
                feeAssetLocation = Chain.Xcm.MultiLocation(
                    parents = 0,
                    interior = "X1(AccountId32({network: Any, id: <account>}))"
                )
            )
        )

        listOf(
            noRecipient,
            duplicateRecipient,
            prefixedRecipient,
            suffixedRecipient,
            recipientControlledDestination,
            recipientControlledAsset
        ).forEach {
            assertThrows(IllegalArgumentException::class.java) { registry(it) }
        }
    }

    @Test
    fun `reviewed production route rejects beneficiary ecosystem mismatch and testnets`() {
        val accountKey20OnSubstrate = reviewedChains(
            execution = execution(
                beneficiaryLocation = Chain.Xcm.MultiLocation(
                    parents = 0,
                    interior = "X1(AccountKey20({network: Any, key: <account>}))"
                )
            )
        )
        val accountId32OnEthereum = reviewedChains().map { chain ->
            if (chain.id == "destination") {
                chain.copy(isEthereumBased = true, ecosystem = Ecosystem.EthereumBased)
            } else {
                chain
            }
        }
        val testnetOrigin = reviewedChains().map { chain ->
            if (chain.id == "origin") chain.copy(isTestNet = true) else chain
        }
        val testnetDestination = reviewedChains().map { chain ->
            if (chain.id == "destination") chain.copy(isTestNet = true) else chain
        }
        val inconsistentOriginAccountWidth = reviewedChains().map { chain ->
            if (chain.id == "origin") chain.copy(isEthereumBased = true) else chain
        }
        val inconsistentDestinationAccountWidth = reviewedChains().map { chain ->
            if (chain.id == "destination") chain.copy(isEthereumBased = true) else chain
        }

        listOf(
            accountKey20OnSubstrate,
            accountId32OnEthereum,
            testnetOrigin,
            testnetDestination,
            inconsistentOriginAccountWidth,
            inconsistentDestinationAccountWidth
        ).forEach {
            assertThrows(IllegalArgumentException::class.java) { registry(it) }
        }
    }

    private fun registry(reviewedChains: List<Chain>): ApprovedXcmRouteRegistry =
        ApprovedXcmRouteRegistry.fromReviewedChains(
            reviewedChains = reviewedChains,
            routeKeys = listOf(ApprovedXcmRouteKey("origin", "destination", "DOT"))
        )

    private fun reviewedChains(
        bridgeParachainId: String? = null,
        xcmVersion: String = "v3",
        execution: Chain.Xcm.Execution = execution()
    ): List<Chain> = listOf(
        chain(
            id = "origin",
            paraId = "1000",
            parentId = "relay",
            assets = listOf(coreAsset(id = "core-dot", symbol = "DOT", precision = 10)),
            xcm = Chain.Xcm(
                chainId = "origin-location",
                xcmVersion = xcmVersion,
                availableAssets = emptyList(),
                availableDestinations = listOf(
                    destination(
                        chainId = "destination",
                        asset = asset(id = "asset-dot", symbol = "DOT", minAmount = "10"),
                        bridgeParachainId = bridgeParachainId,
                        execution = execution
                    )
                )
            )
        ),
        chain(id = "destination", paraId = "2000", parentId = "relay")
    )

    private fun List<Chain>.withOriginXcm(transform: (Chain.Xcm) -> Chain.Xcm): List<Chain> = map { chain ->
        if (chain.id == "origin") chain.copy(xcm = transform(requireNotNull(chain.xcm))) else chain
    }

    private fun List<Chain>.withDestination(transform: (Chain.Xcm.Destination) -> Chain.Xcm.Destination): List<Chain> =
        withOriginXcm { xcm ->
            xcm.copy(availableDestinations = xcm.availableDestinations.orEmpty().map(transform))
        }

    private fun List<Chain>.withRouteAsset(transform: (Chain.Xcm.Asset) -> Chain.Xcm.Asset): List<Chain> =
        withDestination { destination ->
            destination.copy(
                assets = destination.assets.orEmpty().map { asset ->
                    if (asset.symbol == "DOT") transform(asset) else asset
                }
            )
        }

    private fun destination(
        chainId: String,
        asset: Chain.Xcm.Asset,
        bridgeParachainId: String? = null,
        execution: Chain.Xcm.Execution? = execution()
    ) = Chain.Xcm.Destination(
        chainId = chainId,
        assets = listOf(asset.copy(execution = execution)),
        bridgeParachainId = bridgeParachainId,
        execution = null
    )

    private fun asset(
        id: String?,
        symbol: String?,
        minAmount: String?,
        execution: Chain.Xcm.Execution? = null
    ) = Chain.Xcm.Asset(
        id = id,
        symbol = symbol,
        minAmount = minAmount,
        execution = execution
    )

    private fun execution(
        palletName: String? = "PolkadotXcm",
        callName: String? = "limitedReserveTransferAssets",
        transferType: String? = "limitedReserveTransferAssets",
        destinationFeeMode: String? = "Included",
        destinationFeeAsset: String? = "DOT",
        destinationLocation: Chain.Xcm.MultiLocation = Chain.Xcm.MultiLocation(
            parents = 1,
            interior = "X1(Parachain(2000))"
        ),
        assetLocation: Chain.Xcm.MultiLocation = Chain.Xcm.MultiLocation(parents = 1, interior = "Here"),
        beneficiaryLocation: Chain.Xcm.MultiLocation = Chain.Xcm.MultiLocation(
            parents = 0,
            interior = "X1(AccountId32({network: Any, id: <account>}))"
        ),
        feeAssetLocation: Chain.Xcm.MultiLocation = assetLocation,
        feeAssetItem: Int = 0,
        bridge: Chain.Xcm.Bridge? = null
    ) = Chain.Xcm.Execution(
        palletName = palletName,
        callName = callName,
        transferType = transferType,
        argumentShape = null,
        destinationLocation = destinationLocation,
        assetLocation = assetLocation,
        beneficiaryLocation = beneficiaryLocation,
        feeAssetLocation = feeAssetLocation,
        feeAssetItem = feeAssetItem,
        weightLimit = Chain.Xcm.WeightLimit(type = "Unlimited", refTime = null, proofSize = null),
        destinationFee = Chain.Xcm.DestinationFee(
            mode = destinationFeeMode,
            assetSymbol = destinationFeeAsset,
            amount = null
        ),
        bridge = bridge
    )

    private fun chain(
        id: String,
        paraId: String? = null,
        parentId: String? = null,
        assets: List<CoreAsset> = emptyList(),
        xcm: Chain.Xcm? = null,
        isEthereumBased: Boolean = false,
        isTestNet: Boolean = false,
        ecosystem: Ecosystem = Ecosystem.Substrate
    ) = Chain(
        id = id,
        paraId = paraId,
        rank = null,
        name = id,
        minSupportedVersion = null,
        assets = assets,
        nodes = emptyList(),
        explorers = emptyList(),
        externalApi = null,
        icon = "",
        addressPrefix = 0,
        isEthereumBased = isEthereumBased,
        isTestNet = isTestNet,
        hasCrowdloans = false,
        parentId = parentId,
        supportStakingPool = false,
        isEthereumChain = false,
        chainlinkProvider = false,
        supportNft = false,
        isUsesAppId = false,
        identityChain = null,
        ecosystem = ecosystem,
        androidMinAppVersion = null,
        remoteAssetsSource = null,
        tonBridgeUrl = null,
        xcm = xcm
    )

    private fun coreAsset(
        id: String,
        symbol: String,
        precision: Int
    ) = CoreAsset(
        id = id,
        name = symbol,
        symbol = symbol,
        iconUrl = "",
        chainId = "origin",
        chainName = "origin",
        chainIcon = null,
        isTestNet = false,
        priceId = null,
        precision = precision,
        staking = CoreAsset.StakingType.UNSUPPORTED,
        purchaseProviders = null,
        supportStakingPool = false,
        isUtility = true,
        type = null,
        currencyId = null,
        existentialDeposit = null,
        color = null,
        isNative = true,
        priceProvider = null,
        coinbaseUrl = null
    )

    private fun bundledFile(name: String): File = listOf(
        File("runtime/src/main/assets/$name"),
        File("../runtime/src/main/assets/$name"),
        File("src/main/assets/$name")
    ).first { it.isFile }
}
