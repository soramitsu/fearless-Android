package jp.co.soramitsu.common.compose.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.white16

enum class ActionItemType(
    @DrawableRes val iconId: Int,
    @StringRes val titleId: Int
) {
    SEND(R.drawable.ic_common_send, R.string.common_action_send),
    RECEIVE(R.drawable.ic_common_receive, R.string.common_action_receive),
    TELEPORT(R.drawable.ic_common_teleport, R.string.common_action_teleport),
    HIDE(R.drawable.ic_common_hide, R.string.common_action_hide),
    SHOW(R.drawable.ic_common_hide, R.string.common_action_show),
    BUY(R.drawable.ic_common_buy, R.string.common_action_buy)
}

data class ActionBarViewState(
    val chainId: String,
    val chainAssetId: String,
    val actionItems: List<ActionItemType>
)

@Composable
fun ActionBar(
    state: ActionBarViewState,
    onItemClick: (ActionItemType, String, String) -> Unit = { _, _, _ -> }
) {
    BackgroundCornered {
        Row(Modifier.padding(vertical = 4.dp)) {
            state.actionItems.forEachIndexed { index, actionItem ->
                ActionCell(
                    state = ActionCellViewState(
                        painter = painterResource(actionItem.iconId),
                        title = stringResource(actionItem.titleId)
                    ),
                    onClick = { onItemClick.invoke(actionItem, state.chainId, state.chainAssetId) }
                )

                if (index < state.actionItems.size - 1) {
                    Divider(
                        color = white16,
                        modifier = Modifier
                            .height(64.dp)
                            .width(1.dp)
                            .align(Alignment.CenterVertically)
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun ActionBarPreview() {
    val state = ActionBarViewState(
        chainId = "",
        chainAssetId = "",
        actionItems = ActionItemType.values().asList()
    )

    FearlessTheme {
        ActionBar(state)
    }
}
