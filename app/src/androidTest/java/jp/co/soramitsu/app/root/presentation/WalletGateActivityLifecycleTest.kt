package jp.co.soramitsu.app.root.presentation

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.root.domain.WalletDatabaseStartupResult
import jp.co.soramitsu.app.root.domain.WalletStartupSession
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WalletGateActivityLifecycleTest {

    @Before
    fun setUp() {
        WalletStartupSession.resetForTest()
        WalletGateActivityTestController.reset()
    }

    @After
    fun tearDown() {
        WalletStartupSession.resetForTest()
        WalletGateActivityTestController.reset()
    }

    @Test
    fun readyResultWhileStoppedWaitsForResumeBeforeWalletHandoff() {
        ActivityScenario.launch(WalletGateActivityTestHost::class.java).use { scenario ->
            assertTrue(WalletGateActivityTestController.awaitCheckStarted())
            scenario.moveToState(Lifecycle.State.CREATED)

            assertTrue(
                WalletGateActivityTestController.complete(
                    WalletDatabaseStartupResult.Ready
                )
            )
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            assertEquals(0, WalletGateActivityTestController.walletOpenCount.get())
            assertFalse(WalletStartupSession.isReady())

            scenario.moveToState(Lifecycle.State.RESUMED)

            assertTrue(WalletGateActivityTestController.awaitWalletOpened())
            assertEquals(1, WalletGateActivityTestController.walletOpenCount.get())
            assertTrue(WalletStartupSession.isReady())
            assertEquals(0, WalletGateActivityTestController.errorCount.get())
        }
    }

    @Test
    fun databaseFailureWhileStoppedWaitsForResumeBeforeRealDialog() {
        ActivityScenario.launch(WalletGateActivityTestHost::class.java).use { scenario ->
            assertTrue(WalletGateActivityTestController.awaitCheckStarted())
            scenario.moveToState(Lifecycle.State.CREATED)

            assertTrue(
                WalletGateActivityTestController.complete(
                    WalletDatabaseStartupResult.DatabaseOpenFailed
                )
            )
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            assertEquals(0, WalletGateActivityTestController.errorCount.get())
            assertEquals(0, WalletGateActivityTestController.walletOpenCount.get())

            scenario.moveToState(Lifecycle.State.RESUMED)

            assertTrue(WalletGateActivityTestController.awaitErrorShown())
            val presentation = WalletGateActivityTestController.errorPresentation.get()
            assertNotNull(presentation)
            scenario.onActivity { activity ->
                assertEquals(
                    activity.getString(R.string.wallet_database_unavailable_title),
                    presentation?.title
                )
                assertEquals(
                    activity.getString(R.string.wallet_database_unavailable_message),
                    presentation?.message
                )
                assertEquals(Lifecycle.State.RESUMED, activity.lifecycle.currentState)
                assertFalse(activity.isFinishing)
                assertFalse(activity.isDestroyed)
            }
            assertEquals(1, WalletGateActivityTestController.errorCount.get())
            assertEquals(0, WalletGateActivityTestController.walletOpenCount.get())
            assertFalse(WalletStartupSession.isReady())
        }
    }
}
