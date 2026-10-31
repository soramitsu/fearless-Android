package jp.co.soramitsu.account.impl.presentation.importing.remote_backup

import android.content.Intent
import android.widget.LinearLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.account.api.presentation.backup.GoogleBackupIssue
import jp.co.soramitsu.account.api.presentation.backup.showGoogleBackupIssue
import jp.co.soramitsu.account.api.presentation.backup.toGoogleBackupIssue
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.model.BackupOrigin
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.model.WrappedBackupAccountMeta
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens.EnterBackupPasswordState
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens.RemoteWalletListError
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens.RemoteWalletListState
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens.WalletImportedState
import jp.co.soramitsu.backup.BackupService
import jp.co.soramitsu.backup.GoogleSignInResult
import jp.co.soramitsu.backup.domain.exceptions.AuthConsentException
import jp.co.soramitsu.backup.domain.exceptions.FileNotFoundException
import jp.co.soramitsu.backup.domain.models.BackupAccountMeta
import jp.co.soramitsu.backup.domain.models.BackupAccountType
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.TextInputViewState
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.core.crypto.SubstratePasswordRule
import jp.co.soramitsu.feature_account_impl.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
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

    private val isLoading = MutableStateFlow(false)
    private val steps = listOf(
        ImportRemoteWalletStep.WalletList,
        ImportRemoteWalletStep.EnterBackupPassword,
        ImportRemoteWalletStep.WalletImported
    )
    private val currentStep = MutableStateFlow(steps.first())
    private val remoteWallets = MutableStateFlow<List<WrappedBackupAccountMeta>?>(null)
    private val remoteWalletsIssue = MutableStateFlow<GoogleBackupIssue?>(null)

    private val selectedWallet = MutableStateFlow<WrappedBackupAccountMeta?>(null)
    private val walletImportedState = selectedWallet.map { selectedWallet ->
        WalletImportedState(selectedWallet)
    }

    val requestGoogleAuth = MutableSharedFlow<Event<Intent>>()

    /** Asks the screen to sign in to Google again with its activity result launcher. */
    val requestGoogleSignIn = MutableSharedFlow<Event<Unit>>()

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
        passwordInputViewState,
        isLoading
    ) { selectedWallet, passwordInputViewState, isLoading ->
        EnterBackupPasswordState(
            wallet = selectedWallet,
            passwordInputViewState = passwordInputViewState,
            isLoading = isLoading
        )
    }
    private val remoteWalletListState = combine(remoteWallets, remoteWalletsIssue) { wallets, issue ->
        RemoteWalletListState(wallets = wallets, error = issue?.let(::createRemoteWalletListError))
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

    override fun onWalletSelected(backupAccount: WrappedBackupAccountMeta) {
        selectedWallet.value = backupAccount
        resetPasswordField()
        nextStep()
    }

    private fun resetPasswordField() {
        isPasswordVisible.value = false
        passwordText.value = ""
    }

    override fun onWalletLongClick(backupAccount: WrappedBackupAccountMeta) {
        if (!BuildConfig.DEBUG) return
        if (backupAccount.origin == BackupOrigin.WEB) {
            showError(
                title = resourceManager.getString(R.string.common_warning),
                message = resourceManager.getString(R.string.remove_backup_extension_error_message)
            )
        } else {
            showError(
                title = resourceManager.getString(R.string.common_confirmation_title),
                message = resourceManager.getString(R.string.backup_wallet_delete_alert_message),
                positiveButtonText = resourceManager.getString(R.string.common_delete),
                negativeButtonText = resourceManager.getString(R.string.common_cancel),
                buttonsOrientation = LinearLayout.HORIZONTAL,
                positiveClick = { deleteBackup(backupAccount) }
            )
        }
    }

    private fun deleteBackup(backupAccount: WrappedBackupAccountMeta) {
        launch {
            try {
                // Only the copy this entry shows; other devices' copies of the wallet stay on Drive.
                val meta = backupAccount.backupMeta
                backupService.deleteBackupAccount(meta.address, listOfNotNull(meta.fileId))
            } catch (e: FileNotFoundException) {
                // Already gone from Drive, so it leaves the list as well.
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showGoogleBackupIssue(resourceManager, e.toGoogleBackupIssue())
                return@launch
            }
            remoteWallets.value = remoteWallets.value?.minus(backupAccount)
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
            remoteWalletsIssue.value = null
            try {
                val backupAccounts = backupService.getBackupAccounts().map(::getWrapped)
                val backupAddresses = backupAccounts.map { it.backupMeta.address }.toSet()

                val webBackupNotInCommonBackup = backupService.getWebBackupAccounts()
                    // Substrate-only extension backups all have an empty address; only file ids tell them apart.
                    .distinctBy { it.address.ifEmpty { it.fileId } }
                    .filter { it.address !in backupAddresses }
                    .map { getWrapped(it, origin = BackupOrigin.WEB) }

                remoteWallets.value = webBackupNotInCommonBackup + backupAccounts
            } catch (e: AuthConsentException) {
                requestGoogleAuth.emit(Event(e.intent))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                remoteWalletsIssue.value = e.toGoogleBackupIssue()
            }
        }
    }

    override fun onErrorActionClick() {
        if (remoteWalletsIssue.value == GoogleBackupIssue.SIGN_IN_REQUIRED) {
            requestSignIn()
        } else {
            loadRemoteWallets()
        }
    }

    override fun onImportAnotherWayClick() {
        accountRouter.openImportAccountScreen(WalletEcosystem.Substrate, ImportMode.MnemonicPhrase)
    }

    // An empty list often means the backups are in another Google account; signing in again picks one.
    override fun onUseAnotherGoogleAccountClick() {
        requestSignIn()
    }

    fun signInToGoogle(launcher: ActivityResultLauncher<Intent>) {
        viewModelScope.launch {
            backupService.logout()
            if (backupService.authorize(launcher)) {
                onGoogleAuthorized()
            }
        }
    }

    /** Google sign-in or the Drive permission screen succeeded: repeat what needed it. */
    fun onGoogleAuthorized() {
        when (currentStep.value) {
            ImportRemoteWalletStep.WalletList -> loadRemoteWallets()
            ImportRemoteWalletStep.EnterBackupPassword -> decryptWalletByPassword()
            ImportRemoteWalletStep.WalletImported -> Unit
        }
    }

    fun onGoogleSignInFailed(result: GoogleSignInResult) {
        // Closing the permission screen leaves Drive out of reach, so it needs a way back as well.
        val issue = result.toGoogleBackupIssue() ?: GoogleBackupIssue.SIGN_IN_REQUIRED
        if (currentStep.value == ImportRemoteWalletStep.WalletList) {
            remoteWalletsIssue.value = issue
        } else {
            showImportIssue(issue)
        }
    }

    private fun requestSignIn() {
        launch {
            requestGoogleSignIn.emit(Event(Unit))
        }
    }

    private fun createRemoteWalletListError(issue: GoogleBackupIssue): RemoteWalletListError {
        val actionRes = when {
            issue == GoogleBackupIssue.SIGN_IN_REQUIRED -> R.string.google_backup_sign_in
            issue.isRetryable -> R.string.common_try_again
            else -> null
        }
        return RemoteWalletListError(
            title = resourceManager.getString(issue.titleRes),
            message = resourceManager.getString(issue.messageRes),
            actionText = actionRes?.let(resourceManager::getString)
        )
    }

    private fun getWrapped(backupMeta: BackupAccountMeta, origin: BackupOrigin = BackupOrigin.APP): WrappedBackupAccountMeta {
        return WrappedBackupAccountMeta(backupMeta, origin)
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
        accountRouter.openCreateWalletDialogFromGoogleBackup()
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
        val backupAccountMeta = selectedWallet.value ?: return
        isLoading.value = true
        viewModelScope.launch {
            try {
                val decryptedBackupAccount = when (backupAccountMeta.origin) {
                    BackupOrigin.APP -> backupService.importBackupAccount(
                        address = backupAccountMeta.backupMeta.address,
                        password = passwordText.value
                    )

                    // By Drive file id: Substrate-only extension backups share an empty address.
                    BackupOrigin.WEB -> backupService.importWebBackupAccount(backupAccountMeta.backupMeta)
                }

                if (decryptedBackupAccount.backupAccountType.contains(BackupAccountType.JSON)) {
                    val json = decryptedBackupAccount.json?.substrateJson ?: decryptedBackupAccount.json?.ethJson ?: error("No JSON backup found")
                    interactor.validateJsonBackup(json, passwordText.value)
                }

                val restoringSecretTypes = backupService.restoringSecretTypes(decryptedBackupAccount)
                // A wallet imported with an earlier browser extension's account restores that account.
                val passwordRule = backupService.restoringSubstratePasswordRule(decryptedBackupAccount)
                    ?: SubstratePasswordRule.NFKD
                importLegacyGoogleBackupAccount(
                    interactor,
                    decryptedBackupAccount,
                    passwordText.value,
                    restoringSecretTypes,
                    passwordRule
                )
                nextStep()
            } catch (e: AuthConsentException) {
                requestGoogleAuth.emit(Event(e.intent))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showImportIssue(e.toGoogleBackupIssue())
            } finally {
                isLoading.value = false
            }
        }
    }

    private fun showImportIssue(issue: GoogleBackupIssue) {
        when (issue) {
            // Closing the message returns to the password field.
            GoogleBackupIssue.WRONG_PASSWORD -> showGoogleBackupIssue(
                resourceManager,
                issue,
                positiveButtonRes = R.string.common_try_again
            )

            GoogleBackupIssue.ALREADY_ON_DEVICE -> showGoogleBackupIssue(
                resourceManager,
                issue,
                positiveButtonRes = R.string.google_backup_open_wallet,
                positiveClick = ::openMainScreen,
                negativeButtonRes = R.string.common_close
            )

            // The list screen reloads from Drive when it is shown again.
            GoogleBackupIssue.BACKUP_NOT_FOUND -> showGoogleBackupIssue(
                resourceManager,
                issue,
                positiveButtonRes = R.string.google_backup_refresh,
                positiveClick = ::previousStep
            )

            GoogleBackupIssue.SIGN_IN_REQUIRED -> showGoogleBackupIssue(
                resourceManager,
                issue,
                positiveButtonRes = R.string.google_backup_sign_in,
                positiveClick = ::requestSignIn,
                negativeButtonRes = R.string.common_close
            )

            GoogleBackupIssue.NO_CONNECTION,
            GoogleBackupIssue.STORAGE_FULL,
            GoogleBackupIssue.UNKNOWN -> showGoogleBackupIssue(
                resourceManager,
                issue,
                positiveButtonRes = R.string.common_try_again,
                positiveClick = ::decryptWalletByPassword,
                negativeButtonRes = R.string.common_close
            )

            GoogleBackupIssue.UNSUPPORTED_BACKUP,
            GoogleBackupIssue.ETHEREUM_ONLY_BACKUP,
            GoogleBackupIssue.UNAVAILABLE,
            GoogleBackupIssue.SECRET_NOT_VERIFIED -> showGoogleBackupIssue(
                resourceManager,
                issue,
                positiveButtonRes = R.string.google_backup_import_another_way,
                positiveClick = ::onImportAnotherWayClick,
                negativeButtonRes = R.string.common_close
            )
        }
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
