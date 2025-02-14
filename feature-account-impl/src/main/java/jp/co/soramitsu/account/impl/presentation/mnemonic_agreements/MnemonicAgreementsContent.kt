package jp.co.soramitsu.account.impl.presentation.mnemonic_agreements

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.TextSelectableItem
import jp.co.soramitsu.common.compose.component.TextSelectableItemState
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.theme.customColors

data class MnemonicAgreementsState(
    val losePhraseAgreementItemState: TextSelectableItemState,
    val sharePhraseAgreementItemState: TextSelectableItemState,
    val keepPhraseAgreementItemState: TextSelectableItemState,
    val isShowMnemonicButtonEnabled: Boolean
)

interface MnemonicAgreementsCallback {

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
                    text = stringResource(R.string.mnemonic_agreements_subtitle),
                    color = MaterialTheme.customColors.colorGreyText,
                    textAlign = TextAlign.Center
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
                    .height(48.dp)
                    .padding(horizontal = 16.dp),
                text = stringResource(R.string.backup_wallet_show_mnemonic_phrase),
                enabled = state.isShowMnemonicButtonEnabled,
                onClick = callback::onShowPhrase
            )
            MarginVertical(12.dp)
        }
    }
}

@Preview
@Composable
private fun PreviewMnemonicAgreementsContent() {
    MnemonicAgreementsContent(
        state = MnemonicAgreementsState(
            losePhraseAgreementItemState = TextSelectableItemState(
                isSelected = true,
                textResId = jp.co.soramitsu.feature_account_impl.R.string.mnemonic_agreements_ageement_1
            ),
            sharePhraseAgreementItemState = TextSelectableItemState(
                isSelected = false,
                textResId = jp.co.soramitsu.feature_account_impl.R.string.mnemonic_agreements_ageement_2
            ),
            keepPhraseAgreementItemState = TextSelectableItemState(
                isSelected = true,
                textResId = jp.co.soramitsu.feature_account_impl.R.string.mnemonic_agreements_ageement_3
            ),
            isShowMnemonicButtonEnabled = false
        ),
        callback = object : MnemonicAgreementsCallback {
            override fun onLosePhraseAgreementClick() {}
            override fun onSharePhraseAgreementClick() {}
            override fun onKeepPhraseAgreementClick() {}
            override fun onBackClick() {}
            override fun onShowPhrase() {}
        }
    )
}