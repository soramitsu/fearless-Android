package jp.co.soramitsu.app.root.presentation.main

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Paint
import android.os.LocaleList
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import jp.co.soramitsu.account.impl.presentation.about.AboutScreenContent
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.fw.FwTextRole
import jp.co.soramitsu.common.compose.theme.fw.LocalFwColors
import jp.co.soramitsu.common.compose.theme.fw.fwHudLabel
import jp.co.soramitsu.common.compose.theme.fw.fwTextStyle
import jp.co.soramitsu.common.compose.theme.fw.loadFwFontLicenses
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WalletTypographyInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<PolkaswapNavigationLayoutTestActivity>()
    private data class Mode(val language: String, val scale: Float)
    private val modes = listOf("en", "id", "ja", "pt", "ru", "tr", "vi", "zh").flatMap { language ->
        listOf(1f, 2f).map { Mode(language, it) }
    }
    private val titles = mapOf("en" to "Wallet balance", "id" to "Saldo dompet", "ja" to "ウォレット残高", "pt" to "Saldo da carteira",
        "ru" to "Баланс кошелька", "tr" to "Cüzdan bakiyesi", "vi" to "Số dư ví", "zh" to "钱包余额")

    @Test fun realBundledFontsAndOfflineNoticesLoadWithoutNetwork() {
        val context = compose.activity
        for (font in listOf(R.font.jetbrains_mono_regular, R.font.jetbrains_mono_medium)) {
            val typeface = ResourcesCompat.getFont(context, font)
            assertNotNull(typeface)
            val paint = Paint().apply { this.typeface = typeface; textSize = 24f }
            for (sample in listOf("0", "Ж", "ạ", "ư", "₽", "₿", "A")) assertTrue("Missing mono glyph $sample", paint.hasGlyph(sample))
        }
        val body = Paint().apply { typeface = ResourcesCompat.getFont(context, R.font.inter_regular); textSize = 24f }
        for (sample in listOf("≈", "▲", "▼", "−", "→", "₹", "₺", "₩", "₫", "\u202f", "Ж", "ạ")) {
            assertTrue("Missing body glyph $sample", body.hasGlyph(sample))
        }
        val licenses = loadFwFontLicenses(context.resources)
        assertTrue(licenses.size == 3 && licenses.all { it.text.contains("SIL OPEN FONT LICENSE Version 1.1") })
    }

    @Test fun semanticTypeWrapsWithoutTruncationAtEveryShippedLocaleAndTextSize() {
        var mode by mutableStateOf(modes.first())
        val overflow = mutableMapOf<String, Boolean>()
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply { setLocales(LocaleList(Locale.forLanguageTag(mode.language))) }
            CompositionLocalProvider(LocalConfiguration provides configuration,
                LocalDensity provides Density(LocalDensity.current.density, mode.scale)) {
                FearlessTheme {
                    val colors = LocalFwColors.current
                    Column(Modifier.requiredWidth(320.dp).background(colors.bgBase).testTag("fw-type-fixture")
                        .verticalScroll(rememberScrollState()).padding(16.dp)) {
                        val samples = listOf(FwTextRole.TitleL to titles.getValue(mode.language),
                            FwTextRole.DisplayL to "₹ 12,345,678.90", FwTextRole.BodyM to "≈ − ₽ ₹ ₺ ₩ ₫ € £ ¥ ₿ · Ж ạ ư",
                            FwTextRole.Hud to fwHudLabel(titles.getValue(mode.language), mode.language),
                            FwTextRole.MonoM to "0x0123456789abcdef0123456789abcdef01234567",
                            FwTextRole.PhraseWord to "abandon ability able about above absent")
                        for ((role, value) in samples) {
                            Text(value, style = fwTextStyle(role), color = colors.textPrimary,
                                modifier = Modifier.testTag("fw-type-$role").padding(bottom = 12.dp),
                                onTextLayout = { overflow["${mode.language}-${mode.scale}-$role"] = it.hasVisualOverflow })
                        }
                    }
                }
            }
        }
        for (next in modes) {
            compose.runOnIdle { mode = next }
            compose.onNodeWithTag("fw-type-TitleL").performScrollTo().assertIsDisplayed()
            save("type-${next.language}-${next.scale}-top", "fw-type-fixture")
            compose.onNodeWithTag("fw-type-PhraseWord").performScrollTo().assertIsDisplayed()
            save("type-${next.language}-${next.scale}-bottom", "fw-type-fixture")
            compose.runOnIdle {
                assertFalse("Clipped type: $overflow", overflow.filterKeys { it.startsWith("${next.language}-${next.scale}-") }.values.any { it })
            }
        }
        assertTrue(overflow.size == modes.size * 6)
    }

    @Test fun aboutOpensScrollableOfflineLicensesAndCloseStaysReachable() {
        var mode by mutableStateOf(modes.first())
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply { setLocales(LocaleList(Locale.forLanguageTag(mode.language))) }
            val context = compose.activity.createConfigurationContext(configuration)
            CompositionLocalProvider(LocalConfiguration provides configuration,
                androidx.compose.ui.platform.LocalContext provides context,
                LocalDensity provides Density(LocalDensity.current.density, mode.scale)) {
                FearlessTheme { AboutScreenContent(emptyList()) {} }
            }
        }
        for (next in modes) {
            compose.runOnIdle { mode = next }
            val configuration = Configuration(compose.activity.resources.configuration).apply {
                setLocales(LocaleList(Locale.forLanguageTag(next.language)))
            }
            val title = compose.activity.createConfigurationContext(configuration).getString(R.string.fw_font_licenses)
            compose.onNodeWithText(title).performScrollTo().performClick()
            val close = compose.activity.createConfigurationContext(configuration).getString(R.string.common_close)
            compose.onNodeWithTag("fw-font-licenses-title").assertTextEquals(title)
            compose.onNodeWithTag("fw-font-licenses-close").assertIsDisplayed().assertHasClickAction().assertTextEquals(close)
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("fw-font-licenses-title").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.isNotEmpty())
            assertEquals(next.scale, layouts.single().layoutInput.density.fontScale, 0.001f)
            save("licenses-${next.language}-${next.scale}-top", "fw-font-licenses-dialog")
            val layout = layouts.single()
            assertFalse("$next title overflow: size=${layout.size}, paragraph=${layout.multiParagraph.width}x${layout.multiParagraph.height}, " +
                "width=${layout.didOverflowWidth}, height=${layout.didOverflowHeight}, lines=${layout.lineCount}, constraints=${layout.layoutInput.constraints}",
                layout.hasVisualOverflow)
            compose.onNodeWithTag("fw-font-licenses-list").performScrollToNode(hasTestTag("fw-font-license-JetBrains Mono"))
            compose.onNodeWithTag("fw-font-license-JetBrains Mono").assertIsDisplayed()
            compose.onNodeWithTag("fw-font-licenses-list").performScrollToNode(hasTestTag("fw-font-licenses-end"))
            compose.onNodeWithTag("fw-font-licenses-end").assertIsDisplayed()
            save("licenses-${next.language}-${next.scale}-end", "fw-font-licenses-dialog")
            compose.onNodeWithTag("fw-font-licenses-close").assertIsDisplayed().performClick()
            compose.onNodeWithTag("fw-font-licenses-dialog").assertDoesNotExist()
        }
    }

    private fun save(name: String, tag: String) {
        val label = InstrumentationRegistry.getArguments().getString("fontEvidenceLabel") ?: "current"
        require(label.matches(Regex("[a-z0-9-]+")))
        val folder = compose.activity.getExternalFilesDir("font-evidence-$label")!!.apply { mkdirs() }
        compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap().let { bitmap ->
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
