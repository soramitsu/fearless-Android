package jp.co.soramitsu.common.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import jp.co.soramitsu.common.compose.theme.tokens.DayThemeColors
import jp.co.soramitsu.common.compose.theme.tokens.NightThemeColors
import jp.co.soramitsu.common.compose.theme.tokens.buttonM
import jp.co.soramitsu.common.compose.theme.tokens.displayL
import jp.co.soramitsu.common.compose.theme.tokens.displayM
import jp.co.soramitsu.common.compose.theme.tokens.displayS
import jp.co.soramitsu.common.compose.theme.tokens.headline1
import jp.co.soramitsu.common.compose.theme.tokens.headline2
import jp.co.soramitsu.common.compose.theme.tokens.headline3
import jp.co.soramitsu.common.compose.theme.tokens.headline4
import jp.co.soramitsu.common.compose.theme.tokens.paragraphL
import jp.co.soramitsu.common.compose.theme.tokens.paragraphLBold
import jp.co.soramitsu.common.compose.theme.tokens.paragraphM
import jp.co.soramitsu.common.compose.theme.tokens.paragraphMBold
import jp.co.soramitsu.common.compose.theme.tokens.paragraphS
import jp.co.soramitsu.common.compose.theme.tokens.paragraphSBold
import jp.co.soramitsu.common.compose.theme.tokens.paragraphXS
import jp.co.soramitsu.common.compose.theme.tokens.paragraphXSBold
import jp.co.soramitsu.common.compose.theme.tokens.textL
import jp.co.soramitsu.common.compose.theme.tokens.textLBold
import jp.co.soramitsu.common.compose.theme.tokens.textM
import jp.co.soramitsu.common.compose.theme.tokens.textMBold
import jp.co.soramitsu.common.compose.theme.tokens.textS
import jp.co.soramitsu.common.compose.theme.tokens.textSBold
import jp.co.soramitsu.common.compose.theme.tokens.textXS
import jp.co.soramitsu.common.compose.theme.tokens.textXSBold
import jp.co.soramitsu.ui_core.theme.AppTheme
import jp.co.soramitsu.ui_core.theme.BorderRadius
import jp.co.soramitsu.ui_core.theme.CustomTypography
import jp.co.soramitsu.ui_core.theme.borderRadiuses
import jp.co.soramitsu.ui_core.theme.darkColors
import jp.co.soramitsu.ui_core.theme.defaultCustomTypography
import jp.co.soramitsu.ui_core.theme.lightColors

@Deprecated("use FearlessAppTheme instead")
@Composable
fun FearlessTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        FearlessColors provides flwColors,
        FearlessTypography provides flwTypography
    ) {
        MaterialTheme(
            colors = fearlessMaterialColors,
            content = content
        )
    }
}

val MaterialTheme.customColors: CustomColors
    @Composable
    @ReadOnlyComposable
    get() = FearlessColors.current

val MaterialTheme.customTypography: CustomTypographyFlw
    @Composable
    @ReadOnlyComposable
    get() = FearlessTypography.current

//@Composable
//fun FearlessAppTheme(content: @Composable () -> Unit) = FearlessWalletTheme(true, content)

@Composable
fun FearlessAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    AppTheme(
        darkTheme = darkTheme,
        lightColors = fearlessLightColors,
        darkColors = fearlessDarkColors,
        typography = fearlessTypography,
        borderRadius = fearlessBorderRadius,
        content = content
    )
}

private val fearlessLightColors = lightColors(
    accentPrimary = DayThemeColors.AccentPrimary,
    accentPrimaryContainer = DayThemeColors.AccentPrimaryContainer,
    accentSecondary = DayThemeColors.AccentSecondary,
    accentSecondaryContainer = DayThemeColors.AccentSecondaryContainer,
    accentTertiary = DayThemeColors.AccentTertiary,
    accentTertiaryContainer = DayThemeColors.AccentTertiaryContainer,
    bgPage = DayThemeColors.BgPage,
    bgSurface = DayThemeColors.BgSurface,
    bgSurfaceVariant = DayThemeColors.BgSurfaceVariant,
    bgSurfaceInverted = DayThemeColors.BgSurfaceInverted,
    fgPrimary = DayThemeColors.FgPrimary,
    fgSecondary = DayThemeColors.FgSecondary,
    fgTertiary = DayThemeColors.FgTertiary,
    fgInverted = DayThemeColors.FgInverted,
    fgOutline = DayThemeColors.FgOutline,
    statusSuccess = DayThemeColors.StatusSuccess,
    statusSuccessContainer = DayThemeColors.StatusSuccessContainer,
    statusWarning = DayThemeColors.StatusWarning,
    statusWarningContainer = DayThemeColors.StatusWarningContainer,
    statusError = DayThemeColors.StatusError,
    statusErrorContainer = DayThemeColors.StatusErrorContainer
)

private val fearlessDarkColors = darkColors(
    accentPrimary = NightThemeColors.AccentPrimary,
    accentPrimaryContainer = NightThemeColors.AccentPrimaryContainer,
    accentSecondary = NightThemeColors.AccentSecondary,
    accentSecondaryContainer = NightThemeColors.AccentSecondaryContainer,
    accentTertiary = NightThemeColors.AccentTertiary,
    accentTertiaryContainer = NightThemeColors.AccentTertiaryContainer,
    bgPage = NightThemeColors.BgPage,
    bgSurface = NightThemeColors.BgSurface,
    bgSurfaceVariant = NightThemeColors.BgSurfaceVariant,
    bgSurfaceInverted = NightThemeColors.BgSurfaceInverted,
    fgPrimary = NightThemeColors.FgPrimary,
    fgSecondary = NightThemeColors.FgSecondary,
    fgTertiary = NightThemeColors.FgTertiary,
    fgInverted = NightThemeColors.FgInverted,
    fgOutline = NightThemeColors.FgOutline,
    statusSuccess = NightThemeColors.StatusSuccess,
    statusSuccessContainer = NightThemeColors.StatusSuccessContainer,
    statusWarning = NightThemeColors.StatusWarning,
    statusWarningContainer = NightThemeColors.StatusWarningContainer,
    statusError = NightThemeColors.StatusError,
    statusErrorContainer = NightThemeColors.StatusErrorContainer
)

private val fearlessTypography: CustomTypography = defaultCustomTypography(
    displayL = displayL,
    displayM = displayM,
    displayS = displayS,
    headline1 = headline1,
    headline2 = headline2,
    headline3 = headline3,
    headline4 = headline4,
    textL = textL,
    textM = textM,
    textS = textS,
    textXS = textXS,
    textLBold = textLBold,
    textMBold = textMBold,
    textSBold = textSBold,
    textXSBold = textXSBold,
    paragraphL = paragraphL,
    paragraphM = paragraphM,
    paragraphS = paragraphS,
    paragraphXS = paragraphXS,
    paragraphLBold = paragraphLBold,
    paragraphMBold = paragraphMBold,
    paragraphSBold = paragraphSBold,
    paragraphXSBold = paragraphXSBold,
    buttonM = buttonM
)

private val fearlessBorderRadius: BorderRadius = borderRadiuses(
    s = jp.co.soramitsu.common.compose.theme.tokens.BorderRadius.XS,
    m = jp.co.soramitsu.common.compose.theme.tokens.BorderRadius.M,
    ml = jp.co.soramitsu.common.compose.theme.tokens.BorderRadius.ML,
    xl = jp.co.soramitsu.common.compose.theme.tokens.BorderRadius.L
)
