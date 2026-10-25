package jp.co.soramitsu.app.root.presentation.main

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.ui.text.style.TextOverflow
import jp.co.soramitsu.common.compose.theme.fw.FwTextRole
import jp.co.soramitsu.common.compose.theme.fw.fwTextStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.account.impl.domain.recovery.WalletPinRecovery
import jp.co.soramitsu.account.impl.domain.recovery.WalletRecoveryInventory
import jp.co.soramitsu.account.impl.presentation.pincode.PinCodeViewModel
import jp.co.soramitsu.account.impl.presentation.pincode.PinRecoveryScreen
import jp.co.soramitsu.account.impl.presentation.recovery.RecoveryCenterScreen
import jp.co.soramitsu.account.impl.presentation.recovery.RecoveryTarget
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.BannerWalletRecovery
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.backgroundBlack
import jp.co.soramitsu.common.compose.theme.fw.FwEffects
import jp.co.soramitsu.common.compose.theme.fw.FwHighContrast
import jp.co.soramitsu.common.compose.theme.fw.FwStandard
import jp.co.soramitsu.common.compose.theme.fw.FwTier
import jp.co.soramitsu.common.compose.theme.fw.FwRecoveryAppearance
import jp.co.soramitsu.common.compose.theme.fw.FwRecoveryButton
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import jp.co.soramitsu.common.compose.theme.fw.FwSurface
import jp.co.soramitsu.common.compose.theme.fw.LocalFwColors
import jp.co.soramitsu.common.compose.theme.fw.LocalFwEffects
import jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryKind
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Presentation-only public fixtures; callbacks record their exact target and never write keys. */
@OptIn(ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
class WalletRecoveryAppearanceInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<PolkaswapNavigationLayoutTestActivity>()
    private enum class Screen { Banner, Center, Pin }
    private data class Mode(val language: String, val scale: Float, val screen: Screen, val effects: String)
    private val effects = linkedMapOf(
        "full" to FwEffects(FwTier.Full, false, false),
        "lite" to FwEffects(FwTier.Lite, false, false),
        "reduced" to FwEffects(FwTier.Reduced, false, false),
        "contrast" to FwEffects(FwTier.Full, true, false),
        "still" to FwEffects(FwTier.Full, false, true)
    )

    @Test fun everyLocaleAndSizeKeepsRecoveryActionsAndCapturedRollbackBaseline() {
        val arguments = InstrumentationRegistry.getArguments()
        val selectedEffects = arguments.getString("recoveryAppearanceEffects")?.split(",")?.toSet() ?: effects.keys
        val selectedScreens = arguments.getString("recoveryAppearanceScreens")?.split(",")?.toSet() ?: Screen.entries.map { it.name }.toSet()
        check(selectedEffects.isNotEmpty() && effects.keys.containsAll(selectedEffects))
        check(selectedScreens.isNotEmpty() && Screen.entries.map { it.name }.containsAll(selectedScreens))
        val modes = listOf("en", "id", "ja", "pt", "ru", "tr", "vi", "zh").flatMap { language ->
            listOf(1f, 2f).flatMap { scale -> Screen.entries.filter { it.name in selectedScreens }.flatMap { screen ->
                selectedEffects.map { Mode(language, scale, screen, it) }
            } }
        }
        var mode by mutableStateOf(modes.first())
        val calls = mutableListOf<String>()
        val target = RecoveryTarget.from(7, WalletSecretScope.Ethereum)
        val center = WalletRecoveryInventory.State(listOf(WalletRecoveryInventory.Wallet(
            7, "Public recovery fixture", listOf(WalletRecoveryInventory.Item(target, WalletRecoveryKind.MISSING_KEY)),
            listOf(WalletRecoveryInventory.Advisory(WalletSecretScope.Ton, true)), false
        )), 0)
        val pin = PinCodeViewModel.RecoveryState(loading = false, strongBiometric = true, deviceCredential = true,
            choices = listOf(WalletPinRecovery.Choice("Public recovery fixture", target)))
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply { setLocales(LocaleList(Locale.forLanguageTag(mode.language))) }
            val context = compose.activity.createConfigurationContext(configuration)
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalContext provides context,
                LocalDensity provides Density(LocalDensity.current.density, mode.scale)) {
                FearlessTheme {
                    val fx = effects.getValue(mode.effects)
                    CompositionLocalProvider(LocalFwEffects provides fx, LocalFwColors provides if (fx.highContrast) FwHighContrast else FwStandard) {
                        key(mode) {
                            Box(Modifier.requiredWidth(320.dp).requiredHeight(640.dp).background(backgroundBlack).testTag("recovery-appearance")) {
                                when (mode.screen) {
                                    Screen.Banner -> Column(Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
                                        BannerWalletRecovery { calls += "banner" }
                                    }
                                    Screen.Center -> Scaffold { padding -> Box(Modifier.padding(padding)) {
                                        RecoveryCenterScreen(center, { calls += "back" },
                                        { selected, import -> assertEquals(target, selected); calls += import.name }, {}, { calls += "export" },
                                        { id, chain -> assertEquals(7L, id); assertEquals(null, chain); calls += "backup" },
                                        archivedData = { calls += "archive" }, previousSecureStorage = { calls += "previous" })
                                    } }
                                    Screen.Pin -> PinRecoveryScreen(pin, { calls += "biometric" }, { calls += "view-only" },
                                        { selected, import -> assertEquals(target, selected); calls += import.name }, {})
                                }
                            }
                        }
                    }
                }
            }
        }
        val layouts = JSONArray()
        for (next in modes) {
            compose.runOnIdle { calls.clear(); mode = next }
            save(next, "top")
            if (FwRestyle.isEnabled(FwSurface.Recovery)) layouts.put(readLayouts(next))
            val actions = when (next.screen) {
                Screen.Banner -> listOf("wallet_recovery_fix_now" to "banner")
                Screen.Center -> listOf("wallet_recovery_use_phrase" to ImportMode.MnemonicPhrase.name,
                    "wallet_recovery_use_seed" to ImportMode.RawSeed.name, "wallet_recovery_use_json" to ImportMode.Json.name,
                    "wallet_recovery_backup_key" to "backup", "wallet_keyloss_retry" to "previous",
                    "wallet_archive_open" to "archive", "wallet_emergency_open" to "export",
                    "wallet_recovery_keep_view_only" to "back")
                Screen.Pin -> listOf("pin_recovery_biometric" to "biometric",
                    "wallet_recovery_use_phrase" to ImportMode.MnemonicPhrase.name,
                    "wallet_recovery_use_seed" to ImportMode.RawSeed.name, "wallet_recovery_use_json" to ImportMode.Json.name,
                    "pin_recovery_view_only" to "view-only")
            }
            val configuration = Configuration(compose.activity.resources.configuration).apply { setLocales(LocaleList(Locale.forLanguageTag(next.language))) }
            val resources = compose.activity.createConfigurationContext(configuration).resources
            for ((label, _) in actions) {
                // Resolve the installed app's stable name: resource integers can move between artifacts.
                val resourceId = resources.getIdentifier(label, "string", compose.activity.packageName)
                check(resourceId != 0) { "Missing installed recovery label: $label" }
                val node = compose.onNodeWithText(resources.getString(resourceId))
                node.performScrollTo()
                node.assertIsDisplayed().assertHasClickAction().performClick()
            }
            compose.runOnIdle { assertEquals("Actions changed for $next", actions.map { it.second }, calls.toList()) }
            if (next.screen != Screen.Banner || FwRestyle.isEnabled(FwSurface.Recovery)) save(next, "bottom")
        }
        File(directory(), "text-layouts.json").writeText(layouts.toString(2))
    }

    @Test fun recoveryButtonsKeepFocusPressAndActionWithoutDecorativeEffects() {
        check(FwRestyle.isEnabled(FwSurface.Recovery)) { "This method qualifies the v1 Recovery artifact." }
        var current by mutableStateOf("full")
        var scale by mutableStateOf(1f)
        val requester = FocusRequester()
        var clicks = 0
        var inputModeManager: InputModeManager? = null
        compose.setContent {
            inputModeManager = LocalInputModeManager.current
            val fx = effects.getValue(current)
            val c = if (fx.highContrast) FwHighContrast else FwStandard
            CompositionLocalProvider(LocalFwEffects provides fx, LocalFwColors provides c,
                LocalDensity provides Density(LocalDensity.current.density, scale)) {
                FwRecoveryAppearance {
                    val calm = LocalFwEffects.current
                    check(calm.calm && !calm.glass && !calm.glow && !calm.neuShadows && !calm.aurora)
                    check(calm.highContrast == fx.highContrast && calm.reduceMotion == fx.reduceMotion)
                    Box(Modifier.requiredWidth(320.dp).requiredHeight(240.dp).background(c.bgBase)
                        .testTag("button-host").padding(16.dp), contentAlignment = Alignment.Center) {
                        FwRecoveryButton("Restore from your recovery phrase", { clicks++ },
                            Modifier.focusRequester(requester).testTag("action"))
                    }
                }
            }
        }
        for (font in listOf(1f, 2f)) for (effect in effects.keys) {
            compose.runOnIdle { scale = font; current = effect }
            val node = compose.onNodeWithTag("action").assertIsDisplayed().assertHasClickAction()
            val before = node.fetchSemanticsNode().boundsInRoot
            val density = compose.activity.resources.displayMetrics.density
            assertTrue(before.height >= 48 * density)
            compose.runOnIdle {
                check(inputModeManager!!.requestInputMode(InputMode.Keyboard))
                requester.requestFocus()
            }
            node.assertIsFocused()
            val focused = capture("button-$font-$effect-focus", "button-host")
            val host = compose.onNodeWithTag("button-host").fetchSemanticsNode().boundsInRoot
            val x = ((before.center.x - host.left)).toInt()
            val y = ((before.top - host.top) - 3 * density).toInt()
            assertPixel("outside focus ring", FwStandard.focus, focused.getPixel(x, y))
            assertPixel("focus gap", FwStandard.bgBase, focused.getPixel(x, (before.top - host.top - density).toInt()))
            compose.mainClock.autoAdvance = false
            node.performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(1000)
            val pressed = capture("button-$font-$effect-pressed", "button-host")
            val fillX = (before.left - host.left + 14 * density).toInt()
            val fillY = (before.center.y - host.top).toInt()
            assertPixel("declared secondary fill while focused", FwStandard.surfaceRaised2, focused.getPixel(fillX, fillY))
            assertPixel("declared secondary pressed fill", FwStandard.surfacePressed, pressed.getPixel(fillX, fillY))
            assertEquals(before, node.fetchSemanticsNode().boundsInRoot)
            assertTrue("A press must have a visible state", !focused.sameAs(pressed))
            node.performTouchInput { up() }
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
        }
        assertEquals(10, clicks)
    }

    private fun readLayouts(mode: Mode): JSONObject {
        val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
        val results = JSONArray()
        val count = nodes.fetchSemanticsNodes().size
        assertTrue("Missing rendered text for $mode", count > 0)
        for (index in 0 until count) {
            val layouts = mutableListOf<TextLayoutResult>()
            nodes[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.isNotEmpty())
            for (layout in layouts) {
                if (layout.hasVisualOverflow) {
                    val lineBounds = JSONArray()
                    for (line in 0 until layout.lineCount) lineBounds.put(JSONObject()
                        .put("left", layout.getLineLeft(line)).put("right", layout.getLineRight(line))
                        .put("top", layout.getLineTop(line)).put("bottom", layout.getLineBottom(line))
                        .put("ellipsis", layout.isLineEllipsized(line)))
                    val diagnostic = JSONObject().put("mode", mode.toString()).put("text", layout.layoutInput.text.text)
                        .put("width", layout.size.width).put("height", layout.size.height)
                        .put("paragraphWidth", layout.multiParagraph.width).put("paragraphHeight", layout.multiParagraph.height)
                        .put("overflowWidth", layout.didOverflowWidth).put("overflowHeight", layout.didOverflowHeight)
                        .put("exceededMaxLines", layout.multiParagraph.didExceedMaxLines)
                        .put("constraints", layout.layoutInput.constraints.toString()).put("lines", lineBounds)
                    File(directory(), "overflow-${mode.screen}-${mode.language}-${mode.scale}-${mode.effects}.json")
                        .writeText(diagnostic.toString(2))
                }
                val overflow = hasRenderedOverflow(layout)
                assertTrue("Rendered text clipped at $mode: ${layout.layoutInput.text}; size=${layout.size}, " +
                    "paragraph=${layout.multiParagraph.width}x${layout.multiParagraph.height}, " +
                    "width=${layout.didOverflowWidth}, height=${layout.didOverflowHeight}", !overflow)
                results.put(JSONObject().put("text", layout.layoutInput.text.text)
                    .put("size", layout.layoutInput.style.fontSize.value).put("lineHeight", layout.layoutInput.style.lineHeight.value)
                    .put("lines", layout.lineCount).put("width", layout.size.width).put("height", layout.size.height)
                    .put("overflow", overflow).put("reportedOverflow", layout.hasVisualOverflow))
            }
        }
        return JSONObject().put("mode", mode.toString()).put("texts", results)
    }

    @Test fun renderedLineGateRejectsActualClippingWithoutRejectingACompleteShortLine() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1f)) {
                FwRecoveryAppearance {
                    Column(Modifier.requiredWidth(224.dp).requiredHeight(240.dp).background(LocalFwColors.current.bgBase)
                        .testTag("overflow-controls")) {
                        val style = fwTextStyle(FwTextRole.TitleS, "en", calm = true)
                        Text("A key needs attention", Modifier.testTag("complete"), style = style)
                        Text("A key needs attention", Modifier.requiredWidth(48.dp).testTag("ellipsis"),
                            style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Line one\nLine two", Modifier.requiredHeight(8.dp).testTag("height-clipped"), style = style)
                    }
                }
            }
        }
        val results = JSONArray()
        for ((tag, expected) in listOf("complete" to false, "ellipsis" to true, "height-clipped" to true)) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertEquals(tag, expected, hasRenderedOverflow(layout))
            if (tag == "complete") assertTrue("The measured-width native counterexample must be exercised", layout.didOverflowWidth)
            results.put(JSONObject().put("tag", tag).put("expectedOverflow", expected)
                .put("renderedOverflow", hasRenderedOverflow(layout)).put("reportedOverflow", layout.hasVisualOverflow)
                .put("width", layout.size.width).put("height", layout.size.height)
                .put("paragraphWidth", layout.multiParagraph.width).put("paragraphHeight", layout.multiParagraph.height))
        }
        File(directory(), "overflow-controls.json").writeText(results.toString(2))
        capture("overflow-controls", "overflow-controls")
    }

    private fun hasRenderedOverflow(layout: TextLayoutResult): Boolean {
        // Native semantics can return a wide paragraph constraint for a shorter measured
        // text node. Integer node bounds permit at most half a pixel of rounding.
        val linesFit = (0 until layout.lineCount).all { line ->
            layout.getLineLeft(line) >= -0.5f && layout.getLineRight(line) <= layout.size.width + 0.5f &&
                layout.getLineTop(line) >= -0.5f && layout.getLineBottom(line) <= layout.size.height + 0.5f &&
                !layout.isLineEllipsized(line)
        }
        return layout.didOverflowHeight || layout.multiParagraph.didExceedMaxLines || !linesFit ||
            layout.getLineEnd(layout.lineCount - 1) != layout.layoutInput.text.length
    }

    private fun assertPixel(label: String, expected: Color, actual: Int) {
        for (shift in listOf(0, 8, 16, 24)) assertTrue(label,
            kotlin.math.abs((expected.toArgb() ushr shift and 255) - (actual ushr shift and 255)) <= 1)
    }

    private fun directory(): File {
        val label = InstrumentationRegistry.getArguments().getString("recoveryAppearanceEvidenceLabel") ?: "default"
        return InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("recovery-appearance-$label")!!.apply { mkdirs() }
    }

    private fun capture(name: String, tag: String): Bitmap {
        compose.waitForIdle()
        val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        File(directory(), "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        return bitmap
    }

    private fun save(mode: Mode, position: String) {
        capture("${mode.screen}-${mode.language}-${mode.scale}-${mode.effects}-$position", "recovery-appearance")
    }
}
