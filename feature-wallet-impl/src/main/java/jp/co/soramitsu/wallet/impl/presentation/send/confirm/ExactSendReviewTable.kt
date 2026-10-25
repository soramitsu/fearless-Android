package jp.co.soramitsu.wallet.impl.presentation.send.confirm

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.B2
import jp.co.soramitsu.common.compose.component.BackgroundCornered
import jp.co.soramitsu.common.compose.component.H3
import jp.co.soramitsu.common.compose.component.Image
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.theme.black2

/** Wraps complete native review values, retaining warning actions and their icons. */
@Composable
fun ExactSendReviewTable(items: List<TitleValueViewState>, onItemClick: (Int) -> Unit) {
    BackgroundCornered(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            items.forEach { item ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    .then(item.clickState?.let { click -> Modifier.clickable(role = Role.Button) { onItemClick(click.identifier) } } ?: Modifier)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        B2(text = item.title, color = black2, modifier = Modifier.weight(1f))
                        item.clickState?.let { Image(res = it.icon) }
                    }
                    item.value?.let { H3(text = it, color = item.valueColor, modifier = Modifier.fillMaxWidth()) }
                    item.additionalValue?.let { B2(text = it, color = black2, modifier = Modifier.fillMaxWidth()) }
                }
            }
        }
    }
}
