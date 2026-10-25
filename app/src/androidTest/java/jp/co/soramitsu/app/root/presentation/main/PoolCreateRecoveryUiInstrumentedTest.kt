package jp.co.soramitsu.app.root.presentation.main

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.GradientIconState
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.backgroundBlack
import jp.co.soramitsu.staking.impl.presentation.confirm.pool.join.PoolJoinRecoveryScreen
import jp.co.soramitsu.staking.impl.presentation.confirm.pool.join.ConfirmJoinPoolScreenViewState
import jp.co.soramitsu.staking.impl.presentation.confirm.pool.join.PoolJoinRecoveryViewState
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/** Actual content used by create recovery; public fields and local callbacks, no Fragment navigation or signing claim. */
@RunWith(AndroidJUnit4::class)
class PoolCreateRecoveryUiInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<PolkaswapNavigationLayoutTestActivity>()
    private data class Mode(val language: String, val scale: Float, val phase: String)
    private val messages = linkedMapOf(
        "pending" to R.string.pool_join_recovery_pending,
        "checking" to R.string.pool_join_recovery_checking,
        "miss" to R.string.pool_join_recovery_not_found,
        "unavailable" to R.string.pool_join_recovery_unavailable,
        "finalized" to R.string.pool_join_recovery_finalized
    )
    private val details = jp.co.soramitsu.staking.impl.data.repository.PoolJoinJournal.CreateDetails(
        "Original\n池", "5F3sa2TJAWMqDhXG6jhV4N8ko9SxwGy8TpaNS1repo5EYjQXf", "5GrwvaEF5zXb26Fz9rcQpDWS64xHn6i9nKPrRRX3Yk2Y3Y5C")
    private val hash = "0x" + "abcdef0123456789".repeat(4)
    private val address = "5FHneW46xGXgs5mUiveU4sbTyGBzmstUQspZC92UhjJM694ty"

    @Test fun centeredTextGateAcceptsCompleteLabelsAndRejectsClipping() {
        compose.setContent {
            FearlessTheme {
                Column(Modifier.requiredWidth(320.dp).requiredHeight(320.dp)) {
                    AccentButton(text = "Check recent blocks", modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), onClick = {})
                    androidx.compose.material.Text("Clipped centered title", Modifier.requiredWidth(48.dp).testTag("ellipsis"),
                        textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    androidx.compose.material.Text("Line one\nLine two", Modifier.requiredHeight(8.dp).testTag("height"), textAlign = TextAlign.Center)
                }
            }
        }
        val records = JSONArray()
        for ((name, expected) in listOf("complete" to false, "ellipsis" to true, "height" to true)) {
            val values = mutableListOf<TextLayoutResult>()
            val node = if (name == "complete") compose.onNodeWithText("Check recent blocks", useUnmergedTree = true) else compose.onNodeWithTag(name)
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(values) }
            val layout = values.single()
            assertEquals(name, expected, renderedOverflow(layout))
            if (name == "complete") assertTrue("Exercise the centered paragraph/node-width counterexample", layout.hasVisualOverflow)
            records.put(JSONObject().put("name", name).put("expected", expected).put("renderedOverflow", renderedOverflow(layout))
                .put("reportedOverflow", layout.hasVisualOverflow).put("width", layout.size.width).put("paragraphWidth", layout.multiParagraph.width))
        }
        File(directory(), "text-gate-controls.json").writeText(records.toString(2))
    }

    @Test fun allLocalesAndSizesKeepOriginalDetailsAndExactRecoveryActions() {
        val modes = listOf("en", "id", "ja", "pt", "ru", "tr", "vi", "zh").flatMap { language ->
            listOf(1f, 2f).flatMap { scale -> messages.keys.map { Mode(language, scale, it) } }
        }
        var mode by mutableStateOf(modes.first())
        val calls = mutableListOf<String>()
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply { setLocales(LocaleList(Locale.forLanguageTag(mode.language))) }
            val context = compose.activity.createConfigurationContext(configuration)
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalContext provides context,
                LocalDensity provides Density(LocalDensity.current.density, mode.scale)) {
                FearlessTheme {
                    key(mode) {
                        Box(Modifier.requiredWidth(320.dp).requiredHeight(640.dp).background(backgroundBlack).testTag("pool-recovery-host")) {
                            val recovery = PoolJoinRecoveryViewState(context.getString(messages.getValue(mode.phase)), "37 LOCAL", "8",
                                "Local public fixture", address, hash, mode.phase in listOf("pending", "miss"), mode.phase == "miss", mode.phase == "finalized", create = details, returnLabelRes = R.string.pool_recovery_return_earn)
                            PoolJoinRecoveryScreen(recovery,
                                { calls += "back" }, { calls += if (it) "older" else "recent" }, { calls += "return" })
                        }
                    }
                }
            }
        }
        val evidence = JSONArray()
        for (next in modes) {
            compose.runOnIdle { calls.clear(); mode = next }
            compose.waitForIdle()
            save(next, "top")
            val layouts = textLayouts(next)
            compose.onNodeWithText("99 LOCAL").assertDoesNotExist()
            // Each full identifier is present in the actual text semantics, with measured wrapping.
            compose.onNodeWithText(address).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(details.poolName).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(details.nominatorAddress).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(details.stateTogglerAddress).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(hash).performScrollTo().assertIsDisplayed()
            val expected = mutableListOf<String>()
            if (next.phase == "finalized") {
                action(next, R.string.pool_recovery_return_earn)
                expected += "return"
                compose.onNodeWithText(text(next, R.string.pool_join_recovery_recent)).assertDoesNotExist()
            } else {
                val recent = compose.onNodeWithText(text(next, R.string.pool_join_recovery_recent)).performScrollTo()
                if (next.phase in listOf("pending", "miss")) {
                    action(next, R.string.pool_join_recovery_recent)
                    expected += "recent"
                } else recent.assertIsNotEnabled()
                if (next.phase == "miss") {
                    action(next, R.string.pool_join_recovery_older)
                    expected += "older"
                } else compose.onNodeWithText(text(next, R.string.pool_join_recovery_older)).assertDoesNotExist()
            }
            save(next, "bottom")
            val back = compose.onNodeWithContentDescription(text(next, R.string.ux_back)).performScrollTo()
            back.assertHasClickAction().performTouchInput { click() }
            expected += "back"
            compose.runOnIdle { assertEquals("Exact local action list for $next", expected, calls.toList()) }
            evidence.put(JSONObject().put("mode", next.toString()).put("layouts", layouts).put("actions", JSONArray(expected)))
            File(directory(), "layouts-actions.json").writeText(evidence.toString(2))
        }
        assertEquals(80, evidence.length())
    }

    private fun action(mode: Mode, id: Int) {
        val node = compose.onNodeWithText(text(mode, id)).performScrollTo().assertIsDisplayed().assertHasClickAction()
        val bounds = node.fetchSemanticsNode().boundsInRoot
        assertTrue("48dp target for $mode", bounds.height + .1f >= 48 * compose.activity.resources.displayMetrics.density)
        node.performTouchInput { click() }
    }

    private fun textLayouts(mode: Mode): JSONArray {
        val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
        val result = JSONArray()
        for (index in nodes.fetchSemanticsNodes().indices) {
            val values = mutableListOf<TextLayoutResult>()
            nodes[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(values) }
            for (layout in values) {
                val entry = JSONObject().put("text", layout.layoutInput.text.text).put("width", layout.size.width)
                    .put("height", layout.size.height).put("lines", layout.lineCount).put("overflow", renderedOverflow(layout))
                    .put("reportedOverflow", layout.hasVisualOverflow).put("paragraphWidth", layout.multiParagraph.width)
                    .put("alignment", layout.layoutInput.style.textAlign.toString())
                result.put(entry)
                if (renderedOverflow(layout)) File(directory(), "failure.json").writeText(JSONObject().put("mode", mode.toString()).put("layout", entry).toString(2))
                assertFalse("Text overflow for $mode: ${layout.layoutInput.text.text}", renderedOverflow(layout))
                for (line in 0 until layout.lineCount) assertFalse(layout.isLineEllipsized(line))
            }
        }
        assertTrue(result.length() >= 10)
        return result
    }

    private fun renderedOverflow(layout: TextLayoutResult): Boolean {
        // Centered text retains the wider paragraph constraint in native semantics even when
        // the node measures to its glyph width. Compare in the measured node's coordinates.
        val offset = if (layout.layoutInput.style.textAlign == TextAlign.Center)
            (layout.multiParagraph.width - layout.size.width) / 2f else 0f
        val linesFit = (0 until layout.lineCount).all { line ->
            layout.getLineLeft(line) - offset >= -.5f && layout.getLineRight(line) - offset <= layout.size.width + .5f &&
                layout.getLineTop(line) >= -.5f && layout.getLineBottom(line) <= layout.size.height + .5f &&
                !layout.isLineEllipsized(line)
        }
        return layout.didOverflowHeight || layout.multiParagraph.didExceedMaxLines || !linesFit ||
            layout.getLineEnd(layout.lineCount - 1) != layout.layoutInput.text.length
    }

    private fun text(mode: Mode, id: Int): String {
        val config = Configuration(compose.activity.resources.configuration).apply { setLocales(LocaleList(Locale.forLanguageTag(mode.language))) }
        return compose.activity.createConfigurationContext(config).getString(id)
    }

    private fun directory(): File {
        val label = InstrumentationRegistry.getArguments().getString("poolRecoveryEvidence") ?: "v1"
        require(label.matches(Regex("[a-zA-Z0-9_-]+")))
        return File(compose.activity.getExternalFilesDir(null), "pool-create-recovery-$label").apply { mkdirs() }
    }

    private fun save(mode: Mode, position: String) {
        val bitmap = compose.onNodeWithTag("pool-recovery-host").captureToImage().asAndroidBitmap()
        File(directory(), "${mode.language}-${mode.scale}-${mode.phase}-$position.png").outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
