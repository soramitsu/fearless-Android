package jp.co.soramitsu.xcm

import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.core.models.ChainIdWithMetadata
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.xcm.domain.XcmArgumentShape
import jp.co.soramitsu.xcm.domain.XcmEntitiesFetcher
import jp.co.soramitsu.xcm.domain.XcmJunctionType
import jp.co.soramitsu.xcm.domain.XcmTransferType
import jp.co.soramitsu.xcm.domain.XcmWeightLimitType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset as CoreAsset

class XcmServiceTest {

    @Test
    fun `public service does not advertise transfer support even when route metadata exists`() = runBlocking {
        val service = serviceWithRoute()

        assertFalse(service.isXcmSupportAsset(originChainId = "origin", assetSymbol = "DOT"))
    }

    @Test
    fun `advertises transfer support with available engine`() = runBlocking {
        val service = serviceWithRoute(RecordingXcmTransferEngine())

        assertTrue(service.isXcmSupportAsset(originChainId = "origin", assetSymbol = "xcdot"))
        assertFalse(service.isXcmSupportAsset(originChainId = "origin", assetSymbol = "KSM"))
        assertFalse(service.isXcmSupportAsset(originChainId = "missing", assetSymbol = "DOT"))
    }

    @Test
    fun `public service exposes configured min amount`() = runBlocking {
        val service = serviceWithRoute()

        assertEquals(
            BigInteger.TEN,
            service.getAmountMinLimit(
                originChainId = "origin",
                destinationChainId = "destination",
                asset = coreAsset("xcdot")
            )
        )
    }

    @Test
    fun `public service delegates keypair provider and metadata to transfer engine`() {
        val engine = RecordingXcmTransferEngine()
        val service = serviceWithRoute(engine)
        val keypairProvider = Any()
        val metadata = ChainIdWithMetadata(chainId = "origin", metadata = "0x010203")

        service.updateKeypairProvider(chainId = "origin", keypairProvider = keypairProvider)
        service.addPreloadedMetadata(metadata)

        assertEquals("origin", engine.keypairChainId)
        assertTrue(engine.keypairProvider === keypairProvider)
        assertEquals(listOf(metadata), engine.preloadedMetadata)
    }

    @Test
    fun `public service delegates transfer after validating route and amount`() = runBlocking {
        val engine = RecordingXcmTransferEngine()
        val service = serviceWithRoute(engine)
        val senderAccountId = byteArrayOf(1, 2, 3)

        val extrinsicHash = service.transfer(
            originChain = chain("origin"),
            destinationChain = chain("destination"),
            asset = coreAsset("DOT"),
            senderAccountId = senderAccountId,
            address = "5Destination",
            amount = BigInteger.TEN
        )

        assertEquals("0xhash", extrinsicHash)
        val request = requireNotNull(engine.transferRequest)
        assertEquals("origin", request.originChain.id)
        assertEquals("destination", request.destinationChain.id)
        assertEquals("DOT", request.asset.symbol)
        assertArrayEquals(senderAccountId, request.senderAccountId)
        assertEquals("5Destination", request.recipientAddress)
        assertEquals(BigInteger.TEN, request.amount)
        assertEquals("PolkadotXcm", request.executionSpec.palletName)
        assertEquals("limitedReserveTransferAssets", request.executionSpec.callName)
        assertEquals(XcmTransferType.LIMITED_RESERVE_TRANSFER_ASSETS, request.executionSpec.transferType)
        assertEquals(XcmArgumentShape.POLKADOT_XCM_TRANSFER_ASSETS, request.executionSpec.argumentShape)
        assertEquals("v3", request.executionSpec.xcmVersion)
        assertEquals(XcmJunctionType.PARACHAIN, request.executionSpec.destinationLocation.junctions.single().type)
        assertEquals("X2(Parachain(1000), GeneralKey(dot))", request.executionSpec.assetLocation.interior)
        assertEquals(XcmJunctionType.PARACHAIN, request.executionSpec.assetLocation.junctions[0].type)
        assertEquals(XcmJunctionType.ACCOUNT_ID32, request.executionSpec.beneficiaryLocation.junctions.single().type)
        assertEquals(XcmWeightLimitType.LIMITED, request.executionSpec.weightLimit.type)
    }

    @Test
    fun `public service rejects transfer below route min amount before engine call`() {
        val engine = RecordingXcmTransferEngine()
        val service = serviceWithRoute(engine)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                service.transfer(
                    originChain = chain("origin"),
                    destinationChain = chain("destination"),
                    asset = coreAsset("DOT"),
                    senderAccountId = byteArrayOf(1),
                    address = "5Destination",
                    amount = BigInteger("9")
                )
            }
        }
        assertEquals(null, engine.transferRequest)
    }

    @Test
    fun `public service rejects missing execution spec before engine call`() {
        val engine = RecordingXcmTransferEngine()
        val service = serviceWithRoute(engine = engine, execution = null)

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                service.transfer(
                    originChain = chain("origin"),
                    destinationChain = chain("destination"),
                    asset = coreAsset("DOT"),
                    senderAccountId = byteArrayOf(1),
                    address = "5Destination",
                    amount = BigInteger.TEN
                )
            }
        }
        assertEquals(null, engine.transferRequest)
    }

    @Test
    fun `public service rejects malformed execution spec before engine call`() {
        val engine = RecordingXcmTransferEngine()
        val service = serviceWithRoute(
            engine = engine,
            execution = executableRouteSpec(palletName = " ")
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                service.transfer(
                    originChain = chain("origin"),
                    destinationChain = chain("destination"),
                    asset = coreAsset("DOT"),
                    senderAccountId = byteArrayOf(1),
                    address = "5Destination",
                    amount = BigInteger.TEN
                )
            }
        }
        assertEquals(null, engine.transferRequest)
    }

    @Test
    fun `public service rejects mismatched argument shape before engine call`() {
        val engine = RecordingXcmTransferEngine()
        val service = serviceWithRoute(
            engine = engine,
            execution = executableRouteSpec(
                callName = "transferMultiasset",
                transferType = "xTokensTransferMultiasset",
                argumentShape = "xTokensTransferMultiasset"
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                service.transfer(
                    originChain = chain("origin"),
                    destinationChain = chain("destination"),
                    asset = coreAsset("DOT"),
                    senderAccountId = byteArrayOf(1),
                    address = "5Destination",
                    amount = BigInteger.TEN
                )
            }
        }
        assertEquals(null, engine.transferRequest)
    }

    @Test
    @Suppress("FunctionSignature")
    fun `does not advertise transfer support when execution spec is missing or malformed`() = runBlocking {
        val missingSpecService = serviceWithRoute(engine = RecordingXcmTransferEngine(), execution = null)
        val malformedSpecService = serviceWithRoute(
            engine = RecordingXcmTransferEngine(),
            execution = executableRouteSpec(weightLimit = Chain.Xcm.WeightLimit(type = "Limited", refTime = null, proofSize = "0"))
        )
        val malformedMultilocationService = serviceWithRoute(
            engine = RecordingXcmTransferEngine(),
            execution = executableRouteSpec(
                beneficiaryLocation = Chain.Xcm.MultiLocation(
                    parents = 0,
                    interior = "X1(AccountId32({network: Any}))"
                )
            )
        )
        val unsupportedArgumentShapeService = serviceWithRoute(
            engine = RecordingXcmTransferEngine(),
            execution = executableRouteSpec(argumentShape = "operatorAlias")
        )

        assertFalse(missingSpecService.isXcmSupportAsset(originChainId = "origin", assetSymbol = "DOT"))
        assertFalse(malformedSpecService.isXcmSupportAsset(originChainId = "origin", assetSymbol = "DOT"))
        assertFalse(malformedMultilocationService.isXcmSupportAsset(originChainId = "origin", assetSymbol = "DOT"))
        assertFalse(unsupportedArgumentShapeService.isXcmSupportAsset(originChainId = "origin", assetSymbol = "DOT"))
    }

    @Test
    fun `public service rejects unsupported route asset before engine call`() {
        val engine = RecordingXcmTransferEngine()
        val service = serviceWithRoute(engine)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                service.transfer(
                    originChain = chain("origin"),
                    destinationChain = chain("destination"),
                    asset = coreAsset("KSM"),
                    senderAccountId = byteArrayOf(1),
                    address = "5Destination",
                    amount = BigInteger.TEN
                )
            }
        }
        assertEquals(null, engine.transferRequest)
    }

    @Test
    fun `public service rejects blank recipient before engine call`() {
        val engine = RecordingXcmTransferEngine()
        val service = serviceWithRoute(engine)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                service.transfer(
                    originChain = chain("origin"),
                    destinationChain = chain("destination"),
                    asset = coreAsset("DOT"),
                    senderAccountId = byteArrayOf(1),
                    address = " ",
                    amount = BigInteger.TEN
                )
            }
        }
        assertEquals(null, engine.transferRequest)
    }

    @Test
    fun `public service delegates AccountKey20 transfer after validating EVM recipient`() = runBlocking {
        val engine = RecordingXcmTransferEngine()
        val service = serviceWithRoute(
            engine = engine,
            execution = executableRouteSpec(
                beneficiaryLocation = Chain.Xcm.MultiLocation(
                    parents = 0,
                    interior = "X1(AccountKey20({network: Any, key: <account>}))"
                )
            )
        )

        service.transfer(
            originChain = chain("origin"),
            destinationChain = chain("destination"),
            asset = coreAsset("DOT"),
            senderAccountId = byteArrayOf(1),
            address = VALID_EVM_RECIPIENT,
            amount = BigInteger.TEN
        )

        val request = requireNotNull(engine.transferRequest)
        assertEquals(VALID_EVM_RECIPIENT, request.recipientAddress)
        assertEquals(XcmJunctionType.ACCOUNT_KEY20, request.executionSpec.beneficiaryLocation.junctions.single().type)
    }

    @Test
    fun `public service rejects malformed AccountKey20 recipient before engine call`() {
        val invalidRecipients = listOf(
            "5Destination",
            "1111111111111111111111111111111111111111",
            "0x111111111111111111111111111111111111111",
            "0x11111111111111111111111111111111111111111",
            "0x11111111111111111111111111111111111111zz"
        )

        invalidRecipients.forEach { recipient ->
            val engine = RecordingXcmTransferEngine()
            val service = serviceWithRoute(
                engine = engine,
                execution = executableRouteSpec(
                    beneficiaryLocation = Chain.Xcm.MultiLocation(
                        parents = 0,
                        interior = "X1(AccountKey20({network: Any, key: <account>}))"
                    )
                )
            )

            assertThrows("recipient $recipient should be rejected", IllegalArgumentException::class.java) {
                runBlocking {
                    service.transfer(
                        originChain = chain("origin"),
                        destinationChain = chain("destination"),
                        asset = coreAsset("DOT"),
                        senderAccountId = byteArrayOf(1),
                        address = recipient,
                        amount = BigInteger.TEN
                    )
                }
            }
            assertEquals(null, engine.transferRequest)
        }
    }

    @Test
    fun `public service rejects malformed AccountKey20 origin fee recipient before engine call`() {
        val engine = RecordingXcmTransferEngine()
        val service = serviceWithRoute(
            engine = engine,
            execution = executableRouteSpec(
                beneficiaryLocation = Chain.Xcm.MultiLocation(
                    parents = 0,
                    interior = "X1(AccountKey20({network: Any, key: <account>}))"
                )
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                service.getXcmOriginFee(
                    originChain = chain("origin"),
                    destinationChainId = "destination",
                    asset = coreAsset("DOT"),
                    address = "not-evm",
                    amount = BigInteger.TEN
                )
            }
        }
        assertEquals(null, engine.originFeeRequest)
    }

    @Test
    fun `public service rejects empty sender before engine call`() {
        val engine = RecordingXcmTransferEngine()
        val service = serviceWithRoute(engine)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                service.transfer(
                    originChain = chain("origin"),
                    destinationChain = chain("destination"),
                    asset = coreAsset("DOT"),
                    senderAccountId = byteArrayOf(),
                    address = "5Destination",
                    amount = BigInteger.TEN
                )
            }
        }
        assertEquals(null, engine.transferRequest)
    }

    @Test
    fun `public service rejects same origin and destination before engine call`() {
        val engine = RecordingXcmTransferEngine()
        val service = serviceWithRoute(engine)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                service.transfer(
                    originChain = chain("origin"),
                    destinationChain = chain("origin"),
                    asset = coreAsset("DOT"),
                    senderAccountId = byteArrayOf(1),
                    address = "5Destination",
                    amount = BigInteger.TEN
                )
            }
        }
        assertEquals(null, engine.transferRequest)
    }

    @Test
    fun `public service rejects destination fee estimation`() {
        val service = serviceWithRoute()

        assertThrows(UnsupportedOperationException::class.java) {
            runBlocking {
                service.getXcmDestinationFee(
                    originChainId = "origin",
                    destinationChainId = "destination",
                    asset = coreAsset("DOT")
                )
            }
        }
    }

    @Test
    fun `public service rejects origin fee estimation`() {
        val service = serviceWithRoute()

        assertThrows(UnsupportedOperationException::class.java) {
            runBlocking {
                service.getXcmOriginFee(
                    originChain = chain("origin"),
                    destinationChainId = "destination",
                    asset = coreAsset("DOT"),
                    address = "address",
                    amount = BigInteger.TEN
                )
            }
        }
    }

    @Test
    fun `public service delegates fee estimation when engine is available`() = runBlocking {
        val engine = RecordingXcmTransferEngine()
        val service = serviceWithRoute(engine)

        assertEquals(
            BigDecimal("0.01"),
            service.getXcmDestinationFee(
                originChainId = "origin",
                destinationChainId = "destination",
                asset = coreAsset("DOT")
            )
        )
        assertEquals(
            BigDecimal("0.02"),
            service.getXcmOriginFee(
                originChain = chain("origin"),
                destinationChainId = "destination",
                asset = coreAsset("DOT"),
                originFeeAsset = coreAsset("KSM"),
                address = "5Destination",
                amount = BigInteger.TEN
            )
        )
        assertEquals(
            DestinationFeeRequest("origin", "destination", "DOT"),
            engine.destinationFeeRequest
        )
        assertEquals(
            OriginFeeRequest("origin", "destination", "DOT", "KSM", "5Destination", BigInteger.TEN),
            engine.originFeeRequest
        )
        assertEquals(XcmTransferType.LIMITED_RESERVE_TRANSFER_ASSETS, engine.originFeeExecutionSpec?.transferType)
    }

    @Test
    fun `public service rejects transfer submission`() {
        val service = serviceWithRoute()

        assertThrows(UnsupportedOperationException::class.java) {
            runBlocking {
                service.transfer(
                    originChain = chain("origin"),
                    destinationChain = chain("destination"),
                    asset = coreAsset("DOT"),
                    senderAccountId = byteArrayOf(1),
                    address = "address",
                    amount = BigInteger.TEN
                )
            }
        }
    }

    private fun serviceWithRoute(
        engine: XcmTransferEngine = UnavailableXcmTransferEngine,
        execution: Chain.Xcm.Execution? = executableRouteSpec()
    ): XcmService {
        val origin = chain(
            id = "origin",
            xcm = Chain.Xcm(
                chainId = null,
                xcmVersion = "v3",
                availableAssets = listOf(Chain.Xcm.Asset(id = "dot", symbol = "DOT", minAmount = null)),
                availableDestinations = listOf(
                    Chain.Xcm.Destination(
                        chainId = "destination",
                        assets = listOf(Chain.Xcm.Asset(id = "dot-route", symbol = "DOT", minAmount = "10")),
                        bridgeParachainId = null,
                        execution = execution
                    )
                )
            )
        )

        return XcmService(XcmEntitiesFetcher { listOf(origin) }, engine)
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
        feeAssetItem: Int? = 0,
        weightLimit: Chain.Xcm.WeightLimit? = Chain.Xcm.WeightLimit(
            type = "Limited",
            refTime = "6000000000",
            proofSize = "65536"
        ),
        destinationFee: Chain.Xcm.DestinationFee? = Chain.Xcm.DestinationFee(
            mode = "Estimated",
            assetSymbol = "DOT",
            amount = null
        ),
        bridge: Chain.Xcm.Bridge? = null
    ) = Chain.Xcm.Execution(
        palletName = palletName,
        callName = callName,
        transferType = transferType,
        argumentShape = argumentShape,
        destinationLocation = destinationLocation,
        assetLocation = assetLocation,
        beneficiaryLocation = beneficiaryLocation,
        feeAssetLocation = feeAssetLocation,
        feeAssetItem = feeAssetItem,
        weightLimit = weightLimit,
        destinationFee = destinationFee,
        bridge = bridge
    )

    private fun coreAsset(symbol: String) = CoreAsset(
        id = "asset-$symbol",
        name = symbol,
        symbol = symbol,
        iconUrl = "",
        chainId = "origin",
        chainName = "origin",
        chainIcon = null,
        isTestNet = false,
        priceId = null,
        precision = 12,
        staking = CoreAsset.StakingType.UNSUPPORTED,
        purchaseProviders = null,
        supportStakingPool = false,
        isUtility = false,
        type = null,
        currencyId = null,
        existentialDeposit = null,
        color = null,
        isNative = null,
        priceProvider = null,
        coinbaseUrl = null
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

    private companion object {
        const val VALID_EVM_RECIPIENT = "0x1111111111111111111111111111111111111111"
    }

    private data class OriginFeeRequest(
        val originChainId: ChainId,
        val destinationChainId: ChainId,
        val assetSymbol: String,
        val originFeeAssetSymbol: String,
        val address: String,
        val amount: BigInteger
    )

    private data class DestinationFeeRequest(
        val originChainId: ChainId,
        val destinationChainId: ChainId,
        val assetSymbol: String
    )

    private class RecordingXcmTransferEngine : XcmTransferEngine {
        override val isAvailable: Boolean = true
        var keypairChainId: ChainId? = null
        var keypairProvider: Any? = null
        var preloadedMetadata = emptyList<ChainIdWithMetadata>()
        var transferRequest: XcmTransferRequest? = null
        var destinationFeeRequest: DestinationFeeRequest? = null
        var originFeeRequest: OriginFeeRequest? = null
        var originFeeExecutionSpec: jp.co.soramitsu.xcm.domain.XcmExecutionSpec? = null

        override fun updateKeypairProvider(chainId: ChainId, keypairProvider: Any) {
            keypairChainId = chainId
            this.keypairProvider = keypairProvider
        }

        override fun addPreloadedMetadata(vararg chainMetadatas: ChainIdWithMetadata) {
            preloadedMetadata = chainMetadatas.toList()
        }

        override suspend fun transfer(request: XcmTransferRequest): String {
            transferRequest = request
            return "0xhash"
        }

        override suspend fun getDestinationFee(
            originChainId: ChainId,
            destinationChainId: ChainId,
            asset: CoreAsset,
            executionSpec: jp.co.soramitsu.xcm.domain.XcmExecutionSpec
        ): BigDecimal {
            destinationFeeRequest = DestinationFeeRequest(originChainId, destinationChainId, asset.symbol)
            return BigDecimal("0.01")
        }

        override suspend fun getOriginFee(
            originChain: Chain,
            originChainId: ChainId,
            destinationChainId: ChainId,
            asset: CoreAsset,
            originFeeAsset: CoreAsset,
            address: String,
            amount: BigInteger,
            executionSpec: jp.co.soramitsu.xcm.domain.XcmExecutionSpec
        ): BigDecimal {
            originFeeRequest = OriginFeeRequest(
                originChainId,
                destinationChainId,
                asset.symbol,
                originFeeAsset.symbol,
                address,
                amount
            )
            originFeeExecutionSpec = executionSpec
            return BigDecimal("0.02")
        }
    }
}
