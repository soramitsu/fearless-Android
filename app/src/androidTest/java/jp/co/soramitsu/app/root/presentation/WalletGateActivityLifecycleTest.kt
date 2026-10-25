package jp.co.soramitsu.app.root.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.root.domain.WalletDatabaseStartupResult
import jp.co.soramitsu.app.root.domain.WalletStartupSession
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageHealthTestHooks
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WalletGateActivityLifecycleTest {

    @Before
    fun setUp() {
        WalletSecureStorageHealthTestHooks.reset()
        WalletStartupSession.resetForTest()
        WalletGateActivityTestController.reset()
    }

    @After
    fun tearDown() {
        WalletStartupSession.resetForTest()
        WalletSecureStorageHealthTestHooks.reset()
        WalletGateActivityTestController.reset()
    }

    @Test
    fun readyResultWhileStoppedWaitsForResumeBeforeWalletHandoff() {
        ActivityScenario.launch<WalletGateActivityTestHost>(
            launcherIntent("stopped-ready")
        ).use { scenario ->
            assertTrue(WalletGateActivityTestController.awaitCheckStarted())
            scenario.moveToState(Lifecycle.State.CREATED)

            assertTrue(
                WalletGateActivityTestController.complete(
                    WalletDatabaseStartupResult.Ready
                )
            )
            waitForIdle()

            assertEquals(
                0,
                WalletGateActivityTestController.walletOpenCount.get()
            )

            scenario.moveToState(Lifecycle.State.RESUMED)

            assertTrue(WalletGateActivityTestController.awaitWalletOpened())
            assertEquals(
                1,
                WalletGateActivityTestController.walletOpenCount.get()
            )
            assertEquals(
                1,
                WalletGateActivityTestController.startupCheckCount.get()
            )
            assertEquals(0, WalletGateActivityTestController.errorCount.get())
        }
    }

    @Test
    fun failureWhileStoppedWaitsForResumeBeforeErrorPresentation() {
        ActivityScenario.launch<WalletGateActivityTestHost>(
            launcherIntent("stopped-failure")
        ).use { scenario ->
            assertTrue(WalletGateActivityTestController.awaitCheckStarted())
            scenario.moveToState(Lifecycle.State.CREATED)

            assertTrue(
                WalletGateActivityTestController.complete(
                    WalletDatabaseStartupResult.DatabaseOpenFailed
                )
            )
            waitForIdle()

            assertEquals(0, WalletGateActivityTestController.errorCount.get())
            assertEquals(
                0,
                WalletGateActivityTestController.walletOpenCount.get()
            )

            scenario.moveToState(Lifecycle.State.RESUMED)

            assertTrue(WalletGateActivityTestController.awaitErrorShown())
            val presentation =
                WalletGateActivityTestController.errorPresentation.get()
            assertNotNull(presentation)
            scenario.onActivity { activity ->
                assertEquals(
                    activity.getString(
                        R.string.wallet_database_unavailable_title
                    ),
                    presentation?.title
                )
                assertEquals(
                    activity.getString(
                        R.string.wallet_database_unavailable_message
                    ),
                    presentation?.message
                )
                assertFalse(activity.isFinishing)
                assertFalse(activity.isDestroyed)
            }
            assertEquals(1, WalletGateActivityTestController.errorCount.get())
            assertEquals(
                1,
                WalletGateActivityTestController.startupCheckCount.get()
            )
        }
    }

    @Test
    fun permanentRecoveryFailureShowsDiagnosticInsteadOfRetryOnlyRoute() {
        ActivityScenario.launch<WalletGateActivityTestHost>(
            launcherIntent("permanent-recovery")
        ).use { scenario ->
            assertTrue(WalletGateActivityTestController.awaitCheckStarted())
            assertTrue(
                WalletGateActivityTestController.complete(
                    WalletDatabaseStartupResult.RecoveryRequired(
                        "TON_CONNECT_JOURNAL_RECOVERY"
                    )
                )
            )

            assertTrue(WalletGateActivityTestController.awaitErrorShown())
            val presentation =
                WalletGateActivityTestController.errorPresentation.get()
            scenario.onActivity { activity ->
                assertEquals(
                    activity.getString(
                        R.string.wallet_recovery_required_title
                    ),
                    presentation?.title
                )
                assertTrue(
                    presentation?.message.orEmpty().contains(
                        "TON_CONNECT_JOURNAL_RECOVERY"
                    )
                )
            }
            assertEquals(1, WalletGateActivityTestController.errorCount.get())
            assertEquals(
                1,
                WalletGateActivityTestController.startupCheckCount.get()
            )
        }
    }

    @Test
    fun dismissedRecoveryReleasesLeaseAndRepresentsRetainedFailure() {
        ActivityScenario.launch<WalletGateActivityTestHost>(
            launcherIntent("recovery-dismiss")
        ).use { scenario ->
            assertTrue(WalletGateActivityTestController.awaitCheckStarted())
            assertTrue(
                WalletGateActivityTestController.complete(
                    WalletDatabaseStartupResult.RecoveryRequired(
                        "WALLET_MASTER_KEY_RECOVERY"
                    )
                )
            )
            assertTrue(WalletGateActivityTestController.awaitErrorShown())
            WalletGateActivityTestController.expectNextErrorPresentation()

            scenario.onActivity { activity ->
                activity.dismissRecoveryForTest()
            }

            assertTrue(WalletGateActivityTestController.awaitErrorShown())
            assertEquals(
                2,
                WalletGateActivityTestController.errorCount.get()
            )
            assertEquals(
                1,
                WalletGateActivityTestController.startupCheckCount.get()
            )
        }
    }

    @Test
    fun supportReturnCanRepresentRetainedRecoveryWithoutRetryingGate() {
        ActivityScenario.launch<WalletGateActivityTestHost>(
            launcherIntent("recovery-support-return")
        ).use { scenario ->
            assertTrue(WalletGateActivityTestController.awaitCheckStarted())
            assertTrue(
                WalletGateActivityTestController.complete(
                    WalletDatabaseStartupResult.RecoveryRequired(
                        "TON_DATABASE_MIGRATION_RECOVERY"
                    )
                )
            )
            assertTrue(WalletGateActivityTestController.awaitErrorShown())
            scenario.moveToState(Lifecycle.State.CREATED)
            WalletGateActivityTestController.expectNextErrorPresentation()

            scenario.onActivity { activity ->
                activity.dismissRecoveryForTest()
            }
            scenario.moveToState(Lifecycle.State.RESUMED)

            assertTrue(WalletGateActivityTestController.awaitErrorShown())
            assertEquals(
                2,
                WalletGateActivityTestController.errorCount.get()
            )
            assertEquals(
                1,
                WalletGateActivityTestController.startupCheckCount.get()
            )
        }
    }

    @Test
    fun deepLinkDuringPendingCheckReplacesLauncherAndDoesNotReopen() {
        val newestUri = Uri.parse(
            "https://fearlesswallet.io/ton-connect/newest-pending"
        )
        ActivityScenario.launch<WalletGateActivityTestHost>(
            launcherIntent("initial-launcher")
        ).use { scenario ->
            assertTrue(WalletGateActivityTestController.awaitCheckStarted())

            scenario.onActivity { activity ->
                activity.deliverNewIntentForTest(
                    Intent(Intent.ACTION_VIEW, newestUri).apply {
                        putExtra("entry", "newest-deep-link")
                    }
                )
            }
            waitForIdle()
            assertEquals(
                newestUri,
                WalletGateActivityTestController.deliveredIntent.get()?.data
            )

            assertTrue(
                WalletGateActivityTestController.complete(
                    WalletDatabaseStartupResult.Ready
                )
            )
            assertTrue(WalletGateActivityTestController.awaitWalletOpened())

            val forwarded =
                WalletGateActivityTestController.forwardedIntent.get()
            assertEquals(newestUri, forwarded?.data)
            assertEquals(
                "newest-deep-link",
                forwarded?.getStringExtra("entry")
            )
            assertEquals(
                1,
                WalletGateActivityTestController.startupCheckCount.get()
            )
            assertEquals(
                1,
                WalletGateActivityTestController.walletOpenCount.get()
            )
        }
    }

    @Test
    fun rotationJoinsProcessOwnedCheckAndForwardsOnce() {
        val uri = Uri.parse(
            "https://fearlesswallet.io/ton-connect/rotation"
        )
        ActivityScenario.launch<WalletGateActivityTestHost>(
            deepLinkIntent(uri, "before-rotation")
        ).use { scenario ->
            assertTrue(WalletGateActivityTestController.awaitCheckStarted())

            scenario.recreate()
            waitForIdle()

            assertEquals(
                1,
                WalletGateActivityTestController.startupCheckCount.get()
            )
            assertTrue(
                WalletGateActivityTestController.complete(
                    WalletDatabaseStartupResult.Ready
                )
            )
            assertTrue(WalletGateActivityTestController.awaitWalletOpened())

            assertEquals(
                1,
                WalletGateActivityTestController.walletOpenCount.get()
            )
            assertEquals(
                uri,
                WalletGateActivityTestController.forwardedIntent.get()?.data
            )
        }
    }

    @Test
    fun legacyGateRelaunchDiscardsNonRestorableFragmentState() {
        ActivityScenario.launch<WalletGateActivityTestHost>(
            launcherIntent("non-restorable-fragment")
        ).use { scenario ->
            assertTrue(WalletGateActivityTestController.awaitCheckStarted())
            scenario.onActivity { activity ->
                activity.installNonRestorableFragmentForTest()
                assertTrue(activity.hasNonRestorableFragmentForTest())
            }

            // This models ActivityThread.handleRelaunchActivity. Restoring the
            // saved FragmentManager state would call Fragment.instantiate and
            // throw NoSuchMethodException for the fixture's missing ctor.
            scenario.recreate()
            waitForIdle()

            scenario.onActivity { activity ->
                assertFalse(activity.hasNonRestorableFragmentForTest())
            }
            assertEquals(
                1,
                WalletGateActivityTestController.startupCheckCount.get()
            )
            assertTrue(
                WalletGateActivityTestController.complete(
                    WalletDatabaseStartupResult.Ready
                )
            )
            assertTrue(WalletGateActivityTestController.awaitWalletOpened())
        }
    }

    @Test
    fun retainedFailureSurvivesRotationWithoutAutomaticReopen() {
        ActivityScenario.launch<WalletGateActivityTestHost>(
            launcherIntent("failure-rotation")
        ).use { scenario ->
            assertTrue(WalletGateActivityTestController.awaitCheckStarted())
            assertTrue(
                WalletGateActivityTestController.complete(
                    WalletDatabaseStartupResult.DatabaseOpenFailed
                )
            )
            assertTrue(WalletGateActivityTestController.awaitErrorShown())
            waitForIdle()

            WalletGateActivityTestController.prepareForNextError()
            scenario.recreate()

            assertTrue(WalletGateActivityTestController.awaitErrorShown())
            assertEquals(2, WalletGateActivityTestController.errorCount.get())
            assertEquals(
                1,
                WalletGateActivityTestController.startupCheckCount.get()
            )
            assertEquals(
                0,
                WalletGateActivityTestController.walletOpenCount.get()
            )
        }
    }

    @Test
    fun explicitRetryKeepsNewestDeepLinkAndStartsOneNewCheck() {
        val newestUri = Uri.parse(
            "https://fearlesswallet.io/ton-connect/retry-newest"
        )
        ActivityScenario.launch<WalletGateActivityTestHost>(
            launcherIntent("retry-launcher")
        ).use { scenario ->
            assertTrue(WalletGateActivityTestController.awaitCheckStarted())
            assertTrue(
                WalletGateActivityTestController.complete(
                    WalletDatabaseStartupResult.DatabaseOpenFailed
                )
            )
            assertTrue(WalletGateActivityTestController.awaitErrorShown())
            waitForIdle()

            scenario.onActivity { activity ->
                activity.deliverNewIntentForTest(
                    deepLinkIntent(newestUri, "retry-deep-link")
                )
            }
            waitForIdle()
            WalletGateActivityTestController.prepareNextAttempt()
            scenario.onActivity { activity ->
                activity.retryForTest()
            }

            assertTrue(WalletGateActivityTestController.awaitCheckStarted())
            assertEquals(
                2,
                WalletGateActivityTestController.startupCheckCount.get()
            )
            assertTrue(
                WalletGateActivityTestController.complete(
                    WalletDatabaseStartupResult.Ready
                )
            )
            assertTrue(WalletGateActivityTestController.awaitWalletOpened())

            val forwarded =
                WalletGateActivityTestController.forwardedIntent.get()
            assertEquals(newestUri, forwarded?.data)
            assertEquals(
                "retry-deep-link",
                forwarded?.getStringExtra("entry")
            )
            assertEquals(
                1,
                WalletGateActivityTestController.walletOpenCount.get()
            )
            assertEquals(1, WalletGateActivityTestController.errorCount.get())
        }
    }

    @Test
    fun readyProcessForwardsNewEntryWithoutReopeningDatabase() {
        ActivityScenario.launch<WalletGateActivityTestHost>(
            launcherIntent("ready-initial")
        ).use {
            assertTrue(WalletGateActivityTestController.awaitCheckStarted())
            assertTrue(
                WalletGateActivityTestController.complete(
                    WalletDatabaseStartupResult.Ready
                )
            )
            assertTrue(WalletGateActivityTestController.awaitWalletOpened())
        }

        WalletGateActivityTestController.prepareForNextWalletOpen()
        val readyUri = Uri.parse(
            "https://fearlesswallet.io/ton-connect/ready-fast-path"
        )
        ActivityScenario.launch<WalletGateActivityTestHost>(
            deepLinkIntent(readyUri, "ready-deep-link")
        ).use {
            assertTrue(WalletGateActivityTestController.awaitWalletOpened())
        }

        assertEquals(
            1,
            WalletGateActivityTestController.startupCheckCount.get()
        )
        assertEquals(
            2,
            WalletGateActivityTestController.walletOpenCount.get()
        )
        assertEquals(
            readyUri,
            WalletGateActivityTestController.forwardedIntent.get()?.data
        )
    }

    @Test
    fun readyProcessDurabilityLatchBlocksRelaunchedDeepLink() {
        ActivityScenario.launch<WalletGateActivityTestHost>(
            launcherIntent("ready-before-latch")
        ).use {
            assertTrue(WalletGateActivityTestController.awaitCheckStarted())
            assertTrue(
                WalletGateActivityTestController.complete(
                    WalletDatabaseStartupResult.Ready
                )
            )
            assertTrue(WalletGateActivityTestController.awaitWalletOpened())
        }

        WalletSecureStorageHealthTestHooks.latchProcessRestartRequired()
        WalletGateActivityTestController.prepareForNextError()
        val blockedUri = Uri.parse(
            "https://fearlesswallet.io/ton-connect/blocked-after-latch"
        )
        ActivityScenario.launch<WalletGateActivityTestHost>(
            deepLinkIntent(blockedUri, "blocked-after-latch")
        ).use { scenario ->
            assertTrue(WalletGateActivityTestController.awaitErrorShown())
            val presentation =
                WalletGateActivityTestController.errorPresentation.get()
            scenario.onActivity { activity ->
                assertEquals(
                    activity.getString(
                        R.string.wallet_startup_restart_required_title
                    ),
                    presentation?.title
                )
                assertEquals(
                    activity.getString(
                        R.string.wallet_startup_restart_required_message
                    ),
                    presentation?.message
                )
            }
        }

        assertEquals(
            1,
            WalletGateActivityTestController.startupCheckCount.get()
        )
        assertEquals(
            1,
            WalletGateActivityTestController.walletOpenCount.get()
        )
        assertEquals(1, WalletGateActivityTestController.errorCount.get())
    }

    @Test
    fun readyActivityNewIntentAfterLatchShowsRestartInsteadOfWallet() {
        val blockedUri = Uri.parse(
            "https://fearlesswallet.io/ton-connect/new-intent-after-latch"
        )
        ActivityScenario.launch<WalletGateActivityTestHost>(
            launcherIntent("ready-before-new-intent-latch")
        ).use { scenario ->
            assertTrue(WalletGateActivityTestController.awaitCheckStarted())
            assertTrue(
                WalletGateActivityTestController.complete(
                    WalletDatabaseStartupResult.Ready
                )
            )
            assertTrue(WalletGateActivityTestController.awaitWalletOpened())
            WalletSecureStorageHealthTestHooks.latchProcessRestartRequired()
            WalletGateActivityTestController.prepareForNextError()
            WalletGateActivityTestController.prepareForNextWalletOpen()

            scenario.onActivity { activity ->
                activity.deliverNewIntentForTest(
                    deepLinkIntent(blockedUri, "blocked-new-intent")
                )
            }

            assertTrue(WalletGateActivityTestController.awaitErrorShown())
            waitForIdle()
            assertEquals(
                1,
                WalletGateActivityTestController.startupCheckCount.get()
            )
            assertEquals(
                1,
                WalletGateActivityTestController.walletOpenCount.get()
            )
            assertEquals(
                blockedUri,
                WalletGateActivityTestController.deliveredIntent.get()?.data
            )
        }
    }

    @Test
    fun freshProcessSessionCanRunGateAfterProcessLocalLatch() {
        WalletSecureStorageHealthTestHooks.latchProcessRestartRequired()
        ActivityScenario.launch<WalletGateActivityTestHost>(
            launcherIntent("blocked-process")
        ).use {
            assertTrue(WalletGateActivityTestController.awaitErrorShown())
            assertEquals(
                0,
                WalletGateActivityTestController.startupCheckCount.get()
            )
            assertEquals(
                0,
                WalletGateActivityTestController.walletOpenCount.get()
            )
        }

        WalletSecureStorageHealthTestHooks.reset()
        WalletStartupSession.resetForTest()
        WalletGateActivityTestController.reset()
        ActivityScenario.launch<WalletGateActivityTestHost>(
            launcherIntent("fresh-process")
        ).use {
            assertTrue(WalletGateActivityTestController.awaitCheckStarted())
            assertTrue(
                WalletGateActivityTestController.complete(
                    WalletDatabaseStartupResult.Ready
                )
            )
            assertTrue(WalletGateActivityTestController.awaitWalletOpened())
            assertEquals(
                1,
                WalletGateActivityTestController.startupCheckCount.get()
            )
            assertEquals(
                1,
                WalletGateActivityTestController.walletOpenCount.get()
            )
        }
    }

    @Test
    fun twoConcurrentActivitiesShareCheckAndOnlyNewestOpensWallet() {
        val newestUri = Uri.parse(
            "https://fearlesswallet.io/ton-connect/concurrent-newest"
        )
        ActivityScenario.launch<WalletGateActivityTestHost>(
            launcherIntent("concurrent-old")
        ).use {
            assertTrue(WalletGateActivityTestController.awaitCheckStarted())

            ActivityScenario.launch<WalletGateActivityTestHost>(
                deepLinkIntent(newestUri, "concurrent-newest")
            ).use {
                waitForIdle()
                assertEquals(
                    1,
                    WalletGateActivityTestController.startupCheckCount.get()
                )
                assertTrue(
                    WalletGateActivityTestController.complete(
                        WalletDatabaseStartupResult.Ready
                    )
                )
                assertTrue(
                    WalletGateActivityTestController.awaitWalletOpened()
                )
                waitForIdle()

                assertEquals(
                    1,
                    WalletGateActivityTestController.walletOpenCount.get()
                )
                val forwarded =
                    WalletGateActivityTestController.forwardedIntent.get()
                assertEquals(newestUri, forwarded?.data)
                assertEquals(
                    "concurrent-newest",
                    forwarded?.getStringExtra("entry")
                )
            }
        }
    }

    @Test
    fun destroyedActivityWaiterDoesNotCancelProcessOwnedCheck() {
        val firstScenario = ActivityScenario.launch<WalletGateActivityTestHost>(
            launcherIntent("destroyed-waiter")
        )
        assertTrue(WalletGateActivityTestController.awaitCheckStarted())
        firstScenario.close()

        val newestUri = Uri.parse(
            "https://fearlesswallet.io/ton-connect/after-destroy"
        )
        ActivityScenario.launch<WalletGateActivityTestHost>(
            deepLinkIntent(newestUri, "replacement-activity")
        ).use {
            waitForIdle()
            assertEquals(
                1,
                WalletGateActivityTestController.startupCheckCount.get()
            )
            assertTrue(
                WalletGateActivityTestController.complete(
                    WalletDatabaseStartupResult.Ready
                )
            )
            assertTrue(WalletGateActivityTestController.awaitWalletOpened())
        }

        assertEquals(
            1,
            WalletGateActivityTestController.walletOpenCount.get()
        )
        assertEquals(
            newestUri,
            WalletGateActivityTestController.forwardedIntent.get()?.data
        )
    }

    @Test
    fun manyPendingDeepLinksForwardOnlyNewestPayload() {
        ActivityScenario.launch<WalletGateActivityTestHost>(
            launcherIntent("deep-link-stress-launcher")
        ).use { scenario ->
            assertTrue(WalletGateActivityTestController.awaitCheckStarted())

            val newestIndex = 127
            scenario.onActivity { activity ->
                repeat(newestIndex + 1) { index ->
                    activity.deliverNewIntentForTest(
                        deepLinkIntent(
                            Uri.parse(
                                "https://fearlesswallet.io/ton-connect/stress-$index"
                            ),
                            "stress-$index"
                        )
                    )
                }
            }
            waitForIdle()

            assertTrue(
                WalletGateActivityTestController.complete(
                    WalletDatabaseStartupResult.Ready
                )
            )
            assertTrue(WalletGateActivityTestController.awaitWalletOpened())

            val forwarded =
                WalletGateActivityTestController.forwardedIntent.get()
            assertEquals(
                "https://fearlesswallet.io/ton-connect/stress-$newestIndex",
                forwarded?.dataString
            )
            assertEquals(
                "stress-$newestIndex",
                forwarded?.getStringExtra("entry")
            )
            assertEquals(
                1,
                WalletGateActivityTestController.startupCheckCount.get()
            )
            assertEquals(
                1,
                WalletGateActivityTestController.walletOpenCount.get()
            )
        }
    }

    @Test
    fun repeatedRotationJoinsOneProcessOwnedCheck() {
        ActivityScenario.launch<WalletGateActivityTestHost>(
            launcherIntent("rotation-stress")
        ).use { scenario ->
            assertTrue(WalletGateActivityTestController.awaitCheckStarted())

            repeat(10) {
                scenario.recreate()
                waitForIdle()
            }

            assertEquals(
                1,
                WalletGateActivityTestController.startupCheckCount.get()
            )
            assertTrue(
                WalletGateActivityTestController.complete(
                    WalletDatabaseStartupResult.Ready
                )
            )
            assertTrue(WalletGateActivityTestController.awaitWalletOpened())
            assertEquals(
                1,
                WalletGateActivityTestController.walletOpenCount.get()
            )
        }
    }

    @Test
    fun rapidDuplicateRetryActionsStartOneNewCheck() {
        ActivityScenario.launch<WalletGateActivityTestHost>(
            launcherIntent("duplicate-retry")
        ).use { scenario ->
            assertTrue(WalletGateActivityTestController.awaitCheckStarted())
            assertTrue(
                WalletGateActivityTestController.complete(
                    WalletDatabaseStartupResult.DatabaseOpenFailed
                )
            )
            assertTrue(WalletGateActivityTestController.awaitErrorShown())
            waitForIdle()

            WalletGateActivityTestController.prepareNextAttempt()
            scenario.onActivity { activity ->
                activity.retryForTest()
                activity.retryForTest()
            }

            assertTrue(WalletGateActivityTestController.awaitCheckStarted())
            assertEquals(
                2,
                WalletGateActivityTestController.startupCheckCount.get()
            )
            assertTrue(
                WalletGateActivityTestController.complete(
                    WalletDatabaseStartupResult.Ready
                )
            )
            assertTrue(WalletGateActivityTestController.awaitWalletOpened())
            assertEquals(
                1,
                WalletGateActivityTestController.walletOpenCount.get()
            )
            assertEquals(1, WalletGateActivityTestController.errorCount.get())
        }
    }

    @Test
    fun internalHandoffRejectsCallerTaskFlagsButPreservesUriGrants() {
        val sourceUri = Uri.parse(
            "content://jp.co.soramitsu.test/wallet-import.json"
        )
        val source = Intent(Intent.ACTION_VIEW, sourceUri).apply {
            // Intent.setType() clears any existing data URI. Use the atomic
            // Android API so this adversarial fixture actually contains both
            // the content URI and MIME type that the handoff must preserve.
            setDataAndType(sourceUri, "application/json")
            putExtra("entry", "hostile-flags")
            selector = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://attacker.invalid/selector")
            )
            flags =
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION or
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_FORWARD_RESULT or
                Intent.FLAG_ACTIVITY_NO_HISTORY
        }

        val handoff = source.toWalletInternalHandoff(
            context = appContext(),
            target = WalletGateActivityTestHost::class.java
        )

        val expectedFlags =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        assertEquals(expectedFlags, handoff.flags)
        assertEquals(Intent.ACTION_VIEW, handoff.action)
        assertEquals(sourceUri, handoff.data)
        assertEquals("application/json", handoff.type)
        assertEquals("hostile-flags", handoff.getStringExtra("entry"))
        assertEquals(
            WalletGateActivityTestHost::class.java.name,
            handoff.component?.className
        )
        assertNull(handoff.`package`)
        assertNull(handoff.selector)
    }

    private fun launcherIntent(entry: String): Intent {
        return Intent(appContext(), WalletGateActivityTestHost::class.java)
            .setAction(Intent.ACTION_MAIN)
            .putExtra("entry", entry)
    }

    private fun deepLinkIntent(uri: Uri, entry: String): Intent {
        return Intent(appContext(), WalletGateActivityTestHost::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(uri)
            .putExtra("entry", entry)
    }

    private fun appContext(): Context {
        return ApplicationProvider.getApplicationContext()
    }

    private fun waitForIdle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }
}
