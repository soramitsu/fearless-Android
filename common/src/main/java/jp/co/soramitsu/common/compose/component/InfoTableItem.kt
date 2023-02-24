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
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.bold
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.greenText
import jp.co.soramitsu.common.utils.clickableWithNoIndication

@Composable
fun InfoTableItem(state: TitleValueViewState, onClick: (Int) -> Unit = {}) {
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
        val clickModifier = if (state.value != null && state.clickState != null) {
            Modifier.clickableWithNoIndication { onClick(state.clickState.identifier) }
        } else {
            Modifier
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically)
                .then(clickModifier)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .fillMaxWidth()
            ) {
                state.value?.let {
                    Text(
                        text = it,
                        color = state.valueColor,
                        modifier = Modifier.align(Alignment.End),
                        style = MaterialTheme.customTypography.header5.bold(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                        modifier = Modifier.align(Alignment.End),
                        maxLines = 1
                    )
                }
            }
        }
        state.clickState?.let {
            Image(
                res = it.icon,
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .padding(start = 8.dp)
                    .align(Alignment.CenterVertically)
                    .clickableWithNoIndication { onClick(state.clickState.identifier) }
            )
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
            InfoTableItem(
                TitleValueViewState(
                    "From",
                    null,
                    null,
                    clickState = TitleValueViewState.ClickState(R.drawable.ic_info_14, 1)
                )
            )
            InfoTableItem(
                TitleValueViewState(
                    "From",
                    "8484834",
                    null,
                    greenText,
                    clickState = TitleValueViewState.ClickState(R.drawable.ic_info_14, 1)
                )
            )
            InfoTableItem(
                TitleValueViewState(
                    "From",
                    "8484834",
                    "sd434f34f3wf434f34f34f34f34f",
                    clickState = TitleValueViewState.ClickState(R.drawable.ic_info_14, 1)
                )
            )
            InfoTableItem(
                TitleValueViewState(
                    "From",
                    "84848348484834848483484848348484834848483484848348484834848483484848348484834848483484848348484834",
                    "sd434f34f3wf434f34f34f34f34f",
                    clickState = TitleValueViewState.ClickState(R.drawable.ic_info_14, 1)
                )
            )
        }
    }
}
