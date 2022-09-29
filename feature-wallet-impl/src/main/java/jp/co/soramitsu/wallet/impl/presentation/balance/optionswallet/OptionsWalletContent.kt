package jp.co.soramitsu.wallet.impl.presentation.balance.optionswallet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ButtonColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.Grip
import jp.co.soramitsu.common.compose.component.H3
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.TextButton
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.grayButtonBackground
import jp.co.soramitsu.feature_wallet_impl.R

data class OptionsWalletScreenViewState(
    val isSelected: Boolean
)

@Composable
fun OptionsWalletContent(
    state: OptionsWalletScreenViewState,
    exportWallet: () -> Unit,
    openWalletDetails: () -> Unit,
    deleteWallet: () -> Unit
) {
    BottomSheetScreen {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
        ) {
            MarginVertical(margin = 2.dp)
            Grip(Modifier.align(Alignment.CenterHorizontally))
            MarginVertical(margin = 8.dp)
            H3(text = stringResource(id = R.string.common_title_wallet_option))
            MarginVertical(margin = 28.dp)
            TextButton(
                text = stringResource(id = R.string.common_details_wallet),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                openWalletDetails()
            }
            MarginVertical(margin = 12.dp)
            TextButton(
                text = stringResource(id = R.string.common_export_wallet),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                exportWallet()
            }
            if (!state.isSelected) {
                MarginVertical(margin = 12.dp)
                TextButton(
                    text = stringResource(id = R.string.common_delete_wallet),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = object : ButtonColors {
                        @Composable
                        override fun backgroundColor(enabled: Boolean): State<Color> {
                            return rememberUpdatedState(grayButtonBackground)
                        }

                        @Composable
                        override fun contentColor(enabled: Boolean): State<Color> {
                            return rememberUpdatedState(colorAccentDark)
                        }
                    }
                ) {
                    deleteWallet()
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
            exportWallet = {},
            openWalletDetails = {},
            deleteWallet = {}
        )
    }
}
