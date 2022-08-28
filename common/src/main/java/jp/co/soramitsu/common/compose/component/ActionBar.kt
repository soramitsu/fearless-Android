package jp.co.soramitsu.common.compose.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.backgroundBlurColor
import jp.co.soramitsu.common.compose.theme.white16
import jp.co.soramitsu.common.compose.theme.FearlessTheme

data class ActionItem(
    @DrawableRes val iconId: Int,
    val title: String,
    val onClick: () -> Unit
)

data class ActionBarViewState(
    val actionItems: List<ActionItem>
)

@Composable
fun ActionBar(
    state: ActionBarViewState
) {
    BackgroundCornered(
        backgroundColor = backgroundBlurColor
    ) {
        Row {
            state.actionItems.forEachIndexed { index, actionItem ->
                ActionCell(
                    state = ActionCellViewState(
                        painter = painterResource(actionItem.iconId),
                        title = actionItem.title
                    ),
                    onClick = actionItem.onClick
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
        actionItems = listOf(
            ActionItem(
                iconId = R.drawable.ic_common_send,
                title = stringResource(R.string.common_action_send),
                onClick = {}
            ),
            ActionItem(
                iconId = R.drawable.ic_common_receive,
                title = stringResource(R.string.common_action_receive),
                onClick = {}
            ),
            ActionItem(
                iconId = R.drawable.ic_common_teleport,
                title = stringResource(R.string.common_action_teleport),
                onClick = {}
            ),
            ActionItem(
                iconId = R.drawable.ic_common_hide,
                title = stringResource(R.string.common_action_hide),
                onClick = {}
            )
        )
    )

    FearlessTheme {
        ActionBar(state)
    }
}
