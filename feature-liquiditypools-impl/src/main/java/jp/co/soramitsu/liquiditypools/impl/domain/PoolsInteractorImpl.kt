package jp.co.soramitsu.liquiditypools.impl.domain

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.data.secrets.v1.Keypair
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.liquiditypools.blockexplorer.BlockExplorerManager
import jp.co.soramitsu.liquiditypools.data.PoolDataDto
import jp.co.soramitsu.liquiditypools.data.PoolsRepository
import jp.co.soramitsu.liquiditypools.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.liquiditypools.domain.model.BasicPoolData
import jp.co.soramitsu.liquiditypools.domain.model.CommonPoolData
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import kotlin.coroutines.CoroutineContext

class PoolsInteractorImpl(
    private val poolsRepository: PoolsRepository,
    private val accountRepository: AccountRepository,
    private val blockExplorerManager: BlockExplorerManager,
    private val coroutineContext: CoroutineContext = Dispatchers.Default
) : PoolsInteractor {

    override val poolsChainId = poolsRepository.poolsChainId

    override suspend fun getBasicPools(): List<BasicPoolData> {
        return poolsRepository.getBasicPools(poolsChainId)
    }

    override fun subscribePoolsCacheOfAccount(address: String): Flow<List<CommonPoolData>> {
        return poolsRepository.subscribePools(address).flowOn(coroutineContext)
    }

    private val soraPoolsAddressFlow = flowOf {
        val meta = accountRepository.getSelectedMetaAccount()
        val chain = accountRepository.getChain(poolsChainId)
        meta.address(chain)
    }.mapNotNull { it }
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun subscribePoolsCacheCurrentAccount(): Flow<List<CommonPoolData>> {
        return soraPoolsAddressFlow.flatMapLatest { address ->
            poolsRepository.subscribePools(address)
        }
    }

    override suspend fun getPoolData(baseTokenId: String, targetTokenId: String): Flow<CommonPoolData> {
        val address = accountRepository.getSelectedAccount(poolsChainId).address
        return poolsRepository.subscribePool(address, baseTokenId, targetTokenId)
    }

    override suspend fun getUserPoolData(
        chainId: ChainId,
        address: String,
        baseTokenId: String,
        tokenId: ByteArray
    ): PoolDataDto? {
        return poolsRepository.getUserPoolData(chainId, address, baseTokenId, tokenId)
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
        return poolsRepository.calcAddLiquidityNetworkFee(
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
        tokenBase: Asset,
        tokenTarget: Asset,
    ): BigDecimal? {
        return poolsRepository.calcRemoveLiquidityNetworkFee(
            poolsChainId,
            tokenBase,
            tokenTarget
        )
    }

    override suspend fun isPairEnabled(baseTokenId: String, targetTokenId: String): Boolean {
        val dexId = poolsRepository.getPoolBaseTokenDexId(poolsChainId, baseTokenId)
        return poolsRepository.isPairAvailable(
            poolsChainId,
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
        val status = poolsRepository.observeRemoveLiquidity(
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

        val secrets = accountRepository.getMetaAccountSecrets(metaAccount.id)?.get(MetaAccountSecrets.SubstrateKeypair)
        requireNotNull(secrets)
        val private = secrets[KeyPairSchema.PrivateKey]
        val public = secrets[KeyPairSchema.PublicKey]
        val nonce = secrets[KeyPairSchema.Nonce]
        val keypair = Keypair(public, private, nonce)

        val status = poolsRepository.observeAddLiquidity(
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

        return status?.getOrNull() ?: ""
    }

    override suspend fun syncPools(): Unit = withContext(Dispatchers.Default) {
        val address = accountRepository.getSelectedAccount(poolsChainId).address
        supervisorScope {
            launch { poolsRepository.updateBasicPools(poolsChainId) }
            launch { poolsRepository.updateAccountPools(poolsChainId, address) }
            launch { blockExplorerManager.syncSbApy() }
        }
    }

    override suspend fun updateAccountPools(): Unit = withContext(Dispatchers.Default) {
        val address = accountRepository.getSelectedAccount(poolsChainId).address
        poolsRepository.updateAccountPools(poolsChainId, address)
    }

    override suspend fun getSbApy(id: String): Double? = withContext(coroutineContext) {
        blockExplorerManager.getApy(id)
    }
}
