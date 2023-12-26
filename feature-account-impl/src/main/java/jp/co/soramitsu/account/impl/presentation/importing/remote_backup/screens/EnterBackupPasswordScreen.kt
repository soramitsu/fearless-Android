package jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.ImportRemoteWalletState
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.model.WrappedBackupAccountMeta
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.views.CompactWalletItemViewState
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.B0
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.TextInput
import jp.co.soramitsu.common.compose.component.TextInputViewState
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.component.WalletItem
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.gray2
import jp.co.soramitsu.common.compose.theme.white08

data class EnterBackupPasswordState(
    val wallet: WrappedBackupAccountMeta?,
    val passwordInputViewState: TextInputViewState,
    val isLoading: Boolean,
    val heightDiffDp: Dp
) : ImportRemoteWalletState

interface EnterBackupPasswordCallback {

    fun onBackClick()

    fun onContinueClick()

    fun onPasswordChanged(password: String)

    fun onPasswordVisibilityClick()
}

@Composable
internal fun EnterBackupPasswordScreen(
    state: EnterBackupPasswordState,
    callback: EnterBackupPasswordCallback
) {
    var focusedState by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun onFocusChanged(focusState: FocusState) {
        focusedState = focusState.isFocused
    }

    val borderColor = if (focusedState) {
        colorAccentDark
    } else {
        white08
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(rememberNestedScrollInteropConnection())
//            .padding(bottom = state.heightDiffDp)
    ) {
        Column {
            Toolbar(
                modifier = Modifier.padding(bottom = 12.dp),
                state = ToolbarViewState(
                    title = stringResource(R.string.import_remote_wallet_title_password),
                    navigationIcon = R.drawable.ic_arrow_back_24dp
                ),
                onNavigationClick = callback::onBackClick
            )
            MarginVertical(margin = 24.dp)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                B0(
                    text = stringResource(R.string.import_remote_wallet_subtitle_password),
                    textAlign = TextAlign.Center,
                    color = gray2
                )
                MarginVertical(margin = 16.dp)
                WalletItem(
                    state = CompactWalletItemViewState(title = state.wallet?.backupMeta?.name.orEmpty()),
                    onSelected = {}
                )
                MarginVertical(margin = 16.dp)
                TextInput(
                    modifier = Modifier.focusRequester(focusRequester),
                    state = state.passwordInputViewState,
                    borderColor = borderColor,
                    onFocusChanged = ::onFocusChanged,
                    onInput = callback::onPasswordChanged,
                    onEndIconClick = callback::onPasswordVisibilityClick
                )
                MarginVertical(margin = 16.dp)
            }

            AccentButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 16.dp),
                text = stringResource(R.string.common_continue),
                enabled = true,
                loading = state.isLoading,
                onClick = callback::onContinueClick
            )
            MarginVertical(12.dp)
        }
    }
}

@Preview
@Composable
private fun PreviewEnterBackupPasswordScreen() {
    FearlessAppTheme {
        EnterBackupPasswordScreen(
            state = EnterBackupPasswordState(
                wallet = null,
                passwordInputViewState = TextInputViewState(
                    text = "password text",
                    hint = "hint"
                ),
                isLoading = false,
                heightDiffDp = 100.dp
            ),
            callback = object : EnterBackupPasswordCallback {
                override fun onBackClick() {}
                override fun onContinueClick() {}
                override fun onPasswordChanged(password: String) {}
                override fun onPasswordVisibilityClick() {}
            }
        )
    }
}
