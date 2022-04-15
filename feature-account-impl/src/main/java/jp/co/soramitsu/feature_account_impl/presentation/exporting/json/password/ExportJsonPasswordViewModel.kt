package jp.co.soramitsu.feature_account_impl.presentation.exporting.json.password

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.combine
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.common.view.ButtonState
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.presentation.exporting.ExportSource
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.exporting.ExportViewModel
import jp.co.soramitsu.feature_account_impl.presentation.exporting.json.confirm.ExportJsonConfirmPayload
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.moonriverChainId
import kotlinx.coroutines.launch

class ExportJsonPasswordViewModel(
    private val router: AccountRouter,
    private val interactor: AccountInteractor,
    chainRegistry: ChainRegistry,
    private val payload: ExportJsonPasswordPayload,
    resourceManager: ResourceManager
) : ExportViewModel(interactor, resourceManager, chainRegistry, payload.metaId, payload.chainId, payload.isExportWallet, ExportSource.Json) {

    val isExportWallet = payload.isExportWallet
    val passwordLiveData = MutableLiveData<String>()
    val passwordConfirmationLiveData = MutableLiveData<String>()

    val showDoNotMatchingErrorLiveData = passwordLiveData.combine(passwordConfirmationLiveData) { password, confirmation ->
        confirmation.isNotBlank() && confirmation != password
    }

    private val nextEnabled = passwordLiveData.combine(passwordConfirmationLiveData, initial = false) { password, confirmation ->
        password.isNotBlank() && confirmation.isNotBlank() && password == confirmation
    }
    private val nextProgress = MutableLiveData(false)

    val nextButtonState = nextEnabled.combine(nextProgress) { enabled, inProgress ->
        when {
            inProgress -> ButtonState.PROGRESS
            enabled -> ButtonState.NORMAL
            else -> ButtonState.DISABLED
        }
    }

    init {
        showSecurityWarning()
    }

    fun back() {
        router.back()
    }

    fun nextClicked() {
        nextProgress.value = true
        val password = passwordLiveData.value!!

        viewModelScope.launch {
            val isEthBased = chainLiveData.value?.isEthereumBased

            val substrateJsonResult = interactor.generateRestoreJson(payload.metaId, payload.chainId, password)
            val ethereumJsonResult = interactor.generateRestoreJson(payload.metaId, moonriverChainId, password)

            val payload = when {
                payload.isExportWallet && substrateJsonResult.isSuccess -> {
                    ExportJsonConfirmPayload(
                        payload.metaId,
                        payload.chainId,
                        substrateJsonResult.requireValue(),
                        ethereumJsonResult.getOrNull(),
                        payload.isExportWallet
                    )
                }
                !payload.isExportWallet && isEthBased == false && substrateJsonResult.isSuccess -> {
                    ExportJsonConfirmPayload(
                        payload.metaId,
                        payload.chainId,
                        substrateJsonResult.requireValue(),
                        null,
                        payload.isExportWallet
                    )
                }
                !payload.isExportWallet && isEthBased == true && ethereumJsonResult.isSuccess -> {
                    ExportJsonConfirmPayload(
                        payload.metaId,
                        payload.chainId,
                        null,
                        ethereumJsonResult.requireValue(),
                        payload.isExportWallet
                    )
                }
                else -> null
            }
            if (payload != null) {
                router.openExportJsonConfirm(payload)
            }
        }
    }

    override fun securityWarningCancel() {
        back()
    }

    fun resetProgress() {
        nextProgress.value = false
    }
}
