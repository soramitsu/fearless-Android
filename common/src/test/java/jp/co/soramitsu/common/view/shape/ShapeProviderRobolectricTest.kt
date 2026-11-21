package jp.co.soramitsu.common.view.shape

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ShapeDrawable
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.fw.FwNeuDrawable
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShapeProviderRobolectricTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After fun restorePalette() { unmockkObject(FwRestyle) }

    @Test
    fun shouldRenderCutCornersAndOptionalStrokes() {
        mockkObject(FwRestyle)
        every { FwRestyle.paletteEnabled } returns false
        val plain = render(context.getCutCornerDrawableFromColors(Color.RED, null, 20, 2))
        val stroked = render(context.getCutCornerDrawableFromColors(Color.RED, Color.GREEN, 20, 2))
        assertEquals(Color.RED, plain.getPixel(50, 30))
        assertEquals(Color.TRANSPARENT, plain.getPixel(1, 1))
        assertEquals(Color.TRANSPARENT, plain.getPixel(98, 58))
        assertEquals(Color.GREEN, stroked.getPixel(50, 1))
        val leftCut = render(context.getCutLeftBottomCornerDrawableFromColors(Color.BLUE, 20, 10))
        assertEquals(Color.BLUE, leftCut.getPixel(50, 30))
        assertEquals(Color.TRANSPARENT, leftCut.getPixel(1, 58))
        assertTrue(context.getFocusedDrawable() is ShapeDrawable)
        assertTrue(context.getDisabledDrawable() is ShapeDrawable)
        assertTrue(context.getIdleDrawable() is ShapeDrawable)
        assertTrue(context.getSelectedDrawable() is ShapeDrawable)
    }

    @Test
    fun shouldChooseDisabledFocusedAndIdleStateDrawablesInOrder() {
        mockkObject(FwRestyle)
        every { FwRestyle.paletteEnabled } returns false
        val disabled = ColorDrawable(Color.GRAY)
        val focused = ColorDrawable(Color.GREEN)
        val idle = ColorDrawable(Color.BLUE)
        val selector = context.getCutCornersStateDrawable(disabled, focused, idle)
        selector.state = intArrayOf()
        assertSame(disabled, selector.current)
        selector.state = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_focused)
        assertSame(focused, selector.current)
        selector.state = intArrayOf(android.R.attr.state_enabled)
        assertSame(idle, selector.current)
    }

    @Test
    fun shouldUseRoundedSunlitShapesAndRetainTheRequestedFill() {
        mockkObject(FwRestyle)
        every { FwRestyle.paletteEnabled } returns true
        val shape = context.getCutCornerDrawableFromColors(Color.RED, Color.GREEN, strokeSizeInDp = 0)
        assertTrue(shape is GradientDrawable)
        assertEquals(Color.RED, render(shape).getPixel(50, 30))
        assertTrue(context.getCutCornersStateDrawable() is FwNeuDrawable)
        val fromResources = context.getCutCornerDrawable(R.color.white, R.color.colorAccentDark)
        assertEquals(context.getColor(R.color.white), render(fromResources).getPixel(50, 30))
        assertEquals(Color.RED, Color.RED.toColorStateList().defaultColor)
    }

    @Test
    fun shouldRetainRippleContentAndMask() {
        val content = ColorDrawable(Color.RED)
        val mask = ColorDrawable(Color.WHITE)
        val ripple = context.addRipple(content, mask) as RippleDrawable
        assertEquals(2, ripple.numberOfLayers)
        assertSame(content, ripple.getDrawable(0))
        assertSame(mask, ripple.findDrawableByLayerId(android.R.id.mask))
    }

    private fun render(drawable: Drawable): Bitmap {
        val bitmap = Bitmap.createBitmap(100, 60, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, 100, 60)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }
}
