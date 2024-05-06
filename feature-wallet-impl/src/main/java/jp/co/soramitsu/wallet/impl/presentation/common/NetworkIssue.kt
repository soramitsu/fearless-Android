package jp.co.soramitsu.wallet.impl.presentation.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.B0
import jp.co.soramitsu.common.compose.component.GradientIcon
import jp.co.soramitsu.common.compose.component.GrayButton
import jp.co.soramitsu.common.compose.component.H3
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.alertYellow
import jp.co.soramitsu.common.compose.theme.white50
import jp.co.soramitsu.feature_wallet_impl.R

@Composable
fun NetworkIssue(retryButtonLoading: Boolean, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        GradientIcon(
            iconRes = R.drawable.ic_alert_24,
            color = alertYellow,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            contentPadding = PaddingValues(bottom = 4.dp)
        )
        MarginVertical(margin = 16.dp)
        H3(text = stringResource(R.string.common_search_assets_alert_title))
        MarginVertical(margin = 16.dp)
        B0(
            text = stringResource(R.string.network_issue_main),
            color = white50,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.weight(1f))
        GrayButton(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            text = stringResource(id = R.string.common_try_again),
            loading = retryButtonLoading,
            onClick = onRetry
        )
        MarginVertical(margin = 80.dp)
    }
}

@Preview
@Composable
fun NetworkIssuePreview() {
    FearlessAppTheme {
        Column {
            NetworkIssue(true) {

            }
            NetworkIssue(false) {

            }
        }
    }
}