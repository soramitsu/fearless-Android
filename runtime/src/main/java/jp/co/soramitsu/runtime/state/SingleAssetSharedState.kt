package jp.co.soramitsu.runtime.state

import jp.co.soramitsu.common.data.holders.ChainIdHolder
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

interface SingleAssetSharedState : ChainIdHolder {

    data class SelectedAsset(
        val chain: Chain,
        val asset: Chain.Asset,
    )

    val selectedAsset: Flow<SelectedAsset>

    override suspend fun chainId(): String {
        return selectedAsset.first().chain.id
    }
}

suspend fun SingleAssetSharedState.chain() = selectedAsset.first().chain

suspend fun SingleAssetSharedState.chainAsset() = selectedAsset.first().asset
