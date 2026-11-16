package jp.co.soramitsu.common.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.fw.FwEffects
import jp.co.soramitsu.common.compose.theme.fw.FwNavBarDrawable
import jp.co.soramitsu.common.compose.theme.fw.FwStandard
import jp.co.soramitsu.common.compose.theme.fw.FwTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BottomNavigationViewRobolectricTest {
    private val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_Soramitsu_Fearless)

    @Test
    fun shouldRetainMenuSelectionAndMoveTheSunlitWell() {
        val view = BottomNavigationViewWithFAB(context)
        view.menu.add(0, 101, 0, "Wallet").setIcon(R.drawable.ic_vector)
        view.menu.add(0, 102, 1, "Settings").setIcon(R.drawable.ic_vector)
        assertTrue(view.background is MaterialShapeDrawable)
        view.setSunlitBottomInset(0)
        view.moveSunlitWell(101, false, false)
        view.applySunlitStyle(FwEffects(FwTier.Full, false, true), FwStandard)
        view.setSunlitBottomInset(20)
        assertTrue(view.background is FwNavBarDrawable)
        assertEquals(20, (view.background as FwNavBarDrawable).bottomInset)
        assertNull(view.itemRippleColor)
        assertFalse(view.isItemActiveIndicatorEnabled)
        view.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 400, 120)
        view.moveSunlitWell(101, false, false)
        val first = render(view)
        view.selectedItemId = 102
        view.moveSunlitWell(102, false, false)
        val second = render(view)
        assertFalse(first.sameAs(second))
        assertEquals(102, view.selectedItemId)
        assertEquals("Settings", view.menu.findItem(102).title)
        view.moveSunlitWell(102, true, false)
        assertFalse(second.sameAs(render(view)))
        view.moveSunlitWell(View.NO_ID, false, true)
        assertEquals(2, view.menu.size())
    }

    @Test
    fun shouldDrawTheFabCradleAndClampItsVerticalOffset() {
        fun shape(diameter: Float, offset: Float): Bitmap {
            val edge = BottomNavigationWithFABTopEdgeTreatment(diameter, 4f, offset)
            val drawable = MaterialShapeDrawable(ShapeAppearanceModel.builder().setTopEdge(edge).build())
            drawable.setTint(android.graphics.Color.RED)
            drawable.setBounds(0, 0, 200, 80)
            return Bitmap.createBitmap(200, 80, Bitmap.Config.ARGB_8888).also { drawable.draw(Canvas(it)) }
        }
        val flat = shape(0f, 0f)
        val cradle = shape(40f, 4f)
        assertFalse(flat.sameAs(cradle))
        assertTrue(shape(40f, 24f).sameAs(shape(40f, 1000f)))
        assertTrue(shape(40f, -24f).sameAs(shape(40f, -1000f)))
    }

    private fun render(view: View): Bitmap = Bitmap.createBitmap(400, 120, Bitmap.Config.ARGB_8888).also {
        view.draw(Canvas(it))
    }
}
