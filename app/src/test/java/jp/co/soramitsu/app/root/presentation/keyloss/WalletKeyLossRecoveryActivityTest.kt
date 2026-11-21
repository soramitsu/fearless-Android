package jp.co.soramitsu.app.root.presentation.keyloss

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.app.HiltShim
import jp.co.soramitsu.app.HiltShimApplication
import jp.co.soramitsu.app.idleMainUntil
import jp.co.soramitsu.app.root.domain.WalletDatabaseStartupGate
import jp.co.soramitsu.app.root.domain.WalletDatabaseStartupResult
import jp.co.soramitsu.app.root.domain.WalletStartupSession
import jp.co.soramitsu.app.root.domain.keyloss.WalletPublicSnapshot
import jp.co.soramitsu.app.root.domain.keyloss.WalletPublicSnapshotReader
import jp.co.soramitsu.app.root.presentation.WalletStartupActivity
import jp.co.soramitsu.app.root.presentation.emergency.WalletEmergencyExportActivity
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.data.storage.encrypt.EncryptionUtil
import jp.co.soramitsu.common.data.storage.encrypt.WalletKeyLossConsent
import jp.co.soramitsu.common.data.storage.encrypt.WalletKeyLossRetry
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageFailureKind
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltShimApplication::class)
class WalletKeyLossRecoveryActivityTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val encryption = mockk<EncryptionUtil>(relaxed = true)
    private val startupGate = mockk<WalletDatabaseStartupGate>()
    private val publicReader = mockk<WalletPublicSnapshotReader>()
    private val consent = mockk<WalletKeyLossConsent>()
    private lateinit var controller: ActivityController<WalletKeyLossRecoveryActivity>
    private val activity get() = controller.get()

    @Before
    fun setUp() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        WalletStartupSession.resetForTest()
        HiltShim.reset()
        HiltShim.inject(WalletKeyLossRecoveryActivity::class) { screen ->
            screen.encryption = encryption
            screen.startupGate = startupGate
            screen.publicReader = publicReader
        }
        coEvery { startupGate.open() } returns WalletDatabaseStartupResult.Ready
    }

    @After
    fun tearDown() {
        if (::controller.isInitialized && !activity.isDestroyed) controller.pause().stop().destroy()
        WalletStartupSession.resetForTest()
        HiltShim.reset()
    }

    @Test
    fun homeOffersRetryEmergencyAccessAndCloseOnAnUnsecuredDevice() {
        launch(deviceSecure = false)
        assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        assertEquals(listOf(R.string.wallet_keyloss_title, R.string.wallet_keyloss_intro).map { string(it) }, texts().take(2))
        assertEquals(
            listOf(R.string.wallet_keyloss_retry, R.string.wallet_emergency_open, R.string.common_close).map { string(it) },
            buttonLabels()
        )
        button(R.string.wallet_emergency_open).performClick()
        assertEquals(WalletEmergencyExportActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
        button(R.string.common_close).performClick()
        assertTrue(activity.isFinishing)
    }

    @Test
    fun retryOutcomesExplainTheNextStepAndOfferAFreshStartOnlyWithConsent() {
        launch(deviceSecure = false)
        every { encryption.retryPreviousSecureStorage() } returns WalletKeyLossRetry.RestartDevice
        button(R.string.wallet_keyloss_retry).performClick()
        idleMainUntil { texts().contains(string(R.string.wallet_keyloss_restart_device)) }
        assertFalse(buttonLabels().contains(string(R.string.wallet_keyloss_start)))

        every { encryption.retryPreviousSecureStorage() } returns WalletKeyLossRetry.RetryLater
        button(R.string.wallet_keyloss_retry).performClick()
        idleMainUntil { texts().contains(string(R.string.wallet_keyloss_retry_later)) }

        every { encryption.retryPreviousSecureStorage() } returns WalletKeyLossRetry.CanStart(consent)
        button(R.string.wallet_keyloss_retry).performClick()
        idleMainUntil { texts().contains(string(R.string.wallet_keyloss_can_start)) }
        assertTrue(buttonLabels().contains(string(R.string.wallet_keyloss_start)))

        every { encryption.retryPreviousSecureStorage() } throws WalletSecureStorageUnavailableException(
            "latched", kind = WalletSecureStorageFailureKind.PROCESS_RESTART_REQUIRED
        )
        button(R.string.wallet_keyloss_retry).performClick()
        idleMainUntil { texts().contains(string(R.string.wallet_startup_restart_required_message)) }
        assertFalse("A failed retry withdraws the consent", buttonLabels().contains(string(R.string.wallet_keyloss_start)))

        every { encryption.retryPreviousSecureStorage() } throws IllegalStateException("keystore")
        button(R.string.wallet_keyloss_retry).performClick()
        idleMainUntil { texts().contains(string(R.string.wallet_keyloss_retry_later)) }
    }

    @Test
    fun recoveredStorageRechecksTheStartupGateBeforeReturningToStartup() {
        launch(deviceSecure = false)
        every { encryption.retryPreviousSecureStorage() } returns WalletKeyLossRetry.Recovered
        button(R.string.wallet_keyloss_retry).performClick()
        idleMainUntil { activity.isFinishing }
        coVerify(exactly = 1) { startupGate.open() }
        assertEquals(WalletStartupActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
    }

    @Test
    fun freshStartNeedsTheBackupConfirmationBeforeItCanProceed() {
        launch(deviceSecure = false)
        every { encryption.retryPreviousSecureStorage() } returns WalletKeyLossRetry.CanStart(consent)
        button(R.string.wallet_keyloss_retry).performClick()
        idleMainUntil { buttonLabels().contains(string(R.string.wallet_keyloss_start)) }

        button(R.string.wallet_keyloss_start).performClick()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        val proceed = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        assertFalse(proceed.isEnabled)
        val confirmation = descendants(dialog.window!!.decorView).filterIsInstance<CheckBox>().single()
        assertEquals(string(R.string.wallet_keyloss_backup_confirmation), confirmation.text.toString())
        confirmation.isChecked = true
        assertTrue(proceed.isEnabled)
        confirmation.isChecked = false
        proceed.performClick()
        assertTrue("An unchecked confirmation keeps the dialog open", dialog.isShowing)
        confirmation.isChecked = true
        proceed.performClick()

        idleMainUntil { activity.isFinishing }
        verify(exactly = 1) { encryption.beginFreshKeySlot(consent, true) }
        coVerify(exactly = 1) { startupGate.open() }
        assertEquals(WalletStartupActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
    }

    @Test
    fun securedDeviceConfirmsCredentialsBeforeShowingCachedPublicData() {
        launch(deviceSecure = true)
        every { publicReader.read() } returns WalletPublicSnapshot(
            wallets = listOf(
                WalletPublicSnapshot.Wallet(
                    name = "Main wallet",
                    identities = listOf(
                        "substrateAccountId" to "aa11",
                        "ethereumAddress" to "0123456789abcdef0123456789abcdef01234567",
                        "tonPublicKey" to "ton-key",
                        "address" to "5Fsora",
                        "publicKey" to "pub"
                    ),
                    balances = listOf(WalletPublicSnapshot.Balance("polkadot", "dot", "1.5 DOT"))
                )
            ),
            incomplete = true
        )
        button(R.string.wallet_keyloss_view).performClick()
        val confirm = shadowOf(activity).nextStartedActivityForResult
        assertNotNull(confirm)
        shadowOf(activity).receiveResult(confirm.intent, Activity.RESULT_OK, null)
        idleMainUntil { texts().contains("Main wallet") }

        val shown = texts()
        assertTrue(shown.contains(string(R.string.wallet_keyloss_cached_warning)))
        assertTrue(shown.contains(string(R.string.wallet_keyloss_incomplete)))
        assertTrue(shown.contains(activity.getString(R.string.wallet_keyloss_substrate_id, "aa11")))
        assertTrue(shown.contains(activity.getString(R.string.wallet_keyloss_evm_address, "0x0123456789abcdef0123456789abcdef01234567")))
        assertTrue(shown.contains(activity.getString(R.string.wallet_keyloss_ton_public, "ton-key")))
        assertTrue(shown.contains(activity.getString(R.string.wallet_keyloss_address, "5Fsora")))
        assertTrue(shown.contains(activity.getString(R.string.wallet_keyloss_public, "pub")))
        assertTrue(shown.contains(activity.getString(R.string.wallet_keyloss_balance, "polkadot", "dot", "1.5 DOT")))

        button(R.string.wallet_keyloss_back).performClick()
        assertTrue(texts().contains(string(R.string.wallet_keyloss_intro)))
    }

    @Test
    fun cancelledCredentialsReturnHomeAndAnEmptySnapshotSaysSo() {
        launch(deviceSecure = true)
        every { publicReader.read() } returns WalletPublicSnapshot(emptyList(), incomplete = false)
        button(R.string.wallet_keyloss_view).performClick()
        val cancelled = shadowOf(activity).nextStartedActivityForResult
        shadowOf(activity).receiveResult(cancelled.intent, Activity.RESULT_CANCELED, null)
        assertTrue(texts().contains(string(R.string.wallet_keyloss_intro)))

        button(R.string.wallet_keyloss_view).performClick()
        val accepted = shadowOf(activity).nextStartedActivityForResult
        shadowOf(activity).receiveResult(accepted.intent, Activity.RESULT_OK, null)
        idleMainUntil { texts().contains(string(R.string.wallet_keyloss_no_cached)) }
        assertFalse(texts().contains(string(R.string.wallet_keyloss_incomplete)))
    }

    @Test
    fun securedFreshStartAuthenticatesThenActivates() {
        launch(deviceSecure = true)
        every { encryption.retryPreviousSecureStorage() } returns WalletKeyLossRetry.CanStart(consent)
        button(R.string.wallet_keyloss_retry).performClick()
        idleMainUntil { buttonLabels().contains(string(R.string.wallet_keyloss_start)) }
        button(R.string.wallet_keyloss_start).performClick()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        descendants(dialog.window!!.decorView).filterIsInstance<CheckBox>().single().isChecked = true
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()

        val confirm = shadowOf(activity).nextStartedActivityForResult
        assertNotNull(confirm)
        shadowOf(activity).receiveResult(confirm.intent, Activity.RESULT_OK, null)
        idleMainUntil { activity.isFinishing }
        verify(exactly = 1) { encryption.beginFreshKeySlot(consent, true) }
    }

    @Test
    fun leavingTheScreenWithdrawsAnOfferedFreshStart() {
        launch(deviceSecure = false)
        every { encryption.retryPreviousSecureStorage() } returns WalletKeyLossRetry.CanStart(consent)
        button(R.string.wallet_keyloss_retry).performClick()
        idleMainUntil { buttonLabels().contains(string(R.string.wallet_keyloss_start)) }
        controller.pause().stop()
        assertFalse(buttonLabels().contains(string(R.string.wallet_keyloss_start)))
        assertTrue(texts().contains(string(R.string.wallet_keyloss_intro)))
        controller.start().resume()
    }

    private fun launch(deviceSecure: Boolean) {
        shadowOf(context.getSystemService(KeyguardManager::class.java)).setIsDeviceSecure(deviceSecure)
        controller = Robolectric.buildActivity(
            WalletKeyLossRecoveryActivity::class.java,
            WalletKeyLossRecoveryActivity.intent(context)
        ).setup()
    }

    private fun content(): LinearLayout {
        val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ScrollView
        return root.getChildAt(0) as LinearLayout
    }

    private fun children(): List<View> = (0 until content().childCount).map { content().getChildAt(it) }
    private fun texts(): List<String> = children().filter { it !is Button }.filterIsInstance<TextView>().map { it.text.toString() }
    private fun buttonLabels(): List<String> = children().filterIsInstance<Button>().map { it.text.toString() }
    private fun button(label: Int): Button = children().filterIsInstance<Button>().single { it.text.toString() == string(label) }
    private fun string(id: Int): String = activity.getString(id)

    private fun descendants(view: View): List<View> =
        if (view is ViewGroup) listOf(view) + (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else listOf(view)
}
