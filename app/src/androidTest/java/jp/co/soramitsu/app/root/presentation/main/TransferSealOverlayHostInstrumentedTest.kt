package jp.co.soramitsu.app.root.presentation.main

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import jp.co.soramitsu.common.compose.component.GradientIconState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.fw.FwEffects
import jp.co.soramitsu.common.compose.theme.fw.FwStandard
import jp.co.soramitsu.common.compose.theme.fw.FwTier
import jp.co.soramitsu.common.compose.theme.fw.LocalFwEffects
import jp.co.soramitsu.common.compose.theme.fw.TRANSFER_SEAL_OVERLAY_TAG
import jp.co.soramitsu.common.compose.theme.fw.TransferSealOverlayHost
import jp.co.soramitsu.common.feedback.TransferSeal
import jp.co.soramitsu.staking.impl.presentation.confirm.pool.join.ConfirmJoinPoolScreen
import jp.co.soramitsu.staking.impl.presentation.confirm.pool.join.ConfirmJoinPoolScreenViewState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The activity-level seal overlay (brand kit SPEC v4 part 3) used by the relay-chain staking confirmations, whose own
 * progress is a spinner in their button: it takes no touches, leaves with the operation, and moves no layout. Also the
 * pool-join sheet, which draws the seal on its own loader. Placeholder values only; nothing is signed or sent.
 */
@RunWith(AndroidJUnit4::class)
class TransferSealOverlayHostInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<PolkaswapNavigationLayoutTestActivity>()

    private val dir by lazy { File(compose.activity.getExternalFilesDir(null), "seal-flows").apply { mkdirs() } }

    @After fun clear() = TransferSeal.clear()

    private fun saveScreen(name: String) {
        // Let the display catch up with the last composed frame (the test clock composes; vsync draws).
        Thread.sleep(300)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(dir, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    private fun tapAt(x: Float, y: Float) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val down = SystemClock.uptimeMillis()
        instrumentation.sendPointerSync(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0))
        instrumentation.sendPointerSync(MotionEvent.obtain(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0))
    }

    @Test fun overlayTakesNoTouchesLeavesWithTheOperationAndMovesNoLayout() {
        var taps = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            FearlessTheme {
                CompositionLocalProvider(LocalFwEffects provides FwEffects(FwTier.Full, highContrast = false, reduceMotion = false)) {
                    Column(Modifier.fillMaxSize().background(FwStandard.bgBase).padding(24.dp)) {
                        Text("Bond more", color = Color.White, modifier = Modifier.testTag("title"))
                        Box(Modifier.fillMaxWidth().height(56.dp).background(FwStandard.brand).clickable { taps++ }.testTag("confirm")) {
                            Text("Confirm", color = Color.White, modifier = Modifier.padding(16.dp))
                        }
                    }
                }
            }
        }
        val host = compose.runOnUiThread { TransferSealOverlayHost.install(compose.activity) }
        assertSame("installed once", host, compose.runOnUiThread { TransferSealOverlayHost.install(compose.activity) })
        step()
        compose.onNodeWithTag(TRANSFER_SEAL_OVERLAY_TAG).assertDoesNotExist()
        val titleBefore = compose.onNodeWithTag("title").fetchSemanticsNode().boundsInRoot

        // The operation is handed over: the seal covers the screen on ink.
        compose.runOnUiThread { TransferSeal.ready(TransferSeal.nowMs(), shownOver = true) }
        step(400)
        compose.onNodeWithTag(TRANSFER_SEAL_OVERLAY_TAG).assertExists()
        saveScreen("staking-relay-1-waiting")

        // A tap still reaches the screen underneath, exactly as without the overlay.
        val center = compose.onNodeWithTag("confirm").fetchSemanticsNode().boundsInWindow.center
        tapAt(center.x, center.y)
        step()
        assertEquals("the overlay takes no touches", 1, taps)

        // Accepted: the overlay leaves at once, so the app's result is never covered; the layout did not move.
        compose.runOnUiThread { TransferSeal.accepted(TransferSeal.nowMs()) }
        step()
        compose.onNodeWithTag(TRANSFER_SEAL_OVERLAY_TAG).assertDoesNotExist()
        assertEquals(titleBefore, compose.onNodeWithTag("title").fetchSemanticsNode().boundsInRoot)
        saveScreen("staking-relay-2-accepted-gone")

        // A definite failure: the broken seal shows for the break, then the overlay leaves.
        compose.runOnUiThread {
            TransferSeal.ready(TransferSeal.nowMs(), shownOver = true)
            TransferSeal.failed(TransferSeal.nowMs())
        }
        step(100)
        compose.onNodeWithTag(TRANSFER_SEAL_OVERLAY_TAG).assertExists()
        saveScreen("staking-relay-3-failed")
        step(1_000)
        compose.onNodeWithTag(TRANSFER_SEAL_OVERLAY_TAG).assertDoesNotExist()
        assertEquals(titleBefore, compose.onNodeWithTag("title").fetchSemanticsNode().boundsInRoot)

        // A seal the screen draws itself (not over the activity) never shows in the overlay.
        compose.runOnUiThread { TransferSeal.ready(TransferSeal.nowMs()) }
        step()
        compose.onNodeWithTag(TRANSFER_SEAL_OVERLAY_TAG).assertDoesNotExist()
    }

    private fun step(ms: Long = 64) {
        compose.mainClock.advanceTimeBy(ms)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        compose.mainClock.advanceTimeBy(16)
    }

    @Test fun poolJoinSheetDrawsTheSealOnItsLoaderAndABreakOnFailure() {
        val loading = mutableStateOf(true)
        TransferSeal.set(TransferSeal.Record(readyAtMs = TransferSeal.nowMs() - 2_000))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            FearlessTheme {
                CompositionLocalProvider(LocalFwEffects provides FwEffects(FwTier.Full, highContrast = false, reduceMotion = false)) {
                    Box(Modifier.fillMaxSize().background(FwStandard.bgBase)) {
                        ConfirmJoinPoolScreen(
                            ConfirmJoinPoolScreenViewState(
                                toolbarViewState = ToolbarViewState("Confirm", null),
                                amount = "10 DOT",
                                address = TitleValueViewState("Account", "public-fixture"),
                                selectedPool = TitleValueViewState("Pool", "Public fixture pool"),
                                networkFee = TitleValueViewState("Network fee", "0.01 DOT"),
                                assetIcon = GradientIconState.Local(jp.co.soramitsu.common.R.drawable.ic_fearless_logo),
                                isLoading = loading.value
                            ),
                            onNavigationClick = {}, onConfirm = {}
                        )
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(400)
        save("pool-join-1-waiting")
        TransferSeal.clear()
        loading.value = false
        compose.mainClock.advanceTimeBy(32)
        val before = compose.onNodeWithText("10 DOT").fetchSemanticsNode().boundsInRoot
        compose.runOnUiThread { TransferSeal.set(TransferSeal.Record(readyAtMs = TransferSeal.nowMs() - 2_000, failedAtMs = TransferSeal.nowMs())) }
        compose.mainClock.advanceTimeBy(32)
        save("pool-join-3-failed")
        assertEquals("layout moved when the result arrived", before, compose.onNodeWithText("10 DOT").fetchSemanticsNode().boundsInRoot)
        compose.mainClock.advanceTimeBy(1_000)
        save("pool-join-4-after-break")
    }

    private fun save(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        assertTrue(bitmap.width > 0)
    }
}
