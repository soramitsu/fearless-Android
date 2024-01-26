package jp.co.soramitsu.nft.impl.presentation.collection

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.B1
import jp.co.soramitsu.common.compose.component.BackgroundCornered
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.CapsTitle
import jp.co.soramitsu.common.compose.component.H5Bold
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.MenuIconItem
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.component.getImageRequest
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.white08
import jp.co.soramitsu.common.compose.theme.white50
import jp.co.soramitsu.common.utils.clickableSingle

data class NftCollectionScreenState(
    val collectionName: String,
    val collectionImageUrl: String,
    val collectionDescription: String?,
    val myNFTs: List<NftItem>,
    val availableNFTs: List<NftItem>
)

data class NftItem(
    val thumbnailUrl: String,
    val name: String,
    val description: String?,
    val id: Int
)

private val previewState = NftCollectionScreenState(
    collectionName = "Birds collection",
    collectionImageUrl = "",
    collectionDescription = "Inspired by coin pusher arcade games, CryptoDozer is a coin & doll collecting game - powered by the blockchain. Line up your coins to push crypto dolls into your collection. Use special in-game items, such as Walls, Fever Time and the powerful Bull Dozer to become a CryptoDozer master.",
    myNFTs = listOf(
        NftItem("", "Bird1", "#4 in editions of 100", 1),
        NftItem("", "Bird2", "#5 in editions of 100", 2),
        NftItem("", "Bird3", "#6 in editions of 100", 3)
    ),
    availableNFTs = listOf(
        NftItem("", "Bird4", "#7 in editions of 100", 4),
        NftItem("", "Bird5", "#8 in editions of 100", 5)
    )
)

interface NftCollectionScreenInterface {
    fun close()
    fun onItemClick(item: NftItem)
    fun onSendClick(item: NftItem)
    fun onShareClick(item: NftItem)
    fun onLoadPreviousPage()
    fun onLoadNextPage()
}

@Composable
fun NFTCollectionScreen(viewModel: NftCollectionViewModel) {
    val state: NftCollectionScreenState by viewModel.state.collectAsStateWithLifecycle(previewState)
    NftCollectionScreen(state = state, screenInterface = viewModel)
}

@Suppress("NOTHING_TO_INLINE")
inline fun LazyGridState.isFirstItemFullyVisible(): Boolean {
    val itemVisibilityInfo = layoutInfo.visibleItemsInfo.firstOrNull() ?: return false

    val isFirstVisible = itemVisibilityInfo.index == 0
    val isFullyVisible = itemVisibilityInfo.offset.y >= 0

    return isFirstVisible && isFullyVisible
}

@Suppress("NOTHING_TO_INLINE")
inline fun LazyGridState.isLastItemFullyVisible(): Boolean {
    val itemVisibilityInfo = layoutInfo.visibleItemsInfo.lastOrNull() ?: return false

    val isLastItemVisible =
        itemVisibilityInfo.index == layoutInfo.totalItemsCount.minus(1)

    val itemVisibleHeight = layoutInfo.viewportSize.height - itemVisibilityInfo.offset.y

    val isFullyVisible = itemVisibleHeight == itemVisibilityInfo.size.height

    return isLastItemVisible && isFullyVisible
}

@Composable
fun NftCollectionScreen(
    state: NftCollectionScreenState,
    screenInterface: NftCollectionScreenInterface
) {
    val lazyGridState = rememberLazyGridState()

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (lazyGridState.isFirstItemFullyVisible()) {
                    screenInterface.onLoadPreviousPage()
                }

                if (lazyGridState.isLastItemFullyVisible()) {
                    screenInterface.onLoadNextPage()
                }

                return Offset.Zero
            }
        }
    }

    BottomSheetScreen {
        Toolbar(
            state = ToolbarViewState(
                state.collectionName,
                null,
                MenuIconItem(icon = R.drawable.ic_cross_24, screenInterface::close)
            )
        )
        LazyVerticalGrid(
            state = lazyGridState,
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(16.dp)
                .nestedScroll(nestedScrollConnection)
        ) {
            this.item(span = { GridItemSpan(2) }) {
                Column {
                    AsyncImage(
                        model = getImageRequest(LocalContext.current, state.collectionImageUrl),
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .fillMaxWidth()
                            .wrapContentHeight()
                    )
                    MarginVertical(margin = 12.dp)
                    state.collectionDescription?.let { B1(text = it) }
                    MarginVertical(margin = 16.dp)
                }
            }
            nftGridHeader(text = { stringResource(id = R.string.nft_collection_my_nfts) })
            items(state.myNFTs) {
                NftItem(it, screenInterface::onItemClick) {
                    NftItemActionButton(
                        stringResource(id = R.string.common_action_send),
                        R.drawable.ic_send_outlined
                    ) { screenInterface.onSendClick(it) }
                }
            }
            if (state.availableNFTs.isNotEmpty()) {
                nftGridHeader(text = {
                    stringResource(
                        id = R.string.nft_collection_available_nfts,
                        state.collectionName
                    )
                })
            }
            items(state.availableNFTs) {
                NftItem(it, screenInterface::onItemClick) {
                    NftItemActionButton(
                        stringResource(id = R.string.common_share),
                        R.drawable.ic_share_arrow_white_24
                    ) { screenInterface.onShareClick(it) }
                }
            }
            item { MarginVertical(margin = 80.dp) }
        }
    }
}

private fun LazyGridScope.nftGridHeader(text: @Composable () -> String) {
    item(span = { GridItemSpan(2) }) {
        Column(modifier = Modifier.padding(horizontal = 12.dp)) {
            H5Bold(text = text())
            MarginVertical(margin = 12.dp)
            Divider(
                color = white08,
                modifier = Modifier
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
private fun NftItemActionButton(text: String, @DrawableRes iconRes: Int, onClick: () -> Unit) {
    AccentButton(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        iconRes = iconRes,
        onClick = onClick
    )
}

@Composable
private fun NftItem(
    item: NftItem,
    onClick: (NftItem) -> Unit,
    actionButton: @Composable () -> Unit
) {
    BackgroundCornered(modifier = Modifier.clickableSingle { onClick(item) }) {
        Column(modifier = Modifier.padding(8.dp)) {
            AsyncImage(
                model = getImageRequest(LocalContext.current, item.thumbnailUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .size(152.dp)
            )
            MarginVertical(margin = 6.dp)
            CapsTitle(text = item.name, color = white50, modifier = Modifier.width(152.dp))
            item.description?.let {
                H5Bold(
                    text = it,
                    modifier = Modifier.width(152.dp),
                    maxLines = 2
                )
            }
            actionButton()
        }
    }
}

@Preview
@Composable
fun NftCollectionScreenPreview() {
    FearlessAppTheme {
        NftCollectionScreen(previewState, object : NftCollectionScreenInterface {
            override fun close() = Unit
            override fun onItemClick(item: NftItem) = Unit
            override fun onSendClick(item: NftItem) = Unit
            override fun onShareClick(item: NftItem) = Unit
            override fun onLoadPreviousPage() = Unit
            override fun onLoadNextPage() = Unit
        })
    }
}