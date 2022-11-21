package jp.co.soramitsu.wallet.impl.presentation.addressbook

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.BackgroundCorneredWithBorder
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.H4
import jp.co.soramitsu.common.compose.component.InputWithHint
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.SelectorState
import jp.co.soramitsu.common.compose.component.SelectorWithBorder
import jp.co.soramitsu.common.compose.component.ToolbarBottomSheet
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black05
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.white24
import jp.co.soramitsu.feature_wallet_impl.R

data class CreateContactViewState(
    val chainSelectorState: SelectorState,
    val contactNameInput: String,
    val contactAddressInput: String
)

interface CreateContactScreenInterface {
    fun onNavigationClick()
    fun onCreateContactClick()
    fun onChainClick()
    fun onNameInput(input: String)
    fun onAddressInput(input: String)
}

@Composable
fun CreateContactContent(
    state: CreateContactViewState,
    callback: CreateContactScreenInterface
) {
    BottomSheetScreen {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
            ) {
                ToolbarBottomSheet(
                    title = stringResource(id = R.string.create_contact),
                    onNavigationClicked = { callback.onNavigationClick() }
                )
                MarginVertical(margin = 24.dp)
                SelectorWithBorder(
                    state = state.chainSelectorState,
                    onClick = callback::onChainClick
                )
                MarginVertical(margin = 12.dp)
                InputWithHintCornered(
                    input = state.contactNameInput,
                    hint = stringResource(id = R.string.contact_name),
                    onInput = callback::onNameInput
                )
                MarginVertical(margin = 12.dp)
                InputWithHintCornered(
                    input = state.contactAddressInput,
                    hint = stringResource(id = R.string.contact_address),
                    onInput = callback::onAddressInput
                )
                MarginVertical(margin = 12.dp)
                Spacer(modifier = Modifier.weight(1f))
                MarginVertical(margin = 12.dp)
                AccentButton(
                    text = stringResource(id = R.string.create_contact),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    onClick = callback::onCreateContactClick
                )
                MarginVertical(margin = 12.dp)
            }
        }
    }
}

@Composable
fun InputWithHintCornered(
    input: String?,
    hint: String?,
    onInput: (String) -> Unit
) {
    BackgroundCorneredWithBorder(
        backgroundColor = black05,
        borderColor = white24,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
    ) {
        InputWithHint(
            state = input,
            onInput = onInput,
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.CenterStart)
                .padding(horizontal = 12.dp)
        ) {
            hint?.let {
                H4(
                    text = it,
                    color = black2
                )
            }
        }
    }
}

@Composable
@Preview
fun PreviewCreateContactContent() {
    val state = CreateContactViewState(SelectorState.default, "", "")
    val callback = object : CreateContactScreenInterface {
        override fun onNavigationClick() {}
        override fun onCreateContactClick() {}
        override fun onChainClick() {}
        override fun onNameInput(input: String) {}
        override fun onAddressInput(input: String) {}
    }
    FearlessTheme {
        CreateContactContent(state, callback)
    }
}
