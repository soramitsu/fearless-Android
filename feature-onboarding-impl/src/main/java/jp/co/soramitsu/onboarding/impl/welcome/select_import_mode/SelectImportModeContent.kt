package jp.co.soramitsu.onboarding.impl.welcome.select_import_mode

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.GrayButton
import jp.co.soramitsu.common.compose.component.H4
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.feature_onboarding_impl.R

interface SelectImportModeScreenInterface {

    fun onCancelClick()

    fun onGoogleClick()

    fun onMnemonicPhraseClick()

    fun onRawSeedClick()

    fun onJsonClick()
}

@Composable
fun SelectImportModeContent(
    callback: SelectImportModeScreenInterface
) {
    BottomSheetScreen {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MarginVertical(margin = 8.dp)
            H4(text = stringResource(id = R.string.select_import_mode_title))
            MarginVertical(margin = 16.dp)
            GrayButton(
                text = stringResource(id = R.string.select_import_mode_btn_google),
                modifier = Modifier
                    .fillMaxWidth(),
                onClick = callback::onGoogleClick
            )
            MarginVertical(margin = 8.dp)
            GrayButton(
                text = stringResource(id = R.string.select_import_mode_btn_mnemonic),
                modifier = Modifier
                    .fillMaxWidth(),
                onClick = callback::onMnemonicPhraseClick
            )
            MarginVertical(margin = 8.dp)
            GrayButton(
                text = stringResource(id = R.string.select_import_mode_btn_raw_seed),
                modifier = Modifier
                    .fillMaxWidth(),
                onClick = callback::onRawSeedClick
            )
            MarginVertical(margin = 8.dp)
            GrayButton(
                text = stringResource(id = R.string.select_import_mode_btn_json),
                modifier = Modifier
                    .fillMaxWidth(),
                onClick = callback::onJsonClick
            )
            MarginVertical(margin = 8.dp)
            AccentButton(
                text = stringResource(id = R.string.common_cancel),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                onClick = callback::onCancelClick
            )
            MarginVertical(margin = 12.dp)
        }
    }
}
