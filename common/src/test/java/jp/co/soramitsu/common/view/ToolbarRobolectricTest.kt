package jp.co.soramitsu.common.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import jp.co.soramitsu.common.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ToolbarRobolectricTest {
    private val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_Soramitsu_Fearless)

    @Test
    fun shouldApplyXmlTitleIconsVisibilityAndRightActionText() {
        val attrs = Robolectric.buildAttributeSet().addAttribute(R.attr.titleText, "Wallet")
            .addAttribute(R.attr.iconRight, "@drawable/ic_vector").addAttribute(R.attr.textRight, "Edit")
            .addAttribute(R.attr.homeButtonIcon, "@drawable/ic_vector").addAttribute(R.attr.homeButtonVisible, "false")
            .addAttribute(R.attr.dividerVisible, "false").build()
        val toolbar = Toolbar(context, attrs)
        assertEquals("Wallet", toolbar.findViewById<TextView>(R.id.titleTv).text.toString())
        assertEquals("Edit", toolbar.rightActionText.text.toString())
        assertEquals(View.GONE, toolbar.findViewById<View>(R.id.backImg).visibility)
        assertEquals(View.GONE, toolbar.findViewById<View>(R.id.toolbarDivider).visibility)
        toolbar.setTitle(R.string.common_account)
        assertEquals(context.getString(R.string.common_account), toolbar.findViewById<TextView>(R.id.titleTv).text.toString())
        toolbar.showHomeButton()
        assertEquals(View.VISIBLE, toolbar.findViewById<View>(R.id.backImg).visibility)
        toolbar.hideHomeButton()
        assertEquals(View.GONE, toolbar.findViewById<View>(R.id.backImg).visibility)
        toolbar.setHomeButtonVisibility(true)
        toolbar.setDividerVisible(true)
        assertEquals(View.VISIBLE, toolbar.findViewById<View>(R.id.toolbarDivider).visibility)
    }

    @Test
    fun shouldRouteHomeTextAndIconActionsAndPlaceCustomActions() {
        val toolbar = Toolbar(context)
        val icon = ColorDrawable(Color.RED)
        toolbar.setHomeButtonIcon(icon)
        assertSame(icon, toolbar.findViewById<ImageView>(R.id.backImg).drawable)
        var homes = 0
        var rights = 0
        toolbar.setHomeButtonListener { homes++ }
        toolbar.setRightActionClickListener { rights++ }
        toolbar.findViewById<View>(R.id.backImg).performClick()
        toolbar.setTextRight("Edit")
        toolbar.rightActionText.performClick()
        toolbar.setRightIconDrawable(icon)
        assertSame(icon, toolbar.findViewById<ImageView>(R.id.rightImg).drawable)
        assertEquals(View.GONE, toolbar.rightActionText.visibility)
        toolbar.findViewById<View>(R.id.rightImg).performClick()
        assertEquals(1, homes)
        assertEquals(2, rights)
        val clicks = mutableListOf<String>()
        toolbar.addCustomAction(R.drawable.ic_vector) { clicks += "first" }
        toolbar.addCustomAction(R.drawable.ic_vector) { clicks += "second" }
        val actions = toolbar.findViewById<LinearLayout>(R.id.toolbarCustomActions)
        assertEquals(2, actions.childCount)
        actions.getChildAt(0).performClick()
        actions.getChildAt(1).performClick()
        assertEquals(listOf("second", "first"), clicks)
        val density = context.resources.displayMetrics.density
        assertEquals((10 * density).toInt(), (actions.getChildAt(0).layoutParams as LinearLayout.LayoutParams).rightMargin)
        assertEquals((16 * density).toInt(), (actions.getChildAt(1).layoutParams as LinearLayout.LayoutParams).rightMargin)
    }
}
