package jp.co.soramitsu.account.impl.presentation.exporting.json.password

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.hasChainAccount
import jp.co.soramitsu.account.api.presentation.exporting.ExportSource
import jp.co.soramitsu.account.impl.domain.BackupPasswordRule
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

    // The wallet name and addresses a new export password must not be; null until loaded.
    private val forbiddenValues = MutableLiveData<Pair<String?, Set<String>>?>(null)

    private val passwordMeetsRule = passwordLiveData.combine(forbiddenValues, initial = false) { password, forbidden ->
        forbidden != null && BackupPasswordRule.check(password, forbidden.first, forbidden.second) == null
    }

    /** The rule is always shown; it is marked while a typed password breaks it. */
    val showPasswordRuleErrorLiveData = passwordLiveData.combine(passwordMeetsRule, initial = false) { password, meetsRule ->
        password.isNotEmpty() && !meetsRule
    }

    private val nextEnabled = passwordLiveData.combine(passwordConfirmationLiveData, initial = false) { password, confirmation ->
        password.isNotBlank() && confirmation.isNotBlank() && password == confirmation
    }.combine(passwordMeetsRule, initial = false) { matched, meetsRule -> matched && meetsRule }
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
        viewModelScope.launch {
            val wallet = runCatching { interactor.getMetaAccount(payload.metaId) }.getOrNull()
            forbiddenValues.value = wallet?.name to wallet?.let(BackupPasswordRule::walletAddresses).orEmpty()
        }
    }

    fun back() {
        router.back()
    }

    fun nextClicked() {
        val password = passwordLiveData.value ?: return
        val forbidden = forbiddenValues.value ?: return
        // Exports are new files: the rule applies even when the button was reached another way.
        if (BackupPasswordRule.check(password, forbidden.first, forbidden.second) != null) return
        nextProgress.value = true

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
            } else {
                nextProgress.value = false
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
