package jp.co.soramitsu.xcm

import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.xcm.domain.XcmArgumentShape
import jp.co.soramitsu.xcm.domain.XcmBridgeExecutionSpec
import jp.co.soramitsu.xcm.domain.XcmDestinationFeeMode
import jp.co.soramitsu.xcm.domain.XcmDestinationFeeSpec
import jp.co.soramitsu.xcm.domain.XcmExecutionSpec
import jp.co.soramitsu.xcm.domain.XcmJunctionSpec
import jp.co.soramitsu.xcm.domain.XcmJunctionType
import jp.co.soramitsu.xcm.domain.XcmMultiLocationParser
import jp.co.soramitsu.xcm.domain.XcmMultiLocationSpec
import jp.co.soramitsu.xcm.domain.XcmTransferType
import jp.co.soramitsu.xcm.domain.XcmWeightLimitSpec
import jp.co.soramitsu.xcm.domain.XcmWeightLimitType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset as CoreAsset

class SubstrateXcmTransferEngineTest {

    @Test
    fun `submits limited reserve transfer call with versioned xcm arguments`() = runBlocking {
        val submitter = RecordingSubmitter()
        val engine = SubstrateXcmTransferEngine(submitter)
        val provider = FakeKeypairProvider()
        engine.updateKeypairProvider("origin", provider)

        val hash = engine.transfer(
            XcmTransferRequest(
                originChain = chain("origin"),
                destinationChain = chain("destination"),
                asset = coreAsset("DOT"),
                senderAccountId = ByteArray(32) { 1 },
                recipientAddress = "5Destination",
                amount = BigInteger.TEN,
                executionSpec = executionSpec()
            )
        )

        assertEquals("0xhash", hash)
        assertEquals("origin", submitter.submitChain!!.id)
        assertEquals(32, submitter.submitAccountId!!.size)
        assertSame(provider, submitter.submitKeypairProvider)

        val call = requireNotNull(submitter.submitCall)
        assertEquals("PolkadotXcm", call.moduleName)
        assertEquals("limitedReserveTransferAssets", call.callName)
        assertTrue(call.arguments.containsKey("weight_limit"))
        assertTrue(call.arguments["dest"].toString().contains("Parachain=2000"))
        assertTrue(call.arguments["beneficiary"].toString().contains("5Destination"))
        assertFalse(call.arguments["beneficiary"].toString().contains("<account>"))
        assertTrue(call.arguments["assets"].toString().contains("Fungible=10"))
        assertEquals(0, call.arguments["fee_asset_item"])
    }

    @Test
    fun `builds non-limited transfer call without weight limit`() {
        val engine = SubstrateXcmTransferEngine(RecordingSubmitter())

        val call = engine.buildTransferCall(
            executionSpec = executionSpec(
                transferType = XcmTransferType.RESERVE_TRANSFER_ASSETS,
                callName = "reserveTransferAssets"
            ),
            recipientAddress = "5Destination",
            amount = BigInteger.TEN
        )

        assertFalse(call.arguments.containsKey("weight_limit"))
        assertEquals("reserveTransferAssets", call.callName)
    }

    @Test
    fun `builds XTokens transfer multiasset call with explicit argument shape`() {
        val engine = SubstrateXcmTransferEngine(RecordingSubmitter())

        val call = engine.buildTransferCall(
            executionSpec = executionSpec(
                palletName = "XTokens",
                callName = "transferMultiasset",
                transferType = XcmTransferType.X_TOKENS_TRANSFER_MULTIASSET,
                argumentShape = XcmArgumentShape.X_TOKENS_TRANSFER_MULTIASSET
            ),
            recipientAddress = "5Destination",
            amount = BigInteger.TEN
        )

        assertEquals("XTokens", call.moduleName)
        assertEquals("transferMultiasset", call.callName)
        assertTrue(call.arguments["asset"].toString().contains("Fungible=10"))
        assertTrue(call.arguments["dest"].toString().contains("5Destination"))
        assertTrue(call.arguments.containsKey("dest_weight_limit"))
        assertFalse(call.arguments.containsKey("beneficiary"))
        assertFalse(call.arguments.containsKey("assets"))
        assertFalse(call.arguments.containsKey("fee_asset_item"))
    }

    @Test
    fun `rejects XCM argument shape and pallet mismatches before building calls`() {
        val engine = SubstrateXcmTransferEngine(RecordingSubmitter())

        assertThrows(IllegalArgumentException::class.java) {
            engine.buildTransferCall(
                executionSpec = executionSpec(
                    palletName = "XTokens",
                    callName = "limitedReserveTransferAssets"
                ),
                recipientAddress = "5Destination",
                amount = BigInteger.TEN
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            engine.buildTransferCall(
                executionSpec = executionSpec(
                    palletName = "PolkadotXcm",
                    callName = "transferMultiasset",
                    transferType = XcmTransferType.X_TOKENS_TRANSFER_MULTIASSET,
                    argumentShape = XcmArgumentShape.X_TOKENS_TRANSFER_MULTIASSET
                ),
                recipientAddress = "5Destination",
                amount = BigInteger.TEN
            )
        }
    }

    @Test
    fun `builds AccountKey20 beneficiary for EVM destination routes`() {
        val engine = SubstrateXcmTransferEngine(RecordingSubmitter())

        val call = engine.buildTransferCall(
            executionSpec = executionSpec(
                beneficiaryLocation = multiLocation(0, "X1(AccountKey20({network: Any, key: <account>}))")
            ),
            recipientAddress = "0x1111111111111111111111111111111111111111",
            amount = BigInteger.TEN
        )

        assertTrue(call.arguments["beneficiary"].toString().contains("AccountKey20"))
        assertTrue(call.arguments["beneficiary"].toString().contains("0x1111111111111111111111111111111111111111"))
        assertFalse(call.arguments["beneficiary"].toString().contains("<account>"))
    }

    @Test
    fun `estimates origin fee through submitter with built transfer call`() = runBlocking {
        val submitter = RecordingSubmitter(estimatedFee = BigInteger("12345"))
        val engine = SubstrateXcmTransferEngine(submitter)
        val provider = FakeKeypairProvider()
        engine.updateKeypairProvider("origin", provider)

        val fee = engine.getOriginFee(
            originChain = chain("origin"),
            originChainId = "origin",
            destinationChainId = "destination",
            asset = coreAsset("DOT"),
            originFeeAsset = coreAsset("DOT"),
            address = "5Destination",
            amount = BigInteger.TEN,
            executionSpec = executionSpec()
        )

        assertEquals(BigDecimal("0.000000012345"), fee)
        assertEquals("origin", submitter.estimateChain!!.id)
        assertEquals(32, submitter.estimateAccountId!!.size)
        assertSame(provider, submitter.estimateKeypairProvider)
        assertTrue(requireNotNull(submitter.estimateCall).arguments["beneficiary"].toString().contains("5Destination"))
    }

    @Test
    fun `scales origin fee with origin fee asset precision instead of transfer asset precision`() = runBlocking {
        val submitter = RecordingSubmitter(estimatedFee = BigInteger("12345"))
        val engine = SubstrateXcmTransferEngine(submitter)
        engine.updateKeypairProvider("origin", FakeKeypairProvider())

        val fee = engine.getOriginFee(
            originChain = chain("origin"),
            originChainId = "origin",
            destinationChainId = "destination",
            asset = coreAsset("USDT", precision = 6),
            originFeeAsset = coreAsset("DOT", precision = 12),
            address = "5Destination",
            amount = BigInteger.TEN,
            executionSpec = executionSpec(assetSymbol = "USDT")
        )

        assertEquals(BigDecimal("0.000000012345"), fee)
    }

    @Test
    fun `rejects missing or invalid keypair provider before submitter call`() {
        val submitter = RecordingSubmitter()
        val engine = SubstrateXcmTransferEngine(submitter)

        assertThrows(IllegalArgumentException::class.java) {
            engine.updateKeypairProvider("origin", Any())
        }

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                engine.transfer(
                    XcmTransferRequest(
                        originChain = chain("origin"),
                        destinationChain = chain("destination"),
                        asset = coreAsset("DOT"),
                        senderAccountId = ByteArray(32) { 1 },
                        recipientAddress = "5Destination",
                        amount = BigInteger.TEN,
                        executionSpec = executionSpec()
                    )
                )
            }
        }
        assertEquals(0, submitter.submitCount)
    }

    @Test
    fun `rejects wrong-width sender accounts before provider or submitter access`() {
        listOf(0, 1, 20, 31, 33, 64).forEach { size ->
            val submitter = RecordingSubmitter()
            val engine = SubstrateXcmTransferEngine(submitter)

            assertThrows("sender width $size should be rejected", IllegalArgumentException::class.java) {
                runBlocking {
                    engine.transfer(
                        XcmTransferRequest(
                            originChain = chain("origin"),
                            destinationChain = chain("destination"),
                            asset = coreAsset("DOT"),
                            senderAccountId = ByteArray(size),
                            recipientAddress = "5Destination",
                            amount = BigInteger.TEN,
                            executionSpec = executionSpec()
                        )
                    )
                }
            }
            assertEquals(0, submitter.submitCount)
        }
    }

    @Test
    fun `uses origin ecosystem account width for anonymous fee estimation`() = runBlocking {
        val substrateSubmitter = RecordingSubmitter()
        val substrateEngine = SubstrateXcmTransferEngine(substrateSubmitter)
        substrateEngine.updateKeypairProvider("substrate", FakeKeypairProvider())
        substrateEngine.getOriginFee(
            originChain = chain("substrate"),
            originChainId = "substrate",
            destinationChainId = "destination",
            asset = coreAsset("DOT", chainId = "substrate"),
            originFeeAsset = coreAsset("DOT", chainId = "substrate"),
            address = "5Destination",
            amount = BigInteger.TEN,
            executionSpec = executionSpec()
        )
        assertEquals(32, substrateSubmitter.estimateAccountId!!.size)

        val ethereumSubmitter = RecordingSubmitter()
        val ethereumEngine = SubstrateXcmTransferEngine(ethereumSubmitter)
        ethereumEngine.updateKeypairProvider("ethereum-based", FakeKeypairProvider())
        ethereumEngine.getOriginFee(
            originChain = chain(
                id = "ethereum-based",
                isEthereumBased = true,
                ecosystem = Ecosystem.EthereumBased
            ),
            originChainId = "ethereum-based",
            destinationChainId = "destination",
            asset = coreAsset("DOT", chainId = "ethereum-based"),
            originFeeAsset = coreAsset("DOT", chainId = "ethereum-based"),
            address = "5Destination",
            amount = BigInteger.TEN,
            executionSpec = executionSpec()
        )
        assertEquals(20, ethereumSubmitter.estimateAccountId!!.size)
    }

    @Test
    fun `rejects invalid xcm version and blank recipient before submitter call`() {
        val submitter = RecordingSubmitter()
        val engine = SubstrateXcmTransferEngine(submitter)
        engine.updateKeypairProvider("origin", FakeKeypairProvider())

        assertThrows(IllegalArgumentException::class.java) {
            engine.buildTransferCall(
                executionSpec = executionSpec(xcmVersion = "three"),
                recipientAddress = "5Destination",
                amount = BigInteger.TEN
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            engine.buildTransferCall(
                executionSpec = executionSpec(),
                recipientAddress = " ",
                amount = BigInteger.TEN
            )
        }
        assertEquals(0, submitter.submitCount)
    }

    @Test
    fun `rejects nonzero fee index and mismatched fee asset location before submitter call`() {
        val submitter = RecordingSubmitter()
        val engine = SubstrateXcmTransferEngine(submitter)

        assertThrows(IllegalArgumentException::class.java) {
            engine.buildTransferCall(
                executionSpec = executionSpec(feeAssetItem = 1),
                recipientAddress = "5Destination",
                amount = BigInteger.TEN
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            engine.buildTransferCall(
                executionSpec = executionSpec(feeAssetLocation = multiLocation(0, "Here")),
                recipientAddress = "5Destination",
                amount = BigInteger.TEN
            )
        }
        assertEquals(0, submitter.submitCount)
    }

    @Test
    fun `rejects recipient-controlled route locations and malformed beneficiary authority`() {
        val engine = SubstrateXcmTransferEngine(RecordingSubmitter())
        val accountJunction = XcmJunctionSpec(
            type = XcmJunctionType.ACCOUNT_ID32,
            value = "{network: Any, id: <account>}"
        )
        val recipientControlledDestination = executionSpec().copy(
            destinationLocation = XcmMultiLocationSpec(
                parents = 0,
                interior = "X1(AccountId32({network: Any, id: <account>}))",
                junctions = listOf(accountJunction)
            )
        )
        val missingBeneficiary = executionSpec().copy(
            beneficiaryLocation = multiLocation(0, "Here")
        )
        val prefixedBeneficiary = executionSpec().copy(
            beneficiaryLocation = XcmMultiLocationSpec(
                parents = 0,
                interior = "X1(AccountId32({network: Any, id: prefix<account>}))",
                junctions = listOf(accountJunction.copy(value = "{network: Any, id: prefix<account>}"))
            )
        )
        val suffixedBeneficiary = executionSpec().copy(
            beneficiaryLocation = XcmMultiLocationSpec(
                parents = 0,
                interior = "X1(AccountId32({network: Any, id: <account>suffix}))",
                junctions = listOf(accountJunction.copy(value = "{network: Any, id: <account>suffix}"))
            )
        )

        listOf(
            recipientControlledDestination,
            missingBeneficiary,
            prefixedBeneficiary,
            suffixedBeneficiary
        ).forEach { malformed ->
            assertThrows(IllegalArgumentException::class.java) {
                engine.buildTransferCall(malformed, "5Destination", BigInteger.TEN)
            }
        }
    }

    @Test
    fun `returns zero when destination fee is included`() = runBlocking {
        val engine = SubstrateXcmTransferEngine(RecordingSubmitter())

        val fee = engine.getDestinationFee(
            originChainId = "origin",
            destinationChainId = "destination",
            asset = coreAsset("DOT"),
            executionSpec = executionSpec(destinationFeeMode = XcmDestinationFeeMode.INCLUDED)
        )

        assertEquals(BigDecimal.ZERO, fee)
    }

    @Test
    fun `returns fixed destination fee normalized by asset precision`() = runBlocking {
        val engine = SubstrateXcmTransferEngine(RecordingSubmitter())

        val fee = engine.getDestinationFee(
            originChainId = "origin",
            destinationChainId = "destination",
            asset = coreAsset("DOT"),
            executionSpec = executionSpec(
                destinationFeeMode = XcmDestinationFeeMode.FIXED,
                destinationFeeAmount = BigInteger("123450000000")
            )
        )

        assertEquals(BigDecimal("0.123450000000"), fee)
    }

    @Test
    fun `rejects estimated destination fee until destination estimator is configured`() {
        val engine = SubstrateXcmTransferEngine(RecordingSubmitter())

        assertThrows(UnsupportedOperationException::class.java) {
            runBlocking {
                engine.getDestinationFee(
                    originChainId = "origin",
                    destinationChainId = "destination",
                    asset = coreAsset("DOT"),
                    executionSpec = executionSpec(destinationFeeMode = XcmDestinationFeeMode.ESTIMATED)
                )
            }
        }
    }

    @Test
    fun `rejects destination fee asset mismatch before returning fixed fee`() {
        val engine = SubstrateXcmTransferEngine(RecordingSubmitter())

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                engine.getDestinationFee(
                    originChainId = "origin",
                    destinationChainId = "destination",
                    asset = coreAsset("KSM"),
                    executionSpec = executionSpec(
                        destinationFeeMode = XcmDestinationFeeMode.FIXED,
                        destinationFeeAmount = BigInteger("1000")
                    )
                )
            }
        }
    }

    @Test
    fun `rejects bridge execution before building calls or calculating fees`() {
        val engine = SubstrateXcmTransferEngine(RecordingSubmitter())
        val bridgeSpec = XcmBridgeExecutionSpec(
            parachainId = "3000",
            feeAssetLocation = multiLocation(1, "Here"),
            feeAssetItem = 0
        )
        val executionSpec = executionSpec(bridge = bridgeSpec)

        val callError = assertThrows(IllegalArgumentException::class.java) {
            engine.buildTransferCall(executionSpec, "5Destination", BigInteger.TEN)
        }
        val feeError = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                engine.getDestinationFee(
                    originChainId = "origin",
                    destinationChainId = "destination",
                    asset = coreAsset("DOT"),
                    executionSpec = executionSpec
                )
            }
        }

        val marker = "XCM bridge execution is unsupported until the production transfer engine consumes bridge fee semantics"
        assertEquals(marker, callError.message)
        assertEquals(marker, feeError.message)
    }

    @Test
    fun `rejects origin fee chain mismatch before submitter call`() {
        val submitter = RecordingSubmitter()
        val engine = SubstrateXcmTransferEngine(submitter)
        engine.updateKeypairProvider("origin", FakeKeypairProvider())

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                engine.getOriginFee(
                    originChain = chain("other"),
                    originChainId = "origin",
                    destinationChainId = "destination",
                    asset = coreAsset("DOT"),
                    originFeeAsset = coreAsset("DOT"),
                    address = "5Destination",
                    amount = BigInteger.TEN,
                    executionSpec = executionSpec()
                )
            }
        }
        assertEquals(0, submitter.estimateCount)
    }

    @Test
    fun `rejects negative origin fee asset precision before submitter call`() {
        val submitter = RecordingSubmitter()
        val engine = SubstrateXcmTransferEngine(submitter)
        engine.updateKeypairProvider("origin", FakeKeypairProvider())

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                engine.getOriginFee(
                    originChain = chain("origin"),
                    originChainId = "origin",
                    destinationChainId = "destination",
                    asset = coreAsset("DOT"),
                    originFeeAsset = coreAsset("DOT", precision = -1),
                    address = "5Destination",
                    amount = BigInteger.TEN,
                    executionSpec = executionSpec()
                )
            }
        }
        assertEquals(0, submitter.estimateCount)
    }

    @Test
    fun `rejects origin fee asset from a different chain before submitter call`() {
        val submitter = RecordingSubmitter()
        val engine = SubstrateXcmTransferEngine(submitter)
        engine.updateKeypairProvider("origin", FakeKeypairProvider())

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                engine.getOriginFee(
                    originChain = chain("origin"),
                    originChainId = "origin",
                    destinationChainId = "destination",
                    asset = coreAsset("DOT"),
                    originFeeAsset = coreAsset("DOT", chainId = "other"),
                    address = "5Destination",
                    amount = BigInteger.TEN,
                    executionSpec = executionSpec()
                )
            }
        }
        assertEquals(0, submitter.estimateCount)
    }

    private fun executionSpec(
        palletName: String = "PolkadotXcm",
        transferType: XcmTransferType = XcmTransferType.LIMITED_RESERVE_TRANSFER_ASSETS,
        callName: String = "limitedReserveTransferAssets",
        argumentShape: XcmArgumentShape = XcmArgumentShape.POLKADOT_XCM_TRANSFER_ASSETS,
        xcmVersion: String = "v3",
        destinationFeeMode: XcmDestinationFeeMode = XcmDestinationFeeMode.ESTIMATED,
        destinationFeeAmount: BigInteger? = null,
        assetSymbol: String = "DOT",
        beneficiaryLocation: XcmMultiLocationSpec = multiLocation(0, "X1(AccountId32({network: Any, id: <account>}))"),
        feeAssetLocation: XcmMultiLocationSpec = multiLocation(1, "X2(Parachain(1000), GeneralKey(dot))"),
        feeAssetItem: Int = 0,
        bridge: XcmBridgeExecutionSpec? = null
    ) = XcmExecutionSpec(
        palletName = palletName,
        callName = callName,
        transferType = transferType,
        argumentShape = argumentShape,
        xcmVersion = xcmVersion,
        destinationLocation = multiLocation(1, "X1(Parachain(2000))"),
        assetLocation = multiLocation(1, "X2(Parachain(1000), GeneralKey(dot))"),
        beneficiaryLocation = beneficiaryLocation,
        feeAssetLocation = feeAssetLocation,
        feeAssetItem = feeAssetItem,
        weightLimit = XcmWeightLimitSpec(
            type = XcmWeightLimitType.LIMITED,
            refTime = BigInteger("6000000000"),
            proofSize = BigInteger("65536")
        ),
        destinationFee = XcmDestinationFeeSpec(
            mode = destinationFeeMode,
            assetSymbol = assetSymbol,
            amount = destinationFeeAmount
        ),
        bridge = bridge
    )

    private fun multiLocation(parents: Int, interior: String) = XcmMultiLocationSpec(
        parents = parents,
        interior = interior,
        junctions = XcmMultiLocationParser.requireValidInterior(interior, "test")
    )

    private fun coreAsset(
        symbol: String,
        precision: Int = 12,
        chainId: String = "origin"
    ) = CoreAsset(
        id = "asset-$symbol",
        name = symbol,
        symbol = symbol,
        iconUrl = "",
        chainId = chainId,
        chainName = chainId,
        chainIcon = null,
        isTestNet = false,
        priceId = null,
        precision = precision,
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

    private fun chain(
        id: String,
        isEthereumBased: Boolean = false,
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
        isEthereumBased = isEthereumBased,
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
        xcm = null
    )

    private class RecordingSubmitter(
        private val estimatedFee: BigInteger = BigInteger("99")
    ) : XcmExtrinsicSubmitter {
        var submitCount = 0
        var estimateCount = 0
        var submitChain: Chain? = null
        var estimateChain: Chain? = null
        var submitAccountId: ByteArray? = null
        var estimateAccountId: ByteArray? = null
        var submitKeypairProvider: KeypairProvider? = null
        var estimateKeypairProvider: KeypairProvider? = null
        var submitCall: XcmExtrinsicCall? = null
        var estimateCall: XcmExtrinsicCall? = null

        override suspend fun submit(
            chain: Chain,
            accountId: ByteArray,
            keypairProvider: KeypairProvider,
            call: XcmExtrinsicCall
        ): String {
            submitCount += 1
            submitChain = chain
            submitAccountId = accountId
            submitKeypairProvider = keypairProvider
            submitCall = call
            return "0xhash"
        }

        override suspend fun estimateFee(
            chain: Chain,
            accountId: ByteArray,
            keypairProvider: KeypairProvider,
            call: XcmExtrinsicCall
        ): BigInteger {
            estimateCount += 1
            estimateChain = chain
            estimateAccountId = accountId
            estimateKeypairProvider = keypairProvider
            estimateCall = call
            return estimatedFee
        }
    }

    private class FakeKeypairProvider : KeypairProvider {
        override suspend fun getCryptoTypeFor(
            chain: jp.co.soramitsu.core.models.IChain,
            accountId: ByteArray
        ): CryptoType {
            error("Keypair lookup should be delegated to production submitter")
        }

        override suspend fun getKeypairFor(chain: jp.co.soramitsu.core.models.IChain, accountId: ByteArray): Keypair {
            error("Keypair lookup should be delegated to production submitter")
        }
    }
}
