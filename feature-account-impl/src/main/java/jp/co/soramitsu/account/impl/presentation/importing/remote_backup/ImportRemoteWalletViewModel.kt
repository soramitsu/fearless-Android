package jp.co.soramitsu.account.impl.presentation.importing.remote_backup

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
    private val remoteWallets = MutableStateFlow(emptyList<BackupAccountMeta>())

    private val selectedWallet = MutableStateFlow<BackupAccountMeta?>(null)
    private val walletImportedState = selectedWallet.map { selectedWallet ->
        WalletImportedState(selectedWallet)
    }

    private val defaultTextInputViewState = TextInputViewState(
        text = "",
        hint = "Enter password",
        placeholder = "",
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
    private val remoteWalletListState = combine(
        remoteWallets,
        interactor.getMetaAccountsGoogleAddresses()
    ) { wallets, localWalletAddresses ->
        val remoteWalletListState1 = RemoteWalletListState(wallets = wallets.filter { it.address !in localWalletAddresses })
        println("!!! wallets: ${wallets.size}")
        println("!!! localWalletAddresses: ${localWalletAddresses.size}")
        remoteWalletListState1
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
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = remoteWalletListState.value) as StateFlow<ImportRemoteWalletState>

    override fun onWalletSelected(backupAccount: BackupAccountMeta) {
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
        /*
                if (hasPreviousStep()) {
                    if (currentStep.value == ImportRemoteWalletStep.WalletImported) {
                        openMainScreen()
                    } else {
                        previousStep()
                    }
                } else {
                    accountRouter.back()
                }
        */
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
        passwordInputViewState.value = passwordInputViewState.value.copy(
            text = password
        )
    }

    override fun onImportMore() {
        accountRouter.openImportRemoteWalletDialog()
    }
}
