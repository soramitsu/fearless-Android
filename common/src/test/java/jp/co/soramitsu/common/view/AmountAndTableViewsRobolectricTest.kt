package jp.co.soramitsu.common.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.InputType
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import jp.co.soramitsu.common.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AmountAndTableViewsRobolectricTest {
    private val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_Soramitsu_Fearless)

    @Test
    fun shouldRenderAssetDetailsAndPropagateEnabledStateToAmountInput() {
        val attrs = Robolectric.buildAttributeSet().addAttribute(android.R.attr.enabled, "false").build()
        val view = AmountView(context, attrs)
        assertFalse(view.amountInput.isEnabled)
        assertEquals(InputType.TYPE_NULL, view.amountInput.inputType)
        view.isEnabled = true
        assertTrue(view.amountInput.isEnabled)
        assertEquals(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL, view.amountInput.inputType)
        view.setAssetName("DOT")
        view.setAssetBalance("100 DOT")
        view.setAssetBalanceFiatAmount("$500")
        assertEquals("DOT", view.findViewById<TextView>(R.id.stakingAssetToken).text.toString())
        assertEquals("100 DOT", view.findViewById<TextView>(R.id.stakingAssetBalance).text.toString())
        assertEquals("$500", view.findViewById<TextView>(R.id.stakingAssetFiatAmount).text.toString())
        view.hideAssetFiatAmount()
        assertEquals(View.GONE, view.findViewById<View>(R.id.stakingAssetFiatAmount).visibility)
        view.showAssetFiatAmount()
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.stakingAssetFiatAmount).visibility)
        view.setAssetBalanceFiatAmount(null)
        assertEquals(View.GONE, view.findViewById<View>(R.id.stakingAssetFiatAmount).visibility)
        val image = ColorDrawable(Color.RED)
        view.setAssetImage(image)
        assertSame(image, view.findViewById<ImageView>(R.id.stakingAssetImage).drawable)
        view.setAssetImageResource(R.drawable.ic_vector)
        assertNotNull(view.findViewById<ImageView>(R.id.stakingAssetImage).drawable)
        view.childDrawableStateChanged(view.amountInput)
        assertTrue(view.drawableState.contains(android.R.attr.state_enabled))
    }

    @Test
    fun shouldSwitchTableCellsBetweenProgressValueAndHiddenStates() {
        val attrs = Robolectric.buildAttributeSet().addAttribute(R.attr.title, "Fee")
            .addAttribute(R.attr.dividerVisible, "false").build()
        val cell = TableCellView(context, attrs)
        assertEquals("Fee", cell.title.text.toString())
        assertEquals(View.GONE, cell.findViewById<View>(R.id.tableCellValueDivider).visibility)
        cell.showProgress()
        assertEquals(View.VISIBLE, cell.findViewById<View>(R.id.tableCellValueProgress).visibility)
        assertEquals(View.GONE, cell.findViewById<View>(R.id.tableCellContent).visibility)
        cell.showValue("1 DOT", "$5")
        assertEquals("1 DOT", cell.valuePrimary.text.toString())
        assertEquals("$5", cell.findViewById<TextView>(R.id.tableCellValueSecondary).text.toString())
        assertEquals(View.GONE, cell.findViewById<View>(R.id.tableCellValueProgress).visibility)
        cell.showValueOrHide("2 DOT")
        assertEquals(View.GONE, cell.findViewById<View>(R.id.tableCellValueSecondary).visibility)
        cell.setValueColorRes(R.color.white)
        assertEquals(context.getColor(R.color.white), cell.valuePrimary.currentTextColor)
        cell.setTitle(R.string.common_account)
        assertEquals(context.getString(R.string.common_account), cell.title.text.toString())
        cell.setDividerVisible(true)
        assertEquals(View.VISIBLE, cell.findViewById<View>(R.id.tableCellValueDivider).visibility)
        cell.showValueOrHide(null)
        assertEquals(View.GONE, cell.visibility)
    }
}
