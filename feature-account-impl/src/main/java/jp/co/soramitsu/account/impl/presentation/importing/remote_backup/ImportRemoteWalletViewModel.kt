package jp.co.soramitsu.account.impl.presentation.importing.remote_backup

import android.widget.LinearLayout
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens.EnterBackupPasswordState
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens.RemoteWalletListState
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens.WalletImportedState
import jp.co.soramitsu.backup.BackupService
import jp.co.soramitsu.backup.domain.models.BackupAccountMeta
import jp.co.soramitsu.backup.domain.models.DecryptedBackupAccount
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.TextInputViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_account_impl.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ImportRemoteWalletViewModel @Inject constructor(
    private val accountRouter: AccountRouter,
    private val backupService: BackupService,
    private val interactor: AccountInteractor,
    private val resourceManager: ResourceManager
) : BaseViewModel(), ImportRemoteWalletCallback {

    private val steps = listOf(
        ImportRemoteWalletStep.WalletList,
        ImportRemoteWalletStep.EnterBackupPassword,
        ImportRemoteWalletStep.WalletImported
    )
    private val currentStep = MutableStateFlow(steps.first())
    private val remoteWallets = MutableStateFlow<List<BackupAccountMeta>?>(null)

    private val selectedWallet = MutableStateFlow<BackupAccountMeta?>(null)
    private val walletImportedState = selectedWallet.map { selectedWallet ->
        WalletImportedState(selectedWallet)
    }

    private val defaultTextInputViewState = TextInputViewState(
        text = "",
        hint = resourceManager.getString(R.string.import_remote_wallet_hint_enter_password),
        endIcon = R.drawable.ic_eye_disabled,
        mode = TextInputViewState.Mode.Password
    )

    private val isPasswordVisible = MutableStateFlow(false)
    private val passwordText = MutableStateFlow("")
    private val passwordInputViewState = combine(
        passwordText,
        isPasswordVisible
    ) { password, isVisible ->
        TextInputViewState(
            text = password,
            hint = resourceManager.getString(R.string.import_remote_wallet_hint_enter_password),
            endIcon = if (isVisible) R.drawable.ic_eye_enabled else R.drawable.ic_eye_disabled,
            mode = if (isVisible) TextInputViewState.Mode.Text else TextInputViewState.Mode.Password
        )
    }.stateIn(this, SharingStarted.Eagerly, defaultTextInputViewState)

    private val enterBackupPasswordState = combine(
        selectedWallet,
        passwordInputViewState
    ) { selectedWallet, passwordInputViewState ->
        EnterBackupPasswordState(
            wallet = selectedWallet,
            passwordInputViewState = passwordInputViewState
        )
    }
    private val remoteWalletListState = combine(
        remoteWallets,
        interactor.getMetaAccountsGoogleAddresses()
    ) { wallets, localWalletAddresses ->
        RemoteWalletListState(wallets = wallets?.filter { it.address !in localWalletAddresses })
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

    override fun onWalletSelected(backupAccount: BackupAccountMeta) {
        selectedWallet.value = backupAccount
        resetPasswordField()
        nextStep()
    }

    private fun resetPasswordField() {
        isPasswordVisible.value = false
        passwordText.value = ""
    }

    override fun onWalletLongClick(backupAccount: BackupAccountMeta) {
        if (!BuildConfig.DEBUG) return
        showError(
            title = resourceManager.getString(R.string.common_confirmation_title),
            message = resourceManager.getString(R.string.backup_wallet_delete_alert_message),
            positiveButtonText = resourceManager.getString(R.string.common_delete),
            negativeButtonText = resourceManager.getString(R.string.common_cancel),
            buttonsOrientation = LinearLayout.HORIZONTAL
        ) {
            launch {
                backupService.deleteBackupAccount(backupAccount.address)
                val current = remoteWallets.value
                val new = current?.minus(backupAccount)
                remoteWallets.value = new
            }
        }
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

    override fun loadRemoteWallets() {
        viewModelScope.launch {
            remoteWallets.value = backupService.getBackupAccounts()
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
        backClicked()
    }

    override fun onCreateNewWallet() {
        accountRouter.openCreateWalletDialog(true)
    }

    fun backClicked() {
        when (currentStep.value) {
            ImportRemoteWalletStep.WalletList -> {
                accountRouter.back()
            }

            ImportRemoteWalletStep.EnterBackupPassword -> {
                previousStep()
            }

            ImportRemoteWalletStep.WalletImported -> {
                openMainScreen()
            }
        }
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
        viewModelScope.launch {
            runCatching {
                val decryptedBackupAccount = backupService.importBackupAccount(
                    address = selectedWallet.value!!.address,
                    password = passwordInputViewState.value.text
                )
                importFromMnemonic(decryptedBackupAccount)
                nextStep()
            }
                .onFailure {
                    handleDecryptException(it)
                }
        }
    }

    private fun handleDecryptException(throwable: Throwable) {
        throwable.printStackTrace()
        showError(resourceManager.getString(R.string.import_json_invalid_password))
    }

    private suspend fun importFromMnemonic(
        decryptedBackupAccount: DecryptedBackupAccount
    ) {
        interactor.importFromMnemonic(
            walletName = decryptedBackupAccount.name,
            mnemonic = decryptedBackupAccount.mnemonicPhrase.orEmpty(), // TODO: Backup fix
            substrateDerivationPath = decryptedBackupAccount.substrateDerivationPath.orEmpty(), // TODO: Backup fix
            ethereumDerivationPath = decryptedBackupAccount.ethDerivationPath.orEmpty(), // TODO: Backup fix
            selectedEncryptionType = decryptedBackupAccount.cryptoType,
            withEth = true,
            isBackedUp = true,
            googleBackupAddress = decryptedBackupAccount.address
        ).getOrThrow()
    }

    private fun openMainScreen() {
        launch {
            if (interactor.isCodeSet()) {
                accountRouter.openMain()
            } else {
                accountRouter.openCreatePincode()
            }
        }
    }

    override fun onPasswordChanged(password: String) {
        passwordText.value = password
    }

    override fun onImportMore() {
        accountRouter.openImportRemoteWalletDialog()
    }

    override fun onPasswordVisibilityClick() {
        isPasswordVisible.value = isPasswordVisible.value.not()
    }
}
