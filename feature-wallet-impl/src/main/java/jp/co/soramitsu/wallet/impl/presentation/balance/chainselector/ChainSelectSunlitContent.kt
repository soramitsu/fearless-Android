package jp.co.soramitsu.wallet.impl.presentation.balance.chainselector

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import jp.co.soramitsu.common.compose.component.ChainSelectorViewStateWithFilters
import jp.co.soramitsu.common.compose.component.CorneredInput
import jp.co.soramitsu.common.compose.component.FearlessProgress
import jp.co.soramitsu.common.compose.component.NavigationIconButton
import jp.co.soramitsu.common.compose.component.getImageRequest
import jp.co.soramitsu.common.compose.theme.fw.FwCheckMark
import jp.co.soramitsu.common.compose.theme.fw.FwEmptyState
import jp.co.soramitsu.common.compose.theme.fw.FwFilterChip
import jp.co.soramitsu.common.compose.theme.fw.FwIconWell
import jp.co.soramitsu.common.compose.theme.fw.FwListRow
import jp.co.soramitsu.common.compose.theme.fw.FwTextRole
import jp.co.soramitsu.common.compose.theme.fw.LocalFwColors
import jp.co.soramitsu.common.compose.theme.fw.fwPressScale
import jp.co.soramitsu.common.compose.theme.fw.fwTextStyle
import jp.co.soramitsu.common.utils.castOrNull
import jp.co.soramitsu.feature_wallet_impl.R

private val GUTTER = 20.dp

/**
 * The network picker in the sun-lit design: raised rows, each network's icon in a well, and the chosen
 * network marked by its rim and a check. The list keeps the page gutter as content padding, so the
 * rows' light is not cut at the list's edges. Same state, callbacks and test tags as the legacy page.
 */
@Composable
internal fun ChainSelectSunlitContent(
    state: ChainSelectScreenContract.State,
    contract: ChainSelectScreenContract
) {
    val c = LocalFwColors.current
    Column(Modifier.nestedScroll(rememberNestedScrollInteropConnection()).fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = GUTTER)) {
            NavigationIconButton(onNavigationClick = contract::onBackButtonClick, modifier = Modifier.align(Alignment.CenterStart))
            Text(
                stringResource(R.string.common_network_management),
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 56.dp),
                style = fwTextStyle(FwTextRole.TitleM),
                color = c.textPrimary
            )
        }
        Spacer(Modifier.height(16.dp))
        CorneredInput(
            modifier = Modifier.padding(horizontal = GUTTER),
            state = state.searchQuery,
            hintLabel = stringResource(R.string.common_search),
            onInput = contract::onSearchInput
        )
        state.onlyNetworkName?.let {
            Text(
                stringResource(R.string.portfolio_only_network, it),
                modifier = Modifier.padding(start = GUTTER, end = GUTTER, top = 8.dp),
                style = fwTextStyle(FwTextRole.BodyS),
                color = c.textSecondary
            )
        }
        if (state is ChainSelectScreenContract.State.Impl.FilteringDecorator) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = GUTTER, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ChainSelectorViewStateWithFilters.Filter.entries.forEach { filter ->
                    FwFilterChip(
                        label = stringResource(filterTitle(filter)),
                        selected = state.selectedFilter === filter,
                        onClick = { contract.onFilterApplied(filter) },
                        modifier = Modifier.testTag("ChainFilter_${filter.name}")
                    )
                }
            }
        } else {
            Spacer(Modifier.height(12.dp))
        }
        val chains = state.chains
        when {
            chains == null -> Box(Modifier.fillMaxSize()) { FearlessProgress(Modifier.align(Alignment.Center)) }
            chains.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                FwEmptyState(
                    title = stringResource(jp.co.soramitsu.common.R.string.common_search_assets_alert_title),
                    description = stringResource(jp.co.soramitsu.common.R.string.common_search_network_and_assets_alert_description),
                    iconRes = jp.co.soramitsu.common.R.drawable.ic_alert_24
                )
            }
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = GUTTER, end = GUTTER, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.showAllChains) {
                    val decorated = state.castOrNull<ChainSelectScreenContract.State.Impl.FilteringDecorator>()
                    val applied = decorated?.appliedFilter ?: ChainSelectorViewStateWithFilters.Filter.All
                    val selected = decorated?.selectedFilter ?: ChainSelectorViewStateWithFilters.Filter.All
                    item {
                        SunlitAllRow(
                            selectedFilter = selected,
                            isSelected = state.selectedChainId == null && applied === selected,
                            onSelected = { contract.onChainSelected(null) }
                        )
                    }
                }
                items(chains.map { it.markSelected(isSelected = it.id == state.selectedChainId) }) { chain ->
                    SunlitChainRow(chain, contract)
                }
            }
        }
    }
}

private fun filterTitle(filter: ChainSelectorViewStateWithFilters.Filter): Int = when (filter) {
    ChainSelectorViewStateWithFilters.Filter.All -> R.string.network_management_all
    ChainSelectorViewStateWithFilters.Filter.Popular -> R.string.network_management_popular
    ChainSelectorViewStateWithFilters.Filter.Favorite -> R.string.network_managment_favourite
}

@Composable
private fun SunlitAllRow(
    selectedFilter: ChainSelectorViewStateWithFilters.Filter,
    isSelected: Boolean,
    onSelected: () -> Unit
) {
    val c = LocalFwColors.current
    val icon = when (selectedFilter) {
        ChainSelectorViewStateWithFilters.Filter.All -> R.drawable.ic_all_chains
        ChainSelectorViewStateWithFilters.Filter.Popular -> R.drawable.ic_popular_chains
        ChainSelectorViewStateWithFilters.Filter.Favorite -> R.drawable.ic_favorite_enabled
    }
    val title = when (selectedFilter) {
        ChainSelectorViewStateWithFilters.Filter.All -> R.string.chain_selection_all_networks
        ChainSelectorViewStateWithFilters.Filter.Popular -> R.string.network_management_popular
        ChainSelectorViewStateWithFilters.Filter.Favorite -> R.string.network_managment_favourite
    }
    FwListRow(selected = isSelected, onClick = onSelected) {
        FwIconWell(size = 36.dp) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = c.textPrimary,
                modifier = Modifier.testTag("ChainItem_image_all_networks").size(20.dp)
            )
        }
        Text(stringResource(title), Modifier.weight(1f), style = fwTextStyle(FwTextRole.LabelL), color = c.textPrimary)
        if (isSelected) FwCheckMark(Modifier.testTag("ChainItem_image_selected"))
    }
}

@Composable
private fun SunlitChainRow(state: ChainSelectScreenContract.State.ItemState, contract: ChainSelectScreenContract) {
    val c = LocalFwColors.current
    FwListRow(
        selected = state.isSelected,
        onClick = { contract.onChainSelected(state) },
        contentPadding = PaddingValues(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)
    ) {
        FwIconWell(size = 36.dp) {
            AsyncImage(
                model = state.imageUrl?.let { getImageRequest(LocalContext.current, it) },
                contentDescription = null,
                modifier = Modifier.testTag("ChainItem_image_${state.id}").size(22.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(state.title, style = fwTextStyle(FwTextRole.LabelL), color = c.textPrimary)
            state.balance?.let { Text(it, style = fwTextStyle(FwTextRole.NumS), color = c.textSecondary) }
        }
        if (state.isSelected) FwCheckMark(Modifier.testTag("ChainItem_image_selected"))
        if (state is ChainSelectScreenContract.State.ItemState.Impl.FilteringDecorator) {
            FavoriteStar(marked = state.isMarkedAsFavorite, onClick = { contract.onChainMarkedFavorite(state) })
        }
    }
}

/** The star keeps its own 48 dp target; a marked network is filled in brand light, an unmarked one stays quiet. */
@Composable
private fun FavoriteStar(marked: Boolean, onClick: () -> Unit) {
    val c = LocalFwColors.current
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val label = stringResource(R.string.network_managment_favourite)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .toggleable(value = marked, interactionSource = interactions, indication = null, role = Role.Checkbox, onValueChange = { onClick() })
            .testTag("ChainItem_marked_favourite"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(R.drawable.ic_favorite_filled),
            contentDescription = label,
            tint = if (marked) c.textBrand else c.textTertiary,
            modifier = Modifier.size(18.dp).fwPressScale(pressed).alpha(if (marked) 1f else 0.6f)
        )
    }
}
