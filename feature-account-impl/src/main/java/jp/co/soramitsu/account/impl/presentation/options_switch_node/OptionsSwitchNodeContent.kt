package jp.co.soramitsu.account.impl.presentation.options_switch_node

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.feature_account_api.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

data class OptionsSwitchNodeScreenViewState(
    val metaId: Long,
    val chainId: ChainId,
    val chainName: String
)

@Composable
fun OptionsSwitchNodeContent(
    state: OptionsSwitchNodeScreenViewState,
    onSwitch: (chainId: ChainId) -> Unit,
    dontShowAgain: (chainId: ChainId, metaId: Long) -> Unit,
    onBackClicked: () -> Unit
) {
    BottomSheetScreen {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_close),
                    tint = white,
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.TopEnd)
                        .clickable { onBackClicked() }
                )
                H3(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 32.dp),
                    text = stringResource(id = R.string.options_switch_node_title, state.chainName),
                    textAlign = TextAlign.Center
                )
            }
            MarginVertical(margin = 28.dp)
            GrayButton(
                text = stringResource(id = R.string.switch_node),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                onSwitch(state.chainId)
            }
            MarginVertical(margin = 12.dp)
            TextButton(
                text = stringResource(id = R.string.issue_do_not_show_again),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = customButtonColors(grayButtonBackground, colorAccentDark)
            ) {
                dontShowAgain(state.chainId, state.metaId)
            }
            MarginVertical(margin = 12.dp)
        }
    }
}

@Preview
@Composable
private fun OptionsSwitchNodeScreenPreview() {
    FearlessTheme {
        OptionsSwitchNodeContent(
            state = OptionsSwitchNodeScreenViewState(
                metaId = 1,
                chainId = "",
                chainName = "Kusama"
            ),
            onSwitch = {},
            dontShowAgain = { t, t2 -> },
            onBackClicked = {}
        )
    }
}
