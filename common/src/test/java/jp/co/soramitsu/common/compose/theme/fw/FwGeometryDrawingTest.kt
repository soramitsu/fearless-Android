package jp.co.soramitsu.common.compose.theme.fw

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import jp.co.soramitsu.common.feedback.SealFrame
import jp.co.soramitsu.common.shikiban.Shikiban
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FwGeometryDrawingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val geometry get() = requireNotNull(FwSolarGeometry.load(context))
    private val tokens get() = requireNotNull(Shikiban.load(context))

    @Test
    fun shouldRevealSolarStrokesAndHideZeroStrengthOrRadius() {
        val empty = draw { drawFwSolarGeometry(geometry, center, 85f, progress = 0f) }
        assertTrue(empty.isClear())
        val partial = draw { drawFwSolarGeometry(geometry, center, 85f, progress = 0.45f) }
        val complete = draw { drawFwSolarGeometry(geometry, center, 85f) }
        assertFalse(partial.isClear())
        assertFalse(complete.sameAs(partial))
        assertTrue(draw { drawFwSolarGeometry(geometry, center, 85f, alpha = 0f) }.isClear())
        assertTrue(draw { drawFwSolarGeometry(geometry, center, 0f) }.isClear())
        val muted = draw {
            drawFwSolarGeometry(geometry, center, 85f, tickTurn = 0.2f) { if (it == "ring") 0f else 0.2f }
        }
        assertFalse(muted.sameAs(complete))
    }

    @Test
    fun shouldShowProgressWithoutCometsAndAnimateIndeterminateMirrorLoaders() {
        val determinate = draw { drawMirrorLoader(tokens, 75f, seconds = 1f, progress = 0.4f) }
        val sameProgressLater = draw { drawMirrorLoader(tokens, 75f, seconds = 3f, progress = 0.4f) }
        assertTrue(determinate.sameAs(sameProgressLater))
        val busy = draw { drawMirrorLoader(tokens, 75f, seconds = 1f, progress = null) }
        assertFalse(busy.isClear())
        assertFalse(busy.sameAs(draw { drawMirrorLoader(tokens, 75f, seconds = 1.4f, progress = null) }))
        assertFalse(determinate.sameAs(draw { drawMirrorLoader(tokens, 75f, seconds = 1f, progress = 1f) }))
    }

    @Test
    fun shouldSeparateLiveSyncFailedSegmentsAndReducedMotionDials() {
        val state = tokens.dialState(done = 3, failed = 2, total = 8)
        val still = draw { drawSyncDial(tokens, state, busy = true, comet = false, seconds = 0f, seal = 1f) }
        val moving = draw { drawSyncDial(tokens, state, busy = true, comet = true, seconds = 0.6f, seal = 0.4f) }
        assertFalse(still.isClear())
        assertFalse(still.sameAs(moving))
        val done = draw { drawSyncDial(tokens, tokens.dialState(6, 2, 8), busy = false, comet = false, seconds = 0f, seal = 1f) }
        assertFalse(done.sameAs(still))
        assertTrue(android.graphics.Color.alpha(done.getPixel(100, 100)) > 0)
        assertTrue(android.graphics.Color.alpha(still.getPixel(100, 100)) == 0)
    }

    @Test
    fun shouldDrawTransferStagesPendingStrokesAndBrokenSeals() {
        fun render(frame: SealFrame, seconds: Float = 0.7f) = draw {
            drawFwSeal(geometry, tokens, center, 82f, frame, seconds, turn = 0.15f)
        }
        assertTrue(render(SealFrame.Idle).isClear())
        val waitingRing = render(SealFrame.Idle.copy(waiting = true))
        val signed = render(SealFrame(2f, 0f, 1f, 0f, waiting = true))
        val complete = render(SealFrame(4f, 1f, 1f, 0f, waiting = false))
        val broken = render(SealFrame(4f, 1f, 1f, 0.6f, waiting = false))
        assertFalse(waitingRing.isClear())
        assertFalse(signed.sameAs(waitingRing))
        assertFalse(complete.sameAs(signed))
        assertFalse(broken.sameAs(complete))
        assertTrue(render(SealFrame(4f, 1f, 0f, 0f, waiting = false)).isClear())
    }

    private fun draw(block: DrawScope.() -> Unit): Bitmap {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(200f, 200f), block)
        return bitmap
    }

    private fun Bitmap.isClear(): Boolean {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        return pixels.all { android.graphics.Color.alpha(it) == 0 }
    }
}
