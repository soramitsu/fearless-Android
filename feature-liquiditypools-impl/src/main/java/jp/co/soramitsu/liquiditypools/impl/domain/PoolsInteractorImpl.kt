package jp.co.soramitsu.liquiditypools.impl.domain

import java.math.BigDecimal
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.data.secrets.v1.Keypair
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
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
import kotlinx.coroutines.flow.Flow

class PoolsInteractorImpl(
    private val polkaswapRepository: PolkaswapRepository,
    private val accountRepository: AccountRepository,
    private val polkaswapInteractor: PolkaswapInteractor,
    private val chainRegistry: ChainRegistry,
    private val keypairProvider: KeypairProvider,
) : PoolsInteractor {

    override suspend fun getBasicPools(chainId: ChainId): List<BasicPoolData> {
        return polkaswapRepository.getBasicPools(chainId)
    }

    override fun subscribePoolsCacheOfAccount(address: String): Flow<List<CommonPoolData>> {
        return polkaswapRepository.subscribePools(address)
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
        tokenFrom: Asset,
        tokenTo: Asset,
        tokenFromAmount: BigDecimal,
        tokenToAmount: BigDecimal,
        pairEnabled: Boolean,
        pairPresented: Boolean,
        slippageTolerance: Double
    ): BigDecimal? {
        return polkaswapRepository.calcAddLiquidityNetworkFee(
            chainId,
            address,
            tokenFrom,
            tokenTo,
            tokenFromAmount,
            tokenToAmount,
            pairEnabled,
            pairPresented,
            slippageTolerance,
        )
    }

    override suspend fun calcRemoveLiquidityNetworkFee(
        chainId: ChainId,
        tokenFrom: Asset,
        tokenTo: Asset,
    ): BigDecimal? {
        return polkaswapRepository.calcRemoveLiquidityNetworkFee(
            chainId,
            tokenFrom,
            tokenTo
        )
    }

//    override suspend fun updateApy() {
//        polkaswapInteractor.updatePoolsSbApy()
//    }

    override fun getPoolStrategicBonusAPY(reserveAccountOfPool: String): Double? =
        polkaswapInteractor.getPoolStrategicBonusAPY(reserveAccountOfPool)

    override suspend fun isPairEnabled(chainId: ChainId, inputTokenId: String, outputTokenId: String): Boolean {
        val dexId = polkaswapRepository.getPoolBaseTokenDexId(chainId, inputTokenId)
        return polkaswapRepository.isPairAvailable(
            chainId,
            inputTokenId,
            outputTokenId,
            dexId
        )
    }

    override suspend fun observeAddLiquidity(
        chainId: ChainId,
        tokenFrom: Asset,
        tokenTo: Asset,
        amountFrom: BigDecimal,
        amountTo: BigDecimal,
        enabled: Boolean,
        presented: Boolean,
        slippageTolerance: Double
    ): String {
        val soraChain = chainRegistry.getChain(chainId)
        val metaAccount = accountRepository.getSelectedMetaAccount()
        val address = metaAccount.address(soraChain) ?: return ""

        val networkFee = calcAddLiquidityNetworkFee(
            chainId,
            address,
            tokenFrom,
            tokenTo,
            amountFrom,
            amountTo,
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
            tokenFrom,
            tokenTo,
            amountFrom,
            amountTo,
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
