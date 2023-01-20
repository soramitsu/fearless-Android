package jp.co.soramitsu.polkaswap.api.presentation

import jp.co.soramitsu.polkaswap.api.presentation.models.SwapDetails
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.flow.Flow

interface PolkaswapRouter {

    fun openSelectAsset(
        chainId: ChainId,
        selectedAssetId: String?,
        excludeAssetId: String?
    )

    fun <T> observeResult(key: String): Flow<T>

    fun backWithResult(vararg results: Pair<String, Any?>)

    fun back()

    fun openTransactionSettingsDialog()

    fun openSwapPreviewDialog(swapDetails: SwapDetails)

    fun openSelectMarketDialog()

    fun openOperationSuccess(operationHash: String?, chainId: ChainId)
}
