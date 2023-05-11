package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.theme.black05
import jp.co.soramitsu.common.compose.theme.borderGradientColors
import jp.co.soramitsu.common.compose.theme.customColors
import jp.co.soramitsu.common.compose.theme.white24

@Composable
fun BackgroundCornered(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.customColors.backgroundBlurColor,
    content: @Composable () -> Unit
) {
    Surface(
        color = Color.Unspecified,
        modifier = modifier
            .background(backgroundColor, FearlessCorneredShape())
            .wrapContentSize()
    ) {
        content()
    }
}

@Composable
fun BackgroundCorneredWithBorder(
    modifier: Modifier = Modifier,
    backgroundColor: Color = black05,
    borderColor: Color = white24,
    shape: Shape = FearlessCorneredShape(),
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .wrapContentSize()
            .border(1.dp, color = borderColor, shape = shape),
        shape = shape,
        color = backgroundColor
    ) {
        Box {
            content()
        }
    }
}

@Composable
fun BackgroundCorneredWithGradientBorder(
    modifier: Modifier = Modifier,
    backgroundColor: Color = black05,
    borderColors: List<Color> = borderGradientColors,
    shape: Shape = FearlessCorneredShape(),
    content: @Composable BoxScope.() -> Unit
) {
    val brushColors = if (borderColors.size < 2) {
        val useColor = borderColors.getOrNull(0) ?: white24
        listOf(useColor, useColor)
    } else {
        borderColors
    }

    Surface(
        modifier = modifier
            .wrapContentSize()
            .border(
                1.dp,
                brush = Brush.horizontalGradient(colors = brushColors),
                shape = shape
            ),
        shape = FearlessCorneredShape(),
        color = backgroundColor
    ) {
        Box {
            content()
        }
    }
}
