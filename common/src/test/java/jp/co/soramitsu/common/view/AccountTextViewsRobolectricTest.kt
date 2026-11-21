package jp.co.soramitsu.common.view

import android.content.Context
import android.graphics.Color
import android.graphics.Picture
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.PictureDrawable
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.address.AddressModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AccountTextViewsRobolectricTest {
    private val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_Soramitsu_Fearless)
    private val icon = ColorDrawable(Color.RED)

    @Test
    fun shouldApplyLabeledTextAttributesAndUpdateLabelsIconsAndActions() {
        val attrs = Robolectric.buildAttributeSet().addAttribute(R.attr.label, "Account")
            .addAttribute(R.attr.message, "Initial").addAttribute(R.attr.enabled, "false")
            .addAttribute(android.R.attr.singleLine, "false").build()
        val view = LabeledTextView(context, attrs)
        assertEquals("Account", view.findViewById<TextView>(R.id.labeledTextLabel).text.toString())
        assertEquals("Initial", view.findViewById<TextView>(R.id.labeledTextText).text.toString())
        assertEquals(View.GONE, view.findViewById<View>(R.id.labeledTextAction).visibility)
        view.isEnabled = true
        view.setLabel(R.string.common_account)
        view.setMessage(R.string.common_confirm)
        assertEquals(context.getString(R.string.common_account), view.findViewById<TextView>(R.id.labeledTextLabel).text.toString())
        assertEquals(context.getString(R.string.common_confirm), view.findViewById<TextView>(R.id.labeledTextText).text.toString())
        view.setActionIcon(icon)
        assertSame(icon, view.findViewById<ImageView>(R.id.labeledTextAction).drawable)
        view.setTextIcon(R.drawable.ic_vector)
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.labeledTextIcon).visibility)
        var clicks = 0
        view.setWholeClickListener { clicks++ }
        view.performClick()
        view.findViewById<View>(R.id.labeledTextAction).performClick()
        assertEquals(2, clicks)
    }

    @Test
    fun shouldDisplayAddressNamesAndPictureIcons() {
        val view = LabeledTextView(context)
        val picture = PictureDrawable(Picture())
        view.loadIcon(picture)
        assertSame(picture, view.findViewById<ImageView>(R.id.labeledTextIcon).drawable)
        view.setFromAddressModel(AddressModel("address", picture, "Alice"))
        assertEquals("Alice", view.findViewById<TextView>(R.id.labeledTextText).text.toString())
        view.setFromAddressModel(AddressModel("address", picture))
        assertEquals("address", view.findViewById<TextView>(R.id.labeledTextText).text.toString())
    }

    @Test
    fun shouldRenderAccountInformationAndToggleItsBodyAndAction() {
        val attrs = Robolectric.buildAttributeSet().addAttribute(R.attr.textVisible, "false")
            .addAttribute(R.attr.enabled, "false").addAttribute(R.attr.accountActionIcon, "@drawable/ic_vector").build()
        val view = AccountInfoView(context, attrs)
        assertTrue(view.isFocusable)
        assertEquals(View.GONE, view.findViewById<View>(R.id.accountAddressText).visibility)
        view.setTitle("Alice")
        view.setText("5address")
        view.setAccountIcon(icon)
        view.setActionIcon(icon)
        view.showBody()
        assertEquals("Alice", view.findViewById<TextView>(R.id.accountTitle).text.toString())
        assertEquals("5address", view.findViewById<TextView>(R.id.accountAddressText).text.toString())
        assertSame(icon, view.findViewById<ImageView>(R.id.accountIcon).drawable)
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.accountAddressText).visibility)
        view.hideBody()
        assertEquals(View.GONE, view.findViewById<View>(R.id.accountAddressText).visibility)
        view.isEnabled = true
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.accountAction).visibility)
        var clicks = 0
        view.setWholeClickListener { clicks++ }
        view.performClick()
        view.findViewById<View>(R.id.accountAction).performClick()
        assertEquals(2, clicks)
    }

    @Test
    fun shouldApplyProfileOptionAttributesAndRouteBothClickTargets() {
        val attrs = Robolectric.buildAttributeSet().addAttribute(R.attr.optionTitle, "Settings")
            .addAttribute(R.attr.optionSubtitle, "Details").addAttribute(R.attr.optionSubtitleVisible, "false")
            .addAttribute(R.attr.enabled, "false").build()
        val view = ProfileOptionItemView(context, attrs)
        assertEquals("Settings", view.findViewById<TextView>(R.id.optionTitle).text.toString())
        assertEquals("Details", view.findViewById<TextView>(R.id.optionSubtitle).text.toString())
        assertEquals(View.GONE, view.findViewById<View>(R.id.optionIcon).visibility)
        view.setIcon(icon)
        view.setActionIcon(icon)
        view.setAccountIcon(icon)
        assertSame(icon, view.findViewById<ImageView>(R.id.optionIcon).drawable)
        view.showBody()
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.optionSubtitle).visibility)
        view.hideBody()
        assertEquals(View.GONE, view.findViewById<View>(R.id.optionSubtitle).visibility)
        view.isEnabled = true
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.optionAction).visibility)
        var clicks = 0
        view.setWholeClickListener { clicks++ }
        view.performClick()
        view.findViewById<View>(R.id.optionAction).performClick()
        assertEquals(2, clicks)
        view.setIcon(null)
        assertEquals(View.GONE, view.findViewById<View>(R.id.optionIcon).visibility)
    }
}
