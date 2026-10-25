package jp.co.soramitsu.common.compose.theme.fw

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import jp.co.soramitsu.common.compose.theme.Inter
import jp.co.soramitsu.common.compose.theme.Sora
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FwTypeTest {
    @Test fun displayFontUsesSoraOnlyForSupportedLatinTitles() {
        for (language in listOf("en", "id", "in", "pt", "pt-PT", "tr")) {
            assertEquals(Sora, FwFont.display(language))
            assertEquals(Sora, fwTextStyle(FwTextRole.TitleL, language).fontFamily)
        }
        for (language in listOf("ru", "vi", "ja", "zh-Hans", "uk", "ar")) {
            assertEquals(FwFont.body(language), FwFont.display(language))
            assertNotEquals(Sora, fwTextStyle(FwTextRole.TitleM, language).fontFamily)
        }
        assertEquals(Inter, FwFont.body("vi"))
        assertEquals(FontFamily.SansSerif, FwFont.body("zh_Hans"))
        assertEquals(FontFamily.SansSerif, FwFont.body("ja-JP"))
    }

    @Test fun numbersUseTabularBodyAndRecoveryWordsNeverUseMono() {
        for (language in listOf("en", "id", "ja", "pt", "ru", "tr", "vi", "zh")) {
            for (role in listOf(FwTextRole.DisplayXL, FwTextRole.DisplayL, FwTextRole.NumL, FwTextRole.NumM, FwTextRole.NumS)) {
                val style = fwTextStyle(role, language)
                assertEquals(FwFont.body(language), style.fontFamily)
                assertEquals("tnum", style.fontFeatureSettings)
            }
            assertEquals(FwFont.body(language), fwTextStyle(FwTextRole.PhraseWord, language).fontFamily)
            assertEquals(FwFont.Mono, fwTextStyle(FwTextRole.MonoM, language).fontFamily)
            assertEquals("zero", fwTextStyle(FwTextRole.MonoM, language).fontFeatureSettings)
        }
    }

    @Test fun hudUsesLocaleCorrectCasingSpacingAndCoveredFonts() {
        assertEquals("İŞLEM", fwHudLabel("işlem", "tr"))
        assertEquals("TOTAL BALANCE", fwHudLabel("Total balance", "en"))
        for ((language, label) in listOf("ru" to "Общий баланс", "vi" to "Tổng số dư", "ja" to "合計残高", "zh" to "总余额")) {
            assertEquals(label, fwHudLabel(label, language))
            val cjk = language in listOf("ja", "zh")
            val style = fwTextStyle(FwTextRole.Hud, language)
            assertEquals(if (cjk) FwFont.body(language) else FwFont.Mono, style.fontFamily)
            assertEquals(if (cjk) 0.em else 0.04.em, style.letterSpacing)
        }
        assertEquals(0.08.em, fwTextStyle(FwTextRole.Hud, "pt-PT").letterSpacing)
    }

    @Test fun scaleRespectsMinimumSizeCalmLeadingAndBundledWeights() {
        for (role in FwTextRole.entries) {
            val style = fwTextStyle(role, "en")
            assertTrue(style.fontSize.value >= 12)
            assertTrue(style.lineHeight.value > style.fontSize.value)
            if (style.fontFamily == Inter) assertNotEquals(FontWeight.Medium, style.fontWeight)
        }
        assertEquals(24.sp, fwTextStyle(FwTextRole.BodyL, "en").lineHeight)
        assertEquals(26.sp, fwTextStyle(FwTextRole.BodyL, "en", calm = true).lineHeight)
        assertEquals(40.sp, fwTextStyle(FwTextRole.DisplayXL, "en").fontSize)
        assertEquals((-0.02).em, fwTextStyle(FwTextRole.DisplayXL, "en").letterSpacing)
        assertEquals(FontWeight.Medium, fwTextStyle(FwTextRole.Hud, "ru").fontWeight)
    }
}
