package jp.co.soramitsu.common.view.bottomSheet

import android.app.Activity
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.view.PrimaryButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AlertBottomSheetRobolectricTest {
    @Test
    fun shouldKeepSecondaryActionOpenAndDismissAfterConfirmation() {
        val controller = Robolectric.buildActivity(Activity::class.java)
        controller.get().setTheme(R.style.Theme_Soramitsu_Fearless)
        controller.setup()
        val events = mutableListOf<String>()
        val sheet = AlertBottomSheet.Builder(controller.get())
            .setTitle("Network unavailable").setMessage("Try a different connection")
            .setButtonText("Retry").setIcon(R.drawable.ic_info_14)
            .setSecondaryButton(R.string.common_cancel) { events += "secondary" }
            .callback { events += "confirm" }.onDismiss { events += "dismiss" }
            .setCancelable(false).build()
        try {
            sheet.show()
            assertEquals("Network unavailable", sheet.findViewById<TextView>(R.id.alertTitle)?.text.toString())
            assertEquals("Try a different connection", sheet.findViewById<TextView>(R.id.alertMessage)?.text.toString())
            assertEquals(BottomSheetBehavior.STATE_EXPANDED, sheet.behavior.state)
            assertTrue(sheet.behavior.skipCollapsed)
            sheet.findViewById<Button>(R.id.alertSecondaryButton)!!.performClick()
            assertEquals(listOf("secondary"), events)
            assertTrue(sheet.isShowing)
            sheet.findViewById<PrimaryButton>(R.id.alertConfirmButton)!!.performClick()
            assertFalse(sheet.isShowing)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf("secondary", "confirm", "dismiss"), events)
        } finally {
            sheet.dismiss()
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun shouldResolveResourceLabelsAndHideTheAbsentSecondaryAction() {
        val controller = Robolectric.buildActivity(Activity::class.java)
        controller.get().setTheme(R.style.Theme_Soramitsu_Fearless)
        controller.setup()
        val context = controller.get()
        val sheet = AlertBottomSheet.Builder(context).setTitle(R.string.common_error_general_title)
            .setMessage(R.string.network_issue_unavailable).setButtonText(R.string.common_close).build()
        try {
            sheet.show()
            assertEquals(context.getString(R.string.common_error_general_title), sheet.findViewById<TextView>(R.id.alertTitle)?.text.toString())
            assertEquals(context.getString(R.string.network_issue_unavailable), sheet.findViewById<TextView>(R.id.alertMessage)?.text.toString())
            assertEquals(View.GONE, sheet.findViewById<Button>(R.id.alertSecondaryButton)?.visibility)
            assertEquals(context.getString(R.string.common_close), sheet.findViewById<PrimaryButton>(R.id.alertConfirmButton)?.text.toString())
        } finally {
            sheet.dismiss()
            controller.pause().stop().destroy()
        }
    }
}
