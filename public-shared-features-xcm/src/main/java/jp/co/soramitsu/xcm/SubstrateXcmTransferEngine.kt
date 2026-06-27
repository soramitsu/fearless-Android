package jp.co.soramitsu.xcm

import jp.co.soramitsu.core.extrinsic.ExtrinsicBuilderFactory
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.core.models.ChainIdWithMetadata
import jp.co.soramitsu.core.rpc.RpcCalls
import jp.co.soramitsu.core.utils.removedXcPrefix
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.xcm.domain.XcmArgumentShape
import jp.co.soramitsu.xcm.domain.XcmDestinationFeeMode
import jp.co.soramitsu.xcm.domain.XcmExecutionSpec
import jp.co.soramitsu.xcm.domain.XcmJunctionSpec
import jp.co.soramitsu.xcm.domain.XcmJunctionType
import jp.co.soramitsu.xcm.domain.XcmMultiLocationSpec
import jp.co.soramitsu.xcm.domain.XcmTransferType
import jp.co.soramitsu.xcm.domain.XcmWeightLimitType
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Locale

data class XcmExtrinsicCall(
    val moduleName: String,
    val callName: String,
    val arguments: Map<String, Any?>
)

interface XcmExtrinsicSubmitter {
    suspend fun submit(
        chain: Chain,
        accountId: ByteArray,
        keypairProvider: KeypairProvider,
        call: XcmExtrinsicCall
    ): String

    suspend fun estimateFee(
        chain: Chain,
        accountId: ByteArray,
        keypairProvider: KeypairProvider,
        call: XcmExtrinsicCall
    ): BigInteger
}

class ExtrinsicServiceXcmSubmitter(
    private val rpcCalls: RpcCalls,
    private val extrinsicBuilderFactory: ExtrinsicBuilderFactory
) : XcmExtrinsicSubmitter {

    override suspend fun submit(
        chain: Chain,
        accountId: ByteArray,
        keypairProvider: KeypairProvider,
        call: XcmExtrinsicCall
    ): String {
        return extrinsicService(keypairProvider)
            .submitExtrinsic(
                chain = chain,
                accountId = accountId,
                formExtrinsic = { applyXcmCall(call) }
            )
            .getOrThrow()
    }

    override suspend fun estimateFee(
        chain: Chain,
        accountId: ByteArray,
        keypairProvider: KeypairProvider,
        call: XcmExtrinsicCall
    ): BigInteger {
        return extrinsicService(keypairProvider)
            .estimateFee(
                chain = chain,
                accountId = accountId,
                formExtrinsic = { applyXcmCall(call) }
            )
    }

    private fun extrinsicService(keypairProvider: KeypairProvider) = ExtrinsicService(
        rpcCalls = rpcCalls,
        keypairProvider = keypairProvider,
        extrinsicBuilderFactory = extrinsicBuilderFactory
    )

    private fun ExtrinsicBuilder.applyXcmCall(call: XcmExtrinsicCall) {
        call(
            moduleName = call.moduleName,
            callName = call.callName,
            arguments = call.arguments
        )
    }
}

class SubstrateXcmTransferEngine(
    private val submitter: XcmExtrinsicSubmitter
) : XcmTransferEngine {

    private val keypairProviders = mutableMapOf<ChainId, KeypairProvider>()
    private val preloadedMetadata = mutableMapOf<ChainId, String?>()

    override val isAvailable: Boolean = true

    override fun updateKeypairProvider(chainId: ChainId, keypairProvider: Any) {
        require(chainId.isNotBlank()) { "XCM keypair provider chain id must not be blank" }
        require(keypairProvider is KeypairProvider) {
            "XCM keypair provider must implement KeypairProvider"
        }
        keypairProviders[chainId] = keypairProvider
    }

    override fun addPreloadedMetadata(vararg chainMetadatas: ChainIdWithMetadata) {
        chainMetadatas.forEach { metadata ->
            require(metadata.chainId.isNotBlank()) { "XCM preloaded metadata chain id must not be blank" }
            preloadedMetadata[metadata.chainId] = metadata.metadata
        }
    }

    override suspend fun transfer(request: XcmTransferRequest): String {
        val provider = requireKeypairProvider(request.originChain.id)
        val call = buildTransferCall(request.executionSpec, request.recipientAddress, request.amount)

        return submitter.submit(
            chain = request.originChain,
            accountId = request.senderAccountId,
            keypairProvider = provider,
            call = call
        )
    }

    override suspend fun getDestinationFee(
        originChainId: ChainId,
        destinationChainId: ChainId,
        asset: Asset,
        executionSpec: XcmExecutionSpec
    ): BigDecimal {
        require(originChainId.isNotBlank()) { "XCM destination fee origin chain id must not be blank" }
        require(destinationChainId.isNotBlank()) { "XCM destination fee destination chain id must not be blank" }
        require(originChainId != destinationChainId) { "XCM destination fee route chains must be different" }
        require(asset.precision >= 0) { "XCM destination fee asset precision must not be negative" }
        require(asset.symbol.normalizedAssetSymbol() == executionSpec.destinationFee.assetSymbol.normalizedAssetSymbol()) {
            "XCM destination fee asset ${executionSpec.destinationFee.assetSymbol} does not match ${asset.symbol}"
        }

        return when (executionSpec.destinationFee.mode) {
            XcmDestinationFeeMode.INCLUDED -> BigDecimal.ZERO
            XcmDestinationFeeMode.FIXED -> BigDecimal(requireNotNull(executionSpec.destinationFee.amount), asset.precision)
            XcmDestinationFeeMode.ESTIMATED -> throw UnsupportedOperationException(
                "XCM destination fee estimation is unavailable for ${asset.symbol} from $originChainId to $destinationChainId"
            )
        }
    }

    override suspend fun getOriginFee(
        originChain: Chain,
        originChainId: ChainId,
        destinationChainId: ChainId,
        asset: Asset,
        address: String,
        amount: BigInteger,
        executionSpec: XcmExecutionSpec
    ): BigDecimal {
        require(originChain.id == originChainId) {
            "XCM origin fee chain object must match originChainId"
        }
        require(asset.precision >= 0) { "XCM origin fee asset precision must not be negative" }
        val provider = requireKeypairProvider(originChainId)
        val call = buildTransferCall(executionSpec, address, amount)

        return BigDecimal(
            submitter.estimateFee(
                chain = originChain,
                accountId = ByteArray(FEE_ESTIMATE_ACCOUNT_ID_SIZE_BYTES),
                keypairProvider = provider,
                call = call
            ),
            asset.precision
        )
    }

    fun buildTransferCall(
        executionSpec: XcmExecutionSpec,
        recipientAddress: String,
        amount: BigInteger
    ): XcmExtrinsicCall {
        require(recipientAddress.isNotBlank()) { "XCM recipient address must not be blank" }
        require(amount > BigInteger.ZERO) { "XCM transfer amount must be greater than zero" }

        return when (executionSpec.argumentShape) {
            XcmArgumentShape.POLKADOT_XCM_TRANSFER_ASSETS -> buildPolkadotXcmTransferAssetsCall(
                executionSpec,
                recipientAddress,
                amount
            )
            XcmArgumentShape.X_TOKENS_TRANSFER_MULTIASSET -> buildXTokensTransferMultiassetCall(
                executionSpec,
                recipientAddress,
                amount
            )
        }
    }

    private fun buildPolkadotXcmTransferAssetsCall(
        executionSpec: XcmExecutionSpec,
        recipientAddress: String,
        amount: BigInteger
    ): XcmExtrinsicCall {
        require(executionSpec.palletName == "PolkadotXcm") {
            "XCM PolkadotXcm transfer-assets call requires palletName PolkadotXcm"
        }
        require(executionSpec.callName == executionSpec.transferType.polkadotXcmCallName()) {
            "XCM PolkadotXcm transfer-assets callName must match transferType"
        }

        val arguments = linkedMapOf<String, Any?>(
            "dest" to versionedMultiLocation(executionSpec, executionSpec.destinationLocation, recipientAddress),
            "beneficiary" to versionedMultiLocation(executionSpec, executionSpec.beneficiaryLocation, recipientAddress),
            "assets" to versioned(
                executionSpec,
                listOf(
                    mapOf(
                        "id" to versionedMultiLocation(executionSpec, executionSpec.assetLocation, recipientAddress),
                        "fun" to mapOf("Fungible" to amount)
                    )
                )
            ),
            "fee_asset_item" to executionSpec.feeAssetItem
        )

        if (executionSpec.transferType.requiresWeightLimit()) {
            arguments["weight_limit"] = weightLimit(executionSpec)
        }

        return XcmExtrinsicCall(
            moduleName = executionSpec.palletName,
            callName = executionSpec.callName,
            arguments = arguments
        )
    }

    private fun buildXTokensTransferMultiassetCall(
        executionSpec: XcmExecutionSpec,
        recipientAddress: String,
        amount: BigInteger
    ): XcmExtrinsicCall {
        require(executionSpec.palletName == "XTokens") {
            "XCM XTokens transferMultiasset call requires palletName XTokens"
        }
        require(executionSpec.callName == "transferMultiasset") {
            "XCM XTokens transferMultiasset call requires callName transferMultiasset"
        }
        require(executionSpec.transferType == XcmTransferType.X_TOKENS_TRANSFER_MULTIASSET) {
            "XCM XTokens transferMultiasset call requires transferType xTokensTransferMultiasset"
        }

        return XcmExtrinsicCall(
            moduleName = executionSpec.palletName,
            callName = executionSpec.callName,
            arguments = linkedMapOf(
                "asset" to versioned(
                    executionSpec,
                    mapOf(
                        "id" to versionedMultiLocation(executionSpec, executionSpec.assetLocation, recipientAddress),
                        "fun" to mapOf("Fungible" to amount)
                    )
                ),
                "dest" to versionedMultiLocation(executionSpec, executionSpec.beneficiaryLocation, recipientAddress),
                "dest_weight_limit" to weightLimit(executionSpec)
            )
        )
    }

    private fun requireKeypairProvider(chainId: ChainId): KeypairProvider {
        return keypairProviders[chainId]
            ?: error("XCM keypair provider missing for $chainId")
    }

    private fun XcmTransferType.requiresWeightLimit(): Boolean {
        return this == XcmTransferType.LIMITED_RESERVE_TRANSFER_ASSETS ||
            this == XcmTransferType.LIMITED_TELEPORT_ASSETS
    }

    private fun XcmTransferType.polkadotXcmCallName(): String {
        return when (this) {
            XcmTransferType.RESERVE_TRANSFER_ASSETS -> "reserveTransferAssets"
            XcmTransferType.LIMITED_RESERVE_TRANSFER_ASSETS -> "limitedReserveTransferAssets"
            XcmTransferType.TELEPORT_ASSETS -> "teleportAssets"
            XcmTransferType.LIMITED_TELEPORT_ASSETS -> "limitedTeleportAssets"
            XcmTransferType.X_TOKENS_TRANSFER_MULTIASSET -> ""
        }
    }

    private fun versionedMultiLocation(
        executionSpec: XcmExecutionSpec,
        location: XcmMultiLocationSpec,
        recipientAddress: String
    ): Map<String, Any?> = versioned(executionSpec, multiLocation(location, recipientAddress))

    private fun versioned(executionSpec: XcmExecutionSpec, value: Any?): Map<String, Any?> {
        return mapOf(xcmVersionVariant(executionSpec.xcmVersion) to value)
    }

    private fun xcmVersionVariant(value: String): String {
        val version = value.trim()
        require(Regex("^v[0-9]+$", RegexOption.IGNORE_CASE).matches(version)) {
            "XCM version must use v<number> format"
        }
        return version.uppercase(Locale.US)
    }

    private fun String.normalizedAssetSymbol(): String = trim()
        .removedXcPrefix()
        .uppercase(Locale.US)

    private fun multiLocation(location: XcmMultiLocationSpec, recipientAddress: String): Map<String, Any?> {
        val interior = if (location.junctions.isEmpty()) {
            "Here"
        } else {
            mapOf("X${location.junctions.size}" to location.junctions.map { it.toRuntimeJunction(recipientAddress) })
        }

        return mapOf(
            "parents" to location.parents,
            "interior" to interior
        )
    }

    private fun XcmJunctionSpec.toRuntimeJunction(recipientAddress: String): Map<String, Any?> {
        return when (type) {
            XcmJunctionType.PARACHAIN -> mapOf("Parachain" to value!!.toBigInteger())
            XcmJunctionType.ACCOUNT_ID32 -> mapOf("AccountId32" to value!!.replace("<account>", recipientAddress))
            XcmJunctionType.ACCOUNT_KEY20 -> mapOf("AccountKey20" to value!!.replace("<account>", recipientAddress))
            XcmJunctionType.PALLET_INSTANCE -> mapOf("PalletInstance" to value!!.toBigInteger())
            XcmJunctionType.GENERAL_INDEX -> mapOf("GeneralIndex" to value!!.toBigInteger())
            XcmJunctionType.GENERAL_KEY -> mapOf("GeneralKey" to value)
        }
    }

    private fun weightLimit(executionSpec: XcmExecutionSpec): Any {
        return when (executionSpec.weightLimit.type) {
            XcmWeightLimitType.UNLIMITED -> "Unlimited"
            XcmWeightLimitType.LIMITED -> mapOf(
                "Limited" to mapOf(
                    "refTime" to requireNotNull(executionSpec.weightLimit.refTime),
                    "proofSize" to requireNotNull(executionSpec.weightLimit.proofSize)
                )
            )
        }
    }

    private companion object {
        const val FEE_ESTIMATE_ACCOUNT_ID_SIZE_BYTES = 32
    }
}
