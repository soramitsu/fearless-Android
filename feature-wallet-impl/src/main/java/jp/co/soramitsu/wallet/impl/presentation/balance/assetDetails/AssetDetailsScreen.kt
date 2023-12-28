package jp.co.soramitsu.wallet.impl.presentation.balance.assetDetails

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import jp.co.soramitsu.common.compose.component.AssetBalanceViewState
import jp.co.soramitsu.common.compose.component.BackgroundCornered
import jp.co.soramitsu.common.compose.component.ChainSelector
import jp.co.soramitsu.common.compose.component.ChainSelectorViewState
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.MainToolbarShimmer
import jp.co.soramitsu.common.compose.component.MainToolbarViewState
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.MultiToggleButton
import jp.co.soramitsu.common.compose.component.MultiToggleButtonState
import jp.co.soramitsu.common.compose.component.Shimmer
import jp.co.soramitsu.common.compose.component.ToolbarHomeIcon
import jp.co.soramitsu.common.compose.component.ToolbarHomeIconState
import jp.co.soramitsu.common.compose.component.getImageRequest
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.compose.theme.white16
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.domain.interfaces.AssetSorting
import jp.co.soramitsu.wallet.impl.presentation.balance.detail.AssetBalance

@Composable
fun AssetDetailsToolbar(
    state: LoadingState<MainToolbarViewState>,
    callback: AssetDetailsCallback
) {
    when(state) {
        is LoadingState.Loading -> {
            MainToolbarShimmer(
                homeIconState = ToolbarHomeIconState(navigationIcon = R.drawable.ic_arrow_back_24dp),
            )
        }
        is LoadingState.Loaded -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .wrapContentSize()
                        .align(Alignment.CenterStart)
                ) {
                    ToolbarHomeIcon(
                        state = ToolbarHomeIconState(navigationIcon = state.data.homeIconState.navigationIcon),
                        onClick = callback::onNavigationBack
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = state.data.title,
                        style = MaterialTheme.customTypography.header4,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    MarginVertical(margin = 4.dp)

                    ChainSelector(
                        selectorViewState = state.data.selectorViewState,
                        onChangeChainClick = null
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun AssetDetailsToolbarPreview() {
    FearlessAppTheme {
        AssetDetailsToolbar(
            state = LoadingState.Loaded(
                MainToolbarViewState(
                    title = "MyWallet",
                    homeIconState = ToolbarHomeIconState(
                        navigationIcon = R.drawable.ic_arrow_back_24dp
                    ),
                    selectorViewState = ChainSelectorViewState(
                        selectedChainName = "Temp",
                        selectedChainId = "123"
                    )
                )
            ),
            callback = emptyAssetDetailsCallback()
        )
    }
}

@Composable
fun AssetDetailsContent(
    state: AssetDetailsState,
    callback: AssetDetailsCallback
) {
    val items = remember(state.items.size, state.tabState?.currentSelection, state.assetSorting) {
        SnapshotStateList<AssetDetailsState.ItemState>().apply {
            addAll(state.items)
        }
    }

    Column(
        modifier = Modifier
            .nestedScroll(rememberNestedScrollInteropConnection())
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AssetBalance(
            balanceLoadingState = state.balanceState,
            onAddressClick = remember { { /* DO NOTHING */ } }
        )

        state.tabState?.let { tabState ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    MultiToggleButton(
                        state = tabState,
                        onToggleChange = callback::onChainTabClick
                    )
                }

                IconButton(
                    onClick = callback::onSortChainsClick,
                    modifier = Modifier
                        .size(40.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_sort),
                        tint = white,
                        contentDescription = null
                    )
                }
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(
                items = items,
                key = { it.chainId }
            ) {
                AssetDetailsItem(
                    itemState = it,
                    callback = callback
                )
            }
        }
    }
}

@Preview
@Composable
private fun AssetDetailsContentPreview() {
    FearlessAppTheme {
        AssetDetailsContent(
            state = AssetDetailsViewState(
                assetSorting = AssetSorting.FiatBalance,
                balanceState = LoadingState.Loaded(
                    AssetBalanceViewState(
                        transferableBalance = "123",
                        changeViewState = ChangeBalanceViewState(
                            "123%",
                            "75"
                        ),
                        address = ""
                    )
                ),
                tabState = MultiToggleButtonState(
                    currentSelection = AssetDetailsState.Tab.AvailableChains,
                    toggleStates = AssetDetailsState.Tab.values().toList()
                ),
                items = listOf(
                    AssetDetailsItemViewState(
                        assetId = "1",
                        chainId = "0",
                        iconUrl = "",
                        chainName = "Sora TestNet",
                        assetRepresentation = "1630.62 XOR",
                        fiatRepresentation = "$3,342.77"
                    ),
                    AssetDetailsItemViewState(
                        assetId = "1",
                        chainId = "1",
                        iconUrl = "",
                        chainName = "Sora TestNet",
                        assetRepresentation = "1630.62 XOR",
                        fiatRepresentation = "$3,342.77"
                    ),
                    AssetDetailsItemViewState(
                        assetId = "1",
                        chainId = "2",
                        iconUrl = "",
                        chainName = "Sora TestNet",
                        assetRepresentation = "1630.62 XOR",
                        fiatRepresentation = "$3,342.77"
                    ),
                    AssetDetailsItemViewState(
                        assetId = "1",
                        chainId = "3",
                        iconUrl = "",
                        chainName = "Sora TestNet",
                        assetRepresentation = "1630.62 XOR",
                        fiatRepresentation = "$3,342.77"
                    )
                )
            ),
            callback = emptyAssetDetailsCallback()
        )
    }
}

@Composable
fun AssetDetailsItem(
    itemState: AssetDetailsState.ItemState,
    callback: AssetDetailsCallback
) {
    BackgroundCornered(
        modifier = Modifier.clickable(onClick = { callback.onChainClick(itemState) })
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .height(IntrinsicSize.Max)
                .padding(8.dp)
                .fillMaxWidth(),
        ) {
            AsyncImage(
                model = getImageRequest(LocalContext.current, itemState.iconUrl),
                alignment = Alignment.Center,
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )

            Divider(
                color = white16,
                modifier = Modifier.size(width = 1.dp, height = 64.dp)
            )

            if (itemState.chainName == null) {
                Shimmer(
                    Modifier
                        .padding(top = 8.dp, bottom = 4.dp)
                        .size(height = 16.dp, width = 54.dp)
                )
            } else {
                Text(
                    text = itemState.chainName.orEmpty(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.customTypography.header3
                        .copy(textAlign = TextAlign.End),
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (itemState.assetRepresentation == null) {
                    Shimmer(
                        modifier = Modifier
                            .padding(top = 8.dp, bottom = 4.dp)
                            .size(height = 16.dp, width = 54.dp)
                    )
                } else {
                    Text(
                        text = itemState.assetRepresentation.orEmpty(),
                        maxLines = 1,
                        style = MaterialTheme.customTypography.header3
                            .copy(textAlign = TextAlign.End),
                    )
                    Text(
                        text = itemState.fiatRepresentation.orEmpty(),
                        maxLines = 1,
                        style = MaterialTheme.customTypography.body1,
                        modifier = Modifier.alpha(0.64f)
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun AssetDetailsItemPreview() {
    FearlessAppTheme(
        darkTheme = false
    ) {
        AssetDetailsItem(
            itemState = AssetDetailsItemViewState(
                assetId = null,
                chainId = "0",
                iconUrl = "",
                chainName = "Sora TestNet",
                assetRepresentation = "1630.62 XOR",
                fiatRepresentation = "$3,342.77"
            ),
            callback = emptyAssetDetailsCallback()
        )
    }
}

private fun emptyAssetDetailsCallback(): AssetDetailsCallback {
    return object : AssetDetailsCallback {
        override fun onNavigationBack() {
            /* DO NOTHING */
        }

        override fun onSelectChainClick() {
            /* DO NOTHING */
        }

        override fun onChainTabClick(tab: AssetDetailsState.Tab) {
            /* DO NOTHING */
        }

        override fun onSortChainsClick() {
            /* DO NOTHING */
        }

        override fun onChainClick(itemState: AssetDetailsState.ItemState) {
            /* DO NOTHING */
        }
    }
}