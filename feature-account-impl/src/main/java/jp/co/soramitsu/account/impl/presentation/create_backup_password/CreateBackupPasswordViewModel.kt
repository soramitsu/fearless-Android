package jp.co.soramitsu.account.impl.presentation.create_backup_password

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.presentation.create_backup_password.SaveBackupPayload
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.TextInputViewState
import jp.co.soramitsu.common.compose.component.TextSelectableItemState
import jp.co.soramitsu.common.resources.ResourceManager
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
    private val savedStateHandle: SavedStateHandle,
    private val interactor: AccountInteractor,
    private val resourceManager: ResourceManager
) : BaseViewModel(), CreateBackupPasswordCallback {

    private val payload = savedStateHandle.get<SaveBackupPayload?>(CreateBackupPasswordDialog.PAYLOAD_KEY)

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
        accountRouter.backWithResult(CreateBackupPasswordDialog.RESULT_BACKUP_KEY to Activity.RESULT_CANCELED)
    }

    override fun onApplyPasswordClick() {
        isLoading.value = true

        viewModelScope.launch {
            runCatching {
                val createResult = payload?.addAccountPayload?.let {
                    interactor.createAccount(it)
                }

                val walletId = createResult?.getOrNull() ?: payload?.walletId
                walletId?.let {
                    interactor.saveGoogleBackupAccount(
                        walletId,
                        originPassword.value
                    )
                }
                walletId
            }
                .onSuccess { walletId ->
                    walletId?.let {
                        interactor.updateWalletBackedUp(walletId)
                    }
                    continueBasedOnCodeStatus()
                    isLoading.value = false
                }
                .onFailure {
                    isLoading.value = false
                    showError(it)
                }
        }
    }

    private fun continueBasedOnCodeStatus() {
        accountRouter.backWithResult(CreateBackupPasswordDialog.RESULT_BACKUP_KEY to Activity.RESULT_OK)
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
}
