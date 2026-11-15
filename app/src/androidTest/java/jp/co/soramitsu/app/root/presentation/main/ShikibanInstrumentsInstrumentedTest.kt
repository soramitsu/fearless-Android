package jp.co.soramitsu.app.root.presentation.main

import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.fw.FwEffects
import jp.co.soramitsu.common.compose.theme.fw.FwMirrorLoader
import jp.co.soramitsu.common.compose.theme.fw.FwStandard
import jp.co.soramitsu.common.compose.theme.fw.FwSyncDial
import jp.co.soramitsu.common.compose.theme.fw.FwSyncPhase
import jp.co.soramitsu.common.compose.theme.fw.FwSyncState
import jp.co.soramitsu.common.compose.theme.fw.FwTier
import jp.co.soramitsu.common.compose.theme.fw.FwTransferSeal
import jp.co.soramitsu.common.compose.theme.fw.LocalFwEffects
import jp.co.soramitsu.common.compose.theme.fw.LocalFwValueTicks
import jp.co.soramitsu.common.compose.theme.fw.fwValueTick
import jp.co.soramitsu.common.feedback.TransferSeal
import jp.co.soramitsu.common.launch.LaunchFlightStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The shikiban instruments (brand kit SPEC v4 parts 2–4) drawn on a device, and the launch tiers leaving no transform. */
@RunWith(AndroidJUnit4::class)
class ShikibanInstrumentsInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<PolkaswapNavigationLayoutTestActivity>()

    private val dir by lazy { File(compose.activity.getExternalFilesDir(null), "shikiban").apply { mkdirs() } }

    private fun capture(tag: String, name: String): Bitmap {
        val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        return bitmap
    }

    private fun countColour(bitmap: Bitmap, predicate: (Int, Int, Int) -> Boolean): Int {
        var n = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val p = bitmap.getPixel(x, y)
            if (predicate((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)) n++
        }
        return n
    }

    private fun content(reduceMotion: Boolean, sync: FwSyncState, seal: TransferSeal.Record) {
        compose.setContent {
            FearlessTheme {
                CompositionLocalProvider(LocalFwEffects provides FwEffects(FwTier.Full, false, reduceMotion)) {
                    Column(Modifier.background(FwStandard.bgBase).padding(16.dp)) {
                        Row {
                            Box(Modifier.testTag("dial").padding(8.dp)) { FwSyncDial(sync, size = 72.dp) }
                            Box(Modifier.testTag("loader").padding(8.dp)) { FwMirrorLoader(Modifier.size(96.dp)) }
                            Box(Modifier.testTag("filled").padding(8.dp)) { FwMirrorLoader(Modifier.size(96.dp), progress = 0.6f) }
                        }
                        Box(Modifier.testTag("seal")) { FwTransferSeal(seal, size = 176.dp) }
                    }
                }
            }
        }
    }

    @Test fun dialShowsGoldShareEmberFailuresAndCyanCometWhileSyncing() {
        compose.mainClock.autoAdvance = false
        content(false, FwSyncState(FwSyncPhase.Running, done = 9, failed = 2, total = 14), TransferSeal.Record())
        compose.mainClock.advanceTimeBy(400)
        val dial = capture("dial", "dial-syncing-9-2-14")
        val ember = countColour(dial) { r, g, b -> r > 200 && g in 60..120 && b in 50..110 }
        val cyan = countColour(dial) { r, g, b -> r < 120 && g > 180 && b > 180 }
        val gold = countColour(dial) { r, g, b -> r > 220 && g > 170 && b < 170 }
        assertTrue("ember share drawn ($ember)", ember > 20)
        assertTrue("cyan comet drawn ($cyan)", cyan > 10)
        assertTrue("gold share drawn ($gold)", gold > 50)
    }

    @Test fun reducedMotionShowsStatesWithoutMoving() {
        compose.mainClock.autoAdvance = false
        TransferSeal.set(TransferSeal.Record())
        content(true, FwSyncState(FwSyncPhase.Running, done = 5, failed = 0, total = 14), TransferSeal.Record(readyAtMs = 0, signedAtMs = 0))
        compose.mainClock.advanceTimeBy(100)
        val dial0 = capture("dial", "still-dial-0")
        val seal0 = capture("seal", "still-seal-0")
        compose.mainClock.advanceTimeBy(600)
        assertTrue("the dial does not move", dial0.sameAs(capture("dial", "still-dial-1")))
        assertTrue("the seal does not move", seal0.sameAs(capture("seal", "still-seal-1")))
        val cyan = countColour(dial0) { r, g, b -> r < 120 && g > 180 && b > 180 }
        assertEquals("no comet under reduced motion", 0, cyan)
    }

    @Test fun sealDrawsTheStagesReachedAndBreaksOnFailure() {
        compose.mainClock.autoAdvance = false
        // Under reduced motion a broken seal stands still, ember and faint: a deterministic frame to measure.
        val now = TransferSeal.nowMs()
        content(true, FwSyncState(FwSyncPhase.Done), TransferSeal.Record(readyAtMs = now - 5_000, signedAtMs = now - 4_000, failedAtMs = now - 2_000))
        compose.mainClock.advanceTimeBy(100)
        val broken = capture("seal", "seal-broken")
        val ember = countColour(broken) { r, g, b -> r > 60 && r > g * 2 && r * 2 > b * 3 }
        // Gold #FFD58A has a green channel near 213 and blue under 145; ember strokes adding up where they cross
        // (#FF5A4D twice) reach green 180 with blue over 150, so they are not counted.
        val gold = countColour(broken) { r, g, b -> r > 220 && g > 190 && b in 60..145 }
        assertTrue("a broken seal is ember ($ember)", ember > 0)
        assertEquals("a broken seal has no gold", 0, gold)
    }

    @Test fun valueTickDrawsAnUpLineOnlyAfterTheFirstValueChanges() {
        compose.mainClock.autoAdvance = false
        var value by mutableStateOf("1.00 DOT")
        compose.setContent {
            FearlessTheme {
                CompositionLocalProvider(LocalFwEffects provides FwEffects(FwTier.Full, false, false), LocalFwValueTicks provides true) {
                    Box(Modifier.background(FwStandard.bgBase).padding(16.dp)) {
                        androidx.compose.material.Text(value, color = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.testTag("value").fwValueTick(value))
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(600)
        val first = capture("value", "tick-first")
        val upColour = { r: Int, g: Int, b: Int -> r < 120 && g > 180 && b > 180 }
        assertEquals("no line on the first value", 0, countColour(first, upColour))
        compose.runOnUiThread { value = "2.00 DOT" }
        compose.mainClock.advanceTimeBy(160)
        val ticking = capture("value", "tick-up")
        assertTrue("an up line sweeps under a grown value", countColour(ticking, upColour) > 0)
        compose.mainClock.advanceTimeBy(800)
        assertEquals("the line has gone", 0, countColour(capture("value", "tick-done"), upColour))
    }

    @Test fun launchTiersLeaveNoScaleOrPivotOnAViewAfterwards() {
        compose.runOnUiThread {
            val view = View(compose.activity)
            compose.activity.setContentView(view)
            LaunchFlightStage.registerView(view, 2)
            LaunchFlightStage.begin(10f, 20f, { 0.2f }, { 0.5f })
            assertEquals(0.2f, view.scaleX, 0f)
            assertEquals(0.5f, view.alpha, 0f)
            LaunchFlightStage.end()
            assertEquals(1f, view.scaleX, 0f)
            assertEquals(1f, view.scaleY, 0f)
            assertEquals(1f, view.alpha, 0f)
            assertFalse(view.isPivotSet)
            assertFalse(LaunchFlightStage.active)
        }
    }
}
