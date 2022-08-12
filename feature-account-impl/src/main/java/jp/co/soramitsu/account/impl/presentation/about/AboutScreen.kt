package jp.co.soramitsu.account.impl.presentation.about

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import jp.co.soramitsu.common.compose.component.AssetChainsBadge
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.presentation.LoadingState

@Composable
fun AboutScreen(
    viewModel: AboutViewModel = hiltViewModel()
) {
    val state = viewModel.uiState.value

    when (state) {
        is LoadingState.Loading -> {}
        is LoadingState.Loaded -> { state.data }
    }

    val list = listOf(
        "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Moonbeam.svg",
        "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Polkadot.svg",
        "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Karura.svg",
        "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Statemine.svg",
        "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Moonriver.svg",
        "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Rococo.svg"
    )

    AssetChainsBadge(list, Modifier.padding(0.dp, 24.dp, 0.dp, 0.dp))
}

@Preview
@Composable
fun PreviewAboutScreen() {
    FearlessTheme {
        AboutScreen()
    }
}
