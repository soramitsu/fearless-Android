package jp.co.soramitsu.app.root.presentation

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.app.root.domain.WalletStartupSession
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageHealthTestHooks
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WalletSecureStorageRestartActivityLifecycleTest {

    @Before
    fun setUp() {
        WalletSecureStorageHealthTestHooks.reset()
        WalletStartupSession.resetForTest()
        WalletSecureStorageRestartActivityTestController.reset()
    }

    @After
    fun tearDown() {
        WalletStartupSession.resetForTest()
        WalletSecureStorageHealthTestHooks.reset()
        WalletSecureStorageRestartActivityTestController.reset()
    }

    @Test
    fun latchWhileResumedRedirectsAndFinishesExactlyOnce() {
        ActivityScenario.launch(
            WalletSecureStorageRestartActivityTestHost::class.java
        )
            .use {
                assertTrue(
                    WalletSecureStorageRestartActivityTestController
                        .awaitCreated()
                )

                WalletSecureStorageHealthTestHooks
                    .latchProcessRestartRequired()

                assertTrue(
                    WalletSecureStorageRestartActivityTestController
                        .awaitRedirect()
                )
                waitForIdle()
                assertEquals(
                    1,
                    WalletSecureStorageRestartActivityTestController
                        .redirectCount.get()
                )
            }
    }

    @Test
    fun latchWhileStoppedDefersRedirectUntilForeground() {
        ActivityScenario.launch(
            WalletSecureStorageRestartActivityTestHost::class.java
        )
            .use { scenario ->
                assertTrue(
                    WalletSecureStorageRestartActivityTestController
                        .awaitCreated()
                )
                scenario.moveToState(Lifecycle.State.CREATED)

                WalletSecureStorageHealthTestHooks
                    .latchProcessRestartRequired()
                waitForIdle()
                assertEquals(
                    0,
                    WalletSecureStorageRestartActivityTestController
                        .redirectCount.get()
                )

                resumeUntilSafetyRedirect(scenario)
                assertEquals(
                    1,
                    WalletSecureStorageRestartActivityTestController
                        .redirectCount.get()
                )
            }
    }

    @Test
    fun replacementHostAfterBackgroundLatchRedirectsOnlyNewStartedHost() {
        ActivityScenario.launch(
            WalletSecureStorageRestartActivityTestHost::class.java
        ).use { scenario ->
            assertTrue(
                WalletSecureStorageRestartActivityTestController.awaitCreated()
            )
            scenario.moveToState(Lifecycle.State.CREATED)
            WalletSecureStorageHealthTestHooks.latchProcessRestartRequired()
            waitForIdle()
            assertEquals(
                0,
                WalletSecureStorageRestartActivityTestController
                    .redirectCount.get()
            )
        }

        // Keep the replacement alive so ActivityScenario can observe RESUMED;
        // the first case above separately verifies the production finish().
        WalletSecureStorageRestartActivityTestController.finishOnRedirect =
            false
        ActivityScenario.launch(
            WalletSecureStorageRestartActivityTestHost::class.java
        ).use {
            assertTrue(
                WalletSecureStorageRestartActivityTestController.awaitRedirect()
            )
            waitForIdle()
            assertEquals(
                2,
                WalletSecureStorageRestartActivityTestController
                    .createCount.get()
            )
            assertEquals(
                1,
                WalletSecureStorageRestartActivityTestController
                    .redirectCount.get()
            )
        }
    }

    private fun waitForIdle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun resumeUntilSafetyRedirect(
        scenario: ActivityScenario<WalletSecureStorageRestartActivityTestHost>
    ) {
        try {
            scenario.moveToState(Lifecycle.State.RESUMED)
        } catch (_: AssertionError) {
            // The redirect deliberately calls finish() from onStart(). In that
            // valid path ActivityScenario observes DESTROYED before RESUMED.
            // The awaited redirect below keeps unrelated lifecycle failures
            // from being accepted as success.
        }
        assertTrue(
            WalletSecureStorageRestartActivityTestController.awaitRedirect()
        )
    }
}
