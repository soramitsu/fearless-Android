package jp.co.soramitsu.xcm

import jp.co.soramitsu.core.extrinsic.ExtrinsicBuilderFactory
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.core.extrinsic.ExtrinsicSubmissionTracking
import jp.co.soramitsu.core.extrinsic.MutationExecutionGuard
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.core.extrinsic.mortality.MortalityConstructor
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.core.models.ChainIdWithMetadata
import jp.co.soramitsu.core.rpc.RpcCalls
import jp.co.soramitsu.core.utils.normalizedAssetSymbol
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.xcm.domain.XcmArgumentShape
import jp.co.soramitsu.xcm.domain.XcmDestinationFeeMode
import jp.co.soramitsu.xcm.domain.XcmExecutionSpec
import jp.co.soramitsu.xcm.domain.XcmJunctionSpec
import jp.co.soramitsu.xcm.domain.XcmJunctionType
import jp.co.soramitsu.xcm.domain.XcmMultiLocationSpec
import jp.co.soramitsu.xcm.domain.XcmTransferType
import jp.co.soramitsu.xcm.domain.XcmWeightLimitType
import jp.co.soramitsu.xcm.domain.isRecipientAt
import jp.co.soramitsu.xcm.domain.supportedXcmVersions
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class XcmExtrinsicCall(
    val moduleName: String,
    val callName: String,
    val arguments: Map<String, Any?>,
    /** Reviewed (pallet, call) index that the signing runtime must resolve the names to, when pinned. */
    val expectedIndex: Pair<Int, Int>? = null
)

/**
 * A submission signed only by [walletId], through a [keypairProvider] that can bind signing to that wallet, whose
 * signed bytes [tracking] retains until the outcome is settled, so an ambiguous failure can never lead to a second
 * transfer.
 */
class XcmTrackedSubmission(
    val walletId: Long,
    val keypairProvider: KeypairProvider,
    val tracking: ExtrinsicSubmissionTracking
)

interface XcmExtrinsicSubmitter {
    suspend fun submit(
        chain: Chain,
        accountId: ByteArray,
        keypairProvider: KeypairProvider,
        call: XcmExtrinsicCall
    ): String

    suspend fun submitTracked(
        chain: Chain,
        accountId: ByteArray,
        call: XcmExtrinsicCall,
        submission: XcmTrackedSubmission
    ): String = throw UnsupportedOperationException("Tracked cross-chain submission is unavailable")

    suspend fun estimateFee(
        chain: Chain,
        accountId: ByteArray,
        keypairProvider: KeypairProvider,
        call: XcmExtrinsicCall
    ): BigInteger
}

class ExtrinsicServiceXcmSubmitter(
    private val rpcCalls: RpcCalls,
    private val extrinsicBuilderFactory: ExtrinsicBuilderFactory,
    private val mortalityConstructor: MortalityConstructor,
    private val authorize: (String) -> MutationExecutionGuard = { error("Mutation authorization is unavailable") }
) : XcmExtrinsicSubmitter {

    /** Untracked submission could repeat a transfer after an ambiguous failure, so this submitter refuses it. */
    override suspend fun submit(
        chain: Chain,
        accountId: ByteArray,
        keypairProvider: KeypairProvider,
        call: XcmExtrinsicCall
    ): String = error("Cross-chain submissions must be tracked")

    override suspend fun submitTracked(
        chain: Chain,
        accountId: ByteArray,
        call: XcmExtrinsicCall,
        submission: XcmTrackedSubmission
    ): String {
        // The configured endpoint must serve the network the transfer is signed for; the genesis hash in the
        // signature alone does not stop a node of another network from receiving it.
        val genesis = rpcCalls.getBlockHash(chain.id, BigInteger.ZERO)
        require(genesis.removePrefix("0x").equals(chain.id.removePrefix("0x"), ignoreCase = true)) {
            "The connected node serves another network"
        }
        return extrinsicService(submission.keypairProvider)
            .submitTrackedExtrinsicForWallet(
                chain = chain,
                accountId = accountId,
                walletId = submission.walletId,
                authorize = authorize,
                tracking = submission.tracking,
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
        val quote = buildXcmFeeQuote(
            runtime = rpcCalls.getRuntime(chain.id),
            accountId = accountId,
            cryptoType = keypairProvider.getCryptoTypeFor(chain, accountId),
            nonce = rpcCalls.getAccountNonce(chain, accountId),
            era = mortalityConstructor.construct(chain).era,
            call = call
        )
        return rpcCalls.estimateExtrinsicFee(chain.id, quote)
    }

    private fun extrinsicService(keypairProvider: KeypairProvider) = ExtrinsicService(
        rpcCalls = rpcCalls,
        keypairProvider = keypairProvider,
        extrinsicBuilderFactory = extrinsicBuilderFactory
    )

    private fun ExtrinsicBuilder.applyXcmCall(call: XcmExtrinsicCall) {
        val function = runtime.metadata.module(call.moduleName).resolveXcmCall(call.callName)
        call.requirePinnedIndex(function)
        // The builder looks the call up again by exact name, so it gets the name the metadata actually uses.
        this.call(
            moduleName = call.moduleName,
            callName = function.name,
            arguments = call.toRuntimeArguments(function)
        )
    }
}

class SubstrateXcmTransferEngine(
    private val submitter: XcmExtrinsicSubmitter
) : XcmTransferEngine {

    // Screens register providers and metadata while quotes and transfers read them on other threads.
    private val keypairProviders = ConcurrentHashMap<ChainId, KeypairProvider>()
    private val preloadedMetadata = ConcurrentHashMap<ChainId, String>()

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
            metadata.metadata?.let { preloadedMetadata[metadata.chainId] = it } ?: preloadedMetadata.remove(metadata.chainId)
        }
    }

    override suspend fun transfer(request: XcmTransferRequest): String {
        require(request.asset.chainId == request.originChain.id) {
            "XCM transfer asset must belong to the origin chain"
        }
        require(request.senderAccountId.size == request.originChain.expectedAccountIdSize()) {
            "XCM transfer sender account id must match the approved origin-chain account width"
        }
        requireExecutionSpecMatchesAsset(request.asset, request.executionSpec)
        val call = buildTransferCall(request.executionSpec, request.recipientAddress, request.amount)

        val submission = request.submission ?: return submitter.submit(
            chain = request.originChain,
            accountId = request.senderAccountId,
            keypairProvider = requireKeypairProvider(request.originChain.id),
            call = call
        )
        return submitter.submitTracked(request.originChain, request.senderAccountId, call, submission)
    }

    override suspend fun getDestinationFee(
        originChainId: ChainId,
        destinationChainId: ChainId,
        asset: Asset,
        executionSpec: XcmExecutionSpec
    ): BigDecimal {
        requireExecutionSpecMatchesAsset(asset, executionSpec)
        require(originChainId.isNotBlank()) { "XCM destination fee origin chain id must not be blank" }
        require(destinationChainId.isNotBlank()) { "XCM destination fee destination chain id must not be blank" }
        require(originChainId != destinationChainId) { "XCM destination fee route chains must be different" }
        require(asset.chainId == originChainId) { "XCM destination fee asset must belong to the origin chain" }
        require(asset.precision >= 0) { "XCM destination fee asset precision must not be negative" }

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
        originFeeAsset: Asset,
        address: String,
        amount: BigInteger,
        executionSpec: XcmExecutionSpec
    ): BigDecimal {
        require(originChain.id == originChainId) {
            "XCM origin fee chain object must match originChainId"
        }
        require(originFeeAsset.precision >= 0) { "XCM origin fee asset precision must not be negative" }
        require(originFeeAsset.chainId == originChainId) {
            "XCM origin fee asset chain must match originChainId"
        }
        require(asset.chainId == originChainId) {
            "XCM transfer asset must belong to originChainId"
        }
        requireExecutionSpecMatchesAsset(asset, executionSpec)
        val provider = requireKeypairProvider(originChainId)
        val call = buildTransferCall(executionSpec, address, amount)

        val estimatedOriginFeePlancks = submitter.estimateFee(
            chain = originChain,
            accountId = ByteArray(originChain.expectedAccountIdSize()),
            keypairProvider = provider,
            call = call
        )

        return BigDecimal(estimatedOriginFeePlancks, originFeeAsset.precision)
    }

    fun buildTransferCall(
        executionSpec: XcmExecutionSpec,
        recipientAddress: String,
        amount: BigInteger
    ): XcmExtrinsicCall {
        requireSingleAssetExecutionSpec(executionSpec)
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

    private fun requireSingleAssetExecutionSpec(executionSpec: XcmExecutionSpec) {
        require(executionSpec.bridge == null) {
            "XCM bridge execution is unsupported until the production transfer engine consumes bridge fee semantics"
        }
        require(executionSpec.xcmVersion in supportedXcmVersions) {
            "XCM execution spec xcmVersion must be one of ${supportedXcmVersions.joinToString()}"
        }
        require(executionSpec.feeAssetItem == 0) {
            "XCM single-asset execution feeAssetItem must be zero"
        }
        require(executionSpec.feeAssetLocation == executionSpec.assetLocation) {
            "XCM single-asset execution feeAssetLocation must equal assetLocation"
        }
        val accountJunctionTypes = setOf(XcmJunctionType.ACCOUNT_ID32, XcmJunctionType.ACCOUNT_KEY20)
        listOf(
            "destinationLocation" to executionSpec.destinationLocation,
            "assetLocation" to executionSpec.assetLocation,
            "feeAssetLocation" to executionSpec.feeAssetLocation
        ).forEach { (fieldName, location) ->
            require(location.junctions.none { it.type in accountJunctionTypes }) {
                "XCM execution spec $fieldName must not contain recipient authority"
            }
        }
        val beneficiaryAccounts = executionSpec.beneficiaryLocation.junctions
            .filter { it.type in accountJunctionTypes }
        require(beneficiaryAccounts.size == 1) {
            "XCM execution spec beneficiaryLocation must contain exactly one recipient authority"
        }
        val beneficiaryAccount = beneficiaryAccounts.single()
        val expectedAuthority = when (beneficiaryAccount.type) {
            XcmJunctionType.ACCOUNT_ID32 -> "{network: Any, id: <account>}"
            XcmJunctionType.ACCOUNT_KEY20 -> "{network: Any, key: <account>}"
            else -> error("unreachable beneficiary account type")
        }
        require(beneficiaryAccount.value == expectedAuthority) {
            "XCM execution spec beneficiary account must use exact $expectedAuthority recipient authority"
        }
    }

    private fun requireExecutionSpecMatchesAsset(asset: Asset, executionSpec: XcmExecutionSpec) {
        requireSingleAssetExecutionSpec(executionSpec)
        require(asset.symbol.normalizedAssetSymbol() == executionSpec.destinationFee.assetSymbol.normalizedAssetSymbol()) {
            "XCM execution spec for ${executionSpec.destinationFee.assetSymbol} does not match transfer asset ${asset.symbol}"
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
                        "id" to assetId(executionSpec, recipientAddress),
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
        require(executionSpec.beneficiaryLocation.isRecipientAt(executionSpec.destinationLocation)) {
            "XCM XTokens beneficiaryLocation must be destinationLocation followed by the recipient account"
        }

        return XcmExtrinsicCall(
            moduleName = executionSpec.palletName,
            callName = executionSpec.callName,
            arguments = linkedMapOf(
                "asset" to versioned(
                    executionSpec,
                    mapOf(
                        "id" to assetId(executionSpec, recipientAddress),
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

    private fun Chain.expectedAccountIdSize(): Int =
        if (isEthereumBased) ETHEREUM_ACCOUNT_ID_SIZE_BYTES else SUBSTRATE_ACCOUNT_ID_SIZE_BYTES

    private fun versionedMultiLocation(
        executionSpec: XcmExecutionSpec,
        location: XcmMultiLocationSpec,
        recipientAddress: String
    ): Map<String, Any?> = versioned(executionSpec, multiLocation(location, recipientAddress, executionSpec.xcmVersion))

    // The asset id is not a versioned location: V3 wraps the location in `AssetId::Concrete`, while V4 and
    // newer use the location itself (inside a one-field newtype that the runtime conversion resolves from
    // metadata). Wrapping it in a version variant, as a versioned location is, does not decode on any runtime.
    private fun assetId(executionSpec: XcmExecutionSpec, recipientAddress: String): Any {
        val location = multiLocation(executionSpec.assetLocation, recipientAddress, executionSpec.xcmVersion)
        return if (xcmVersionVariant(executionSpec.xcmVersion) == "V3") mapOf("Concrete" to location) else location
    }

    private fun versioned(executionSpec: XcmExecutionSpec, value: Any?): Map<String, Any?> {
        return mapOf(xcmVersionVariant(executionSpec.xcmVersion) to value)
    }

    private fun xcmVersionVariant(value: String): String {
        val version = value.trim()
        require(version.lowercase(Locale.US) in supportedXcmVersions) {
            "XCM version must be one of ${supportedXcmVersions.joinToString()}"
        }
        return version.uppercase(Locale.US)
    }

    private fun multiLocation(
        location: XcmMultiLocationSpec,
        recipientAddress: String,
        xcmVersion: String
    ): Map<String, Any?> {
        val junctions = location.junctions.map { it.toRuntimeJunction(recipientAddress) }
        val interior: Any = when {
            junctions.isEmpty() -> "Here"
            // V3 defines X1 as a single junction; V4 and newer define every X<n> as an array of n junctions.
            junctions.size == 1 && xcmVersionVariant(xcmVersion) == "V3" -> mapOf("X1" to junctions.single())
            else -> mapOf("X${junctions.size}" to junctions)
        }

        return mapOf(
            "parents" to location.parents,
            "interior" to interior
        )
    }

    private fun XcmJunctionSpec.toRuntimeJunction(recipientAddress: String): Map<String, Any?> {
        return when (type) {
            XcmJunctionType.PARACHAIN -> mapOf("Parachain" to value!!.toBigInteger())
            // The reviewed spec text `{network: Any, id: <account>}` only fixes which account field carries the
            // recipient. `Any` is the V1/V2 spelling of "no network restriction", which V3 and newer encode as
            // a missing (None) network, so only the account bytes are passed on.
            XcmJunctionType.ACCOUNT_ID32 -> mapOf("AccountId32" to mapOf("id" to recipientAddress))
            XcmJunctionType.ACCOUNT_KEY20 -> mapOf("AccountKey20" to mapOf("key" to recipientAddress))
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
        const val SUBSTRATE_ACCOUNT_ID_SIZE_BYTES = 32
        const val ETHEREUM_ACCOUNT_ID_SIZE_BYTES = 20
    }
}
