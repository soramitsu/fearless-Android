package jp.co.soramitsu.xcm.domain

import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class XcmEntitiesFetcherTest {

    @Test
    fun `filters origin chains by destination and normalized asset symbol`() = runBlocking {
        val fetcher = fetcher(
            chain(
                id = "origin-a",
                xcm = xcm(
                    availableAssets = listOf(xcmAsset("DOT")),
                    destinations = listOf(
                        destination("destination-a", xcmAsset("DOT")),
                        destination("destination-b", xcmAsset("KSM"))
                    )
                )
            ),
            chain(
                id = "origin-b",
                xcm = xcm(
                    availableAssets = listOf(xcmAsset("KSM")),
                    destinations = listOf(destination("destination-a", xcmAsset("KSM")))
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
    fun `returns destination filtered assets with parsed min amount`() = runBlocking {
        val fetcher = fetcher(
            chain(
                id = "origin",
                xcm = xcm(
                    destinations = listOf(
                        destination(
                            "destination",
                            xcmAsset(symbol = "xcKSM", id = "ksm-route", minAmount = "12000000000"),
                            xcmAsset(symbol = "DOT", id = "dot-route", minAmount = "0")
                        ),
                        destination("other", xcmAsset("HDX"))
                    )
                )
            )
        )

        val assets = fetcher.getAvailableAssets(originChainId = "origin", destinationChainId = "destination")

        assertEquals(listOf("KSM", "DOT"), assets.map { it.symbol })
        assertEquals("ksm-route", assets[0].id)
        assertEquals(BigInteger("12000000000"), assets[0].minAmount)
        assertEquals(BigInteger.ZERO, assets[1].minAmount)
    }

    @Test
    fun `falls back to origin available assets when no route assets exist`() = runBlocking {
        val fetcher = fetcher(
            chain(
                id = "origin",
                xcm = xcm(
                    availableAssets = listOf(xcmAsset("DOT"), xcmAsset("xcKSM"))
                )
            )
        )

        assertEquals(
            listOf("DOT", "KSM"),
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
                        destination("dot-destination", xcmAsset("DOT")),
                        destination("ksm-destination", xcmAsset("xcKSM")),
                        destination("all-destination", xcmAsset("DOT"), xcmAsset("KSM"))
                    )
                )
            )
        )

        assertEquals(
            listOf("ksm-destination", "all-destination"),
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
            listOf("destination"),
            fetcher.getAvailableDestinationChains(originChainId = "origin", assetSymbol = null)
        )

        val assets = fetcher.getAvailableAssets(originChainId = "origin", destinationChainId = "destination")
        assertEquals(listOf("KSM", "DOT"), assets.map { it.symbol })
        assertEquals(null, assets[0].minAmount)
        assertEquals(null, assets[1].minAmount)
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
            )
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

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                fetcher.getExecutableRoute(
                    originChainId = "origin",
                    destinationChainId = "destination",
                    assetSymbol = "DOT"
                )
            }
        }
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

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                fetcher.getExecutableRoute(
                    originChainId = "origin",
                    destinationChainId = "destination",
                    assetSymbol = "DOT"
                )
            }
        }
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

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                malformedBeneficiary.getExecutableRoute("origin", "destination", "DOT")
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                wrongJunctionCount.getExecutableRoute("origin", "destination", "DOT")
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                unsupportedJunction.getExecutableRoute("origin", "destination", "DOT")
            }
        }

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

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                fetcher.getExecutableRoute(
                    originChainId = "origin",
                    destinationChainId = "destination",
                    assetSymbol = "DOT"
                )
            }
        }
        assertFalse(fetcher.hasExecutableRouteAsset(originChainId = "origin", assetSymbol = "DOT"))
    }

    private fun fetcher(vararg chains: Chain) = XcmEntitiesFetcher { chains.toList() }

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
    ) = Chain.Xcm.Destination(
        chainId = chainId,
        assets = assets.toList(),
        bridgeParachainId = bridgeParachainId,
        execution = execution
    )

    private fun executableRouteSpec(
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
        bridge: Chain.Xcm.Bridge? = null
    ) = Chain.Xcm.Execution(
        palletName = "XTokens",
        callName = "limitedReserveTransferAssets",
        transferType = "limitedReserveTransferAssets",
        destinationLocation = destinationLocation,
        assetLocation = assetLocation,
        beneficiaryLocation = beneficiaryLocation,
        feeAssetLocation = feeAssetLocation,
        feeAssetItem = 0,
        weightLimit = weightLimit,
        destinationFee = Chain.Xcm.DestinationFee(
            mode = "Estimated",
            assetSymbol = "DOT",
            amount = null
        ),
        bridge = bridge
    )

    private fun xcmAsset(
        symbol: String,
        id: String? = null,
        minAmount: String? = null
    ) = Chain.Xcm.Asset(
        id = id,
        symbol = symbol,
        minAmount = minAmount
    )

    private fun chain(id: String, xcm: Chain.Xcm? = null) = Chain(
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
        isEthereumBased = false,
        isTestNet = false,
        hasCrowdloans = false,
        parentId = null,
        supportStakingPool = false,
        isEthereumChain = false,
        chainlinkProvider = false,
        supportNft = false,
        isUsesAppId = false,
        identityChain = null,
        ecosystem = Ecosystem.Substrate,
        androidMinAppVersion = null,
        remoteAssetsSource = null,
        tonBridgeUrl = null,
        xcm = xcm
    )
}
