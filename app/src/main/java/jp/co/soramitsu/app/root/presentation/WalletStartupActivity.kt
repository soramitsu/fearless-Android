package jp.co.soramitsu.app.root.presentation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStateAtLeast
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.root.domain.WalletDatabaseStartupGate
import jp.co.soramitsu.app.root.domain.WalletDatabaseStartupResult
import jp.co.soramitsu.app.root.domain.WalletStartupSession
import jp.co.soramitsu.common.resources.ContextManager
import jp.co.soramitsu.common.resources.LanguagesHolder
import jp.co.soramitsu.common.view.bottomSheet.AlertBottomSheet
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WalletStartupActivity : WalletGateActivity()

/**
 * A deliberately lightweight gate shared by the launcher and the legacy
 * RootActivity component. The latter can be restored directly from a task
 * created by an older app version, so both entry points must enforce the same
 * key/database checks before the navigation graph or a ViewModel is created.
 */
abstract class WalletGateActivity : AppCompatActivity() {

    @Inject
    lateinit var databaseStartupGate: WalletDatabaseStartupGate

    private var startupJob: Job? = null
    private var errorSheetVisible = false

    override fun attachBaseContext(base: Context) {
        val contextManager = ContextManager.getInstanceOrInit(
            base.applicationContext,
            LanguagesHolder()
        )
        applyOverrideConfiguration(contextManager.setLocale(base).resources.configuration)
        super.attachBaseContext(contextManager.setLocale(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Never restore the previous heavy RootActivity's FragmentManager
        // state. On an in-place upgrade that bundle can contain the NavHost,
        // which would instantiate Room-backed ViewModels inside super.onCreate
        // and bypass this gate.
        super.onCreate(null)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        setContentView(R.layout.activity_wallet_startup)
        runStartupGate()
    }

    private fun runStartupGate() {
        if (startupJob?.isActive == true) return

        val startupCheck = WalletStartupSession.beginCheckUnlessReady()
        if (startupCheck == null) {
            startupJob = lifecycleScope.launch {
                lifecycle.withStateAtLeast(Lifecycle.State.RESUMED) {
                    if (WalletStartupSession.isReady()) {
                        openWallet()
                    }
                }
            }
            return
        }

        startupJob = lifecycleScope.launch {
            val result = performStartupCheck()
            lifecycle.withStateAtLeast(Lifecycle.State.RESUMED) {
                when (result) {
                    WalletDatabaseStartupResult.Ready -> {
                        if (WalletStartupSession.markReady(startupCheck)) {
                            openWallet()
                        } else {
                            finish()
                        }
                    }
                    WalletDatabaseStartupResult.SecureStorageUnavailable -> {
                        if (WalletStartupSession.isPending(startupCheck)) {
                            showStartupError(
                                title = getString(
                                    R.string.wallet_secure_storage_unavailable_title
                                ),
                                message = getString(
                                    R.string.wallet_secure_storage_unavailable_message
                                )
                            )
                        } else {
                            finish()
                        }
                    }
                    WalletDatabaseStartupResult.DatabaseOpenFailed -> {
                        if (WalletStartupSession.isPending(startupCheck)) {
                            showStartupError(
                                title = getString(
                                    R.string.wallet_database_unavailable_title
                                ),
                                message = getString(
                                    R.string.wallet_database_unavailable_message
                                )
                            )
                        } else {
                            finish()
                        }
                    }
                }
            }
        }
    }

    /**
     * Kept as a narrow override point for lifecycle instrumentation tests.
     * Production subclasses use the injected gate unchanged.
     */
    protected open suspend fun performStartupCheck(): WalletDatabaseStartupResult {
        return databaseStartupGate.open()
    }

    /**
     * Overridden only by the instrumentation host so a successful lifecycle
     * handoff can be observed without starting the full wallet graph.
     */
    protected open fun openWallet() {
        val forwardedIntent = Intent(intent).apply {
            setClass(this@WalletGateActivity, WalletRootActivity::class.java)
            flags = flags and Intent.FLAG_ACTIVITY_NEW_TASK.inv()
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(forwardedIntent)
        finish()
    }

    /**
     * Overridden by lifecycle instrumentation tests to observe presentation
     * after the real non-cancelable error sheet has been shown.
     */
    protected open fun showStartupError(title: String, message: String) {
        if (errorSheetVisible || isFinishing || isDestroyed) return
        errorSheetVisible = true

        AlertBottomSheet.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setButtonText(R.string.common_retry)
            .setCancelable(false)
            .callback {
                errorSheetVisible = false
                runStartupGate()
            }
            .build()
            .show()
    }
}
