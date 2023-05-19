package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.backgroundBlurColor
import jp.co.soramitsu.common.compose.theme.colorAccent
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.white

data class ChainSelectorViewState(
    val selectedChainName: String? = null,
    val selectedChainId: String? = null,
    val selectedChainStatusColor: Color = colorAccent
)

@Composable
fun ChainSelector(
    selectorViewState: ChainSelectorViewState,
    onChangeChainClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundBlurColor)
            .clickable(
                role = Role.Button,
                onClick = onChangeChainClick
            )
    ) {
        Box(Modifier.padding(8.dp)) {
            Canvas(
                modifier = Modifier
                    .size(6.dp)
            ) {
                drawCircle(color = selectorViewState.selectedChainStatusColor)
            }
        }
        Text(
            text = selectorViewState.selectedChainName ?: stringResource(R.string.chain_selection_all_networks),
            style = MaterialTheme.customTypography.body1,
            maxLines = 1
        )
        Icon(
            painter = painterResource(id = R.drawable.ic_arrow_down),
            contentDescription = null,
            modifier = Modifier.padding(8.dp),
            tint = white
        )
    }
}

@Preview
@Composable
private fun ChainSelectorPreview() {
    ChainSelector(
        selectorViewState = ChainSelectorViewState(
            selectedChainId = "id",
            selectedChainName = "Kusama",
            selectedChainStatusColor = colorAccent
        ),
        onChangeChainClick = {}
    )
}
