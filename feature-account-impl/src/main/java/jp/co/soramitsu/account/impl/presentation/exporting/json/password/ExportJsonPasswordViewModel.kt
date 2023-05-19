package jp.co.soramitsu.account.impl.presentation.exporting.json.password

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.hasChainAccount
import jp.co.soramitsu.account.api.presentation.exporting.ExportSource
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.exporting.ExportViewModel
import jp.co.soramitsu.account.impl.presentation.exporting.json.confirm.ExportJsonConfirmPayload
import jp.co.soramitsu.account.impl.presentation.exporting.json.password.ExportJsonPasswordFragment.Companion.PAYLOAD_KEY
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.combine
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.common.view.ButtonState
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExportJsonPasswordViewModel @Inject constructor(
    private val router: AccountRouter,
    private val interactor: AccountInteractor,
    private val chainRegistry: ChainRegistry,
    resourceManager: ResourceManager,
    private val savedStateHandle: SavedStateHandle
) : ExportViewModel(
    interactor,
    resourceManager,
    chainRegistry,
    savedStateHandle.get<ExportJsonPasswordPayload>(PAYLOAD_KEY)!!.metaId,
    savedStateHandle.get<ExportJsonPasswordPayload>(PAYLOAD_KEY)!!.chainId,
    savedStateHandle.get<ExportJsonPasswordPayload>(PAYLOAD_KEY)!!.isExportWallet,
    ExportSource.Json
) {

    private val payload = savedStateHandle.get<ExportJsonPasswordPayload>(PAYLOAD_KEY)!!
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

            val chainForSubstrateExport = when {
                payload.isExportWallet -> polkadotChainId
                else -> payload.chainId
            }

            val chainForEthereumExport = when {
                payload.isExportWallet -> {
                    val supportedEthChains = chainRegistry.currentChains.first().filter { chain ->
                        chain.isSupported && chain.isEthereumBased && accountInteractor.getMetaAccount(payload.metaId).hasChainAccount(chain.id).not()
                    }.sortedWith(compareBy<Chain> { it.isTestNet }.thenBy { it.name })

                    supportedEthChains.getOrNull(0)?.id
                }
                else -> payload.chainId
            }

            val substrateJsonResult = interactor.generateRestoreJson(payload.metaId, chainForSubstrateExport, password)
            val ethereumJsonResult = when (chainForEthereumExport) {
                null -> Result.failure(IllegalArgumentException("No chain specified"))
                else -> interactor.generateRestoreJson(payload.metaId, chainForEthereumExport, password)
            }

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
