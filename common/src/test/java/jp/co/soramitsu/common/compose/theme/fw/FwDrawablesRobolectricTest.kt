package jp.co.soramitsu.common.compose.theme.fw

import android.app.Activity
import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.graphics.toArgb
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FwDrawablesRobolectricTest {
    private val full = FwEffects(FwTier.Full, highContrast = false, reduceMotion = false)

    @Test
    fun shouldPaintRaisedInsetAndPressedSurfacesWithTheirDeclaredFill() {
        for (level in FwElevation.entries.filter { it != FwElevation.Emissive }) {
            val drawable = FwNeuDrawable(FwStandard, full, 1f, level, cornerRadius = 12f)
            val expected = if (level == FwElevation.Inset) FwStandard.surfaceSunken else FwStandard.surfaceRaised
            assertEquals(expected.toArgb(), render(drawable).getPixel(100, 60))
            assertTrue(drawable.isStateful)
            drawable.state = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed)
            assertEquals(FwStandard.surfaceSunken.toArgb(), render(drawable).getPixel(100, 60))
            assertFalse(drawable.setState(drawable.state))
        }
    }

    @Test
    fun shouldShowPressedBrandAndDisabledFallbackForEmissiveControls() {
        val drawable = FwNeuDrawable(FwStandard, full, 1f, FwElevation.Emissive, focusRing = Color.GREEN)
        assertNotEquals(Color.TRANSPARENT, render(drawable).getPixel(100, 60))
        drawable.state = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed)
        assertEquals(FwStandard.brandPressed.toArgb(), render(drawable).getPixel(100, 60))
        drawable.state = intArrayOf(android.R.attr.state_focused)
        assertEquals(FwStandard.surfaceRaised.toArgb(), render(drawable).getPixel(100, 60))
        drawable.state = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_focused)
        assertTrue(render(drawable).pixels().any { it == Color.GREEN })
    }

    @Test
    fun shouldDrawVisibleBoundariesWhenEffectsAreReduced() {
        for (fx in listOf(full.copy(tier = FwTier.Reduced), full.copy(highContrast = true), full.copy(calm = true))) {
            val drawable = FwNeuDrawable(FwStandard, fx, 1f, FwElevation.Raised1, cornerRadius = 0f, shadowRoom = 4f)
            val bitmap = render(drawable)
            assertEquals(FwStandard.surfaceRaised.toArgb(), bitmap.getPixel(100, 60))
            assertTrue(Color.red(bitmap.getPixel(4, 60)) > Color.red(bitmap.getPixel(100, 60)))
            assertEquals(255, Color.alpha(bitmap.getPixel(4, 60)))
            assertEquals(PixelFormat.TRANSLUCENT, drawable.opacity)
        }
    }

    @Test
    fun shouldAddPageLightOnlyForLitBackdrops() {
        val flat = FwPageBackdropDrawable(FwStandard, false)
        val lit = FwPageBackdropDrawable(FwStandard, true)
        assertEquals(FwStandard.bgBase.toArgb(), render(flat).getPixel(100, 60))
        assertFalse(render(flat).sameAs(render(lit)))
        assertEquals(PixelFormat.OPAQUE, lit.opacity)
    }

    @Test
    fun shouldFillClearAnimateAndMarkPinDotsInError() {
        val dot = FwPinDotDrawable(FwStandard, full, 1f)
        val empty = render(dot)
        dot.setFilled(true, animate = false)
        val filled = render(dot)
        assertFalse(empty.sameAs(filled))
        dot.error = true
        assertTrue(dot.error)
        assertFalse(filled.sameAs(render(dot)))
        dot.error = false
        dot.setFilled(false, animate = false)
        assertTrue(empty.sameAs(render(dot)))
        dot.setFilled(true, animate = true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertFalse(empty.sameAs(render(dot)))
    }

    @Test
    fun shouldMoveTheNavigationWellAndRespectTheBottomInset() {
        val bar = FwNavBarDrawable(FwStandard, full, 1f, 5f, 5f, 24f)
        val idle = render(bar)
        bar.select(40f, 40f, animate = true)
        val first = render(bar)
        assertFalse(idle.sameAs(first))
        bar.select(160f, 40f, animate = true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertFalse(first.sameAs(render(bar)))
        bar.bottomInset = 30
        assertEquals(30, bar.bottomInset)
        assertEquals(Color.TRANSPARENT, render(bar).getPixel(100, 115))
        val calm = FwNavBarDrawable(FwStandard, full.copy(calm = true), 1f, 0f, 0f, 24f)
        calm.select(40f, 40f, animate = true)
        val before = render(calm)
        calm.select(160f, 40f, animate = true)
        assertFalse(before.sameAs(render(calm)))
    }

    @Test
    fun shouldGiveViewsRowMarginsAndRoomForTheirShadows() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = controller.get()
            val parent = FrameLayout(activity)
            val row = View(activity)
            row.fwListRow(12, 6)
            parent.addView(row)
            activity.setContentView(parent)
            val density = row.resources.displayMetrics.density
            val params = row.layoutParams as FrameLayout.LayoutParams
            assertEquals((12 * density).toInt(), params.leftMargin)
            assertEquals((6 * density).toInt(), params.bottomMargin)
            assertTrue(row.background is FwNeuDrawable)
            assertFalse(parent.clipChildren)
            assertTrue(FwViews.surface(activity, calm = true) is FwNeuDrawable)
            assertEquals(FwStandard, FwStandardOrHighContrast(activity))
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun shouldPaintOnlyTheDialogContainersBottomPadding() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val dialog = Dialog(controller.get())
        try {
            val container = FrameLayout(controller.get()).apply {
                id = com.google.android.material.R.id.container
                setPadding(0, 0, 0, 10)
            }
            dialog.setContentView(container)
            dialog.fwSheetNavigationBar()
            val bitmap = render(requireNotNull(container.background))
            assertEquals(Color.TRANSPARENT, bitmap.getPixel(100, 50))
            assertEquals(FwStandard.surfaceRaised2.toArgb(), bitmap.getPixel(100, 115))
            container.setPadding(0, 0, 0, 0)
            assertTrue(render(requireNotNull(container.background)).pixels().all { it == Color.TRANSPARENT })
        } finally {
            dialog.dismiss()
            controller.pause().stop().destroy()
        }
    }

    private fun render(drawable: Drawable): Bitmap {
        val bitmap = Bitmap.createBitmap(200, 120, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, bitmap.width, bitmap.height)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    private fun Bitmap.pixels(): IntArray = IntArray(width * height).also {
        getPixels(it, 0, width, 0, 0, width, height)
    }
}
