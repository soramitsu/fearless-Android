package jp.co.soramitsu.account.impl.presentation.create_backup_password

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.presentation.create_backup_password.CreateBackupPasswordPayload
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.backup.BackupService
import jp.co.soramitsu.backup.domain.models.DecryptedBackupAccount
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.TextInputViewState
import jp.co.soramitsu.common.compose.component.TextSelectableItemState
import jp.co.soramitsu.feature_account_impl.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import jp.co.soramitsu.common.utils.combine as combineFlows

@HiltViewModel
class CreateBackupPasswordViewModel @Inject constructor(
    private val accountRouter: AccountRouter,
    private val backupService: BackupService,
    private val savedStateHandle: SavedStateHandle,
    private val interactor: AccountInteractor
) : BaseViewModel(), CreateBackupPasswordCallback {

    private val payload = savedStateHandle.get<CreateBackupPasswordPayload>(CreateBackupPasswordDialog.PAYLOAD_KEY)!!

    private val originPassword = MutableStateFlow("")
    private val confirmPassword = MutableStateFlow("")
    private val isUserAgreedWithStatements = MutableStateFlow(false)
    private val isAgreementsChecked = MutableStateFlow(false)
    private val passwordMatchingTextResource = combine(originPassword, confirmPassword) {
            originPassword, confirmPassword ->
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
    private val arePasswordsMatched = combine(originPassword, confirmPassword) {
            originPassword, confirmPassword ->
        originPassword.isNotEmpty() &&
            confirmPassword.length >= originPassword.length &&
            confirmPassword == originPassword
    }

    private val highlightConfirmPassword = combine(originPassword, confirmPassword) {
            originPassword, confirmPassword ->
        originPassword.isNotEmpty() &&
            confirmPassword.length >= originPassword.length &&
            confirmPassword != originPassword
    }
    private val isSetButtonEnabled = combine(arePasswordsMatched, isAgreementsChecked) {
            arePasswordsMatched, isAgreementsChecked ->
        arePasswordsMatched && isAgreementsChecked
    }

    private val initialState = CreateBackupPasswordViewState(
        originPasswordViewState = createTextInputViewState(hint = "Set password", originPassword.value),
        confirmPasswordViewState = createTextInputViewState(hint = "Confirm password", confirmPassword.value),
        isUserAgreedWithStatements = isUserAgreedWithStatements.value,
        agreementsState = TextSelectableItemState(
            isSelected = isAgreementsChecked.value,
            textResId = jp.co.soramitsu.common.R.string.create_backup_password_agreements
        ),
        passwordMatchingTextResource = null,
        highlightConfirmPassword = false,
        isSetButtonEnabled = true
    )
    val state = combineFlows(
        originPassword,
        confirmPassword,
        isUserAgreedWithStatements,
        isAgreementsChecked,
        passwordMatchingTextResource,
        highlightConfirmPassword,
        isSetButtonEnabled
    ) {
            originPassword, confirmPassword, isUserAgreedWithStatements,
            isAgreementsChecked, passwordMatchingTextResource,
            highlightConfirmPassword, isSetButtonEnabled ->
        CreateBackupPasswordViewState(
            originPasswordViewState = createTextInputViewState(hint = "Set password", password = originPassword),
            confirmPasswordViewState = createTextInputViewState(hint = "Confirm password", password = confirmPassword),
            isUserAgreedWithStatements = isUserAgreedWithStatements,
            agreementsState = TextSelectableItemState(
                isSelected = isAgreementsChecked,
                textResId = R.string.create_backup_password_agreements
            ),
            passwordMatchingTextResource = passwordMatchingTextResource,
            highlightConfirmPassword = highlightConfirmPassword,
            isSetButtonEnabled = isSetButtonEnabled
        )
    }.stateIn(viewModelScope, started = SharingStarted.Eagerly, initialValue = initialState)

    private fun createTextInputViewState(
        hint: String,
        password: String
    ): TextInputViewState {
        return TextInputViewState(
            hint = hint,
            text = password,
            mode = TextInputViewState.Mode.Password
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
        accountRouter.back()
    }

    override fun onApplyPasswordClick() {
        viewModelScope.launch {
            runCatching {
                importFromMnemonic()
                saveBackupAccount()
            }
                .onSuccess {
                    continueBasedOnCodeStatus()
                }
                .onFailure {
                    // TODO: Handle create account error
                    // handleCreateAccountError(result.requireException())
                }
        }
    }

    private suspend fun importFromMnemonic() {
        interactor.importFromMnemonic(
            walletName = payload.accountName,
            mnemonic = payload.mnemonic,
            substrateDerivationPath = payload.substrateDerivationPath,
            ethereumDerivationPath = payload.ethereumDerivationPath,
            selectedEncryptionType = payload.cryptoType,
            withEth = true
        ).getOrThrow()
    }

    private suspend fun saveBackupAccount() {
        val password = originPassword.value
        backupService.saveBackupAccount(
            account = DecryptedBackupAccount(
                name = payload.accountName,
                address = UUID.randomUUID().toString(),
                mnemonicPhrase = payload.mnemonic,
                substrateDerivationPath = payload.substrateDerivationPath,
                ethDerivationPath = payload.ethereumDerivationPath,
                cryptoType = payload.cryptoType,
                backupAccountType = listOf(), // TODO: Backup fix
                seed = null, // TODO: Backup fix
                json = null // TODO: Backup fix
            ),
            password = password
        )
    }

    private suspend fun continueBasedOnCodeStatus() {
        if (interactor.isCodeSet()) {
            accountRouter.openMain()
        } else {
            accountRouter.openCreatePincode()
        }
    }

    override fun onAgreementsClick() {
        isAgreementsChecked.value = !isAgreementsChecked.value
    }
}
