package jp.co.soramitsu.app.root.domain

import androidx.annotation.VisibleForTesting
import java.util.concurrent.CancellationException
import kotlin.math.max
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageHealth

/**
 * Opaque startup payload retained while secure storage and Room are checked.
 *
 * The domain coordinator intentionally does not depend on Android's [Intent].
 * Production wraps an immutable Intent copy; JVM tests use small fake payloads.
 */
interface WalletStartupPayload

internal data class WalletStartupAttemptResult(
    val attemptId: Long,
    val result: WalletDatabaseStartupResult
)

internal data class WalletStartupPayloadClaim(
    val sequence: Long,
    val payload: WalletStartupPayload
)

/**
 * Process-local startup coordinator.
 *
 * One application-owned coroutine performs the gate at a time. Activities only
 * await its shared result, so rotation or a second launcher/deep-link entry
 * cannot reopen Room concurrently or cancel the check when its lifecycle ends.
 *
 * Incoming payloads are assigned monotonic sequence numbers. Only the newest
 * unforwarded payload can be claimed after the process becomes ready, and that
 * claim is atomic. This prevents duplicate WalletRootActivity launches while
 * still allowing later deep links in an already-ready process to be delivered.
 */
internal object WalletStartupSession {

    private sealed interface Phase {
        data object Idle : Phase

        data class Checking(
            val attemptId: Long,
            val completion: CompletableDeferred<WalletStartupAttemptResult>
        ) : Phase

        data class Failed(
            val attempt: WalletStartupAttemptResult,
            val completion: CompletableDeferred<WalletStartupAttemptResult>
        ) : Phase

        data object Ready : Phase
    }

    private data class PendingPayload(
        val sequence: Long,
        val payload: WalletStartupPayload
    )

    private val processScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

    private var phase: Phase = Phase.Idle
    private var attemptSequence = 0L
    private var payloadSequence = 0L
    private var latestPayload: PendingPayload? = null
    private var lastForwardedPayloadSequence = 0L
    private var failurePresentationOwner: Any? = null
    private val activeJobs = mutableMapOf<Long, Job>()

    @Volatile
    private var ready = false

    init {
        WalletSecureStorageHealth.addProcessRestartRequiredListener {
            invalidateForProcessRestart()
        }
    }

    /**
     * Records a new entry or restores the same entry after Activity recreation.
     *
     * A sequence from the saved instance state is reused only when it cannot
     * overwrite a newer process-local payload. After process death the session
     * is empty, so the restored sequence and Intent copy are admitted again.
     */
    @Synchronized
    fun recordPayload(
        payload: WalletStartupPayload,
        restoredSequence: Long? = null
    ): Long {
        if (restoredSequence != null) {
            val current = latestPayload
            when {
                current?.sequence == restoredSequence -> {
                    latestPayload = current.copy(payload = payload)
                    return restoredSequence
                }

                current != null && current.sequence > restoredSequence -> {
                    // Keep the restoring Activity associated with its original
                    // sequence. Returning the newer sequence would let a
                    // second recreation of this stale Activity masquerade as
                    // (and overwrite) the newer deep-link payload.
                    return restoredSequence
                }

                restoredSequence <= lastForwardedPayloadSequence -> {
                    return restoredSequence
                }

                else -> {
                    payloadSequence = max(payloadSequence, restoredSequence)
                    latestPayload = PendingPayload(restoredSequence, payload)
                    return restoredSequence
                }
            }
        }

        payloadSequence += 1L
        latestPayload = PendingPayload(payloadSequence, payload)
        return payloadSequence
    }

    /**
     * Returns the shared in-flight result, starts exactly one check, or returns
     * the ready fast path without invoking [performStartupCheck].
     */
    fun openOrJoin(
        performStartupCheck: suspend () -> WalletDatabaseStartupResult
    ): Deferred<WalletStartupAttemptResult> = startOrJoin(
        retryAfterFailure = false,
        performStartupCheck = performStartupCheck
    )

    /**
     * Explicitly retries a retained failure. Concurrent retry requests still
     * join the same new in-flight check, while a ready process stays fast.
     */
    fun retry(
        performStartupCheck: suspend () -> WalletDatabaseStartupResult
    ): Deferred<WalletStartupAttemptResult> = startOrJoin(
        retryAfterFailure = true,
        performStartupCheck = performStartupCheck
    )

    private fun startOrJoin(
        retryAfterFailure: Boolean,
        performStartupCheck: suspend () -> WalletDatabaseStartupResult
    ): Deferred<WalletStartupAttemptResult> {
        if (!WalletSecureStorageHealth.isHealthy()) {
            return invalidateForProcessRestart()
        }

        var attemptToLaunch: Phase.Checking? = null
        var storageBecameUnhealthy = false

        val completion = synchronized(this) {
            if (!WalletSecureStorageHealth.isHealthy()) {
                storageBecameUnhealthy = true
                null
            } else {
                when (val current = phase) {
                    Phase.Ready -> {
                        return@synchronized CompletableDeferred(
                            WalletStartupAttemptResult(
                                attemptId = READY_FAST_PATH_ATTEMPT_ID,
                                result = WalletDatabaseStartupResult.Ready
                            )
                        )
                    }

                    is Phase.Checking -> current.completion

                    is Phase.Failed -> {
                        if (
                            !retryAfterFailure ||
                            current.attempt.result ==
                            WalletDatabaseStartupResult.ProcessRestartRequired
                        ) {
                            return@synchronized current.completion
                        }
                        createCheckingPhase().also {
                            attemptToLaunch = it
                        }.completion
                    }

                    Phase.Idle -> {
                        createCheckingPhase().also {
                            attemptToLaunch = it
                        }.completion
                    }
                }
            }
        }

        if (storageBecameUnhealthy) {
            return invalidateForProcessRestart()
        }
        val healthyCompletion = checkNotNull(completion)

        attemptToLaunch?.let { checking ->
            // Register the process-owned worker before it can execute. An
            // immediate startup result must not win the launch/registration
            // race and then be cancelled while publishing its completion.
            val job = processScope.launch(start = CoroutineStart.LAZY) {
                val result = try {
                    performStartupCheck()
                } catch (failure: CancellationException) {
                    if (!currentCoroutineContext().isActive) {
                        cancelAttempt(checking)
                        return@launch
                    }

                    // A dependency may use CancellationException as its own
                    // failure signal even though this process-owned worker was
                    // not cancelled. Treat that as a closed startup failure so
                    // every waiting Activity receives retry UI instead of
                    // being cancelled into a permanent splash screen.
                    WalletDatabaseStartupResult.DatabaseOpenFailed
                } catch (_: Throwable) {
                    WalletDatabaseStartupResult.DatabaseOpenFailed
                }

                completeAttempt(checking, result)
            }

            val shouldStart = synchronized(this) {
                if (phase === checking) {
                    activeJobs[checking.attemptId] = job
                    true
                } else {
                    false
                }
            }
            if (shouldStart) {
                job.start()
            } else {
                job.cancel()
            }
        }

        return healthyCompletion
    }

    @Synchronized
    private fun createCheckingPhase(): Phase.Checking {
        attemptSequence += 1L
        return Phase.Checking(
            attemptId = attemptSequence,
            completion = CompletableDeferred()
        ).also {
            phase = it
            failurePresentationOwner = null
        }
    }

    /**
     * Claims the newest retained payload once the gate is ready.
     *
     * The sequence update and readiness check share the same lock, so two
     * resumed activities cannot both launch/forward the heavy root.
     */
    fun claimLatestPayload(): WalletStartupPayloadClaim? {
        if (!WalletSecureStorageHealth.isHealthy()) {
            invalidateForProcessRestart()
            return null
        }

        var storageBecameUnhealthy = false
        val claim = synchronized(this) {
            if (!WalletSecureStorageHealth.isHealthy()) {
                storageBecameUnhealthy = true
                return@synchronized null
            }
            if (phase != Phase.Ready) return@synchronized null

            val pending = latestPayload ?: return@synchronized null
            if (pending.sequence <= lastForwardedPayloadSequence) {
                return@synchronized null
            }

            lastForwardedPayloadSequence = pending.sequence
            latestPayload = null
            WalletStartupPayloadClaim(
                sequence = pending.sequence,
                payload = pending.payload
            )
        }
        if (storageBecameUnhealthy) {
            invalidateForProcessRestart()
        }
        return claim
    }

    /**
     * Allows one resumed Activity to own the non-cancelable failure UI.
     */
    @Synchronized
    fun claimFailurePresentation(
        attemptId: Long,
        owner: Any
    ): Boolean {
        val failed = phase as? Phase.Failed
        if (failed?.attempt?.attemptId != attemptId) {
            return false
        }

        if (failurePresentationOwner != null) {
            return failurePresentationOwner === owner
        }

        failurePresentationOwner = owner
        return true
    }

    /**
     * Releases only the caller's presentation lease. This lets a recreated
     * Activity show the retained failure without allowing two live Activities
     * to display duplicate retry sheets.
     */
    @Synchronized
    fun releaseFailurePresentation(attemptId: Long, owner: Any) {
        val failed = phase as? Phase.Failed
        if (
            failed?.attempt?.attemptId == attemptId &&
            failurePresentationOwner === owner
        ) {
            failurePresentationOwner = null
        }
    }

    fun isReady(): Boolean {
        if (!WalletSecureStorageHealth.isHealthy()) {
            invalidateForProcessRestart()
            return false
        }

        val readySnapshot = ready
        if (!WalletSecureStorageHealth.isHealthy()) {
            invalidateForProcessRestart()
            return false
        }
        return readySnapshot
    }

    private fun completeAttempt(
        checking: Phase.Checking,
        result: WalletDatabaseStartupResult
    ) {
        if (result == WalletDatabaseStartupResult.ProcessRestartRequired) {
            WalletSecureStorageHealth.latchProcessRestartRequired()
            invalidateForProcessRestart()
            return
        }
        if (
            result == WalletDatabaseStartupResult.Ready &&
            !WalletSecureStorageHealth.isHealthy()
        ) {
            invalidateForProcessRestart()
            return
        }

        var storageBecameUnhealthy = false
        synchronized(this) {
            val current = phase
            if (current !== checking) {
                checking.completion.cancel()
                return
            }
            activeJobs.remove(checking.attemptId)

            if (
                result == WalletDatabaseStartupResult.Ready &&
                !WalletSecureStorageHealth.isHealthy()
            ) {
                storageBecameUnhealthy = true
            } else if (result == WalletDatabaseStartupResult.Ready) {
                ready = true
                phase = Phase.Ready
                failurePresentationOwner = null
            } else {
                ready = false
                val attempt = WalletStartupAttemptResult(
                    checking.attemptId,
                    result
                )
                phase = Phase.Failed(attempt, checking.completion)
                failurePresentationOwner = null
            }
        }

        if (storageBecameUnhealthy) {
            invalidateForProcessRestart()
            return
        }
        checking.completion.complete(
            WalletStartupAttemptResult(checking.attemptId, result)
        )
    }

    /**
     * Synchronously replaces every process-local phase with the non-retryable
     * restart result. If a gate is in flight, its existing Deferred is
     * completed with that result so lifecycle waiters cannot remain stranded.
     */
    private fun invalidateForProcessRestart(): CompletableDeferred<
        WalletStartupAttemptResult
    > {
        var completionToComplete:
            CompletableDeferred<WalletStartupAttemptResult>? = null
        var attemptToComplete: WalletStartupAttemptResult? = null
        var jobsToCancel: List<Job> = emptyList()

        val retainedCompletion = synchronized(this) {
            val current = phase
            val retainedRestartFailure = current as? Phase.Failed
            if (
                retainedRestartFailure?.attempt?.result ==
                WalletDatabaseStartupResult.ProcessRestartRequired
            ) {
                ready = false
                return@synchronized retainedRestartFailure.completion
            }

            val completion: CompletableDeferred<WalletStartupAttemptResult>
            val attemptId: Long
            if (current is Phase.Checking) {
                completion = current.completion
                attemptId = current.attemptId
            } else {
                attemptSequence += 1L
                completion = CompletableDeferred()
                attemptId = attemptSequence
            }

            val attempt = WalletStartupAttemptResult(
                attemptId = attemptId,
                result = WalletDatabaseStartupResult.ProcessRestartRequired
            )
            ready = false
            phase = Phase.Failed(attempt, completion)
            failurePresentationOwner = null
            jobsToCancel = activeJobs.values.toList()
            activeJobs.clear()
            completionToComplete = completion
            attemptToComplete = attempt
            completion
        }

        completionToComplete?.complete(checkNotNull(attemptToComplete))
        jobsToCancel.forEach(Job::cancel)
        return retainedCompletion
    }

    private fun cancelAttempt(checking: Phase.Checking) {
        synchronized(this) {
            val current = phase
            if (current === checking) {
                activeJobs.remove(checking.attemptId)
                ready = false
                phase = Phase.Idle
            }
        }
        checking.completion.cancel()
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun resetForTest() {
        val jobs: List<Job>
        val completion: CompletableDeferred<WalletStartupAttemptResult>?
        synchronized(this) {
            completion = (phase as? Phase.Checking)?.completion
            jobs = activeJobs.values.toList()
            activeJobs.clear()
            phase = Phase.Idle
            payloadSequence = 0L
            latestPayload = null
            lastForwardedPayloadSequence = 0L
            failurePresentationOwner = null
            ready = false
        }
        completion?.cancel()
        jobs.forEach(Job::cancel)
    }

    private const val READY_FAST_PATH_ATTEMPT_ID = 0L
}
