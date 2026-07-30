package jp.co.soramitsu.app.root.presentation

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletStartupRestartContractTest {

    @Test
    fun `restart-required startup never offers an in-process retry`() {
        val source = File(
            repositoryRoot(),
            "app/src/main/java/jp/co/soramitsu/app/root/presentation/" +
                "WalletStartupActivity.kt"
        ).readText()
        val branch = source.substringAfter(
            "WalletDatabaseStartupResult.ProcessRestartRequired ->"
        ).substringBefore(
            "is WalletDatabaseStartupResult.RecoveryRequired ->"
        )
        val dialog = source.substringAfter(
            "protected open fun showProcessRestartRequired()"
        ).substringBefore(
            "protected open fun closeWalletProcess()"
        )
        val close = source.substringAfter(
            "protected open fun closeWalletProcess()"
        ).substringBefore(
            "protected open fun showRecoveryRequired"
        )

        assertTrue(branch.contains("showProcessRestartRequired()"))
        assertFalse(branch.contains("retryStartupGate()"))
        assertTrue(dialog.contains(".setCancelable(false)"))
        assertTrue(dialog.contains("closeWalletProcess()"))
        assertFalse(dialog.contains("retryStartupGate()"))
        assertTrue(close.contains("finishAndRemoveTask()"))
        assertTrue(close.contains("Process.killProcess(Process.myPid())"))
    }

    @Test
    fun `live wallet observes restart latch and hands control back to startup`() {
        val source = File(
            repositoryRoot(),
            "app/src/main/java/jp/co/soramitsu/app/root/presentation/" +
                "WalletRootActivity.kt"
        ).readText()

        assertTrue(source.contains("WalletSecureStorageRestartRedirector("))
        assertTrue(
            source.contains(
                "WalletSecureStorageHealth::addProcessRestartRequiredListener"
            )
        )
        assertTrue(source.contains("secureStorageRestartRedirector.register()"))
        assertTrue(source.contains("secureStorageRedirectHostStarted = true"))
        assertTrue(source.contains("secureStorageRedirectHostStarted = false"))
        assertTrue(source.contains("secureStorageRestartRedirector.onHostStarted()"))
        assertTrue(source.contains("secureStorageRestartRedirector.unregister()"))
        assertTrue(source.contains("contentInitializationAllowed = false"))
        assertTrue(source.contains("onContentInitializationBlocked()"))

        val teardown = source.substringAfter("override fun onDestroy()")
            .substringBefore("private fun unregisterNetworkCallback()")
        assertFalse(teardown.contains("if (contentInitializationAllowed)"))
        assertTrue(teardown.contains("processLifecycleObserverRegistered"))
        assertTrue(teardown.contains("unregisterNetworkCallback()"))
        assertTrue(teardown.contains("navigatorAttached"))
        assertTrue(teardown.contains("navigator.detach()"))

        val newIntent = source.substringAfter(
            "override fun onNewIntent(intent: Intent)"
        ).substringBefore("@SuppressLint")
        val retainIntentIndex = newIntent.indexOf("setIntent(intent)")
        val initializationGuardIndex = newIntent.indexOf(
            "if (!contentInitializationAllowed)"
        )
        val processIntentIndex = newIntent.indexOf("processIntent(intent)")
        assertTrue(retainIntentIndex >= 0)
        assertTrue(initializationGuardIndex >= 0)
        assertTrue(processIntentIndex >= 0)
        assertTrue(retainIntentIndex < initializationGuardIndex)
        assertTrue(retainIntentIndex < processIntentIndex)
    }

    private fun repositoryRoot(): File {
        val workingDirectory = System.getProperty("user.dir")
            ?: error("The test process has no working directory")
        return generateSequence(File(workingDirectory)) {
            it.parentFile
        }.firstOrNull {
            File(it, "settings.gradle").isFile &&
                File(it, "app").isDirectory
        } ?: error("Cannot locate the Fearless Android repository root")
    }
}
