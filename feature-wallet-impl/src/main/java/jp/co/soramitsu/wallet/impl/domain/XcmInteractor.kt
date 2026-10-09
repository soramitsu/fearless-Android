package jp.co.soramitsu.wallet.impl.domain

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.common.utils.combineToPair
import jp.co.soramitsu.common.utils.isZero
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.core.models.ChainIdWithMetadata
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair as SigningKeypair
import jp.co.soramitsu.core.utils.removedXcPrefix
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.ext.fakeAddress
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeFilesCache
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.domain.model.CrossChainTransfer
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import jp.co.soramitsu.xcm.XcmService
import jp.co.soramitsu.xcm.domain.XcmEntitiesFetcher
import jp.co.soramitsu.xcm.domain.XcmAsset
import jp.co.soramitsu.xcm.domain.CrossChainAssetIdentity
import jp.co.soramitsu.xcm.domain.CrossChainRouteProviderRegistry
import jp.co.soramitsu.xcm.domain.CrossChainRouteQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import jp.co.soramitsu.core.models.Asset as CoreAsset

class XcmInteractor(
    private val walletInteractor: WalletInteractor,
    private val chainRegistry: ChainRegistry,
    private val currentAccountAddress: CurrentAccountAddressUseCase,
    private val xcmEntitiesFetcher: XcmEntitiesFetcher,
    private val accountInteractor: AccountInteractor,
    private val runtimeFilesCache: RuntimeFilesCache,
    private val xcmService: XcmService,
    private val featureToggleStore: ProductFeatureToggleStore,
    private val routeProviderRegistry: CrossChainRouteProviderRegistry? = null,
    private val signingKeypairProvider: KeypairProvider? = null
) {

    suspend fun prepareDataForChains(originChainId: ChainId, destinationChainId: ChainId) = withContext(Dispatchers.Default) {
        val metaAccount = accountInteractor.selectedMetaAccount()
        val cryptoType = metaAccount.substrateCryptoType ?: throw IllegalStateException("Can't do XCM without susbtrate keypair")
        xcmService.updateKeypairProvider(
            chainId = originChainId,
            keypairProvider = signingKeypairProvider ?: object : KeypairProvider {
                override suspend fun getCryptoTypeFor(chain: IChain, accountId: ByteArray) = cryptoType
                override suspend fun getKeypairFor(chain: IChain, accountId: ByteArray): SigningKeypair =
                    error("An authorized signing provider is required")
            }
        )
        val fromChainMetadata = ChainIdWithMetadata(
            chainId = originChainId,
            metadata = runCatching { runtimeFilesCache.getChainMetadata(originChainId) }.getOrNull()
        )
        val toChainMetadata = ChainIdWithMetadata(
            chainId = destinationChainId,
            metadata = kotlin.runCatching { runtimeFilesCache.getChainMetadata(destinationChainId) }.getOrNull()
        )
        xcmService.addPreloadedMetadata(fromChainMetadata, toChainMetadata)
    }

    fun getAvailableAssetsFlow(originChainId: ChainId?): Flow<List<AssetWithStatus>> {
        return combineToPair(walletInteractor.assetsFlow(), getAvailableAssetsFlowInternal(originChainId))
            .map { (assets, availableRouteAssets) ->
                assets.filter { assetWithStatus ->
                    val asset = assetWithStatus.asset.token.configuration
                    availableRouteAssets.xcmAssets.any { approved ->
                        asset.chainId == approved.originChainId &&
                            asset.id == approved.originAssetId &&
                            asset.precision == approved.originAssetPrecision &&
                            asset.symbol.normalizedXcmSymbol() == approved.symbol
                    } || availableRouteAssets.providerAssets.any { reviewed ->
                        asset.chainId == reviewed.originNetworkId &&
                            asset.id == reviewed.originAssetId &&
                            asset.symbol.uppercase() == reviewed.symbol
                    }
                }
            }
    }

    private fun getAvailableAssetsFlowInternal(originChainId: ChainId?) = flow {
        val availableXcmAssets = xcmEntitiesFetcher.getAvailableAssets(
            originChainId = originChainId,
            destinationChainId = null
        )
        val availableProviderAssets = routeProviderRegistry?.capabilities(
            CrossChainRouteQuery(originNetworkId = originChainId)
        ).orEmpty().filter { it.hasRoute }.flatMapTo(linkedSetOf()) { it.supportedAssets }
        emit(AvailableRouteAssets(availableXcmAssets, availableProviderAssets))
    }

    private data class AvailableRouteAssets(
        val xcmAssets: List<XcmAsset>,
        val providerAssets: Set<CrossChainAssetIdentity>
    )

    private fun String.normalizedXcmSymbol(): String = trim()
        .lowercase()
        .removedXcPrefix()
        .uppercase()

    suspend fun performCrossChainTransfer(transfer: CrossChainTransfer): Result<String> {
        return runCatching {
            check(featureToggleStore.xcmMutationsEnabled) {
                "Cross-chain transfers are temporarily disabled."
            }
            val originChain = chainRegistry.getChain(transfer.originChainId)
            val destinationChain = chainRegistry.getChain(transfer.destinationChainId)
            val selfAddress = currentAccountAddress(originChain.id) ?: throw IllegalStateException("No self address")

            val exactAmountInPlanks = exactXcmAmountInPlanks(transfer)

            xcmService.transfer(
                originChain = originChain,
                destinationChain = destinationChain,
                asset = transfer.chainAsset,
                senderAccountId = originChain.accountIdOf(selfAddress),
                address = transfer.recipient,
                amount = exactAmountInPlanks
            )
        }
    }

    suspend fun getDestinationFee(
        originChainId: ChainId,
        destinationChainId: ChainId,
        tokenConfiguration: Asset
    ): BigDecimal? {
        return runCatching {
            xcmService.getXcmDestinationFee(
                originChainId = originChainId,
                destinationChainId = destinationChainId,
                asset = tokenConfiguration
            )
        }.getOrNull()
    }

    suspend fun getOriginFee(
        originNetworkId: ChainId,
        destinationNetworkId: ChainId,
        asset: CoreAsset,
        amount: BigDecimal
    ): BigDecimal? {
        return runCatching {
            val originChain = chainRegistry.getChain(originNetworkId)
            val destinationChain = chainRegistry.getChain(destinationNetworkId)
            xcmService.getXcmOriginFee(
                originChain = originChain,
                destinationChainId = destinationNetworkId,
                asset = asset,
                originFeeAsset = originChain.utilityAsset ?: asset,
                address = destinationChain.fakeAddress(),
                amount = asset.getPlanksFromAmountForOriginFee(amount)
            )
        }.getOrNull()
    }

    suspend fun getAmountMinLimit(originChainId: ChainId, destinationChainId: ChainId, asset: Asset) =
        xcmService.getAmountMinLimit(originChainId, destinationChainId, asset)

    private fun Asset.getPlanksFromAmountForOriginFee(amount: BigDecimal): BigInteger {
        val rawAmountInPlanks = planksFromAmount(amount)
        if (rawAmountInPlanks.isZero()) {
            return BigInteger.ONE
        }

        return rawAmountInPlanks
    }
}

/** The SORA KSM destination currently supports twelve fractional digits. Reject, never round. */
internal fun exactXcmAmountInPlanks(transfer: CrossChainTransfer): BigInteger {
    if (transfer.originChainId == soraMainChainId &&
        transfer.chainAsset.currencyId == SORA_KSM_CURRENCY_ID
    ) {
        require(transfer.amount.stripTrailingZeros().scale() <= 12 &&
            transfer.destinationFee.stripTrailingZeros().scale() <= 12) {
            "SORA KSM XCM amount and destination fee must use at most 12 fractional digits"
        }
    }
    return transfer.exactFullAmountInPlanks()
}

/** The quoted origin fee is denominated in the origin utility asset, not the transfer asset. */
internal fun exactXcmOriginFeeInPlanks(
    originChainId: ChainId,
    expectedUtilityAssetId: String,
    utilityAsset: Asset,
    quotedFee: BigDecimal
): BigInteger {
    require(utilityAsset.chainId == originChainId && utilityAsset.id == expectedUtilityAssetId) {
        "XCM origin fee asset does not match the origin utility asset"
    }
    require(utilityAsset.precision >= 0 && quotedFee.signum() >= 0) {
        "XCM origin fee or precision is invalid"
    }
    return quotedFee.scaleByPowerOfTen(utilityAsset.precision).toBigIntegerExact()
}

private const val SORA_KSM_CURRENCY_ID =
    "0x00117b0fa73c4672e03a7d9d774e3b3f91beb893e93d9a8d0430295f44225db8"
