package jp.co.soramitsu.account.impl.presentation.account.rename

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.B0
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.TextInput
import jp.co.soramitsu.common.compose.component.TextInputViewState
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.customColors
import jp.co.soramitsu.common.compose.theme.darkButtonBackground

data class RenameAccountState(
    val walletNickname: TextInputViewState?,
    val isSaveEnabled: Boolean,
    val heightDiffDp: Dp
) {
    companion object {
        val Empty = RenameAccountState(
            walletNickname = null,
            isSaveEnabled = false,
            heightDiffDp = 0.dp
        )
    }
}

interface RenameAccountCallback {
    fun accountNameChanged(accountName: CharSequence)

    fun onSaveClicked()

    fun onBackClick()
}

@Composable
fun RenameAccountDialogContent(
    state: RenameAccountState,
    callback: RenameAccountCallback
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
//            .padding(bottom = state.heightDiffDp)
    ) {
        Column {
            Toolbar(
                modifier = Modifier.padding(bottom = 12.dp),
                state = ToolbarViewState(
                    title = stringResource(R.string.change_wallet_name),
                    navigationIcon = R.drawable.ic_arrow_back_24dp
                ),
                onNavigationClick = callback::onBackClick
            )

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                MarginVertical(margin = 24.dp)
                B0(
                    text = stringResource(R.string.rename_account_description),
                    color = MaterialTheme.customColors.colorGreyText,
                    textAlign = TextAlign.Center
                )
                MarginVertical(margin = 16.dp)
                val walletNickname = state.walletNickname
                if (walletNickname == null) { // loading
                    TextInput(
                        modifier = Modifier.focusRequester(focusRequester),
                        state = TextInputViewState("", "Wallet name"),
                        onInput = callback::accountNameChanged,
                        borderColor = colorAccentDark,
                        backgroundColor = darkButtonBackground
                    )
                } else {
                    TextInput(
                        modifier = Modifier.focusRequester(focusRequester),
                        state = walletNickname,
                        onInput = callback::accountNameChanged,
                        borderColor = colorAccentDark,
                        backgroundColor = darkButtonBackground
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            AccentButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 16.dp)
                    .imePadding(),
                enabled = state.isSaveEnabled,
                text = stringResource(R.string.common_save),
                onClick = callback::onSaveClicked
            )
            MarginVertical(12.dp)
        }
    }
}

@Preview
@Composable
private fun PreviewRenameAccountDialogContent() {
    FearlessAppTheme {
        RenameAccountDialogContent(
            state = RenameAccountState(TextInputViewState("my best wallet", "Wallet name"), false, 0.dp),
            callback = object : RenameAccountCallback {
                override fun accountNameChanged(accountName: CharSequence) {}
                override fun onSaveClicked() {}
                override fun onBackClick() {}
            }
        )
    }
}
