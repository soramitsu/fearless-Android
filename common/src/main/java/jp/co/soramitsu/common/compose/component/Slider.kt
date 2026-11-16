package jp.co.soramitsu.common.compose.component

import androidx.compose.material.MaterialTheme
import androidx.compose.material.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.customColors
import kotlin.math.roundToInt

@Composable
fun Slider(
    value: Float,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChange: (Float) -> Unit = {},
    step: Float = 0.1f
) {
    androidx.compose.material.Slider(
        modifier = modifier,
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = sliderSteps(valueRange, step),
        colors = SliderDefaults.colors(
            inactiveTickColor = Color.Transparent,
            inactiveTrackColor = MaterialTheme.customColors.white08,
            activeTickColor = Color.Transparent,
            activeTrackColor = colorAccentDark,
            thumbColor = MaterialTheme.customColors.white
        )
    )
}

/**
 * The `steps` argument of the Material slider for positions every [step] across [valueRange]. Material counts
 * only the stops between the two ends, so N gaps of [step] take N - 1 steps; the gap count is rounded because
 * float division can land just below or above a whole number (10f / 0.1f).
 */
internal fun sliderSteps(valueRange: ClosedFloatingPointRange<Float>, step: Float): Int =
    (((valueRange.endInclusive - valueRange.start) / step).roundToInt() - 1).coerceAtLeast(0)

@Preview
@Composable
fun SliderPreview() {
    Slider(value = 0f)
}
