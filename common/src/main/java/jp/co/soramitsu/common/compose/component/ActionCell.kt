package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.white04
import jp.co.soramitsu.common.utils.onSingleClick
import jp.co.soramitsu.common.utils.rememberLastClickTime

data class ActionCellViewState(
    val painter: Painter,
    val title: String,
    val isEnabled: Boolean = true
)

@Composable
fun ActionCell(
    state: ActionCellViewState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val lastClickTimeState = rememberLastClickTime()
    val boxModifier = when {
        state.isEnabled -> modifier.clickable(
            role = Role.Button,
            onClick = {
                onSingleClick(
                    lastClickTimeState = lastClickTimeState,
                    onClick = onClick
                )
            }
        )
        else -> modifier
    }
        .size(80.dp)
        .testTag("ActionCell_${state.title}")

    Box(
        modifier = boxModifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = state.painter,
                tint = if (state.isEnabled) Color.White else black2,
                contentDescription = null,
                modifier = Modifier
                    .background(white04, RoundedCornerShape(size = 12.dp))
                    .padding(6.dp)
            )
            MarginVertical(6.dp)
            Text(
                text = state.title,
                color = if (state.isEnabled) Color.White else black2,
                style = MaterialTheme.customTypography.body2,
                maxLines = 1
            )
        }
    }
}

@Preview
@Composable
private fun ActionSellPreview() {
    val state = ActionCellViewState(
        painter = painterResource(R.drawable.ic_common_send),
        title = "Send"
    )

    FearlessTheme {
        Row(modifier = Modifier.background(color = Color.Black)) {
            ActionCell(
                state = state,
                onClick = {}
            )
            ActionCell(
                state = state.copy(isEnabled = false),
                onClick = {}
            )
        }
    }
}
