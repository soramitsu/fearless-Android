package jp.co.soramitsu.wallet.impl.presentation.balance.assetselector

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import jp.co.soramitsu.common.compose.component.B0
import jp.co.soramitsu.common.compose.component.B1
import jp.co.soramitsu.common.compose.component.CorneredInput
import jp.co.soramitsu.common.compose.component.H3
import jp.co.soramitsu.common.compose.component.H5
import jp.co.soramitsu.common.compose.component.Image
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.TestnetBadge
import jp.co.soramitsu.common.compose.component.getImageRequest
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.black4
import jp.co.soramitsu.common.compose.theme.gray2
import jp.co.soramitsu.common.utils.clickableWithNoIndication
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

data class AssetSelectScreenViewState(
    val assets: List<AssetItemState>,
    /** Hidden assets the wallet can spend; listed in their own collapsed section. */
    val hiddenAssets: List<AssetItemState> = emptyList(),
    val isHiddenExpanded: Boolean = false,
    val searchQuery: String? = null,
    val showAllChains: Boolean = true,
    val emptyMessage: AssetSelectEmptyMessage = AssetSelectEmptyMessage.NothingFound
) {
    companion object {
        val default = AssetSelectScreenViewState(emptyList())
    }
}

enum class AssetSelectEmptyMessage {
    NothingFound,

    /** A spending picker lists only what can be sent; say so instead of "nothing found". */
    NothingToSend
}

interface AssetSelectContentInterface {
    fun onAssetSelected(assetItemState: AssetItemState)
    fun onSearchInput(input: String)
    fun onHiddenSectionToggle() {}
}

@Composable
fun AssetSelectContent(
    state: AssetSelectScreenViewState,
    callback: AssetSelectContentInterface
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
    ) {
        H3(
            text = stringResource(id = R.string.common_select_asset),
            modifier = Modifier.align(CenterHorizontally)
        )
        MarginVertical(margin = 16.dp)
        CorneredInput(state = state.searchQuery, onInput = callback::onSearchInput, hintLabel = stringResource(id = R.string.assets_search_hint))
        if (state.assets.isEmpty() && state.hiddenAssets.isEmpty()) {
            MarginVertical(margin = 16.dp)
            Column(
                horizontalAlignment = CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .align(CenterHorizontally)
            ) {
                EmptyResultContent(state.emptyMessage)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.assets, key = { "${it.chainId}:${it.id}" }) { asset ->
                    AssetItem(
                        state = asset,
                        onSelected = callback::onAssetSelected
                    )
                }
                if (state.hiddenAssets.isNotEmpty()) {
                    item(key = "hidden-section") {
                        HiddenAssetsHeader(
                            count = state.hiddenAssets.size,
                            isExpanded = state.isHiddenExpanded,
                            onToggle = callback::onHiddenSectionToggle
                        )
                    }
                    if (state.isHiddenExpanded) {
                        items(state.hiddenAssets, key = { "hidden:${it.chainId}:${it.id}" }) { asset ->
                            AssetItem(
                                state = asset,
                                onSelected = callback::onAssetSelected
                            )
                        }
                    }
                }
            }
        }
        MarginVertical(margin = 52.dp)
    }
}

@Composable
private fun HiddenAssetsHeader(
    count: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        verticalAlignment = CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("AssetSelect_hidden_section")
            .clickable(onClick = onToggle)
    ) {
        B1(
            text = "${stringResource(id = R.string.hidden_assets)} · $count",
            color = black2,
            modifier = Modifier.weight(1f)
        )
        Icon(
            painter = painterResource(
                if (isExpanded) R.drawable.ic_chevron_up_white else R.drawable.ic_chevron_down_white
            ),
            contentDescription = stringResource(
                if (isExpanded) R.string.common_collapse else R.string.common_expand
            ),
            tint = gray2
        )
    }
}

@Composable
fun EmptyResultContent(message: AssetSelectEmptyMessage = AssetSelectEmptyMessage.NothingFound) {
    Column(
        horizontalAlignment = CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_alert),
            contentDescription = null,
            tint = gray2
        )
        when (message) {
            AssetSelectEmptyMessage.NothingFound -> {
                H3(text = stringResource(id = R.string.common_search_assets_alert_title))
                B0(
                    text = stringResource(id = R.string.common_search_network_and_assets_alert_description),
                    color = gray2
                )
            }
            AssetSelectEmptyMessage.NothingToSend -> B0(
                text = stringResource(id = R.string.asset_select_nothing_to_send),
                color = gray2,
                textAlign = TextAlign.Center
            )
        }
    }
}

data class AssetItemState(
    val id: String,
    val imageUrl: String?,
    val chainName: String?,
    val symbol: String,
    val amount: String?,
    val fiatAmount: String,
    val isSelected: Boolean = false,
    val chainId: ChainId,
    val isTestnet: Boolean = false
) {
    /** "SYMBOL · Network": the network is part of the choice. */
    val label: String
        get() = listOfNotNull(symbol, chainName?.takeIf(String::isNotBlank)).joinToString(" · ")
}

@Composable
fun AssetItem(
    state: AssetItemState,
    onSelected: (AssetItemState) -> Unit
) {
    // At least 48dp; large text grows the row instead of clipping the label or the amount.
    Row(
        verticalAlignment = CenterVertically,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .fillMaxWidth()
    ) {
        Row(
            verticalAlignment = CenterVertically,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp)
                .clickableWithNoIndication { onSelected(state) }
        ) {
            AsyncImage(
                model = state.imageUrl?.let { getImageRequest(LocalContext.current, it) },
                contentDescription = null,
                modifier = Modifier
                    .testTag("AssetItem_image_${state.id}")
                    .size(32.dp)
            )
            MarginHorizontal(margin = 10.dp)
            Row(
                verticalAlignment = CenterVertically
            ) {
                Row(
                    verticalAlignment = CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Never ellipsized: the network is part of the choice.
                    B1(
                        text = state.label,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .testTag("AssetItem_label_${state.chainId}_${state.id}")
                    )
                    if (state.isTestnet) {
                        MarginHorizontal(margin = 4.dp)
                        TestnetBadge()
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    if (state.fiatAmount.isNotBlank()) {
                        B1(text = state.fiatAmount, color = black2)
                    }
                    H5(text = state.amount ?: stringResource(R.string.portfolio_balance_unavailable))
                }
            }
        }
        MarginHorizontal(margin = 10.dp)
        if (state.isSelected) {
            Image(
                res = R.drawable.ic_selected,
                modifier = Modifier
                    .wrapContentWidth()
                    .testTag("ChainItem_image_selected")
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
            imageUrl = "https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/assets/icons/chains/white/Moonriver.svg",
            chainName = "Kusama",
            symbol = "KSM",
            amount = "0",
            fiatAmount = "0$",
            isSelected = false,
            chainId = ""
        ),
        AssetItemState(
            id = "2",
            imageUrl = "https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/assets/icons/chains/white/Kusama.svg",
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
    Column(
        Modifier.background(black4)
    ) {
        AssetSelectContent(
            state = state,
            callback = object : AssetSelectContentInterface {
                override fun onAssetSelected(assetItemState: AssetItemState) {}
                override fun onSearchInput(input: String) {}
            }
        )
    }
}
