package jp.co.soramitsu.common.compose.component

import android.os.Parcelable
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.backgroundBlack
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.transparent
import jp.co.soramitsu.common.compose.theme.white
import kotlinx.parcelize.Parcelize

@Parcelize
sealed class HaloIconState : Parcelable {
    @Parcelize
    class Remote(val url: String, val color: String?) : HaloIconState()

    @Parcelize
    class Local(@DrawableRes val res: Int) : HaloIconState()
}

@Composable
fun HaloIcon(
    @DrawableRes iconRes: Int,
    color: Color,
    modifier: Modifier = Modifier,
    background: Color = transparent,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val gradientBrush = Brush.radialGradient(
        colors = listOf(color, transparent)
    )

    val haloPadding = 14.dp
    val haloWidth = 30.dp
    val imageSize = 90.dp
    val haloSize = imageSize + haloPadding * 2 + haloWidth * 2
    Box(
        modifier
            .size(haloSize)
            .border(haloWidth, gradientBrush, CircleShape)
            .padding(haloWidth)
            .background(background, CircleShape)
            .padding(contentPadding)
    ) {
        Image(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(imageSize)
                .align(Alignment.Center),
            res = iconRes,
            tint = color
        )
    }
}

@Composable
fun HaloIcon(
    icon: String,
    color: Color,
    modifier: Modifier = Modifier,
    background: Color = transparent,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    tintImage: Boolean = true
) {
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
fun HaloIcon(
    icon: HaloIconState,
    color: Color,
    modifier: Modifier = Modifier,
    background: Color = transparent,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    when (icon) {
        is HaloIconState.Remote -> {
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

        is HaloIconState.Local -> {
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
private fun HaloIconPreview() {
    FearlessAppTheme {
        Column {
            HaloIcon(
                iconRes = R.drawable.ic_fearless_bird,
                color = colorAccentDark,
                background = backgroundBlack
            )
            Box(modifier = Modifier.size(149.dp)) {
                HaloIcon(
                    iconRes = R.drawable.ic_fearless_bird,
                    color = colorAccentDark,
                    background = backgroundBlack
                )
            }
        }
    }
}
