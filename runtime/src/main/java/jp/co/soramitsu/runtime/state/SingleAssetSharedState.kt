package jp.co.soramitsu.runtime.state

import jp.co.soramitsu.common.data.holders.ChainIdHolder
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chainWithAsset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val DELIMITER = ":"

abstract class SingleAssetSharedState(
    private val preferencesKey: String,
    private val chainRegistry: ChainRegistry,
    private val filter: (Chain, Chain.Asset) -> Boolean,
    private val preferences: Preferences
) : ChainIdHolder {

    data class AssetWithChain(
        val chain: Chain,
        val asset: Chain.Asset,
    )

    val assetWithChain: Flow<AssetWithChain> = preferences.stringFlow(
        field = preferencesKey,
        initialValueProducer = {
            val defaultAsset = availableToSelect().first()

            encode(defaultAsset.chainId, defaultAsset.id)
        }
    )
        .distinctUntilChanged()
        .debounce(100)
        .filterNotNull()
        .map { encoded ->
            val (chainId, chainAssetId) = decode(encoded)

            val (chain, chainAsset) = try {
                chainRegistry.chainWithAsset(chainId, chainAssetId)
            } catch (e: NoSuchElementException) {
                val defaultAsset = availableToSelect().first()
                preferences.putString(preferencesKey, encode(defaultAsset.chainId, defaultAsset.id))
                chainRegistry.chainWithAsset(defaultAsset.chainId, defaultAsset.id)
            }

            AssetWithChain(chain, chainAsset)
        }

    suspend fun availableToSelect(): List<Chain.Asset> {
        val allChains = chainRegistry.currentChains.first()

        return allChains.map { chain ->
            chain.assets.filter { chainAsset ->
                filter(chain, chainAsset)
            }
        }.flatten()
    }

    fun update(chainId: ChainId, chainAssetId: String) {
        preferences.putString(preferencesKey, encode(chainId, chainAssetId))
    }

    override suspend fun chainId(): String {
        return assetWithChain.first().chain.id
    }

    private fun encode(chainId: ChainId, chainAssetId: String): String {
        return "$chainId$DELIMITER$chainAssetId"
    }

    private fun decode(value: String): Pair<ChainId, String> {
        val (chainId, chainAssetRaw) = value.split(DELIMITER)

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
