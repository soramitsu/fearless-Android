package jp.co.soramitsu.common.compose.utils

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.math.max
import kotlin.math.min

@Suppress("NOTHING_TO_INLINE")
inline fun LazyGridState.isFirstItemFullyVisible(): Boolean {
    val itemVisibilityInfo = layoutInfo.visibleItemsInfo.firstOrNull() ?: return false

    val isFirstVisible = itemVisibilityInfo.index == 0
    val isFullyVisible = itemVisibilityInfo.offset.y < 0

    if (isFirstVisible && isFullyVisible) {
        itemVisibilityInfo.index
    }

    return isFirstVisible && isFullyVisible
}

@Suppress("NOTHING_TO_INLINE")
inline fun LazyGridState.isLastItemFullyVisible(): Boolean {
    val itemVisibilityInfo = layoutInfo.visibleItemsInfo.lastOrNull() ?: return false

    val isLastItemVisible =
        itemVisibilityInfo.index == layoutInfo.totalItemsCount.minus(1)

    val itemVisibleHeight = layoutInfo.viewportSize.height - itemVisibilityInfo.offset.y
    val isFullyVisible = itemVisibleHeight >= itemVisibilityInfo.size.height

    return isLastItemVisible && isFullyVisible
}

fun LazyGridState.nestedScrollConnectionForPageScrolling(
    pageScrollingCallback: PageScrollingCallback
): NestedScrollConnection {
    return object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            val isDirectionToPrevPages = max(consumed.y, available.y) > 0
            val isFirstItemFullyVisible = isFirstItemFullyVisible()

            if (isDirectionToPrevPages && isFirstItemFullyVisible) {
                pageScrollingCallback.onAllPrevPagesScrolled()
            }

            val isDirectionToNextPages = min(consumed.y, available.y) < -0
            val isLastItemFullyVisible = isLastItemFullyVisible()

            if (isDirectionToNextPages && isLastItemFullyVisible) {
                pageScrollingCallback.onAllNextPagesScrolled()
            }

            return Offset.Zero
        }
    }
}

@Stable
interface PageScrollingCallback {

    fun onAllPrevPagesScrolled()

    fun onAllNextPagesScrolled()

}