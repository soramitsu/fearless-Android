package jp.co.soramitsu.wallet.impl.domain

import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.common.data.secrets.v1.Keypair
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.utils.combineToPair
import jp.co.soramitsu.core.extrinsic.keypair_provider.SingleKeypairProvider
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.core.models.ChainIdWithMetadata
import jp.co.soramitsu.core.utils.removedXcPrefix
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.ext.fakeAddress
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeFilesCache
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.domain.model.CrossChainTransfer
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import jp.co.soramitsu.xcm.XcmService
import jp.co.soramitsu.xcm.domain.XcmEntitiesFetcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.core.models.Asset as CoreAsset

class XcmInteractor(
    private val walletInteractor: WalletInteractor,
    private val chainRegistry: ChainRegistry,
    private val currentAccountAddress: CurrentAccountAddressUseCase,
    private val xcmEntitiesFetcher: XcmEntitiesFetcher,
    private val accountInteractor: AccountInteractor,
    private val runtimeFilesCache: RuntimeFilesCache,
    private val xcmService: XcmService
) {

    suspend fun prepareDataForChains(originChainId: ChainId, destinationChainId: ChainId) {
        val metaAccount = accountInteractor.selectedMetaAccount()
        val originChain = chainRegistry.getChain(originChainId)
        val keypairType = if (originChain.isEthereumBased) {
            MetaAccountSecrets.EthereumKeypair
        } else {
            MetaAccountSecrets.SubstrateKeypair
        }
        val secrets = accountInteractor.getMetaAccountSecrets(metaAccount.id)?.get(keypairType)
        requireNotNull(secrets)
        val private = secrets[KeyPairSchema.PrivateKey]
        val public = secrets[KeyPairSchema.PublicKey]
        val nonce = secrets[KeyPairSchema.Nonce]

        xcmService.updateKeypairProvider(
            chainId = originChainId,
            keypairProvider = SingleKeypairProvider(
                keypair = Keypair(public, private, nonce),
                cryptoType = metaAccount.substrateCryptoType
            )
        )
        val fromChainMetadata = ChainIdWithMetadata(
            chainId = originChainId,
            metadata = runtimeFilesCache.getChainMetadata(originChainId)
        )
        val toChainMetadata = ChainIdWithMetadata(
            chainId = destinationChainId,
            metadata = runtimeFilesCache.getChainMetadata(destinationChainId)
        )
        xcmService.addPreloadedMetadata(fromChainMetadata, toChainMetadata)
    }

    fun getAvailableAssetsFlow(originChainId: ChainId?): Flow<List<AssetWithStatus>> {
        return combineToPair(walletInteractor.assetsFlow(), getAvailableAssetSymbolsFlow(originChainId))
            .map { (assets, availableXcmAssetSymbols) ->
                assets.filter {
                    val assetSymbol = it.asset.token.configuration.symbol.uppercase()
                    val removedXcAssetSymbol = assetSymbol.removedXcPrefix()
                    removedXcAssetSymbol in availableXcmAssetSymbols ||
                        assetSymbol in availableXcmAssetSymbols
                }
            }
    }

    private fun getAvailableAssetSymbolsFlow(originChainId: ChainId?): Flow<List<String>> {
        return flow {
            val availableXcmAssetSymbols = xcmEntitiesFetcher.getAvailableAssets(
                originChainId = originChainId,
                destinationChainId = null
            ).map { it.symbol.uppercase() }
            emit(availableXcmAssetSymbols)
        }
    }

    suspend fun performCrossChainTransfer(transfer: CrossChainTransfer): Result<String> {
        return runCatching {
            val originChain = chainRegistry.getChain(transfer.originChainId)
            val destinationChain = chainRegistry.getChain(transfer.destinationChainId)
            val selfAddress = currentAccountAddress(originChain.id) ?: throw IllegalStateException("No self address")

            val ksmInSoraMainnetCurrencyId = "0x00117b0fa73c4672e03a7d9d774e3b3f91beb893e93d9a8d0430295f44225db8"
            // todo remove this sora ksm check when https://github.com/sora-xor/sora2-network/issues/845 will be fixed
            // if we transfer ksm asset from sora network - we have to convert precision 18 to 12
            val roundedAmountInPlanks = if(transfer.originChainId == soraMainChainId && transfer.chainAsset.currencyId == ksmInSoraMainnetCurrencyId) {
                val roundedAmount = transfer.amount.round(MathContext(12, RoundingMode.HALF_EVEN))
                transfer.chainAsset.planksFromAmount(roundedAmount)
            } else {
                transfer.fullAmountInPlanks
            }

            xcmService.transfer(
                originChain = originChain,
                destinationChain = destinationChain,
                asset = transfer.chainAsset,
                senderAccountId = originChain.accountIdOf(selfAddress),
                address = transfer.recipient,
                amount = roundedAmountInPlanks
            )
        }
    }

    suspend fun getDestinationFee(
        destinationChainId: ChainId,
        tokenConfiguration: Asset
    ): BigDecimal? {
        return runCatching {
            xcmService.getXcmDestinationFee(
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
            val chain = chainRegistry.getChain(destinationNetworkId)
            xcmService.getXcmOriginFee(
                originChainId = originNetworkId,
                destinationChainId = destinationNetworkId,
                asset = asset,
                address = chain.fakeAddress(),
                amount = asset.getPlanksFromAmountForOriginFee(amount)
            )
        }.getOrNull()
    }

    private fun Asset.getPlanksFromAmountForOriginFee(amount: BigDecimal): BigInteger {
        val rawAmountInPlanks = planksFromAmount(amount)
        if (rawAmountInPlanks == BigInteger.ZERO) {
            return BigInteger.ONE
        }

        return rawAmountInPlanks
    }
}
