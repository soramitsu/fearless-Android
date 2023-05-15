package jp.co.soramitsu.wallet.impl.domain

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.utils.combineToPair
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.ext.fakeAddress
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeFilesCache
import jp.co.soramitsu.shared_utils.encrypt.keypair.BaseKeypair
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.domain.model.CrossChainTransfer
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import jp.co.soramitsu.xcm_impl.FromChainData
import jp.co.soramitsu.xcm_impl.XcmService
import jp.co.soramitsu.xcm_impl.domain.XcmEntitiesFetcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import jp.co.soramitsu.core.models.Asset as CoreAsset

class XcmInteractor(
    private val walletInteractor: WalletInteractor,
    private val chainRegistry: ChainRegistry,
    private val currentAccountAddress: CurrentAccountAddressUseCase,
    private val xcmEntitiesFetcher: XcmEntitiesFetcher,
    private val accountInteractor: AccountInteractor,
    private val runtimeFilesCache: RuntimeFilesCache
) {

    private val xcmService: MutableStateFlow<XcmService?> = MutableStateFlow(null)
    private val nonNullXcmService = xcmService.filterNotNull()

    suspend fun initXcmService(originChainId: ChainId, destinationChainId: ChainId) {
        xcmService.value = null
        val fromChainData = buildFromChainData(originChainId)
        val destinationChainMetadata = runtimeFilesCache.getChainMetadata(destinationChainId)
        this.xcmService.value = XcmService.create(fromChainData, destinationChainId, destinationChainMetadata)
    }

    private suspend fun buildFromChainData(originChainId: ChainId): FromChainData {
        val metaAccount = accountInteractor.selectedMetaAccount()
        val secrets = accountInteractor.getMetaAccountSecrets(metaAccount.id)?.get(MetaAccountSecrets.SubstrateKeypair)
        requireNotNull(secrets)
        val private = secrets[KeyPairSchema.PrivateKey]
        val public = secrets[KeyPairSchema.PublicKey]

        return FromChainData(
            chain = originChainId,
            cryptoType = metaAccount.substrateCryptoType,
            chainMetadata = runtimeFilesCache.getChainMetadata(originChainId),
            keypair = BaseKeypair(private, public)
        )
    }


    fun xcmAssetsFlow(originChainId: ChainId?): Flow<List<AssetWithStatus>> {
        return combineToPair(walletInteractor.assetsFlow(), getAvailableXcmAssetSymbolsFlow(originChainId))
            .map { (assets, availableXcmAssetSymbols) ->
                assets.filter {
                    val assetSymbol = it.asset.token.configuration.symbol.uppercase()
                    assetSymbol in availableXcmAssetSymbols
                }
            }
    }

    private fun getAvailableXcmAssetSymbolsFlow(originChainId: ChainId?): Flow<List<String>> {
        return flow {
            val availableXcmAssetSymbols = xcmEntitiesFetcher.getAvailableAssets(
                originalChainId = originChainId,
                destinationChainId = null
            ).map { it.uppercase() }
            emit(availableXcmAssetSymbols)
        }
    }

    suspend fun performCrossChainTransfer(
        transfer: CrossChainTransfer,
        fee: BigDecimal,
        tipInPlanks: BigInteger?
    ): Result<String> {
        return runCatching {
            val originChain = chainRegistry.getChain(transfer.originChainId)
            val destinationChain = chainRegistry.getChain(transfer.destinationChainId)
            val selfAddress = currentAccountAddress(originChain.id) ?: throw IllegalStateException("No self address")
            val service = nonNullXcmService.first()
            service.transfer(
                fromChain = originChain,
                toChain = destinationChain,
                asset = transfer.chainAsset,
                senderAccountId = originChain.accountIdOf(selfAddress),
                address = transfer.recipient,
                amount = transfer.fullAmountInPlanks
            )
        }
    }

    suspend fun getXcmDestFee(
        destinationChainId: ChainId,
        tokenSymbol: String
    ): BigDecimal? {
        val r = runCatching {
            val service = nonNullXcmService.first()
            service.getXcmDestFee(
                toChainId = destinationChainId,
                tokenSymbol = tokenSymbol
            )
        }
        return r.getOrNull()
    }

    suspend fun getXcmOrigFee(
        originNetworkId: ChainId,
        destinationNetworkId: ChainId,
        asset: CoreAsset,
        amount: BigDecimal
    ): BigDecimal? {
        val r = runCatching {
            val chain = chainRegistry.getChain(originNetworkId)
            val service = nonNullXcmService.first()
            service.getXcmOrigFee(
                fromChainId = originNetworkId,
                toChainId = destinationNetworkId,
                asset = asset,
                address = chain.fakeAddress(),
                amount = asset.getPlanksFromAmountForXcmOrigFee(amount)
            )
        }
        return r.getOrNull()
    }

    private fun Asset.getPlanksFromAmountForXcmOrigFee(amount: BigDecimal): BigInteger {
        val rawAmountInPlanks = planksFromAmount(amount)
        if (rawAmountInPlanks == BigInteger.ZERO) {
            return BigInteger.ONE
        }

        return rawAmountInPlanks
    }
}
