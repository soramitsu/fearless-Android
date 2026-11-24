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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.theme.black05
import jp.co.soramitsu.common.compose.theme.borderGradientColors
import jp.co.soramitsu.common.compose.theme.customColors
import jp.co.soramitsu.common.compose.theme.fw.FwCard
import jp.co.soramitsu.common.compose.theme.fw.FwLight
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import jp.co.soramitsu.common.compose.theme.fw.FwShape
import jp.co.soramitsu.common.compose.theme.fw.LocalFwColors
import jp.co.soramitsu.common.compose.theme.fw.LocalFwEffects
import jp.co.soramitsu.common.compose.theme.fw.fwNeuInset
import jp.co.soramitsu.common.compose.theme.white08
import jp.co.soramitsu.common.compose.theme.white16
import jp.co.soramitsu.common.compose.theme.white24

@Composable
fun BackgroundCornered(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.customColors.backgroundBlurColor,
    content: @Composable () -> Unit
) {
    if (FwRestyle.paletteEnabled) {
        when (backgroundColor) {
            // The default card is a raised, key-lit surface; its rim catches the sun when near it.
            MaterialTheme.customColors.backgroundBlurColor -> FwCard(modifier = modifier.wrapContentSize(), content = content)
            Color.Unspecified, Color.Transparent -> Box(modifier.wrapContentSize()) { content() }
            else -> Box(modifier.background(backgroundColor, FwShape.Card).wrapContentSize()) { content() }
        }
        return
    }
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
    if (FwRestyle.paletteEnabled) {
        // Inputs, selectors and data tables are wells pressed into the page. A border is kept only
        // when it reports a state (focus, error, warning); neutral outlines are replaced by the well.
        val neutral = borderColor == white24 || borderColor == white08 || borderColor == white16 ||
            borderColor == Color.Unspecified || borderColor.alpha == 0f
        Box(
            modifier = modifier
                .wrapContentSize()
                .fwNeuInset(LocalFwEffects.current, LocalFwColors.current, FwShape.Well)
                .then(if (neutral) Modifier else Modifier.border(1.5.dp, borderColor, FwShape.Well))
                .clip(FwShape.Well)
        ) {
            content()
        }
        return
    }
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
    if (FwRestyle.paletteEnabled) {
        // Highlighted cards keep their raised surface and catch the full sun rim instead of a rainbow border.
        FwCard(modifier = modifier.wrapContentSize(), sunRim = FwLight.SUN_RIM_MAX) { Box { content() } }
        return
    }
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
