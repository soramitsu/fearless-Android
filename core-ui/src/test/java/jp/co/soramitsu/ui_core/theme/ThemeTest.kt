package jp.co.soramitsu.ui_core.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ThemeTest {
    @get:Rule val compose = createComposeRule()

    // Distinct opaque test colours (#000000 + n * 1000) built from channels: the raw-colour ratchet
    // (FwRawColourRatchetTest) counts every hex Color literal under src/, test sources included.
    private val palette = (1..21).map { Color(red = 0, green = it * 1000 shr 8, blue = it * 1000 and 0xFF) }
    private val styles = (1..24).map { TextStyle(fontSize = it.sp) }
    private val typography = defaultCustomTypography(
        displayL = styles[0],
        displayM = styles[1],
        displayS = styles[2],
        headline1 = styles[3],
        headline2 = styles[4],
        headline3 = styles[5],
        headline4 = styles[6],
        textL = styles[7],
        textM = styles[8],
        textS = styles[9],
        textXS = styles[10],
        textLBold = styles[11],
        textMBold = styles[12],
        textSBold = styles[13],
        textXSBold = styles[14],
        paragraphL = styles[15],
        paragraphM = styles[16],
        paragraphS = styles[17],
        paragraphXS = styles[18],
        paragraphLBold = styles[19],
        paragraphMBold = styles[20],
        paragraphSBold = styles[21],
        paragraphXSBold = styles[22],
        buttonM = styles[23]
    )
    private val light = lightColors(
        accentPrimary = palette[0],
        accentPrimaryContainer = palette[1],
        accentSecondary = palette[2],
        accentSecondaryContainer = palette[3],
        accentTertiary = palette[4],
        accentTertiaryContainer = palette[5],
        bgPage = palette[6],
        bgSurface = palette[7],
        bgSurfaceVariant = palette[8],
        bgSurfaceInverted = palette[9],
        fgPrimary = palette[10],
        fgSecondary = palette[11],
        fgTertiary = palette[12],
        fgInverted = palette[13],
        fgOutline = palette[14],
        statusSuccess = palette[15],
        statusSuccessContainer = palette[16],
        statusWarning = palette[17],
        statusWarningContainer = palette[18],
        statusError = palette[19],
        statusErrorContainer = palette[20]
    )
    private val dark = darkColors(
        accentPrimary = palette[0].copy(alpha = 0.75f),
        accentPrimaryContainer = palette[1].copy(alpha = 0.75f),
        accentSecondary = palette[2].copy(alpha = 0.75f),
        accentSecondaryContainer = palette[3].copy(alpha = 0.75f),
        accentTertiary = palette[4].copy(alpha = 0.75f),
        accentTertiaryContainer = palette[5].copy(alpha = 0.75f),
        bgPage = palette[6].copy(alpha = 0.75f),
        bgSurface = palette[7].copy(alpha = 0.75f),
        bgSurfaceVariant = palette[8].copy(alpha = 0.75f),
        bgSurfaceInverted = palette[9].copy(alpha = 0.75f),
        fgPrimary = palette[10].copy(alpha = 0.75f),
        fgSecondary = palette[11].copy(alpha = 0.75f),
        fgTertiary = palette[12].copy(alpha = 0.75f),
        fgInverted = palette[13].copy(alpha = 0.75f),
        fgOutline = palette[14].copy(alpha = 0.75f),
        statusSuccess = palette[15].copy(alpha = 0.75f),
        statusSuccessContainer = palette[16].copy(alpha = 0.75f),
        statusWarning = palette[17].copy(alpha = 0.75f),
        statusWarningContainer = palette[18].copy(alpha = 0.75f),
        statusError = palette[19].copy(alpha = 0.75f),
        statusErrorContainer = palette[20].copy(alpha = 0.75f)
    )
    private val radii = borderRadiuses(2.dp, 4.dp, 8.dp, 16.dp)

    @Test
    fun lightThemeMapsSemanticColorsAndProvidesTypographyAndRadii() {
        assertTheme(darkTheme = false, expected = light)
    }

    @Test
    fun darkThemeUsesDarkPaletteAndMaterialDarkFlag() {
        assertTheme(darkTheme = true, expected = dark)
    }

    private fun assertTheme(darkTheme: Boolean, expected: CustomColors) {
        compose.setContent {
            AppTheme(darkTheme, light, dark, typography, radii) {
                val material = MaterialTheme.colors
                assertEquals(!darkTheme, material.isLight)
                assertEquals(expected, MaterialTheme.customColors)
                assertEquals(typography, MaterialTheme.customTypography)
                assertEquals(radii, MaterialTheme.borderRadius)
                assertEquals(expected.accentPrimary, material.primary)
                assertEquals(expected.accentPrimaryContainer, material.primaryVariant)
                assertEquals(expected.accentSecondary, material.secondary)
                assertEquals(expected.bgPage, material.background)
                assertEquals(expected.bgSurface, material.surface)
                assertEquals(expected.statusError, material.error)
                assertEquals(expected.fgInverted, material.onPrimary)
                assertEquals(expected.fgInverted, material.onSecondary)
                assertEquals(expected.fgInverted, material.onError)
                assertEquals(expected.fgPrimary, material.onBackground)
                assertEquals(expected.fgPrimary, material.onSurface)
                Text("Themed content", style = MaterialTheme.customTypography.headline1)
            }
        }
        compose.onNodeWithText("Themed content").assertIsDisplayed()
    }
}
