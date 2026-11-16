package jp.co.soramitsu.app.root.presentation

import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import jp.co.soramitsu.app.HiltShimApplication
import jp.co.soramitsu.app.HiltShimHostActivity
import jp.co.soramitsu.app.root.domain.InitializationStep
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.view.bottomSheet.AlertBottomSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import android.os.Looper
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltShimApplication::class)
class StartupConnectionAlertTest {
    private val activity: AppCompatActivity = Robolectric.buildActivity(HiltShimHostActivity::class.java).setup().get()

    @Test
    fun settingsAndNetworkFailuresExplainWhatCouldNotRefreshAndRetryThatStep() {
        val retried = mutableListOf<InitializationStep>()
        val settings = createStartupConnectionAlert(activity, InitializationStep.AppConfig) { retried += it }.shown()
        assertEquals(activity.getString(R.string.common_warning), settings.text(R.id.alertTitle))
        assertEquals(activity.getString(R.string.startup_settings_refresh_unavailable), settings.text(R.id.alertMessage))
        assertEquals(activity.getString(R.string.common_retry), settings.text(R.id.alertConfirmButton))
        settings.findViewById<View>(R.id.alertConfirmButton)!!.performClick()
        idle()
        assertFalse(settings.isShowing)

        val network = createStartupConnectionAlert(activity, InitializationStep.ChainsConfig) { retried += it }.shown()
        assertEquals(activity.getString(R.string.startup_network_refresh_unavailable), network.text(R.id.alertMessage))
        assertEquals(activity.getString(R.string.common_continue), network.text(R.id.alertSecondaryButton))
        network.findViewById<View>(R.id.alertSecondaryButton)!!.performClick()
        idle()
        assertFalse("Continue dismisses without retrying", network.isShowing)
        assertEquals(listOf(InitializationStep.AppConfig), retried)
    }

    @Test
    fun updateNoticeOffersTheStoreButKeepsTheWalletReachable() {
        var updates = 0
        val alert = createStartupVersionAlert(activity) { updates++ }.shown()
        assertEquals(activity.getString(R.string.update_needed_text), alert.text(R.id.alertTitle))
        assertEquals(activity.getString(R.string.startup_app_update_notice), alert.text(R.id.alertMessage))
        assertEquals(activity.getString(R.string.common_update), alert.text(R.id.alertConfirmButton))
        alert.findViewById<View>(R.id.alertSecondaryButton)!!.performClick()
        idle()
        assertFalse(alert.isShowing)
        assertEquals(0, updates)

        val again = createStartupVersionAlert(activity) { updates++ }.shown()
        again.findViewById<View>(R.id.alertConfirmButton)!!.performClick()
        idle()
        assertEquals(1, updates)
        assertFalse(again.isShowing)
    }

    private fun AlertBottomSheet.shown(): AlertBottomSheet = apply {
        show()
        idle()
        assertTrue(isShowing)
    }

    private fun AlertBottomSheet.text(id: Int): String = findViewById<TextView>(id)!!.text.toString()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
