package jp.co.soramitsu.coredb.recovery.selection

import jp.co.soramitsu.coredb.recovery.decision.EndpointDecisionLimits
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One database lifetime's process-local order for endpoint selection and topology writers.
 * Admit before queueing. Waiting and SQL never hold [stateLock]; callbacks run outside it.
 * This is not a cross-process revision, an authentication token, or persistent authority.
 */
class EndpointMutationLane internal constructor(private var sequence: Long) {
    internal val stateLock = Any()
    private val serial = Mutex()
    private var open = true
    private val latest = mutableMapOf<String, Operation>()
    private var latestCatalog: Operation? = null
    private val runtimeChangeState = MutableStateFlow(Any())

    /** Conflated wake-up only. Consumers must fresh-read and recheck demand; this grants no work. */
    val runtimeChanges: StateFlow<Any> = runtimeChangeState.asStateFlow()

    constructor() : this(0)

    private fun notifyRuntimeChange() {
        check(!Thread.holdsLock(stateLock))
        runtimeChangeState.value = Any()
    }

    /** Opaque, non-restorable command identity. Endpoint text is excluded from diagnostics. */
    class Operation internal constructor(
        internal val lane: EndpointMutationLane,
        internal val chainIds: Set<String>,
        internal val sequence: Long,
        internal val catalogReplacement: Boolean = false
    ) {
        internal var consumed = false
        internal var entered = false
        override fun toString(): String = "EndpointMutationOperation"
    }

    /** Read-only process version; it grants neither a mutation nor authenticated action. */
    class RuntimeVersion internal constructor(
        internal val lane: EndpointMutationLane,
        internal val chainId: String,
        internal val chain: Operation?,
        internal val chainConsumed: Boolean?,
        internal val catalog: Operation?,
        internal val catalogConsumed: Boolean?
    ) {
        override fun toString(): String = "EndpointRuntimeVersion"
    }

    class RuntimeReadSuperseded internal constructor() : IllegalStateException("Endpoint runtime read was superseded")

    class RuntimeRead<T> internal constructor(val value: T, val version: RuntimeVersion) {
        override fun toString(): String = "EndpointRuntimeRead"
    }

    /**
     * The DAO owns this fresh read. SQL and physical commit share the serial lane; no state
     * monitor is held during SQL. A held preparation's later final grant also invalidates it.
     */
    internal suspend fun <T> readRuntime(chainId: String, read: suspend () -> T): RuntimeRead<T> {
        EndpointDecisionLimits.requireText(chainId, EndpointDecisionLimits.TARGET_ID_BYTES, nonempty = true)
        return serial.withLock {
            val version = synchronized(stateLock) {
                check(open) { "Endpoint runtime order is unavailable" }
                RuntimeVersion(
                    this, chainId, latest[chainId], latest[chainId]?.consumed,
                    latestCatalog, latestCatalog?.consumed
                )
            }
            val value = read()
            if (!isCurrent(version)) throw RuntimeReadSuperseded()
            RuntimeRead(value, version)
        }
    }

    /** Pure predicate only; callers may use it under a transport state lock, never as a write grant. */
    fun isCurrent(version: RuntimeVersion): Boolean = synchronized(stateLock) {
        require(version.lane === this) { "Endpoint runtime version belongs to another database" }
        open && latest[version.chainId] === version.chain && latest[version.chainId]?.consumed == version.chainConsumed &&
            latestCatalog === version.catalog && latestCatalog?.consumed == version.catalogConsumed
    }

    /** Automatic requalification waits until the overlapping intent is consumed or superseded. */
    fun isSettled(version: RuntimeVersion): Boolean = synchronized(stateLock) {
        isCurrent(version) && !pending(latest[version.chainId]) && !pending(latestCatalog)
    }

    private fun pending(operation: Operation?): Boolean =
        operation != null && isLatestLocked(operation) && !operation.consumed

    /** Atomically order an explicit action from exactly the rendered read-only observation. */
    fun admitObserved(version: RuntimeVersion): Operation? {
        val operation = synchronized(stateLock) {
            if (!isCurrent(version)) null else admitLocked(setOf(version.chainId))
        }
        if (operation != null) notifyRuntimeChange()
        return operation
    }

    /** Every new overlapping intent retires older queued work, including A-to-B-to-A. */
    fun admit(chainIds: Collection<String>): Operation {
        val ids = chainIds.toSet()
        require(ids.isNotEmpty() && ids.size <= MAX_CHAINS)
        ids.forEach { EndpointDecisionLimits.requireText(it, EndpointDecisionLimits.TARGET_ID_BYTES, nonempty = true) }
        val operation = synchronized(stateLock) { admitLocked(ids) }
        notifyRuntimeChange()
        return operation
    }

    private fun admitLocked(ids: Set<String>): Operation {
        check(Thread.holdsLock(stateLock))
        check(open && sequence in 0 until Long.MAX_VALUE) { "Endpoint mutation order is unavailable" }
        check((latest.keys + ids).size <= MAX_CHAINS) { "Endpoint mutation order exceeds its chain budget" }
        return Operation(this, ids, ++sequence).also { operation -> ids.forEach { latest[it] = operation } }
    }

    /**
     * A catalog replacement discovers removals from a fresh lane-owned snapshot.
     * It therefore fences all earlier chains, including parents unknown at admission.
     * Any later chain intent retires this whole replacement before its final grant.
     */
    fun admitCatalogReplacement(): Operation {
        val operation = synchronized(stateLock) {
            check(open && sequence in 0 until Long.MAX_VALUE) { "Endpoint mutation order is unavailable" }
            Operation(this, emptySet(), ++sequence, catalogReplacement = true).also {
                latestCatalog = it
                latest.clear()
            }
        }
        notifyRuntimeChange()
        return operation
    }

    /** Preparation may release the lane and later reacquire it only with this still-current identity. */
    suspend fun <T> withOperation(operation: Operation, action: suspend () -> T): T {
        synchronized(stateLock) { requireCurrentLocked(operation) }
        try {
            return serial.withLock(operation) {
                synchronized(stateLock) {
                    requireCurrentLocked(operation)
                    check(!operation.entered)
                    operation.entered = true
                }
                try {
                    action()
                } finally {
                    synchronized(stateLock) { operation.entered = false }
                }
            }
        } catch (failure: Throwable) {
            abandon(operation)
            throw failure
        } finally {
            // grantFinal can consume an identity before physical commit; wake only after serial release.
            notifyRuntimeChange()
        }
    }

    /** Retire queued/prepared work without waiting for storage. An already granted commit may finish. */
    fun abandon(operation: Operation) {
        synchronized(stateLock) {
            require(operation.lane === this)
            operation.consumed = true
        }
        notifyRuntimeChange()
    }

    /** Current admission only; a final grant must repeat this check while the lane is held. */
    fun isCurrent(operation: Operation): Boolean = synchronized(stateLock) {
        require(operation.lane === this)
        isLatestLocked(operation) && !operation.consumed
    }

    /** A result's command may be latest after consumption; this does not grant work or UI publication. */
    fun isLatest(operation: Operation): Boolean = synchronized(stateLock) {
        require(operation.lane === this)
        isLatestLocked(operation)
    }

    /** Database close/replacement retires every old capture; a new database gets a new lane. */
    fun retire() {
        synchronized(stateLock) {
            open = false
            latest.clear()
            latestCatalog = null
        }
        notifyRuntimeChange()
    }

    internal fun requireChain(operation: Operation, chainId: String) {
        synchronized(stateLock) {
            requireCurrentLocked(operation)
            require(operation.chainIds == setOf(chainId)) { "Endpoint mutation target differs" }
        }
    }

    /** An explicit transaction helper may cover a subset of its already admitted operation. */
    internal fun requireOwnedTargets(operation: Operation, chainIds: Collection<String>) {
        val ids = chainIds.toSet()
        require(ids.size <= MAX_CHAINS)
        ids.forEach { EndpointDecisionLimits.requireText(it, EndpointDecisionLimits.TARGET_ID_BYTES, nonempty = true) }
        synchronized(stateLock) {
            requireCurrentLocked(operation)
            check(operation.entered) { "Endpoint mutation does not own its lane" }
            require(operation.catalogReplacement || operation.chainIds.containsAll(ids)) { "Endpoint mutation targets differ" }
        }
    }

    internal fun requireOwnedCatalog(operation: Operation) {
        synchronized(stateLock) {
            requireCurrentLocked(operation)
            require(operation.catalogReplacement && operation.entered) { "Endpoint catalog does not own its lane" }
        }
    }

    /** Only pure request-grant/completion state belongs here; never the network callback. */
    internal fun <T> withCurrent(operation: Operation, grant: () -> T): T = synchronized(stateLock) {
        requireCurrentLocked(operation)
        grant()
    }

    internal fun requireCurrentLocked(operation: Operation) {
        check(Thread.holdsLock(stateLock))
        require(operation.lane === this)
        check(isLatestLocked(operation) && !operation.consumed) { "Endpoint mutation was superseded or consumed" }
    }

    /** Only pure authority state checks belong here. Physical commit must follow after this returns. */
    internal fun grantFinal(operation: Operation, authorization: () -> Unit) {
        synchronized(stateLock) {
            requireCurrentLocked(operation)
            check(operation.entered) { "Endpoint mutation does not own its lane" }
            operation.consumed = true
            authorization()
        }
    }

    private fun isLatestLocked(operation: Operation): Boolean = open && if (operation.catalogReplacement) {
        latestCatalog === operation && sequence == operation.sequence
    } else {
        operation.sequence > (latestCatalog?.sequence ?: 0) && operation.chainIds.all { latest[it] === operation }
    }

    private companion object {
        const val MAX_CHAINS = 4096
    }
}
