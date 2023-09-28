package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.white50

data class InfoItemSetViewState(
    val title: String?,
    val infoItems: List<InfoItemViewState>
)

@Composable
fun InfoItemSet(state: InfoItemSetViewState) {
    BackgroundCorneredWithBorder(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth()
                .wrapContentHeight(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            state.title?.let {
                    Column {
                        H4(
                            modifier = Modifier.fillMaxWidth(),
                            text = state.title,
                            color = white50,
                            textAlign = TextAlign.Start
                        )
                        MarginVertical(margin = 4.dp)
                    }
            }
            state.infoItems.map { infoItemState ->
                InfoItemContent(state = infoItemState)
            }
        }
    }
}

@Preview
@Composable
private fun InfoItemSetPreview() {
    val state = InfoItemSetViewState(
        title = "Some required chain name",
        infoItems = listOf(
            InfoItemViewState(
                title = "Methods",
                subtitle = "eth_sendTransaction, personal_sign"
            ),
            InfoItemViewState(
                title = "Events",
                subtitle = "accountsChanged, chainChanged"
            )
        )
    )
    FearlessTheme {
        InfoItemSet(state)
    }
}
