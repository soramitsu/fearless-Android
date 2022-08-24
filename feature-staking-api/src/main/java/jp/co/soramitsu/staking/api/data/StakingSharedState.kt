package jp.co.soramitsu.staking.api.data

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.common.data.holders.ChainIdHolder
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chainWithAsset
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import jp.co.soramitsu.wallet.impl.domain.TokenUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.Token
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

private const val STAKING_SHARED_STATE = "STAKING_CURRENT_ASSET"

class StakingSharedState(
    private val chainRegistry: ChainRegistry,
    private val preferences: Preferences,
    private val walletRepository: WalletRepository,
    private val accountRepository: AccountRepository
) : ChainIdHolder, TokenUseCase {

    companion object {
        private const val DELIMITER = ":"
    }

    val selectionItem: Flow<StakingAssetSelection> = preferences.stringFlow(
        field = STAKING_SHARED_STATE,
        initialValueProducer = {
            val defaultAsset = availableToSelect().first()

            encode(defaultAsset)
        }
    )
        .distinctUntilChanged()
        .debounce(100)
        .filterNotNull()
        .map { encoded ->
            decode(encoded)
        }

    val assetWithChain: Flow<SingleAssetSharedState.AssetWithChain> = selectionItem.map {
        val (chain, asset) = chainRegistry.chainWithAsset(it.chainId, it.chainAssetId)
        SingleAssetSharedState.AssetWithChain(chain, asset)
    }

    fun currentAssetFlow() = assetWithChain
        .map { chainAndAsset ->
            val meta = accountRepository.getSelectedMetaAccount()
            meta.accountId(chainAndAsset.chain)?.let {
                Pair(meta, chainAndAsset)
            }
        }.mapNotNull { it }
        .flatMapLatest { (selectedMetaAccount, chainAndAsset) ->
            val (chain, chainAsset) = chainAndAsset

            walletRepository.assetFlow(
                metaId = selectedMetaAccount.id,
                accountId = selectedMetaAccount.accountId(chain)!!,
                chainAsset = chainAsset,
                minSupportedVersion = chain.minSupportedVersion
            )
        }

    suspend fun availableAssetsToSelect(): List<Asset> {
        val metaAccount = accountRepository.getSelectedMetaAccount()
        val availableChainAssets = availableToSelect()
        val availableAssetIds = availableChainAssets.map { it.chainAssetId }

        return walletRepository.getAssets(metaAccount.id).filter {
            it.token.configuration.id in availableAssetIds
        }.sortedBy {
            it.token.configuration.orderInStaking
        }
    }

    suspend fun availableToSelect(): List<StakingAssetSelection> {
        val allChains = chainRegistry.currentChains.first()

        return allChains.map { chain ->
            val staking = chain.assets.filter { chainAsset ->
                chainAsset.staking != Chain.Asset.StakingType.UNSUPPORTED
            }.map { StakingAssetSelection.Staking(chain.id, it.id) }
            val pools = chain.assets.filter { it.supportStakingPool }
                .map { StakingAssetSelection.Pool(chain.id, it.id) }
            staking + pools
        }.flatten()
    }

    fun update(item: StakingAssetSelection) {
        preferences.putString(STAKING_SHARED_STATE, encode(item))
    }

    suspend fun chain(): Chain {
        return assetWithChain.first().chain
    }

    override suspend fun chainId(): String {
        return selectionItem.first().chainId
    }

    private fun encode(item: StakingAssetSelection): String {
        return "${item.chainId}$DELIMITER${item.chainAssetId}$DELIMITER${item.type}"
    }

    private fun decode(value: String): StakingAssetSelection {
        val (chainId, chainAssetRaw, type) = value.split(DELIMITER)

        return StakingAssetSelection.from(chainId, chainAssetRaw, type)
    }

    override suspend fun currentToken(): Token {
        return currentAssetFlow().first().token
    }

    override fun currentTokenFlow(): Flow<Token> {
        return currentAssetFlow().map { it.token }
    }
}

sealed class StakingAssetSelection(val chainId: ChainId, val chainAssetId: String) {
    abstract val type: String

    class Staking(chainId: ChainId, chainAssetId: String) : StakingAssetSelection(chainId, chainAssetId) {
        override val type = "staking"
    }

    class Pool(chainId: ChainId, chainAssetId: String) : StakingAssetSelection(chainId, chainAssetId) {
        override val type = "pool"
    }

    companion object {
        fun from(chainId: ChainId, chainAssetId: String, type: String) = when (type) {
            "staking" -> Staking(chainId, chainAssetId)
            "pool" -> Pool(chainId, chainAssetId)
            else -> error("StakingAssetSelection.from Unknown staking type: $type")
        }
    }
}
