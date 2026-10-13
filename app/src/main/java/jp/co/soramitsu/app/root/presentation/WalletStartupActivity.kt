package jp.co.soramitsu.app.root.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
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
import jp.co.soramitsu.app.root.domain.WalletStartupPayload
import jp.co.soramitsu.app.root.domain.WalletStartupSession
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageHealth
import jp.co.soramitsu.common.resources.ContextManager
import jp.co.soramitsu.common.resources.LanguagesHolder
import jp.co.soramitsu.common.view.bottomSheet.AlertBottomSheet
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WalletStartupActivity : WalletGateActivity() {

    override fun onNewIntent(intent: Intent) {
        // Keep this override on the exported component explicit. The shared
        // base performs setIntent(), atomic retention, and ready fast-path
        // forwarding for both this component and legacy RootActivity tasks.
        super.onNewIntent(intent)
    }
}

internal class WalletStartupIntentPayload(
    val intent: Intent
) : WalletStartupPayload

internal fun Intent.toWalletInternalHandoff(
    context: Context,
    target: Class<*>
): Intent {
    return Intent(this).apply {
        // The exported startup Activity may receive caller-controlled task
        // flags. Preserve only URI grants needed by document imports, then
        // apply the wallet's own single-task-stack handoff contract.
        component = null
        setPackage(null)
        selector = null
        setClass(context, target)
        flags = (flags and WALLET_URI_GRANT_FLAGS) or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
}

/**
 * Lightweight launcher/legacy-task gate. It never restores a previous
 * FragmentManager and it does not construct the heavy wallet ViewModel.
 */
abstract class WalletGateActivity : AppCompatActivity() {

    @Inject
    lateinit var databaseStartupGate: WalletDatabaseStartupGate

    private var startupJob: Job? = null
    private var errorSheetVisible = false
    private var payloadSequence: Long = NO_PAYLOAD_SEQUENCE
    private var presentedFailureAttemptId: Long? = null
    private var recoveryRePresentationPending = false
    private val failurePresentationOwner = Any()

    override fun attachBaseContext(base: Context) {
        val contextManager = ContextManager.getInstanceOrInit(
            base.applicationContext,
            LanguagesHolder()
        )
        applyOverrideConfiguration(
            contextManager.setLocale(base).resources.configuration
        )
        super.attachBaseContext(contextManager.setLocale(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Deliberately discard restored fragments/navigation. The incoming
        // Bundle is used only for our primitive payload sequence below.
        super.onCreate(null)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        setContentView(R.layout.activity_wallet_startup)

        val restoredSequence = savedInstanceState
            ?.takeIf { it.containsKey(STATE_PAYLOAD_SEQUENCE) }
            ?.getLong(STATE_PAYLOAD_SEQUENCE)
        payloadSequence = WalletStartupSession.recordPayload(
            payload = WalletStartupIntentPayload(Intent(intent)),
            restoredSequence = restoredSequence
        )

        runStartupGate(retryAfterFailure = false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (payloadSequence != NO_PAYLOAD_SEQUENCE) {
            outState.putLong(STATE_PAYLOAD_SEQUENCE, payloadSequence)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        scheduleRecoveryRePresentation()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            scheduleRecoveryRePresentation()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        // Activity.getIntent() is not updated by the framework automatically.
        // Retain an immutable copy globally before any resumed Activity can
        // claim a handoff, and keep the Activity's current Intent in sync.
        setIntent(intent)
        payloadSequence = WalletStartupSession.recordPayload(
            WalletStartupIntentPayload(Intent(intent))
        )

        // A pending check already has a waiter. A healthy ready process takes
        // the fast path, while a durability-invalidated process immediately
        // returns its retained restart-required failure.
        if (
            WalletStartupSession.isReady() ||
            !WalletSecureStorageHealth.isHealthy()
        ) {
            runStartupGate(retryAfterFailure = false)
        }
    }

    override fun onDestroy() {
        presentedFailureAttemptId?.let { attemptId ->
            WalletStartupSession.releaseFailurePresentation(
                attemptId,
                failurePresentationOwner
            )
        }
        presentedFailureAttemptId = null
        super.onDestroy()
    }

    private fun runStartupGate(retryAfterFailure: Boolean) {
        if (startupJob?.isActive == true) return

        val sharedResult = if (retryAfterFailure) {
            WalletStartupSession.retry {
                performStartupCheck()
            }
        } else {
            WalletStartupSession.openOrJoin {
                performStartupCheck()
            }
        }
        startupJob = lifecycleScope.launch {
            val attempt = sharedResult.await()
            lifecycle.withStateAtLeast(Lifecycle.State.RESUMED) {
                when (attempt.result) {
                    WalletDatabaseStartupResult.Ready -> {
                        val claim = WalletStartupSession.claimLatestPayload()
                        if (!WalletStartupSession.isReady()) {
                            // A durability failure can race the completed gate.
                            // Re-enter after this lifecycle job completes so the
                            // retained restart-required result owns the UI.
                            scheduleInvalidatedReadyReentry()
                        } else if (claim == null) {
                            finish()
                        } else {
                            openWallet(claim.payload)
                        }
                    }

                    WalletDatabaseStartupResult.SecureStorageUnavailable -> {
                        if (
                            WalletStartupSession.claimFailurePresentation(
                                attempt.attemptId,
                                failurePresentationOwner
                            )
                        ) {
                            presentedFailureAttemptId = attempt.attemptId
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
                        if (
                            WalletStartupSession.claimFailurePresentation(
                                attempt.attemptId,
                                failurePresentationOwner
                            )
                        ) {
                            presentedFailureAttemptId = attempt.attemptId
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

                    WalletDatabaseStartupResult.ProcessRestartRequired -> {
                        if (
                            WalletStartupSession.claimFailurePresentation(
                                attempt.attemptId,
                                failurePresentationOwner
                            )
                        ) {
                            presentedFailureAttemptId = attempt.attemptId
                            showProcessRestartRequired()
                        } else {
                            finish()
                        }
                    }

                    is WalletDatabaseStartupResult.RecoveryRequired -> {
                        if (
                            WalletStartupSession.claimFailurePresentation(
                                attempt.attemptId,
                                failurePresentationOwner
                            )
                        ) {
                            presentedFailureAttemptId = attempt.attemptId
                            showRecoveryRequired(
                                diagnosticCode =
                                    attempt.result.diagnosticCode
                            )
                        } else {
                            finish()
                        }
                    }
                }
            }
        }
    }

    private fun scheduleInvalidatedReadyReentry() {
        val activeJob = startupJob
        if (activeJob == null) {
            window.decorView.post {
                if (!isFinishing && !isDestroyed) {
                    runStartupGate(retryAfterFailure = false)
                }
            }
            return
        }

        activeJob.invokeOnCompletion {
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    runStartupGate(retryAfterFailure = false)
                }
            }
        }
    }

    /**
     * Starts a new attempt only after an explicit retry action. A retained
     * failure otherwise remains stable across rotation and late entrants.
     */
    protected fun retryStartupGate() {
        presentedFailureAttemptId?.let { attemptId ->
            WalletStartupSession.releaseFailurePresentation(
                attemptId,
                failurePresentationOwner
            )
        }
        presentedFailureAttemptId = null
        errorSheetVisible = false
        runStartupGate(retryAfterFailure = true)
    }

    protected fun recoveryPresentationDismissed() {
        presentedFailureAttemptId?.let { attemptId ->
            WalletStartupSession.releaseFailurePresentation(
                attemptId,
                failurePresentationOwner
            )
        }
        presentedFailureAttemptId = null
        errorSheetVisible = false
        recoveryRePresentationPending = true
        scheduleRecoveryRePresentation()
    }

    private fun scheduleRecoveryRePresentation() {
        if (!recoveryRePresentationPending || isFinishing || isDestroyed) {
            return
        }
        window.decorView.post {
            if (
                !recoveryRePresentationPending ||
                isFinishing ||
                isDestroyed ||
                !lifecycle.currentState.isAtLeast(
                    Lifecycle.State.RESUMED
                ) ||
                !hasWindowFocus()
            ) {
                return@post
            }
            val activeJob = startupJob?.takeIf { it.isActive }
            if (activeJob != null) {
                activeJob.invokeOnCompletion {
                    runOnUiThread {
                        scheduleRecoveryRePresentation()
                    }
                }
                return@post
            }
            recoveryRePresentationPending = false
            runStartupGate(retryAfterFailure = false)
        }
    }

    protected open suspend fun performStartupCheck(): WalletDatabaseStartupResult {
        return databaseStartupGate.open()
    }

    protected open fun openWallet(payload: WalletStartupPayload) {
        val startupPayload = payload as? WalletStartupIntentPayload
            ?: run {
                finish()
                return
            }
        val forwardedIntent = startupPayload.intent.toWalletInternalHandoff(
            context = this,
            target = WalletRootActivity::class.java
        )
        startActivity(forwardedIntent)
        finish()
    }

    protected open fun showStartupError(title: String, message: String) {
        if (errorSheetVisible || isFinishing || isDestroyed) return
        errorSheetVisible = true

        AlertBottomSheet.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setButtonText(R.string.common_retry)
            .setCancelable(false)
            .callback {
                retryStartupGate()
            }
            .build()
            .show()
    }

    protected open fun showProcessRestartRequired() {
        if (errorSheetVisible || isFinishing || isDestroyed) return
        errorSheetVisible = true

        AlertBottomSheet.Builder(this)
            .setTitle(R.string.wallet_startup_restart_required_title)
            .setMessage(R.string.wallet_startup_restart_required_message)
            .setButtonText(R.string.common_close)
            .setCancelable(false)
            .callback {
                closeWalletProcess()
            }
            .build()
            .show()
    }

    protected open fun closeWalletProcess() {
        finishAndRemoveTask()
        Process.killProcess(Process.myPid())
    }

    protected open fun showRecoveryRequired(diagnosticCode: String) {
        if (errorSheetVisible || isFinishing || isDestroyed) return
        errorSheetVisible = true
        val diagnosticMessage = getString(
            R.string.wallet_startup_recovery_required_message,
            diagnosticCode
        )

        AlertBottomSheet.Builder(this)
            .setTitle(R.string.wallet_recovery_required_title)
            .setMessage(diagnosticMessage)
            .setButtonText(R.string.about_support)
            .setCancelable(true)
            .callback {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(
                            jp.co.soramitsu.common.BuildConfig.SUPPORT_URL
                        )
                    )
                )
            }
            .onDismiss {
                recoveryPresentationDismissed()
            }
            .build()
            .show()
    }

    private companion object {
        const val STATE_PAYLOAD_SEQUENCE = "wallet_startup_payload_sequence"
        const val NO_PAYLOAD_SEQUENCE = -1L
    }
}

private const val WALLET_URI_GRANT_FLAGS =
    Intent.FLAG_GRANT_READ_URI_PERMISSION or
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
