package co.jp.soramitsu.walletconnect.domain

import co.jp.soramitsu.walletconnect.model.ChainChooseResult
import jp.co.soramitsu.core.models.ChainId
import kotlinx.coroutines.flow.Flow

interface WalletConnectRouter {
    fun back()

    fun openOperationSuccess(operationHash: String?, chainId: ChainId?)

    fun openOperationSuccess(
        operationHash: String?,
        chainId: ChainId?,
        customMessage: String?
    )

    fun openOperationSuccessAndPopUpToNearestRelatedScreen(
        operationHash: String?,
        chainId: ChainId?,
        customMessage: String?
    )

    fun openSelectMultipleChains(
        items: List<String>,
        selected: List<String>,
        isViewMode: Boolean = false
    )

    fun openSelectMultipleChainsForResult(items: List<String>, selected: List<String>): Flow<ChainChooseResult>

    fun openRequestPreview(topic: String)

    fun openRawData(payload: String)
}
