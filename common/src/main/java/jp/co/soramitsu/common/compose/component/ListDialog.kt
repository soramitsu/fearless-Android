package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.theme.black2

data class ListDialogState<T : ListDialogState.Item>(
    val titleRes: Int,
    val items: List<T>
) {
    interface Item {
        val titleRes: Int
    }
}

@Composable
fun <T : ListDialogState.Item> ColumnScope.ListDialog(state: ListDialogState<T>, onSelected: (T) -> Unit) {
    H3(
        text = stringResource(state.titleRes),
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
    )
    LazyColumn {
        items(state.items) {
            H5(
                text = stringResource(it.titleRes),
                color = black2,
                modifier = Modifier
                    .clickable { onSelected(it) }
                    .padding(16.dp)
                    .fillMaxWidth()
            )
        }
    }
}
