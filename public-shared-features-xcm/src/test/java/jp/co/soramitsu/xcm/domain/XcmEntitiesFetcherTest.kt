package jp.co.soramitsu.xcm.domain

import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset as CoreAsset

class XcmEntitiesFetcherTest {

    @Test
    fun `filters origin chains by destination and normalized asset symbol`() = runBlocking {
        val fetcher = fetcher(
            chain(
                id = "origin-a",
                xcm = xcm(
                    availableAssets = listOf(xcmAsset("DOT")),
                    destinations = listOf(
                        destination("destination-a", xcmAsset("DOT"), execution = executableRouteSpec()),
                        destination(
                            "destination-b",
                            xcmAsset("KSM"),
                            execution = executableRouteSpec(assetSymbol = "KSM")
                        )
                    )
                )
            ),
            chain(
                id = "origin-b",
                xcm = xcm(
                    availableAssets = listOf(xcmAsset("KSM")),
                    destinations = listOf(
                        destination(
                            "destination-a",
                            xcmAsset("KSM"),
                            execution = executableRouteSpec(assetSymbol = "KSM")
                        )
                    )
                )
            ),
            chain(id = "not-xcm")
        )

        assertEquals(
            listOf("origin-a"),
            fetcher.getAvailableOriginChains(assetSymbol = "xcdot", destinationChainId = "destination-a")
        )
        assertEquals(
            listOf("origin-a", "origin-b"),
            fetcher.getAvailableOriginChains(assetSymbol = null, destinationChainId = "destination-a")
        )
    }

    @Test
    fun `returns approved destination asset with parsed min amount`() = runBlocking {
        val fetcher = fetcher(
            chain(
                id = "origin",
                xcm = xcm(
                    destinations = listOf(
                        destination(
                            "destination",
                            xcmAsset(symbol = "xcKSM", id = "ksm-route", minAmount = "12000000000"),
                            execution = executableRouteSpec(assetSymbol = "KSM")
                        ),
                        destination("other", xcmAsset("HDX"))
                    )
                )
            )
        )

        val assets = fetcher.getAvailableAssets(originChainId = "origin", destinationChainId = "destination")

        assertEquals(listOf("KSM"), assets.map { it.symbol })
        assertEquals("ksm-route", assets[0].id)
        assertEquals(BigInteger("12000000000"), assets[0].minAmount)
    }

    @Test
    fun `does not expose available assets without an approved executable route`() = runBlocking {
        val fetcher = fetcher(
            chain(
                id = "origin",
                xcm = xcm(
                    availableAssets = listOf(xcmAsset("DOT"), xcmAsset("xcKSM"))
                )
            )
        )

        assertEquals(
            emptyList<String>(),
            fetcher.getAvailableAssets(originChainId = "origin", destinationChainId = null).map { it.symbol }
        )
    }

    @Test
    fun `filters destination chains by normalized asset symbol`() = runBlocking {
        val fetcher = fetcher(
            chain(
                id = "origin",
                xcm = xcm(
                    destinations = listOf(
                        destination(
                            "dot-destination",
                            xcmAsset("DOT"),
                            execution = executableRouteSpec()
                        ),
                        destination(
                            "ksm-destination",
                            xcmAsset("xcKSM"),
                            execution = executableRouteSpec(assetSymbol = "KSM")
                        ),
                        destination("all-destination", xcmAsset("DOT"), xcmAsset("KSM"))
                    )
                )
            )
        )

        assertEquals(
            listOf("ksm-destination"),
            fetcher.getAvailableDestinationChains(originChainId = "origin", assetSymbol = "ksm")
        )
    }

    @Test
    fun `ignores malformed destinations assets and min amounts`() = runBlocking {
        val fetcher = fetcher(
            chain(
                id = "origin",
                xcm = xcm(
                    destinations = listOf(
                        Chain.Xcm.Destination(
                            chainId = null,
                            assets = listOf(xcmAsset("DOT")),
                            bridgeParachainId = null
                        ),
                        destination(
                            "destination",
                            xcmAsset(symbol = " ", minAmount = "100"),
                            xcmAsset(symbol = "KSM", minAmount = "-1"),
                            xcmAsset(symbol = "DOT", minAmount = "not-a-number")
                        )
                    )
                )
            )
        )

        assertEquals(
            emptyList<String>(),
            fetcher.getAvailableDestinationChains(originChainId = "origin", assetSymbol = null)
        )

        val assets = fetcher.getAvailableAssets(originChainId = "origin", destinationChainId = "destination")
        assertTrue(assets.isEmpty())
    }

    @Test
    fun `returns validated executable route spec`() = runBlocking {
        val fetcher = fetcher(
            chain(
                id = "origin",
                xcm = xcm(
                    destinations = listOf(
                        destination("destination", xcmAsset("DOT"), execution = executableRouteSpec())
                    )
                )
            )
        )

        val route = requireNotNull(
            fetcher.getExecutableRoute(
                originChainId = "origin",
                destinationChainId = "destination",
                assetSymbol = "xcdot"
            )
        )

        assertEquals("DOT", route.asset.symbol)
        assertEquals(XcmTransferType.LIMITED_RESERVE_TRANSFER_ASSETS, route.executionSpec.transferType)
        assertEquals(XcmArgumentShape.POLKADOT_XCM_TRANSFER_ASSETS, route.executionSpec.argumentShape)
        assertEquals("v3", route.executionSpec.xcmVersion)
        assertEquals(XcmWeightLimitType.LIMITED, route.executionSpec.weightLimit.type)
        assertEquals(XcmJunctionType.PARACHAIN, route.executionSpec.destinationLocation.junctions.single().type)
        assertEquals(XcmJunctionType.PARACHAIN, route.executionSpec.assetLocation.junctions[0].type)
        assertEquals("1000", route.executionSpec.assetLocation.junctions[0].value)
        assertEquals(XcmJunctionType.ACCOUNT_ID32, route.executionSpec.beneficiaryLocation.junctions.single().type)
        assertTrue(route.executionSpec.beneficiaryLocation.junctions.single().value!!.contains("<account>"))
        assertTrue(fetcher.hasExecutableRouteAsset(originChainId = "origin", assetSymbol = "DOT"))
    }

    @Test
    fun `returns validated executable AccountKey20 route spec`() = runBlocking {
        val fetcher = fetcher(
            chain(
                id = "origin",
                xcm = xcm(
                    destinations = listOf(
                        destination(
                            "evm-destination",
                            xcmAsset("DOT"),
                            execution = executableRouteSpec(
                                beneficiaryLocation = Chain.Xcm.MultiLocation(
                                    parents = 0,
                                    interior = "X1(AccountKey20({network: Any, key: <account>}))"
                                )
                            )
                        )
                    )
                )
            ),
            chain(id = "evm-destination", ecosystem = Ecosystem.EthereumBased)
        )

        val route = requireNotNull(
            fetcher.getExecutableRoute(
                originChainId = "origin",
                destinationChainId = "evm-destination",
                assetSymbol = "DOT"
            )
        )

        assertEquals(XcmJunctionType.ACCOUNT_KEY20, route.executionSpec.beneficiaryLocation.junctions.single().type)
        assertTrue(route.executionSpec.beneficiaryLocation.junctions.single().value!!.contains("<account>"))
        assertTrue(fetcher.hasExecutableRouteAsset(originChainId = "origin", assetSymbol = "DOT"))
    }

    @Test
    fun `missing execution spec is not executable`() = runBlocking {
        val fetcher = fetcher(
            chain(
                id = "origin",
                xcm = xcm(destinations = listOf(destination("destination", xcmAsset("DOT"), execution = null)))
            )
        )

        assertEquals(null, fetcher.getExecutableRoute("origin", "destination", "DOT"))
        assertFalse(fetcher.hasExecutableRouteAsset(originChainId = "origin", assetSymbol = "DOT"))
    }

    @Test
    fun `malformed execution spec is not executable`() = runBlocking {
        val fetcher = fetcher(
            chain(
                id = "origin",
                xcm = xcm(
                    destinations = listOf(
                        destination(
                            "destination",
                            xcmAsset("DOT"),
                            execution = executableRouteSpec(
                                weightLimit = Chain.Xcm.WeightLimit(type = "Limited", refTime = "1", proofSize = null)
                            )
                        )
                    )
                )
            )
        )

        assertEquals(null, fetcher.getExecutableRoute("origin", "destination", "DOT"))
        assertFalse(fetcher.hasExecutableRouteAsset(originChainId = "origin", assetSymbol = "DOT"))
    }

    @Test
    fun `malformed multilocation interiors are not executable`() = runBlocking {
        val malformedBeneficiary = fetcher(
            chain(
                id = "origin",
                xcm = xcm(
                    destinations = listOf(
                        destination(
                            "destination",
                            xcmAsset("DOT"),
                            execution = executableRouteSpec(
                                beneficiaryLocation = Chain.Xcm.MultiLocation(
                                    parents = 0,
                                    interior = "X1(AccountId32({network: Any}))"
                                )
                            )
                        )
                    )
                )
            )
        )
        val wrongJunctionCount = fetcher(
            chain(
                id = "origin",
                xcm = xcm(
                    destinations = listOf(
                        destination(
                            "destination",
                            xcmAsset("DOT"),
                            execution = executableRouteSpec(
                                assetLocation = Chain.Xcm.MultiLocation(
                                    parents = 1,
                                    interior = "X2(Parachain(1000))"
                                )
                            )
                        )
                    )
                )
            )
        )
        val unsupportedJunction = fetcher(
            chain(
                id = "origin",
                xcm = xcm(
                    destinations = listOf(
                        destination(
                            "destination",
                            xcmAsset("DOT"),
                            execution = executableRouteSpec(
                                assetLocation = Chain.Xcm.MultiLocation(
                                    parents = 1,
                                    interior = "X1(Unsupported(1))"
                                )
                            )
                        )
                    )
                )
            )
        )

        assertEquals(null, malformedBeneficiary.getExecutableRoute("origin", "destination", "DOT"))
        assertEquals(null, wrongJunctionCount.getExecutableRoute("origin", "destination", "DOT"))
        assertEquals(null, unsupportedJunction.getExecutableRoute("origin", "destination", "DOT"))

        assertFalse(malformedBeneficiary.hasExecutableRouteAsset(originChainId = "origin", assetSymbol = "DOT"))
        assertFalse(wrongJunctionCount.hasExecutableRouteAsset(originChainId = "origin", assetSymbol = "DOT"))
        assertFalse(unsupportedJunction.hasExecutableRouteAsset(originChainId = "origin", assetSymbol = "DOT"))
    }

    @Test
    fun `bridge routes require matching bridge execution spec`() = runBlocking {
        val fetcher = fetcher(
            chain(
                id = "origin",
                xcm = xcm(
                    destinations = listOf(
                        destination(
                            "destination",
                            xcmAsset("DOT"),
                            bridgeParachainId = "2000",
                            execution = executableRouteSpec(
                                bridge = Chain.Xcm.Bridge(
                                    parachainId = "3000",
                                    feeAssetLocation = Chain.Xcm.MultiLocation(parents = 1, interior = "Here"),
                                    feeAssetItem = 0
                                )
                            )
                        )
                    )
                )
            )
        )

        assertEquals(null, fetcher.getExecutableRoute("origin", "destination", "DOT"))
        assertFalse(fetcher.hasExecutableRouteAsset(originChainId = "origin", assetSymbol = "DOT"))
    }

    @Test
    fun `execution argument shapes reject unsupported and mismatched call contracts`() = runBlocking {
        val unsupportedShape = fetcher(
            chain(
                id = "origin",
                xcm = xcm(
                    destinations = listOf(
                        destination(
                            "destination",
                            xcmAsset("DOT"),
                            execution = executableRouteSpec(argumentShape = "operatorAlias")
                        )
                    )
                )
            )
        )
        val mismatchedXTokensShape = fetcher(
            chain(
                id = "origin",
                xcm = xcm(
                    destinations = listOf(
                        destination(
                            "destination",
                            xcmAsset("DOT"),
                            execution = executableRouteSpec(
                                palletName = "PolkadotXcm",
                                callName = "transferMultiasset",
                                transferType = "xTokensTransferMultiasset",
                                argumentShape = "xTokensTransferMultiasset"
                            )
                        )
                    )
                )
            )
        )

        assertEquals(null, unsupportedShape.getExecutableRoute("origin", "destination", "DOT"))
        assertEquals(null, mismatchedXTokensShape.getExecutableRoute("origin", "destination", "DOT"))
        assertFalse(unsupportedShape.hasExecutableRouteAsset(originChainId = "origin", assetSymbol = "DOT"))
        assertFalse(mismatchedXTokensShape.hasExecutableRouteAsset(originChainId = "origin", assetSymbol = "DOT"))
    }

    private fun fetcher(vararg chains: Chain): XcmEntitiesFetcher {
        val discoveredChains = chains.toList().map { origin ->
            val approvedCoreAssets = origin.xcm?.availableDestinations.orEmpty()
                .flatMap { it.assets.orEmpty() }
                .filter { it.execution != null }
                .mapNotNull { it.symbol?.normalizedTestSymbol() }
                .distinct()
                .map { symbol -> coreAsset(origin.id, symbol) }
            origin.copy(assets = approvedCoreAssets)
        }.withDestinationStubs()
        val routeKeys = discoveredChains.flatMap { origin ->
            origin.xcm?.availableDestinations.orEmpty().flatMap { destination ->
                destination.assets.orEmpty().mapNotNull { asset ->
                    if (asset.execution == null) return@mapNotNull null
                    ApprovedXcmRouteKey(
                        originChainId = origin.id,
                        destinationChainId = destination.chainId.orEmpty(),
                        assetSymbol = asset.symbol.orEmpty()
                    )
                }
            }
        }
        val approvedRoutes = runCatching {
            ApprovedXcmRouteRegistry.fromReviewedChains(discoveredChains, routeKeys)
        }.getOrElse {
            ApprovedXcmRouteRegistry.unavailable()
        }
        return XcmEntitiesFetcher({ discoveredChains }, approvedRoutes)
    }

    private fun String.normalizedTestSymbol(): String = trim()
        .replace(Regex("^xc", RegexOption.IGNORE_CASE), "")
        .uppercase()

    private fun List<Chain>.withDestinationStubs(): List<Chain> {
        val knownIds = mapTo(mutableSetOf(), Chain::id)
        val destinationIds = flatMap { chain ->
            chain.xcm?.availableDestinations.orEmpty().mapNotNull { it.chainId }
        }
        return this + destinationIds.filter { knownIds.add(it) }.map { chain(it) }
    }

    private fun xcm(
        availableAssets: List<Chain.Xcm.Asset> = emptyList(),
        destinations: List<Chain.Xcm.Destination> = emptyList()
    ) = Chain.Xcm(
        chainId = null,
        xcmVersion = "v3",
        availableAssets = availableAssets,
        availableDestinations = destinations
    )

    private fun destination(
        chainId: String,
        vararg assets: Chain.Xcm.Asset,
        bridgeParachainId: String? = null,
        execution: Chain.Xcm.Execution? = null
    ): Chain.Xcm.Destination {
        require(execution == null || assets.size == 1) {
            "Tests must bind execution to an exact route asset"
        }
        return Chain.Xcm.Destination(
            chainId = chainId,
            assets = assets.map { asset ->
                if (execution == null) asset else asset.copy(execution = execution)
            },
            bridgeParachainId = bridgeParachainId,
            execution = null
        )
    }

    private fun executableRouteSpec(
        palletName: String? = "PolkadotXcm",
        callName: String? = "limitedReserveTransferAssets",
        transferType: String? = "limitedReserveTransferAssets",
        argumentShape: String? = null,
        destinationLocation: Chain.Xcm.MultiLocation? = Chain.Xcm.MultiLocation(
            parents = 1,
            interior = "X1(Parachain(2000))"
        ),
        assetLocation: Chain.Xcm.MultiLocation? = Chain.Xcm.MultiLocation(
            parents = 1,
            interior = "X2(Parachain(1000), GeneralKey(dot))"
        ),
        beneficiaryLocation: Chain.Xcm.MultiLocation? = Chain.Xcm.MultiLocation(
            parents = 0,
            interior = "X1(AccountId32({network: Any, id: <account>}))"
        ),
        feeAssetLocation: Chain.Xcm.MultiLocation? = Chain.Xcm.MultiLocation(
            parents = 1,
            interior = "X2(Parachain(1000), GeneralKey(dot))"
        ),
        weightLimit: Chain.Xcm.WeightLimit? = Chain.Xcm.WeightLimit(
            type = "Limited",
            refTime = "6000000000",
            proofSize = "65536"
        ),
        bridge: Chain.Xcm.Bridge? = null,
        assetSymbol: String = "DOT"
    ) = Chain.Xcm.Execution(
        palletName = palletName,
        callName = callName,
        transferType = transferType,
        argumentShape = argumentShape,
        destinationLocation = destinationLocation,
        assetLocation = assetLocation,
        beneficiaryLocation = beneficiaryLocation,
        feeAssetLocation = feeAssetLocation,
        feeAssetItem = 0,
        weightLimit = weightLimit,
        destinationFee = Chain.Xcm.DestinationFee(
            mode = "Included",
            assetSymbol = assetSymbol,
            amount = null
        ),
        bridge = bridge
    )

    private fun xcmAsset(
        symbol: String,
        id: String? = "route-asset",
        minAmount: String? = null,
        execution: Chain.Xcm.Execution? = null
    ) = Chain.Xcm.Asset(
        id = id,
        symbol = symbol,
        minAmount = minAmount,
        execution = execution
    )

    private fun chain(
        id: String,
        xcm: Chain.Xcm? = null,
        ecosystem: Ecosystem = Ecosystem.Substrate
    ) = Chain(
        id = id,
        paraId = null,
        rank = null,
        name = id,
        minSupportedVersion = null,
        assets = emptyList(),
        nodes = emptyList(),
        explorers = emptyList(),
        externalApi = null,
        icon = "",
        addressPrefix = 0,
        isEthereumBased = ecosystem == Ecosystem.EthereumBased,
        isTestNet = false,
        hasCrowdloans = false,
        parentId = null,
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

    private fun coreAsset(chainId: String, symbol: String) = CoreAsset(
        id = "core-$chainId-${symbol.lowercase()}",
        name = symbol,
        symbol = symbol,
        iconUrl = "",
        chainId = chainId,
        chainName = chainId,
        chainIcon = null,
        isTestNet = false,
        priceId = null,
        precision = 12,
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
}
