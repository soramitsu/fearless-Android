package jp.co.soramitsu.account.impl.presentation.create_backup_password

import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.presentation.backup.GoogleBackupIssue
import jp.co.soramitsu.account.api.presentation.backup.showGoogleBackupIssue
import jp.co.soramitsu.account.api.presentation.backup.toGoogleBackupIssue
import jp.co.soramitsu.account.api.presentation.create_backup_password.SaveBackupPayload
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.backup.GoogleSignInResult
import jp.co.soramitsu.backup.domain.exceptions.AuthConsentException
import jp.co.soramitsu.backup.domain.models.BackupAccountType
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.TextInputViewState
import jp.co.soramitsu.common.compose.component.TextSelectableItemState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_account_impl.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import jp.co.soramitsu.common.utils.combine as combineFlows

@HiltViewModel
class CreateBackupPasswordViewModel @Inject constructor(
    private val accountRouter: AccountRouter,
    private val savedStateHandle: SavedStateHandle,
    private val interactor: AccountInteractor,
    private val resourceManager: ResourceManager
) : BaseViewModel(), CreateBackupPasswordCallback {

    private val payload = savedStateHandle.get<SaveBackupPayload?>(CreateBackupPasswordDialog.PAYLOAD_KEY)

    // Kept across retries and a recreated screen, so one backup attempt never creates the wallet twice.
    private var createdWalletId: Long?
        get() = savedStateHandle.get<Long>(CREATED_WALLET_ID_KEY)
        set(value) {
            savedStateHandle[CREATED_WALLET_ID_KEY] = value
        }
    private var isBackupUploaded = false
    private var leftOutBackupTypes: Set<BackupAccountType> = emptySet()

    /** True once this screen created the wallet; leaving then hands the wallet to the caller. */
    val isWalletCreated = MutableStateFlow(createdWalletId != null)

    /** Asks the screen to open a Google Drive permission screen. */
    val requestGoogleConsent = MutableSharedFlow<Event<Intent>>()

    /** Asks the screen to sign in to Google again with its activity result launcher. */
    val requestGoogleSignIn = MutableSharedFlow<Event<Unit>>()

    private val originPassword = MutableStateFlow("")
    private val confirmPassword = MutableStateFlow("")
    private val isUserAgreedWithStatements = MutableStateFlow(false)
    private val isAgreementsChecked = MutableStateFlow(false)
    private val isLoading = MutableStateFlow(false)
    private val isOriginPasswordVisible = MutableStateFlow(false)
    private val isConfirmPasswordVisible = MutableStateFlow(false)
    private val passwordMatchingTextResource = combine(originPassword, confirmPassword) { originPassword, confirmPassword ->
        if (originPassword.isNotEmpty() && confirmPassword.length >= originPassword.length) {
            if (confirmPassword == originPassword) {
                R.string.create_backup_password_matched
            } else {
                R.string.create_backup_password_not_matched
            }
        } else {
            null
        }
    }
    private val arePasswordsMatched = combine(originPassword, confirmPassword) { originPassword, confirmPassword ->
        originPassword.isNotEmpty() &&
            confirmPassword.length >= originPassword.length &&
            confirmPassword == originPassword
    }

    private val highlightConfirmPassword = combine(originPassword, confirmPassword) { originPassword, confirmPassword ->
        originPassword.isNotEmpty() &&
            confirmPassword.length >= originPassword.length &&
            confirmPassword != originPassword
    }
    private val isSetButtonEnabled = combine(arePasswordsMatched, isAgreementsChecked) { arePasswordsMatched, isAgreementsChecked ->
        arePasswordsMatched && isAgreementsChecked
    }

    private val initialState = CreateBackupPasswordViewState(
        originPasswordViewState = createTextInputViewState(
            hint = resourceManager.getString(R.string.export_json_password_new),
            password = originPassword.value,
            isPasswordVisible = false
        ),
        confirmPasswordViewState = createTextInputViewState(
            hint = resourceManager.getString(R.string.export_json_password_confirm),
            password = confirmPassword.value,
            isPasswordVisible = false
        ),
        isUserAgreedWithStatements = isUserAgreedWithStatements.value,
        agreementsState = TextSelectableItemState(
            isSelected = isAgreementsChecked.value,
            textResId = jp.co.soramitsu.common.R.string.create_backup_password_agreements
        ),
        passwordMatchingTextResource = null,
        highlightConfirmPassword = false,
        isSetButtonEnabled = true,
        isLoading = false
    )
    val state = combineFlows(
        originPassword,
        confirmPassword,
        isUserAgreedWithStatements,
        isAgreementsChecked,
        passwordMatchingTextResource,
        highlightConfirmPassword,
        isSetButtonEnabled,
        isLoading,
        isOriginPasswordVisible,
        isConfirmPasswordVisible
    ) { originPassword, confirmPassword, isUserAgreedWithStatements, isAgreementsChecked, passwordMatchingTextResource,
        highlightConfirmPassword, isSetButtonEnabled, isLoading, isOriginPasswordVisible, isConfirmPasswordVisible ->
        CreateBackupPasswordViewState(
            originPasswordViewState = createTextInputViewState(
                hint = resourceManager.getString(R.string.export_json_password_new),
                password = originPassword,
                isPasswordVisible = isOriginPasswordVisible
            ),
            confirmPasswordViewState = createTextInputViewState(
                hint = resourceManager.getString(R.string.export_json_password_confirm),
                password = confirmPassword,
                isPasswordVisible = isConfirmPasswordVisible
            ),
            isUserAgreedWithStatements = isUserAgreedWithStatements,
            agreementsState = TextSelectableItemState(
                isSelected = isAgreementsChecked,
                textResId = R.string.create_backup_password_agreements
            ),
            passwordMatchingTextResource = passwordMatchingTextResource,
            highlightConfirmPassword = highlightConfirmPassword,
            isSetButtonEnabled = isSetButtonEnabled,
            isLoading = isLoading
        )
    }.stateIn(viewModelScope, started = SharingStarted.Eagerly, initialValue = initialState)

    private fun createTextInputViewState(
        hint: String,
        password: String,
        isPasswordVisible: Boolean
    ): TextInputViewState {
        return TextInputViewState(
            hint = hint,
            text = password,
            mode = if (isPasswordVisible) TextInputViewState.Mode.Text else TextInputViewState.Mode.Password,
            endIcon = if (isPasswordVisible) R.drawable.ic_eye_enabled else R.drawable.ic_eye_disabled
        )
    }

    override fun onOriginPasswordChange(password: String) {
        originPassword.value = password
    }

    override fun onConfirmPasswordChange(password: String) {
        confirmPassword.value = password
    }

    override fun onIsUserAgreedWithStatementsChange(isChecked: Boolean) {
        isUserAgreedWithStatements.value = isChecked
    }

    override fun onBackClick() {
        backUpLater()
    }

    override fun onApplyPasswordClick() {
        if (isLoading.value) return
        isLoading.value = true

        viewModelScope.launch {
            try {
                val walletId = createdWalletId ?: payload?.walletId ?: createWallet()
                if (!isBackupUploaded) {
                    leftOutBackupTypes = interactor.saveGoogleBackupAccount(walletId, originPassword.value)
                    isBackupUploaded = true
                }
                // Only a backup that reached Drive marks the wallet as backed up.
                interactor.updateWalletBackedUp(walletId)
                if (leftOutBackupTypes.isEmpty()) {
                    continueBasedOnCodeStatus()
                } else {
                    showBackedUpWithoutPhrase()
                }
            } catch (e: AuthConsentException) {
                requestGoogleConsent.emit(Event(e.intent))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showBackupIssue(e.toGoogleBackupIssue())
            } finally {
                isLoading.value = false
            }
        }
    }

    fun signInToGoogle(launcher: ActivityResultLauncher<Intent>) {
        viewModelScope.launch {
            if (interactor.authorizeGoogleBackup(launcher)) {
                onApplyPasswordClick()
            }
        }
    }

    /** Google sign-in or the Drive permission screen succeeded: try the backup again. */
    fun onGoogleAuthorized() {
        onApplyPasswordClick()
    }

    fun onGoogleSignInFailed(result: GoogleSignInResult) {
        // Backing out of sign-in leaves the password screen, where the backup can be started again.
        val issue = result.toGoogleBackupIssue() ?: return
        showBackupIssue(issue)
    }

    private suspend fun createWallet(): Long {
        val addAccountPayload = requireNotNull(payload?.addAccountPayload) { "No wallet to back up" }
        return interactor.createAccount(addAccountPayload).getOrThrow().also { walletId ->
            createdWalletId = walletId
            isWalletCreated.value = true
        }
    }

    private fun showBackupIssue(issue: GoogleBackupIssue) {
        val message = resourceManager.getString(issue.messageRes).let { message ->
            if (createdWalletId != null) {
                message + "\n\n" + resourceManager.getString(R.string.google_backup_wallet_created_not_backed_up)
            } else {
                message
            }
        }

        when (issue) {
            GoogleBackupIssue.SIGN_IN_REQUIRED -> showGoogleBackupIssue(
                resourceManager,
                issue,
                message = message,
                positiveButtonRes = R.string.google_backup_sign_in,
                positiveClick = ::requestSignIn,
                negativeButtonRes = R.string.google_backup_back_up_later,
                negativeClick = ::backUpLater
            )

            // An earlier attempt already created this wallet; it only has to be opened.
            GoogleBackupIssue.ALREADY_ON_DEVICE -> showGoogleBackupIssue(
                resourceManager,
                issue,
                message = message,
                positiveButtonRes = R.string.google_backup_open_wallet,
                positiveClick = { finishWith(CreateBackupPasswordDialog.RESULT_BACKUP_SKIPPED) }
            )

            // Trying again cannot help: the previous screen offers the other ways to back up.
            GoogleBackupIssue.SECRET_NOT_VERIFIED,
            GoogleBackupIssue.UNAVAILABLE -> showGoogleBackupIssue(
                resourceManager,
                issue,
                message = message,
                positiveButtonRes = R.string.common_ok,
                positiveClick = ::backUpLater
            )

            else -> showGoogleBackupIssue(
                resourceManager,
                issue,
                message = message,
                positiveButtonRes = R.string.common_try_again,
                positiveClick = ::onApplyPasswordClick,
                negativeButtonRes = R.string.google_backup_back_up_later,
                negativeClick = ::backUpLater
            )
        }
    }

    // The uploaded seed or JSON restores this wallet; say why its phrase is not in the backup.
    private fun showBackedUpWithoutPhrase() {
        showError(
            title = resourceManager.getString(R.string.google_backup_saved_without_phrase_title),
            message = resourceManager.getString(R.string.google_backup_saved_without_phrase_message),
            positiveButtonText = resourceManager.getString(R.string.common_continue),
            positiveClick = ::continueBasedOnCodeStatus,
            onBackClick = ::continueBasedOnCodeStatus
        )
    }

    private fun requestSignIn() {
        launch {
            requestGoogleSignIn.emit(Event(Unit))
        }
    }

    /** Leaves without a backup. A wallet created here stays, not marked as backed up, so the app keeps asking. */
    private fun backUpLater() {
        finishWith(if (createdWalletId != null) CreateBackupPasswordDialog.RESULT_BACKUP_SKIPPED else Activity.RESULT_CANCELED)
    }

    private fun continueBasedOnCodeStatus() {
        finishWith(Activity.RESULT_OK)
    }

    private fun finishWith(resultCode: Int) {
        accountRouter.backWithResult(CreateBackupPasswordDialog.RESULT_BACKUP_KEY to resultCode)
    }

    override fun onAgreementsClick() {
        isAgreementsChecked.value = !isAgreementsChecked.value
    }

    override fun onOriginPasswordVisibilityClick() {
        isOriginPasswordVisible.value = isOriginPasswordVisible.value.not()
    }

    override fun onConfirmPasswordVisibilityClick() {
        isConfirmPasswordVisible.value = isConfirmPasswordVisible.value.not()
    }

    private companion object {
        const val CREATED_WALLET_ID_KEY = "created_wallet_id"
    }
}
