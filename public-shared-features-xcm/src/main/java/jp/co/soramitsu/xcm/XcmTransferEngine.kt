package jp.co.soramitsu.xcm

import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.core.models.ChainIdWithMetadata
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.xcm.domain.XcmExecutionSpec
import java.math.BigDecimal
import java.math.BigInteger

class XcmTransferRequest(
    val originChain: Chain,
    val destinationChain: Chain,
    val asset: Asset,
    val senderAccountId: ByteArray,
    val recipientAddress: String,
    val amount: BigInteger,
    val executionSpec: XcmExecutionSpec
)

interface XcmTransferEngine {
    val isAvailable: Boolean

    fun updateKeypairProvider(chainId: ChainId, keypairProvider: Any)

    fun addPreloadedMetadata(vararg chainMetadatas: ChainIdWithMetadata)

    suspend fun transfer(request: XcmTransferRequest): String

    suspend fun getDestinationFee(
        originChainId: ChainId,
        destinationChainId: ChainId,
        asset: Asset,
        executionSpec: XcmExecutionSpec
    ): BigDecimal

    @Suppress("LongParameterList")
    suspend fun getOriginFee(
        originChain: Chain,
        originChainId: ChainId,
        destinationChainId: ChainId,
        asset: Asset,
        originFeeAsset: Asset,
        address: String,
        amount: BigInteger,
        executionSpec: XcmExecutionSpec
    ): BigDecimal
}

/**
 * Keeps reviewed routes and fee quotes available while enforcing both the compiled submission
 * permission and the runtime mutation switch before entering the signing/submission delegate.
 *
 * The switch is evaluated for every transfer rather than captured during dependency injection, so
 * a remote kill-switch update takes effect without recreating the authenticated graph or process.
 */
class MutationGuardedXcmTransferEngine(
    private val delegate: XcmTransferEngine,
    private val transfersEnabled: Boolean,
    private val mutationsEnabled: () -> Boolean,
    private val mutationsDisabledReason: () -> String = {
        "Reviewed XCM actions are temporarily disabled."
    }
) : XcmTransferEngine {

    override val isAvailable: Boolean
        get() = delegate.isAvailable

    override fun updateKeypairProvider(chainId: ChainId, keypairProvider: Any) {
        delegate.updateKeypairProvider(chainId, keypairProvider)
    }

    override fun addPreloadedMetadata(vararg chainMetadatas: ChainIdWithMetadata) {
        delegate.addPreloadedMetadata(*chainMetadatas)
    }

    override suspend fun transfer(request: XcmTransferRequest): String {
        check(transfersEnabled) { "Reviewed XCM actions are unavailable in this build." }
        check(mutationsEnabled()) { mutationsDisabledReason() }
        return delegate.transfer(request)
    }

    override suspend fun getDestinationFee(
        originChainId: ChainId,
        destinationChainId: ChainId,
        asset: Asset,
        executionSpec: XcmExecutionSpec
    ): BigDecimal = delegate.getDestinationFee(
        originChainId = originChainId,
        destinationChainId = destinationChainId,
        asset = asset,
        executionSpec = executionSpec
    )

    override suspend fun getOriginFee(
        originChain: Chain,
        originChainId: ChainId,
        destinationChainId: ChainId,
        asset: Asset,
        originFeeAsset: Asset,
        address: String,
        amount: BigInteger,
        executionSpec: XcmExecutionSpec
    ): BigDecimal = delegate.getOriginFee(
        originChain = originChain,
        originChainId = originChainId,
        destinationChainId = destinationChainId,
        asset = asset,
        originFeeAsset = originFeeAsset,
        address = address,
        amount = amount,
        executionSpec = executionSpec
    )
}

object UnavailableXcmTransferEngine : XcmTransferEngine {
    override val isAvailable: Boolean = false

    override fun updateKeypairProvider(chainId: ChainId, keypairProvider: Any) = Unit

    override fun addPreloadedMetadata(vararg chainMetadatas: ChainIdWithMetadata) = Unit

    override suspend fun transfer(request: XcmTransferRequest): String = throw xcmUnavailable()

    override suspend fun getDestinationFee(
        originChainId: ChainId,
        destinationChainId: ChainId,
        asset: Asset,
        executionSpec: XcmExecutionSpec
    ): BigDecimal = throw xcmUnavailable()

    override suspend fun getOriginFee(
        originChain: Chain,
        originChainId: ChainId,
        destinationChainId: ChainId,
        asset: Asset,
        originFeeAsset: Asset,
        address: String,
        amount: BigInteger,
        executionSpec: XcmExecutionSpec
    ): BigDecimal = throw xcmUnavailable()

    private fun xcmUnavailable() = UnsupportedOperationException(
        "XCM transfers are unavailable until an open-source transfer engine is configured"
    )
}
