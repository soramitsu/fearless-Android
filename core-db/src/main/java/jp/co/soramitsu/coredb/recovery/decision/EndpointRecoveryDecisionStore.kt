package jp.co.soramitsu.coredb.recovery.decision

import android.database.Cursor
import android.os.CancellationSignal
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import jp.co.soramitsu.coredb.recovery.EndpointInventorySource
import jp.co.soramitsu.coredb.recovery.EndpointRecordRef
import jp.co.soramitsu.coredb.recovery.endpointInteger

/**
 * Bounded offline intent persistence. This store never edits an endpoint source,
 * owned record, operational node, account, selection or preference.
 *
 * All calls are synchronous IO work with a caller-owned cancellation signal.
 * Queries observe cancellation; transaction acquisition and physical commit are
 * Android blocking calls and are not promised a hard three-second upper bound.
 * Callers must not hold a lifecycle monitor or invoke this on the main thread.
 */
class EndpointRecoveryDecisionStore(private val database: SupportSQLiteDatabase) {
    /** Bind exact raw typed cells and provenance in one SQLite snapshot. */
    fun prepareSource(signal: CancellationSignal, ref: EndpointRecordRef): EndpointDecisionSourceBinding =
        readTransaction(signal) { EndpointDecisionFingerprint.prepare(database, signal, ref) }

    /** An absent local catalog row cannot fabricate a target choice. */
    fun prepareTarget(signal: CancellationSignal, chainId: String): EndpointDecisionTarget? =
        readTransaction(signal) { EndpointDecisionTargetReader.read(database, signal, chainId) }

    /** Read only bounded persisted intent; source obsolescence never upgrades its PENDING state. */
    fun read(signal: CancellationSignal, source: EndpointInventorySource, rowId: Long): EndpointRecoveryDecision? =
        readTransaction(signal) {
            requireDecisionSchema(database, signal)
            readInTransaction(signal, source, rowId)
        }

    /**
     * Revalidate both captures and the decision revision, then save one PENDING row.
     *
     * Null [expectedDecisionRevision] requires absence; updates require an exact
     * positive revision and reject overflow. A deleted target parent preserves
     * the captured choice as pending. A present changed parent rejects the save.
     * The final atomic grant precedes only marking/ending this transaction.
     * Revocation after that grant may precede physical commit, but cannot publish
     * the returned value or authorize any subsequent action.
     */
    fun saveOffline(
        signal: CancellationSignal,
        source: EndpointDecisionSourceBinding,
        candidate: EndpointDecisionCandidate,
        target: EndpointDecisionTarget,
        expectedDecisionRevision: Long?,
        authority: EndpointDecisionAuthority
    ): EndpointRecoveryDecision {
        val attempt = authority.newAttempt()
        var transactionOpen = false
        try {
            require(expectedDecisionRevision == null || expectedDecisionRevision > 0)
            signal.throwIfCanceled()
            check(!database.inTransaction()) { "Endpoint decision save cannot join an outer transaction" }
            database.beginTransactionNonExclusive()
            transactionOpen = true
            signal.throwIfCanceled()
            requireDecisionSchema(database, signal)
            val observedSource = EndpointDecisionFingerprint.prepare(database, signal, source.ref)
            check(observedSource.fingerprint == source.fingerprint) { "Endpoint decision source changed" }
            val observedTarget = EndpointDecisionTargetReader.read(database, signal, target.chainId)
            check(observedTarget == null || sameTarget(observedTarget, target)) { "Endpoint decision target changed" }
            val existing = readInTransaction(signal, source.ref.source, source.ref.rowId)
            check(existing?.revision == expectedDecisionRevision) { "Endpoint decision revision changed" }
            val revision = existing?.let { EndpointDecisionLimits.nextRevision(it.revision) } ?: 1L
            signal.throwIfCanceled()
            val values = arrayOf<Any?>(
                revision, source.ref.source.name, source.ref.rowId, source.ref.revision, source.fingerprint,
                candidate.name, candidate.url, target.chainId, target.identityFingerprint,
                target.family.name, target.expectedGenesis, "PENDING"
            )
            val id = if (existing == null) {
                database.compileStatement(
                    "INSERT OR ABORT INTO main.endpoint_recovery_decisions " +
                        "(revision,sourceKind,sourceRowId,sourceRevision,sourceFingerprint,candidateName,candidateUrl," +
                        "targetChainId,targetIdentityFingerprint,targetFamily,targetExpectedGenesis,state) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)"
                ).use { statement ->
                    SimpleSQLiteQuery.bind(statement, values)
                    statement.executeInsert().also { check(it > 0) { "Endpoint decision insert failed" } }
                }
            } else {
                database.compileStatement(
                    "UPDATE OR ABORT main.endpoint_recovery_decisions SET revision=?,sourceKind=?,sourceRowId=?," +
                        "sourceRevision=?,sourceFingerprint=?,candidateName=?,candidateUrl=?,targetChainId=?," +
                        "targetIdentityFingerprint=?,targetFamily=?,targetExpectedGenesis=?,state=? WHERE id=? AND revision=?"
                ).use { statement ->
                    SimpleSQLiteQuery.bind(statement, arrayOf<Any?>(*values, existing.id, existing.revision))
                    check(statement.executeUpdateDelete() == 1) { "Endpoint decision update conflict" }
                }
                existing.id
            }
            // Construct and validate the bounded result before granting the commit.
            val result = EndpointRecoveryDecision(id, revision, source, candidate, target)
            signal.throwIfCanceled()
            authority.commitOnce(attempt) {
                database.setTransactionSuccessful()
                transactionOpen = false
                database.endTransaction()
            }
            return result
        } finally {
            authority.abandon(attempt)
            if (transactionOpen) database.endTransaction()
        }
    }

    private fun sameTarget(first: EndpointDecisionTarget, second: EndpointDecisionTarget): Boolean =
        first.chainId == second.chainId && first.identityFingerprint == second.identityFingerprint &&
            first.family == second.family && first.expectedGenesis == second.expectedGenesis

    private inline fun <T> readTransaction(signal: CancellationSignal, read: () -> T): T {
        signal.throwIfCanceled()
        check(!database.inTransaction()) { "Endpoint decision read cannot join an outer transaction" }
        database.beginTransactionNonExclusive()
        try {
            signal.throwIfCanceled()
            return read().also { signal.throwIfCanceled() }
        } finally {
            // No success mark: these calls contain only reads.
            database.endTransaction()
        }
    }

    internal fun readInTransaction(
        signal: CancellationSignal,
        source: EndpointInventorySource,
        rowId: Long
    ): EndpointRecoveryDecision? {
        val textColumns = listOf(
            "sourceKind" to 32, "sourceFingerprint" to 64, "candidateName" to EndpointDecisionLimits.LABEL_BYTES,
            "candidateUrl" to EndpointDecisionLimits.URL_BYTES, "targetChainId" to EndpointDecisionLimits.TARGET_ID_BYTES,
            "targetIdentityFingerprint" to 64, "targetFamily" to 32, "targetExpectedGenesis" to 128, "state" to 16
        )
        val textProjection = textColumns.joinToString { (name, limit) -> decisionTextProjection(name, limit) }
        return database.query(SimpleSQLiteQuery(
            "SELECT CASE WHEN typeof(id)='integer' THEN id END," +
                "CASE WHEN typeof(revision)='integer' THEN revision END," +
                "CASE WHEN typeof(sourceRowId)='integer' THEN sourceRowId END," +
                "CASE WHEN typeof(sourceRevision)='integer' THEN sourceRevision END," +
                "typeof(targetExpectedGenesis),$textProjection,typeof(sourceRevision) IN ('null','integer') " +
                "FROM main.endpoint_recovery_decisions WHERE sourceKind=? AND sourceRowId=? LIMIT 2",
            arrayOf<Any?>(source.name, rowId)
        ), signal).use { cursor ->
            signal.throwIfCanceled()
            if (!cursor.moveToFirst()) return@use null
            check(cursor.columnCount == 15 && cursor.endpointInteger(14) == 1L)
            val id = cursor.endpointInteger(0)
            val revision = cursor.endpointInteger(1)
            check(cursor.endpointInteger(2) == rowId)
            val sourceRevision = if (cursor.isNull(3)) null else cursor.endpointInteger(3)
            check(cursor.decisionText(5, 32) == source.name)
            val sourceBinding = EndpointDecisionSourceBinding(
                EndpointRecordRef(source, rowId, sourceRevision), cursor.decisionText(6, 64)
            )
            val candidate = EndpointDecisionCandidate(
                cursor.decisionText(7, EndpointDecisionLimits.LABEL_BYTES),
                cursor.decisionText(8, EndpointDecisionLimits.URL_BYTES)
            )
            val targetChainId = cursor.decisionText(9, EndpointDecisionLimits.TARGET_ID_BYTES)
            val targetFingerprint = cursor.decisionText(10, 64)
            val familyText = cursor.decisionText(11, 32)
            val family = EndpointDecisionTarget.Family.entries.singleOrNull { it.name == familyText }
                ?: error("Endpoint decision target family is invalid")
            check(cursor.getType(4) == Cursor.FIELD_TYPE_STRING)
            val genesis = when (cursor.getString(4)) {
                "null" -> { check(cursor.isNull(12)); null }
                "text" -> cursor.decisionText(12, 128)
                else -> error("Endpoint decision genesis storage is invalid")
            }
            check(cursor.decisionText(13, 16) == "PENDING") { "Endpoint decision state is unsupported" }
            check(!cursor.moveToNext()) { "Endpoint decision source key is ambiguous" }
            signal.throwIfCanceled()
            EndpointRecoveryDecision(id, revision, sourceBinding, candidate,
                EndpointDecisionTarget(targetChainId, targetFingerprint, family, genesis))
        }
    }
}
