package jp.co.soramitsu.tonconnect.impl.presentation.discoverdapp

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.jp.soramitsu.feature_tonconnect_impl.R
import co.jp.soramitsu.tonconnect.model.DappModel
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.CorneredInput
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState

data class DappsListState(val title: String, val dapps: List<DappModel>)

@Composable
fun SeeAllDappsBottomSheet(state: DappsListState, onDappSelected: (String) -> Unit, onCloseClick: () -> Unit) {
    val searchQueryState = remember { mutableStateOf("") }
    val filteredDapps = if (searchQueryState.value.isEmpty()) {
        state.dapps
    } else {
        state.dapps.filter {
            it.name?.lowercase().orEmpty().contains(searchQueryState.value.lowercase()) ||
                    it.url?.lowercase().orEmpty().contains(searchQueryState.value.lowercase()) ||
                    it.description?.lowercase().orEmpty()
                        .contains(searchQueryState.value.lowercase())
        }
    }
    BottomSheetScreen  {
        Toolbar(ToolbarViewState(state.title, R.drawable.ic_cross_24), onNavigationClick = onCloseClick)
        MarginVertical(12.dp)
        CorneredInput(
            modifier = Modifier.padding(horizontal = 16.dp),
            state = searchQueryState.value,
            onInput = { searchQueryState.value = it },
            hintLabel = stringResource(id = R.string.common_search)
        )

        if (filteredDapps.isEmpty()) {
            EmptySumimasen()
        } else {
            LazyColumn(modifier = Modifier.fillMaxHeight(), contentPadding = PaddingValues(bottom = 80.dp)) {
                items(filteredDapps) {
                    DappItem(it, onDappSelected, null)
                }
            }
        }
    }
}

@Composable
@Preview
fun SeeAllPreview() {
    val dapps = listOf(
        DappModel(
            identifier = "",
            chains = listOf(),
            name = "DeDust",
            url = "Dapp url",
            description = "dApp description",
            background = "",
            icon = ""
        ),
        DappModel(
            identifier = "",
            chains = listOf(),
            name = "DeDust",
            url = "Dapp url",
            description = "dApp description",
            background = "",
            icon = ""
        ),
        DappModel(
            identifier = "",
            chains = listOf(),
            name = "DeDust",
            url = "Dapp url",
            description = "dApp description",
            background = "",
            icon = ""
        ),
    )
    SeeAllDappsBottomSheet(DappsListState("Featured", dapps), {}, {})
}