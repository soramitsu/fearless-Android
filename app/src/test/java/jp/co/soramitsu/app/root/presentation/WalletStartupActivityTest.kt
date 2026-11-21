package jp.co.soramitsu.app.root.presentation

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import jp.co.soramitsu.app.HiltShim
import jp.co.soramitsu.app.HiltShimApplication
import jp.co.soramitsu.app.idleMainUntil
import jp.co.soramitsu.app.root.domain.WalletDatabaseStartupGate
import jp.co.soramitsu.app.root.domain.WalletDatabaseStartupResult
import jp.co.soramitsu.app.root.domain.WalletStartupSession
import jp.co.soramitsu.app.root.presentation.emergency.WalletEmergencyExportActivity
import jp.co.soramitsu.app.root.presentation.keyloss.WalletKeyLossRecoveryActivity
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageHealthTestHooks
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
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
import org.robolectric.shadows.ShadowProcess
import jp.co.soramitsu.common.R as CommonR

/** The production startup screens (WalletStartupActivity and the legacy RootActivity) with a mocked startup gate. */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltShimApplication::class)
class WalletStartupActivityTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val gate = mockk<WalletDatabaseStartupGate>()
    private val controllers = mutableListOf<ActivityController<out WalletGateActivity>>()

    @Before
    fun setUp() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        WalletSecureStorageHealthTestHooks.reset()
        WalletStartupSession.resetForTest()
        ShadowProcess.clearKilledProcesses()
        HiltShim.reset()
        HiltShim.inject(WalletStartupActivity::class) { it.databaseStartupGate = gate }
        HiltShim.inject(RootActivity::class) { it.databaseStartupGate = gate }
    }

    @After
    fun tearDown() {
        controllers.filterNot { it.get().isDestroyed }.forEach { it.pause().stop().destroy() }
        WalletStartupSession.resetForTest()
        WalletSecureStorageHealthTestHooks.reset()
        HiltShim.reset()
    }

    @Test
    fun readyGateForwardsOnlyTheAllowlistedEntryToTheWalletHost() {
        coEvery { gate.open() } returns WalletDatabaseStartupResult.Ready
        val source = Intent(context, WalletStartupActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse("fearless://buy-success?status=done"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .putExtra("android-support-nav:controller:deepLinkIds", intArrayOf(42))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val startup = launch(WalletStartupActivity::class.java, source)
        idleMainUntil { startup.get().isFinishing }

        val forwarded = shadowOf(startup.get()).nextStartedActivity
        assertEquals(WalletRootActivity::class.java.name, forwarded.component?.className)
        assertEquals(Intent.ACTION_VIEW, forwarded.action)
        assertEquals(Uri.parse("fearless://buy-success?status=done"), forwarded.data)
        assertNull(forwarded.extras)
        assertNull(forwarded.categories)
        assertEquals(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP, forwarded.flags)
        coVerify(exactly = 1) { gate.open() }
    }

    @Test
    fun anUnreadableDatabaseOffersRetryAndEmergencyAccess() {
        coEvery { gate.open() } returns WalletDatabaseStartupResult.DatabaseOpenFailed
        val startup = launch(WalletStartupActivity::class.java, launcher())
        val sheet = awaitSheet(null)
        assertEquals(string(CommonR.string.wallet_database_unavailable_title), sheetText(sheet, CommonR.id.alertTitle))
        assertEquals(string(CommonR.string.wallet_database_unavailable_message), sheetText(sheet, CommonR.id.alertMessage))
        val emergency = sheet.findViewById<View>(CommonR.id.alertSecondaryButton)
        assertEquals(View.VISIBLE, emergency.visibility)
        emergency.performClick()
        assertEquals(WalletEmergencyExportActivity::class.java.name, shadowOf(startup.get()).nextStartedActivity.component?.className)
        assertTrue("The secondary action keeps the sheet open", sheet.isShowing)

        coEvery { gate.open() } returns WalletDatabaseStartupResult.Ready
        sheet.findViewById<View>(CommonR.id.alertConfirmButton).performClick()
        idleMainUntil { startup.get().isFinishing }
        coVerify(exactly = 2) { gate.open() }
        assertEquals(WalletRootActivity::class.java.name, shadowOf(startup.get()).nextStartedActivity.component?.className)
    }

    @Test
    fun unavailableSecureStorageOnlyOffersRetry() {
        coEvery { gate.open() } returns WalletDatabaseStartupResult.SecureStorageUnavailable
        launch(WalletStartupActivity::class.java, launcher())
        val sheet = awaitSheet(null)
        assertEquals(string(CommonR.string.wallet_secure_storage_unavailable_title), sheetText(sheet, CommonR.id.alertTitle))
        assertEquals(View.GONE, sheet.findViewById<View>(CommonR.id.alertSecondaryButton).visibility)
    }

    @Test
    fun lostWalletMasterKeyOpensTheKeyLossRecovery() {
        coEvery { gate.open() } returns WalletDatabaseStartupResult.RecoveryRequired("WALLET_MASTER_KEY_RECOVERY")
        val startup = launch(WalletStartupActivity::class.java, launcher())
        idleMainUntil { startup.get().isFinishing }
        assertEquals(WalletKeyLossRecoveryActivity::class.java.name, shadowOf(startup.get()).nextStartedActivity.component?.className)
    }

    @Test
    fun otherRecoveryFailuresShowTheirDiagnosticAndReturnAfterDismissal() {
        coEvery { gate.open() } returns WalletDatabaseStartupResult.RecoveryRequired("TON_CONNECT_JOURNAL_RECOVERY")
        val startup = launch(WalletStartupActivity::class.java, launcher())
        startup.windowFocusChanged(true)
        val first = awaitSheet(null)
        assertEquals(string(CommonR.string.wallet_recovery_required_title), sheetText(first, CommonR.id.alertTitle))
        assertEquals(
            startup.get().getString(CommonR.string.wallet_startup_recovery_required_message, "TON_CONNECT_JOURNAL_RECOVERY"),
            sheetText(first, CommonR.id.alertMessage)
        )
        first.findViewById<View>(CommonR.id.alertSecondaryButton).performClick()
        assertEquals(WalletEmergencyExportActivity::class.java.name, shadowOf(startup.get()).nextStartedActivity.component?.className)

        first.dismiss()
        val second = awaitSheet(first)
        assertEquals(string(CommonR.string.wallet_recovery_required_title), sheetText(second, CommonR.id.alertTitle))
        coVerify(exactly = 1) { gate.open() }
    }

    @Test
    fun restartRequiredClosesTheWholeProcess() {
        coEvery { gate.open() } returns WalletDatabaseStartupResult.ProcessRestartRequired
        val startup = launch(WalletStartupActivity::class.java, launcher())
        val sheet = awaitSheet(null)
        assertEquals(string(CommonR.string.wallet_startup_restart_required_title), sheetText(sheet, CommonR.id.alertTitle))
        sheet.findViewById<View>(CommonR.id.alertConfirmButton).performClick()
        assertTrue(startup.get().isFinishing)
        assertTrue(ShadowProcess.wasKilled(Process.myPid()))
    }

    @Test
    fun aSecondWaiterThatLosesTheFailureLeaseSimplyCloses() {
        coEvery { gate.open() } returns WalletDatabaseStartupResult.DatabaseOpenFailed
        launch(WalletStartupActivity::class.java, launcher())
        awaitSheet(null)
        val second = launch(RootActivity::class.java, Intent(context, RootActivity::class.java).setAction(Intent.ACTION_MAIN))
        idleMainUntil { second.get().isFinishing }
        coVerify(exactly = 1) { gate.open() }
    }

    @Test
    fun legacyRootActivityTasksUseTheSameGateAndNewEntriesTakeTheFastPath() {
        coEvery { gate.open() } returns WalletDatabaseStartupResult.Ready
        val legacy = launch(RootActivity::class.java, Intent(context, RootActivity::class.java).setAction(Intent.ACTION_MAIN))
        idleMainUntil { legacy.get().isFinishing }
        assertEquals(WalletRootActivity::class.java.name, shadowOf(legacy.get()).nextStartedActivity.component?.className)

        val startup = launch(WalletStartupActivity::class.java, launcher())
        idleMainUntil { startup.get().isFinishing }
        shadowOf(startup.get()).nextStartedActivity
        startup.newIntent(
            Intent(context, WalletStartupActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setData(Uri.parse("https://fearlesswallet.io/ton-connect/v2?id=1"))
        )
        idleMainUntil { shadowOf(startup.get()).peekNextStartedActivity() != null }
        val forwarded = shadowOf(startup.get()).nextStartedActivity
        assertEquals(Uri.parse("https://fearlesswallet.io/ton-connect/v2?id=1"), forwarded.data)
        coVerify(exactly = 1) { gate.open() }
    }

    @Test
    fun nonAllowlistedEntriesStartAtTheLauncherAction() {
        val hostile = Intent(Intent.ACTION_VIEW, Uri.parse("content://elsewhere/doc"))
            .putExtra("entry", "hostile")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val reduced = hostile.toWalletInternalHandoff(context, WalletRootActivity::class.java)
        assertEquals(Intent.ACTION_MAIN, reduced.action)
        assertNull(reduced.data)
        assertNull(reduced.extras)
        assertEquals(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP, reduced.flags)

        val document = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse("content://downloads/wallet.json"), "application/json")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val imported = document.toWalletInternalHandoff(context, WalletRootActivity::class.java)
        assertEquals(Intent.ACTION_VIEW, imported.action)
        assertEquals("application/json", imported.type)
        assertEquals(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_GRANT_READ_URI_PERMISSION,
            imported.flags
        )
    }

    private fun <T : WalletGateActivity> launch(type: Class<T>, intent: Intent): ActivityController<T> =
        Robolectric.buildActivity(type, intent).setup().also { controllers += it }

    private fun launcher(): Intent = Intent(context, WalletStartupActivity::class.java).setAction(Intent.ACTION_MAIN)

    private fun awaitSheet(previous: Dialog?): Dialog {
        idleMainUntil { ShadowDialog.getLatestDialog()?.let { it !== previous && it.isShowing } == true }
        return ShadowDialog.getLatestDialog().also { assertNotSame(previous, it) }
    }

    private fun sheetText(sheet: Dialog, id: Int): String = sheet.findViewById<TextView>(id).text.toString()

    private fun string(id: Int): String = context.getString(id)

}
