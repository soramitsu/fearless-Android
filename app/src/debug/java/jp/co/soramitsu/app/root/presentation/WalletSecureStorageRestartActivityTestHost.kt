package jp.co.soramitsu.app.root.presentation

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageHealth

/** Debug-only lifecycle host for the live-wallet restart redirect contract. */
class WalletSecureStorageRestartActivityTestHost : AppCompatActivity() {

    private var hostStarted = false

    private val restartRedirector = WalletSecureStorageRestartRedirector(
        addRestartRequiredListener =
        WalletSecureStorageHealth::addProcessRestartRequiredListener,
        postToMain = { action -> runOnUiThread(action) },
        canRedirectNow = {
            hostStarted &&
            !isFinishing &&
                !isDestroyed
        },
        redirectToStartup = {
            WalletSecureStorageRestartActivityTestController.recordRedirect()
            if (
                WalletSecureStorageRestartActivityTestController
                    .finishOnRedirect
            ) {
                finish()
            }
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restartRedirector.register()
        WalletSecureStorageRestartActivityTestController.recordCreated()
    }

    override fun onStart() {
        super.onStart()
        hostStarted = true
        restartRedirector.onHostStarted()
    }

    override fun onStop() {
        hostStarted = false
        super.onStop()
    }

    override fun onDestroy() {
        hostStarted = false
        restartRedirector.unregister()
        super.onDestroy()
    }
}

object WalletSecureStorageRestartActivityTestController {
    private const val WAIT_SECONDS = 10L

    @Volatile
    private var created = CountDownLatch(1)

    @Volatile
    private var redirected = CountDownLatch(1)

    val createCount = AtomicInteger()
    val redirectCount = AtomicInteger()

    @Volatile
    var finishOnRedirect = true

    fun reset() {
        created = CountDownLatch(1)
        redirected = CountDownLatch(1)
        createCount.set(0)
        redirectCount.set(0)
        finishOnRedirect = true
    }

    fun recordCreated() {
        createCount.incrementAndGet()
        created.countDown()
    }

    fun recordRedirect() {
        redirectCount.incrementAndGet()
        redirected.countDown()
    }

    fun awaitCreated(): Boolean = created.await(WAIT_SECONDS, TimeUnit.SECONDS)

    fun awaitRedirect(): Boolean = redirected.await(WAIT_SECONDS, TimeUnit.SECONDS)
}
