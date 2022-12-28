package jp.co.soramitsu.polkaswap.api.presentation

import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.flow.Flow

interface PolkaswapRouter {

    fun openSelectAsset(
        chainId: ChainId,
        selectedAssetId: String?,
        excludeAssetId: String?
    )

    fun <T> observeResult(key: String): Flow<T>

    fun back()

    fun openTransactionSettingsDialog()

    fun openSwapPreviewDialog()

    fun openSelectMarketDialog()
}
