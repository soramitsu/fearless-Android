package jp.co.soramitsu.feature_account_impl.presentation.exporting.json.confirm

import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_api.presentation.exporting.ExportSource
import jp.co.soramitsu.feature_account_impl.presentation.exporting.ExportViewModel
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry

class ExportJsonConfirmViewModel(
    private val router: AccountRouter,
    resourceManager: ResourceManager,
    accountInteractor: AccountInteractor,
    chainRegistry: ChainRegistry,
    payload: ExportJsonConfirmPayload
) : ExportViewModel(accountInteractor, resourceManager, chainRegistry, payload.metaId, payload.chainId, ExportSource.Json) {

    val json = payload.json

    fun changePasswordClicked() {
        back()
    }

    fun back() {
        router.back()
    }

    fun confirmClicked() {
        exportText(json)
    }
}
