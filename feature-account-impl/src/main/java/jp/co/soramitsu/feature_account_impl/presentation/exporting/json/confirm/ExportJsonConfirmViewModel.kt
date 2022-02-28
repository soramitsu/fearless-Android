package jp.co.soramitsu.feature_account_impl.presentation.exporting.json.confirm

import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.presentation.exporting.ExportSource
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.exporting.ExportViewModel
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry

class ExportJsonConfirmViewModel(
    private val router: AccountRouter,
    resourceManager: ResourceManager,
    accountInteractor: AccountInteractor,
    chainRegistry: ChainRegistry,
    payload: ExportJsonConfirmPayload
) : ExportViewModel(accountInteractor, resourceManager, chainRegistry, payload.metaId, payload.chainId, payload.isExportWallet, ExportSource.Json) {

    val substrateJson = payload.substrateJson
    val ethereumJson = payload.ethereumJson

    fun changePasswordClicked() {
        back()
    }

    fun back() {
        router.back()
    }

    fun confirmSubstrateClicked() {
        substrateJson ?: return
        exportText(substrateJson)
    }

    fun confirmEthereumClicked() {
        ethereumJson ?: return
        exportText(ethereumJson)
    }

    fun shareCompleted() {
        router.finishExportFlow()
    }
}
