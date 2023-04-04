package jp.co.soramitsu.common.compose.component

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.alertYellow
import jp.co.soramitsu.common.compose.theme.black05
import jp.co.soramitsu.common.compose.theme.colorAccent
import jp.co.soramitsu.common.compose.theme.customButtonColors

enum class NetworkIssueType(
    @StringRes val reason: Int? = null,
    @StringRes val action: Int? = null,
    val actionColor: Color = colorAccent
) {
    Node(
        reason = R.string.network_issue_node_unavailable,
        action = R.string.network_issue_action_switch_node
    ),
    Network(
        reason = R.string.network_issue_network_unavailable,
        actionColor = alertYellow
    ),
    Account(
        reason = R.string.network_issue_add_an_account,
        action = R.string.network_issue_action_add_account,
        actionColor = alertYellow
    )
}

data class NetworkIssueItemState(
    val iconUrl: String,
    val title: String,
    val subTitle: String? = null,
    val type: NetworkIssueType,

    val chainId: String,
    val chainName: String,
    val assetId: String,
    val priceId: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NetworkIssueItemState

        if (chainId != other.chainId) return false
        if (assetId != other.assetId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = chainId.hashCode()
        result = 31 * result + assetId.hashCode()
        return result
    }
}

@Composable
fun NetworkIssueItem(
    state: NetworkIssueItemState,
    onClick: () -> Unit
) {
    BackgroundCorneredWithBorder(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        borderColor = Color.White.copy(alpha = 0.48f),
        backgroundColor = black05
    ) {
        Row(
            modifier = Modifier
                .wrapContentHeight()
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = getImageRequest(LocalContext.current, state.iconUrl),
                contentDescription = null,
                modifier = Modifier
                    .testTag("NetworkIssueItem_image")
                    .size(32.dp)
                    .align(Alignment.CenterVertically)
            )
            Column(
                Modifier
                    .padding(horizontal = 8.dp)
                    .weight(1f)
            ) {
                H6(
                    text = state.title,
                    color = Color.White.copy(alpha = 0.64f),
                    maxLines = 1
                )
                val subTitle = state.subTitle ?: state.type.reason?.let { stringResource(id = it) }
                if (subTitle != null) {
                    B3(
                        text = subTitle,
                        color = Color.White.copy(alpha = 0.5f),
                        maxLines = 1
                    )
                }
            }

            val actionLabel = state.type.action?.let { stringResource(id = it) }
            if (actionLabel == null) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_alert_16),
                    tint = state.type.actionColor,
                    contentDescription = null
                )
            } else {
                TextButtonSmall(
                    modifier = Modifier.height(24.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    text = actionLabel,
                    colors = customButtonColors(state.type.actionColor),
                    onClick = onClick::invoke
                )
            }
        }
    }
}

@Composable
@Preview
private fun NetworkIssueItemPreview() {
    val state = NetworkIssueItemState(
        iconUrl = "",
        title = "BIFROST Network BIFROST Network BIFROST Network",
        type = NetworkIssueType.Node,
        chainId = "BifrostSamaId",
        chainName = "BifrostSama",
        assetId = "AssetId"
    )
    Column(Modifier.background(Color.Black)) {
        NetworkIssueItem(state) {}
        NetworkIssueItem(state.copy(type = NetworkIssueType.Network)) {}
        NetworkIssueItem(state.copy(type = NetworkIssueType.Account)) {}
    }
}
