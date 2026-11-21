package jp.co.soramitsu.common.utils

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
class ViewExtensionsRobolectricTest {
    @Test
    fun shouldUpdateVisibilityPaddingTextAndDrawableSizing() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val text = TextView(activity)
        text.setPadding(1, 2, 3, 4)
        text.updatePadding(top = 10, end = 20)
        assertEquals(listOf(1, 10, 20, 4), listOf(text.paddingLeft, text.paddingTop, text.paddingRight, text.paddingBottom))
        text.makeInvisible()
        assertEquals(View.INVISIBLE, text.visibility)
        text.makeGone()
        assertEquals(View.GONE, text.visibility)
        text.makeVisible()
        assertEquals(View.VISIBLE, text.visibility)
        text.setTextOrHide(null)
        assertEquals(View.GONE, text.visibility)
        text.setTextOrHide("Balance")
        assertEquals("Balance", text.text.toString())
        assertEquals(View.VISIBLE, text.visibility)
        text.setVisible(false, View.INVISIBLE)
        assertEquals(View.INVISIBLE, text.visibility)
        text.setTextColorRes(R.color.white)
        assertEquals(activity.getColor(R.color.white), text.currentTextColor)
        text.setDrawableStart(R.drawable.ic_info_14, widthInDp = 20, heightInDp = 15, tint = R.color.white)
        assertEquals((20 * activity.resources.displayMetrics.density).toInt(), text.compoundDrawablesRelative[0].bounds.width())
        text.setCompoundDrawableTint(R.color.white)
        assertEquals(activity.getColor(R.color.white), TextViewCompat.getCompoundDrawableTintList(text)?.defaultColor)
        text.setDrawableStart(ColorDrawable(Color.RED), 12, 8, R.color.white)
        assertEquals((8 * activity.resources.displayMetrics.density).toInt(), text.compoundDrawablesRelative[0].bounds.height())
        text.setDrawableStart(start = null)
        assertNull(text.compoundDrawablesRelative[0])
        activity.finish()
    }

    @Test
    fun shouldBindEditableTextBothWaysWithoutDuplicateChanges() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val input = EditText(activity)
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle = registry
        }
        owner.registry.currentState = Lifecycle.State.RESUMED
        val text = MutableLiveData("initial")
        input.bindTo(text, owner)
        assertEquals("initial", input.text.toString())
        input.setText("typed")
        assertEquals("typed", text.value)
        text.value = "model"
        assertEquals("model", input.text.toString())
        owner.registry.currentState = Lifecycle.State.DESTROYED
        activity.finish()
    }

    @Test
    fun shouldLaunchBrowserAndEmailIntentsAndDisplayToastText() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.showBrowser("https://example.invalid/help")
        val browser = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, browser.action)
        assertEquals("https://example.invalid/help", browser.dataString)
        activity.createSendEmailIntent("help@example.invalid", "Support")
        val chooser = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val email = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SENDTO, email.action)
        assertEquals("mailto:help@example.invalid", email.dataString)
        activity.showToast("Copied")
        assertEquals("Copied", ShadowToast.getTextOfLatestToast())
        activity.finish()
    }

    @Test
    fun shouldRunGlobalLayoutCallbacksOnlyOnceAndInflateWithoutAttaching() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val parent = FrameLayout(activity)
        var layouts = 0
        parent.doOnGlobalLayout { layouts++ }
        parent.viewTreeObserver.dispatchOnGlobalLayout()
        parent.viewTreeObserver.dispatchOnGlobalLayout()
        assertEquals(1, layouts)
        val child = parent.inflateChild(android.R.layout.simple_list_item_1)
        assertNull(child.parent)
        assertEquals(0, parent.childCount)
        activity.finish()
    }

    @Test
    fun shouldNormalizeDisplayStringsAndNumbers() {
        assertEquals("#112233", 0x80112233.toInt().toHexColor())
        assertEquals("a+b%2Fc", "a b/c".urlEncoded())
        assertFalse(0.asBoolean())
        assertTrue((-1).asBoolean())
        assertEquals(listOf(4, 3, 1), "4.3.1".splitVersions())
        assertEquals("Wallet Balance", "walletBalance".camelCaseToCapitalizedWords())
        assertEquals("0", String.ZERO)
        assertEquals('0', Char.ZERO)
        assertEquals("failed", Result.failure<Int>("failed").exceptionOrNull()?.message)
    }
}
