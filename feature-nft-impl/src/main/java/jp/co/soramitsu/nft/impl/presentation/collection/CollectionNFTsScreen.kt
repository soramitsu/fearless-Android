package jp.co.soramitsu.nft.impl.presentation.collection

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.valentinilk.shimmer.shimmer
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.B0
import jp.co.soramitsu.common.compose.component.B1
import jp.co.soramitsu.common.compose.component.B2
import jp.co.soramitsu.common.compose.component.BackgroundCornered
import jp.co.soramitsu.common.compose.component.GradientIcon
import jp.co.soramitsu.common.compose.component.H3
import jp.co.soramitsu.common.compose.component.H5
import jp.co.soramitsu.common.compose.component.H5Bold
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.Shimmer
import jp.co.soramitsu.common.compose.models.Loadable
import jp.co.soramitsu.common.compose.models.LoadableListPage
import jp.co.soramitsu.common.compose.models.Render
import jp.co.soramitsu.common.compose.models.ScreenLayout
import jp.co.soramitsu.common.compose.models.retrievePainter
import jp.co.soramitsu.common.compose.models.retrieveString
import jp.co.soramitsu.common.compose.theme.shimmerColor
import jp.co.soramitsu.common.compose.theme.warningOrange
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.compose.theme.white08
import jp.co.soramitsu.common.compose.theme.white50
import jp.co.soramitsu.common.compose.utils.PageScrollingCallback
import jp.co.soramitsu.common.compose.utils.nestedScrollConnectionForPageScrolling
import jp.co.soramitsu.common.utils.clickableSingle
import jp.co.soramitsu.nft.impl.presentation.collection.models.NFTsScreenView
import jp.co.soramitsu.nft.navigation.NFTNavGraphRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Suppress("FunctionName")
fun NavGraphBuilder.CollectionNFTsNavComposable(
    viewsListFlow: StateFlow<LoadableListPage<NFTsScreenView>>,
    pageScrollingCallback: PageScrollingCallback
) {
    composable(NFTNavGraphRoute.CollectionNFTsScreen.routeName) {
        val viewsList = viewsListFlow.collectAsStateWithLifecycle()

        CollectionNFTsScreen(
            loadablePage = viewsList.value,
            pageScrollingCallback = pageScrollingCallback
        )
    }
}

@Composable
private fun CollectionNFTsScreen(
    loadablePage: LoadableListPage<NFTsScreenView>,
    pageScrollingCallback: PageScrollingCallback
) {
    val lazyGridState = rememberLazyGridState()

    val nestedScrollConnection = remember(lazyGridState) {
        lazyGridState.nestedScrollConnectionForPageScrolling(pageScrollingCallback)
    }

    val mutableViewsList = remember { mutableStateListOf<NFTsScreenView>() }

    LaunchedEffect(loadablePage) {
        when (loadablePage) {
            is LoadableListPage.ReadyToRender -> {
                val shouldSkipEmptyPlaceHolder = !mutableViewsList.isEmpty() &&
                    loadablePage.views.contains(NFTsScreenView.EmptyPlaceHolder)

                if (!shouldSkipEmptyPlaceHolder) {
                    mutableViewsList.clear()
                    mutableViewsList.addAll(loadablePage.views)
                } else if (NFTsScreenView.LoadingIndication in mutableViewsList) {
                    mutableViewsList.remove(NFTsScreenView.LoadingIndication)
                }
            }

            is LoadableListPage.PreviousPageLoading -> {
                if (NFTsScreenView.LoadingIndication in mutableViewsList) {
                    mutableViewsList.remove(NFTsScreenView.LoadingIndication)
                }

                val index = mutableViewsList.indexOfFirst {
                    it is NFTsScreenView.SectionHeader
                }.plus(1)

                mutableViewsList.add(index, NFTsScreenView.LoadingIndication)
            }

            is LoadableListPage.NextPageLoading -> {
                if (NFTsScreenView.LoadingIndication in mutableViewsList) {
                    mutableViewsList.remove(NFTsScreenView.LoadingIndication)
                }

                val index = mutableViewsList.indexOfLast {
                    it is NFTsScreenView.ItemModel
                }.plus(1)

                mutableViewsList.add(index, NFTsScreenView.LoadingIndication)
            }

            is LoadableListPage.Reloading -> {
                mutableViewsList.clear()
                mutableViewsList.addAll(loadablePage.views)
            }
        }
    }

    val savableIndex = rememberSaveable(
        stateSaver = Saver(
            save = { it },
            restore = { it }
        )
    ) {
        mutableIntStateOf(0)
    }

    val firstVisibleItemIndexAsState = remember(lazyGridState) {
        derivedStateOf { lazyGridState.firstVisibleItemIndex }
    }

    LaunchedEffect(firstVisibleItemIndexAsState) {
        snapshotFlow { firstVisibleItemIndexAsState.value }
            .onEach { index -> savableIndex.value = index }
            .flowOn(Dispatchers.Default)
            .launchIn(this)
    }

    LazyVerticalGrid(
        state = lazyGridState,
        columns = GridCells.Fixed(2),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .nestedScroll(nestedScrollConnection)
            .padding(horizontal = 16.dp)
            .padding(
                top = 8.dp,
                bottom = 16.dp
            )
    ) {
        for (view in mutableViewsList) {
            when (view) {
                is NFTsScreenView.ScreenHeader ->
                    NFTScreenHeader(view)

                is NFTsScreenView.SectionHeader ->
                    NFTSectionHeader(view)

                is NFTsScreenView.ItemModel ->
                    NFTItem(view)

                is NFTsScreenView.LoadingIndication ->
                    NFTLoadingIndication(view)

                is NFTsScreenView.EmptyPlaceHolder ->
                    NFTEmptyPlaceholder(view)
            }
        }

        item { MarginVertical(margin = 80.dp) }
    }

    // Be sure to scroll only after items are loaded into mutableViewsList, and from it to LazyGrid
    LaunchedEffect(loadablePage) {
        lazyGridState.scrollToItem(savableIndex.value)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Suppress("FunctionName", "MagicNumber")
private fun LazyGridScope.NFTScreenHeader(screenHeader: NFTsScreenView.ScreenHeader) {
    when (val thumbnail = screenHeader.thumbnail) {
        is Loadable.ReadyToRender -> {
            item(
                span = { GridItemSpan(2) },
                key = screenHeader.key,
                contentType = screenHeader.contentType
            ) {
                Image(
                    modifier = Modifier
                        .animateItemPlacement()
                        .clip(RoundedCornerShape(8.dp))
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    painter = thumbnail.data.retrievePainter(),
                    contentDescription = null,
                    alignment = Alignment.Center,
                    contentScale = ContentScale.FillWidth
                )
            }
        }

        is Loadable.InProgress -> {
            item(
                span = { GridItemSpan(2) },
                contentType = screenHeader.contentType
            ) {
                Box(
                    modifier = Modifier
                        .animateItemPlacement()
                        .clip(RoundedCornerShape(8.dp))
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .shimmer(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(shimmerColor, RoundedCornerShape(11.dp))
                    )
                }
            }
        }
    }

    when (val description = screenHeader.description) {
        is Loadable.ReadyToRender -> {
            description.data?.let { textModel ->
                item(
                    span = { GridItemSpan(2) }
                ) {
                    B1(
                        modifier = Modifier.animateItemPlacement(),
                        text = textModel.retrieveString(),
                        color = white
                    )
                }
            }
        }

        is Loadable.InProgress -> {
            item(
                span = { GridItemSpan(2) }
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.animateItemPlacement()
                ) {
                    Shimmer(
                        modifier = Modifier
                            .fillMaxWidth(.9f)
                            .height(13.dp)
                    )
                    Shimmer(
                        modifier = Modifier
                            .fillMaxWidth(.6f)
                            .height(13.dp)
                    )

                    Shimmer(
                        modifier = Modifier
                            .fillMaxWidth(.3f)
                            .height(13.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Suppress("FunctionName", "MagicNumber")
private fun LazyGridScope.NFTSectionHeader(sectionHeader: NFTsScreenView.SectionHeader) {
    item(
        span = { GridItemSpan(2) },
        key = sectionHeader.key,
        contentType = sectionHeader.contentType
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .animateItemPlacement()
        ) {
            sectionHeader.title.Render(
                shimmerModifier = Modifier
                    .padding(vertical = 16.dp)
                    .fillMaxWidth(.4f)
                    .height(15.dp)
            ) { _, data ->
                H5Bold(
                    modifier = Modifier
                        .padding(vertical = 16.dp)
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    text = data.retrieveString()
                )
            }

            Divider(
                color = white08,
                modifier = Modifier
                    .fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Suppress("FunctionName", "MagicNumber")
private fun LazyGridScope.NFTItem(itemModel: NFTsScreenView.ItemModel) {
    item(
        span = {
            if (itemModel.screenLayout === ScreenLayout.List) {
                GridItemSpan(2)
            } else {
                GridItemSpan(1)
            }
        },
        key = itemModel.key,
        contentType = itemModel.contentType
    ) {
        BackgroundCornered(
            modifier = Modifier
                .clickableSingle(onClick = itemModel.onItemClick)
                .animateItemPlacement()
        ) {
            itemModel.screenLayout.Render(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth()
            ) {
                itemModel.thumbnail.Render(
                    shimmerModifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .size(152.dp),
                    shimmerRadius = 11.dp
                ) { shimmerModifier, data ->
                    Image(
                        modifier = shimmerModifier,
                        painter = data.retrievePainter(),
                        contentDescription = null,
                        alignment = Alignment.Center,
                        contentScale = ContentScale.Crop
                    )
                }

                Column {
                    itemModel.title.Render(
                        shimmerModifier = Modifier
                            .fillMaxWidth(.9f)
                            .height(15.dp)
                    ) { _, data ->
                        H5(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(),
                            text = data.retrieveString(),
                            color = white,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
                    }

                    itemModel.description.Render(
                        shimmerModifier = Modifier
                            .padding(top = 4.dp)
                            .fillMaxWidth(.45f)
                            .height(11.dp)
                    ) { _, data ->
                        with(data) {
                            if (this == null) {
                                return@with
                            }

                            B2(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight(),
                                text = this.retrieveString(),
                                color = white50,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1
                            )
                        }
                    }

                    if (itemModel is NFTsScreenView.ItemModel.WithButtonDecorator) {
                        AccentButton(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .fillMaxWidth(),
                            text = itemModel.buttonText.retrieveString(),
                            iconRes = itemModel.buttonImage.id,
                            onClick = itemModel.onButtonClick
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Suppress("FunctionName")
private fun LazyGridScope.NFTLoadingIndication(loadingIndication: NFTsScreenView.LoadingIndication) {
    item(
        span = {
            GridItemSpan(2)
        },
        contentType = loadingIndication.contentType
    ) {
        Box(
            modifier = Modifier
                .animateItemPlacement()
                .fillMaxWidth()
                .height(60.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = white
            )
        }
    }
}

@Suppress("FunctionName")
private fun LazyGridScope.NFTEmptyPlaceholder(placeholderModel: NFTsScreenView.EmptyPlaceHolder) {
    item(
        span = {
            GridItemSpan(2)
        },
        contentType = placeholderModel.contentType
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GradientIcon(
                    iconRes = placeholderModel.image.id,
                    color = warningOrange
                )

                H3(
                    text = placeholderModel.header.retrieveString()
                )

                B0(
                    text = placeholderModel.body.retrieveString(),
                    color = white50,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
