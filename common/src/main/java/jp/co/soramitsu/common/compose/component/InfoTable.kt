package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.compose.theme.FearlessTheme

@Composable
fun InfoTable(items: List<TitleValueViewState>) {
    BackgroundCorneredWithBorder(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Column {
            items.forEach {
                InfoTableItem(it)
            }
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
        InfoTable(state)
    }
}
