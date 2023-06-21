package jp.co.soramitsu.account.impl.presentation.mnemonic_agreements

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.B0
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.TextSelectableItem
import jp.co.soramitsu.common.compose.component.TextSelectableItemState
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState

data class MnemonicAgreementsState(
    val losePhraseAgreementItemState: TextSelectableItemState,
    val sharePhraseAgreementItemState: TextSelectableItemState,
    val keepPhraseAgreementItemState: TextSelectableItemState,
    val isShowMnemonicButtonEnabled: Boolean
)

sealed interface MnemonicAgreementsCallback {

    fun onLosePhraseAgreementClick()

    fun onSharePhraseAgreementClick()

    fun onKeepPhraseAgreementClick()

    fun onBackClick()

    fun onShowPhrase()
}

@Composable
internal fun MnemonicAgreementsContent(
    state: MnemonicAgreementsState,
    callback: MnemonicAgreementsCallback,
    modifier: Modifier = Modifier
) {
    BottomSheetScreen {
        Column(modifier = modifier) {
            Toolbar(
                modifier = Modifier.padding(bottom = 12.dp),
                state = ToolbarViewState(
                    title = stringResource(R.string.mnemonic_agreements_title),
                    navigationIcon = R.drawable.ic_arrow_back_24dp
                ),
                onNavigationClick = callback::onBackClick
            )
            MarginVertical(margin = 24.dp)

            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                B0(
                    text = stringResource(R.string.mnemonic_agreements_subtitle)
                )
                MarginVertical(16.dp)
                TextSelectableItem(
                    state = state.losePhraseAgreementItemState,
                    onSelectedCallback = callback::onLosePhraseAgreementClick
                )
                MarginVertical(8.dp)
                TextSelectableItem(
                    state = state.sharePhraseAgreementItemState,
                    onSelectedCallback = callback::onSharePhraseAgreementClick
                )
                MarginVertical(8.dp)
                TextSelectableItem(
                    state = state.keepPhraseAgreementItemState,
                    onSelectedCallback = callback::onKeepPhraseAgreementClick
                )
                MarginVertical(16.dp)
            }

            Spacer(modifier = Modifier.weight(1f))
            AccentButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                state = ButtonViewState(
                    text = stringResource(R.string.backup_wallet_show_mnemonic_phrase),
                    enabled = state.isShowMnemonicButtonEnabled
                ),
                onClick = callback::onShowPhrase
            )
            MarginVertical(12.dp)
        }
    }
}
