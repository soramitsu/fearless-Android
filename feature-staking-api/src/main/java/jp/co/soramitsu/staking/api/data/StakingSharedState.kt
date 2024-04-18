package jp.co.soramitsu.staking.api.data

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.common.data.holders.ChainIdHolder
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.reefChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraTestChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ternoaChainId
import jp.co.soramitsu.runtime.multiNetwork.chainWithAsset
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import jp.co.soramitsu.wallet.impl.domain.TokenUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.Token
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.shareIn
import jp.co.soramitsu.core.models.Asset as CoreAsset

enum class StakingType {
    PARACHAIN, RELAYCHAIN, POOL
}

enum class SyntheticStakingType {
    DEFAULT, SORA, TERNOA, REEF
}

fun CoreAsset.syntheticStakingType(): SyntheticStakingType {
    return when {
        (chainId == soraMainChainId || chainId == soraTestChainId) &&
                staking == CoreAsset.StakingType.RELAYCHAIN -> SyntheticStakingType.SORA

        chainId == ternoaChainId && staking == CoreAsset.StakingType.RELAYCHAIN -> SyntheticStakingType.TERNOA

        chainId == reefChainId && staking == CoreAsset.StakingType.RELAYCHAIN -> SyntheticStakingType.REEF

        else -> SyntheticStakingType.DEFAULT
    }
}

private const val STAKING_SHARED_STATE = "STAKING_CURRENT_ASSET_TYPE"

class StakingSharedState(
    private val chainRegistry: ChainRegistry,
    private val preferences: Preferences,
    private val walletRepository: WalletRepository,
    private val accountRepository: AccountRepository,
    private val scope: CoroutineScope
) : ChainIdHolder, TokenUseCase {

    companion object {
        private const val DELIMITER = ":"
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectionItem: Flow<StakingAssetSelection> = accountRepository.selectedMetaAccountFlow()
        .flatMapLatest {
            preferences.stringFlow(
                field = STAKING_SHARED_STATE,
                initialValueProducer = {
                    val defaultAsset = availableToSelect().first()

                    encode(defaultAsset)
                }
            )
        }
        .map { encoded ->
            encoded?.let { decode(it) }
        }
        .distinctUntilChanged()
        .filterNotNull()
        .shareIn(scope, SharingStarted.Eagerly, replay = 1)

    val assetWithChain: Flow<SingleAssetSharedState.AssetWithChain> = selectionItem.map {
        val (chain, asset) = chainRegistry.chainWithAsset(it.chainId, it.chainAssetId)
        SingleAssetSharedState.AssetWithChain(chain, asset)
    }.shareIn(scope, SharingStarted.Eagerly, replay = 1)

    suspend fun assetWithChain(selectionItem: StakingAssetSelection) {
        val (chain, asset) = chainRegistry.chainWithAsset(
            selectionItem.chainId,
            selectionItem.chainAssetId
        )
        SingleAssetSharedState.AssetWithChain(chain, asset)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun currentAssetFlow() = combine(
        assetWithChain,
        accountRepository.selectedMetaAccountFlow()
    ) { chainAndAsset, meta ->
        meta.accountId(chainAndAsset.chain)?.let {
            Pair(meta, chainAndAsset)
        }
    }
        .mapNotNull { it }
        .flatMapLatest { (selectedMetaAccount, chainAndAsset) ->
            val (chain, chainAsset) = chainAndAsset
            walletRepository.assetFlow(
                metaId = selectedMetaAccount.id,
                accountId = selectedMetaAccount.accountId(chain)!!,
                chainAsset = chainAsset,
                minSupportedVersion = chain.minSupportedVersion
            )
        }.shareIn(scope, SharingStarted.Eagerly, replay = 1)

    suspend fun availableAssetsToSelect(): List<Asset> {
        val metaAccount = accountRepository.getSelectedMetaAccount()
        val availableChainAssets = availableToSelect().associate { it.chainId to it.chainAssetId }
        val allAssets = walletRepository.getAssets(metaAccount.id)

        return allAssets.filter {
            it.token.configuration.id in availableChainAssets.values && it.token.configuration.chainId in availableChainAssets.keys
        }.sortedBy {
            it.token.configuration.orderInStaking
        }
    }

    suspend fun availableToSelect(): List<StakingAssetSelection> {
        val wallet = accountRepository.getSelectedMetaAccount()
        val allChains = chainRegistry.currentChains.first().filter {
            wallet.accountId(it) != null
        }

        return allChains.map { chain ->
            val staking = chain.assets.filter { chainAsset ->
                chainAsset.staking != CoreAsset.StakingType.UNSUPPORTED
            }.map {
                when (it.staking) {
                    CoreAsset.StakingType.PARACHAIN -> StakingAssetSelection.ParachainStaking(
                        chain.id,
                        it.id
                    )

                    CoreAsset.StakingType.RELAYCHAIN -> StakingAssetSelection.RelayChainStaking(
                        chain.id,
                        it.id
                    )

                    else -> error("StakingSharedState.availableToSelect wrong staking type: ${it.staking}")
                }
            }
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
        return chain().id
    }

    private fun encode(item: StakingAssetSelection): String {
        return "${item.chainId}$DELIMITER${item.chainAssetId}$DELIMITER${item.type.name}"
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
    abstract val type: StakingType

    class RelayChainStaking(chainId: ChainId, chainAssetId: String) :
        StakingAssetSelection(chainId, chainAssetId) {
        override val type = StakingType.RELAYCHAIN
    }

    class ParachainStaking(chainId: ChainId, chainAssetId: String) :
        StakingAssetSelection(chainId, chainAssetId) {
        override val type = StakingType.PARACHAIN
    }

    class Pool(chainId: ChainId, chainAssetId: String) :
        StakingAssetSelection(chainId, chainAssetId) {
        override val type = StakingType.POOL
    }

    companion object {
        fun from(chainId: ChainId, chainAssetId: String, type: String) = when (type) {
            StakingType.RELAYCHAIN.name -> RelayChainStaking(chainId, chainAssetId)
            StakingType.PARACHAIN.name -> ParachainStaking(chainId, chainAssetId)
            StakingType.POOL.name -> Pool(chainId, chainAssetId)
            else -> error("StakingAssetSelection.from Unknown staking type: $type")
        }
    }
}
