package jp.co.soramitsu.staking.api.data

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.common.data.holders.ChainIdHolder
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.reefChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraTestChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ternoaChainId
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import jp.co.soramitsu.wallet.impl.domain.TokenUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.Token
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transformLatest
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
    @Suppress("UNUSED_PARAMETER") chainRegistry: ChainRegistry,
    private val preferences: Preferences,
    private val walletRepository: WalletRepository,
    private val accountRepository: AccountRepository,
    chainsRepository: ChainsRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) : ChainIdHolder, TokenUseCase {

    companion object {
        private const val DELIMITER = ":"
    }

    val selectionRevision = StakingSelectionRevision().apply { catalogChanged() }

    private data class AvailableAsset(
        val storedAsset: Asset,
        val chain: Chain,
        val configuration: CoreAsset
    )

    private data class ObservedSelection(
        val assets: List<AvailableAsset>,
        val options: List<StakingAssetSelection>,
        val selected: StakingAssetSelection?,
        val selectedAsset: AvailableAsset?
    )

    /** Null revokes the previous observation while a changed catalog or account is being read. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val observation = combine(
        preferences.stringFlow(field = STAKING_SHARED_STATE),
        accountRepository.selectedMetaAccountFlow(),
        chainsRepository.chainsFlow()
    ) { encoded, account, catalog -> Triple(encoded, account, catalog) }
        .transformLatest { (encoded, account, catalog) ->
            selectionRevision.catalogChanged()
            emit(null)
            val relevantChains = catalog.filter { account.accountId(it) != null }.associateBy { it.id }
            val assets = if (relevantChains.isEmpty()) {
                emptyList()
            } else {
                walletRepository.getAssets(account.id).mapNotNull { stored ->
                    val chain = relevantChains[stored.token.configuration.chainId] ?: return@mapNotNull null
                    val accountId = account.accountId(chain) ?: return@mapNotNull null
                    if (stored.metaId != account.id || !stored.accountId.contentEquals(accountId)) return@mapNotNull null
                    val configuration = chain.assetsById[stored.token.configuration.id] ?: return@mapNotNull null
                    if (configuration.staking == CoreAsset.StakingType.UNSUPPORTED && !configuration.supportStakingPool) {
                        return@mapNotNull null
                    }
                    val currentAsset = stored.copy(token = stored.token.copy(configuration = configuration))
                    AvailableAsset(currentAsset, chain, configuration)
                }.sortedBy { it.configuration.orderInStaking }
            }
            val options = assets.flatMap { available ->
                buildList {
                    val chainId = available.chain.id
                    val asset = available.configuration
                    when (asset.staking) {
                        CoreAsset.StakingType.PARACHAIN -> add(StakingAssetSelection.ParachainStaking(chainId, asset.id))
                        CoreAsset.StakingType.RELAYCHAIN -> add(StakingAssetSelection.RelayChainStaking(chainId, asset.id))
                        CoreAsset.StakingType.UNSUPPORTED -> Unit
                    }
                    if (asset.supportStakingPool) add(StakingAssetSelection.Pool(chainId, asset.id))
                }
            }.distinct()
            // Only an absent preference permits an ephemeral default. Invalid or unavailable bytes stay stored.
            val selected = if (encoded == null) options.firstOrNull() else decodeStored(encoded)?.takeIf { it in options }
            val selectedAsset = selected?.let { choice ->
                assets.firstOrNull { it.chain.id == choice.chainId && it.configuration.id == choice.chainAssetId }
            }
            currentCoroutineContext().ensureActive()
            selectionRevision.catalogSelected(selected)
            emit(ObservedSelection(assets, options, selected, selectedAsset))
        }
        .shareIn(scope, SharingStarted.Eagerly, replay = 1)

    val selectionItemAvailability: Flow<StakingAssetSelection?> = observation
        .map { it?.selected }
        .distinctUntilChanged()

    val selectionItem: Flow<StakingAssetSelection> = selectionItemAvailability.filterNotNull()

    val assetWithChainAvailability: Flow<SingleAssetSharedState.AssetWithChain?> = observation
        .map { state ->
            state?.selectedAsset?.let { SingleAssetSharedState.AssetWithChain(it.chain, it.configuration) }
        }
        .distinctUntilChanged()

    val assetWithChain: Flow<SingleAssetSharedState.AssetWithChain> = assetWithChainAvailability.filterNotNull()

    val currentAssetAvailability: Flow<Asset?> = observation
        .map { it?.selectedAsset?.storedAsset }
        .distinctUntilChanged()

    fun currentAssetFlow(): Flow<Asset> = currentAssetAvailability.filterNotNull()

    val currentTokenAvailability: Flow<Token?> = currentAssetAvailability.map { it?.token }.distinctUntilChanged()

    suspend fun availableAssetsToSelect(): List<Asset> = observation.filterNotNull().first().assets.map { it.storedAsset }

    suspend fun availableToSelect(): List<StakingAssetSelection> = observation.filterNotNull().first().options

    fun update(item: StakingAssetSelection) {
        selectionRevision.changed(item)
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

    private fun decodeStored(value: String): StakingAssetSelection? {
        val first = value.indexOf(DELIMITER)
        val last = value.lastIndexOf(DELIMITER)
        if (first <= 0 || last <= first + 1 || last == value.lastIndex) return null
        val chainId = value.substring(0, first)
        val assetId = value.substring(first + 1, last)
        return when (value.substring(last + 1)) {
            StakingType.RELAYCHAIN.name -> StakingAssetSelection.RelayChainStaking(chainId, assetId)
            StakingType.PARACHAIN.name -> StakingAssetSelection.ParachainStaking(chainId, assetId)
            StakingType.POOL.name -> StakingAssetSelection.Pool(chainId, assetId)
            else -> null
        }
    }

    override suspend fun currentToken(): Token {
        return currentAssetFlow().first().token
    }

    override fun currentTokenFlow(): Flow<Token> {
        return currentAssetFlow().map { it.token }
    }
}

sealed class StakingAssetSelection(open val chainId: ChainId, open val chainAssetId: String) {
    abstract val type: StakingType

    abstract operator fun component1(): ChainId
    abstract operator fun component2(): String

    data class RelayChainStaking(override val chainId: ChainId, override val chainAssetId: String) :
        StakingAssetSelection(chainId, chainAssetId) {
        override val type = StakingType.RELAYCHAIN
    }

    data class ParachainStaking(override val chainId: ChainId, override val chainAssetId: String) :
        StakingAssetSelection(chainId, chainAssetId) {
        override val type = StakingType.PARACHAIN
    }

    data class Pool(override val chainId: ChainId, override val chainAssetId: String) :
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
