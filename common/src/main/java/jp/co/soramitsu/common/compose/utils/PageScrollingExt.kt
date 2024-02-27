package jp.co.soramitsu.common.compose.utils

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlin.math.max
import kotlin.math.min

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
            val isDirectionToPrevPages = max(consumed.y, available.y) > 0.5

            if (isDirectionToPrevPages && isFirstItemFullyVisible()) {
                pageScrollingCallback.onAllPrevPagesScrolled()
            }

            val isDirectionToNextPages = min(consumed.y, available.y) < -0.5

            if (isDirectionToNextPages && isLastItemFullyVisible()) {
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