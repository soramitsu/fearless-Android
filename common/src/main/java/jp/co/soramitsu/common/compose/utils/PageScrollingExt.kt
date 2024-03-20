package jp.co.soramitsu.common.compose.utils

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlin.math.abs
import kotlin.math.max

fun nestedScrollConnectionForPageScrolling(
    pageScrollingCallback: PageScrollingCallback
): NestedScrollConnection {
    return object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            val isScrollThresholdBreached = isScrollThresholdBreached(abs(consumed.y), abs(available.y))
            val isScrollDirectionDown = max(consumed.y, available.y) > 0f

            if (isScrollDirectionDown && isScrollThresholdBreached) {
                pageScrollingCallback.onAllPrevPagesScrolled()
            }

            if (!isScrollDirectionDown && isScrollThresholdBreached) {
                pageScrollingCallback.onAllNextPagesScrolled()
            }

            return Offset.Zero
        }

        private fun isScrollThresholdBreached(consumed: Float, available: Float): Boolean {
            return if (consumed > 0) {
                (available / (available + consumed)) in (0.15..0.85)
            } else {
                available > 15
            }
        }
    }
}

@Stable
interface PageScrollingCallback {

    fun onAllPrevPagesScrolled()

    fun onAllNextPagesScrolled()

}