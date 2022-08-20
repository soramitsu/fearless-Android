package jp.co.soramitsu.wallet.impl.presentation.balance.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.MultiToggleButton
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.presentation.LoadingState

@OptIn(ExperimentalUnitApi::class)
@Composable
fun WalletScreen(
    viewModel: BalanceListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    when (state) {
        is LoadingState.Loading<WalletState> -> {
            Text(
                text = "LOADING...",
                modifier = Modifier
                    .fillMaxSize(),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Black,
                fontSize = TextUnit(40f, TextUnitType.Sp)
            )
        }
        is LoadingState.Loaded<WalletState> -> {
            val data = (state as LoadingState.Loaded<WalletState>).data
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                MarginVertical(margin = 16.dp)
                // todo it's Balance component here
                Box(
                    modifier = Modifier
                        .height(58.dp)
                        .fillMaxWidth()
                )
                MarginVertical(margin = 24.dp)
                MultiToggleButton(
                    data.multiToggleButtonState,
                    onToggleChange = viewModel::assetTypeChanged
                )
                MarginVertical(margin = 16.dp)
                LazyColumn {
                    items(data.assets) { asset ->
                        AssetListItem(asset) { viewModel.assetClicked(it) }
                        MarginVertical(margin = 8.dp)
                    }
                    item { MarginVertical(margin = 80.dp) }
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
