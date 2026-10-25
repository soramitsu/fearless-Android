package jp.co.soramitsu.account.impl.domain.recovery

import jp.co.soramitsu.core.runtime.EndpointRuntimeStatus
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.recovery.decision.EndpointRecoveryDecision
import jp.co.soramitsu.coredb.recovery.decision.EndpointSelectionAuthority
import jp.co.soramitsu.coredb.recovery.decision.EndpointSelectionCapture
import jp.co.soramitsu.coredb.recovery.decision.EndpointSelectionRequest
import jp.co.soramitsu.coredb.recovery.decision.EndpointSelectionResult
import jp.co.soramitsu.coredb.recovery.decision.EndpointSelectionStore
import jp.co.soramitsu.coredb.recovery.selection.EndpointMutationLane
import jp.co.soramitsu.runtime.multiNetwork.connection.ConnectionPool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Read-only eligibility and a separate explicit selection action under the current PIN lease. */
class SavedEndpointSelections internal constructor(
    private val access: SavedEndpointAccess,
    private val storage: Storage,
    private val lane: EndpointMutationLane,
    private val runtime: (String) -> Flow<EndpointRuntimeStatus?> = { flowOf(null) }
) {
    @Inject constructor(database: AppDatabase, access: SavedEndpointAccess, pool: ConnectionPool) :
        this(access, SqlStorage(database), database.chainDao().endpointMutationLane, pool::runtimeStatus)

    /** Observation grants no mutation, network request, verification or transport claim. */
    internal suspend fun observe(
        owner: Any,
        lease: SavedEndpointAccess.Lease,
        decision: EndpointRecoveryDecision,
        publish: (Result<EndpointMutationLane.RuntimeRead<EndpointSelectionRequest?>>) -> Unit
    ) = withDecisionLease(access, owner, lease, { storage.observe(decision) }, publish)

    /** Main-thread explicit tap captures the exact rendered observation before any suspension. */
    internal fun newOperation(
        owner: Any,
        lease: SavedEndpointAccess.Lease,
        observation: EndpointMutationLane.RuntimeRead<EndpointSelectionRequest?>
    ): SavedEndpointSelectionOperation? {
        val request = observation.value ?: return null
        val authority = access.selectionAuthority(owner, lease, lane)?.takeIf { it.isCurrent() } ?: return null
        val mutation = lane.admitObserved(observation.version) ?: return null
        return SavedEndpointSelectionOperation(owner, lease, authority, request, lane, mutation)
    }

    internal suspend fun execute(
        operation: SavedEndpointSelectionOperation,
        publish: (Result<EndpointSelectionResult>) -> Unit
    ) {
        val admitted = withContext(Dispatchers.Main.immediate) {
            if (!current(operation)) {
                operation.cancel()
            false
            } else {
                operation.begin()
            }
        }
        if (!admitted) return
        try {
            val result = try {
                val capture = storage.prepare(operation)
                currentCoroutineContext().ensureActive()
                val attempt = operation.bind(capture)
                Result.success(storage.commit(capture, operation.authority, attempt))
            } catch (canceled: CancellationException) {
                throw canceled
            } catch (failure: Exception) {
                Result.failure(failure)
            }
            withContext(Dispatchers.Main.immediate) {
                currentCoroutineContext().ensureActive()
                if (current(operation) && operation.grantPublication()) publish(result)
            }
        } finally {
            operation.cancel()
        }
    }

    internal fun current(operation: SavedEndpointSelectionOperation): Boolean =
        access.selectionAuthority(operation.owner, operation.lease, lane) === operation.authority &&
            operation.authority.isCurrent() && lane.isLatest(operation.mutation)

    internal fun current(observation: EndpointMutationLane.RuntimeRead<EndpointSelectionRequest?>): Boolean =
        lane.isCurrent(observation.version)

    /** Wake on runtime or lane currency changes without acquiring a connection or mutating selection. */
    internal fun runtimeStatus(chainId: String): Flow<EndpointRuntimeStatus?> =
        combine(runtime(chainId), lane.runtimeChanges) { status, _ -> status }

    internal interface Storage {
        suspend fun observe(
            decision: EndpointRecoveryDecision
        ): EndpointMutationLane.RuntimeRead<EndpointSelectionRequest?>
        suspend fun prepare(operation: SavedEndpointSelectionOperation): EndpointSelectionCapture
        suspend fun commit(
            capture: EndpointSelectionCapture,
            authority: EndpointSelectionAuthority,
            attempt: EndpointSelectionAuthority.Attempt
        ): EndpointSelectionResult
    }

    private class SqlStorage(private val database: AppDatabase) : Storage {
        private fun store() =
            EndpointSelectionStore(database.openHelper.writableDatabase, database.chainDao().endpointMutationLane)
        override suspend fun observe(decision: EndpointRecoveryDecision) = withSavedEndpointMutationDeadline { signal ->
            store().observeAttached(signal, decision)
        }
        override suspend fun prepare(operation: SavedEndpointSelectionOperation) =
            withSavedEndpointMutationDeadline { signal ->
            store().prepare(signal, operation.request, operation.mutation)
        }
        override suspend fun commit(
            capture: EndpointSelectionCapture,
            authority: EndpointSelectionAuthority,
            attempt: EndpointSelectionAuthority.Attempt
        ) = withSavedEndpointMutationDeadline { signal ->
            store().commit(signal, capture, authority, attempt)
        }
    }
}
