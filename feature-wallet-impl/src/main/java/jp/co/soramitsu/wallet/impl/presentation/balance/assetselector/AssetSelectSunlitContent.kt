package jp.co.soramitsu.wallet.impl.presentation.balance.assetselector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import jp.co.soramitsu.common.compose.component.CorneredInput
import jp.co.soramitsu.common.compose.component.FearlessProgress
import jp.co.soramitsu.common.compose.component.TestnetBadge
import jp.co.soramitsu.common.compose.component.getImageRequest
import jp.co.soramitsu.common.compose.theme.fw.FwCheckMark
import jp.co.soramitsu.common.compose.theme.fw.FwEmptyState
import jp.co.soramitsu.common.compose.theme.fw.FwExpandChevron
import jp.co.soramitsu.common.compose.theme.fw.FwIconWell
import jp.co.soramitsu.common.compose.theme.fw.FwListRow
import jp.co.soramitsu.common.compose.theme.fw.FwTextRole
import jp.co.soramitsu.common.compose.theme.fw.LocalFwColors
import jp.co.soramitsu.common.compose.theme.fw.fwTextStyle
import jp.co.soramitsu.feature_wallet_impl.R

private val GUTTER = 20.dp

/**
 * The asset picker in the sun-lit design. The network stays part of the label and is never cut: it is
 * part of the choice. Same state, callbacks and test tags as the legacy page.
 */
@Composable
internal fun AssetSelectSunlitContent(state: AssetSelectScreenViewState, callback: AssetSelectContentInterface) {
    val c = LocalFwColors.current
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.common_select_asset),
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(horizontal = GUTTER),
            style = fwTextStyle(FwTextRole.TitleM),
            color = c.textPrimary
        )
        Spacer(Modifier.height(16.dp))
        CorneredInput(
            modifier = Modifier.padding(horizontal = GUTTER),
            state = state.searchQuery,
            onInput = callback::onSearchInput,
            hintLabel = stringResource(R.string.assets_search_hint)
        )
        Spacer(Modifier.height(12.dp))
        if (state.isLoading) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { FearlessProgress() }
        } else if (state.assets.isEmpty() && state.hiddenAssets.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = GUTTER), contentAlignment = Alignment.TopCenter) {
                when (state.emptyMessage) {
                    AssetSelectEmptyMessage.NothingFound -> FwEmptyState(
                        title = stringResource(R.string.common_search_assets_alert_title),
                        description = stringResource(R.string.common_search_network_and_assets_alert_description),
                        iconRes = jp.co.soramitsu.common.R.drawable.ic_alert_24
                    )
                    AssetSelectEmptyMessage.NothingToSend -> FwEmptyState(
                        title = stringResource(R.string.asset_select_nothing_to_send),
                        iconRes = jp.co.soramitsu.common.R.drawable.ic_alert_24
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = GUTTER, end = GUTTER, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.assets, key = { "${it.chainId}:${it.id}" }) { SunlitAssetRow(it, callback::onAssetSelected) }
                if (state.hiddenAssets.isNotEmpty()) {
                    item(key = "hidden-section") {
                        FwListRow(
                            modifier = Modifier.testTag("AssetSelect_hidden_section"),
                            onClick = callback::onHiddenSectionToggle
                        ) {
                            Text(
                                "${stringResource(R.string.hidden_assets)} · ${state.hiddenAssets.size}",
                                modifier = Modifier.weight(1f),
                                style = fwTextStyle(FwTextRole.LabelL),
                                color = c.textSecondary
                            )
                            FwExpandChevron(
                                expanded = state.isHiddenExpanded,
                                contentDescription = stringResource(
                                    if (state.isHiddenExpanded) R.string.common_collapse else R.string.common_expand
                                )
                            )
                        }
                    }
                    if (state.isHiddenExpanded) {
                        items(state.hiddenAssets, key = { "hidden:${it.chainId}:${it.id}" }) {
                            SunlitAssetRow(it, callback::onAssetSelected)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(36.dp))
    }
}

/** "SYMBOL · Network", never cut: the network is part of the choice. */
@Composable
private fun SunlitAssetLabel(state: AssetItemState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            state.label,
            modifier = Modifier.weight(1f, fill = false).testTag("AssetItem_label_${state.chainId}_${state.id}"),
            style = fwTextStyle(FwTextRole.LabelL),
            color = LocalFwColors.current.textPrimary
        )
        if (state.isTestnet) {
            Spacer(Modifier.width(4.dp))
            TestnetBadge()
        }
    }
}

@Composable
private fun SunlitAssetAmount(state: AssetItemState, alignment: Alignment.Horizontal) {
    val c = LocalFwColors.current
    val textAlign = if (alignment == Alignment.End) TextAlign.End else TextAlign.Start
    Column(horizontalAlignment = alignment) {
        Text(
            state.amount ?: stringResource(R.string.portfolio_balance_unavailable),
            style = fwTextStyle(FwTextRole.NumM),
            color = c.textPrimary,
            textAlign = textAlign
        )
        if (state.fiatAmount.isNotBlank()) {
            Text(state.fiatAmount, style = fwTextStyle(FwTextRole.NumS), color = c.textSecondary, textAlign = textAlign)
        }
    }
}

@Composable
private fun SunlitAssetRow(state: AssetItemState, onSelected: (AssetItemState) -> Unit) {
    val c = LocalFwColors.current
    FwListRow(selected = state.isSelected, onClick = { onSelected(state) }) {
        FwIconWell(size = 40.dp) {
            AsyncImage(
                model = state.imageUrl?.let { getImageRequest(LocalContext.current, it) },
                contentDescription = null,
                modifier = Modifier.testTag("AssetItem_image_${state.id}").size(26.dp)
            )
        }
        // At large text the amount goes under the name: side by side it would squeeze the name to a letter a line.
        if (LocalDensity.current.fontScale > 1.3f) {
            Column(Modifier.weight(1f)) {
                SunlitAssetLabel(state)
                SunlitAssetAmount(state, Alignment.Start)
            }
        } else {
            Box(Modifier.weight(1f)) { SunlitAssetLabel(state) }
            SunlitAssetAmount(state, Alignment.End)
        }
        if (state.isSelected) FwCheckMark(Modifier.testTag("ChainItem_image_selected"))
    }
}
