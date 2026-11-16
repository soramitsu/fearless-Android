package jp.co.soramitsu.common.compose.theme.fw

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.feedback.TransferSeal
import jp.co.soramitsu.common.shikiban.Shikiban
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FwSurfaceRenderingTest {
    @get:Rule val compose = createComposeRule()
    private val full = FwEffects(FwTier.Full, highContrast = false, reduceMotion = true)
    private lateinit var hostView: View
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun shouldPaintEachSurfaceAndUpdateItsPressedState() {
        var level by mutableStateOf(FwElevation.Raised1)
        var pressed by mutableStateOf(false)
        var effects by mutableStateOf(full)
        show {
            Box(Modifier.size(120.dp).testTag("surface").fwSurface(
                effects, FwStandard, RoundedCornerShape(12.dp), level, pressed, sunRim = 0f, interactive = true
            ))
        }
        for (fx in listOf(full, full.copy(highContrast = true), full.copy(tier = FwTier.Reduced))) {
            for (elevation in FwElevation.entries) {
                compose.runOnIdle { level = elevation; pressed = false; effects = fx }
                val resting = snapshot("surface")
                assertTrue(resting.width > 0)
                assertNotEquals(0, resting.getPixel(resting.width / 2, resting.height / 2))
                compose.runOnIdle { pressed = true }
                val down = snapshot("surface")
                if (elevation != FwElevation.Inset) assertFalse(resting.sameAs(down))
            }
        }
    }

    @Test
    fun shouldKeepInsetErrorAndFocusDistinctAndPreserveCustomFills() {
        var error by mutableStateOf(false)
        var focus by mutableStateOf(false)
        show {
            Column {
                Box(Modifier.size(100.dp).testTag("input").fwNeuInset(full, FwStandard, focused = focus, error = error))
                Box(Modifier.size(100.dp).testTag("custom").fwSurface(
                    full, FwStandard, CircleShape, fill = Color.Cyan, sunRim = 0f
                ))
            }
        }
        val neutral = snapshot("input")
        compose.runOnIdle { focus = true }
        val focused = snapshot("input")
        compose.runOnIdle { error = true }
        val invalid = snapshot("input")
        assertFalse(neutral.sameAs(focused))
        assertFalse(focused.sameAs(invalid))
        val custom = snapshot("custom")
        assertEquals(Color.Cyan.toArgb(), custom.getPixel(custom.width / 2, custom.height / 2))
    }

    @Test
    fun shouldRenderChromeCardsAndGlowsWithoutChangingTheirChildren() {
        var reduced by mutableStateOf(false)
        show {
            val fx = if (reduced) full.copy(calm = true) else full
            Column(Modifier.testTag("decoration").fwBackdrop(fx, FwStandard)) {
                Box(Modifier.size(90.dp).fwChrome(fx, FwStandard)) { Text("Chrome") }
                Box(Modifier.size(90.dp).fwGlass(fx, FwStandard, accent = true)) { Text("Card") }
                Box(Modifier.size(90.dp).fwGlow(fx, CircleShape).fwNeuRaised(fx, FwStandard, CircleShape)) { Text("Well") }
                Box(Modifier.size(90.dp).fwGlow(fx, RoundedCornerShape(10.dp)).fwNeuRaised(
                    fx, FwStandard, RoundedCornerShape(10.dp), elevated = false
                )) { Text("Row") }
            }
        }
        val lit = snapshot("decoration")
        listOf("Chrome", "Card", "Well", "Row").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.runOnIdle { reduced = true }
        assertFalse(lit.sameAs(snapshot("decoration")))
        compose.onNodeWithText("Card").assertIsDisplayed()
    }

    @Test
    fun shouldKeepSyncAndTransactionStatesVisuallyDistinct() {
        var phase by mutableStateOf(FwSyncPhase.Idle)
        var status by mutableStateOf(FwTxStatus.Pending)
        show {
            Column {
                FwSunBadge(FwSyncState(phase, 0.6f, done = 3, failed = 1, total = 8), Modifier.testTag("sun"), contentDescription = "Sync status")
                FwTxStatusRing(status, Modifier.testTag("transaction")) { Text("TX") }
                FwLockupHero(contentDescription = "Fearless wallet")
            }
        }
        val idle = snapshot("sun")
        val pending = snapshot("transaction")
        compose.runOnIdle { phase = FwSyncPhase.Running; status = FwTxStatus.Success }
        val running = snapshot("sun")
        assertFalse(idle.sameAs(running))
        assertFalse(pending.sameAs(snapshot("transaction")))
        compose.runOnIdle { phase = FwSyncPhase.Failed; status = FwTxStatus.Failed }
        assertFalse(running.sameAs(snapshot("sun")))
        compose.onNodeWithContentDescription("Sync status").assertIsDisplayed()
        compose.onNodeWithContentDescription("Fearless wallet").assertIsDisplayed()
        compose.onNodeWithText("TX").assertIsDisplayed()
    }

    @Test
    fun shouldRenderSkyGeometryLatticeAndArrivalLightBehindTheContent() {
        var pulse by mutableStateOf(0f)
        var effects by mutableStateOf(full)
        val tokens = requireNotNull(Shikiban.load(context))
        val geometry = requireNotNull(FwSolarGeometry.load(context))
        show {
            val turn = rememberFwTickTurn(geometry)
            val heaven = rememberFwHeavenBoost(false)
            Box(Modifier.size(240.dp).testTag("sky")
                .fwBackdrop(effects, FwStandard, Offset(100f, 90f))
                .fwSkyHero(effects, FwStandard, ImageBitmap(2, 2), geometry,
                    badge = { Offset(100f, 90f) to 40f }, textTop = { 170f },
                    tickTurn = turn, pulse = mutableStateOf(pulse), heaven = heaven)
                .fwCompositionMarks(effects, tokens) { 170f }
                .fwLitRim(effects, FwStandard, RoundedCornerShape(12.dp))) {
                Text("Wallet balance")
            }
        }
        val idle = snapshot("sky")
        compose.runOnIdle { pulse = 0.4f }
        assertFalse(idle.sameAs(snapshot("sky")))
        compose.runOnIdle { effects = full.copy(calm = true) }
        assertFalse(idle.sameAs(snapshot("sky")))
        compose.onNodeWithText("Wallet balance").assertIsDisplayed()
    }

    @Test
    fun shouldPaintSealLoaderAndDialForTheirDeclaredStates() {
        var record by mutableStateOf(TransferSeal.Record(readyAtMs = 0, signedAtMs = 100, sentAtMs = 200))
        show {
            Column {
                FwTransferSeal(record, Modifier.testTag("seal"), 120.dp)
                FwMirrorLoader(Modifier.size(70.dp).testTag("loader"), progress = 0.5f)
                FwSyncDial(FwSyncState(FwSyncPhase.Done, done = 4, total = 4), Modifier.testTag("dial"), 40.dp)
                FwSolarIgnite(Modifier.size(90.dp).testTag("ignite"))
            }
        }
        val sent = snapshot("seal")
        compose.runOnIdle { record = record.copy(failedAtMs = 300) }
        assertFalse(sent.sameAs(snapshot("seal")))
        listOf("loader", "dial", "ignite").forEach {
            compose.onNodeWithTag(it).assertIsDisplayed()
            assertTrue(snapshot(it).width > 0)
        }
    }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            hostView = LocalView.current
            FearlessAppTheme {
                CompositionLocalProvider(LocalFwEffects provides full, LocalFwColors provides FwStandard, content = content)
            }
        }
    }

    private fun snapshot(tag: String): Bitmap {
        val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        return compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(hostView.width, hostView.height, Bitmap.Config.ARGB_8888)
            hostView.draw(android.graphics.Canvas(bitmap))
            Bitmap.createBitmap(bitmap, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt())
        }
    }
}
