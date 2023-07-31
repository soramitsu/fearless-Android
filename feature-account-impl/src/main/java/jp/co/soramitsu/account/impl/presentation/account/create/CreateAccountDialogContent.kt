package jp.co.soramitsu.account.impl.presentation.account.create

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.B0
import jp.co.soramitsu.common.compose.component.B2
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.TextInput
import jp.co.soramitsu.common.compose.component.TextInputViewState
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.customColors
import jp.co.soramitsu.common.compose.theme.darkButtonBackground

data class CreateAccountState(
    val walletNickname: TextInputViewState,
    val isContinueEnabled: Boolean
) {
    companion object {
        val Empty = CreateAccountState(
            walletNickname = TextInputViewState(text = "", hint = "Wallet name"),
            isContinueEnabled = false
        )
    }
}

interface CreateAccountCallback {

    fun accountNameChanged(accountName: CharSequence)

    fun nextClicked()

    fun onBackClick()
}

@Composable
fun CreateAccountDialogContent(
    state: CreateAccountState,
    callback: CreateAccountCallback
) {
    Column {
        Toolbar(
            modifier = Modifier.padding(bottom = 12.dp),
            state = ToolbarViewState(
                title = stringResource(R.string.create_account_title),
                navigationIcon = R.drawable.ic_arrow_back_24dp
            ),
            onNavigationClick = callback::onBackClick
        )

        Column(
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            MarginVertical(margin = 24.dp)
            B0(
                text = stringResource(R.string.create_account_description),
                color = MaterialTheme.customColors.colorGreyText,
                textAlign = TextAlign.Center
            )
            MarginVertical(margin = 16.dp)
            TextInput(
                state = state.walletNickname,
                onInput = callback::accountNameChanged,
                borderColor = colorAccentDark,
                backgroundColor = darkButtonBackground
            )
            MarginVertical(margin = 8.dp)
            B2(
                text = stringResource(R.string.create_account_edit_text_visibility),
                color = MaterialTheme.customColors.colorGreyText
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        AccentButton(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 16.dp)
                .imePadding(),
            enabled = state.isContinueEnabled,
            text = stringResource(R.string.common_continue),
            onClick = callback::nextClicked
        )
        MarginVertical(12.dp)
    }
}

@Preview
@Composable
private fun PreviewCreateAccountDialogContent() {
    FearlessAppTheme {
        CreateAccountDialogContent(
            state = CreateAccountState(TextInputViewState("my best wallet", "Wallet name"), false),
            callback = object : CreateAccountCallback {
                override fun accountNameChanged(accountName: CharSequence) {}
                override fun nextClicked() {}
                override fun onBackClick() {}
            }
        )
    }
}
