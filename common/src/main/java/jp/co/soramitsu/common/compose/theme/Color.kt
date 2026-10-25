package jp.co.soramitsu.common.compose.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.ButtonColors
import androidx.compose.material.RadioButtonColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import jp.co.soramitsu.common.compose.theme.fw.FwLegacyPalette
import androidx.core.graphics.toColorInt
import jp.co.soramitsu.fearless_utils.extensions.requirePrefix

val colorPrimary = FwLegacyPalette.colorPrimary
val colorSelected = FwLegacyPalette.colorSelected
val colorPrimaryDark = FwLegacyPalette.colorPrimaryDark
val colorAccent = FwLegacyPalette.colorAccent
val colorHint = FwLegacyPalette.colorHint
val gray1 = FwLegacyPalette.gray1
val gray2 = FwLegacyPalette.gray2
val gray3 = FwLegacyPalette.gray3
val gray4 = FwLegacyPalette.gray4
val grayDisabled = FwLegacyPalette.grayDisabled
val colorGreyText = FwLegacyPalette.colorGreyText
val black = FwLegacyPalette.black
val green = FwLegacyPalette.green
val greenText = FwLegacyPalette.greenText
val dividerGray = FwLegacyPalette.dividerGray
val bottomSheetBackground = FwLegacyPalette.bottomSheetBackground
val red = FwLegacyPalette.red
val blurColorDark = FwLegacyPalette.blurColorDark
val blurColor = FwLegacyPalette.blurColor
val blurColorLight = FwLegacyPalette.blurColorLight
val backgroundBlurColor = FwLegacyPalette.backgroundBlurColor
val selectedGreen = FwLegacyPalette.selectedGreen
val white = FwLegacyPalette.white
val white04 = FwLegacyPalette.white04
val white08 = FwLegacyPalette.white08
val white10 = FwLegacyPalette.white10
val white16 = FwLegacyPalette.white16
val white20 = FwLegacyPalette.white20
val white24 = FwLegacyPalette.white24
val white30 = FwLegacyPalette.white30
val white40 = FwLegacyPalette.white40
val white50 = FwLegacyPalette.white50
val white60 = FwLegacyPalette.white60
val white64 = FwLegacyPalette.white64

val borderGradientColors = FwLegacyPalette.borderGradientColors

val black1 = FwLegacyPalette.black1
val black2 = FwLegacyPalette.black2
val black3 = FwLegacyPalette.black3
val black4 = FwLegacyPalette.black4
val black05 = FwLegacyPalette.black05
val black72 = FwLegacyPalette.black72
val black50 = FwLegacyPalette.black50

val purple = FwLegacyPalette.purple
val backgroundBlack = FwLegacyPalette.backgroundBlack
val grayButtonBackground = FwLegacyPalette.grayButtonBackground
val darkButtonBackground = FwLegacyPalette.darkButtonBackground
val shimmerColor = FwLegacyPalette.shimmerColor

val accountIconLight = FwLegacyPalette.accountIconLight
val accountIconDark = FwLegacyPalette.accountIconDark
val errorRed = FwLegacyPalette.errorRed
val warningOrange = FwLegacyPalette.warningOrange
val alertYellow = FwLegacyPalette.alertYellow
val warningYellow = FwLegacyPalette.warningYellow
val demeterYellow = FwLegacyPalette.demeterYellow

val transparent = FwLegacyPalette.transparent

val colorAccentDark = FwLegacyPalette.colorAccentDark
val colorAccentSecondary = FwLegacyPalette.colorAccentSecondary
val soraRed = FwLegacyPalette.soraRed
val soraBlueDialog = FwLegacyPalette.soraBlueDialog

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

val accentDarkButtonColors = object : ButtonColors {
    @Composable
    override fun backgroundColor(enabled: Boolean): State<Color> {
        return rememberUpdatedState(if (enabled) colorAccentDark else colorAccentDark.copy(alpha = 0.5f))
    }

    @Composable
    override fun contentColor(enabled: Boolean): State<Color> {
        return rememberUpdatedState(if (enabled) white else white30)
    }
}

val accentDarkDisabledButtonColors = object : ButtonColors {
    @Composable
    override fun backgroundColor(enabled: Boolean): State<Color> {
        return rememberUpdatedState(if (enabled) colorAccentDark else white08)
    }

    @Composable
    override fun contentColor(enabled: Boolean): State<Color> {
        return rememberUpdatedState(if (enabled) white else white08)
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
            !selected -> colorAccentDark
            else -> colorAccentDark
        }

        return if (enabled) {
            animateColorAsState(target, tween(durationMillis = 100))
        } else {
            rememberUpdatedState(target)
        }
    }
}

fun String.colorFromHex(): Color? {
    val hexColor = this.requirePrefix("#")
    return runCatching { Color(hexColor.toColorInt()) }.getOrNull()
}
