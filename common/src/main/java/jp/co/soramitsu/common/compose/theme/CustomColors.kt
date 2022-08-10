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
    val black: Color,
    val white: Color,
    val dividerGray: Color,
    val bottomSheetBackground: Color,
    val red: Color,
    val blurColorDark: Color,
    val blurColor: Color,
    val blurColorLight: Color,
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
    black = black,
    white = white,
    dividerGray = dividerGray,
    bottomSheetBackground = bottomSheetBackground,
    red = red,
    blurColorDark = blurColorDark,
    blurColor = blurColor,
    blurColorLight = blurColorLight,
    accountIconLight = accountIconLight,
    accountIconDark = accountIconDark,
    errorRed = errorRed
)

internal val FearlessColors = staticCompositionLocalOf { flwColors }
