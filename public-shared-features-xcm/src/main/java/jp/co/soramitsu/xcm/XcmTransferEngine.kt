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
