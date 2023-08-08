package jp.co.soramitsu.account.impl.presentation.create_backup_password

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.B0
import jp.co.soramitsu.common.compose.component.B2
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.TextInput
import jp.co.soramitsu.common.compose.component.TextInputViewState
import jp.co.soramitsu.common.compose.component.TextSelectableItem
import jp.co.soramitsu.common.compose.component.TextSelectableItemState
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.colorAccent
import jp.co.soramitsu.common.compose.theme.gray2
import jp.co.soramitsu.common.compose.theme.white24

data class CreateBackupPasswordViewState(
    val originPasswordViewState: TextInputViewState,
    val confirmPasswordViewState: TextInputViewState,
    val isUserAgreedWithStatements: Boolean,
    val agreementsState: TextSelectableItemState,
    @StringRes val passwordMatchingTextResource: Int?,
    val highlightConfirmPassword: Boolean,
    val isSetButtonEnabled: Boolean,
    val isLoading: Boolean,
    val heightDiffDp: Dp
)

interface CreateBackupPasswordCallback {

    fun onOriginPasswordChange(password: String)

    fun onConfirmPasswordChange(password: String)

    fun onIsUserAgreedWithStatementsChange(isChecked: Boolean)

    fun onBackClick()

    fun onApplyPasswordClick()

    fun onAgreementsClick()

    fun onOriginPasswordVisibilityClick()

    fun onConfirmPasswordVisibilityClick()
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun CreateBackupPasswordContent(
    state: CreateBackupPasswordViewState,
    callback: CreateBackupPasswordCallback
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    BottomSheetScreen {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = state.heightDiffDp)
        ) {
            Column {
                Toolbar(
                    modifier = Modifier.padding(bottom = 12.dp),
                    state = ToolbarViewState(
                        title = stringResource(R.string.create_backup_password_title),
                        navigationIcon = R.drawable.ic_arrow_back_24dp
                    ),
                    onNavigationClick = callback::onBackClick
                )
                MarginVertical(margin = 24.dp)

                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                        .weight(1f)
                ) {
                    B0(
                        text = stringResource(R.string.create_backup_password_subtitle),
                        color = gray2
                    )
                    MarginVertical(margin = 16.dp)
                    TextInput(
                        modifier = Modifier.focusRequester(focusRequester),
                        state = state.originPasswordViewState,
                        onInput = callback::onOriginPasswordChange,
                        onEndIconClick = callback::onOriginPasswordVisibilityClick
                    )
                    MarginVertical(margin = 16.dp)
                    TextInput(
                        state = state.confirmPasswordViewState,
                        onInput = callback::onConfirmPasswordChange,
                        onEndIconClick = callback::onConfirmPasswordVisibilityClick,
                        borderColor = if (state.highlightConfirmPassword) {
                            colorAccent
                        } else {
                            white24
                        }
                    )
                    MarginVertical(margin = 8.dp)
                    B2(
                        text = state.passwordMatchingTextResource
                            ?.let { stringResource(it) }.orEmpty(),
                        color = if (state.highlightConfirmPassword) {
                            colorAccent
                        } else {
                            gray2
                        }
                    )
                    MarginVertical(margin = 8.dp)
                    TextSelectableItem(
                        state = state.agreementsState,
                        onSelectedCallback = callback::onAgreementsClick
                    )
                }

                AccentButton(
                    modifier = Modifier
                        .height(48.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    text = stringResource(R.string.create_backup_password_btn_set),
                    loading = state.isLoading,
                    enabled = state.isSetButtonEnabled,
                    onClick = {
                        keyboardController?.hide()
                        callback.onApplyPasswordClick()
                    }
                )
                MarginVertical(12.dp)
            }
        }
    }
}

@Preview
@Composable
fun PreviewCreateBackupPasswordContent() {
    FearlessAppTheme {
        CreateBackupPasswordContent(
            state = CreateBackupPasswordViewState(
                originPasswordViewState = TextInputViewState(
                    text = "origin password",
                    hint = "origin hint",
                    endIcon = R.drawable.ic_eye_disabled
                ),
                confirmPasswordViewState = TextInputViewState(
                    text = "confirm password",
                    hint = "confirm hint"
                ),
                isUserAgreedWithStatements = false,
                agreementsState = TextSelectableItemState(
                    isSelected = true,
                    R.string.create_backup_password_agreements
                ),
                passwordMatchingTextResource = null,
                highlightConfirmPassword = true,
                isSetButtonEnabled = true,
                isLoading = false,
                heightDiffDp = 0.dp
            ),
            callback = object : CreateBackupPasswordCallback {
                override fun onOriginPasswordChange(password: String) {}
                override fun onConfirmPasswordChange(password: String) {}
                override fun onIsUserAgreedWithStatementsChange(isChecked: Boolean) {}
                override fun onBackClick() {}
                override fun onApplyPasswordClick() {}
                override fun onAgreementsClick() {}
                override fun onOriginPasswordVisibilityClick() {}
                override fun onConfirmPasswordVisibilityClick() {}
            }
        )
    }
}
