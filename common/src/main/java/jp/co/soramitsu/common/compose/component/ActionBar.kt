package jp.co.soramitsu.common.compose.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    BUY(R.drawable.ic_common_buy, R.string.common_action_buy),
    SWAP(R.drawable.ic_exchange, R.string.common_action_swap)
}

data class ActionBarViewState(
    val chainId: String,
    val chainAssetId: String,
    val actionItems: List<ActionItemType>,
    val disabledItems: List<ActionItemType> = emptyList()
)

@Composable
fun ActionBar(
    state: ActionBarViewState,
    fillMaxWidth: Boolean = false,
    onItemClick: (ActionItemType, String, String) -> Unit = { _, _, _ -> }
) {
    BackgroundCornered {
        Row(Modifier.padding(vertical = 4.dp)) {
            state.actionItems.forEachIndexed { index, actionItem ->
                val itemClickHandler = remember { { onItemClick(actionItem, state.chainId, state.chainAssetId) } }
                val icon = painterResource(id = actionItem.iconId)
                val title = stringResource(id = actionItem.titleId)
                val actionViewState = remember {
                    ActionCellViewState(
                        painter = icon,
                        title = title,
                        isEnabled = actionItem !in state.disabledItems
                    )
                }
                ActionCell(
                    state = actionViewState,
                    modifier = if (fillMaxWidth) Modifier.weight(1f) else Modifier,
                    onClick = itemClickHandler
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

@Composable
fun ActionBarShimmer(items: Int = 4, fillMaxWidth: Boolean = false) {
    val rowModifier = (if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier).padding(vertical = 8.dp)
    Row(rowModifier, horizontalArrangement = Arrangement.SpaceAround) {
        repeat(items) {
            ShimmerRectangle(
                Modifier
                    .size(64.dp)
                    .align(Alignment.CenterVertically)
            )
        }
    }
}

@Preview
@Composable
private fun ActionBarPreview() {
    val state = ActionBarViewState(
        chainId = "",
        chainAssetId = "",
        actionItems = ActionItemType.values().asList().take(3)
    )

    FearlessTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            ActionBar(state = state)
            ActionBar(state = state, fillMaxWidth = true)
            ActionBarShimmer(fillMaxWidth = true)
        }
    }
}
