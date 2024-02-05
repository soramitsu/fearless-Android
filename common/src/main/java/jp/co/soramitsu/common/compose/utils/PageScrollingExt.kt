package jp.co.soramitsu.common.compose.utils

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
@Suppress("NOTHING_TO_INLINE")
inline fun LazyGridState.isFirstItemFullyVisibleState(
    buffer: Int = 0
): State<Boolean> {
    return remember {
        derivedStateOf {
            val itemVisibilityInfo = layoutInfo.visibleItemsInfo.firstOrNull()
                ?: return@derivedStateOf false

            val isFirstVisible = itemVisibilityInfo.index == buffer
            val isFullyVisible = itemVisibilityInfo.offset.y < 0

            return@derivedStateOf isFirstVisible && isFullyVisible
        }
    }
}

@Composable
@Suppress("NOTHING_TO_INLINE")
inline fun LazyGridState.isLastItemFullyVisibleState(
    buffer: Int = 0
): State<Boolean> {
    return remember {
        derivedStateOf {
            val itemVisibilityInfo = layoutInfo.visibleItemsInfo.lastOrNull()
                ?: return@derivedStateOf false

            val isLastItemVisible =
                itemVisibilityInfo.index == layoutInfo.totalItemsCount.minus(buffer)

            val itemVisibleHeight = layoutInfo.viewportSize.height - itemVisibilityInfo.offset.y
            val isFullyVisible = itemVisibleHeight <= itemVisibilityInfo.size.height

            return@derivedStateOf isLastItemVisible && isFullyVisible
        }
    }
}

@Stable
interface PageScrollingCallback {

    fun onAllPrevPagesScrolled()

    fun onAllNextPagesScrolled()

}

@Composable
@Suppress("NOTHING_TO_INLINE")
inline fun LazyGridState.SetupScrollingPaginator(
    bufferFromTop: Int = 0,
    bufferFromBottom: Int = 0,
    pageScrollingCallback: PageScrollingCallback
) {
    val isFirstItemFullyVisible = isFirstItemFullyVisibleState(bufferFromTop)
    val isLastItemFullyVisible = isLastItemFullyVisibleState(bufferFromBottom)

    LaunchedEffect(Unit) {
        snapshotFlow { isFirstItemFullyVisible.value }
            .onEach { pageScrollingCallback.onAllPrevPagesScrolled() }
            .launchIn(this)

        snapshotFlow { isLastItemFullyVisible.value }
            .onEach { pageScrollingCallback.onAllNextPagesScrolled() }
            .launchIn(this)
    }
}