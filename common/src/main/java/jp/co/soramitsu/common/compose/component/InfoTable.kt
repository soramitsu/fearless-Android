package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.theme.FearlessTheme

@Composable
fun InfoTable(
    items: List<TitleValueViewState>,
    modifier: Modifier = Modifier,
    onItemClick: (Int) -> Unit = {}
) {
    BackgroundCorneredWithBorder(
        modifier = modifier
            .fillMaxWidth()
    ) {
        Column {
            MarginVertical(margin = 6.dp)
            items.forEach {
                InfoTableItem(it, onClick = onItemClick)
            }
            MarginVertical(margin = 8.dp)
        }
    }
}

@Preview
@Composable
private fun InfoTableItemPreview() {
    val state = listOf(
        TitleValueViewState(
            "From",
            "Account for Join",
            "0xEBN4KURhvkURhvkURhvkURhvkURhvkURhvk"
        ),
        TitleValueViewState(
            "From",
            "Account for Join",
            null
        ),
        TitleValueViewState(
            "From",
            null,
            null
        )
    )
    FearlessTheme {
        Column(Modifier.padding(16.dp)) {
            InfoTable(state)
            MarginVertical(margin = 16.dp)
            InfoTable(
                listOf(
                    TitleValueViewState(
                        "nominator",
                        "Account for Join",
                        "0xEBN4KURhvkURhvkURhvkURhvkURhvkURhvkN4KURhvkURhvkURhvkURhvkURhvkURhvkN4KURhvkURhvkURhvkURhvkURhvkURhvk"
                    ),
                    TitleValueViewState(
                        "From",
                        "0xEBN4KURhvkURhvkURhvkURhvkURhvkURhvkN4KURhvkURhvkURhvkURhvkURhvkURhvkN4KURhvkURhvkURhvkURhvkURhvkURhvk",
                        null
                    ),
                    TitleValueViewState(
                        "From",
                        "0xEBN4KURhvkURhvkURhvkURhvkURhvkURhvkN4KURhvkURhvkURhvkURhvkURhvkURhvkN4KURhvkURhvkURhvkURhvkURhvkURhvk",
                        null
                    )
                )
            )
        }
    }
}
