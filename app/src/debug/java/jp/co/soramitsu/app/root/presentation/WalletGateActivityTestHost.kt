package jp.co.soramitsu.app.root.presentation

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import jp.co.soramitsu.app.root.domain.WalletDatabaseStartupResult
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

    override fun openWallet() {
        WalletGateActivityTestController.recordWalletOpened()
    }

    override fun showStartupError(title: String, message: String) {
        super.showStartupError(title, message)
        WalletGateActivityTestController.recordErrorShown(title, message)
    }
}

object WalletGateActivityTestController {

    private const val WAIT_SECONDS = 10L

    private var result = CompletableDeferred<WalletDatabaseStartupResult>()
    private var checkStarted = CountDownLatch(1)
    private var walletOpened = CountDownLatch(1)
    private var errorShown = CountDownLatch(1)

    val walletOpenCount = AtomicInteger()
    val errorCount = AtomicInteger()
    val errorPresentation = AtomicReference<StartupErrorPresentation?>()

    fun reset() {
        result.cancel()
        result = CompletableDeferred()
        checkStarted = CountDownLatch(1)
        walletOpened = CountDownLatch(1)
        errorShown = CountDownLatch(1)
        walletOpenCount.set(0)
        errorCount.set(0)
        errorPresentation.set(null)
    }

    suspend fun performStartupCheck(): WalletDatabaseStartupResult {
        checkStarted.countDown()
        return result.await()
    }

    fun complete(startupResult: WalletDatabaseStartupResult): Boolean {
        return result.complete(startupResult)
    }

    fun recordWalletOpened() {
        walletOpenCount.incrementAndGet()
        walletOpened.countDown()
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
