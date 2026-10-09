package jp.co.soramitsu.xcm

import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.fearless_utils.encrypt.keypair.BaseKeypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.xcm.domain.CrossChainProtocol
import jp.co.soramitsu.xcm.domain.CrossChainRouteAvailability
import jp.co.soramitsu.xcm.domain.CrossChainRouteQuery
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class ReviewedPolkaswapBridgeTest {

    @Test
    fun `catalog ports all fourteen reviewed one-call directions and only EVM remains unavailable`() {
        val catalog = ReviewedPolkaswapBridgeCatalog

        assertEquals(14, catalog.executableRoutes.size)
        assertEquals(
            8,
            catalog.executableForProvider(ReviewedPolkaswapBridgeProviderIds.SORA_SUBSTRATE).size
        )
        assertEquals(
            6,
            catalog.executableForProvider(ReviewedPolkaswapBridgeProviderIds.LIBERLAND).size
        )
        assertTrue(catalog.executableRoutes.none { it.providerId == ReviewedPolkaswapBridgeProviderIds.SORA_EVM })
        val substrate = catalog.executableForProvider(ReviewedPolkaswapBridgeProviderIds.SORA_SUBSTRATE)
        assertEquals(4, substrate.count { it.originChainId == catalog.SORA_CHAIN_ID })
        assertEquals(4, substrate.count { it.destinationChainId == catalog.SORA_CHAIN_ID })

        val soraLld = catalog.executableRoutes.single {
            it.originChainId == catalog.SORA_CHAIN_ID && it.destinationChainId == catalog.LIBERLAND_CHAIN_ID &&
                it.symbol == "LLD"
        }
        assertEquals("1.1", soraLld.minimumAmount)
        assertEquals("bridgeProxy", soraLld.execution.runtimeCall.pallet)
        assertEquals(listOf("networkId", "assetId", "recipient", "amount"), soraLld.execution.runtimeCall.arguments)

        val reverseLld = catalog.executableRoutes.single {
            it.originChainId == catalog.LIBERLAND_CHAIN_ID && it.symbol == "LLD"
        }
        assertEquals("1.1", reverseLld.minimumAmount)
        assertEquals("a6b83d39-a488-4b34-8352-280705a792ea", reverseLld.originCanonicalAssetId)
        assertEquals("a6b83d39-a488-4b34-8352-280705a792e", reverseLld.originXcmAssetId)
        assertEquals(ReviewedBridgeExternalAsset.Lld, reverseLld.execution.externalAsset)

        val external = substrate.filter { it.execution.kind == ReviewedBridgeExecutionKind.ExternalToSoraXcmV3 }
        assertEquals(setOf("DOT", "KSM", "ACA", "ASTR"), external.mapTo(linkedSetOf()) { it.symbol })
        assertEquals(
            mapOf("DOT" to "1.1", "KSM" to "0.05", "ACA" to "56", "ASTR" to "73"),
            external.associate { it.symbol to it.minimumAmount }
        )
        assertTrue(
            external.all {
            it.execution.destinationMinimum is ReviewedBridgeDestinationMinimum.SoraParachainAssetMinimum
        }
        )

        val ethereum = catalog.unavailableRoutes.filter { it.symbol == "ETH" }
        assertEquals(2, ethereum.size)
        assertEquals(2, catalog.unavailableRoutes.size)
        assertTrue(
            catalog.unavailableRoutes.all { route ->
                route.routeId == unavailableReviewedRouteId(
                    route.providerId,
                    route.originChainId,
                    route.destinationChainId,
                    route.originAssetId
                )
            }
        )
        assertTrue(ethereum.any { it.reason.contains("second claim transaction and recovery tracking") })
        assertTrue(ethereum.any { it.reason.contains("contract transaction, live gas and allowance checks") })
    }

    @Test
    fun `provider exposes immutable context only for an exact runtime-resolved route`() = runBlocking {
        val route = dotRoute()
        var enabled = false
        val provider = ReviewedPolkaswapBridgeRouteProvider(
            providerId = ReviewedPolkaswapBridgeProviderIds.SORA_SUBSTRATE,
            protocol = CrossChainProtocol.PolkaswapSoraSubstrate,
            runtimeResolver = fixedRuntimeResolver(),
            actionsEnabled = { enabled },
            actionsDisabledReason = { "Polkaswap bridge actions are temporarily disabled." }
        )

        val broad = provider.capability(CrossChainRouteQuery(originNetworkId = route.originChainId))
        assertTrue(broad.hasRoute)
        assertEquals(null, broad.providerContext)

        val disabled = provider.capability(route.query())
        assertTrue(disabled.hasRoute)
        assertFalse(disabled.canExecute)
        assertEquals(route.routeId, disabled.providerContext?.routeId)
        assertNotNull(disabled.providerContext?.runtimeFingerprint)

        enabled = true
        val executable = provider.capability(route.query())
        assertTrue(executable.canExecute)
        assertEquals(route.providerId, executable.providerContext?.providerId)
    }

    @Test
    fun `ethereum provider discloses both exact directions but authorizes neither`() = runBlocking {
        val provider = ReviewedPolkaswapBridgeRouteProvider(
            providerId = ReviewedPolkaswapBridgeProviderIds.SORA_EVM,
            protocol = CrossChainProtocol.PolkaswapSoraEvm,
            runtimeResolver = fixedRuntimeResolver(),
            actionsEnabled = { true },
            actionsDisabledReason = { "must remain unavailable" }
        )

        val inventory = provider.inventoryCapabilities()
        assertEquals(2, inventory.size)
        assertTrue(inventory.all { it.availability == CrossChainRouteAvailability.Unavailable })
        assertTrue(inventory.none { it.canExecute || it.providerContext != null })
    }

    @Test
    fun `sora to substrate submits exact bridgeProxy burn after final re-resolution`() = runBlocking {
        val route = dotRoute()
        val submitter = RecordingSubmitter()
        val executor = executor(route, submitter)
        val request = request(route)

        val quote = executor.quote(request)
        val hash = executor.submit(request, quote)

        assertEquals("0xbridge", hash)
        assertEquals(1, submitter.submitCount)
        assertEquals(2, submitter.estimateCount)
        val call = requireNotNull(submitter.submittedCall)
        assertEquals("BridgeProxy", call.moduleName)
        assertEquals("burn", call.callName)
        assertEquals(mapOf("Sub" to "Polkadot"), call.arguments["network_id"])
        assertEquals(route.execution.soraAssetId, call.arguments["asset_id"])
        assertEquals(request.amountInPlanks, call.arguments["amount"])
        assertTrue(call.arguments["recipient"].toString().contains("Parachain"))
        assertTrue(call.arguments["recipient"].toString().contains("AccountId32"))
    }

    @Test
    fun `liberland to sora builds exact soraBridgeApp burn`() {
        val route = ReviewedPolkaswapBridgeCatalog.executableRoutes.single {
            it.originChainId == ReviewedPolkaswapBridgeCatalog.LIBERLAND_CHAIN_ID && it.symbol == "LLM"
        }
        val recipient = ByteArray(32) { 7 }.toAddress(69)
        val executor = executor(route, RecordingSubmitter())
        val call = executor.buildCall(route, runtime(route), recipient, BigInteger.TEN)

        assertEquals("SoraBridgeApp", call.moduleName)
        assertEquals("burn", call.callName)
        assertEquals("Mainnet", call.arguments["network_id"])
        assertEquals(mapOf("Asset" to 1L), call.arguments["asset_id"])
        assertEquals(mapOf("Sora" to recipient), call.arguments["recipient"])
        assertEquals(BigInteger.TEN, call.arguments["amount"])
    }

    @Test
    fun `liberland LLD reverse uses exact distinct catalog and XCM identities`() {
        val route = ReviewedPolkaswapBridgeCatalog.executableRoutes.single {
            it.originChainId == ReviewedPolkaswapBridgeCatalog.LIBERLAND_CHAIN_ID && it.symbol == "LLD"
        }
        val recipient = ByteArray(32) { 7 }.toAddress(69)
        val call = executor(route, RecordingSubmitter()).buildCall(
            route,
            runtime(route),
            recipient,
            BigInteger.TEN
        )

        assertEquals("a6b83d39-a488-4b34-8352-280705a792ea", route.originCanonicalAssetId)
        assertEquals("a6b83d39-a488-4b34-8352-280705a792e", route.originXcmAssetId)
        assertEquals("LLD", call.arguments["asset_id"])
        assertEquals(mapOf("Sora" to recipient), call.arguments["recipient"])
    }

    @Test
    fun `relay to sora builds exact reviewed reserve transfer V3`() {
        val route = externalRoute("DOT")
        val recipientBytes = ByteArray(32) { 7 }
        val recipient = recipientBytes.toAddress(69)
        val call = executor(route, RecordingSubmitter()).buildCall(route, runtime(route), recipient, BigInteger.TEN)
        val account = mapOf("AccountId32" to mapOf("id" to "0x${"07".repeat(32)}"))

        assertEquals("XcmPallet", call.moduleName)
        assertEquals("reserveTransferAssets", call.callName)
        assertEquals(
            mapOf(
                "V3" to mapOf(
                    "parents" to 0,
                    "interior" to mapOf("X1" to mapOf("Parachain" to 2025))
                )
            ),
            call.arguments["dest"]
        )
        assertEquals(
            mapOf("V3" to mapOf("parents" to 0, "interior" to mapOf("X1" to account))),
            call.arguments["beneficiary"]
        )
        assertEquals(
            mapOf(
                "V3" to listOf(
                    mapOf(
                        "id" to mapOf("Concrete" to mapOf("parents" to 0, "interior" to "Here")),
                        "fun" to mapOf("Fungible" to BigInteger.TEN)
                    )
                )
            ),
            call.arguments["assets"]
        )
        assertEquals(0, call.arguments["fee_asset_item"])
    }

    @Test
    fun `astar and acala to sora build their exact reviewed call variants`() {
        val recipient = ByteArray(32) { 7 }.toAddress(69)
        val account = mapOf("AccountId32" to mapOf("id" to "0x${"07".repeat(32)}"))
        val astar = externalRoute("ASTR")
        val astarCall = executor(astar, RecordingSubmitter()).buildCall(
            astar,
            runtime(astar),
            recipient,
            BigInteger.TEN
        )
        assertEquals("PolkadotXcm", astarCall.moduleName)
        assertEquals(
            mapOf(
                "V3" to mapOf(
                    "parents" to 1,
                    "interior" to mapOf("X1" to mapOf("Parachain" to 2025))
                )
            ),
            astarCall.arguments["dest"]
        )

        val acala = externalRoute("ACA")
        val acalaCall = executor(acala, RecordingSubmitter()).buildCall(
            acala,
            runtime(acala),
            recipient,
            BigInteger.TEN
        )
        assertEquals("XTokens", acalaCall.moduleName)
        assertEquals("transfer", acalaCall.callName)
        assertEquals(mapOf("Token" to "ACA"), acalaCall.arguments["currency_id"])
        assertEquals(BigInteger.TEN, acalaCall.arguments["amount"])
        assertEquals(
            mapOf(
                "V3" to mapOf(
                    "parents" to 1,
                    "interior" to mapOf(
                        "X2" to listOf(mapOf("Parachain" to 2025), account)
                    )
                )
            ),
            acalaCall.arguments["dest"]
        )
        assertEquals(mapOf("Unlimited" to null), acalaCall.arguments["dest_weight_limit"])
    }

    @Test
    fun `all reviewed production calls convert to audited metadata-native SCALE values`() {
        val recipient = ByteArray(32) { 7 }.toAddress(69)
        val signatures = linkedSetOf<String>()

        ReviewedPolkaswapBridgeCatalog.executableRoutes.forEach { route ->
            val call = executor(route, RecordingSubmitter()).buildCall(
                route = route,
                runtime = runtime(route),
                recipientAddress = recipient,
                amountInPlanks = BigInteger.TEN
            )
            val function = ReviewedBridgeRuntimeMetadataContractFixture.functionFor(call)
            val runtimeArguments = call.toRuntimeArguments(function)

            signatures += "${call.moduleName}.${call.callName}"
            assertEquals(function.arguments.map { it.name }, runtimeArguments.keys.toList())
            function.arguments.forEach { argument ->
                val type = requireNotNull(argument.type)
                assertTrue(
                    "${call.moduleName}.${call.callName}.${argument.name} is not metadata-native",
                    type.isValidInstance(runtimeArguments[argument.name])
                )
            }
        }

        assertEquals(
            setOf(
                "BridgeProxy.burn",
                "SoraBridgeApp.burn",
                "XcmPallet.reserveTransferAssets",
                "XTokens.transfer",
                "PolkadotXcm.reserveTransferAssets"
            ),
            signatures
        )
    }

    @Test
    fun `runtime fingerprint drift blocks submit`() = runBlocking {
        val route = dotRoute()
        val submitter = RecordingSubmitter()
        var runtimeVersion = "1"
        val executor = executor(
            route = route,
            submitter = submitter,
            runtimeResolver = ReviewedBridgeRuntimeResolver { runtime(route, runtimeVersion) }
        )
        val request = request(route)
        val quote = executor.quote(request)
        runtimeVersion = "2"

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { executor.submit(request, quote) }
        }
        assertEquals(0, submitter.submitCount)
    }

    @Test
    fun `runtime argument spelling drift is not normalized into an authority match`() = runBlocking {
        val route = dotRoute()
        val submitter = RecordingSubmitter()
        val executor = executor(
            route = route,
            submitter = submitter,
            runtimeResolver = ReviewedBridgeRuntimeResolver {
                runtime(route).copy(
                    rawArgumentNames = listOf("network-id", "asset_id", "recipient", "amount")
                )
            }
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { executor.quote(request(route)) }
        }
        assertEquals(0, submitter.estimateCount)
        assertEquals(0, submitter.submitCount)
    }

    @Test
    fun `fee drift blocks submit`() = runBlocking {
        val route = dotRoute()
        val submitter = RecordingSubmitter(fees = ArrayDeque(listOf(BigInteger.TEN, BigInteger.valueOf(11))))
        val executor = executor(route, submitter)
        val request = request(route)
        val quote = executor.quote(request)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { executor.submit(request, quote) }
        }
        assertEquals(0, submitter.submitCount)
    }

    @Test
    fun `kill switch is checked at final mutation boundary`() = runBlocking {
        val route = dotRoute()
        val submitter = RecordingSubmitter()
        var enabled = true
        val executor = executor(route, submitter, mutationsEnabled = { enabled })
        val request = request(route)
        val quote = executor.quote(request)
        enabled = false

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { executor.submit(request, quote) }
        }
        assertEquals("Polkaswap bridge actions are temporarily disabled.", error.message)
        assertEquals(0, submitter.submitCount)
        assertEquals(2, submitter.estimateCount)
    }

    @Test
    fun `fresh account balance drift blocks submit`() = runBlocking {
        val route = dotRoute()
        val submitter = RecordingSubmitter()
        var reads = 0
        val executor = executor(
            route,
            submitter,
            balanceReader = ReviewedBridgeBalanceReader { origin, transfer, fee, account ->
                reads += 1
                balance(origin, transfer, fee, if (reads == 1) account else ByteArray(32) { 9 })
            }
        )
        val request = request(route)
        val quote = executor.quote(request)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { executor.submit(request, quote) }
        }
        assertEquals(0, submitter.submitCount)
    }

    @Test
    fun `stale or insufficient authoritative balance blocks before submission`() {
        val route = dotRoute()

        listOf(
            balanceReaderFor(route, observedAtMillis = NOW - 30_001),
            balanceReaderFor(route, transferBalance = BigInteger.ONE)
        ).forEach { reader ->
            val submitter = RecordingSubmitter()
            val executor = executor(route, submitter, balanceReader = reader)

            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { executor.quote(request(route)) }
            }
            assertEquals(0, submitter.submitCount)
        }
    }

    @Test
    fun `watch-only account can quote without accessing its private key`() {
        val route = dotRoute()
        val submitter = RecordingSubmitter()
        val request = request(route).copy(keypairProvider = WatchOnlyKeypairProvider)

        runBlocking { executor(route, submitter).quote(request) }
        assertEquals(1, submitter.estimateCount)
        assertEquals(0, submitter.submitCount)
    }

    // The route argument keeps fixture construction paired with the exercised route.
    @Suppress("UnusedParameter")
    private fun executor(
        route: ReviewedBridgeRoute,
        submitter: RecordingSubmitter,
        runtimeResolver: ReviewedBridgeRuntimeResolver = fixedRuntimeResolver(),
        balanceReader: ReviewedBridgeBalanceReader = ReviewedBridgeBalanceReader { origin, transfer, fee, account ->
            balance(origin, transfer, fee, account)
        },
        mutationsEnabled: () -> Boolean = { true }
    ) = ReviewedPolkaswapBridgeExecutor(
        runtimeResolver = runtimeResolver,
        balanceReader = balanceReader,
        submitter = submitter,
        mutationsEnabled = mutationsEnabled,
        nowMillis = { NOW }
    )

    private fun request(route: ReviewedBridgeRoute): ReviewedBridgeTransferRequest {
        val transfer = asset(route, isUtility = false)
        val utility = coreAsset(
            id = "utility",
            chainId = route.originChainId,
            symbol = "XOR",
            precision = 18,
            currencyId = "0x0200000000000000000000000000000000000000000000000000000000000000",
            isUtility = true
        )
        val origin = chain(route.originChainId, 69, listOf(transfer, utility))
        val destinationPrefix = if (route.destinationChainId == ReviewedPolkaswapBridgeCatalog.SORA_CHAIN_ID) 69 else 0
        val destination = chain(route.destinationChainId, destinationPrefix, emptyList())
        val account = ByteArray(32) { 1 }
        return ReviewedBridgeTransferRequest(
            providerId = route.providerId,
            routeId = route.routeId,
            originChain = origin,
            destinationChain = destination,
            asset = transfer,
            senderAccountId = account,
            recipientAddress = ByteArray(32) { 2 }.toAddress(destinationPrefix.toShort()),
            amountInPlanks = BigInteger("2000000000000000000"),
            keypairProvider = SignableKeypairProvider
        )
    }

    private fun asset(route: ReviewedBridgeRoute, isUtility: Boolean) = coreAsset(
        id = route.originAssetId,
        chainId = route.originChainId,
        symbol = route.symbol,
        precision = route.precision,
        currencyId = route.originCanonicalAssetId,
        isUtility = isUtility
    )

    private fun coreAsset(
        id: String,
        chainId: String,
        symbol: String,
        precision: Int,
        currencyId: String,
        isUtility: Boolean
    ) = Asset(
        id = id,
        name = symbol,
        symbol = symbol,
        iconUrl = "",
        chainId = chainId,
        chainName = chainId,
        chainIcon = null,
        isTestNet = false,
        priceId = null,
        precision = precision,
        staking = Asset.StakingType.UNSUPPORTED,
        purchaseProviders = null,
        supportStakingPool = false,
        isUtility = isUtility,
        type = null,
        currencyId = currencyId,
        existentialDeposit = null,
        color = null,
        isNative = null,
        priceProvider = null,
        coinbaseUrl = null
    )

    private fun chain(
        id: String,
        prefix: Int,
        assets: List<Asset>
    ) = Chain(
        id = id,
        paraId = null,
        rank = null,
        name = id,
        minSupportedVersion = null,
        assets = assets,
        nodes = emptyList(),
        explorers = emptyList(),
        externalApi = null,
        icon = "",
        addressPrefix = prefix,
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
        xcm = null
    )

    private fun fixedRuntimeResolver() = ReviewedBridgeRuntimeResolver { route -> runtime(route) }

    private fun runtime(route: ReviewedBridgeRoute, version: String = "1") = ReviewedBridgeRuntimeResolution(
        originRuntime = ReviewedBridgeRuntimeIdentity(
            genesisHash = "0x${route.originChainId}",
            specName = "origin-$version",
            specVersion = version.toInt(),
            transactionVersion = 1
        ),
        destinationRuntime = ReviewedBridgeRuntimeIdentity(
            genesisHash = "0x${route.destinationChainId}",
            specName = "destination-$version",
            specVersion = version.toInt(),
            transactionVersion = 1
        ),
        moduleName = route.execution.runtimeCall.pallet.replaceFirstChar { it.uppercase() },
        callName = route.execution.runtimeCall.call,
        rawArgumentNames = route.execution.runtimeCall.arguments.map(String::toTestSnakeCase),
        registrationFingerprint = "registration-$version",
        minimumFingerprint = "minimum-$version",
        runtimeMinimumInPlanks = BigInteger.ONE
    )

    private fun ReviewedBridgeRoute.query() = CrossChainRouteQuery(
        originNetworkId = originChainId,
        destinationNetworkId = destinationChainId,
        originAssetId = originAssetId
    )

    private fun dotRoute() = ReviewedPolkaswapBridgeCatalog.executableRoutes.single {
        it.destinationChainId == ReviewedPolkaswapBridgeCatalog.POLKADOT_CHAIN_ID && it.symbol == "DOT"
    }

    private fun externalRoute(symbol: String) = ReviewedPolkaswapBridgeCatalog.executableRoutes.single {
        it.destinationChainId == ReviewedPolkaswapBridgeCatalog.SORA_CHAIN_ID &&
            it.execution.kind == ReviewedBridgeExecutionKind.ExternalToSoraXcmV3 && it.symbol == symbol
    }

    @Suppress("UnusedParameter")
    private fun balanceReaderFor(
        route: ReviewedBridgeRoute,
        transferBalance: BigInteger = BigInteger("100000000000000000000"),
        observedAtMillis: Long = NOW
    ) = ReviewedBridgeBalanceReader { origin, transfer, fee, account ->
        ReviewedBridgeBalanceSnapshot(
            originChainId = origin.id,
            accountId = account,
            transferAssetId = transfer.id,
            transferAssetBalance = transferBalance,
            feeAssetId = fee.id,
            feeAssetBalance = BigInteger("100000000000000000000"),
            observedAtMillis = observedAtMillis
        )
    }

    private class RecordingSubmitter(
        private val fees: ArrayDeque<BigInteger> = ArrayDeque(listOf(BigInteger.TEN))
    ) : XcmExtrinsicSubmitter {
        var submitCount = 0
        var estimateCount = 0
        var submittedCall: XcmExtrinsicCall? = null

        override suspend fun submit(
            chain: Chain,
            accountId: ByteArray,
            keypairProvider: KeypairProvider,
            call: XcmExtrinsicCall
        ): String {
            submitCount += 1
            submittedCall = call
            return "0xbridge"
        }

        override suspend fun estimateFee(
            chain: Chain,
            accountId: ByteArray,
            keypairProvider: KeypairProvider,
            call: XcmExtrinsicCall
        ): BigInteger {
            estimateCount += 1
            return if (fees.size > 1) fees.removeFirst() else fees.first()
        }
    }

    private object SignableKeypairProvider : KeypairProvider {
        override suspend fun getCryptoTypeFor(chain: jp.co.soramitsu.core.models.IChain, accountId: ByteArray) =
            CryptoType.SR25519

        override suspend fun getKeypairFor(chain: jp.co.soramitsu.core.models.IChain, accountId: ByteArray) =
            BaseKeypair(ByteArray(64) { 4 }, ByteArray(32) { 3 })
    }

    private object WatchOnlyKeypairProvider : KeypairProvider {
        override suspend fun getCryptoTypeFor(chain: jp.co.soramitsu.core.models.IChain, accountId: ByteArray) =
            CryptoType.SR25519

        override suspend fun getKeypairFor(chain: jp.co.soramitsu.core.models.IChain, accountId: ByteArray): Keypair =
            error("Read-only quote must never access a private key")
    }

    private companion object {
        const val NOW = 1_000_000L

        fun balance(
            origin: Chain,
            transfer: Asset,
            fee: Asset,
            account: ByteArray
        ) = ReviewedBridgeBalanceSnapshot(
            originChainId = origin.id,
            accountId = account,
            transferAssetId = transfer.id,
            transferAssetBalance = BigInteger("100000000000000000000"),
            feeAssetId = fee.id,
            feeAssetBalance = BigInteger("100000000000000000000"),
            observedAtMillis = NOW
        )
    }
}

private fun String.toTestSnakeCase(): String = buildString(length + 4) {
    this@toTestSnakeCase.forEachIndexed { index, character ->
        if (character.isUpperCase()) {
            if (index != 0) append('_')
            append(character.lowercaseChar())
        } else {
            append(character)
        }
    }
}
