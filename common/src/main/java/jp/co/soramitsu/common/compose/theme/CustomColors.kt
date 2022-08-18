package jp.co.soramitsu.common.compose.theme

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Stable
data class CustomColors(
    val colorPrimary: Color,
    val colorSelected: Color,
    val colorPrimaryDark: Color,
    val colorAccent: Color,
    val colorHint: Color,
    val gray1: Color,
    val gray2: Color,
    val gray3: Color,
    val gray4: Color,
    val grayDisabled: Color,
    val colorGreyText: Color,
    val green: Color,
    val greenText: Color,
    val black: Color,
    val white: Color,
    val white04: Color,
    val white10: Color,
    val white16: Color,
    val white20: Color,
    val white24: Color,
    val white30: Color,
    val white40: Color,
    val white50: Color,
    val white60: Color,
    val dividerGray: Color,
    val bottomSheetBackground: Color,
    val red: Color,
    val blurColorDark: Color,
    val blurColor: Color,
    val blurColorLight: Color,
    val backgroundBlurColor: Color,
    val accountIconLight: Color,
    val accountIconDark: Color,
    val errorRed: Color
)

val flwColors = CustomColors(
    colorPrimary = colorPrimary,
    colorSelected = colorSelected,
    colorPrimaryDark = colorPrimaryDark,
    colorAccent = colorAccent,
    colorHint = colorHint,
    gray1 = gray1,
    gray2 = gray2,
    gray3 = gray3,
    gray4 = gray4,
    grayDisabled = grayDisabled,
    colorGreyText = colorGreyText,
    green = green,
    greenText = greenText,
    black = black,
    white = white,
    white04 = white04,
    white10 = white10,
    white16 = white16,
    white20 = white20,
    white24 = white24,
    white30 = white30,
    white40 = white40,
    white50 = white50,
    white60 = white60,
    dividerGray = dividerGray,
    bottomSheetBackground = bottomSheetBackground,
    red = red,
    blurColorDark = blurColorDark,
    blurColor = blurColor,
    blurColorLight = blurColorLight,
    backgroundBlurColor = backgroundBlurColor,
    accountIconLight = accountIconLight,
    accountIconDark = accountIconDark,
    errorRed = errorRed
)

internal val FearlessColors = staticCompositionLocalOf { flwColors }
