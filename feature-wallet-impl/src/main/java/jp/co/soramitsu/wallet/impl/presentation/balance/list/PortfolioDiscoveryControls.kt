package jp.co.soramitsu.wallet.impl.presentation.balance.list

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.B0
import jp.co.soramitsu.common.compose.component.B2
import jp.co.soramitsu.common.compose.component.ChainSelectorViewStateWithFilters
import jp.co.soramitsu.common.compose.component.CorneredInput
import jp.co.soramitsu.common.compose.component.GradientIcon
import jp.co.soramitsu.common.compose.component.GrayButton
import jp.co.soramitsu.common.compose.component.H3
import jp.co.soramitsu.common.compose.theme.alertYellow
import jp.co.soramitsu.common.compose.theme.fw.FwFilterChip
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import jp.co.soramitsu.common.compose.theme.fw.FwSelectorPill
import jp.co.soramitsu.common.compose.theme.fw.FwSurface
import jp.co.soramitsu.common.compose.theme.white50
import jp.co.soramitsu.feature_wallet_impl.R
import kotlin.math.roundToInt

/**
 * The one list's tools, always on the wallet home above the rows (web 4.2.9 parity): a search for
 * tokens and networks, then the network chips. All networks, a Popular or Favorites group chosen in
 * the full network list, every network the wallet holds something on (largest first), and More
 * networks, which opens that full list. A chip is the header's network filter; picking it again
 * shows every network.
 */
@Composable
fun PortfolioDiscoveryControls(
    data: AssetsLoadingState.Loaded,
    callback: WalletScreenInterface,
    modifier: Modifier = Modifier
) {
    val sunlit = FwRestyle.isEnabled(FwSurface.Portfolio)
    Column(modifier.fillMaxWidth()) {
        CorneredInput(
            modifier = Modifier.testTag(SEARCH_TAG),
            textModifier = Modifier.heightIn(min = CONTROL_HEIGHT),
            state = data.searchQuery,
            hintLabel = stringResource(R.string.portfolio_search_tokens_networks),
            onInput = callback::onPortfolioSearch
        )
        if (data.networkFilterAvailable) {
            NetworkChips(data, callback, sunlit)
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun NetworkChips(data: AssetsLoadingState.Loaded, callback: WalletScreenInterface, sunlit: Boolean) {
    val gutter = if (sunlit) SUNLIT_GUTTER else LEGACY_GUTTER
    val label = stringResource(R.string.portfolio_network_filters)
    val scroll = rememberScrollState()
    // Where the selected network's chip sits in the row: a network picked in the full list is scrolled into sight.
    var selectedPlacement by remember { mutableStateOf<Pair<String, IntRange>?>(null) }
    val gutterPx = with(LocalDensity.current) { gutter.roundToPx() }
    LaunchedEffect(data.selectedNetwork, selectedPlacement) {
        val (id, span) = selectedPlacement ?: return@LaunchedEffect
        if (id != data.selectedNetwork) return@LaunchedEffect
        // The row's content starts with the gutter; scrolling to the chip's own start leaves the gutter before it.
        val start = span.first + gutterPx
        val end = span.last + gutterPx
        if (start < scroll.value || end > scroll.value + scroll.viewportSize) scroll.animateScrollTo(span.first)
    }
    // The row runs under the page gutter to the screen edges, so a scrolled chip and its light are never cut.
    Row(
        Modifier
            .bleedHorizontally(gutter)
            .horizontalScroll(scroll)
            .padding(horizontal = gutter, vertical = if (sunlit) 12.dp else 8.dp)
            .semantics { contentDescription = label }
            .testTag(FILTERS_TAG),
        horizontalArrangement = Arrangement.spacedBy(if (sunlit) 10.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val chip: @Composable (String, Boolean, String, () -> Unit) -> Unit = { text, selected, tag, onClick ->
            val chipModifier = Modifier.heightIn(min = CONTROL_HEIGHT).testTag(tag)
            if (sunlit) {
                FwFilterChip(label = text, selected = selected, onClick = onClick, modifier = chipModifier)
            } else {
                FilterChip(selected = selected, onClick = onClick, modifier = chipModifier) { B2(text = text) }
            }
        }
        chip(stringResource(R.string.chain_selection_all_networks), !data.isNetworkFiltered, ALL_TAG) {
            callback.onPortfolioNetworkSelect(null)
        }
        // A group keeps its chip while it filters; it opens the full list, where the group is changed.
        data.networkGroup?.let { group ->
            chip(stringResource(groupTitle(group)), true, GROUP_TAG, callback::onPortfolioMoreNetworks)
        }
        data.networkOptions.forEach { network ->
            val selected = data.selectedNetwork == network.id
            Box(
                if (selected) {
                    Modifier.onPlaced { placed ->
                        val start = placed.positionInParent().x.roundToInt()
                        selectedPlacement = network.id to start..(start + placed.size.width)
                    }
                } else {
                    Modifier
                }
            ) {
                chip(network.name, selected, networkTag(network.id)) { callback.onPortfolioNetworkSelect(network.id) }
            }
        }
        val more = stringResource(R.string.portfolio_more_networks)
        if (sunlit) {
            FwSelectorPill(
                text = more,
                onClick = callback::onPortfolioMoreNetworks,
                modifier = Modifier.heightIn(min = CONTROL_HEIGHT).widthIn(max = MORE_MAX_WIDTH).testTag(MORE_TAG)
            )
        } else {
            chip(more, false, MORE_TAG, callback::onPortfolioMoreNetworks)
        }
    }
}

/**
 * What a search that found nothing says: nothing matched, and, while a network or group narrows the
 * list, a button that searches every network with the same words.
 */
@Composable
fun PortfolioSearchEmpty(data: AssetsLoadingState.Loaded, callback: WalletScreenInterface) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(SEARCH_EMPTY_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        GradientIcon(
            iconRes = R.drawable.ic_alert_24,
            color = alertYellow,
            contentPadding = PaddingValues(bottom = 4.dp)
        )
        H3(text = stringResource(R.string.common_search_assets_alert_title))
        B0(text = stringResource(R.string.common_search_network_and_assets_alert_description), color = white50)
        if (data.isNetworkFiltered && data.networkFilterAvailable) {
            GrayButton(
                text = stringResource(R.string.portfolio_search_all_networks),
                modifier = Modifier.fillMaxWidth().heightIn(min = CONTROL_HEIGHT).testTag(SEARCH_ALL_TAG),
                onClick = { callback.onPortfolioNetworkSelect(null) }
            )
        }
    }
}

private fun groupTitle(group: ChainSelectorViewStateWithFilters.Filter): Int = when (group) {
    ChainSelectorViewStateWithFilters.Filter.Favorite -> R.string.network_managment_favourite
    else -> R.string.network_management_popular
}

/** Widens the element by [gutter] on each side, centred where it was, so it can draw into the page gutter. */
internal fun Modifier.bleedHorizontally(gutter: Dp): Modifier = layout { measurable, constraints ->
    val extra = (gutter * 2).roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + extra,
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth
        )
    )
    layout((placeable.width - extra).coerceAtLeast(constraints.minWidth), placeable.height) {
        placeable.place(-extra / 2, 0)
    }
}

internal fun networkTag(chainId: String) = "portfolio-network-$chainId"

internal const val SEARCH_TAG = "portfolio-search"
internal const val FILTERS_TAG = "portfolio-network-filters"
internal const val ALL_TAG = "portfolio-network-all"
internal const val GROUP_TAG = "portfolio-network-group"
internal const val MORE_TAG = "portfolio-network-more"
internal const val SEARCH_EMPTY_TAG = "portfolio-search-empty"
internal const val SEARCH_ALL_TAG = "portfolio-search-all-networks"

private val CONTROL_HEIGHT = 48.dp
private val MORE_MAX_WIDTH = 240.dp
private val SUNLIT_GUTTER = 20.dp
private val LEGACY_GUTTER = 16.dp
