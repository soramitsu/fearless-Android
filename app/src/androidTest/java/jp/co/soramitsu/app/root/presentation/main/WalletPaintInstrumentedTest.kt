package jp.co.soramitsu.app.root.presentation.main

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import jp.co.soramitsu.common.compose.component.FearlessCorneredShape
import jp.co.soramitsu.common.compose.theme.fw.FwColors
import jp.co.soramitsu.common.compose.theme.fw.FwEffects
import jp.co.soramitsu.common.compose.theme.fw.FwHighContrast
import jp.co.soramitsu.common.compose.theme.fw.FwStandard
import jp.co.soramitsu.common.compose.theme.fw.FwTier
import jp.co.soramitsu.common.compose.theme.fw.fwBackdrop
import jp.co.soramitsu.common.compose.theme.fw.fwChrome
import jp.co.soramitsu.common.compose.theme.fw.fwGlass
import jp.co.soramitsu.common.compose.theme.fw.fwGlow
import jp.co.soramitsu.common.compose.theme.fw.fwNeuInset
import jp.co.soramitsu.common.compose.theme.fw.fwNeuRaised
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WalletPaintInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<PolkaswapNavigationLayoutTestActivity>()
    private val full = FwEffects(FwTier.Full, highContrast = false, reduceMotion = false)
    private val modes = linkedMapOf("full" to full, "lite" to full.copy(tier = FwTier.Lite),
        "reduced" to full.copy(tier = FwTier.Reduced), "calm" to full.copy(calm = true),
        "high-contrast" to full.copy(highContrast = true))
    private val kinds = listOf("glass", "chrome", "raised", "pressed", "inset", "error", "glow")
    private val shape = RoundedCornerShape(12.dp)

    private fun Modifier.paint(kind: String, fx: FwEffects, colors: FwColors): Modifier = when (kind) {
        "glass" -> fwGlass(fx, colors, accent = true)
        "chrome" -> fwChrome(fx, colors, shape)
        "raised" -> fwNeuRaised(fx, colors, shape)
        "pressed" -> fwNeuRaised(fx, colors, shape, pressed = true)
        "inset" -> fwNeuInset(fx, colors, focused = true)
        "error" -> fwNeuInset(fx, colors, focused = true, error = true)
        else -> fwGlow(fx, FearlessCorneredShape()).fwNeuRaised(fx, colors, FearlessCorneredShape())
    }

    @Test fun tierChangesKeepChildActionsAndBoundsAtNormalAndDoubleText() {
        var current by mutableStateOf("full")
        var scale by mutableStateOf(1f)
        val clicked = mutableListOf<String>()
        compose.setContent {
            val fx = modes.getValue(current)
            val colors = if (fx.highContrast) FwHighContrast else FwStandard
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                Column(Modifier.requiredWidth(320.dp).fwBackdrop(fx, colors).testTag("gallery").padding(16.dp)) {
                    for (kind in kinds) Box(Modifier.requiredWidth(288.dp).requiredHeight(64.dp).paint(kind, fx, colors)
                        .testTag(kind).clickable(role = Role.Button) { clicked.add(kind) }, contentAlignment = Alignment.Center) {
                        Text(kind, fontSize = 16.sp, color = colors.textPrimary)
                    }
                }
            }
        }
        val bounds = mutableMapOf<String, Rect>()
        for (font in listOf(1f, 2f)) for (mode in modes.keys) {
            compose.runOnIdle { current = mode; scale = font }
            for (kind in kinds) {
                val node = compose.onNodeWithTag(kind).assertIsDisplayed().assertHasClickAction()
                val actual = node.fetchSemanticsNode().boundsInRoot
                assertEquals(bounds.getOrPut(kind) { actual }, actual)
                node.performClick()
            }
            save("gallery-$mode-$font", "gallery")
        }
        assertEquals(List(10) { kinds }.flatten(), clicked)
    }

    @Test fun decorativeLayersNeverTintOpaqueChildContent() {
        var current by mutableStateOf("full")
        var kind by mutableStateOf("glass")
        val marker = Color(0xFFE1F7B3)
        compose.setContent {
            val fx = modes.getValue(current)
            val colors = if (fx.highContrast) FwHighContrast else FwStandard
            Box(Modifier.requiredSize(176.dp, 128.dp).background(colors.bgBase).testTag("pixels"), contentAlignment = Alignment.Center) {
                Box(Modifier.requiredSize(120.dp, 72.dp).paint(kind, fx, colors).testTag("control")) {
                    // Reaches into the top six-dp and bottom two-dp decorative strips.
                    Box(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 1.dp).background(marker))
                }
            }
        }
        for (mode in modes.keys) for (surface in kinds) {
            compose.runOnIdle { current = mode; kind = surface }
            val image = save("content-$mode-$surface", "pixels")
            val node = compose.onNodeWithTag("control").captureToImage().asAndroidBitmap()
            val inset = (compose.activity.resources.displayMetrics.density * 3).toInt()
            for (y in inset until node.height - inset) assertPixel("$mode/$surface child y=$y", marker.toArgb(), node.getPixel(node.width / 2, y))
            assertTrue(image.width > node.width)
        }
    }

    @Test fun opaqueFallbacksFocusErrorAndGlowMatchLiveEffects() {
        var current by mutableStateOf("full")
        var kind by mutableStateOf("glass")
        var circle by mutableStateOf(false)
        var glowEnabled by mutableStateOf(true)
        compose.setContent {
            val fx = modes.getValue(current)
            val colors = if (fx.highContrast) FwHighContrast else FwStandard
            Box(Modifier.requiredSize(176.dp, 128.dp).background(Color.White).testTag("surface"), contentAlignment = Alignment.Center) {
                val modifier = if (kind == "glow") {
                    val outline = if (circle) CircleShape else shape
                    Modifier.fwGlow(fx, outline, enabled = glowEnabled).fwNeuRaised(fx, colors, outline, elevated = false)
                } else Modifier.paint(kind, fx, colors)
                Box(Modifier.requiredSize(if (circle && kind == "glow") 72.dp else 120.dp, 72.dp).then(modifier).testTag("control"))
            }
        }
        for (mode in modes.keys) for (surface in listOf("glass", "chrome", "inset", "error")) {
            compose.runOnIdle { current = mode; kind = surface }
            save("surface-$mode-$surface", "surface")
            val image = compose.onNodeWithTag("control").captureToImage().asAndroidBitmap()
            val fx = modes.getValue(mode)
            val c = if (fx.highContrast) FwHighContrast else FwStandard
            val expected = when (surface) {
                "glass" -> if (fx.glass) c.glassSheen.compositeOver(c.glass.compositeOver(Color.White)) else c.surfaceRaised
                "chrome" -> if (fx.paintReduced) c.surfaceRaised else c.chromeSolid.compositeOver(Color.White)
                else -> c.surfaceSunken
            }
            assertPixel("$mode/$surface center", expected.toArgb(), image.getPixel(image.width / 2, image.height / 2))
            if (surface in listOf("inset", "error")) {
                val rimX = compose.activity.resources.displayMetrics.density.toInt()
                assertPixel("$mode/$surface rim", (if (surface == "error") c.danger else c.focus).toArgb(),
                    image.getPixel(rimX, image.height / 2))
            }
        }
        for (round in listOf(false, true)) for (mode in modes.keys) {
            compose.runOnIdle { current = mode; kind = "glow"; circle = round; glowEnabled = true }
            val enabled = save("glow-$mode-$round-on", "surface")
            compose.runOnIdle { glowEnabled = false }
            val disabled = save("glow-$mode-$round-off", "surface")
            assertEquals("$mode circle=$round", !modes.getValue(mode).glow, enabled.sameAs(disabled))
        }
    }

    @Test fun genericShapeRimsPreserveDeclaredAlphaInStandardAndHighContrast() {
        var highContrast by mutableStateOf(false)
        var kind by mutableStateOf("raised")
        compose.setContent {
            val c = if (highContrast) FwHighContrast else FwStandard
            val fx = full.copy(tier = FwTier.Reduced, highContrast = highContrast)
            val outline = FearlessCorneredShape()
            val modifier = when (kind) {
                "raised" -> Modifier.fwNeuRaised(fx, c, outline)
                "inset" -> Modifier.fwNeuInset(fx, c, outline)
                else -> Modifier.fwGlass(fx, c, outline)
            }
            Box(Modifier.requiredSize(120.dp, 72.dp).then(modifier).testTag("rim"))
        }
        for (hc in listOf(false, true)) for (surface in listOf("raised", "inset", "glass")) {
            compose.runOnIdle { highContrast = hc; kind = surface }
            val c = if (hc) FwHighContrast else FwStandard
            val background = if (surface == "inset") c.surfaceSunken else c.surfaceRaised
            val rim = if (surface == "glass" && !hc) c.strokeStrong else c.strokeControl
            val image = save("generic-rim-$surface-$hc", "rim")
            val x = (compose.activity.resources.displayMetrics.density * 0.5f).toInt()
            assertPixel("$surface highContrast=$hc", rim.compositeOver(background).toArgb(), image.getPixel(x, image.height / 2))
        }
    }

    private fun assertPixel(label: String, expected: Int, actual: Int) {
        for (shift in listOf(0, 8, 16, 24)) {
            assertTrue("$label expected=${expected.toUInt().toString(16)} actual=${actual.toUInt().toString(16)}",
                kotlin.math.abs((expected ushr shift and 255) - (actual ushr shift and 255)) <= 1)
        }
    }

    private fun save(name: String, tag: String): Bitmap {
        val label = InstrumentationRegistry.getArguments().getString("paintEvidenceLabel") ?: "unlabelled"
        val folder = compose.activity.getExternalFilesDir("paint-evidence-$label")!!.apply { mkdirs() }
        val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }
}
