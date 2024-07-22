package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.bold
import jp.co.soramitsu.common.compose.theme.customTypography

data class TitleIconValueState(
    val title: String,
    val iconUrl: String? = null,
    val value: String? = null
)

@Composable
fun InfoTableItemAsset(state: TitleIconValueState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 55.dp)
            .padding(vertical = 6.dp, horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterVertically)
        ) {
            H5(
                modifier = Modifier.align(Alignment.CenterVertically),
                text = state.title,
                color = black2
            )
        }
        MarginHorizontal(margin = 16.dp)
        Row(
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically),
            horizontalArrangement = Arrangement.End
        ) {
            state.iconUrl?.let {
                AsyncImage(
                    model = getImageRequest(LocalContext.current, it),
                    contentDescription = null,
                    modifier = Modifier
                        .size(12.dp)
                        .align(Alignment.CenterVertically)
                )
                MarginHorizontal(margin = 5.dp)
            }
            state.value?.let {
                Text(
                    text = it,
                    modifier = Modifier.align(Alignment.CenterVertically),
                    style = MaterialTheme.customTypography.header5.bold(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Preview
@Composable
private fun InfoTableCustomPreview() {
    val state = TitleIconValueState(
        "From",
        "https://raw.githubusercontent.com/soramitsu/shared-features-utils/master/icons/tokens/coloured/PSWAP.svg",
        "PSWAP",
    )
    FearlessTheme {
        Column {
            InfoTableItemAsset(state)
        }
    }
}
