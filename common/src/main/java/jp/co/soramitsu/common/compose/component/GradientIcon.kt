package jp.co.soramitsu.common.compose.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
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
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.transparent

@Composable
fun GradientIcon(@DrawableRes iconRes: Int, color: Color, modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues(0.dp)) {
    val gradientBrush = Brush.radialGradient(
        colors = listOf(color, transparent)
    )
    Box(modifier = modifier) {
        Box(
            Modifier
                .size(90.dp)
                .background(transparent, CircleShape)
                .border(10.dp, gradientBrush, CircleShape)
                .padding(contentPadding)
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
}

@Composable
fun GradientIcon(icon: String, color: Color, modifier: Modifier = Modifier) {
    val gradientBrush = Brush.radialGradient(
        colors = listOf(color, transparent)
    )
    Box(modifier = modifier) {
        Box(
            Modifier
                .size(90.dp)
                .background(transparent, CircleShape)
                .border(10.dp, gradientBrush, CircleShape)
        ) {
            AsyncImage(
                model = getImageRequest(LocalContext.current, icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(color),
                modifier = Modifier
                    .size(45.dp)
                    .align(Alignment.Center)
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
