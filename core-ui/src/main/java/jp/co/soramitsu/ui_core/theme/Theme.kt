package jp.co.soramitsu.ui_core.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp

@Stable
data class CustomColors(
    val accentPrimary: Color,
    val accentPrimaryContainer: Color,
    val accentSecondary: Color,
    val accentSecondaryContainer: Color,
    val accentTertiary: Color,
    val accentTertiaryContainer: Color,
    val bgPage: Color,
    val bgSurface: Color,
    val bgSurfaceVariant: Color,
    val bgSurfaceInverted: Color,
    val fgPrimary: Color,
    val fgSecondary: Color,
    val fgTertiary: Color,
    val fgInverted: Color,
    val fgOutline: Color,
    val statusSuccess: Color,
    val statusSuccessContainer: Color,
    val statusWarning: Color,
    val statusWarningContainer: Color,
    val statusError: Color,
    val statusErrorContainer: Color
)

@Stable
data class BorderRadius(
    val s: Dp,
    val m: Dp,
    val ml: Dp,
    val xl: Dp
)

@Stable
data class CustomTypography(
    val displayL: TextStyle,
    val displayM: TextStyle,
    val displayS: TextStyle,
    val headline1: TextStyle,
    val headline2: TextStyle,
    val headline3: TextStyle,
    val headline4: TextStyle,
    val textL: TextStyle,
    val textM: TextStyle,
    val textS: TextStyle,
    val textXS: TextStyle,
    val textLBold: TextStyle,
    val textMBold: TextStyle,
    val textSBold: TextStyle,
    val textXSBold: TextStyle,
    val paragraphL: TextStyle,
    val paragraphM: TextStyle,
    val paragraphS: TextStyle,
    val paragraphXS: TextStyle,
    val paragraphLBold: TextStyle,
    val paragraphMBold: TextStyle,
    val paragraphSBold: TextStyle,
    val paragraphXSBold: TextStyle,
    val buttonM: TextStyle
)

private val LocalCustomColors = staticCompositionLocalOf<CustomColors> {
    error("CustomColors are not provided")
}
private val LocalCustomTypography = staticCompositionLocalOf<CustomTypography> {
    error("CustomTypography is not provided")
}
private val LocalBorderRadius = staticCompositionLocalOf<BorderRadius> {
    error("BorderRadius is not provided")
}

val MaterialTheme.customTypography: CustomTypography
    @Composable
    @ReadOnlyComposable
    get() = LocalCustomTypography.current

val MaterialTheme.customColors: CustomColors
    @Composable
    @ReadOnlyComposable
    get() = LocalCustomColors.current

val MaterialTheme.borderRadius: BorderRadius
    @Composable
    @ReadOnlyComposable
    get() = LocalBorderRadius.current

@Composable
fun AppTheme(
    darkTheme: Boolean,
    lightColors: CustomColors,
    darkColors: CustomColors,
    typography: CustomTypography,
    borderRadius: BorderRadius,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalCustomColors provides if (darkTheme) darkColors else lightColors,
        LocalCustomTypography provides typography,
        LocalBorderRadius provides borderRadius
    ) {
        val colors = if (darkTheme) darkColors else lightColors
        MaterialTheme(
            colors = if (darkTheme) {
                androidx.compose.material.darkColors(
                    primary = colors.accentPrimary,
                    primaryVariant = colors.accentPrimaryContainer,
                    secondary = colors.accentSecondary,
                    background = colors.bgPage,
                    surface = colors.bgSurface,
                    error = colors.statusError,
                    onPrimary = colors.fgInverted,
                    onSecondary = colors.fgInverted,
                    onBackground = colors.fgPrimary,
                    onSurface = colors.fgPrimary,
                    onError = colors.fgInverted
                )
            } else {
                androidx.compose.material.lightColors(
                    primary = colors.accentPrimary,
                    primaryVariant = colors.accentPrimaryContainer,
                    secondary = colors.accentSecondary,
                    background = colors.bgPage,
                    surface = colors.bgSurface,
                    error = colors.statusError,
                    onPrimary = colors.fgInverted,
                    onSecondary = colors.fgInverted,
                    onBackground = colors.fgPrimary,
                    onSurface = colors.fgPrimary,
                    onError = colors.fgInverted
                )
            },
            content = content
        )
    }
}

@Suppress("LongParameterList")
fun lightColors(
    accentPrimary: Color,
    accentPrimaryContainer: Color,
    accentSecondary: Color,
    accentSecondaryContainer: Color,
    accentTertiary: Color,
    accentTertiaryContainer: Color,
    bgPage: Color,
    bgSurface: Color,
    bgSurfaceVariant: Color,
    bgSurfaceInverted: Color,
    fgPrimary: Color,
    fgSecondary: Color,
    fgTertiary: Color,
    fgInverted: Color,
    fgOutline: Color,
    statusSuccess: Color,
    statusSuccessContainer: Color,
    statusWarning: Color,
    statusWarningContainer: Color,
    statusError: Color,
    statusErrorContainer: Color
): CustomColors = CustomColors(
    accentPrimary,
    accentPrimaryContainer,
    accentSecondary,
    accentSecondaryContainer,
    accentTertiary,
    accentTertiaryContainer,
    bgPage,
    bgSurface,
    bgSurfaceVariant,
    bgSurfaceInverted,
    fgPrimary,
    fgSecondary,
    fgTertiary,
    fgInverted,
    fgOutline,
    statusSuccess,
    statusSuccessContainer,
    statusWarning,
    statusWarningContainer,
    statusError,
    statusErrorContainer
)

@Suppress("LongParameterList")
fun darkColors(
    accentPrimary: Color,
    accentPrimaryContainer: Color,
    accentSecondary: Color,
    accentSecondaryContainer: Color,
    accentTertiary: Color,
    accentTertiaryContainer: Color,
    bgPage: Color,
    bgSurface: Color,
    bgSurfaceVariant: Color,
    bgSurfaceInverted: Color,
    fgPrimary: Color,
    fgSecondary: Color,
    fgTertiary: Color,
    fgInverted: Color,
    fgOutline: Color,
    statusSuccess: Color,
    statusSuccessContainer: Color,
    statusWarning: Color,
    statusWarningContainer: Color,
    statusError: Color,
    statusErrorContainer: Color
): CustomColors = lightColors(
    accentPrimary,
    accentPrimaryContainer,
    accentSecondary,
    accentSecondaryContainer,
    accentTertiary,
    accentTertiaryContainer,
    bgPage,
    bgSurface,
    bgSurfaceVariant,
    bgSurfaceInverted,
    fgPrimary,
    fgSecondary,
    fgTertiary,
    fgInverted,
    fgOutline,
    statusSuccess,
    statusSuccessContainer,
    statusWarning,
    statusWarningContainer,
    statusError,
    statusErrorContainer
)

fun borderRadiuses(
    s: Dp,
    m: Dp,
    ml: Dp,
    xl: Dp
): BorderRadius = BorderRadius(s, m, ml, xl)

@Suppress("LongParameterList")
fun defaultCustomTypography(
    displayL: TextStyle,
    displayM: TextStyle,
    displayS: TextStyle,
    headline1: TextStyle,
    headline2: TextStyle,
    headline3: TextStyle,
    headline4: TextStyle,
    textL: TextStyle,
    textM: TextStyle,
    textS: TextStyle,
    textXS: TextStyle,
    textLBold: TextStyle,
    textMBold: TextStyle,
    textSBold: TextStyle,
    textXSBold: TextStyle,
    paragraphL: TextStyle,
    paragraphM: TextStyle,
    paragraphS: TextStyle,
    paragraphXS: TextStyle,
    paragraphLBold: TextStyle,
    paragraphMBold: TextStyle,
    paragraphSBold: TextStyle,
    paragraphXSBold: TextStyle,
    buttonM: TextStyle
): CustomTypography = CustomTypography(
    displayL,
    displayM,
    displayS,
    headline1,
    headline2,
    headline3,
    headline4,
    textL,
    textM,
    textS,
    textXS,
    textLBold,
    textMBold,
    textSBold,
    textXSBold,
    paragraphL,
    paragraphM,
    paragraphS,
    paragraphXS,
    paragraphLBold,
    paragraphMBold,
    paragraphSBold,
    paragraphXSBold,
    buttonM
)
