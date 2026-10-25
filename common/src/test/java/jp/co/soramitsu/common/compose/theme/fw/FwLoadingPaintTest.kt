package jp.co.soramitsu.common.compose.theme.fw

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.ui.graphics.BlendMode
import org.junit.Assert.assertEquals
import org.junit.Test

class FwLoadingPaintTest {
    @Test fun sharedSkeletonUsesLinearSixteenHundredMillisecondCycleWithoutDelay() {
        val repeat = FwLoadingShimmerTheme.animationSpec as InfiniteRepeatableSpec<Float>
        val animation = repeat.animation as TweenSpec<Float>
        assertEquals(1600, animation.durationMillis)
        assertEquals(0, animation.delay)
        assertEquals(LinearEasing, animation.easing)
    }

    @Test fun movingSkeletonMaskHasAnEightPercentBandAndSameBaseAsStaticSkeleton() {
        assertEquals(BlendMode.DstIn, FwLoadingShimmerTheme.blendMode)
        val alpha = FwLoadingShimmerTheme.shaderColors.map { it.alpha }
        assertEquals(FW_LOADING_SKELETON_ALPHA, alpha.first(), 0.002f)
        assertEquals(FW_LOADING_SKELETON_ALPHA, alpha.last(), 0.002f)
        assertEquals(0.08f, alpha[1] - alpha[0], 0.003f)
    }
}
