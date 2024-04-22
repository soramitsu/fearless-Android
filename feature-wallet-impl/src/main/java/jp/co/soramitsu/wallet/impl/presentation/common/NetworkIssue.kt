package jp.co.soramitsu.wallet.impl.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.B0
import jp.co.soramitsu.common.compose.component.GradientIcon
import jp.co.soramitsu.common.compose.component.GrayButton
import jp.co.soramitsu.common.compose.component.H3
import jp.co.soramitsu.common.compose.theme.alertYellow
import jp.co.soramitsu.common.compose.theme.white50
import jp.co.soramitsu.feature_wallet_impl.R

// compose component for network issues, contains warning icon, error message and try again button
@Composable
fun NetworkIssue(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        //Connection Error: Unable to connect to the network. Please try again.
        GradientIcon(
            iconRes = R.drawable.ic_alert_24,
            color = alertYellow,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            contentPadding = PaddingValues(bottom = 4.dp)
        )

        H3(text = "Connection Error")
        B0(
            text = "Unable to connect to the network. Please try again.",
            color = white50
        )
        GrayButton(modifier = Modifier.width(200.dp).height(48.dp), text = "Try again", onClick = onRetry)
    }
}

@Preview
@Composable
fun NetworkIssuePreview() {
    NetworkIssue {

    }
}