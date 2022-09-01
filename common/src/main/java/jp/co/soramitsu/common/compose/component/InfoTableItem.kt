package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.bold
import jp.co.soramitsu.common.compose.theme.customTypography

@Composable
fun InfoTableItem(state: TitleValueViewState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        H5(
            text = state.title,
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .weight(1f),
            color = black2
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .weight(1f)
        ) {
            state.value?.let {
                Text(
                    text = it,
                    modifier = Modifier.align(Alignment.End),
                    style = MaterialTheme.customTypography.header5.bold()
                )
            } ?: ShimmerB0(
                modifier = Modifier
                    .width(120.dp)
                    .align(Alignment.End)
            )

            state.additionalValue?.let {
                val text = if (it.length > 20) {
                    "${it.take(5)}...${it.takeLast(5)}"
                } else {
                    it
                }
                B1(
                    text = text,
                    color = black2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Preview
@Composable
private fun InfoTableItemPreview() {
    val state = TitleValueViewState(
        "From",
        "Account for Join",
        "0xEBN4KURhvkURhvkURhvkURhvkURhvkURhvk"
    )
    FearlessTheme {
        Column {
            InfoTableItem(state)
            InfoTableItem(
                TitleValueViewState(
                    "From",
                    null,
                    null
                )
            )
        }
    }
}
