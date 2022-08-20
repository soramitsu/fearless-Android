package jp.co.soramitsu.wallet.impl.presentation.balance.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import jp.co.soramitsu.common.compose.component.AssetListItem
import jp.co.soramitsu.common.compose.component.MultiToggleButton
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.presentation.LoadingState


@OptIn(ExperimentalUnitApi::class)
@Composable
fun WalletScreen(
    viewModel: BalanceListViewModel = hiltViewModel()
) {

    val state by viewModel.uiState

    var selectedOption by remember { mutableStateOf("Currencies") }
    val onSelectionChange = { text: String ->
        selectedOption = text
    }

    when (state) {
        is LoadingState.Loading -> {
            Text(
                text = "LOADING...",
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Magenta),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Black,
                fontSize = TextUnit(40f, TextUnitType.Sp)
            )
        }
        is LoadingState.Loaded -> {
            val data = (state as LoadingState.Loaded<WalletState>).data
            Column {
                Text(
                    text = "TOOLBAR",
                    modifier = Modifier
                        .height(40.dp)
                        .align(CenterHorizontally),
                    textAlign = TextAlign.Center
                )
                MultiToggleButton(
                    currentSelection = selectedOption,
                    toggleStates = listOf("Currencies", "NFTs"),
                    onToggleChange = {
                        println("!!! toggle = $it")
                        onSelectionChange(it)
                        viewModel.handleSelection(it)
                    }
                )
                LazyColumn {
                    items(data.assets) { asset ->
                        AssetListItem(asset, modifier = Modifier.padding(8.dp))
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun PreviewWalletScreen() {
    FearlessTheme {
        Surface(Modifier.background(Color.Black)) {
            WalletScreen()
        }
    }
}
