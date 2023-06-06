package jp.co.soramitsu.account.impl.presentation.importing.remote_backup

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens.EnterBackupPasswordState
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens.RemoteWalletListState
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens.WalletImportedState
import jp.co.soramitsu.backup.domain.models.EncryptedBackupAccount
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.TextInputViewState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ImportRemoteWalletViewModel @Inject constructor(
    private val accountRouter: AccountRouter
) : BaseViewModel(), ImportRemoteWalletCallback {

    private val steps = listOf(
        ImportRemoteWalletStep.WalletList,
        ImportRemoteWalletStep.EnterBackupPassword,
        ImportRemoteWalletStep.WalletImported
    )
    private val currentStep = MutableStateFlow(steps.first())
    private val remoteWallets = MutableStateFlow(
        List(50) {
            EncryptedBackupAccount(
                name = "Name ${it + 1}",
                address = "",
                encryptedPassphrase = ""
            )
        }
    )

    private val selectedWallet = MutableStateFlow<EncryptedBackupAccount?>(null)
    private val walletImportedState = selectedWallet.map { selectedWallet ->
        WalletImportedState(selectedWallet)
    }

    private val defaultTextInputViewState = TextInputViewState(
        text = "",
        hint = "Enter password",
        placeholder = "************",
        endIcon = null,
        isActive = true,
        mode = TextInputViewState.Mode.Password
    )
    private val passwordInputViewState = MutableStateFlow(defaultTextInputViewState)

    private val enterBackupPasswordState = combine(
        selectedWallet,
        passwordInputViewState
    ) { selectedWallet, passwordInputViewState ->
        EnterBackupPasswordState(
            wallet = selectedWallet,
            passwordInputViewState = passwordInputViewState
        )
    }
    private val remoteWalletListState = remoteWallets.map { wallets ->
        RemoteWalletListState(wallets = wallets)
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = RemoteWalletListState())

    val state: StateFlow<ImportRemoteWalletState> = combine(
        currentStep,
        remoteWalletListState,
        enterBackupPasswordState,
        walletImportedState
    ) { currentStep, remoteWalletListState, enterBackupPasswordState, walletImportedState ->
        when (currentStep) {
            ImportRemoteWalletStep.WalletList -> remoteWalletListState
            ImportRemoteWalletStep.EnterBackupPassword -> enterBackupPasswordState
            ImportRemoteWalletStep.WalletImported -> walletImportedState
        }
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = remoteWalletListState.value)

    override fun onWalletSelected(backupAccount: EncryptedBackupAccount) {
        selectedWallet.value = backupAccount
        nextStep()
    }

    private fun nextStep() {
        if (hasNextStep()) {
            val nextStepIndex = steps.indexOf(currentStep.value) + 1
            currentStep.value = steps[nextStepIndex]
        }
    }

    private fun previousStep() {
        if (hasPreviousStep()) {
            val nextStepIndex = steps.indexOf(currentStep.value) - 1
            currentStep.value = steps[nextStepIndex]
        }
    }

    private fun hasNextStep(): Boolean {
        val nextStepIndex = steps.indexOf(currentStep.value) + 1
        return nextStepIndex in steps.indices
    }

    private fun hasPreviousStep(): Boolean {
        val previousStepIndex = steps.indexOf(currentStep.value) - 1
        return previousStepIndex in steps.indices
    }

    override fun onBackClick() {
        if (hasPreviousStep()) {
            previousStep()
        } else {
            accountRouter.back()
        }
    }

    override fun onCreateNewWallet() {
    }

    override fun onContinueClick() {
        when (currentStep.value) {
            ImportRemoteWalletStep.WalletList -> {
                /* ignore */
            }
            ImportRemoteWalletStep.EnterBackupPassword -> {
                decryptWalletByPassword()
            }
            ImportRemoteWalletStep.WalletImported -> {
                openMainScreen()
            }
        }
    }

    private fun decryptWalletByPassword() {
        // TODO Decrypt wallet
        nextStep()
    }

    private fun openMainScreen() {
    }

    override fun onPasswordChanged(password: String) {
        passwordInputViewState.value = passwordInputViewState.value.copy(
            text = password
        )
    }

    override fun onImportMore() {
    }
}
