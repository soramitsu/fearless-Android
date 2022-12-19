package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.white08
import jp.co.soramitsu.common.utils.clickableWithNoIndication

data class HiddenItemState(
    val isExpanded: Boolean = false
)

@Composable
fun HiddenAssetsItem(
    state: HiddenItemState,
    onClick: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clickableWithNoIndication { onClick() }
    ) {
        Row(
            modifier = Modifier
                .height(64.dp)
                .align(CenterHorizontally)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = white08,
                        shape = RoundedCornerShape(32.dp)
                    )
                    .align(CenterVertically)
                    .rotate(if (state.isExpanded) 180f else 0f)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_down),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Center)
                )
            }
            MarginHorizontal(margin = 16.dp)
            Text(
                text = stringResource(id = R.string.hidden_assets),
                style = MaterialTheme.customTypography.header5,
                color = Color.White,
                modifier = Modifier.align(CenterVertically)
            )
        }
    }
}

@Preview
@Composable
private fun PreviewHiddenAssetsItem() {
    FearlessTheme {
        Surface(Modifier.background(Color.Black)) {
            HiddenAssetsItem(
                state = HiddenItemState(false),
                onClick = {}
            )
        }
    }
}
