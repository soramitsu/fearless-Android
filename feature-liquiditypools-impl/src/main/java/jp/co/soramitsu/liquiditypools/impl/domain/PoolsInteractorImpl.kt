package jp.co.soramitsu.liquiditypools.impl.domain

import java.math.BigDecimal
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.data.secrets.v1.Keypair
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.liquiditypools.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.polkaswap.api.data.PolkaswapRepository
import jp.co.soramitsu.polkaswap.api.data.PoolDataDto
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.api.domain.models.BasicPoolData
import jp.co.soramitsu.polkaswap.api.domain.models.CommonPoolData
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraTestChainId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapNotNull

class PoolsInteractorImpl(
    private val polkaswapRepository: PolkaswapRepository,
    private val accountRepository: AccountRepository,
    private val polkaswapInteractor: PolkaswapInteractor,
    private val chainRegistry: ChainRegistry,
    private val keypairProvider: KeypairProvider,
) : PoolsInteractor {
    override val poolsChainId = soraTestChainId

    override suspend fun getBasicPools(chainId: ChainId): List<BasicPoolData> {
        return polkaswapRepository.getBasicPools(chainId)
    }

//    override fun subscribePoolsCache(): Flow<List<BasicPoolData>> {
//        return polkaswapRepository.subscribePools()
//    }

    override fun subscribePoolsCacheOfAccount(address: String): Flow<List<CommonPoolData>> {
        return polkaswapRepository.subscribePools(address)
    }

    private val soraPoolsAddressFlow = flowOf {
        val meta = accountRepository.getSelectedMetaAccount()
        println("!!! accountflow poolsChainId = $poolsChainId")
        val chain = accountRepository.getChain(poolsChainId)
        meta.address(chain)
    }.mapNotNull { it }
        .distinctUntilChanged()

    override fun subscribePoolsCacheCurrentAccount(): Flow<List<CommonPoolData>> {
        return soraPoolsAddressFlow.flatMapLatest { address ->
            polkaswapRepository.subscribePools(address)
        }

    }

    override suspend fun getPoolData(chainId: ChainId, baseTokenId: String, targetTokenId: String): Flow<CommonPoolData> {
        val address = accountRepository.getSelectedAccount(chainId).address
        return polkaswapRepository.subscribePool(address, baseTokenId, targetTokenId)
    }

//    override suspend fun getPoolCacheOfCurAccount(
//        tokenFromId: String,
//        tokenToId: String
//    ): CommonUserPoolData? {
//        val wallet = accountRepository.getSelectedMetaAccount()
//        val chainId = polkaswapInteractor.polkaswapChainId
//        val chain = chainRegistry.getChain(chainId)
//        val address = wallet.address(chain)
//        return polkaswapRepository.getPoolOfAccount(address, tokenFromId, tokenToId, chainId)
//    }

    override suspend fun getUserPoolData(
        chainId: ChainId,
        address: String,
        baseTokenId: String,
        tokenId: ByteArray
    ): PoolDataDto? {
//        return polkaswapRepository.getPoolOfAccount(address, baseTokenId, tokenId.toHexString(true), polkaswapInteractor.polkaswapChainId)
        return polkaswapRepository.getUserPoolData(chainId, address, baseTokenId, tokenId)

    }

    override suspend fun calcAddLiquidityNetworkFee(
        chainId: ChainId,
        address: String,
        tokenBase: Asset,
        tokenTarget: Asset,
        tokenBaseAmount: BigDecimal,
        tokenTargetAmount: BigDecimal,
        pairEnabled: Boolean,
        pairPresented: Boolean,
        slippageTolerance: Double
    ): BigDecimal? {
        return polkaswapRepository.calcAddLiquidityNetworkFee(
            chainId,
            address,
            tokenBase,
            tokenTarget,
            tokenBaseAmount,
            tokenTargetAmount,
            pairEnabled,
            pairPresented,
            slippageTolerance,
        )
    }

    override suspend fun calcRemoveLiquidityNetworkFee(
        chainId: ChainId,
        tokenBase: Asset,
        tokenTarget: Asset,
    ): BigDecimal? {
        return polkaswapRepository.calcRemoveLiquidityNetworkFee(
            chainId,
            tokenBase,
            tokenTarget
        )
    }

    override fun getPoolStrategicBonusAPY(reserveAccountOfPool: String): Double? =
        polkaswapInteractor.getPoolStrategicBonusAPY(reserveAccountOfPool)

    override suspend fun isPairEnabled(chainId: ChainId, baseTokenId: String, targetTokenId: String): Boolean {
        val dexId = polkaswapRepository.getPoolBaseTokenDexId(chainId, baseTokenId)
        return polkaswapRepository.isPairAvailable(
            chainId,
            baseTokenId,
            targetTokenId,
            dexId
        )
    }

    override suspend fun observeRemoveLiquidity(
    chainId: ChainId,
    tokenBase: Asset,
    tokenTarget: Asset,
    markerAssetDesired: BigDecimal,
    firstAmountMin: BigDecimal,
    secondAmountMin: BigDecimal,
    networkFee: BigDecimal
    ): String {
        val address = accountRepository.getSelectedAccount(chainId).address

        val status = polkaswapRepository.observeRemoveLiquidity(
           chainId,
            tokenBase,
            tokenTarget,
            markerAssetDesired,
            firstAmountMin,
            secondAmountMin,
        )


    return status?.getOrNull() ?: ""
    }

    override suspend fun observeAddLiquidity(
        chainId: ChainId,
        tokenBase: Asset,
        tokenTarget: Asset,
        amountBase: BigDecimal,
        amountTarget: BigDecimal,
        enabled: Boolean,
        presented: Boolean,
        slippageTolerance: Double
    ): String {
        val metaAccount = accountRepository.getSelectedMetaAccount()
        val address = accountRepository.getSelectedAccount(chainId).address

        val networkFee = calcAddLiquidityNetworkFee(
            chainId,
            address,
            tokenBase,
            tokenTarget,
            amountBase,
            amountTarget,
            enabled,
            presented,
            slippageTolerance
        )

        val secrets = accountRepository.getMetaAccountSecrets(metaAccount.id)?.get(MetaAccountSecrets.SubstrateKeypair)
        requireNotNull(secrets)
        val private = secrets[KeyPairSchema.PrivateKey]
        val public = secrets[KeyPairSchema.PublicKey]
        val nonce = secrets[KeyPairSchema.Nonce]
        val keypair = Keypair(public, private, nonce)

        val status = polkaswapRepository.observeAddLiquidity(
            chainId,
            address,
            keypair,
            tokenBase,
            tokenTarget,
            amountBase,
            amountTarget,
            enabled,
            presented,
            slippageTolerance
        )



        if (status != null) {
//            transactionHistoryRepository.saveTransaction(
//                transactionBuilder.buildLiquidity(
//                    txHash = status.txHash,
//                    blockHash = status.blockHash,
//                    fee = networkFee,
//                    status = TransactionStatus.PENDING,
//                    date = Date().time,
//                    token1 = tokenFrom,
//                    token2 = tokenTo,
//                    amount1 = amountFrom,
//                    amount2 = amountTo,
//                    type = TransactionLiquidityType.ADD,
//                )
//            )
        }
        return status?.getOrNull() ?: ""
    }

    override suspend fun updatePools(chainId: ChainId) {
        val address = accountRepository.getSelectedAccount(chainId).address
        polkaswapRepository.updateAccountPools(chainId, address)

        polkaswapRepository.updateBasicPools(chainId)
    }
}
