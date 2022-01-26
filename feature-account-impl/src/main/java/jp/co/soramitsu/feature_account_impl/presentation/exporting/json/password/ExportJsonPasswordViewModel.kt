package jp.co.soramitsu.feature_account_impl.presentation.exporting.json.password

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.combine
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.exporting.json.confirm.ExportJsonConfirmPayload
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import kotlinx.coroutines.launch

class ExportJsonPasswordViewModel(
    private val router: AccountRouter,
    private val interactor: AccountInteractor,
    private val chainRegistry: ChainRegistry,
    private val payload: ExportJsonPasswordPayload
) : BaseViewModel() {

    val passwordLiveData = MutableLiveData<String>()
    val passwordConfirmationLiveData = MutableLiveData<String>()

    val chainLiveData = liveData { emit(chainRegistry.getChain(payload.chainId)) }

    val showDoNotMatchingErrorLiveData = passwordLiveData.combine(passwordConfirmationLiveData) { password, confirmation ->
        confirmation.isNotBlank() && confirmation != password
    }

    val nextEnabled = passwordLiveData.combine(passwordConfirmationLiveData, initial = false) { password, confirmation ->
        password.isNotBlank() && confirmation.isNotBlank() && password == confirmation
    }

    fun back() {
        router.back()
    }

    fun nextClicked() {
        val password = passwordLiveData.value!!

        viewModelScope.launch {
            val result = interactor.generateRestoreJson(payload.metaId, payload.chainId, password)

            if (result.isSuccess) {
                val payload = ExportJsonConfirmPayload(payload.metaId, payload.chainId, result.requireValue())

                router.openExportJsonConfirm(payload)
            }
        }
    }
}
