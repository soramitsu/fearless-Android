package jp.co.soramitsu.app.root.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ApplicationProvider
import jp.co.soramitsu.app.HiltShimApplication
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.idleMainUntil
import jp.co.soramitsu.app.root.domain.WalletDatabaseStartupResult
import jp.co.soramitsu.app.root.domain.WalletStartupSession
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageHealthTestHooks
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/** The startup gate's lifecycle contract, driven through the debug-only WalletGateActivityTestHost. */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltShimApplication::class)
class WalletGateActivityHostTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val controllers = mutableListOf<ActivityController<WalletGateActivityTestHost>>()
    private val gate = WalletGateActivityTestController

    @Before
    fun setUp() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        WalletSecureStorageHealthTestHooks.reset()
        WalletStartupSession.resetForTest()
        gate.reset()
    }

    @After
    fun tearDown() {
        controllers.filterNot { it.get().isDestroyed }.forEach { it.pause().stop().destroy() }
        WalletStartupSession.resetForTest()
        WalletSecureStorageHealthTestHooks.reset()
        gate.reset()
    }

    @Test
    fun startupScreenUsesTheWalletHostLayoutAndHandsTheLaunchOverOnceReady() {
        val host = launch(launcherIntent("layout"))
        val flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        assertEquals(flags, host.get().window.decorView.systemUiVisibility and flags)
        assertTrue(gate.awaitCheckStarted())
        assertTrue(gate.complete(WalletDatabaseStartupResult.Ready))
        idleMainUntil { gate.walletOpenCount.get() == 1 }
        assertEquals(Intent.ACTION_MAIN, gate.forwardedIntent.get()?.action)
        assertEquals("layout", gate.forwardedIntent.get()?.getStringExtra("entry"))
        assertEquals(1, gate.startupCheckCount.get())
        assertEquals(0, gate.errorCount.get())
    }

    @Test
    fun readyResultWhileStoppedWaitsForResumeBeforeTheHandoff() {
        val host = launch(launcherIntent("stopped"))
        assertTrue(gate.awaitCheckStarted())
        host.pause().stop()
        gate.complete(WalletDatabaseStartupResult.Ready)
        idleMainUntil { WalletStartupSession.isReady() }
        assertEquals(0, gate.walletOpenCount.get())
        host.start().resume()
        idleMainUntil { gate.walletOpenCount.get() == 1 }
    }

    @Test
    fun everyFailurePresentsItsOwnExplanation() {
        val expectations = listOf(
            WalletDatabaseStartupResult.SecureStorageUnavailable to R.string.wallet_secure_storage_unavailable_title,
            WalletDatabaseStartupResult.DatabaseOpenFailed to R.string.wallet_database_unavailable_title,
            WalletDatabaseStartupResult.ProcessRestartRequired to R.string.wallet_startup_restart_required_title,
            WalletDatabaseStartupResult.RecoveryRequired("TON_CONNECT_JOURNAL_RECOVERY") to R.string.wallet_recovery_required_title
        )
        expectations.forEach { (result, title) ->
            WalletSecureStorageHealthTestHooks.reset()
            WalletStartupSession.resetForTest()
            gate.reset()
            val host = launch(launcherIntent("failure"))
            assertTrue(gate.awaitCheckStarted())
            gate.complete(result)
            idleMainUntil { gate.errorCount.get() == 1 }
            assertEquals(context.getString(title), gate.errorPresentation.get()?.title)
            assertFalse(host.get().isFinishing)
            assertEquals(0, gate.walletOpenCount.get())
            host.pause().stop().destroy()
        }
        assertTrue(gate.errorPresentation.get()!!.message.contains("TON_CONNECT_JOURNAL_RECOVERY"))
    }

    @Test
    fun explicitRetryStartsOneNewCheckAndThenOpensTheWallet() {
        val host = launch(launcherIntent("retry"))
        assertTrue(gate.awaitCheckStarted())
        gate.complete(WalletDatabaseStartupResult.DatabaseOpenFailed)
        idleMainUntil { gate.errorCount.get() == 1 }

        gate.prepareNextAttempt()
        host.get().retryForTest()
        host.get().retryForTest()
        assertTrue(gate.awaitCheckStarted())
        gate.complete(WalletDatabaseStartupResult.Ready)
        idleMainUntil { gate.walletOpenCount.get() == 1 }
        assertEquals(2, gate.startupCheckCount.get())
    }

    @Test
    fun dismissedRecoveryIsPresentedAgainWithoutANewCheck() {
        val host = launch(launcherIntent("dismiss"))
        host.windowFocusChanged(true)
        assertTrue(gate.awaitCheckStarted())
        gate.complete(WalletDatabaseStartupResult.RecoveryRequired("MIGRATION_JOURNAL"))
        idleMainUntil { gate.errorCount.get() == 1 }

        host.get().dismissRecoveryForTest()
        idleMainUntil { gate.errorCount.get() == 2 }
        assertEquals(1, gate.startupCheckCount.get())

        // A dismissed presentation waits for focus before it is shown again.
        host.windowFocusChanged(false)
        host.get().dismissRecoveryForTest()
        idleMainUntil { true }
        assertEquals(2, gate.errorCount.get())
        host.windowFocusChanged(true)
        idleMainUntil { gate.errorCount.get() == 3 }
    }

    @Test
    fun deepLinkDuringThePendingCheckReplacesTheLauncherPayload() {
        val host = launch(launcherIntent("launcher"))
        assertTrue(gate.awaitCheckStarted())
        host.get().deliverNewIntentForTest(deepLinkIntent(Uri.parse("fearless://buy-success"), "deep-link"))
        assertEquals("fearless://buy-success", gate.deliveredIntent.get()?.data.toString())
        gate.complete(WalletDatabaseStartupResult.Ready)
        idleMainUntil { gate.walletOpenCount.get() == 1 }
        assertEquals("deep-link", gate.forwardedIntent.get()?.getStringExtra("entry"))
        assertEquals(1, gate.startupCheckCount.get())
    }

    @Test
    fun readyProcessForwardsANewEntryWithoutReopeningTheDatabase() {
        val host = launch(launcherIntent("first"))
        assertTrue(gate.awaitCheckStarted())
        gate.complete(WalletDatabaseStartupResult.Ready)
        idleMainUntil { gate.walletOpenCount.get() == 1 }

        gate.prepareForNextWalletOpen()
        host.get().deliverNewIntentForTest(deepLinkIntent(Uri.parse("https://fearlesswallet.io/ton-connect/v2"), "second"))
        idleMainUntil { gate.walletOpenCount.get() == 2 }
        assertEquals("second", gate.forwardedIntent.get()?.getStringExtra("entry"))
        assertEquals(1, gate.startupCheckCount.get())
    }

    @Test
    fun durabilityLatchTurnsANewEntryIntoARestartRequest() {
        val host = launch(launcherIntent("first"))
        assertTrue(gate.awaitCheckStarted())
        gate.complete(WalletDatabaseStartupResult.Ready)
        idleMainUntil { gate.walletOpenCount.get() == 1 }

        WalletSecureStorageHealthTestHooks.latchProcessRestartRequired()
        host.get().deliverNewIntentForTest(launcherIntent("after-latch"))
        idleMainUntil { gate.errorCount.get() == 1 }
        assertEquals(context.getString(R.string.wallet_startup_restart_required_title), gate.errorPresentation.get()?.title)
        assertEquals(1, gate.walletOpenCount.get())
    }

    @Test
    fun recreationJoinsTheProcessOwnedCheckAndDiscardsRestoredFragments() {
        val host = launch(launcherIntent("rotate"))
        assertTrue(gate.awaitCheckStarted())
        host.get().installNonRestorableFragmentForTest()
        assertTrue(host.get().hasNonRestorableFragmentForTest())

        host.recreate()
        assertFalse(host.get().hasNonRestorableFragmentForTest())
        gate.complete(WalletDatabaseStartupResult.Ready)
        idleMainUntil { gate.walletOpenCount.get() == 1 }
        assertEquals(1, gate.startupCheckCount.get())
        assertEquals("rotate", gate.forwardedIntent.get()?.getStringExtra("entry"))
    }

    @Test
    fun aDestroyedWaiterDoesNotCancelTheCheckAnotherEntryJoins() {
        val first = launch(launcherIntent("first"))
        assertTrue(gate.awaitCheckStarted())
        first.pause().stop().destroy()
        val second = launch(launcherIntent("second"))
        gate.complete(WalletDatabaseStartupResult.Ready)
        idleMainUntil { gate.walletOpenCount.get() == 1 }
        assertEquals(1, gate.startupCheckCount.get())
        assertEquals("second", gate.forwardedIntent.get()?.getStringExtra("entry"))
        assertFalse(second.get().isFinishing)
    }

    private fun launch(intent: Intent): ActivityController<WalletGateActivityTestHost> =
        Robolectric.buildActivity(WalletGateActivityTestHost::class.java, intent).setup().also { controllers += it }

    private fun launcherIntent(entry: String): Intent =
        Intent(context, WalletGateActivityTestHost::class.java).setAction(Intent.ACTION_MAIN).putExtra("entry", entry)

    private fun deepLinkIntent(uri: Uri, entry: String): Intent =
        Intent(context, WalletGateActivityTestHost::class.java).setAction(Intent.ACTION_VIEW).setData(uri).putExtra("entry", entry)
}
