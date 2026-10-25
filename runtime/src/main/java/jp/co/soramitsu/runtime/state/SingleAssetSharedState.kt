package jp.co.soramitsu.runtime.state

import jp.co.soramitsu.common.data.holders.ChainIdHolder
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val DELIMITER = ":"

abstract class SingleAssetSharedState(
    private val preferencesKey: String,
    private val chainRegistry: ChainRegistry,
    private val filter: (Chain, Asset) -> Boolean,
    private val preferences: Preferences
) : ChainIdHolder {

    data class AssetWithChain(
        val chain: Chain,
        val asset: Asset
    )

    /** Emits null when the selected asset is unavailable so active consumers can cancel their work. */
    open val assetWithChainAvailability: Flow<AssetWithChain?> = combine(
        preferences.stringFlow(field = preferencesKey).distinctUntilChanged().debounce(100),
        chainRegistry.currentChains
    ) { encoded, chains ->
        if (encoded == null) {
            firstAvailable(chains)
        } else {
            val saved = decodeStored(encoded)
            val chain = chains.firstOrNull { it.id == saved?.first }
            val asset = chain?.assets?.firstOrNull { it.id == saved?.second }

            // A stored choice must never authorize a different chain or asset.
            if (chain != null && asset != null) AssetWithChain(chain, asset) else null
        }
    }.distinctUntilChanged()

    open val assetWithChain: Flow<AssetWithChain> = assetWithChainAvailability
        .filterNotNull()
        .distinctUntilChanged()

    private fun decodeStored(value: String): Pair<ChainId, String>? {
        val delimiter = value.indexOf(DELIMITER)
        if (delimiter <= 0 || delimiter == value.lastIndex) return null

        return decode(value)
    }

    private fun firstAvailable(chains: List<Chain>): AssetWithChain? = chains.firstNotNullOfOrNull { chain ->
        chain.assets.firstOrNull { filter(chain, it) }?.let { asset -> AssetWithChain(chain, asset) }
    }

    suspend fun availableToSelect(): List<Asset> {
        val allChains = chainRegistry.currentChains.first()

        return allChains.map { chain ->
            chain.assets.filter { chainAsset ->
                filter(chain, chainAsset)
            }
        }.flatten()
    }

    open fun update(chainId: ChainId, chainAssetId: String) {
        preferences.putString(preferencesKey, encode(chainId, chainAssetId))
    }

    override suspend fun chainId(): String {
        return assetWithChain.first().chain.id
    }

    protected open fun encode(chainId: ChainId, chainAssetId: String): String {
        return "$chainId$DELIMITER$chainAssetId"
    }

    protected open fun decode(value: String): Pair<ChainId, String> {
        val (chainId, chainAssetRaw) = value.split(DELIMITER, limit = 2)

        return chainId to chainAssetRaw
    }
}

fun SingleAssetSharedState.selectedChainFlow() = assetWithChain
    .map { it.chain }
    .distinctUntilChanged()

suspend fun SingleAssetSharedState.chain() = assetWithChain.first().chain

suspend fun SingleAssetSharedState.chainAsset() = assetWithChain.first().asset

suspend fun SingleAssetSharedState.chainAndAsset() = assetWithChain.first()

fun SingleAssetSharedState.selectedAssetFlow() = assetWithChain
    .map { it.asset }
