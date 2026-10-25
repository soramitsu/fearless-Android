package jp.co.soramitsu.app.root.presentation.main

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import jp.co.soramitsu.common.compose.component.FearlessProgress
import jp.co.soramitsu.common.compose.component.FullScreenLoading
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.fw.FwEffects
import jp.co.soramitsu.common.compose.theme.fw.FwHighContrast
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import jp.co.soramitsu.common.compose.theme.fw.FwStandard
import jp.co.soramitsu.common.compose.theme.fw.FwSurface
import jp.co.soramitsu.common.compose.theme.fw.FwTier
import jp.co.soramitsu.common.compose.theme.fw.LocalFwColors
import jp.co.soramitsu.common.compose.theme.fw.LocalFwEffects
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WalletLoadingAppearanceInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<PolkaswapNavigationLayoutTestActivity>()
    private data class Mode(val locale: String, val scale: Float, val effects: String)
    private val effects = linkedMapOf(
        "full" to FwEffects(FwTier.Full, false, false),
        "lite" to FwEffects(FwTier.Lite, false, false),
        "reduced" to FwEffects(FwTier.Reduced, false, false),
        "contrast" to FwEffects(FwTier.Full, true, false),
        "still" to FwEffects(FwTier.Full, false, true)
    )

    @Test fun progressFitsExistingOneHundredTwentyDpHostAcrossLocalesSizesAndEffects() {
        val modes = listOf("en", "id", "ja", "pt", "ru", "tr", "vi", "zh").flatMap { language ->
            listOf(1f, 2f).flatMap { scale -> effects.keys.map { Mode(language, scale, it) } }
        }
        var mode by mutableStateOf(modes.first())
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val config = Configuration(LocalConfiguration.current).apply { setLocales(LocaleList(Locale.forLanguageTag(mode.locale))) }
            CompositionLocalProvider(LocalConfiguration provides config,
                LocalContext provides compose.activity.createConfigurationContext(config),
                LocalDensity provides Density(LocalDensity.current.density, mode.scale)) {
                FearlessTheme {
                    val fx = effects.getValue(mode.effects)
                    CompositionLocalProvider(LocalFwEffects provides fx, LocalFwColors provides if (fx.highContrast) FwHighContrast else FwStandard) {
                        Box(Modifier.requiredWidth(320.dp).requiredHeight(120.dp).background(FwStandard.bgBase).testTag("host")) {
                            FearlessProgress(Modifier.align(Alignment.Center).testTag("indicator"))
                        }
                    }
                }
            }
        }
        val rows = JSONArray()
        for (next in modes) {
            compose.runOnUiThread { mode = next }
            compose.mainClock.advanceTimeBy(64)
            compose.waitForIdle()
            val host = compose.onNodeWithTag("host").fetchSemanticsNode().boundsInRoot
            val indicator = compose.onNodeWithTag("indicator").fetchSemanticsNode().boundsInRoot
            assertTrue("Indicator must stay inside the existing120dp Manage Assets host: $next",
                indicator.left >= host.left && indicator.top >= host.top && indicator.right <= host.right && indicator.bottom <= host.bottom)
            if (FwRestyle.isEnabled(FwSurface.Loading)) {
                val config = Configuration(compose.activity.resources.configuration).apply { setLocales(LocaleList(Locale.forLanguageTag(next.locale))) }
                val resources = compose.activity.createConfigurationContext(config).resources
                val id = resources.getIdentifier("fw_loading_in_progress", "string", compose.activity.packageName)
                val label = resources.getString(id)
                val node = compose.onNodeWithText(label, useUnmergedTree = true).assertIsDisplayed()
                val layouts = mutableListOf<TextLayoutResult>()
                node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
                assertEquals(1, layouts.size)
                val layout = layouts.single()
                val bounds = node.fetchSemanticsNode().boundsInRoot
                assertFalse("$next clipped progress text", layout.didOverflowHeight)
                assertTrue("$next lost text", layout.getLineEnd(layout.lineCount - 1) == label.length)
                for (line in 0 until layout.lineCount) {
                    assertFalse("$next ellipsis", layout.isLineEllipsized(line))
                    assertTrue("$next left overflow", layout.getLineLeft(line) >= -0.5f)
                    assertTrue("$next right overflow", layout.getLineRight(line) <= bounds.width + 0.5f)
                    assertTrue("$next bottom overflow", layout.getLineBottom(line) <= bounds.height + 0.5f)
                }
                rows.put(JSONObject().put("mode", next.toString()).put("label", label).put("lines", layout.lineCount)
                    .put("width", indicator.width).put("height", indicator.height))
            }
            save("progress-${next.locale}-${next.scale}-${next.effects}", "host")
        }
        File(directory(), "text-layouts.json").writeText(rows.toString(2))
    }

    @Test fun busyOverlayRetainsSharpUnderlyingEdgesAndExactContentWhenFinished() {
        compose.mainClock.autoAdvance = false
        var busy by mutableStateOf(false)
        compose.setContent {
            FearlessTheme {
                CompositionLocalProvider(LocalFwEffects provides FwEffects(FwTier.Full, false, true)) {
                    Box(Modifier.requiredWidth(320.dp).requiredHeight(320.dp).testTag("overlay")) {
                        FullScreenLoading(busy) {
                            Canvas(Modifier.fillMaxSize()) {
                                val width = 16.dp.toPx()
                                for (stripe in 0 until 20) drawRect(
                                    if (stripe % 2 == 0) FwStandard.qrField else FwStandard.bgBase,
                                    Offset(stripe * width, 0f), Size(width, size.height)
                                )
                            }
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(64)
        val before = save("overlay-before", "overlay")
        compose.runOnUiThread { busy = true }
        compose.mainClock.advanceTimeBy(64)
        val during = save("overlay-busy", "overlay")
        if (FwRestyle.isEnabled(FwSurface.Loading)) {
            val stripe = during.width / 20
            for (x in 0 until during.width) {
                val color = if ((x / stripe) % 2 == 0) FwStandard.qrField else FwStandard.bgBase
                val expected = FwStandard.scrim.copy(alpha = 0.8f).compositeOver(color).toArgb()
                val actual = during.getPixel(x, 12)
                for (shift in listOf(0, 8, 16, 24)) {
                    assertTrue("Blurred or wrong scrim at column$x channel$shift",
                        kotlin.math.abs(((actual ushr shift) and 255) - ((expected ushr shift) and 255)) <= 1)
                }
            }
        }
        compose.runOnUiThread { busy = false }
        compose.mainClock.advanceTimeBy(64)
        val after = save("overlay-after", "overlay")
        assertTrue("Loading must not alter the content after finishing", before.sameAs(after))
    }

    private fun directory(): File {
        val label = InstrumentationRegistry.getArguments().getString("loadingEvidenceLabel") ?: "appearance"
        check(label.matches(Regex("[a-zA-Z0-9_-]+")))
        return File(compose.activity.getExternalFilesDir(null), "loading-$label").apply { mkdirs() }
    }

    private fun save(name: String, tag: String): Bitmap {
        compose.waitForIdle()
        val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        File(directory(), "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        return bitmap
    }
}
