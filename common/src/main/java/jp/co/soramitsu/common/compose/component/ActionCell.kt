package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.customTypography

data class ActionCellViewState(
    val painter: Painter,
    val title: String
)

@Composable
fun ActionCell(
    state: ActionCellViewState,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clickable(
                role = Role.Button,
                onClick = onClick
            )
            .size(80.dp)
            .testTag("ActionCell_${state.title}"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = state.painter,
                contentDescription = null
            )
            MarginVertical(6.dp)
            Text(
                text = state.title,
                style = MaterialTheme.customTypography.body2,
                maxLines = 1
            )
        }
    }
}

@Preview
@Composable
fun ActionSellPreview() {
    val state = ActionCellViewState(
        painter = painterResource(R.drawable.ic_common_send),
        title = "Send"
    )

    FearlessTheme {
        ActionCell(
            state = state,
            onClick = {}
        )
    }
}
