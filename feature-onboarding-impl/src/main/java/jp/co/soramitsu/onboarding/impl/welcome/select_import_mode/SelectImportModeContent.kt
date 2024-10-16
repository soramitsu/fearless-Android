package jp.co.soramitsu.onboarding.impl.welcome.select_import_mode

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.GoogleButton
import jp.co.soramitsu.common.compose.component.GrayButton
import jp.co.soramitsu.common.compose.component.H4
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.TransparentBorderedButton
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.white08
import jp.co.soramitsu.feature_onboarding_impl.R

data class SelectImportModeState(
    val preinstalledFeatureEnabled: Boolean
)

interface SelectImportModeScreenInterface {

    fun onCancelClick()

    fun onGoogleClick()

    fun onMnemonicPhraseClick()

    fun onRawSeedClick()

    fun onJsonClick()

    fun onGoogleLoginError(message: String)

    fun onGoogleSignInSuccess()

    fun onPreinstalledImportClick()
}

@Composable
fun SelectImportModeContent(
    state: SelectImportModeState,
    isGoogleAvailable: Boolean,
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
                text = stringResource(id = R.string.select_import_mode_btn_mnemonic),
                modifier = Modifier
                    .height(48.dp)
                    .fillMaxWidth(),
                onClick = callback::onMnemonicPhraseClick
            )
            MarginVertical(margin = 8.dp)
            GrayButton(
                text = stringResource(id = R.string.select_import_mode_btn_raw_seed),
                modifier = Modifier
                    .height(48.dp)
                    .fillMaxWidth(),
                onClick = callback::onRawSeedClick
            )
            MarginVertical(margin = 8.dp)
            GrayButton(
                text = stringResource(id = R.string.select_import_mode_btn_json),
                modifier = Modifier
                    .height(48.dp)
                    .fillMaxWidth(),
                onClick = callback::onJsonClick
            )
            if (isGoogleAvailable) {
                MarginVertical(margin = 8.dp)
                GoogleButton(
                    text = stringResource(id = R.string.select_import_mode_btn_google),
                    backgroundColor = white08,
                    borderColor = Color.Unspecified,
                    onClick = callback::onGoogleClick
                )
            }
            if (state.preinstalledFeatureEnabled) {
                MarginVertical(margin = 8.dp)
                TransparentBorderedButton(
                    iconRes = R.drawable.ic_common_receive,
                    text = stringResource(id = R.string.onboarding_preinstalled_wallet_button_text),
                    backgroundColor = white08,
                    borderColor = Color.Unspecified,
                    onClick = callback::onPreinstalledImportClick
                )
            }
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

@Composable
@Preview
private fun PreviewSelectImportModeContent() {
    FearlessAppTheme {
        SelectImportModeContent(
            state = SelectImportModeState(preinstalledFeatureEnabled = true),
            isGoogleAvailable = true,
            callback = object : SelectImportModeScreenInterface {
                override fun onCancelClick() {}
                override fun onGoogleClick() {}
                override fun onMnemonicPhraseClick() {}
                override fun onRawSeedClick() {}
                override fun onJsonClick() {}
                override fun onGoogleLoginError(message: String) {}
                override fun onGoogleSignInSuccess() {}
                override fun onPreinstalledImportClick() {}
            })
    }
}
