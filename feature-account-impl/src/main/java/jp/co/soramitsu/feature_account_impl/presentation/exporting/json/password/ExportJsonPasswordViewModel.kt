package jp.co.soramitsu.feature_account_impl.presentation.exporting.json.password

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.combine
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_impl.data.mappers.mapNetworkTypeToNetworkModel
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.exporting.json.confirm.ExportJsonConfirmPayload
import kotlinx.coroutines.launch

class ExportJsonPasswordViewModel(
    private val router: AccountRouter,
    private val interactor: AccountInteractor,
    private val accountAddress: String
) : BaseViewModel() {

    val passwordLiveData = MutableLiveData<String>()
    val passwordConfirmationLiveData = MutableLiveData<String>()

    private val accountLiveData = liveData { emit(loadAccount()) }

    val networkTypeLiveData = accountLiveData.map { mapNetworkTypeToNetworkModel(it.network.type) }

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
            val result = interactor.generateRestoreJson(accountAddress, password)

            if (result.isSuccess) {
                val payload = ExportJsonConfirmPayload(accountAddress, result.requireValue())

                router.openExportJsonConfirm(payload)
            }
        }
    }

    private suspend fun loadAccount(): Account {
        return interactor.getAccount(accountAddress)
    }
}