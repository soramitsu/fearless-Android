package jp.co.soramitsu.app.root.presentation.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import jp.co.soramitsu.common.compose.component.FullScreenLoading
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.fw.FwEffects
import jp.co.soramitsu.common.compose.theme.fw.FwTier
import jp.co.soramitsu.common.compose.theme.fw.LocalFwEffects
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the actual loading container with local callbacks, never a wallet or transaction. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalComposeUiApi::class, ExperimentalTestApi::class)
class WalletLoadingInputInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<PolkaswapNavigationLayoutTestActivity>()
    private var busy by mutableStateOf(false)
    private var clicks = 0
    private var backs = 0
    private val requester = FocusRequester()
    private var requestKeyboard: () -> Unit = {}

    private fun install() {
        compose.setContent {
            val input = LocalInputModeManager.current
            requestKeyboard = { input.requestInputMode(InputMode.Keyboard); requester.requestFocus() }
            BackHandler { backs++ }
            FearlessTheme {
                CompositionLocalProvider(LocalFwEffects provides FwEffects(FwTier.Full, false, true)) {
                    Box(Modifier.requiredWidth(320.dp).requiredHeight(320.dp).testTag("loading-input-host")) {
                        FullScreenLoading(busy) {
                            Button(onClick = { clicks++ }, modifier = Modifier.focusRequester(requester).testTag("loading-input-action")) {
                                Text("Public fixture action")
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun busyOverlayBlocksPointerClicksAndRestoresTheSameAction() {
        install()
        val host = compose.onNodeWithTag("loading-input-host")
        val position = compose.onNodeWithTag("loading-input-action").fetchSemanticsNode().boundsInRoot.center -
            host.fetchSemanticsNode().boundsInRoot.topLeft
        compose.runOnUiThread { busy = true }
        compose.waitForIdle()
        host.performTouchInput { click(position) }
        val whileBusy = clicks
        compose.runOnUiThread { busy = false }
        compose.waitForIdle()
        host.performTouchInput { click(position) }
        record("pointer", whileBusy, clicks)
        assertEquals("Busy overlay must not pass a tap through to its covered action", 0, whileBusy)
        assertEquals("The same action must work after loading finishes", 1, clicks)
    }

    @Test fun loadingThatStartsDuringAPressCancelsTheCoveredAction() {
        install()
        val host = compose.onNodeWithTag("loading-input-host")
        val position = compose.onNodeWithTag("loading-input-action").fetchSemanticsNode().boundsInRoot.center -
            host.fetchSemanticsNode().boundsInRoot.topLeft
        host.performTouchInput { down(position) }
        compose.runOnUiThread { busy = true }
        compose.waitForIdle()
        host.performTouchInput { up() }
        val whileBusy = clicks
        compose.runOnUiThread { busy = false }
        compose.waitForIdle()
        host.performTouchInput { click(position) }
        record("held-pointer", whileBusy, clicks)
        assertEquals("A gesture started before loading must not activate after loading begins", 0, whileBusy)
        assertEquals(1, clicks)
    }

    @Test fun busyOverlayBlocksAlreadyFocusedKeyboardActionAndRestoresIt() {
        install()
        compose.runOnUiThread { requestKeyboard() }
        compose.onNodeWithTag("loading-input-action").assertIsFocused()
        compose.runOnUiThread { busy = true }
        compose.waitForIdle()
        compose.onNodeWithTag("loading-input-host").performKeyInput { pressKey(Key.Enter) }
        val whileBusy = clicks
        compose.runOnUiThread { busy = false }
        compose.waitForIdle()
        compose.runOnUiThread { requestKeyboard() }
        compose.onNodeWithTag("loading-input-host").performKeyInput { pressKey(Key.Enter) }
        record("keyboard", whileBusy, clicks)
        assertEquals("Covered focused actions must not accept keyboard activation while busy", 0, whileBusy)
        assertEquals(1, clicks)
    }

    @Test fun aPressSpanningACompleteBusyCycleStaysCancelledUntilRelease() {
        install()
        val host = compose.onNodeWithTag("loading-input-host")
        val position = compose.onNodeWithTag("loading-input-action").fetchSemanticsNode().boundsInRoot.center -
            host.fetchSemanticsNode().boundsInRoot.topLeft
        host.performTouchInput { down(position) }
        compose.runOnUiThread { busy = true }
        compose.waitForIdle()
        compose.runOnUiThread { busy = false }
        compose.waitForIdle()
        host.performTouchInput { up() }
        val spanningBusy = clicks
        host.performTouchInput { click(position) }
        record("completed-busy-pointer", spanningBusy, clicks)
        assertEquals("Loading completion must not revive a press started before loading", 0, spanningBusy)
        assertEquals("A fresh gesture must still work", 1, clicks)
    }

    @Test fun aKeyboardPressSpanningACompleteBusyCycleStaysCancelled() {
        install()
        compose.runOnUiThread { requestKeyboard() }
        compose.onNodeWithTag("loading-input-action").assertIsFocused()
        val host = compose.onNodeWithTag("loading-input-host")
        host.performKeyInput { keyDown(Key.Enter) }
        compose.runOnUiThread { busy = true }
        compose.waitForIdle()
        compose.runOnUiThread { busy = false }
        compose.waitForIdle()
        host.performKeyInput { keyUp(Key.Enter) }
        val spanningBusy = clicks
        compose.runOnUiThread { requestKeyboard() }
        host.performKeyInput { pressKey(Key.Enter) }
        record("completed-busy-keyboard", spanningBusy, clicks)
        assertEquals("Loading completion must not revive an earlier keyboard press", 0, spanningBusy)
        assertEquals(1, clicks)
    }

    @Test fun busyOverlayRemovesCoveredAccessibilityActionsAndRestoresThem() {
        install()
        val idle = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().size
        compose.runOnUiThread { busy = true }
        compose.waitForIdle()
        val whileBusy = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().size
        compose.runOnUiThread { busy = false }
        compose.waitForIdle()
        val after = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().size
        record("semantics", whileBusy, after)
        assertEquals(1, idle)
        assertEquals("Covered controls must not expose accessible click actions while busy", 0, whileBusy)
        assertEquals(idle, after)
    }

    @Test fun loadingPreservesTheExistingSystemBackHandler() {
        install()
        compose.runOnUiThread { busy = true }
        compose.waitForIdle()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        record("back", clicks, backs)
        assertEquals(0, clicks)
        assertEquals("Loading must retain the caller's system Back behavior", 1, backs)
    }

    private fun record(name: String, busyValue: Int, restoredValue: Int) {
        val label = InstrumentationRegistry.getArguments().getString("loadingEvidenceLabel") ?: "input"
        check(label.matches(Regex("[a-zA-Z0-9_-]+")))
        val directory = File(compose.activity.getExternalFilesDir(null), "loading-$label").apply { mkdirs() }
        File(directory, "$name.json").writeText(JSONObject().put("busyValue", busyValue)
            .put("restoredValue", restoredValue).toString(2))
    }
}
