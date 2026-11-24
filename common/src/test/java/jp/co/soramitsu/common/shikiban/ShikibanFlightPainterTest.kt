package jp.co.soramitsu.common.shikiban

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShikibanFlightPainterTest {
    private val tokens = requireNotNull(Shikiban.load(ApplicationProvider.getApplicationContext<Context>()))
    private val painter = ShikibanFlightPainter(tokens, 1f)

    @Test
    fun shouldDrawSquareAndMirrorGatesAndCullInvisibleGates() {
        tokens.gates.forEach { gate ->
            val visible = render { painter.drawGate(it, gate, 100f, 100f, 70f, 1f, 1f) }
            assertFalse(visible.isClear())
            assertTrue(render { painter.drawGate(it, gate, 100f, 100f, 70f, 1f, 0f) }.isClear())
            assertTrue(render { painter.drawGate(it, gate, 100f, 100f, 0f, 1f, 1f) }.isClear())
            assertTrue(render { painter.drawGate(it, gate, 100f, 100f, 10_000f, 1f, 1f, far = 150f) }.isClear())
        }
    }

    @Test
    fun shouldMoveFlightGatesAndLeaveTheEndClear() {
        val start = render { painter.drawGates(it, 0f, 100f, 100f, 200f) }
        val middle = render { painter.drawGates(it, 0.5f, 100f, 100f, 200f) }
        assertFalse(start.sameAs(middle))
        assertTrue(render { painter.drawGates(it, 1f, 100f, 100f, 200f) }.isClear())
    }

    @Test
    fun shouldPaintTheCoronaOnlyForPositiveStrengthAndRadius() {
        assertTrue(render { painter.drawCorona(it, 100f, 100f, 0f, 200f, 1f, 200, 200) }.isClear())
        assertTrue(render { painter.drawCorona(it, 100f, 100f, 30f, 200f, 0f, 200, 200) }.isClear())
        assertFalse(render { painter.drawCorona(it, 100f, 100f, 30f, 200f, 0.8f, 200, 200) }.isClear())
    }

    @Test
    fun shouldPunchTheOpeningWithoutClearingOutsideIt() {
        val bitmap = render {
            it.drawColor(Color.RED)
            painter.punchOpening(it, 100f, 100f, 25f)
        }
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(100, 100))
        assertEquals(Color.RED, bitmap.getPixel(0, 0))
        assertEquals(Color.RED, render {
            it.drawColor(Color.RED)
            painter.punchOpening(it, 100f, 100f, 0f)
        }.getPixel(100, 100))
    }

    private fun render(block: (Canvas) -> Unit): Bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).also {
        block(Canvas(it))
    }

    private fun Bitmap.isClear(): Boolean {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        return pixels.all { Color.alpha(it) == 0 }
    }
}
