package jp.co.soramitsu.common.compose.theme.fw

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import java.util.Locale
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.Inter
import jp.co.soramitsu.common.compose.theme.Sora

/** Semantic roles from the approved type scale; amounts always use the body face. */
enum class FwTextRole {
    DisplayXL, DisplayL, TitleL, TitleM, TitleS, BodyL, BodyM, BodyS,
    LabelL, LabelM, LabelS, NumL, NumM, NumS, Hud, MonoM, MonoS, PhraseWord
}

object FwFont {
    val Mono: FontFamily = FontFamily(
        Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
        Font(R.font.jetbrains_mono_medium, FontWeight.Medium)
    )

    fun body(language: String): FontFamily = if (languageCode(language) in setOf("ja", "zh")) FontFamily.SansSerif else Inter

    fun display(language: String): FontFamily = if (isLatinDisplay(language)) Sora else body(language)
}

private fun languageCode(language: String): String = Locale.forLanguageTag(language.replace('_', '-')).language

private fun isLatinDisplay(language: String): Boolean = languageCode(language) in setOf("en", "id", "in", "pt", "tr")

/** HUD casing follows the actual locale, including Turkish dotted I; non-Latin labels keep their case. */
fun fwHudLabel(text: String, language: String): String = if (isLatinDisplay(language)) {
    text.uppercase(Locale.forLanguageTag(language.replace('_', '-')))
} else text

/** Pure role resolution allows typography to be tested without an Android text renderer. */
fun fwTextStyle(role: FwTextRole, language: String, calm: Boolean = false): TextStyle {
    val body = FwFont.body(language)
    val sizes = when (role) {
        FwTextRole.DisplayXL -> 40 to 44
        FwTextRole.DisplayL -> 32 to 38
        FwTextRole.TitleL -> 24 to 30
        FwTextRole.TitleM -> 20 to 26
        FwTextRole.TitleS, FwTextRole.NumL -> 17 to 22
        FwTextRole.BodyL -> 17 to if (calm) 26 else 24
        FwTextRole.BodyM, FwTextRole.MonoM -> 15 to 22
        FwTextRole.BodyS, FwTextRole.NumS, FwTextRole.MonoS -> 13 to 18
        FwTextRole.LabelL -> 16 to 20
        FwTextRole.LabelM -> 14 to 20
        FwTextRole.LabelS, FwTextRole.Hud -> 12 to 16
        FwTextRole.NumM -> 15 to 20
        FwTextRole.PhraseWord -> 17 to 24
    }
    val mono = role in setOf(FwTextRole.MonoM, FwTextRole.MonoS) ||
        (role == FwTextRole.Hud && languageCode(language) in setOf("en", "id", "in", "pt", "tr", "ru", "vi"))
    val family = when {
        mono -> FwFont.Mono
        role == FwTextRole.TitleL || role == FwTextRole.TitleM -> FwFont.display(language)
        else -> body
    }
    val weight = when (role) {
        FwTextRole.Hud -> if (mono) FontWeight.Medium else FontWeight.SemiBold
        FwTextRole.BodyL, FwTextRole.BodyM, FwTextRole.BodyS, FwTextRole.NumM, FwTextRole.NumS,
        FwTextRole.MonoM, FwTextRole.MonoS -> FontWeight.Normal
        else -> FontWeight.SemiBold
    }
    val numbers = role in setOf(FwTextRole.DisplayXL, FwTextRole.DisplayL, FwTextRole.NumL, FwTextRole.NumM, FwTextRole.NumS)
    val spacing = when {
        role == FwTextRole.DisplayXL -> (-0.02).em
        role == FwTextRole.Hud && isLatinDisplay(language) -> 0.08.em
        role == FwTextRole.Hud && languageCode(language) in setOf("ru", "vi") -> 0.04.em
        else -> 0.em
    }
    return TextStyle(fontFamily = family, fontWeight = weight, fontSize = sizes.first.sp, lineHeight = sizes.second.sp,
        letterSpacing = spacing, fontFeatureSettings = when { numbers -> "tnum"; mono -> "zero"; else -> null })
}

/** Reads live app locale and Calm state; it does not replace legacy typography globally. */
@Composable
fun fwTextStyle(role: FwTextRole): TextStyle = fwTextStyle(
    role, LocalConfiguration.current.locales[0].toLanguageTag(), LocalFwEffects.current.calm
)
