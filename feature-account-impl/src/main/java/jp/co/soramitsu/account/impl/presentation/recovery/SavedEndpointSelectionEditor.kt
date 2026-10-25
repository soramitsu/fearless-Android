package jp.co.soramitsu.account.impl.presentation.recovery

import jp.co.soramitsu.account.impl.domain.recovery.SavedEndpointAccess
import jp.co.soramitsu.account.impl.domain.recovery.SavedEndpointSelectionOperation
import jp.co.soramitsu.account.impl.domain.recovery.SavedEndpointSelections
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.core.runtime.EndpointRuntimeStatus
import jp.co.soramitsu.coredb.recovery.decision.EndpointRecoveryDecision
import jp.co.soramitsu.coredb.recovery.decision.EndpointSelectionRequest
import jp.co.soramitsu.coredb.recovery.decision.EndpointSelectionResult
import jp.co.soramitsu.coredb.recovery.selection.EndpointMutationLane
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Main-thread controller for an exact rendered observation and one explicit Select action. */
internal class SavedEndpointSelectionEditor(
    private val owner: Any,
    private val operations: SavedEndpointSelections,
    private val scope: CoroutineScope,
    private val currentLease: () -> SavedEndpointAccess.Lease?,
    private val publish: (SavedEndpointSelectionState?) -> Unit
) {
    var state: SavedEndpointSelectionState? = null
        private set
    private var lease: SavedEndpointAccess.Lease? = null
    private var observation: EndpointMutationLane.RuntimeRead<EndpointSelectionRequest?>? = null
    private var operation: SavedEndpointSelectionOperation? = null
    private var generation = Any()
    private var job: Job? = null

    /** Read-only eligibility never starts a connection, probe or mutation. */
    fun open(decision: EndpointRecoveryDecision) {
        clear()
        val activeLease = currentLease() ?: return
        lease = activeLease
        val token = generation
        update(SavedEndpointSelectionState(SavedEndpointSelectionState.Phase.CHECKING, current = { current(token) }))
        launchJob {
            try {
                val observed = received<EndpointMutationLane.RuntimeRead<EndpointSelectionRequest?>> { callback ->
                    operations.observe(owner, activeLease, decision, callback)
                }
                if (!current(token)) return@launchJob
                observation = observed
                val ready = if (observed.value == null) SavedEndpointSelectionState.Phase.NOT_ATTACHED else SavedEndpointSelectionState.Phase.AVAILABLE
                fun eligible() = current(token) && operations.current(observed)
                update(SavedEndpointSelectionState(ready, current = ::eligible))
                operations.runtimeStatus(decision.target.chainId).collect {
                    if (current(token)) update(SavedEndpointSelectionState(ready, current = ::eligible))
                }
            } catch (canceled: CancellationException) {
                throw canceled
            } catch (_: Exception) {
                if (current(token)) {
                    update(
                        SavedEndpointSelectionState(SavedEndpointSelectionState.Phase.FAILED, current = {
                    current(token)
                })
                    )
                }
            }
        }
    }

    /** The caller also admits the exact displayed form; this admits its exact owner/version once. */
    fun select(expected: SavedEndpointSelectionState) {
        if (state !== expected || expected.phase != SavedEndpointSelectionState.Phase.AVAILABLE) return
        val observed = observation ?: return
        val activeLease = lease?.takeIf { currentLease() === it } ?: return clear()
        val selected = operations.newOperation(owner, activeLease, observed)
        if (selected == null) {
            update(SavedEndpointSelectionState(SavedEndpointSelectionState.Phase.STALE))
            return
        }
        retireWork()
        lease = activeLease
        operation = selected
        val token = generation
        update(SavedEndpointSelectionState(SavedEndpointSelectionState.Phase.SAVING, current = { current(token) }))
        launchJob {
            try {
                val result = received<EndpointSelectionResult> { callback -> operations.execute(selected, callback) }
                if (!current(token) || !operations.current(selected)) return@launchJob
                check(result.decision === selected.request.decision && result.owner === selected.request.owner)
                val decision = result.decision
                val node = ChainNode(decision.candidate.url, decision.candidate.name, isActive = true, isDefault = false)
                fun saved(status: EndpointRuntimeStatus?) = SavedEndpointSelectionState(
                    SavedEndpointSelectionState.Phase.SAVED, decision.target.family, node, status,
                    current = { current(token) && operations.current(selected) }
                )
                update(saved(null))
                operations.runtimeStatus(decision.target.chainId).collect { status ->
                    if (current(token)) update(saved(status))
                }
            } catch (canceled: CancellationException) {
                throw canceled
            } catch (_: Exception) {
                if (current(token)) {
                    update(
                        SavedEndpointSelectionState(SavedEndpointSelectionState.Phase.FAILED, current = {
                    current(token)
                })
                    )
                }
            }
        }
    }

    /** Retire on edit, target/source refresh, attachment, back or lifecycle/PIN changes. */
    fun clear() {
        retireWork()
        lease = null
        observation = null
        update(null)
    }

    private fun current(token: Any): Boolean = token === generation && lease != null && currentLease() === lease

    private fun retireWork() {
        generation = Any()
        operation?.cancel()
        operation = null
        observation = null
        job?.cancel()
        job = null
    }

    private fun update(value: SavedEndpointSelectionState?) {
        state = value
        publish(value)
    }

    private fun launchJob(work: suspend () -> Unit) {
        val pending = scope.launch(start = CoroutineStart.LAZY) { work() }
        job = pending
        pending.start()
    }

    private suspend fun <T> received(work: suspend ((Result<T>) -> Unit) -> Unit): T {
        var result: Result<T>? = null
        work { result = it }
        currentCoroutineContext().ensureActive()
        return (result ?: throw CancellationException("Endpoint selection lease retired")).getOrThrow()
    }
}
