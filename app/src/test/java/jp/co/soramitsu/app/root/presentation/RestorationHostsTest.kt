package jp.co.soramitsu.app.root.presentation

import android.os.Looper
import jp.co.soramitsu.app.HiltShimApplication
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageHealthTestHooks
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The debug restoration hosts for the security warning sheet and the secure-storage restart redirect. */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltShimApplication::class)
class RestorationHostsTest {

    @Before
    fun setUp() {
        SecurityWarningRestorationTestController.reset()
        WalletSecureStorageRestartActivityTestController.reset()
        WalletSecureStorageHealthTestHooks.reset()
    }

    @After
    fun tearDown() {
        SecurityWarningRestorationTestController.reset()
        WalletSecureStorageRestartActivityTestController.reset()
        WalletSecureStorageHealthTestHooks.reset()
    }

    @Test
    fun theSecurityWarningSurvivesRecreationAndOneCancellationClosesTheScreen() {
        val host = Robolectric.buildActivity(SecurityWarningRestorationActivityTestHost::class.java).setup()
        idle()
        val original = host.get().currentSheet()
        assertNotNull(original)
        assertEquals(1, host.get().activeSheetCount())

        host.recreate()
        idle()
        assertEquals(1, SecurityWarningRestorationTestController.restoredActivityCount.get())
        assertNotSame(original, host.get().currentSheet())
        assertEquals(1, host.get().activeSheetCount())
        assertEquals("Recreation is not a cancellation", 0, SecurityWarningRestorationTestController.cancellationCount.get())

        host.get().cancelCurrentSheet()
        idle()
        assertEquals(1, SecurityWarningRestorationTestController.resultCount.get())
        assertEquals(1, SecurityWarningRestorationTestController.cancellationCount.get())
        assertTrue(host.get().isFinishing)
        host.pause().stop().destroy()
    }

    @Test
    fun aStartedHostRedirectsOnceWhenSecureStorageNeedsARestart() {
        val host = Robolectric.buildActivity(WalletSecureStorageRestartActivityTestHost::class.java).setup()
        assertTrue(WalletSecureStorageRestartActivityTestController.awaitCreated())
        assertEquals(1, WalletSecureStorageRestartActivityTestController.createCount.get())

        WalletSecureStorageHealthTestHooks.latchProcessRestartRequired()
        idle()
        assertTrue(WalletSecureStorageRestartActivityTestController.awaitRedirect())
        assertEquals(1, WalletSecureStorageRestartActivityTestController.redirectCount.get())
        assertTrue(host.get().isFinishing)
        host.pause().stop().destroy()
    }

    @Test
    fun aStoppedHostWaitsUntilItIsStartedAgain() {
        WalletSecureStorageRestartActivityTestController.finishOnRedirect = false
        val host = Robolectric.buildActivity(WalletSecureStorageRestartActivityTestHost::class.java).setup()
        host.pause().stop()
        WalletSecureStorageHealthTestHooks.latchProcessRestartRequired()
        idle()
        assertEquals(0, WalletSecureStorageRestartActivityTestController.redirectCount.get())

        host.start().resume()
        idle()
        assertEquals(1, WalletSecureStorageRestartActivityTestController.redirectCount.get())
        assertFalse(host.get().isFinishing)
        host.pause().stop().destroy()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
