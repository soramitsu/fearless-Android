package jp.co.soramitsu.wallet.impl.presentation.balance.optionswallet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.GrayButton
import jp.co.soramitsu.common.compose.component.H3
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.TextButton
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.customButtonColors
import jp.co.soramitsu.common.compose.theme.grayButtonBackground
import jp.co.soramitsu.feature_wallet_impl.R

data class OptionsWalletScreenViewState(
    val isSelected: Boolean
)

interface OptionsWalletCallback {

    fun onExportWalletClick()

    fun onWalletDetailsClick()

    fun onBackupWalletClick()

    fun onDeleteWalletClick()
}

@Composable
fun OptionsWalletContent(
    state: OptionsWalletScreenViewState,
    callback: OptionsWalletCallback
) {
    BottomSheetScreen {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
        ) {
            H3(text = stringResource(id = R.string.common_title_wallet_option))
            MarginVertical(margin = 28.dp)
            GrayButton(
                text = stringResource(id = R.string.common_backup_wallet),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                callback.onBackupWalletClick()
            }
            MarginVertical(margin = 12.dp)
            GrayButton(
                text = stringResource(id = R.string.common_details_wallet),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                callback.onWalletDetailsClick()
            }
            MarginVertical(margin = 12.dp)
            GrayButton(
                text = stringResource(id = R.string.common_export_wallet),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                callback.onExportWalletClick()
            }
            if (!state.isSelected) {
                MarginVertical(margin = 12.dp)
                TextButton(
                    text = stringResource(id = R.string.common_delete_wallet),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = customButtonColors(grayButtonBackground, colorAccentDark)
                ) {
                    callback.onDeleteWalletClick()
                }
            }
            MarginVertical(margin = 12.dp)
        }
    }
}

@Preview
@Composable
private fun OptionsWalletScreenPreview() {
    FearlessTheme {
        OptionsWalletContent(
            state = OptionsWalletScreenViewState(
                isSelected = false
            ),
            callback = object : OptionsWalletCallback {

                override fun onExportWalletClick() {
                }

                override fun onWalletDetailsClick() {
                }

                override fun onBackupWalletClick() {
                }

                override fun onDeleteWalletClick() {
                }
            }
        )
    }
}
