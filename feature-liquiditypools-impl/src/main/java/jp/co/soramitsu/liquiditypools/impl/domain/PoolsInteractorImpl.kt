package jp.co.soramitsu.liquiditypools.impl.domain

import java.math.BigDecimal
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.data.secrets.v1.Keypair
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.liquiditypools.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.polkaswap.api.data.PolkaswapRepository
import jp.co.soramitsu.polkaswap.api.data.PoolDataDto
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.api.domain.models.BasicPoolData
import jp.co.soramitsu.polkaswap.api.domain.models.CommonUserPoolData
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId

class PoolsInteractorImpl(
    private val polkaswapRepository: PolkaswapRepository,
    private val accountRepository: AccountRepository,
    private val polkaswapInteractor: PolkaswapInteractor,
    private val chainRegistry: ChainRegistry,
    private val keypairProvider: KeypairProvider,
) : PoolsInteractor {

    override suspend fun getBasicPools(): List<BasicPoolData> {
        return polkaswapRepository.getBasicPools()
    }

    override suspend fun getPoolCacheOfCurAccount(
        tokenFromId: String,
        tokenToId: String
    ): CommonUserPoolData? {
        val wallet = accountRepository.getSelectedMetaAccount()
        val chainId = polkaswapInteractor.polkaswapChainId
        val chain = chainRegistry.getChain(chainId)
        val address = wallet.address(chain)
        return polkaswapRepository.getPoolOfAccount(address, tokenFromId, tokenToId, chainId)
    }

    override suspend fun getUserPoolData(
        address: String,
        baseTokenId: String,
        tokenId: ByteArray
    ): PoolDataDto? {
//        return polkaswapRepository.getPoolOfAccount(address, baseTokenId, tokenId.toHexString(true), polkaswapInteractor.polkaswapChainId)
        return polkaswapRepository.getUserPoolData(address, baseTokenId, tokenId)

    }

    override suspend fun calcAddLiquidityNetworkFee(
        address: String,
        tokenFrom: jp.co.soramitsu.core.models.Asset,
        tokenTo: jp.co.soramitsu.core.models.Asset,
        tokenFromAmount: BigDecimal,
        tokenToAmount: BigDecimal,
        pairEnabled: Boolean,
        pairPresented: Boolean,
        slippageTolerance: Double
    ): BigDecimal? {
        return polkaswapRepository.calcAddLiquidityNetworkFee(
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

    override suspend fun updateApy() {
        polkaswapInteractor.updatePoolsSbApy()
    }

    override fun getPoolStrategicBonusAPY(reserveAccountOfPool: String): Double? =
        polkaswapInteractor.getPoolStrategicBonusAPY(reserveAccountOfPool)

    override suspend fun isPairEnabled(inputTokenId: String, outputTokenId: String, accountAddress: String): Boolean {
        val dexId = polkaswapRepository.getPoolBaseTokenDexId(inputTokenId)
        return polkaswapRepository.isPairAvailable(
            soraMainChainId,
            inputTokenId,
            outputTokenId,
            dexId
        )
    }

    override suspend fun observeAddLiquidity(
        tokenFrom: jp.co.soramitsu.core.models.Asset,
        tokenTo: jp.co.soramitsu.core.models.Asset,
        amountFrom: BigDecimal,
        amountTo: BigDecimal,
        enabled: Boolean,
        presented: Boolean,
        slippageTolerance: Double
    ): String {
        val soraChain = chainRegistry.getChain(soraMainChainId)
        val metaAccount = accountRepository.getSelectedMetaAccount()
        val address = metaAccount.address(soraChain) ?: return ""

        val networkFee = calcAddLiquidityNetworkFee(
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
        return status?.first ?: ""
    }
}
