package jp.co.soramitsu.feature_account_impl.presentation.exporting.json.confirm

import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.exporting.ExportSource
import jp.co.soramitsu.feature_account_impl.presentation.exporting.ExportViewModel

class ExportJsonConfirmViewModel(
    private val router: AccountRouter,
    resourceManager: ResourceManager,
    accountInteractor: AccountInteractor,
    payload: ExportJsonConfirmPayload
) : ExportViewModel(accountInteractor, payload.address, resourceManager, ExportSource.Json) {

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