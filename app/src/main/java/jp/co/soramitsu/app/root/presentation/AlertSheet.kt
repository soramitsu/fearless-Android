package jp.co.soramitsu.app.root.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.B0
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.GradientIcon
import jp.co.soramitsu.common.compose.component.Grip
import jp.co.soramitsu.common.compose.component.H3
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.alertYellow
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.AlertViewState

@Composable
fun AlertSheet(
    state: AlertViewState,
    onBackClicked: () -> Unit,
    onTopUpClicked: () -> Unit
) {
    BottomSheetScreen {
        Grip(Modifier.align(Alignment.CenterHorizontally))
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_close),
                tint = white,
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.End)
                    .clickable {
                        onBackClicked()
                    }

            )
            MarginVertical(margin = 44.dp)
            GradientIcon(
                iconRes = state.iconRes,
                color = alertYellow,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                contentPadding = PaddingValues(bottom = 6.dp)
            )

            MarginVertical(margin = 8.dp)
            H3(text = state.title, modifier = Modifier.align(Alignment.CenterHorizontally))
            MarginVertical(margin = 8.dp)
            B0(
                text = state.message,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            MarginVertical(margin = 24.dp)
            AccentButton(
                text = state.buttonText,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                onTopUpClicked()
            }
            MarginVertical(margin = 12.dp)
        }
    }
}

@Preview
@Composable
private fun NetworkUnavailableScreenPreview() {
    FearlessTheme {
        AlertSheet(
            state = AlertViewState(
                stringResource(id = R.string.staking_main_network_title, "KSM"),
                stringResource(id = R.string.network_issue_unavailable),
                stringResource(id = R.string.top_up),
                R.drawable.ic_alert_16
            ),
            onBackClicked = { },
            onTopUpClicked = { }
        )
    }
}
