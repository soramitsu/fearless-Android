package jp.co.soramitsu.polkaswap.api.presentation

import jp.co.soramitsu.polkaswap.api.presentation.models.SwapDetailsParcelModel
import jp.co.soramitsu.polkaswap.api.presentation.models.SwapDetailsViewState
import jp.co.soramitsu.polkaswap.api.presentation.models.TransactionSettingsModel
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

    fun backWithResult(resultDestinationId: Int, vararg results: Pair<String, Any?>)

    fun back()

    fun closeSwap()

    fun openTransactionSettingsDialog(initialSettings: TransactionSettingsModel)

    fun openSwapPreviewDialog(swapDetailsViewState: SwapDetailsViewState, parcelModel: SwapDetailsParcelModel)
    fun openSwapPreviewForResult(swapDetailsViewState: SwapDetailsViewState, parcelModel: SwapDetailsParcelModel): Flow<Int>

    fun openSelectMarketDialog()

    fun openOperationSuccess(operationHash: String?, chainId: ChainId?)

    fun openPolkaswapDisclaimerFromSwapTokensFragment()

    fun openPolkaswapDisclaimerFromMainScreen()

    fun openWebViewer(title: String, url: String)
}
