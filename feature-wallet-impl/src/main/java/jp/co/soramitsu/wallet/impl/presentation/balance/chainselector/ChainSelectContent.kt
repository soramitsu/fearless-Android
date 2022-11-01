package jp.co.soramitsu.wallet.impl.presentation.balance.chainselector

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import jp.co.soramitsu.common.compose.component.CorneredInput
import jp.co.soramitsu.common.compose.component.Grip
import jp.co.soramitsu.common.compose.component.H3
import jp.co.soramitsu.common.compose.component.H4
import jp.co.soramitsu.common.compose.component.Image
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.getImageRequest
import jp.co.soramitsu.common.compose.theme.FearlessThemeBlackBg
import jp.co.soramitsu.common.compose.theme.black4
import jp.co.soramitsu.common.utils.clickableWithNoIndication
import jp.co.soramitsu.coredb.model.chain.JoinedChainInfo
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

data class ChainSelectScreenViewState(
    val chains: List<ChainItemState>,
    val selectedChainId: ChainId?,
    val searchQuery: String? = null,
    val showAllChains: Boolean = true
) {
    companion object {
        val default = ChainSelectScreenViewState(emptyList(), null)
    }
}

@Composable
fun ChainSelectContent(
    state: ChainSelectScreenViewState,
    onChainSelected: (chainItemState: ChainItemState?) -> Unit = {},
    onSearchInput: (input: String) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
    ) {
        MarginVertical(margin = 2.dp)
        Grip(Modifier.align(CenterHorizontally))
        MarginVertical(margin = 8.dp)
        H3(text = stringResource(id = R.string.common_select_network))
        MarginVertical(margin = 16.dp)
        CorneredInput(state = state.searchQuery, onInput = onSearchInput)
        LazyColumn(modifier = Modifier.weight(1f)) {
            if (state.showAllChains) {
                item {
                    ChainAllItem(
                        isSelected = state.selectedChainId == null,
                        onSelected = onChainSelected
                    )
                }
            }
            items(state.chains.map { it.copy(isSelected = it.id == state.selectedChainId) }) { chain ->
                ChainItem(
                    state = chain,
                    onSelected = onChainSelected
                )
            }
        }
        MarginVertical(margin = 52.dp)
    }
}

data class ChainItemState(
    val id: String,
    val imageUrl: String?,
    val title: String,
    val isSelected: Boolean = false,
    val tokenSymbols: List<Pair<String, String>> = listOf()
)

fun JoinedChainInfo.toChainItemState() = ChainItemState(
    id = chain.id,
    imageUrl = chain.icon,
    title = chain.name,
    isSelected = false,
    tokenSymbols = assets.map { it.id to it.symbol }
)

@Composable
fun ChainItem(
    state: ChainItemState,
    onSelected: (ChainItemState?) -> Unit
) {
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(48.dp)
                .fillMaxWidth()
                .clickableWithNoIndication { onSelected(state) }
        ) {
            AsyncImage(
                model = state.imageUrl?.let { getImageRequest(LocalContext.current, it) },
                contentDescription = null,
                modifier = Modifier
                    .testTag("ChainItem_image_${state.id}")
                    .size(24.dp)
            )
            MarginHorizontal(margin = 10.dp)
            H4(text = state.title)
        }
        if (state.isSelected) {
            Image(
                res = R.drawable.ic_selected,
                modifier = Modifier
                    .testTag("ChainItem_image_selected")
                    .align(Alignment.CenterEnd)
                    .size(24.dp)
            )
        }
    }
}

@Composable
fun ChainAllItem(
    isSelected: Boolean,
    onSelected: (ChainItemState?) -> Unit
) {
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(48.dp)
                .fillMaxWidth()
                .clickableWithNoIndication { onSelected(null) }
        ) {
            Image(
                res = R.drawable.ic_all_chains,
                modifier = Modifier
                    .testTag("ChainItem_image_all_networks")
                    .size(24.dp)
            )
            MarginHorizontal(margin = 10.dp)
            H4(text = stringResource(id = R.string.chain_selection_all_networks))
        }
        if (isSelected) {
            Image(
                res = R.drawable.ic_selected,
                modifier = Modifier
                    .testTag("ChainItem_image_selected")
                    .align(Alignment.CenterEnd)
                    .size(24.dp)
            )
        }
    }
}

@Preview
@Composable
private fun SelectChainScreenPreview() {
    val items = listOf(
        ChainItemState(
            id = "1",
            imageUrl = "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Moonriver.svg",
            title = "Kusama"
        ),
        ChainItemState(
            id = "2",
            imageUrl = "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Kusama.svg",
            title = "Moonriver"
        )
    )
    val state = ChainSelectScreenViewState(
        chains = items,
        selectedChainId = null,
        searchQuery = null
    )
    FearlessThemeBlackBg {
        Column(
            Modifier.background(black4)
        ) {
            ChainSelectContent(state = state)
        }
    }
}
