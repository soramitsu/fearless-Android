package jp.co.soramitsu.wallet.impl.presentation.balance.chainselector

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import jp.co.soramitsu.common.compose.component.B0
import jp.co.soramitsu.common.compose.component.BackgroundCorneredWithBorder
import jp.co.soramitsu.common.compose.component.CapsTitle
import jp.co.soramitsu.common.compose.component.ChainSelectorViewStateWithFilters
import jp.co.soramitsu.common.compose.component.CorneredInput
import jp.co.soramitsu.common.compose.component.GradientIcon
import jp.co.soramitsu.common.compose.component.H3
import jp.co.soramitsu.common.compose.component.H4
import jp.co.soramitsu.common.compose.component.Image
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.NavigationIconButton
import jp.co.soramitsu.common.compose.component.getImageRequest
import jp.co.soramitsu.common.compose.theme.alertYellow
import jp.co.soramitsu.common.compose.theme.black05
import jp.co.soramitsu.common.compose.theme.black4
import jp.co.soramitsu.common.compose.theme.customColors
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.compose.theme.white50
import jp.co.soramitsu.common.compose.theme.white64
import jp.co.soramitsu.common.utils.castOrNull
import jp.co.soramitsu.common.utils.clickableWithNoIndication
import jp.co.soramitsu.feature_wallet_impl.R

@Composable
fun ChainSelectContent(
    state: ChainSelectScreenContract.State,
    contract: ChainSelectScreenContract
) {
    Column(
        modifier = Modifier
            .nestedScroll(rememberNestedScrollInteropConnection())
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .imePadding()
    ) {
        ChainSelectToolbar(onBackButtonClick = contract::onBackButtonClick)

        MarginVertical(margin = 16.dp)

        CorneredInput(
            state = state.searchQuery,
            hintLabel = stringResource(id = R.string.common_search),
            onInput = contract::onSearchInput
        )

        val chains = state.chains

        LazyColumn(modifier = Modifier.weight(1f)) {
            if (state is ChainSelectScreenContract.State.Impl.FilteringDecorator) {
                item {
                    ChainsFilter(
                        selectedFilter = state.selectedFilter,
                        onClick = contract::onFilterApplied
                    )
                }
            }

            if (state.showAllChains && chains?.isNotEmpty() == true) {
                if (state.showAllChains) {
                    val appliedFilter =
                        state.castOrNull<ChainSelectScreenContract.State.Impl.FilteringDecorator>()
                            ?.appliedFilter ?: ChainSelectorViewStateWithFilters.Filter.All

                    val selectedFilter =
                        state.castOrNull<ChainSelectScreenContract.State.Impl.FilteringDecorator>()
                            ?.selectedFilter ?: ChainSelectorViewStateWithFilters.Filter.All

                    item {
                        ChainAllItem(
                            appliedFilter = appliedFilter,
                            selectedFilter = selectedFilter,
                            isSelected = state.selectedChainId == null,
                            onSelected = contract::onChainSelected
                        )
                    }
                }

                items(chains!!.map { it.markSelected(isSelected = it.id == state.selectedChainId) }) { chain ->
                    ChainItem(
                        state = chain,
                        contract = contract
                    )
                }
            } else {
                item {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .align(CenterHorizontally)
                    ) {
                        EmptyResultContent()
                    }
                }
            }
        }

        MarginVertical(margin = 16.dp)
    }
}

@Composable
fun ChainSelectToolbar(
    onBackButtonClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        NavigationIconButton(
            onNavigationClick = onBackButtonClick,
            modifier = Modifier.align(Alignment.CenterStart)
        )
        
        H3(
            text = stringResource(id = R.string.common_network_management),
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
fun EmptyResultContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        GradientIcon(
            iconRes = jp.co.soramitsu.common.R.drawable.ic_alert_24,
            color = alertYellow,
            modifier = Modifier.align(CenterHorizontally),
            contentPadding = PaddingValues(bottom = 4.dp)
        )

        H3(text = stringResource(id = jp.co.soramitsu.common.R.string.common_search_assets_alert_title))
        B0(
            text = stringResource(id = jp.co.soramitsu.common.R.string.common_search_assets_alert_description),
            color = white50
        )
    }
}

@Composable
inline fun ChainsFilter(
    selectedFilter: ChainSelectorViewStateWithFilters.Filter,
    crossinline onClick: (ChainSelectorViewStateWithFilters.Filter) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.height(56.dp)
    ) {
        for (filter in ChainSelectorViewStateWithFilters.Filter.values()) {
            val filterBorderColor = if (selectedFilter === filter)
                MaterialTheme.customColors.colorAccent else
                black05

            BackgroundCorneredWithBorder(
                borderColor = filterBorderColor,
                modifier = Modifier
                    .testTag("ChainFilter_${filter.name}")
                    .wrapContentSize()
                    .clickableWithNoIndication { onClick(filter) }
            ) {
                val filterName = when(filter) {
                    ChainSelectorViewStateWithFilters.Filter.All ->
                        stringResource(id = R.string.network_management_all)

                    ChainSelectorViewStateWithFilters.Filter.Popular ->
                        stringResource(id = R.string.network_management_popular)

                    ChainSelectorViewStateWithFilters.Filter.Favorite ->
                        stringResource(id = R.string.network_managment_favourite)
                }

                CapsTitle(
                    text = filterName,
                    color = white64,
                    modifier = Modifier
                        .padding(8.dp),
                )
            }
        }
    }
}

@Composable
fun ChainAllItem(
    appliedFilter: ChainSelectorViewStateWithFilters.Filter,
    selectedFilter: ChainSelectorViewStateWithFilters.Filter,
    isSelected: Boolean,
    onSelected: (ChainSelectScreenContract.State.ItemState?) -> Unit
) {
    val imageRes = when(selectedFilter) {
        ChainSelectorViewStateWithFilters.Filter.All -> R.drawable.ic_all_chains
        ChainSelectorViewStateWithFilters.Filter.Popular -> R.drawable.ic_popular_chains
        ChainSelectorViewStateWithFilters.Filter.Favorite -> R.drawable.ic_favorite_enabled
    }

    val titleRes = when(selectedFilter) {
        ChainSelectorViewStateWithFilters.Filter.All -> stringResource(id = R.string.chain_selection_all_networks)
        ChainSelectorViewStateWithFilters.Filter.Popular -> stringResource(id = R.string.network_management_popular)
        ChainSelectorViewStateWithFilters.Filter.Favorite -> stringResource(id = R.string.network_managment_favourite)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .height(48.dp)
            .fillMaxWidth()
            .clickableWithNoIndication { onSelected(null) }
    ) {

        Image(
            res = imageRes,
            tint = white,
            modifier = Modifier
                .testTag("ChainItem_image_all_networks")
                .size(24.dp)
        )

        H4(
            text = titleRes,
            modifier = Modifier.weight(1f)
        )

        val isChainMarkedAsSelected = isSelected &&
                appliedFilter === selectedFilter

        if (isChainMarkedAsSelected) {
            Image(
                res = R.drawable.ic_marked,
                modifier = Modifier
                    .testTag("ChainItem_image_selected")
                    .height(14.dp)
                    .wrapContentWidth()
            )
        }

        val navHintIconTint = MaterialTheme.customColors.white

        val navHintIconAlpha = .16f

        Image(
            res = R.drawable.ic_arrow_right_24,
            tint = navHintIconTint,
            modifier = Modifier
                .size(24.dp)
                .alpha(navHintIconAlpha)
        )
    }
}

@Composable
fun ChainItem(
    state: ChainSelectScreenContract.State.ItemState,
    contract: ChainSelectScreenContract
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .height(48.dp)
            .fillMaxWidth()
            .clickableWithNoIndication { contract.onChainSelected(state) }
    ) {
        AsyncImage(
            model = state.imageUrl?.let { getImageRequest(LocalContext.current, it) },
            contentDescription = null,
            modifier = Modifier
                .testTag("ChainItem_image_${state.id}")
                .size(24.dp)
        )

        H4(
            text = state.title,
            modifier = Modifier.weight(1f)
        )

        if (state.isSelected) {
            val selectedChainIconTint =
                MaterialTheme.customColors.colorAccent

            Image(
                res = R.drawable.ic_marked,
                tint = selectedChainIconTint,
                modifier = Modifier
                    .testTag("ChainItem_image_selected")
                    .height(14.dp)
                    .wrapContentWidth()
            )
        }

        if (state is ChainSelectScreenContract.State.ItemState.Impl.FilteringDecorator) {
            val favoriteChainIconTint =
                if (state.isMarkedAsFavorite)
                    MaterialTheme.customColors.colorAccent else
                    MaterialTheme.customColors.white

            val favoriteChainIconAlpha =
                if (state.isMarkedAsFavorite)
                    1f else .16f

            Image(
                res = R.drawable.ic_favorite_filled,
                tint = favoriteChainIconTint,
                modifier = Modifier
                    .testTag("ChainItem_marked_favourite")
                    .height(14.dp)
                    .wrapContentWidth()
                    .alpha(favoriteChainIconAlpha)
                    .clickableWithNoIndication { contract.onChainMarkedFavorite(state) }
            )
        }

        val navHintIconTint = MaterialTheme.customColors.white

        val navHintIconAlpha = .16f

        Image(
            res = R.drawable.ic_arrow_right_24,
            tint = navHintIconTint,
            modifier = Modifier
                .size(24.dp)
                .alpha(navHintIconAlpha)
        )
    }
}

@Preview
@Composable
private fun SelectChainScreenPreview() {
    val items = listOf(
        ChainSelectScreenContract.State.ItemState.Impl(
            id = "1",
            imageUrl = "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Moonriver.svg",
            title = "Kusama"
        ).toFilteredDecorator(false),
        ChainSelectScreenContract.State.ItemState.Impl(
            id = "2",
            imageUrl = "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Kusama.svg",
            title = "Moonriver"
        ).toFilteredDecorator(true)
    )
    val state = ChainSelectScreenContract.State.Impl.FilteringDecorator(
        ChainSelectorViewStateWithFilters.Filter.Favorite,
        ChainSelectorViewStateWithFilters.Filter.Favorite,
        ChainSelectScreenContract.State.Impl(
            chains = items,
            selectedChainId = "1",
            searchQuery = null
        )
    )
    Column(
        Modifier.background(black4)
    ) {
        ChainSelectContent(
            state = state,
            contract = object : ChainSelectScreenContract {
                override fun onBackButtonClick() {}

                override fun onFilterApplied(filter: ChainSelectorViewStateWithFilters.Filter) {}

                override fun onChainMarkedFavorite(chainItemState: ChainSelectScreenContract.State.ItemState) {}

                override fun onChainSelected(chainItemState: ChainSelectScreenContract.State.ItemState?) {}

                override fun onSearchInput(input: String) {}

                override fun onDialogClose() {}
            }
        )
    }
}
