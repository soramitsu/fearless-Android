package jp.co.soramitsu.xcm

import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.core.models.ChainIdWithMetadata
import jp.co.soramitsu.core.utils.removedXcPrefix
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.xcm.domain.XcmEntitiesFetcher
import jp.co.soramitsu.xcm.domain.XcmExecutableRoute
import jp.co.soramitsu.xcm.domain.XcmJunctionType
import java.math.BigDecimal
import java.math.BigInteger

@Suppress("FunctionOnlyReturningConstant", "UnusedParameter")
class XcmService internal constructor(
    private val xcmEntitiesFetcher: XcmEntitiesFetcher,
    private val transferEngine: XcmTransferEngine = UnavailableXcmTransferEngine
) {
    constructor(
        chainRegistry: ChainRegistry,
        transferEngine: XcmTransferEngine = UnavailableXcmTransferEngine
    ) : this(XcmEntitiesFetcher(chainRegistry), transferEngine)

    fun updateKeypairProvider(chainId: ChainId, keypairProvider: Any) {
        transferEngine.updateKeypairProvider(chainId, keypairProvider)
    }

    fun addPreloadedMetadata(vararg chainMetadatas: ChainIdWithMetadata) {
        transferEngine.addPreloadedMetadata(*chainMetadatas)
    }

    suspend fun transfer(
        originChain: Chain,
        destinationChain: Chain,
        asset: Asset,
        senderAccountId: ByteArray,
        address: String,
        amount: BigInteger
    ): String {
        val executableRoute = validateRouteAndAmount(
            originChain = originChain,
            destinationChain = destinationChain,
            asset = asset,
            amount = amount
        )
        require(senderAccountId.isNotEmpty()) { "XCM sender account id must not be empty" }
        require(address.isNotBlank()) { "XCM recipient address must not be blank" }
        validateRecipientAddress(address, executableRoute)

        return transferEngine.transfer(
            XcmTransferRequest(
                originChain = originChain,
                destinationChain = destinationChain,
                asset = asset,
                senderAccountId = senderAccountId,
                recipientAddress = address,
                amount = amount,
                executionSpec = executableRoute.executionSpec
            )
        )
    }

    suspend fun getXcmDestinationFee(
        originChainId: ChainId,
        destinationChainId: ChainId,
        asset: Asset
    ): BigDecimal {
        require(originChainId.isNotBlank()) { "XCM origin chain id must not be blank" }
        require(destinationChainId.isNotBlank()) { "XCM destination chain id must not be blank" }
        require(originChainId != destinationChainId) { "XCM origin and destination chains must be different" }
        require(asset.symbol.isNotBlank()) { "XCM asset symbol must not be blank" }
        val executableRoute = requireSupportedRoute(originChainId, destinationChainId, asset.symbol, amount = null)

        return transferEngine.getDestinationFee(
            originChainId = originChainId,
            destinationChainId = destinationChainId,
            asset = asset,
            executionSpec = executableRoute.executionSpec
        )
    }

    suspend fun getXcmOriginFee(
        originChain: Chain,
        destinationChainId: ChainId,
        asset: Asset,
        originFeeAsset: Asset = asset,
        address: String,
        amount: BigInteger
    ): BigDecimal {
        require(address.isNotBlank()) { "XCM fee recipient address must not be blank" }
        require(amount > BigInteger.ZERO) { "XCM fee amount must be greater than zero" }
        val executableRoute = requireSupportedRoute(originChain.id, destinationChainId, asset.symbol, amount)
        validateRecipientAddress(address, executableRoute)

        return transferEngine.getOriginFee(
            originChain = originChain,
            originChainId = originChain.id,
            destinationChainId,
            asset,
            originFeeAsset,
            address,
            amount,
            executableRoute.executionSpec
        )
    }

    suspend fun getAmountMinLimit(
        originChainId: ChainId,
        destinationChainId: ChainId,
        asset: Asset
    ): BigInteger? = xcmEntitiesFetcher.getMinAmount(originChainId, destinationChainId, asset.symbol)

    suspend fun isXcmSupportAsset(originChainId: String, assetSymbol: String): Boolean {
        if (!transferEngine.isAvailable || originChainId.isBlank() || assetSymbol.isBlank()) return false

        return xcmEntitiesFetcher.hasExecutableRouteAsset(originChainId, assetSymbol.removedXcPrefix())
    }

    private suspend fun validateRouteAndAmount(
        originChain: Chain,
        destinationChain: Chain,
        asset: Asset,
        amount: BigInteger
    ): XcmExecutableRoute {
        require(originChain.id.isNotBlank()) { "XCM origin chain id must not be blank" }
        require(destinationChain.id.isNotBlank()) { "XCM destination chain id must not be blank" }
        require(originChain.id != destinationChain.id) { "XCM origin and destination chains must be different" }
        require(amount > BigInteger.ZERO) { "XCM transfer amount must be greater than zero" }

        return requireSupportedRoute(originChain.id, destinationChain.id, asset.symbol, amount)
    }

    private suspend fun requireSupportedRoute(
        originChainId: ChainId,
        destinationChainId: ChainId,
        assetSymbol: String,
        amount: BigInteger?
    ): XcmExecutableRoute {
        require(originChainId.isNotBlank()) { "XCM origin chain id must not be blank" }
        require(destinationChainId.isNotBlank()) { "XCM destination chain id must not be blank" }
        require(assetSymbol.isNotBlank()) { "XCM asset symbol must not be blank" }

        val executableRoute = xcmEntitiesFetcher.getExecutableRoute(originChainId, destinationChainId, assetSymbol)
            ?: throw IllegalArgumentException(
                "XCM route does not support $assetSymbol from $originChainId to $destinationChainId"
            )
        if (amount != null) {
            val minAmount = executableRoute.asset.minAmount
            require(minAmount == null || amount >= minAmount) {
                "XCM amount $amount is below minAmount $minAmount for $assetSymbol from $originChainId to $destinationChainId"
            }
        }

        return executableRoute
    }

    private fun validateRecipientAddress(address: String, executableRoute: XcmExecutableRoute) {
        val beneficiaryJunctions = executableRoute.executionSpec.beneficiaryLocation.junctions
        if (beneficiaryJunctions.any { it.type == XcmJunctionType.ACCOUNT_KEY20 }) {
            require(EVM_ADDRESS_PATTERN.matches(address.trim())) {
                "XCM AccountKey20 recipient address must be a 0x-prefixed 20-byte hex address"
            }
        }
    }

    private companion object {
        val EVM_ADDRESS_PATTERN = Regex("^0x[0-9a-fA-F]{40}$")
    }
}
