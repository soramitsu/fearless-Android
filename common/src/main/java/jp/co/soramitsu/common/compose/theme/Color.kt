package jp.co.soramitsu.common.compose.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.ButtonColors
import androidx.compose.material.RadioButtonColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color

val colorPrimary = Color(0xFF004CB7)
val colorSelected = Color(0x66FF009A)
val colorPrimaryDark = Color(0x80004CB7)
val colorAccent = Color(0xFFFF009A)
val colorHint = Color(0xFFCCCCCC)
val gray1 = Color(0xFFCCCCCC)
val gray2 = Color(0xFF888888)
val gray3 = Color(0xFF444444)
val gray4 = Color(0xFF181818)
val grayDisabled = Color(0xFFD0D0D0)
val colorGreyText = Color(0xFF888888)
val black = Color(0xFF000000)
val green = Color(0xFF0ED33B)
val greenText = Color(0xFF09C8A1)
val dividerGray = Color(0xFF444444)
val bottomSheetBackground = Color(0x80838383)
val red = Color(0xFFF00004)
val blurColorDark = Color(0xB3000000)
val blurColor = Color(0x8C000000)
val blurColorLight = Color(0x66000000)
val backgroundBlurColor = Color(0xAFFFFFF)
val selectedGreen = Color(0xFF09C8A1)

val white = Color(0xFFFFFFFF)
val white04 = Color(0x0AFFFFFF)
val white08 = Color(0x14FFFFFF)
val white10 = Color(0x1AFFFFFF)
val white16 = Color(0x29FFFFFF)
val white20 = Color(0x33FFFFFF)
val white24 = Color(0x3DFFFFFF)
val white30 = Color(0x4DFFFFFF)
val white40 = Color(0x66FFFFFF)
val white50 = Color(0x80FFFFFF)
val white60 = Color(0xA6FFFFFF)
val white64 = Color(0xA3FFFFFF)

val black1 = gray1
val black2 = gray2
val black3 = gray3
val black4 = gray4
val black05 = Color(0xff1D1F21)
val black72 = Color(0xB8000000)
val black50 = Color(0x80000000)

val purple = Color(0xFF7700EE)
val backgroundBlack = Color(0xFF131313)
val grayButtonBackground = Color(0xFF2b2b2b)
val shimmerColor = Color(0x80DBDBDB)

val accountIconLight = Color(0xFFEEEEEE)
val accountIconDark = Color(0xFF000000)
val errorRed = Color(0xFFFF3B30)
val warningOrange = Color(0xFFEE7700)
val alertYellow = Color(0xFFEE7700)

val transparent = Color(0xffffff)

val colorAccentDark = Color(0xFFEE0077)

val accentButtonColors = object : ButtonColors {
    @Composable
    override fun backgroundColor(enabled: Boolean): State<Color> {
        return rememberUpdatedState(if (enabled) colorAccent else colorAccentDark)
    }

    @Composable
    override fun contentColor(enabled: Boolean): State<Color> {
        return rememberUpdatedState(if (enabled) white else white64)
    }
}

fun customButtonColors(
    backgroundColor: Color
) = customButtonColors(backgroundColor, Color.White)

fun customButtonColors(
    backgroundColor: Color,
    fontColor: Color
) = object : ButtonColors {
    @Composable
    override fun backgroundColor(enabled: Boolean): State<Color> {
        return rememberUpdatedState(if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.5f))
    }

    @Composable
    override fun contentColor(enabled: Boolean): State<Color> {
        return rememberUpdatedState(if (enabled) fontColor else fontColor.copy(alpha = 0.64f))
    }
}

val accentRadioButtonColors = object : RadioButtonColors {
    @Composable
    override fun radioColor(enabled: Boolean, selected: Boolean): State<Color> {
        val target = when {
            !enabled -> grayDisabled
            !selected -> white16
            else -> colorAccentDark
        }

        return if (enabled) {
            animateColorAsState(target, tween(durationMillis = 100))
        } else {
            rememberUpdatedState(target)
        }
    }
}
