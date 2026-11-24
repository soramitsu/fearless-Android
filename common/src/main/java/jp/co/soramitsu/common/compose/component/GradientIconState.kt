package jp.co.soramitsu.common.compose.component

import android.os.Parcelable
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.fw.FwElevation
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import jp.co.soramitsu.common.compose.theme.fw.LocalFwColors
import jp.co.soramitsu.common.compose.theme.fw.LocalFwEffects
import jp.co.soramitsu.common.compose.theme.fw.fwSurface
import jp.co.soramitsu.common.compose.theme.transparent
import jp.co.soramitsu.common.compose.theme.white
import kotlinx.parcelize.Parcelize

@Parcelize
sealed class GradientIconState : Parcelable {
    @Parcelize
    class Remote(val url: String, val color: String?) : GradientIconState()

    @Parcelize
    class Local(@DrawableRes val res: Int) : GradientIconState()
}

@Composable
fun GradientIcon(
    @DrawableRes iconRes: Int,
    color: Color,
    modifier: Modifier = Modifier,
    background: Color = transparent,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    if (FwRestyle.paletteEnabled) {
        SunlitIconWell(color, modifier) { Image(res = iconRes, tint = color, modifier = Modifier.size(45.dp)) }
        return
    }
    val gradientBrush = Brush.radialGradient(
        colors = listOf(color, transparent)
    )
    Box(
        modifier
            .size(90.dp)
            .border(10.dp, gradientBrush, CircleShape)
            .padding(contentPadding)
            .background(background, CircleShape)
    ) {
        Image(
            res = iconRes,
            tint = color,
            modifier = Modifier
                .size(45.dp)
                .align(Alignment.Center)
        )
    }
}

/**
 * The big status or network icon of a sheet: a well pressed into the surface, with the icon's own colour
 * lighting its rim. It is still, so it also suits signing sheets; Reduced and Calm drop the light.
 */
@Composable
private fun SunlitIconWell(color: Color, modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    val fx = LocalFwEffects.current
    Box(
        modifier = modifier
            .size(90.dp)
            .then(
                if (fx.glow) {
                    Modifier.drawBehind {
                        val radius = size.minDimension / 2f
                        drawCircle(Brush.radialGradient(0.6f to color.copy(alpha = 0.32f), 1f to transparent, center = center, radius = radius), radius)
                    }
                } else {
                    Modifier
                }
            )
            .padding(8.dp)
            .fwSurface(fx, LocalFwColors.current, CircleShape, FwElevation.Inset),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
fun GradientIcon(
    icon: String,
    color: Color,
    modifier: Modifier = Modifier,
    background: Color = transparent,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    tintImage: Boolean = true
) {
    if (FwRestyle.paletteEnabled) {
        SunlitIconWell(color, modifier) {
            AsyncImage(
                model = getImageRequest(LocalContext.current, icon),
                contentDescription = null,
                colorFilter = if (tintImage) ColorFilter.tint(color) else null,
                modifier = Modifier.size(45.dp)
            )
        }
        return
    }
    val gradientBrush = Brush.radialGradient(
        colors = listOf(color, transparent)
    )
    Box(
        modifier = modifier
            .size(90.dp)
            .border(10.dp, gradientBrush, CircleShape)
            .padding(contentPadding)
            .background(background, CircleShape)
    ) {
        AsyncImage(
            model = getImageRequest(LocalContext.current, icon),
            contentDescription = null,
            colorFilter = if (tintImage) ColorFilter.tint(color) else null,
            modifier = Modifier
                .size(45.dp)
                .align(Alignment.Center)
        )
    }
}

@Composable
fun GradientIcon(
    icon: GradientIconState,
    color: Color,
    modifier: Modifier = Modifier,
    background: Color = transparent,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    when (icon) {
        is GradientIconState.Remote -> {
            val iconColorParsed = icon.color?.takeIf { it.isNotEmpty() }?.let {
                Color(android.graphics.Color.parseColor("#$it"))
            } ?: white
            if (icon.url.isEmpty()) {
                GradientIcon(
                    iconRes = R.drawable.ic_about_wiki,
                    color = iconColorParsed,
                    modifier = modifier,
                    background = background,
                    contentPadding = contentPadding
                )
            } else {
                GradientIcon(
                    icon = icon.url,
                    color = iconColorParsed,
                    modifier = modifier,
                    background = background,
                    tintImage = false,
                    contentPadding = contentPadding
                )
            }
        }
        is GradientIconState.Local -> {
            GradientIcon(
                iconRes = icon.res,
                color = color,
                modifier = modifier,
                background = background,
                contentPadding = contentPadding
            )
        }
    }
}

@Composable
@Preview
private fun GradientIconPreview() {
    FearlessTheme {
        GradientIcon(R.drawable.ic_vector, colorAccentDark)
    }
}
