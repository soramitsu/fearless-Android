package jp.co.soramitsu.common.compose.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.colorAccent
import jp.co.soramitsu.common.compose.theme.customButtonColors
import jp.co.soramitsu.common.compose.theme.white50

data class NotificationState(
    @DrawableRes val iconRes: Int,
    @StringRes val titleRes: Int,
    val value: String,
    @StringRes val buttonTextRes: Int,
    val color: Color
)

@Composable
fun Notification(state: NotificationState, onAction: () -> Unit) {
    BackgroundCornered {
        Row(Modifier.padding(8.dp)) {
            MarginHorizontal(margin = 8.dp)
            Image(
                res = state.iconRes,
                modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.CenterVertically),
                tint = state.color
            )
            MarginHorizontal(margin = 8.dp)
            Column(Modifier.weight(1f)) {
                H6(text = stringResource(id = state.titleRes), color = state.color)
                B1(text = state.value, color = white50)
            }
            TextButtonSmall(
                text = stringResource(id = state.buttonTextRes),
                colors = customButtonColors(state.color),
                onClick = onAction,
                modifier = Modifier
                    .height(24.dp)
                    .align(Alignment.CenterVertically)
            )
            MarginHorizontal(margin = 8.dp)
        }
    }
}

@Composable
@Preview
private fun Preview() {
    val state = NotificationState(
        R.drawable.ic_status_warning_16,
        R.string.staking_reward_details_status_claimable,
        "0.49191 KSM",
        R.string.staking_unbond_v1_9_0,
        colorAccent
    )
    FearlessTheme {
        Notification(state) {}
    }
}
