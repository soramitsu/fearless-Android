package jp.co.soramitsu.wallet.impl.presentation.balance.assetselector

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import jp.co.soramitsu.common.compose.component.B1
import jp.co.soramitsu.common.compose.component.CorneredInput
import jp.co.soramitsu.common.compose.component.Grip
import jp.co.soramitsu.common.compose.component.H3
import jp.co.soramitsu.common.compose.component.H5
import jp.co.soramitsu.common.compose.component.Image
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.getImageRequest
import jp.co.soramitsu.common.compose.theme.FearlessThemeBlackBg
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.black4
import jp.co.soramitsu.common.utils.clickableWithNoIndication
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

data class AssetSelectScreenViewState(
    val assets: List<AssetItemState>,
    val selectedAssetId: String? = null,
    val searchQuery: String? = null,
    val showAllChains: Boolean = true
) {
    companion object {
        val default = AssetSelectScreenViewState(emptyList())
    }
}

@Composable
fun AssetSelectContent(
    state: AssetSelectScreenViewState,
    onAssetSelected: (AssetItemState) -> Unit = {},
    onInput: (String) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
    ) {
        MarginVertical(margin = 2.dp)
        Grip(Modifier.align(CenterHorizontally))
        MarginVertical(margin = 8.dp)
        H3(text = stringResource(id = R.string.common_select_asset))
        MarginVertical(margin = 16.dp)
        CorneredInput(state = state.searchQuery, onInput = onInput, hintLabel = stringResource(id = R.string.assets_search_hint))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.assets.map { it.copy(isSelected = it.id == state.selectedAssetId) }) { chain ->
                AssetItem(
                    state = chain,
                    onSelected = onAssetSelected
                )
            }
        }
        MarginVertical(margin = 52.dp)
    }
}

data class AssetItemState(
    val id: String,
    val imageUrl: String?,
    val chainName: String,
    val symbol: String,
    val amount: String,
    val fiatAmount: String,
    val isSelected: Boolean = false,
    val chainId: ChainId
)

@Composable
fun AssetItem(
    state: AssetItemState,
    onSelected: (AssetItemState) -> Unit
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
                    .testTag("AssetItem_image_${state.id}")
                    .size(24.dp)
            )
            MarginHorizontal(margin = 10.dp)
            Column {
                Row {
                    H5(text = state.chainName, color = black2)
                    Spacer(modifier = Modifier.weight(1f))
                    B1(text = state.fiatAmount, color = black2)
                }
                Row {
                    B1(text = state.symbol)
                    Spacer(modifier = Modifier.weight(1f))
                    H5(text = state.amount)
                }
            }
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

@Preview
@Composable
private fun SelectAssetScreenPreview() {
    val items = listOf(
        AssetItemState(
            id = "1",
            imageUrl = "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Moonriver.svg",
            chainName = "Kusama",
            symbol = "KSM",
            amount = "0",
            fiatAmount = "0$",
            isSelected = false,
            chainId = ""
        ),
        AssetItemState(
            id = "2",
            imageUrl = "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Kusama.svg",
            chainName = "Moonriver",
            symbol = "MOVR",
            amount = "10",
            fiatAmount = "23240$",
            isSelected = false,
            chainId = ""
        )
    )
    val state = AssetSelectScreenViewState(
        assets = items,
        searchQuery = null
    )
    FearlessThemeBlackBg {
        Column(
            Modifier.background(black4)
        ) {
            AssetSelectContent(
                state = state
            )
        }
    }
}
