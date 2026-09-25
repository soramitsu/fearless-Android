package jp.co.soramitsu.app.root.presentation

import android.content.Intent
import androidx.fragment.app.Fragment
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.root.domain.WalletDatabaseStartupResult
import jp.co.soramitsu.app.root.domain.WalletStartupPayload
import kotlinx.coroutines.CompletableDeferred

/**
 * Debug-only host for deterministic ActivityScenario lifecycle tests.
 *
 * It never opens the production wallet graph. Release-like variants do not
 * compile or register this component.
 */
class WalletGateActivityTestHost : WalletGateActivity() {

    override suspend fun performStartupCheck(): WalletDatabaseStartupResult {
        return WalletGateActivityTestController.performStartupCheck()
    }

    override fun openWallet(payload: WalletStartupPayload) {
        // ActivityScenario owns teardown for this debug-only host. Finishing
        // the Activity here makes its internal lifecycle tracker stale after a
        // direct onNewIntent() adversarial injection: Android destroys the
        // Activity, but ActivityScenario keeps reporting RESUMED and close()
        // times out. Production WalletGateActivity still starts the internal
        // root and calls finish(); this host records only the handoff contract.
        WalletGateActivityTestController.recordWalletOpened(payload)
    }

    override fun showStartupError(title: String, message: String) {
        WalletGateActivityTestController.recordErrorShown(title, message)
    }

    override fun showRecoveryRequired(diagnosticCode: String) {
        WalletGateActivityTestController.recordErrorShown(
            getString(
                R.string.wallet_recovery_required_title
            ),
            getString(
                R.string.wallet_startup_recovery_required_message,
                diagnosticCode
            )
        )
    }

    override fun showProcessRestartRequired() {
        WalletGateActivityTestController.recordErrorShown(
            getString(R.string.wallet_startup_restart_required_title),
            getString(R.string.wallet_startup_restart_required_message)
        )
    }

    fun deliverNewIntentForTest(intent: Intent) {
        // Exercise the production callback synchronously, then restore the
        // launch Intent used by ActivityScenario's lifecycle matcher. The
        // production callback still observes and retains the new Intent; only
        // this debug host restores the test framework's bookkeeping value.
        val scenarioLaunchIntent = Intent(this.intent)
        onNewIntent(Intent(intent))
        WalletGateActivityTestController.recordDeliveredIntent(this.intent)
        setIntent(scenarioLaunchIntent)
    }

    fun retryForTest() {
        retryStartupGate()
    }

    fun dismissRecoveryForTest() {
        recoveryPresentationDismissed()
    }

    fun installNonRestorableFragmentForTest() {
        supportFragmentManager.beginTransaction()
            .add(
                WalletGateNonRestorableFragment("play-crash-regression"),
                NON_RESTORABLE_FRAGMENT_TAG
            )
            .commitNow()
    }

    fun hasNonRestorableFragmentForTest(): Boolean {
        return supportFragmentManager.findFragmentByTag(
            NON_RESTORABLE_FRAGMENT_TAG
        ) != null
    }

    private companion object {
        const val NON_RESTORABLE_FRAGMENT_TAG =
            "wallet-gate-non-restorable-fragment"
    }
}

/**
 * Exact adversarial fixture for the production Fragment.instantiate crash.
 * Android restoration would throw NoSuchMethodException because this Fragment
 * deliberately exposes only a parameterized constructor.
 */
class WalletGateNonRestorableFragment(
    @Suppress("unused") private val payload: String
) : Fragment()

object WalletGateActivityTestController {

    private const val WAIT_SECONDS = 10L

    @Volatile
    private var result = CompletableDeferred<WalletDatabaseStartupResult>()

    @Volatile
    private var checkStarted = CountDownLatch(1)

    @Volatile
    private var walletOpened = CountDownLatch(1)

    @Volatile
    private var errorShown = CountDownLatch(1)

    val walletOpenCount = AtomicInteger()
    val startupCheckCount = AtomicInteger()
    val errorCount = AtomicInteger()
    val errorPresentation = AtomicReference<StartupErrorPresentation?>()
    val deliveredIntent = AtomicReference<Intent?>()
    val forwardedIntent = AtomicReference<Intent?>()

    fun reset() {
        result.cancel()
        result = CompletableDeferred()
        checkStarted = CountDownLatch(1)
        walletOpened = CountDownLatch(1)
        errorShown = CountDownLatch(1)
        walletOpenCount.set(0)
        startupCheckCount.set(0)
        errorCount.set(0)
        errorPresentation.set(null)
        deliveredIntent.set(null)
        forwardedIntent.set(null)
    }

    fun expectNextErrorPresentation() {
        errorShown = CountDownLatch(1)
    }

    fun prepareNextAttempt() {
        check(result.isCompleted) {
            "The previous startup attempt must complete before retry setup"
        }
        result = CompletableDeferred()
        checkStarted = CountDownLatch(1)
    }

    fun prepareForNextWalletOpen() {
        walletOpened = CountDownLatch(1)
    }

    fun prepareForNextError() {
        errorShown = CountDownLatch(1)
    }

    suspend fun performStartupCheck(): WalletDatabaseStartupResult {
        startupCheckCount.incrementAndGet()
        checkStarted.countDown()
        return result.await()
    }

    fun complete(startupResult: WalletDatabaseStartupResult): Boolean {
        return result.complete(startupResult)
    }

    fun recordWalletOpened(payload: WalletStartupPayload) {
        val intentPayload = payload as? WalletStartupIntentPayload
        forwardedIntent.set(intentPayload?.intent?.let(::Intent))
        walletOpenCount.incrementAndGet()
        walletOpened.countDown()
    }

    fun recordDeliveredIntent(intent: Intent) {
        deliveredIntent.set(Intent(intent))
    }

    fun recordErrorShown(title: String, message: String) {
        errorPresentation.set(StartupErrorPresentation(title, message))
        errorCount.incrementAndGet()
        errorShown.countDown()
    }

    fun awaitCheckStarted(): Boolean {
        return checkStarted.await(WAIT_SECONDS, TimeUnit.SECONDS)
    }

    fun awaitWalletOpened(): Boolean {
        return walletOpened.await(WAIT_SECONDS, TimeUnit.SECONDS)
    }

    fun awaitErrorShown(): Boolean {
        return errorShown.await(WAIT_SECONDS, TimeUnit.SECONDS)
    }
}

data class StartupErrorPresentation(
    val title: String,
    val message: String
)
