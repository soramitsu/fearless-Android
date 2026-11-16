package jp.co.soramitsu.common.compose.component

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Bug 36: the slider passed one step too many to the Material slider, whose `steps` counts the stops
 * between the two ends. The slippage slider (0..10 %, step 0.1) stopped at multiples of 10/101, so a
 * position meant as 2.0 % reported 1.980198 %.
 */
class SliderStepsTest {

    @Test
    fun shouldStopEveryTenthOfAPercent_whenTheSlippageRangeIsZeroToTen() {
        val steps = sliderSteps(0f..10f, 0.1f)

        assertEquals(99, steps)
        assertEquals(2.0f, stopValue(0f..10f, steps, index = 20), 1e-5f)
        assertEquals(0.5f, stopValue(0f..10f, steps, index = 5), 1e-5f)
        assertEquals(10.0f, stopValue(0f..10f, steps, index = steps + 1), 1e-5f)
    }

    @Test
    fun shouldCountOnlyTheStopsBetweenTheEnds_forOtherRanges() {
        assertEquals(9, sliderSteps(0f..1f, 0.1f))
        assertEquals(3, sliderSteps(1f..5f, 1f))
        assertEquals(0, sliderSteps(0f..1f, 1f))
    }

    // The value of stop [index] (0 is the start) as the Material slider places it: evenly over `steps + 1` gaps.
    private fun stopValue(
        range: ClosedFloatingPointRange<Float>,
        steps: Int,
        index: Int
    ): Float = range.start + (range.endInclusive - range.start) * index / (steps + 1)
}
