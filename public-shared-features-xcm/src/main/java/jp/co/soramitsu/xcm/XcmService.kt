package jp.co.soramitsu.xcm

import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.core.models.ChainIdWithMetadata
import jp.co.soramitsu.core.utils.removedXcPrefix
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.addressByte
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.xcm.domain.XcmEntitiesFetcher
import jp.co.soramitsu.xcm.domain.XcmExecutableRoute
import jp.co.soramitsu.xcm.domain.XcmJunctionType
import java.math.BigDecimal
import java.math.BigInteger

@Suppress("FunctionOnlyReturningConstant", "UnusedParameter")
class XcmService(
    private val xcmEntitiesFetcher: XcmEntitiesFetcher,
    private val transferEngine: XcmTransferEngine = UnavailableXcmTransferEngine
) {
    /**
     * Compatibility constructor. Without an immutable APK-owned approval
     * registry, the service remains fail-closed and advertises no XCM routes.
     */
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
        require(executableRoute.originIdentity.matches(originChain)) {
            "XCM origin chain object does not match the approved discovery identity"
        }
        require(executableRoute.destinationIdentity.matches(destinationChain)) {
            "XCM destination chain object does not match the approved discovery identity"
        }
        require(senderAccountId.isNotEmpty()) { "XCM sender account id must not be empty" }
        val expectedSenderAccountIdSize = if (originChain.isEthereumBased) {
            ETHEREUM_ACCOUNT_ID_SIZE_BYTES
        } else {
            SUBSTRATE_ACCOUNT_ID_SIZE_BYTES
        }
        require(senderAccountId.size == expectedSenderAccountIdSize) {
            "XCM sender account id must be $expectedSenderAccountIdSize bytes for the approved origin chain"
        }
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
        val executableRoute = requireSupportedRoute(originChainId, destinationChainId, asset, amount = null)

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
        require(originFeeAsset.chainId == originChain.id) {
            "XCM origin fee asset must belong to the origin chain"
        }
        val executableRoute = requireSupportedRoute(originChain.id, destinationChainId, asset, amount)
        require(executableRoute.originIdentity.matches(originChain)) {
            "XCM origin chain object does not match the approved discovery identity"
        }
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
    ): BigInteger? = requireSupportedRoute(
        originChainId = originChainId,
        destinationChainId = destinationChainId,
        asset = asset,
        amount = null
    ).asset.minAmount

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

        return requireSupportedRoute(originChain.id, destinationChain.id, asset, amount)
    }

    private suspend fun requireSupportedRoute(
        originChainId: ChainId,
        destinationChainId: ChainId,
        asset: Asset,
        amount: BigInteger?
    ): XcmExecutableRoute {
        require(originChainId.isNotBlank()) { "XCM origin chain id must not be blank" }
        require(destinationChainId.isNotBlank()) { "XCM destination chain id must not be blank" }
        require(asset.chainId == originChainId) { "XCM asset must belong to the origin chain" }
        require(asset.symbol.isNotBlank()) { "XCM asset symbol must not be blank" }

        val executableRoute = xcmEntitiesFetcher.getExecutableRoute(originChainId, destinationChainId, asset.symbol)
            ?: throw IllegalArgumentException(
                "XCM route does not support ${asset.symbol} from $originChainId to $destinationChainId"
            )
        require(asset.id == executableRoute.asset.originAssetId) {
            "XCM asset id does not match the APK-approved origin asset"
        }
        require(asset.chainId == executableRoute.asset.originChainId) {
            "XCM asset chain does not match the APK-approved origin asset"
        }
        require(asset.symbol.normalizedXcmSymbol() == executableRoute.asset.symbol) {
            "XCM asset symbol does not match the APK-approved origin asset"
        }
        require(asset.precision == executableRoute.asset.originAssetPrecision) {
            "XCM asset precision does not match the APK-approved origin asset"
        }
        if (amount != null) {
            val minAmount = executableRoute.asset.minAmount
            require(minAmount == null || amount >= minAmount) {
                "XCM amount $amount is below minAmount $minAmount for ${asset.symbol} from $originChainId to $destinationChainId"
            }
        }

        return executableRoute
    }

    private fun validateRecipientAddress(address: String, executableRoute: XcmExecutableRoute) {
        require(address == address.trim()) {
            "XCM recipient address must not contain surrounding whitespace"
        }
        val beneficiaryJunctions = executableRoute.executionSpec.beneficiaryLocation.junctions
        if (beneficiaryJunctions.any { it.type == XcmJunctionType.ACCOUNT_KEY20 }) {
            require(EVM_ADDRESS_PATTERN.matches(address)) {
                "XCM AccountKey20 recipient address must be a 0x-prefixed 20-byte hex address"
            }
        } else {
            val isValidAccountId32 = runCatching {
                val accountId = address.toAccountId()
                accountId.size == SUBSTRATE_ACCOUNT_ID_SIZE_BYTES &&
                    accountId.toAddress(address.addressByte()) == address
            }.getOrDefault(false)
            require(isValidAccountId32) {
                "XCM AccountId32 recipient address must be a valid 32-byte Substrate address"
            }
        }
    }

    private fun String.normalizedXcmSymbol(): String = trim()
        .lowercase()
        .removedXcPrefix()
        .uppercase()

    private companion object {
        const val SUBSTRATE_ACCOUNT_ID_SIZE_BYTES = 32
        const val ETHEREUM_ACCOUNT_ID_SIZE_BYTES = 20
        val EVM_ADDRESS_PATTERN = Regex("^0x[0-9a-fA-F]{40}$")
    }
}
