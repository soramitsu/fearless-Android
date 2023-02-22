package jp.co.soramitsu.common.compose.component

import androidx.compose.material.MaterialTheme
import androidx.compose.material.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.customColors

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
        steps = ((valueRange.endInclusive - valueRange.start) / step).toInt(),
        colors = SliderDefaults.colors(
            inactiveTickColor = Color.Transparent,
            inactiveTrackColor = MaterialTheme.customColors.white08,
            activeTickColor = Color.Transparent,
            activeTrackColor = colorAccentDark,
            thumbColor = MaterialTheme.customColors.white
        )
    )
}

@Preview
@Composable
fun SliderPreview() {
    Slider(value = 0f)
}
