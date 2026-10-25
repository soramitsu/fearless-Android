package jp.co.soramitsu.account.impl.domain.recovery

import javax.inject.Inject
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.recovery.EndpointInventorySource
import jp.co.soramitsu.coredb.recovery.EndpointRecordRef
import jp.co.soramitsu.coredb.recovery.decision.EndpointDecisionAuthority
import jp.co.soramitsu.coredb.recovery.decision.EndpointDecisionCandidate
import jp.co.soramitsu.coredb.recovery.decision.EndpointDecisionSourceBinding
import jp.co.soramitsu.coredb.recovery.decision.EndpointDecisionTarget
import jp.co.soramitsu.coredb.recovery.decision.EndpointDecisionTargetCatalog
import jp.co.soramitsu.coredb.recovery.decision.EndpointDecisionTargetPage
import jp.co.soramitsu.coredb.recovery.decision.EndpointRecoveryDecision
import jp.co.soramitsu.coredb.recovery.decision.EndpointRecoveryDecisionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Offline decision bridge for the existing PIN lease and saved-endpoints editor.
 * It performs no network or selection work. Lifecycle handlers and publication share
 * the Android main thread; no DB work occurs under the access monitor.
 */
class SavedEndpointDecisions internal constructor(
    private val access: SavedEndpointAccess,
    private val store: () -> EndpointRecoveryDecisionStore,
    private val catalog: () -> EndpointDecisionTargetCatalog
) {
    @Inject constructor(database: AppDatabase, access: SavedEndpointAccess) :
        this(access, { EndpointRecoveryDecisionStore(database.openHelper.writableDatabase) },
            { EndpointDecisionTargetCatalog(database.openHelper.writableDatabase) })

    /** Capture a bounded source fingerprint under the exact current PIN lease. */
    suspend fun prepareSource(
        owner: Any, lease: SavedEndpointAccess.Lease, ref: EndpointRecordRef,
        publish: (Result<EndpointDecisionSourceBinding>) -> Unit
    ) = withDecisionLease(access, owner, lease, {
        withSavedEndpointReadDeadline { signal -> store().prepareSource(signal, ref) }
    }, publish)

    /** Capture a present trusted catalog identity under the exact current PIN lease. */
    suspend fun prepareTarget(
        owner: Any, lease: SavedEndpointAccess.Lease, chainId: String,
        publish: (Result<EndpointDecisionTarget?>) -> Unit
    ) = withDecisionLease(access, owner, lease, {
        withSavedEndpointReadDeadline { signal -> store().prepareTarget(signal, chainId) }
    }, publish)

    /** Read bounded offline intent and publish only to its still-current owner. */
    suspend fun read(
        owner: Any, lease: SavedEndpointAccess.Lease, source: EndpointInventorySource, rowId: Long,
        publish: (Result<EndpointRecoveryDecision?>) -> Unit
    ) = withDecisionLease(access, owner, lease, {
        withSavedEndpointReadDeadline { signal -> store().read(signal, source, rowId) }
    }, publish)

    /** Page the complete local catalog, including inactive networks with no nodes. */
    suspend fun readTargets(
        owner: Any, lease: SavedEndpointAccess.Lease, after: Long?, before: Long?,
        publish: (Result<EndpointDecisionTargetPage>) -> Unit
    ) = withDecisionLease(access, owner, lease, {
        withSavedEndpointReadDeadline { signal -> catalog().readPage(signal, after, before) }
    }, publish)

    /** Persist pending intent under a single-use grant; the result grants no network or selection authority. */
    suspend fun saveOffline(
        owner: Any,
        lease: SavedEndpointAccess.Lease,
        source: EndpointDecisionSourceBinding,
        candidate: EndpointDecisionCandidate,
        target: EndpointDecisionTarget,
        expectedRevision: Long?,
        publish: (Result<EndpointRecoveryDecision>) -> Unit
    ) = withDecisionLease(access, owner, lease, { authority ->
        withSavedEndpointReadDeadline { signal ->
            store().saveOffline(signal, source, candidate, target, expectedRevision, authority)
        }
    }, publish)
}

/**
 * Publication is one main-thread continuation, with no suspension between its
 * exact lease check and callback. The callback must update state synchronously,
 * never defer publication or treat this result as permission for another action.
 */
internal suspend fun <T> withDecisionLease(
    access: SavedEndpointAccess,
    owner: Any,
    lease: SavedEndpointAccess.Lease,
    work: suspend (EndpointDecisionAuthority) -> T,
    publish: (Result<T>) -> Unit
) {
    val authority = withContext(Dispatchers.Main.immediate) {
        access.decisionAuthority(owner, lease)?.takeIf { it.isCurrent() }
    } ?: return
    val result = try {
        Result.success(work(authority))
    } catch (canceled: CancellationException) {
        throw canceled
    } catch (failure: Exception) {
        Result.failure(failure)
    }
    withContext(Dispatchers.Main.immediate) {
        currentCoroutineContext().ensureActive()
        if (access.decisionAuthority(owner, lease) === authority && authority.isCurrent()) {
            publish(result)
        }
    }
}
