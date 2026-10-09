package jp.co.soramitsu.wallet.impl.presentation.balance.optionswallet

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.GrayButton
import jp.co.soramitsu.common.compose.component.H4
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.NavigationIconButton
import jp.co.soramitsu.common.compose.component.TextButton
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.customButtonColors
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.grayButtonBackground
import jp.co.soramitsu.feature_wallet_impl.R

data class OptionsWalletScreenViewState(
    val isSelected: Boolean,
    val showScoreButton: Boolean,
    val showDetailsButton: Boolean
)

interface OptionsWalletCallback {

    fun onChangeWalletNameClick()

    fun onWalletDetailsClick()

    fun onBackupWalletClick()

    fun onDeleteWalletClick()

    fun onCloseClick()

    fun onShowWalletScoreClick()
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
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                H4(
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    text = stringResource(id = R.string.common_title_wallet_option)
                )
                NavigationIconButton(
                    navigationIconResId = R.drawable.ic_cross_32,
                    onNavigationClick = callback::onCloseClick
                )
            }
            MarginVertical(margin = 28.dp)
            GrayButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                text = stringResource(id = R.string.export_wallet),
                onClick = callback::onBackupWalletClick
            )
            if (state.showDetailsButton) {
                MarginVertical(margin = 12.dp)
                GrayButton(
                    text = stringResource(id = R.string.common_details_wallet),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    onClick = callback::onWalletDetailsClick
                )
            }
            MarginVertical(margin = 12.dp)
            GrayButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                text = stringResource(id = R.string.change_wallet_name),
                onClick = callback::onChangeWalletNameClick
            )
            if (state.showScoreButton) {
                MarginVertical(margin = 12.dp)
                GrayButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    text = stringResource(id = R.string.account_stats_wallet_option_title),
                    onClick = callback::onShowWalletScoreClick
                )
            }
            if (!state.isSelected) {
                MarginVertical(margin = 12.dp)
                TextButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    textStyle = MaterialTheme.customTypography.header4,
                    text = stringResource(id = R.string.common_delete_wallet),
                    colors = customButtonColors(grayButtonBackground, colorAccentDark),
                    onClick = callback::onDeleteWalletClick)
            }
            MarginVertical(margin = 12.dp)
        }
    }
}

@Preview
@Composable
private fun OptionsWalletScreenPreview() {
    FearlessAppTheme() {
        OptionsWalletContent(
            state = OptionsWalletScreenViewState(
                isSelected = false,
                showScoreButton = true,
                showDetailsButton = true
            ),
            callback = object : OptionsWalletCallback {
                override fun onChangeWalletNameClick() {}
                override fun onWalletDetailsClick() {}
                override fun onBackupWalletClick() {}
                override fun onDeleteWalletClick() {}
                override fun onCloseClick() {}
                override fun onShowWalletScoreClick() {}
            }
        )
    }
}
