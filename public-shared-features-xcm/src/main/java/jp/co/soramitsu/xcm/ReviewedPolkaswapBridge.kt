package jp.co.soramitsu.xcm

import java.math.BigDecimal
import java.math.BigInteger
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b256
import jp.co.soramitsu.fearless_utils.runtime.metadata.moduleOrNull
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.xcm.domain.CrossChainAssetIdentity
import jp.co.soramitsu.xcm.domain.CrossChainFeeQuote
import jp.co.soramitsu.xcm.domain.CrossChainProtocol
import jp.co.soramitsu.xcm.domain.CrossChainProviderContext
import jp.co.soramitsu.xcm.domain.CrossChainRouteAvailability
import jp.co.soramitsu.xcm.domain.CrossChainRouteCapability
import jp.co.soramitsu.xcm.domain.CrossChainRouteProvider
import jp.co.soramitsu.xcm.domain.CrossChainRouteQuery

/** Exact Polkaswap bridge provider ids shared with the reviewed web route authority. */
object ReviewedPolkaswapBridgeProviderIds {
    const val SORA_SUBSTRATE = "sora-substrate-bridge"
    const val SORA_EVM = "sora-evm-bridge"
    const val LIBERLAND = "liberland-bridge"
}

enum class ReviewedBridgeNetwork {
    Polkadot,
    Kusama,
    Liberland
}

enum class ReviewedBridgeRecipientKind {
    Relay,
    Parachain,
    Liberland,
    Sora
}

enum class ReviewedBridgeAssetKind {
    Thischain,
    Sidechain
}

enum class ReviewedBridgeExecutionKind {
    SoraBridgeProxyBurnV3,
    LiberlandToSoraBurn,
    ExternalToSoraXcmV3
}

enum class ReviewedBridgeExternalSource {
    RelayNative,
    AcalaNative,
    AstarNative
}

data class ReviewedBridgeRuntimeCallSchema(
    /** Polkadot.js-style pallet name, for example `bridgeProxy`. */
    val pallet: String,
    val call: String,
    /** Polkadot.js-style metadata argument names. */
    val arguments: List<String>
) {
    init {
        require(pallet.isNotBlank()) { "Reviewed bridge pallet must not be blank" }
        require(call.isNotBlank()) { "Reviewed bridge call must not be blank" }
        require(arguments.isNotEmpty() && arguments.none(String::isBlank)) {
            "Reviewed bridge call arguments must be explicit"
        }
        require(arguments.distinct().size == arguments.size) {
            "Reviewed bridge call arguments must be unique"
        }
    }
}

sealed interface ReviewedBridgeExternalAsset {
    data object Lld : ReviewedBridgeExternalAsset
    data class Asset(val id: Long) : ReviewedBridgeExternalAsset {
        init {
            require(id >= 0) { "Reviewed bridge external asset id must not be negative" }
        }
    }
}

sealed interface ReviewedBridgeDestinationMinimum {
    val precision: Int

    data class BalancesExistentialDeposit(override val precision: Int) : ReviewedBridgeDestinationMinimum
    data class AssetsMinBalance(val assetId: Long, override val precision: Int) : ReviewedBridgeDestinationMinimum
    data class AcalaTokenMinimum(val token: String, override val precision: Int) : ReviewedBridgeDestinationMinimum
    data class SoraParachainAssetMinimum(
        val chainId: ChainId,
        val assetId: String,
        override val precision: Int
    ) : ReviewedBridgeDestinationMinimum
}

data class ReviewedBridgeExecution(
    val kind: ReviewedBridgeExecutionKind,
    val bridgeNetwork: ReviewedBridgeNetwork,
    val recipientKind: ReviewedBridgeRecipientKind,
    val destinationParaId: Int? = null,
    /** Bridge registration parachain id for an external origin such as Acala or Astar. */
    val bridgeParachainId: Int? = null,
    /** Exact reviewed source-call family for an external -> SORA transfer. */
    val externalSource: ReviewedBridgeExternalSource? = null,
    /** SORA parachain destination used by the reviewed external XCM call. */
    val soraParachainId: Int? = null,
    /** Live SORA parachain runtime used to prove the destination asset minimum. */
    val soraParachainChainId: ChainId? = null,
    /** Exact native-token variant used by xTokens.transfer. */
    val currencyToken: String? = null,
    val soraAssetId: String,
    val soraAssetKind: ReviewedBridgeAssetKind,
    val sidechainPrecision: Int,
    val externalAsset: ReviewedBridgeExternalAsset? = null,
    /** Reviewed downstream fee in transferred-asset units. */
    val destinationFee: String,
    val destinationMinimum: ReviewedBridgeDestinationMinimum? = null,
    val runtimeCall: ReviewedBridgeRuntimeCallSchema
) {
    init {
        require(SORA_ASSET_ID.matches(soraAssetId)) { "Reviewed bridge SORA asset id must be 32-byte hex" }
        require(sidechainPrecision >= 0) { "Reviewed bridge sidechain precision must not be negative" }
        require(destinationFee.toBigDecimalOrNull()?.signum() != -1) {
            "Reviewed bridge destination fee must be a non-negative decimal"
        }
        require((recipientKind == ReviewedBridgeRecipientKind.Parachain) == (destinationParaId != null)) {
            "Reviewed bridge parachain recipient must have exactly one destination para id"
        }
        require(
            kind != ReviewedBridgeExecutionKind.LiberlandToSoraBurn ||
                (bridgeNetwork == ReviewedBridgeNetwork.Liberland &&
                    recipientKind == ReviewedBridgeRecipientKind.Sora && externalAsset != null)
        ) { "Liberland to SORA execution authority is incomplete" }
        require(
            kind != ReviewedBridgeExecutionKind.ExternalToSoraXcmV3 ||
                (recipientKind == ReviewedBridgeRecipientKind.Sora && externalSource != null &&
                    soraParachainId != null && soraParachainChainId != null &&
                    (externalSource == ReviewedBridgeExternalSource.AcalaNative) == (currencyToken != null))
        ) { "External to SORA execution authority is incomplete" }
    }

    private companion object {
        val SORA_ASSET_ID = Regex("^0x[0-9a-fA-F]{64}$")
    }
}

data class ReviewedBridgeRoute(
    val routeId: String,
    val providerId: String,
    val originChainId: ChainId,
    val destinationChainId: ChainId,
    /** Registry/balance id. */
    val originAssetId: String,
    /** Canonical AssetKey id (`currencyId` when present). */
    val originCanonicalAssetId: String,
    val originXcmAssetId: String,
    val destinationXcmAssetId: String,
    val symbol: String,
    val precision: Int,
    /** Reviewed minimum in transferred-asset units. */
    val minimumAmount: String?,
    val execution: ReviewedBridgeExecution,
    val estimatedTime: String
) {
    init {
        require(routeId == reviewedRouteId(providerId, originChainId, destinationChainId, originAssetId)) {
            "Reviewed bridge route id is not canonical"
        }
        require(originChainId.isNotBlank() && destinationChainId.isNotBlank() && originChainId != destinationChainId) {
            "Reviewed bridge route chains are invalid"
        }
        require(originAssetId.isNotBlank() && originCanonicalAssetId.isNotBlank()) {
            "Reviewed bridge route asset ids must not be blank"
        }
        require(originXcmAssetId.isNotBlank() && destinationXcmAssetId.isNotBlank()) {
            "Reviewed bridge XCM ids must not be blank"
        }
        require(symbol.isNotBlank() && symbol == symbol.uppercase()) {
            "Reviewed bridge symbol must be normalized"
        }
        require(precision >= 0) { "Reviewed bridge precision must not be negative" }
        require(minimumAmount == null || minimumAmount.toBigDecimalOrNull()?.signum() != -1) {
            "Reviewed bridge minimum must be a non-negative decimal"
        }
        require(estimatedTime.isNotBlank()) { "Reviewed bridge delivery estimate must not be blank" }
    }

    val minimumInPlanks: BigInteger?
        get() = minimumAmount?.toReviewedPlanks(precision)

    val destinationFeeInPlanks: BigInteger
        get() = execution.destinationFee.toReviewedPlanks(precision)
}

data class UnavailableReviewedBridgeRoute(
    val routeId: String,
    val providerId: String,
    val originChainId: ChainId,
    val destinationChainId: ChainId,
    val originAssetId: String,
    val originCanonicalAssetId: String,
    val originXcmAssetId: String,
    val destinationXcmAssetId: String,
    val symbol: String,
    val precision: Int,
    val minimumAmount: String?,
    val reason: String,
    val estimatedTime: String
) {
    init {
        require(
            providerId.isNotBlank() &&
                routeId == unavailableReviewedRouteId(
                    providerId,
                    originChainId,
                    destinationChainId,
                    originAssetId
                )
        ) { "Unavailable reviewed bridge route id is not canonical" }
        require(originChainId.isNotBlank() && destinationChainId.isNotBlank()) {
            "Unavailable reviewed bridge route chains are required"
        }
        require(originAssetId.isNotBlank() && originCanonicalAssetId.isNotBlank()) {
            "Unavailable reviewed bridge asset identity is required"
        }
        require(originXcmAssetId.isNotBlank() && destinationXcmAssetId.isNotBlank()) {
            "Unavailable reviewed bridge XCM identity is required"
        }
        require(symbol.isNotBlank() && precision >= 0) { "Unavailable reviewed bridge asset metadata is invalid" }
        require(minimumAmount == null || minimumAmount.toBigDecimalOrNull()?.signum() != -1) {
            "Unavailable reviewed bridge minimum must be a non-negative decimal"
        }
        require(reason.isNotBlank() && estimatedTime.isNotBlank()) {
            "Unavailable reviewed bridge route must explain its state"
        }
    }
}

fun reviewedRouteId(
    providerId: String,
    originChainId: ChainId,
    destinationChainId: ChainId,
    originAssetId: String
): String = listOf(
    "reviewed-v1",
    providerId,
    originChainId,
    destinationChainId,
    URLEncoder.encode(originAssetId, StandardCharsets.UTF_8.name()).replace("+", "%20")
).joinToString(":")

fun unavailableReviewedRouteId(
    providerId: String,
    originChainId: ChainId,
    destinationChainId: ChainId,
    originAssetId: String
): String = listOf(
    "unavailable-v1",
    providerId,
    originChainId,
    destinationChainId,
    URLEncoder.encode(originAssetId, StandardCharsets.UTF_8.name()).replace("+", "%20")
).joinToString(":")

/** APK-owned authority ported from web `reviewedRoutes.ts` and `routeRegistry.ts`. */
object ReviewedPolkaswapBridgeCatalog {
    const val SORA_CHAIN_ID = "7e4e32d0feafd4f9c9414b0be86373f9a1efa904809b683453a9af6856d38ad5"
    const val POLKADOT_CHAIN_ID = "91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3"
    const val KUSAMA_CHAIN_ID = "b0a8d493285c2df73290dfb7e61f870f17b41801197a149ca93654499ea3dafe"
    const val ACALA_CHAIN_ID = "fc41b9bd8ef8fe53d58c7ea67c794c7ec9a73daf05e6d54b14ff6342c99ba64c"
    const val ASTAR_CHAIN_ID = "9eb76c5184c4ab8679d2d5d819fdf90b9c001403e9e17da2e14b6d8aec4029c6"
    const val LIBERLAND_CHAIN_ID = "6bd89e052d67a45bb60a9a23e8581053d5e0d619f15cb9865946937e690c42d6"
    const val ETHEREUM_CHAIN_ID = "1"

    private const val SUBSTRATE_ESTIMATE = "5–20 minutes"
    private const val EVM_ESTIMATE = "10–60 minutes plus claim"
    private val BURN = ReviewedBridgeRuntimeCallSchema(
        pallet = "bridgeProxy",
        call = "burn",
        arguments = listOf("networkId", "assetId", "recipient", "amount")
    )
    private val LIBERLAND_BURN = ReviewedBridgeRuntimeCallSchema(
        pallet = "soraBridgeApp",
        call = "burn",
        arguments = listOf("networkId", "assetId", "recipient", "amount")
    )
    private val RELAY_RESERVE_TRANSFER = ReviewedBridgeRuntimeCallSchema(
        pallet = "xcmPallet",
        call = "reserveTransferAssets",
        arguments = listOf("dest", "beneficiary", "assets", "feeAssetItem")
    )
    private val ACALA_TRANSFER = ReviewedBridgeRuntimeCallSchema(
        pallet = "xTokens",
        call = "transfer",
        arguments = listOf("currencyId", "amount", "dest", "destWeightLimit")
    )
    private val ASTAR_RESERVE_TRANSFER = ReviewedBridgeRuntimeCallSchema(
        pallet = "polkadotXcm",
        call = "reserveTransferAssets",
        arguments = listOf("dest", "beneficiary", "assets", "feeAssetItem")
    )

    private fun route(
        providerId: String,
        destinationChainId: String,
        originAssetId: String,
        canonicalAssetId: String,
        originXcmAssetId: String,
        destinationXcmAssetId: String,
        symbol: String,
        minimum: String?,
        execution: ReviewedBridgeExecution
    ) = ReviewedBridgeRoute(
        routeId = reviewedRouteId(providerId, SORA_CHAIN_ID, destinationChainId, originAssetId),
        providerId = providerId,
        originChainId = SORA_CHAIN_ID,
        destinationChainId = destinationChainId,
        originAssetId = originAssetId,
        originCanonicalAssetId = canonicalAssetId,
        originXcmAssetId = originXcmAssetId,
        destinationXcmAssetId = destinationXcmAssetId,
        symbol = symbol,
        precision = 18,
        minimumAmount = minimum,
        execution = execution,
        estimatedTime = SUBSTRATE_ESTIMATE
    )

    private fun soraBurn(
        providerId: String,
        destinationChainId: String,
        originAssetId: String,
        canonicalAssetId: String,
        originXcmAssetId: String,
        destinationXcmAssetId: String,
        symbol: String,
        minimum: String?,
        network: ReviewedBridgeNetwork,
        recipientKind: ReviewedBridgeRecipientKind,
        destinationParaId: Int? = null,
        soraAssetId: String,
        assetKind: ReviewedBridgeAssetKind,
        sidechainPrecision: Int,
        externalAsset: ReviewedBridgeExternalAsset? = null,
        destinationFee: String,
        destinationMinimum: ReviewedBridgeDestinationMinimum
    ) = route(
        providerId = providerId,
        destinationChainId = destinationChainId,
        originAssetId = originAssetId,
        canonicalAssetId = canonicalAssetId,
        originXcmAssetId = originXcmAssetId,
        destinationXcmAssetId = destinationXcmAssetId,
        symbol = symbol,
        minimum = minimum,
        execution = ReviewedBridgeExecution(
            kind = ReviewedBridgeExecutionKind.SoraBridgeProxyBurnV3,
            bridgeNetwork = network,
            recipientKind = recipientKind,
            destinationParaId = destinationParaId,
            soraAssetId = soraAssetId,
            soraAssetKind = assetKind,
            sidechainPrecision = sidechainPrecision,
            externalAsset = externalAsset,
            destinationFee = destinationFee,
            destinationMinimum = destinationMinimum,
            runtimeCall = BURN
        )
    )

    private fun externalToSora(
        originChainId: String,
        originAssetId: String,
        canonicalAssetId: String,
        xcmAssetId: String,
        symbol: String,
        precision: Int,
        minimum: String,
        network: ReviewedBridgeNetwork,
        source: ReviewedBridgeExternalSource,
        soraParachainId: Int,
        soraParachainChainId: String,
        bridgeParachainId: Int? = null,
        soraAssetId: String,
        currencyToken: String? = null,
        runtimeCall: ReviewedBridgeRuntimeCallSchema
    ) = ReviewedBridgeRoute(
        routeId = reviewedRouteId(
            ReviewedPolkaswapBridgeProviderIds.SORA_SUBSTRATE,
            originChainId,
            SORA_CHAIN_ID,
            originAssetId
        ),
        providerId = ReviewedPolkaswapBridgeProviderIds.SORA_SUBSTRATE,
        originChainId = originChainId,
        destinationChainId = SORA_CHAIN_ID,
        originAssetId = originAssetId,
        originCanonicalAssetId = canonicalAssetId,
        originXcmAssetId = xcmAssetId,
        destinationXcmAssetId = xcmAssetId,
        symbol = symbol,
        precision = precision,
        minimumAmount = minimum,
        execution = ReviewedBridgeExecution(
            kind = ReviewedBridgeExecutionKind.ExternalToSoraXcmV3,
            bridgeNetwork = network,
            recipientKind = ReviewedBridgeRecipientKind.Sora,
            bridgeParachainId = bridgeParachainId,
            externalSource = source,
            soraParachainId = soraParachainId,
            soraParachainChainId = soraParachainChainId,
            currencyToken = currencyToken,
            soraAssetId = soraAssetId,
            soraAssetKind = ReviewedBridgeAssetKind.Sidechain,
            sidechainPrecision = precision,
            destinationFee = "0",
            destinationMinimum = ReviewedBridgeDestinationMinimum.SoraParachainAssetMinimum(
                chainId = soraParachainChainId,
                assetId = soraAssetId,
                precision = precision
            ),
            runtimeCall = runtimeCall
        ),
        estimatedTime = SUBSTRATE_ESTIMATE
    )

    val executableRoutes: List<ReviewedBridgeRoute> = listOf(
        soraBurn(
            providerId = ReviewedPolkaswapBridgeProviderIds.SORA_SUBSTRATE,
            destinationChainId = KUSAMA_CHAIN_ID,
            originAssetId = "5416b261-a759-4ba6-bc83-ea79a83c5101",
            canonicalAssetId = "0x00117b0fa73c4672e03a7d9d774e3b3f91beb893e93d9a8d0430295f44225db8",
            originXcmAssetId = "5416b261-a759-4ba6-bc83-ea79a83c5101",
            destinationXcmAssetId = "5416b261-a759-4ba6-bc83-ea79a83c5101",
            symbol = "KSM",
            minimum = null,
            network = ReviewedBridgeNetwork.Kusama,
            recipientKind = ReviewedBridgeRecipientKind.Relay,
            soraAssetId = "0x00117b0fa73c4672e03a7d9d774e3b3f91beb893e93d9a8d0430295f44225db8",
            assetKind = ReviewedBridgeAssetKind.Sidechain,
            sidechainPrecision = 12,
            destinationFee = "0.0000900058",
            destinationMinimum = ReviewedBridgeDestinationMinimum.BalancesExistentialDeposit(12)
        ),
        soraBurn(
            providerId = ReviewedPolkaswapBridgeProviderIds.SORA_SUBSTRATE,
            destinationChainId = POLKADOT_CHAIN_ID,
            originAssetId = "cd092a5a-4eb6-4318-9f11-4bf8454d67a2",
            canonicalAssetId = "0x0003b1dbee890acfb1b3bc12d1bb3b4295f52755423f84d1751b2545cebf000b",
            originXcmAssetId = "cd092a5a-4eb6-4318-9f11-4bf8454d67a2",
            destinationXcmAssetId = "99e66d4f-00cd-4d73-bd1b-3adadcacffb2",
            symbol = "DOT",
            minimum = "1.1",
            network = ReviewedBridgeNetwork.Polkadot,
            recipientKind = ReviewedBridgeRecipientKind.Relay,
            soraAssetId = "0x0003b1dbee890acfb1b3bc12d1bb3b4295f52755423f84d1751b2545cebf000b",
            assetKind = ReviewedBridgeAssetKind.Sidechain,
            sidechainPrecision = 10,
            destinationFee = "0.0364421524",
            destinationMinimum = ReviewedBridgeDestinationMinimum.BalancesExistentialDeposit(10)
        ),
        soraBurn(
            providerId = ReviewedPolkaswapBridgeProviderIds.SORA_SUBSTRATE,
            destinationChainId = ACALA_CHAIN_ID,
            originAssetId = "fb88fa55-b8c8-4ff1-afa8-f72a86a238a4",
            canonicalAssetId = "0x001ddbe1a880031da72f7ea421260bec635fa7d1aa72593d5412795408b6b2ba",
            originXcmAssetId = "fb88fa55-b8c8-4ff1-afa8-f72a86a238a4",
            destinationXcmAssetId = "fb88fa55-b8c8-4ff1-afa8-f72a86a238a4",
            symbol = "ACA",
            minimum = "1.1",
            network = ReviewedBridgeNetwork.Polkadot,
            recipientKind = ReviewedBridgeRecipientKind.Parachain,
            destinationParaId = 2000,
            soraAssetId = "0x001ddbe1a880031da72f7ea421260bec635fa7d1aa72593d5412795408b6b2ba",
            assetKind = ReviewedBridgeAssetKind.Sidechain,
            sidechainPrecision = 12,
            destinationFee = "0.0064296",
            destinationMinimum = ReviewedBridgeDestinationMinimum.AcalaTokenMinimum("ACA", 12)
        ),
        soraBurn(
            providerId = ReviewedPolkaswapBridgeProviderIds.SORA_SUBSTRATE,
            destinationChainId = ASTAR_CHAIN_ID,
            originAssetId = "acc32ee0-8fdc-4743-91e1-f70cc4f3069b",
            canonicalAssetId = "0x009dd037fcb32f4fe17c513abd4641a2ece844d106e30788124f0c0acc6e748e",
            originXcmAssetId = "acc32ee0-8fdc-4743-91e1-f70cc4f3069b",
            destinationXcmAssetId = "acc32ee0-8fdc-4743-91e1-f70cc4f3069b",
            symbol = "ASTR",
            minimum = null,
            network = ReviewedBridgeNetwork.Polkadot,
            recipientKind = ReviewedBridgeRecipientKind.Parachain,
            destinationParaId = 2006,
            soraAssetId = "0x009dd037fcb32f4fe17c513abd4641a2ece844d106e30788124f0c0acc6e748e",
            assetKind = ReviewedBridgeAssetKind.Sidechain,
            sidechainPrecision = 18,
            destinationFee = "0.150481591364315913",
            destinationMinimum = ReviewedBridgeDestinationMinimum.BalancesExistentialDeposit(18)
        ),
        externalToSora(
            originChainId = POLKADOT_CHAIN_ID,
            originAssetId = "887a17c7-1370-4de0-97dd-5422e294fa75",
            canonicalAssetId = "887a17c7-1370-4de0-97dd-5422e294fa75",
            xcmAssetId = "99e66d4f-00cd-4d73-bd1b-3adadcacffb2",
            symbol = "DOT",
            precision = 10,
            minimum = "1.1",
            network = ReviewedBridgeNetwork.Polkadot,
            source = ReviewedBridgeExternalSource.RelayNative,
            soraParachainId = 2025,
            soraParachainChainId = "e92d165ad41e41e215d09713788173aecfdbe34d3bed29409d33a2ef03980738",
            soraAssetId = "0x0003b1dbee890acfb1b3bc12d1bb3b4295f52755423f84d1751b2545cebf000b",
            runtimeCall = RELAY_RESERVE_TRANSFER
        ),
        externalToSora(
            originChainId = KUSAMA_CHAIN_ID,
            originAssetId = "1e0c2ec6-935f-49bd-a854-5e12ee6c9f1b",
            canonicalAssetId = "1e0c2ec6-935f-49bd-a854-5e12ee6c9f1b",
            xcmAssetId = "0ceffe96-8090-404e-815c-91118ee5dd65",
            symbol = "KSM",
            precision = 12,
            minimum = "0.05",
            network = ReviewedBridgeNetwork.Kusama,
            source = ReviewedBridgeExternalSource.RelayNative,
            soraParachainId = 2011,
            soraParachainChainId = "6d8d9f145c2177fa83512492cdd80a71e29f22473f4a8943a6292149ac319fb9",
            soraAssetId = "0x00117b0fa73c4672e03a7d9d774e3b3f91beb893e93d9a8d0430295f44225db8",
            runtimeCall = RELAY_RESERVE_TRANSFER
        ),
        externalToSora(
            originChainId = ACALA_CHAIN_ID,
            originAssetId = "c801d6c1-3edf-41a9-9aea-da705eab249b",
            canonicalAssetId = "c801d6c1-3edf-41a9-9aea-da705eab249b",
            xcmAssetId = "7528bb5b-2aa9-4a70-a4ed-3476aec0f87d",
            symbol = "ACA",
            precision = 12,
            minimum = "56",
            network = ReviewedBridgeNetwork.Polkadot,
            source = ReviewedBridgeExternalSource.AcalaNative,
            soraParachainId = 2025,
            soraParachainChainId = "e92d165ad41e41e215d09713788173aecfdbe34d3bed29409d33a2ef03980738",
            bridgeParachainId = 2000,
            soraAssetId = "0x001ddbe1a880031da72f7ea421260bec635fa7d1aa72593d5412795408b6b2ba",
            currencyToken = "ACA",
            runtimeCall = ACALA_TRANSFER
        ),
        externalToSora(
            originChainId = ASTAR_CHAIN_ID,
            originAssetId = "5ab1e8d-81ed-4130-9d29-55b549cc6bab",
            canonicalAssetId = "5ab1e8d-81ed-4130-9d29-55b549cc6bab",
            xcmAssetId = "5ab1e8d-81ed-4130-9d29-55b549cc6bab",
            symbol = "ASTR",
            precision = 18,
            minimum = "73",
            network = ReviewedBridgeNetwork.Polkadot,
            source = ReviewedBridgeExternalSource.AstarNative,
            soraParachainId = 2025,
            soraParachainChainId = "e92d165ad41e41e215d09713788173aecfdbe34d3bed29409d33a2ef03980738",
            bridgeParachainId = 2006,
            soraAssetId = "0x009dd037fcb32f4fe17c513abd4641a2ece844d106e30788124f0c0acc6e748e",
            runtimeCall = ASTAR_RESERVE_TRANSFER
        ),
        soraBurn(
            providerId = ReviewedPolkaswapBridgeProviderIds.LIBERLAND,
            destinationChainId = LIBERLAND_CHAIN_ID,
            originAssetId = "1c3b4fcb-5a5f-4319-9dce-d178006eb9bf",
            canonicalAssetId = "0x00513be65493a7fc3e2128d4230061a530acf40478a4affa20bbba27a310673e",
            originXcmAssetId = "a6b83d39-a488-4b34-8352-280705a792ea",
            destinationXcmAssetId = "a6b83d39-a488-4b34-8352-280705a792ea",
            symbol = "LLD",
            minimum = "1.1",
            network = ReviewedBridgeNetwork.Liberland,
            recipientKind = ReviewedBridgeRecipientKind.Liberland,
            soraAssetId = "0x00513be65493a7fc3e2128d4230061a530acf40478a4affa20bbba27a310673e",
            assetKind = ReviewedBridgeAssetKind.Sidechain,
            sidechainPrecision = 12,
            externalAsset = ReviewedBridgeExternalAsset.Lld,
            destinationFee = "0",
            destinationMinimum = ReviewedBridgeDestinationMinimum.BalancesExistentialDeposit(12)
        ),
        soraBurn(
            providerId = ReviewedPolkaswapBridgeProviderIds.LIBERLAND,
            destinationChainId = LIBERLAND_CHAIN_ID,
            originAssetId = "b774c386-5cce-454a-a845-1ec0381538ec",
            canonicalAssetId = "0x0200000000000000000000000000000000000000000000000000000000000000",
            originXcmAssetId = "b774c386-5cce-454a-a845-1ec0381538ec",
            destinationXcmAssetId = "b774c386-5cce-454a-a845-1ec0381538ec",
            symbol = "XOR",
            minimum = null,
            network = ReviewedBridgeNetwork.Liberland,
            recipientKind = ReviewedBridgeRecipientKind.Liberland,
            soraAssetId = "0x0200000000000000000000000000000000000000000000000000000000000000",
            assetKind = ReviewedBridgeAssetKind.Thischain,
            sidechainPrecision = 18,
            externalAsset = ReviewedBridgeExternalAsset.Asset(774441749),
            destinationFee = "0",
            destinationMinimum = ReviewedBridgeDestinationMinimum.AssetsMinBalance(774441749, 18)
        ),
        soraBurn(
            providerId = ReviewedPolkaswapBridgeProviderIds.LIBERLAND,
            destinationChainId = LIBERLAND_CHAIN_ID,
            originAssetId = "0ef3afdc-cdd3-47cc-bd52-817c54ae65b5",
            canonicalAssetId = "0x00073edd278e1bd6a7f9d0b27d4f3e93b73c8f0832b58a4df13c69611a99f156",
            originXcmAssetId = "0ef3afdc-cdd3-47cc-bd52-817c54ae65b5",
            destinationXcmAssetId = "0ef3afdc-cdd3-47cc-bd52-817c54ae65b5",
            symbol = "LLM",
            minimum = null,
            network = ReviewedBridgeNetwork.Liberland,
            recipientKind = ReviewedBridgeRecipientKind.Liberland,
            soraAssetId = "0x00073edd278e1bd6a7f9d0b27d4f3e93b73c8f0832b58a4df13c69611a99f156",
            assetKind = ReviewedBridgeAssetKind.Sidechain,
            sidechainPrecision = 12,
            externalAsset = ReviewedBridgeExternalAsset.Asset(1),
            destinationFee = "0",
            destinationMinimum = ReviewedBridgeDestinationMinimum.AssetsMinBalance(1, 12)
        ),
        liberlandToSora(
            originAssetId = "a6b83d39-a488-4b34-8352-280705a792ea",
            canonicalAssetId = "a6b83d39-a488-4b34-8352-280705a792ea",
            originXcmAssetId = "a6b83d39-a488-4b34-8352-280705a792e",
            destinationXcmAssetId = "a6b83d39-a488-4b34-8352-280705a792e",
            symbol = "LLD",
            precision = 12,
            minimum = "1.1",
            externalAsset = ReviewedBridgeExternalAsset.Lld,
            soraAssetId = "0x00513be65493a7fc3e2128d4230061a530acf40478a4affa20bbba27a310673e",
            assetKind = ReviewedBridgeAssetKind.Sidechain
        ),
        liberlandToSora(
            originAssetId = "30b43eb9-36b4-4b40-bf72-330d2e20ee86",
            canonicalAssetId = "1",
            originXcmAssetId = "30b43eb9-36b4-4b40-bf72-330d2e20ee86",
            destinationXcmAssetId = "30b43eb9-36b4-4b40-bf72-330d2e20ee86",
            symbol = "LLM",
            precision = 12,
            minimum = null,
            externalAsset = ReviewedBridgeExternalAsset.Asset(1),
            soraAssetId = "0x00073edd278e1bd6a7f9d0b27d4f3e93b73c8f0832b58a4df13c69611a99f156",
            assetKind = ReviewedBridgeAssetKind.Sidechain
        ),
        liberlandToSora(
            originAssetId = "2e7179c9-4308-420e-a654-43c92d119717",
            canonicalAssetId = "774441749",
            originXcmAssetId = "2e7179c9-4308-420e-a654-43c92d119717",
            destinationXcmAssetId = "2e7179c9-4308-420e-a654-43c92d119717",
            symbol = "XOR",
            precision = 18,
            minimum = null,
            externalAsset = ReviewedBridgeExternalAsset.Asset(774441749),
            soraAssetId = "0x0200000000000000000000000000000000000000000000000000000000000000",
            assetKind = ReviewedBridgeAssetKind.Thischain
        )
    )

    private fun liberlandToSora(
        originAssetId: String,
        canonicalAssetId: String,
        originXcmAssetId: String,
        destinationXcmAssetId: String,
        symbol: String,
        precision: Int,
        minimum: String?,
        externalAsset: ReviewedBridgeExternalAsset,
        soraAssetId: String,
        assetKind: ReviewedBridgeAssetKind
    ) = ReviewedBridgeRoute(
        routeId = reviewedRouteId(
            ReviewedPolkaswapBridgeProviderIds.LIBERLAND,
            LIBERLAND_CHAIN_ID,
            SORA_CHAIN_ID,
            originAssetId
        ),
        providerId = ReviewedPolkaswapBridgeProviderIds.LIBERLAND,
        originChainId = LIBERLAND_CHAIN_ID,
        destinationChainId = SORA_CHAIN_ID,
        originAssetId = originAssetId,
        originCanonicalAssetId = canonicalAssetId,
        originXcmAssetId = originXcmAssetId,
        destinationXcmAssetId = destinationXcmAssetId,
        symbol = symbol,
        precision = precision,
        minimumAmount = minimum,
        execution = ReviewedBridgeExecution(
            kind = ReviewedBridgeExecutionKind.LiberlandToSoraBurn,
            bridgeNetwork = ReviewedBridgeNetwork.Liberland,
            recipientKind = ReviewedBridgeRecipientKind.Sora,
            soraAssetId = soraAssetId,
            soraAssetKind = assetKind,
            sidechainPrecision = precision,
            externalAsset = externalAsset,
            destinationFee = "0",
            runtimeCall = LIBERLAND_BURN
        ),
        estimatedTime = SUBSTRATE_ESTIMATE
    )

    val unavailableRoutes: List<UnavailableReviewedBridgeRoute> = listOf(
        UnavailableReviewedBridgeRoute(
            routeId = unavailableReviewedRouteId(
                ReviewedPolkaswapBridgeProviderIds.SORA_EVM,
                SORA_CHAIN_ID,
                ETHEREUM_CHAIN_ID,
                "82f45df3-b6d8-43e7-a440-c0e73ab59785"
            ),
            providerId = ReviewedPolkaswapBridgeProviderIds.SORA_EVM,
            originChainId = SORA_CHAIN_ID,
            destinationChainId = ETHEREUM_CHAIN_ID,
            originAssetId = "82f45df3-b6d8-43e7-a440-c0e73ab59785",
            originCanonicalAssetId = "0x0200070000000000000000000000000000000000000000000000000000000000",
            originXcmAssetId = "82f45df3-b6d8-43e7-a440-c0e73ab59785",
            destinationXcmAssetId = "c2a6c062-d511-4bde-9ce6-ea775d2a302c",
            symbol = "ETH",
            precision = 18,
            minimumAmount = null,
            reason = "Ethereum delivery requires a second claim transaction and recovery tracking. This route stays unavailable until that full flow is reviewed.",
            estimatedTime = EVM_ESTIMATE
        ),
        UnavailableReviewedBridgeRoute(
            routeId = unavailableReviewedRouteId(
                ReviewedPolkaswapBridgeProviderIds.SORA_EVM,
                ETHEREUM_CHAIN_ID,
                SORA_CHAIN_ID,
                "c2a6c062-d511-4bde-9ce6-ea775d2a302c"
            ),
            providerId = ReviewedPolkaswapBridgeProviderIds.SORA_EVM,
            originChainId = ETHEREUM_CHAIN_ID,
            destinationChainId = SORA_CHAIN_ID,
            originAssetId = "c2a6c062-d511-4bde-9ce6-ea775d2a302c",
            originCanonicalAssetId = "c2a6c062-d511-4bde-9ce6-ea775d2a302c",
            originXcmAssetId = "c2a6c062-d511-4bde-9ce6-ea775d2a302c",
            destinationXcmAssetId = "82f45df3-b6d8-43e7-a440-c0e73ab59785",
            symbol = "ETH",
            precision = 18,
            minimumAmount = null,
            reason = "Ethereum → SORA requires a contract transaction, live gas and allowance checks, and recovery tracking. This route stays unavailable until that full flow is reviewed.",
            estimatedTime = EVM_ESTIMATE
        )
    )

    private val byRouteId = executableRoutes.associateBy(ReviewedBridgeRoute::routeId)

    init {
        require(byRouteId.size == executableRoutes.size) { "Reviewed executable bridge route ids must be unique" }
        require(unavailableRoutes.map { it.routeId }.distinct().size == unavailableRoutes.size) {
            "Unavailable reviewed bridge route ids must be unique"
        }
        require(byRouteId.keys.intersect(unavailableRoutes.mapTo(mutableSetOf()) { it.routeId }).isEmpty()) {
            "Reviewed bridge routes cannot be both executable and unavailable"
        }
    }

    fun findExecutable(routeId: String): ReviewedBridgeRoute? = byRouteId[routeId]

    fun executableForProvider(providerId: String): List<ReviewedBridgeRoute> =
        executableRoutes.filter { it.providerId == providerId }

    fun unavailableForProvider(providerId: String): List<UnavailableReviewedBridgeRoute> =
        unavailableRoutes.filter { it.providerId == providerId }
}

data class ReviewedBridgeRuntimeIdentity(
    val genesisHash: String,
    val specName: String,
    val specVersion: Int,
    val transactionVersion: Int
) {
    init {
        require(GENESIS_HASH.matches(genesisHash)) { "Reviewed bridge runtime genesis hash is invalid" }
        require(specName.isNotBlank() && specVersion >= 0 && transactionVersion >= 0) {
            "Reviewed bridge live runtime identity is invalid"
        }
    }

    private companion object {
        val GENESIS_HASH = Regex("^0x[0-9a-fA-F]{64}$")
    }
}

data class ReviewedBridgeRuntimeResolution(
    val originRuntime: ReviewedBridgeRuntimeIdentity,
    val destinationRuntime: ReviewedBridgeRuntimeIdentity,
    val moduleName: String,
    val callName: String,
    val rawArgumentNames: List<String>,
    /** Fingerprint of exact live bridge registration storage values. */
    val registrationFingerprint: String,
    /** Fingerprint of the exact typed constant/storage path and decoded minimum. */
    val minimumFingerprint: String,
    /** Fresh destination minimum normalized into origin-asset planks. */
    val runtimeMinimumInPlanks: BigInteger = BigInteger.ZERO
) {
    init {
        require(moduleName.isNotBlank() && callName.isNotBlank()) {
            "Reviewed bridge resolved call is required"
        }
        require(rawArgumentNames.isNotEmpty() && rawArgumentNames.none(String::isBlank)) {
            "Reviewed bridge resolved arguments are required"
        }
        require(registrationFingerprint.isNotBlank()) { "Reviewed bridge registration proof is required" }
        require(minimumFingerprint.isNotBlank()) { "Reviewed bridge minimum proof is required" }
        require(runtimeMinimumInPlanks.signum() >= 0) { "Reviewed bridge runtime minimum must not be negative" }
    }
}

data class ReviewedBridgeRuntimeMinimum(
    val amountInOriginPlanks: BigInteger,
    val proofFingerprint: String
) {
    init {
        require(amountInOriginPlanks.signum() >= 0 && proofFingerprint.isNotBlank()) {
            "Reviewed bridge runtime minimum proof is invalid"
        }
    }
}

fun interface ReviewedBridgeRuntimeIdentityResolver {
    /** Must query the connected endpoint; configured chain ids are not an identity proof. */
    suspend fun resolve(chainId: ChainId): ReviewedBridgeRuntimeIdentity
}

fun interface ReviewedBridgeRegistrationAuthority {
    /** Verifies exact live bridge storage registration and returns a fingerprint of the proof. */
    suspend fun verify(route: ReviewedBridgeRoute): String
}

fun interface ReviewedBridgeRuntimeResolver {
    /** Resolves current runtime authority; implementations must fail closed on registration drift. */
    suspend fun resolve(route: ReviewedBridgeRoute): ReviewedBridgeRuntimeResolution
}

fun reviewedBridgeRuntimeFingerprint(
    route: ReviewedBridgeRoute,
    runtime: ReviewedBridgeRuntimeResolution
): String {
    val authority = listOf(
        route.routeId,
        route.providerId,
        route.originChainId,
        route.destinationChainId,
        route.originAssetId,
        route.originCanonicalAssetId,
        route.originXcmAssetId,
        route.destinationXcmAssetId,
        route.symbol,
        route.precision.toString(),
        route.minimumAmount.orEmpty(),
        route.execution.toString(),
        runtime.originRuntime.toString(),
        runtime.destinationRuntime.toString(),
        runtime.moduleName,
        runtime.callName,
        runtime.rawArgumentNames.joinToString(","),
        runtime.registrationFingerprint,
        runtime.minimumFingerprint,
        runtime.runtimeMinimumInPlanks.toString()
    ).joinToString("\u001f")
    return authority.toByteArray().blake2b256().toHexString(withPrefix = true)
}

/** Discovery adapter for the immutable reviewed bridge catalog. It never authorizes submission. */
class ReviewedPolkaswapBridgeRouteProvider(
    override val providerId: String,
    override val protocol: CrossChainProtocol,
    private val runtimeResolver: ReviewedBridgeRuntimeResolver,
    private val actionsEnabled: () -> Boolean,
    private val actionsDisabledReason: () -> String
) : CrossChainRouteProvider {
    private val executableRoutes = ReviewedPolkaswapBridgeCatalog.executableForProvider(providerId)
    private val unavailableRoutes = ReviewedPolkaswapBridgeCatalog.unavailableForProvider(providerId)

    init {
        require(executableRoutes.isNotEmpty() || unavailableRoutes.isNotEmpty()) {
            "Reviewed bridge provider has no catalog entries: $providerId"
        }
        require(
            providerId != ReviewedPolkaswapBridgeProviderIds.SORA_EVM || executableRoutes.isEmpty()
        ) { "SORA/EVM must remain unavailable until its full multi-step flow is reviewed" }
    }

    override suspend fun capability(query: CrossChainRouteQuery): CrossChainRouteCapability {
        val hasAssetHint = query.assetSymbol != null || query.originAssetId != null
        if (hasAssetHint && (query.originNetworkId == null || query.originAssetId.isNullOrBlank())) {
            return unavailableCapability(
                reason = "Select a network-scoped asset with an exact asset id.",
                availability = CrossChainRouteAvailability.SetupRequired
            )
        }

        val matchingExecutable = executableRoutes.filter { it.matches(query) }
        val matchingUnavailable = unavailableRoutes.filter { it.matches(query) }
        val exactSelection = query.originNetworkId != null &&
            query.destinationNetworkId != null && !query.originAssetId.isNullOrBlank()
        val exactExecutable = matchingExecutable.singleOrNull().takeIf { exactSelection }
        val exactUnavailable = matchingUnavailable.singleOrNull().takeIf { exactSelection }

        if (exactUnavailable != null) {
            return capabilityForUnavailable(exactUnavailable)
        }
        if (matchingExecutable.isEmpty()) {
            val reason = matchingUnavailable.singleOrNull()?.reason
                ?: "No reviewed ${protocol.displayName} route matches the selected networks and asset."
            return unavailableCapability(
                reason = reason,
                routes = matchingExecutable,
                unavailable = matchingUnavailable
            )
        }

        val enabled = actionsEnabled()
        val runtime = exactExecutable?.let { runtimeResolver.resolve(it) }
        return CrossChainRouteCapability(
            providerId = providerId,
            protocol = protocol,
            availability = CrossChainRouteAvailability.Available,
            actionsEnabled = enabled,
            requiresAccount = true,
            requiresSigning = true,
            supportedOriginNetworkIds = matchingExecutable.mapTo(linkedSetOf()) { it.originChainId },
            supportedDestinationNetworkIds = matchingExecutable.mapTo(linkedSetOf()) { it.destinationChainId },
            supportedAssets = matchingExecutable.mapTo(linkedSetOf()) { it.assetIdentity() },
            minimumAmount = exactExecutable?.minimumAmount,
            minimumAssetSymbol = exactExecutable?.minimumAmount?.let { exactExecutable.symbol },
            destinationFee = exactExecutable?.let {
                CrossChainFeeQuote(it.execution.destinationFee, it.symbol, live = false)
            },
            providerContext = exactExecutable?.let {
                CrossChainProviderContext(
                    providerId = providerId,
                    routeId = it.routeId,
                    runtimeFingerprint = reviewedBridgeRuntimeFingerprint(it, requireNotNull(runtime))
                )
            },
            estimatedTime = exactExecutable?.estimatedTime,
            warnings = if (exactExecutable == null) emptyList() else listOf(
                "The destination fee is reviewed static data; final submission re-resolves live runtime authority and balances."
            ),
            userFacingReason = if (enabled) null else actionsDisabledReason()
        )
    }

    override suspend fun inventoryCapabilities(): List<CrossChainRouteCapability> = buildList {
        executableRoutes.forEach { route ->
            add(
                runCatching {
                    capability(
                        CrossChainRouteQuery(route.originChainId, route.destinationChainId, route.originAssetId)
                    )
                }.getOrElse { error ->
                    capabilityForRuntimeFailure(route, error)
                }
            )
        }
        unavailableRoutes.forEach { add(capabilityForUnavailable(it)) }
    }

    private fun capabilityForRuntimeFailure(route: ReviewedBridgeRoute, error: Throwable) =
        CrossChainRouteCapability(
            providerId = providerId,
            protocol = protocol,
            availability = CrossChainRouteAvailability.Unavailable,
            actionsEnabled = false,
            requiresAccount = true,
            requiresSigning = true,
            supportedOriginNetworkIds = setOf(route.originChainId),
            supportedDestinationNetworkIds = setOf(route.destinationChainId),
            supportedAssets = setOf(route.assetIdentity()),
            minimumAmount = route.minimumAmount,
            minimumAssetSymbol = route.minimumAmount?.let { route.symbol },
            destinationFee = CrossChainFeeQuote(
                amount = route.execution.destinationFee,
                assetSymbol = route.symbol,
                live = false
            ),
            estimatedTime = route.estimatedTime,
            userFacingReason = error.message ?: "Reviewed bridge runtime authority is unavailable."
        )

    private fun capabilityForUnavailable(route: UnavailableReviewedBridgeRoute) = CrossChainRouteCapability(
        providerId = providerId,
        protocol = protocol,
        availability = CrossChainRouteAvailability.Unavailable,
        actionsEnabled = false,
        requiresAccount = true,
        requiresSigning = true,
        supportedOriginNetworkIds = setOf(route.originChainId),
        supportedDestinationNetworkIds = setOf(route.destinationChainId),
        supportedAssets = setOf(route.assetIdentity()),
        minimumAmount = route.minimumAmount,
        minimumAssetSymbol = route.minimumAmount?.let { route.symbol },
        estimatedTime = route.estimatedTime,
        userFacingReason = route.reason
    )

    private fun unavailableCapability(
        reason: String,
        availability: CrossChainRouteAvailability = CrossChainRouteAvailability.Unavailable,
        routes: List<ReviewedBridgeRoute> = emptyList(),
        unavailable: List<UnavailableReviewedBridgeRoute> = emptyList()
    ) = CrossChainRouteCapability(
        providerId = providerId,
        protocol = protocol,
        availability = availability,
        actionsEnabled = false,
        requiresAccount = true,
        requiresSigning = true,
        supportedOriginNetworkIds = (routes.map { it.originChainId } + unavailable.map { it.originChainId }).toSet(),
        supportedDestinationNetworkIds =
            (routes.map { it.destinationChainId } + unavailable.map { it.destinationChainId }).toSet(),
        supportedAssets = (routes.map { it.assetIdentity() } + unavailable.map { it.assetIdentity() }).toSet(),
        userFacingReason = reason
    )

    private fun ReviewedBridgeRoute.matches(query: CrossChainRouteQuery): Boolean =
        (query.originNetworkId == null || originChainId == query.originNetworkId) &&
            (query.destinationNetworkId == null || destinationChainId == query.destinationNetworkId) &&
            (query.originAssetId == null || originAssetId == query.originAssetId)

    private fun UnavailableReviewedBridgeRoute.matches(query: CrossChainRouteQuery): Boolean =
        (query.originNetworkId == null || originChainId == query.originNetworkId) &&
            (query.destinationNetworkId == null || destinationChainId == query.destinationNetworkId) &&
            (query.originAssetId == null || originAssetId == query.originAssetId)

    private fun ReviewedBridgeRoute.assetIdentity() =
        CrossChainAssetIdentity(originChainId, originAssetId, symbol)

    private fun UnavailableReviewedBridgeRoute.assetIdentity() =
        CrossChainAssetIdentity(originChainId, originAssetId, symbol)
}

/** Metadata-backed resolver. Immutable routes remain the authority; live metadata can only disable them. */
class ChainRegistryReviewedBridgeRuntimeResolver(
    private val chainRegistry: ChainRegistry,
    private val runtimeIdentityResolver: ReviewedBridgeRuntimeIdentityResolver,
    private val registrationAuthority: ReviewedBridgeRegistrationAuthority,
    private val runtimeMinimumResolver: suspend (ReviewedBridgeRoute) -> ReviewedBridgeRuntimeMinimum
) : ReviewedBridgeRuntimeResolver {
    override suspend fun resolve(route: ReviewedBridgeRoute): ReviewedBridgeRuntimeResolution {
        val origin = chainRegistry.getChain(route.originChainId)
        val destination = chainRegistry.getChain(route.destinationChainId)
        require(origin.id == route.originChainId && destination.id == route.destinationChainId) {
            "cross_chain_runtime_genesis_mismatch"
        }
        require(!origin.isTestNet && !destination.isTestNet) { "cross_chain_testnet_route_rejected" }
        require(origin.ecosystem == Ecosystem.Substrate && destination.ecosystem == Ecosystem.Substrate) {
            "cross_chain_route_ecosystem_mismatch"
        }
        require(
            origin.xcm?.xcmVersion?.lowercase() == "v3" &&
                destination.xcm?.xcmVersion?.lowercase() == "v3"
        ) { "cross_chain_runtime_xcm_version_drift" }

        val matchingAssets = origin.assets.filter { asset ->
            asset.id == route.originAssetId &&
                (asset.currencyId ?: asset.id) == route.originCanonicalAssetId &&
                asset.symbol.uppercase() == route.symbol &&
                asset.precision == route.precision
        }
        require(matchingAssets.size == 1) { "cross_chain_route_catalog_drift" }

        val xcmAssets = origin.xcm?.availableAssets.orEmpty().filter {
            it.id == route.originXcmAssetId && it.symbol?.uppercase() == route.symbol
        }
        val destinations = origin.xcm?.availableDestinations.orEmpty().filter {
            it.chainId == route.destinationChainId
        }
        val destinationAssets = destinations.flatMap { it.assets.orEmpty() }.filter {
            it.id == route.destinationXcmAssetId && it.symbol?.uppercase() == route.symbol
        }
        require(xcmAssets.size == 1 && destinations.size == 1 && destinationAssets.size == 1) {
            "cross_chain_route_catalog_drift"
        }

        val runtime = chainRegistry.getRuntime(route.originChainId)
        val expected = route.execution.runtimeCall
        val modules = runtime.metadata.modules.values.filter {
            it.name.matchesReviewedPalletName(expected.pallet)
        }
        require(modules.size == 1) { "cross_chain_runtime_execution_drift" }
        val functions = modules.single().calls.orEmpty().values.filter {
            it.name == expected.call
        }
        require(functions.size == 1) { "cross_chain_runtime_execution_drift" }
        val function = functions.single()
        val rawArguments = function.arguments.map { it.name }
        require(rawArguments.matchesReviewedArgumentNames(expected.arguments)) {
            "cross_chain_runtime_execution_drift"
        }

        val originRuntimeIdentity = runtimeIdentityResolver.resolve(route.originChainId)
        val destinationRuntimeIdentity = runtimeIdentityResolver.resolve(route.destinationChainId)
        require(originRuntimeIdentity.genesisHash.matchesChainId(route.originChainId)) {
            "cross_chain_runtime_genesis_mismatch"
        }
        require(destinationRuntimeIdentity.genesisHash.matchesChainId(route.destinationChainId)) {
            "cross_chain_runtime_genesis_mismatch"
        }
        val registrationFingerprint = registrationAuthority.verify(route)
        val runtimeMinimum = runtimeMinimumResolver(route)
        require(route.execution.destinationMinimum == null || runtimeMinimum.amountInOriginPlanks.signum() > 0) {
            "cross_chain_runtime_minimum_unavailable"
        }
        return ReviewedBridgeRuntimeResolution(
            originRuntime = originRuntimeIdentity,
            destinationRuntime = destinationRuntimeIdentity,
            moduleName = modules.single().name,
            callName = function.name,
            rawArgumentNames = rawArguments,
            registrationFingerprint = registrationFingerprint,
            minimumFingerprint = runtimeMinimum.proofFingerprint,
            runtimeMinimumInPlanks = runtimeMinimum.amountInOriginPlanks
        )
    }
}

data class ReviewedBridgeBalanceSnapshot(
    val originChainId: ChainId,
    val accountId: ByteArray,
    val transferAssetId: String,
    val transferAssetBalance: BigInteger,
    val feeAssetId: String,
    val feeAssetBalance: BigInteger,
    val observedAtMillis: Long
) {
    init {
        require(originChainId.isNotBlank() && accountId.isNotEmpty()) { "Reviewed bridge balance scope is required" }
        require(transferAssetId.isNotBlank() && feeAssetId.isNotBlank()) { "Reviewed bridge balance assets are required" }
        require(transferAssetBalance.signum() >= 0 && feeAssetBalance.signum() >= 0) {
            "Reviewed bridge balances must not be negative"
        }
        require(observedAtMillis >= 0) { "Reviewed bridge balance timestamp must not be negative" }
    }

    override fun equals(other: Any?): Boolean = other is ReviewedBridgeBalanceSnapshot &&
        originChainId == other.originChainId && accountId.contentEquals(other.accountId) &&
        transferAssetId == other.transferAssetId && transferAssetBalance == other.transferAssetBalance &&
        feeAssetId == other.feeAssetId && feeAssetBalance == other.feeAssetBalance &&
        observedAtMillis == other.observedAtMillis

    override fun hashCode(): Int = 31 * originChainId.hashCode() + accountId.contentHashCode()
}

fun interface ReviewedBridgeBalanceReader {
    /** Must refresh authoritative state before returning. */
    suspend fun refreshAndRead(
        originChain: Chain,
        transferAsset: Asset,
        feeAsset: Asset,
        accountId: ByteArray
    ): ReviewedBridgeBalanceSnapshot
}

data class ReviewedBridgeTransferRequest(
    val providerId: String,
    val routeId: String,
    val originChain: Chain,
    val destinationChain: Chain,
    val asset: Asset,
    val senderAccountId: ByteArray,
    val recipientAddress: String,
    val amountInPlanks: BigInteger,
    val keypairProvider: KeypairProvider
)

data class ReviewedBridgeQuote(
    val providerId: String,
    val routeId: String,
    val runtimeFingerprint: String,
    /** Binds route/provider/account/recipient/amount/runtime and both fees. */
    val executionFingerprint: String,
    val originFeeInPlanks: BigInteger,
    val destinationFeeInPlanks: BigInteger,
    val effectiveMinimumInPlanks: BigInteger
)

class ReviewedPolkaswapBridgeExecutor(
    private val runtimeResolver: ReviewedBridgeRuntimeResolver,
    private val balanceReader: ReviewedBridgeBalanceReader,
    private val submitter: XcmExtrinsicSubmitter,
    private val mutationsEnabled: () -> Boolean,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val maximumBalanceAgeMillis: Long = 30_000L
) {
    init {
        require(maximumBalanceAgeMillis >= 0) { "Reviewed bridge balance age must not be negative" }
    }

    suspend fun quote(request: ReviewedBridgeTransferRequest): ReviewedBridgeQuote {
        val resolved = resolveAndValidate(request)
        val call = buildCall(resolved.route, resolved.runtime, request.recipientAddress, request.amountInPlanks)
        assertSigningCapability(request)
        val fee = submitter.estimateFee(
            chain = request.originChain,
            accountId = request.senderAccountId,
            keypairProvider = request.keypairProvider,
            call = call
        )
        require(fee.signum() >= 0) { "cross_chain_fee_unavailable" }
        val balances = balanceReader.refreshAndRead(
            originChain = request.originChain,
            transferAsset = request.asset,
            feeAsset = resolved.feeAsset,
            accountId = request.senderAccountId
        )
        assertFreshBalances(request, resolved.feeAsset, fee, balances)

        val runtimeFingerprint = reviewedBridgeRuntimeFingerprint(resolved.route, resolved.runtime)
        val minimum = maxOf(resolved.route.minimumInPlanks ?: BigInteger.ZERO, resolved.runtime.runtimeMinimumInPlanks)
        require(request.amountInPlanks >= minimum) { "cross_chain_amount_below_runtime_minimum" }
        val destinationFee = resolved.route.destinationFeeInPlanks

        return ReviewedBridgeQuote(
            providerId = resolved.route.providerId,
            routeId = resolved.route.routeId,
            runtimeFingerprint = runtimeFingerprint,
            executionFingerprint = quoteFingerprint(
                request = request,
                runtimeFingerprint = runtimeFingerprint,
                originFeeInPlanks = fee,
                destinationFeeInPlanks = destinationFee,
                minimumInPlanks = minimum
            ),
            originFeeInPlanks = fee,
            destinationFeeInPlanks = destinationFee,
            effectiveMinimumInPlanks = minimum
        )
    }

    /** Re-resolves every authority and quote immediately before the guarded one-call submit. */
    suspend fun submit(request: ReviewedBridgeTransferRequest, confirmedQuote: ReviewedBridgeQuote): String {
        require(confirmedQuote.providerId == request.providerId && confirmedQuote.routeId == request.routeId) {
            "cross_chain_provider_context_mismatch"
        }
        val finalQuote = quote(request)
        require(finalQuote.runtimeFingerprint == confirmedQuote.runtimeFingerprint) {
            "cross_chain_runtime_fingerprint_changed"
        }
        require(finalQuote.executionFingerprint == confirmedQuote.executionFingerprint) {
            "cross_chain_quote_changed"
        }
        require(finalQuote.originFeeInPlanks == confirmedQuote.originFeeInPlanks) { "cross_chain_fee_changed" }
        require(finalQuote.destinationFeeInPlanks == confirmedQuote.destinationFeeInPlanks) {
            "cross_chain_destination_fee_changed"
        }

        val resolved = resolveAndValidate(request)
        val call = buildCall(resolved.route, resolved.runtime, request.recipientAddress, request.amountInPlanks)
        require(reviewedBridgeRuntimeFingerprint(resolved.route, resolved.runtime) == finalQuote.runtimeFingerprint) {
            "cross_chain_runtime_fingerprint_changed"
        }
        assertSigningCapability(request)

        // The submitter independently binds the exact prepared intent and guards key/sign/transport boundaries.
        check(mutationsEnabled()) { "Polkaswap bridge actions are temporarily disabled." }
        return submitter.submit(request.originChain, request.senderAccountId, request.keypairProvider, call)
    }

    internal fun buildCall(
        route: ReviewedBridgeRoute,
        runtime: ReviewedBridgeRuntimeResolution,
        recipientAddress: String,
        amountInPlanks: BigInteger
    ): XcmExtrinsicCall {
        require(amountInPlanks.signum() > 0) { "cross_chain_amount_invalid" }
        val accountId = decodeRecipient(recipientAddress)
        val values = when (route.execution.kind) {
            ReviewedBridgeExecutionKind.SoraBridgeProxyBurnV3 -> listOf(
                mapOf("Sub" to route.execution.bridgeNetwork.name),
                route.execution.soraAssetId,
                soraBridgeRecipient(route.execution, accountId),
                amountInPlanks
            )

            ReviewedBridgeExecutionKind.LiberlandToSoraBurn -> listOf(
                "Mainnet",
                when (val external = requireNotNull(route.execution.externalAsset)) {
                    ReviewedBridgeExternalAsset.Lld -> "LLD"
                    is ReviewedBridgeExternalAsset.Asset -> mapOf("Asset" to external.id)
                },
                mapOf("Sora" to recipientAddress),
                amountInPlanks
            )

            ReviewedBridgeExecutionKind.ExternalToSoraXcmV3 -> externalToSoraArguments(
                execution = route.execution,
                accountId = accountId,
                amountInPlanks = amountInPlanks
            )
        }
        require(runtime.rawArgumentNames.size == values.size) { "cross_chain_runtime_execution_drift" }

        return XcmExtrinsicCall(
            moduleName = runtime.moduleName,
            callName = runtime.callName,
            arguments = runtime.rawArgumentNames.zip(values).toMap(LinkedHashMap())
        )
    }

    private suspend fun resolveAndValidate(request: ReviewedBridgeTransferRequest): ResolvedRequest {
        val route = ReviewedPolkaswapBridgeCatalog.findExecutable(request.routeId)
            ?: throw IllegalArgumentException("cross_chain_route_id_not_reviewed")
        require(route.providerId == request.providerId) { "cross_chain_provider_mismatch" }
        require(request.originChain.id == route.originChainId && request.destinationChain.id == route.destinationChainId) {
            "cross_chain_route_network_mismatch"
        }
        require(!request.originChain.isTestNet && !request.destinationChain.isTestNet) {
            "cross_chain_testnet_route_rejected"
        }
        require(request.originChain.ecosystem == Ecosystem.Substrate && request.destinationChain.ecosystem == Ecosystem.Substrate) {
            "cross_chain_route_ecosystem_mismatch"
        }
        require(request.asset.chainId == route.originChainId && request.asset.id == route.originAssetId) {
            "cross_chain_origin_asset_mismatch"
        }
        require((request.asset.currencyId ?: request.asset.id) == route.originCanonicalAssetId) {
            "cross_chain_asset_key_mismatch"
        }
        require(request.asset.symbol.uppercase() == route.symbol && request.asset.precision == route.precision) {
            "cross_chain_asset_metadata_mismatch"
        }
        require(request.amountInPlanks.signum() > 0) { "cross_chain_amount_invalid" }
        route.minimumInPlanks?.let { minimum ->
            require(request.amountInPlanks >= minimum) { "cross_chain_amount_below_reviewed_minimum" }
        }
        require(request.senderAccountId.size == SUBSTRATE_ACCOUNT_SIZE) { "cross_chain_sender_account_mismatch" }
        validateRecipient(request.recipientAddress, request.destinationChain)

        val feeAssets = request.originChain.assets.filter(Asset::isUtility)
        require(feeAssets.size == 1) { "cross_chain_fee_asset_unavailable" }
        val runtime = runtimeResolver.resolve(route)
        require(runtime.originRuntime.genesisHash.matchesChainId(route.originChainId)) {
            "cross_chain_runtime_genesis_mismatch"
        }
        require(runtime.destinationRuntime.genesisHash.matchesChainId(route.destinationChainId)) {
            "cross_chain_runtime_genesis_mismatch"
        }
        require(
            runtime.moduleName.matchesReviewedPalletName(route.execution.runtimeCall.pallet) &&
                runtime.callName == route.execution.runtimeCall.call &&
                runtime.rawArgumentNames.matchesReviewedArgumentNames(route.execution.runtimeCall.arguments)
        ) { "cross_chain_runtime_execution_drift" }

        return ResolvedRequest(route, runtime, feeAssets.single())
    }

    private suspend fun assertSigningCapability(request: ReviewedBridgeTransferRequest) {
        request.keypairProvider.getCryptoTypeFor(request.originChain, request.senderAccountId)
        // Quotes use public crypto metadata only. Actual key ownership is checked under the submission lease.
    }

    private fun assertFreshBalances(
        request: ReviewedBridgeTransferRequest,
        feeAsset: Asset,
        fee: BigInteger,
        balances: ReviewedBridgeBalanceSnapshot
    ) {
        require(balances.originChainId == request.originChain.id) { "cross_chain_balance_scope_mismatch" }
        require(balances.accountId.contentEquals(request.senderAccountId)) { "cross_chain_selected_account_changed" }
        require(balances.transferAssetId == request.asset.id && balances.feeAssetId == feeAsset.id) {
            "cross_chain_balance_asset_mismatch"
        }
        val age = nowMillis() - balances.observedAtMillis
        require(age in 0..maximumBalanceAgeMillis) { "cross_chain_origin_balance_stale" }
        val transferRequired = if (request.asset.id == feeAsset.id) request.amountInPlanks + fee else request.amountInPlanks
        require(balances.transferAssetBalance >= transferRequired) { "cross_chain_origin_balance_insufficient" }
        if (request.asset.id != feeAsset.id) {
            require(balances.feeAssetBalance >= fee) { "cross_chain_fee_balance_insufficient" }
        }
    }

    private fun validateRecipient(recipientAddress: String, destinationChain: Chain): ByteArray {
        val accountId = decodeRecipient(recipientAddress)
        val canonicalAddress = runCatching {
            accountId.toAddress(destinationChain.addressPrefix.toShort())
        }.getOrNull()
        require(canonicalAddress == recipientAddress) { "cross_chain_recipient_network_mismatch" }
        return accountId
    }

    private fun decodeRecipient(recipientAddress: String): ByteArray {
        require(recipientAddress.isNotBlank() && recipientAddress == recipientAddress.trim()) {
            "cross_chain_recipient_invalid"
        }
        val accountId = runCatching { recipientAddress.toAccountId() }.getOrNull()
        require(accountId?.size == SUBSTRATE_ACCOUNT_SIZE) { "cross_chain_recipient_invalid" }
        return accountId
    }

    private fun soraBridgeRecipient(execution: ReviewedBridgeExecution, accountId: ByteArray): Map<String, Any> {
        val account = mapOf("AccountId32" to mapOf("id" to accountId.toHexString(withPrefix = true)))
        return when (execution.recipientKind) {
            ReviewedBridgeRecipientKind.Liberland -> mapOf(
                "Liberland" to accountId.toHexString(withPrefix = true)
            )

            ReviewedBridgeRecipientKind.Relay -> mapOf(
                "Parachain" to mapOf(
                    "V3" to mapOf("parents" to 1, "interior" to mapOf("X1" to account))
                )
            )

            ReviewedBridgeRecipientKind.Parachain -> mapOf(
                "Parachain" to mapOf(
                    "V3" to mapOf(
                        "parents" to 1,
                        "interior" to mapOf(
                            "X2" to listOf(mapOf("Parachain" to execution.destinationParaId), account)
                        )
                    )
                )
            )

            ReviewedBridgeRecipientKind.Sora -> error("SORA recipient is not valid for bridgeProxy.burn")
        }
    }

    private fun externalToSoraArguments(
        execution: ReviewedBridgeExecution,
        accountId: ByteArray,
        amountInPlanks: BigInteger
    ): List<Any> {
        val account = mapOf("AccountId32" to mapOf("id" to accountId.toHexString(withPrefix = true)))
        val soraParachainId = requireNotNull(execution.soraParachainId)
        return when (execution.externalSource) {
            ReviewedBridgeExternalSource.RelayNative,
            ReviewedBridgeExternalSource.AstarNative -> {
                val destinationParents = if (execution.externalSource == ReviewedBridgeExternalSource.AstarNative) 1 else 0
                listOf(
                    mapOf(
                        "V3" to mapOf(
                            "parents" to destinationParents,
                            "interior" to mapOf("X1" to mapOf("Parachain" to soraParachainId))
                        )
                    ),
                    mapOf(
                        "V3" to mapOf(
                            "parents" to 0,
                            "interior" to mapOf("X1" to account)
                        )
                    ),
                    mapOf(
                        "V3" to listOf(
                            mapOf(
                                "id" to mapOf(
                                    "Concrete" to mapOf("parents" to 0, "interior" to "Here")
                                ),
                                "fun" to mapOf("Fungible" to amountInPlanks)
                            )
                        )
                    ),
                    0
                )
            }

            ReviewedBridgeExternalSource.AcalaNative -> listOf(
                mapOf("Token" to requireNotNull(execution.currencyToken)),
                amountInPlanks,
                mapOf(
                    "V3" to mapOf(
                        "parents" to 1,
                        "interior" to mapOf(
                            "X2" to listOf(mapOf("Parachain" to soraParachainId), account)
                        )
                    )
                ),
                mapOf("Unlimited" to null)
            )

            null -> error("cross_chain_execution_definition_mismatch")
        }
    }

    private fun quoteFingerprint(
        request: ReviewedBridgeTransferRequest,
        runtimeFingerprint: String,
        originFeeInPlanks: BigInteger,
        destinationFeeInPlanks: BigInteger,
        minimumInPlanks: BigInteger
    ): String {
        val quote = listOf(
            "1",
            request.routeId,
            request.providerId,
            request.asset.id,
            request.asset.currencyId ?: request.asset.id,
            request.originChain.id,
            request.destinationChain.id,
            request.senderAccountId.toHexString(withPrefix = true),
            request.recipientAddress,
            request.amountInPlanks.toString(),
            originFeeInPlanks.toString(),
            destinationFeeInPlanks.toString(),
            minimumInPlanks.toString(),
            runtimeFingerprint
        ).joinToString("\u001f")
        return quote.toByteArray().blake2b256().toHexString(withPrefix = true)
    }

    private data class ResolvedRequest(
        val route: ReviewedBridgeRoute,
        val runtime: ReviewedBridgeRuntimeResolution,
        val feeAsset: Asset
    )

    private companion object {
        const val SUBSTRATE_ACCOUNT_SIZE = 32
    }
}

/**
 * Runtime metadata uses a PascalCase pallet name while Polkadot.js exposes the same pallet with a
 * lower-camel name. Those are the only two spellings accepted by the reviewed authority.
 */
private fun String.matchesReviewedPalletName(reviewedName: String): Boolean =
    this == reviewedName || this == reviewedName.replaceFirstChar { it.uppercase() }

/**
 * SCALE metadata keeps Rust's snake_case argument names; Polkadot.js exposes lower-camel names.
 * Accept those two exact platform spellings without punctuation/case folding, which could turn a
 * genuinely different runtime schema into a false match.
 */
private fun List<String>.matchesReviewedArgumentNames(reviewedNames: List<String>): Boolean =
    size == reviewedNames.size && indices.all { index ->
        val reviewed = reviewedNames[index]
        this[index] == reviewed || this[index] == reviewed.toReviewedSnakeCase()
    }

private fun String.toReviewedSnakeCase(): String = buildString(length + 4) {
    this@toReviewedSnakeCase.forEachIndexed { index, character ->
        if (character.isUpperCase()) {
            if (index != 0) append('_')
            append(character.lowercaseChar())
        } else {
            append(character)
        }
    }
}

private fun String.matchesChainId(chainId: ChainId): Boolean =
    removePrefix("0x").equals(chainId.removePrefix("0x"), ignoreCase = true)

private fun String.toReviewedPlanks(precision: Int): BigInteger =
    BigDecimal(this).movePointRight(precision).toBigIntegerExact()
