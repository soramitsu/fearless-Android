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
    fun `compiled disable rejects direct transfer even when remote configuration enables it`() = runBlocking {
        val submitter = RecordingSubmitter()
        var remoteRead = false
        val engine = MutationGuardedXcmTransferEngine(
            // No keypair provider is installed: the compiled guard must reject before signing setup.
            delegate = SubstrateXcmTransferEngine(submitter),
            transfersEnabled = false,
            mutationsEnabled = {
                remoteRead = true
            true
            }
        )
        val request = XcmTransferRequest(
            originChain = chain("origin"),
            destinationChain = chain("destination"),
            asset = coreAsset("DOT"),
            senderAccountId = ByteArray(32) { 1 },
            recipientAddress = "5Destination",
            amount = BigInteger.TEN,
            executionSpec = executionSpec()
        )

        val failure = assertThrows(IllegalStateException::class.java) {
            runBlocking { engine.transfer(request) }
        }
        assertEquals("Reviewed XCM actions are unavailable in this build.", failure.message)
        assertFalse(remoteRead)
        assertEquals(0, submitter.submitCount)
        assertEquals(0, submitter.estimateCount)
        assertTrue(engine.isAvailable)
    }

    @Test
    fun `mutation guard observes runtime switch and blocks final submit boundary`() = runBlocking {
        val submitter = RecordingSubmitter()
        val delegate = SubstrateXcmTransferEngine(submitter).apply {
            updateKeypairProvider("origin", FakeKeypairProvider())
        }
        var mutationsEnabled = false
        val engine = MutationGuardedXcmTransferEngine(
            delegate = delegate,
            transfersEnabled = true,
            mutationsEnabled = { mutationsEnabled }
        )
        val request = XcmTransferRequest(
            originChain = chain("origin"),
            destinationChain = chain("destination"),
            asset = coreAsset("DOT"),
            senderAccountId = ByteArray(32) { 1 },
            recipientAddress = "5Destination",
            amount = BigInteger.TEN,
            executionSpec = executionSpec()
        )

        val disabled = assertThrows(IllegalStateException::class.java) {
            runBlocking { engine.transfer(request) }
        }
        assertEquals("Reviewed XCM actions are temporarily disabled.", disabled.message)
        assertEquals(0, submitter.submitCount)

        mutationsEnabled = true
        assertEquals("0xhash", engine.transfer(request))
        assertEquals(1, submitter.submitCount)
    }

    @Test
    fun `a tracked transfer is signed through its wallet-bound provider, not the quote provider`() = runBlocking {
        val submitter = RecordingSubmitter()
        val engine = SubstrateXcmTransferEngine(submitter).apply {
            updateKeypairProvider("origin", FakeKeypairProvider())
        }
        val walletProvider = FakeKeypairProvider()
        val submission = XcmTrackedSubmission(walletId = 7, keypairProvider = walletProvider, tracking = NoTracking)

        val hash = engine.transfer(
            XcmTransferRequest(
                originChain = chain("origin"),
                destinationChain = chain("destination"),
                asset = coreAsset("DOT"),
                senderAccountId = ByteArray(32) { 1 },
                recipientAddress = "5Destination",
                amount = BigInteger.TEN,
                executionSpec = executionSpec(),
                submission = submission
            )
        )

        assertEquals("0xhash", hash)
        assertSame(submission, submitter.trackedSubmission)
        assertSame(walletProvider, submitter.submitKeypairProvider)
    }

    @Test
    fun `the production submitter refuses an untracked cross-chain submission`() = runBlocking {
        val submitter = ExtrinsicServiceXcmSubmitter(
            rpcCalls = org.mockito.Mockito.mock(jp.co.soramitsu.core.rpc.RpcCalls::class.java),
            extrinsicBuilderFactory = org.mockito.Mockito.mock(jp.co.soramitsu.core.extrinsic.ExtrinsicBuilderFactory::class.java),
            mortalityConstructor = org.mockito.Mockito.mock(jp.co.soramitsu.core.extrinsic.mortality.MortalityConstructor::class.java)
        ) { error("must not authorize") }

        val failure = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                submitter.submit(chain("origin"), ByteArray(32), FakeKeypairProvider(), XcmExtrinsicCall("M", "c", emptyMap()))
            }
        }

        assertEquals("Cross-chain submissions must be tracked", failure.message)
    }

    @Test
    fun `a tracked submission is refused when the connected node serves another network`() = runBlocking {
        val rpc = org.mockito.Mockito.mock(jp.co.soramitsu.core.rpc.RpcCalls::class.java)
        val factory = org.mockito.Mockito.mock(jp.co.soramitsu.core.extrinsic.ExtrinsicBuilderFactory::class.java)
        org.mockito.Mockito.`when`(rpc.getBlockHash("origin", BigInteger.ZERO)).thenReturn("0x" + "ff".repeat(32))
        val submitter = ExtrinsicServiceXcmSubmitter(
            rpcCalls = rpc,
            extrinsicBuilderFactory = factory,
            mortalityConstructor = org.mockito.Mockito.mock(jp.co.soramitsu.core.extrinsic.mortality.MortalityConstructor::class.java)
        ) { error("must not authorize") }

        val failure = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                submitter.submitTracked(
                    chain("origin"),
                    ByteArray(32) { 1 },
                    XcmExtrinsicCall("M", "c", emptyMap()),
                    XcmTrackedSubmission(7, FakeKeypairProvider(), NoTracking)
                )
            }
        }

        assertEquals("The connected node serves another network", failure.message)
        org.mockito.Mockito.verifyNoInteractions(factory)
    }

    @Test
    fun `mutation guard leaves reviewed discovery and fee quotes available while writes are off`() = runBlocking {
        val engine = MutationGuardedXcmTransferEngine(
            delegate = SubstrateXcmTransferEngine(RecordingSubmitter()),
            transfersEnabled = false,
            mutationsEnabled = { false }
        )

        assertTrue(engine.isAvailable)
        assertEquals(
            BigDecimal.ZERO,
            engine.getDestinationFee(
                originChainId = "origin",
                destinationChainId = "destination",
                asset = coreAsset("DOT"),
                executionSpec = executionSpec(destinationFeeMode = XcmDestinationFeeMode.INCLUDED)
            )
        )
    }

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
        assertEquals(listOf("dest", "beneficiary", "assets", "fee_asset_item", "weight_limit"), call.arguments.keys.toList())
        assertEquals(mapOf("V3" to location(1, parachain(2000))), call.arguments["dest"])
        assertEquals(mapOf("V3" to location(0, accountId32("5Destination"))), call.arguments["beneficiary"])
        assertEquals(mapOf("V3" to listOf(dotTen())), call.arguments["assets"])
        assertEquals(0, call.arguments["fee_asset_item"])
        assertEquals(
            mapOf("Limited" to mapOf("refTime" to BigInteger("6000000000"), "proofSize" to BigInteger("65536"))),
            call.arguments["weight_limit"]
        )
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
            executionSpec = xTokensSpec(),
            recipientAddress = "5Destination",
            amount = BigInteger.TEN
        )

        assertEquals("XTokens", call.moduleName)
        assertEquals("transferMultiasset", call.callName)
        assertEquals(listOf("asset", "dest", "dest_weight_limit"), call.arguments.keys.toList())
        assertEquals(mapOf("V3" to dotTen()), call.arguments["asset"])
        // The single xTokens destination is the reviewed destination location with the recipient appended.
        assertEquals(mapOf("V3" to location(1, parachain(2000), accountId32("5Destination"))), call.arguments["dest"])
    }

    @Test
    fun `an XTokens beneficiary that leaves the reviewed destination is refused`() {
        val engine = SubstrateXcmTransferEngine(RecordingSubmitter())
        val strayBeneficiaries = listOf(
            multiLocation(0, "X1(AccountId32({network: Any, id: <account>}))"),
            multiLocation(1, "X2(Parachain(3000), AccountId32({network: Any, id: <account>}))"),
            multiLocation(2, "X2(Parachain(2000), AccountId32({network: Any, id: <account>}))"),
            multiLocation(1, "X3(Parachain(2000), PalletInstance(50), AccountId32({network: Any, id: <account>}))")
        )

        strayBeneficiaries.forEach { beneficiary ->
            assertThrows("beneficiary ${beneficiary.interior}", IllegalArgumentException::class.java) {
                engine.buildTransferCall(xTokensSpec(beneficiaryLocation = beneficiary), "5Destination", BigInteger.TEN)
            }
        }
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

        assertEquals(
            mapOf("V3" to location(0, mapOf("AccountKey20" to mapOf("key" to "0x1111111111111111111111111111111111111111")))),
            call.arguments["beneficiary"]
        )
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
        assertEquals(
            mapOf("V3" to location(0, accountId32("5Destination"))),
            requireNotNull(submitter.estimateCall).arguments["beneficiary"]
        )
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

    // V3 runtime values as the engine builds them: X1 holds one junction, X2 and above a list.
    private fun location(parents: Int, vararg junctions: Map<String, Any?>): Map<String, Any?> = mapOf(
        "parents" to parents,
        "interior" to when (junctions.size) {
            0 -> "Here"
            1 -> mapOf("X1" to junctions.single())
            else -> mapOf("X${junctions.size}" to junctions.toList())
        }
    )

    private fun parachain(id: Int) = mapOf("Parachain" to id.toBigInteger())

    private fun accountId32(address: String) = mapOf("AccountId32" to mapOf("id" to address))

    // Ten planks of the spec's asset location, X2(Parachain(1000), GeneralKey(dot)), as a V3 concrete asset.
    private fun dotTen() = mapOf(
        "id" to mapOf("Concrete" to location(1, parachain(1000), mapOf("GeneralKey" to "dot"))),
        "fun" to mapOf("Fungible" to BigInteger.TEN)
    )

    private fun xTokensSpec(
        beneficiaryLocation: XcmMultiLocationSpec =
            multiLocation(1, "X2(Parachain(2000), AccountId32({network: Any, id: <account>}))")
    ) = executionSpec(
        palletName = "XTokens",
        callName = "transferMultiasset",
        transferType = XcmTransferType.X_TOKENS_TRANSFER_MULTIASSET,
        argumentShape = XcmArgumentShape.X_TOKENS_TRANSFER_MULTIASSET,
        beneficiaryLocation = beneficiaryLocation
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
        var trackedSubmission: XcmTrackedSubmission? = null

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

        override suspend fun submitTracked(
            chain: Chain,
            accountId: ByteArray,
            call: XcmExtrinsicCall,
            submission: XcmTrackedSubmission
        ): String {
            trackedSubmission = submission
            return submit(chain, accountId, submission.keypairProvider, call)
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

    private object NoTracking : jp.co.soramitsu.core.extrinsic.ExtrinsicSubmissionTracking {
        override fun guard(
            signedExtrinsic: String,
            intent: String,
            authority: jp.co.soramitsu.core.extrinsic.MutationExecutionGuard
        ) = authority

        override fun acknowledged(transactionHash: String) = Unit

        override fun finished() = Unit
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
