package jp.co.soramitsu.wallet.impl.presentation.balance.networkissues

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.GradientIcon
import jp.co.soramitsu.common.compose.component.H4
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.NetworkIssueItem
import jp.co.soramitsu.common.compose.component.NetworkIssueItemState
import jp.co.soramitsu.common.compose.component.NetworkIssueType
import jp.co.soramitsu.common.compose.component.TextButton
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.alertYellow
import jp.co.soramitsu.common.compose.theme.backgroundBlurColor
import jp.co.soramitsu.common.compose.theme.colorAccent
import jp.co.soramitsu.common.compose.theme.customButtonColors
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.feature_wallet_impl.R

data class NetworkIssuesState(
    val issues: List<NetworkIssueItemState>
)

@Composable
fun NetworkIssuesScreen(
    state: NetworkIssuesState,
    onIssueClicked: (issue: NetworkIssueItemState) -> Unit,
    onBackClicked: () -> Unit
) {
    BottomSheetScreen {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
        ) {
            Box {
                IconButton(
                    onClick = {
                        onBackClicked()
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(backgroundBlurColor)
                        .size(32.dp)
                        .align(Alignment.TopEnd)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        tint = white,
                        contentDescription = null
                    )
                }
                H4(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center),
                    text = stringResource(id = R.string.network_issue_stub),
                    textAlign = TextAlign.Center
                )
            }
            MarginVertical(margin = 44.dp)
            GradientIcon(
                iconRes = R.drawable.ic_alert_16,
                color = alertYellow,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                contentPadding = PaddingValues(bottom = 6.dp)
            )
            MarginVertical(margin = 24.dp)
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(state.issues) { issueState ->
                    NetworkIssueItem(
                        state = issueState,
                        onClick = { onIssueClicked(issueState) }
                    )
                }
                item {
                    MarginVertical(margin = 12.dp)
                }
            }
            TextButton(
                text = stringResource(id = R.string.common_close),
                textStyle = MaterialTheme.customTypography.header4,
                colors = customButtonColors(colorAccent),
                onClick = onBackClicked,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            )
            MarginVertical(margin = 12.dp)
        }
    }
}

@Preview
@Composable
private fun SelectWalletScreenPreview() {
    val issueStates = listOf(
        NetworkIssueItemState(
            iconUrl = "",
            title = "BIFROST Network BIFROST Network BIFROST Network",
            subTitle = "Node is unavailable",
            type = NetworkIssueType.Node,
            chainId = "",
            chainName = "BIFROSTT",
            assetId = ""
        ),
        NetworkIssueItemState(
            iconUrl = "",
            title = "KUSAMA Network",
            type = NetworkIssueType.Network,
            chainId = "",
            chainName = "KUSAMAA",
            assetId = ""
        )
    )

    FearlessTheme {
        NetworkIssuesScreen(
            state = NetworkIssuesState(
                issues = issueStates
            ),
            onIssueClicked = {},
            onBackClicked = {}
        )
    }
}
