package jp.co.soramitsu.coredb.recovery.decision

import android.os.CancellationSignal
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import jp.co.soramitsu.coredb.recovery.EndpointInventorySource
import jp.co.soramitsu.coredb.recovery.EndpointRecordRef

/**
 * Atomic inactive attachment after an explicit bounded probe. Offline decisions remain PENDING.
 * This store never selects a node, edits an archive, writes preferences, or performs network IO.
 * Synchronous calls belong on IO workers; no lifecycle monitor may surround transaction waits.
 * Cancellation bounds reads, not SQLite transaction acquisition or physical commit latency.
 */
class EndpointAttachmentStore(private val database: SupportSQLiteDatabase) {
    private val decisions = EndpointRecoveryDecisionStore(database)

    /** Capture one exact saved intent, live source, present trusted target and key occupancy. */
    fun prepare(
        signal: CancellationSignal,
        ref: EndpointRecordRef,
        decisionId: Long,
        decisionRevision: Long,
        action: EndpointAttachmentAction
    ): EndpointAttachmentCapture = readTransaction(signal) {
        capture(signal, ref, decisionId, decisionRevision, action)
    }

    /**
     * Revalidate an already-present pair without a probe or any DML. The consumed observation grant
     * cannot authorize later requests/commits; the adapter still fences main-thread publication.
     */
    internal fun observePresent(
        signal: CancellationSignal,
        prepared: EndpointAttachmentCapture,
        authority: EndpointAttachmentAuthority,
        attempt: EndpointAttachmentAuthority.Attempt
    ): EndpointAttachmentResult {
        check(prepared.mutation == null) { "Ordered attachment requires its shared writer lane" }
        return observePresentWithGrant(signal, prepared, authority, attempt) {
            authority.grantObservationOnce(attempt, prepared)
        }
    }

    internal fun observePresentOrdered(
        signal: CancellationSignal,
        prepared: EndpointAttachmentCapture,
        authority: EndpointAttachmentAuthority,
        attempt: EndpointAttachmentAuthority.Attempt,
        grant: () -> Unit
    ): EndpointAttachmentResult = observePresentWithGrant(signal, prepared, authority, attempt, grant)

    private fun observePresentWithGrant(
        signal: CancellationSignal,
        prepared: EndpointAttachmentCapture,
        authority: EndpointAttachmentAuthority,
        attempt: EndpointAttachmentAuthority.Attempt,
        grant: () -> Unit
    ): EndpointAttachmentResult {
        authority.requireBinding(attempt, prepared)
        try {
            return readTransaction(signal) {
                check(prepared.alreadyPresent) { "Endpoint attachment is not already present" }
                val current = recapture(signal, prepared)
                val result = presentResult(current, matched = false)
                signal.throwIfCanceled()
                grant()
                result
            }
        } finally {
            authority.abandon(attempt)
        }
    }

    /**
     * Require this invocation's actual probe MATCH and exact recapture, then create only inactive
     * ownership/projection or explicitly reattach the same detached OWNED row. Tombstones reject.
     * A final grant precedes transaction end; revocation after it cannot undo an authorized commit.
     * Reattachment advances the owner revision and makes the old source/decision capture stale.
     */
    internal fun commitMatched(
        signal: CancellationSignal,
        prepared: EndpointAttachmentCapture,
        authority: EndpointAttachmentAuthority,
        attempt: EndpointAttachmentAuthority.Attempt
    ): EndpointAttachmentResult {
        check(prepared.mutation == null) { "Ordered attachment requires its shared writer lane" }
        return commitMatchedWithGrant(signal, prepared, authority, attempt) {
            authority.commitOnce(attempt, prepared) {}
        }
    }

    internal fun commitMatchedOrdered(
        signal: CancellationSignal,
        prepared: EndpointAttachmentCapture,
        authority: EndpointAttachmentAuthority,
        attempt: EndpointAttachmentAuthority.Attempt,
        grant: () -> Unit
    ): EndpointAttachmentResult = commitMatchedWithGrant(signal, prepared, authority, attempt, grant)

    private fun commitMatchedWithGrant(
        signal: CancellationSignal,
        prepared: EndpointAttachmentCapture,
        authority: EndpointAttachmentAuthority,
        attempt: EndpointAttachmentAuthority.Attempt,
        grant: () -> Unit
    ): EndpointAttachmentResult {
        authority.requireBinding(attempt, prepared)
        var transactionOpen = false
        try {
            // Reject unmatched/retired invocations before transaction acquisition or any DML.
            // Revocation can still race this admission; the final grant then rolls SQL back.
            authority.requireMatched(attempt, prepared)
            signal.throwIfCanceled()
            check(!database.inTransaction()) { "Endpoint attachment cannot join an outer transaction" }
            database.beginTransactionNonExclusive()
            transactionOpen = true
            signal.throwIfCanceled()
            val current = recapture(signal, prepared)
            val decision = current.decision
            val plan = current.plan
            val changed = !current.alreadyPresent
            val result = if (!changed) {
                presentResult(current, matched = true)
            } else {
                val ownerId = when (plan.kind) {
                    EndpointAttachmentPolicy.Kind.INSERT_INACTIVE -> database.compileStatement(
                        "INSERT OR ABORT INTO main.custom_endpoint_records " +
                            "(revision,chainId,url,name,attached,deleted,originalSnapshotRowId) VALUES (1,?,?,?,1,0,NULL)"
                    ).use { statement ->
                        SimpleSQLiteQuery.bind(statement, arrayOf<Any?>(
                            decision.target.chainId, decision.candidate.url, decision.candidate.name
                        ))
                        statement.executeInsert().also { check(it > 0) { "Endpoint ownership insert failed" } }
                    }
                    EndpointAttachmentPolicy.Kind.REATTACH_INACTIVE -> {
                        val owner = checkNotNull(current.owner)
                        database.compileStatement(
                            "UPDATE OR ABORT main.custom_endpoint_records SET revision=?,attached=1 " +
                                "WHERE id=? AND revision=? AND attached=0 AND deleted=0"
                        ).use { statement ->
                            SimpleSQLiteQuery.bind(statement, arrayOf<Any?>(plan.nextRevision, owner.id, owner.revision))
                            check(statement.executeUpdateDelete() == 1) { "Endpoint ownership changed" }
                        }
                        owner.id
                    }
                    else -> error("Endpoint attachment plan differs")
                }
                signal.throwIfCanceled()
                database.compileStatement(
                    "INSERT OR ABORT INTO main.chain_nodes (chainId,url,name,isActive,isDefault) VALUES (?,?,?,0,0)"
                ).use { statement ->
                    SimpleSQLiteQuery.bind(statement, arrayOf<Any?>(
                        decision.target.chainId, decision.candidate.url, decision.candidate.name
                    ))
                    statement.executeInsert()
                }
                // Re-read only bounded scalar metadata. No trigger/namespace can bypass admission.
                val written = EndpointAttachmentSql.occupancy(database, signal, decision)
                val revision = checkNotNull(plan.nextRevision)
                check(written.owner == EndpointAttachmentPolicy.Owner(ownerId, revision, 1, 0, true) &&
                    written.originRowId == current.ownerOriginRowId &&
                    written.projection == EndpointAttachmentPolicy.Projection(0, 0, true)) {
                    "Endpoint attachment write differs"
                }
                EndpointAttachmentResult(
                    if (plan.kind == EndpointAttachmentPolicy.Kind.INSERT_INACTIVE)
                        EndpointAttachmentResult.Kind.ADDED_INACTIVE else EndpointAttachmentResult.Kind.REATTACHED_INACTIVE,
                    ownerId, revision, false, true, plan.kind == EndpointAttachmentPolicy.Kind.REATTACH_INACTIVE
                )
            }
            signal.throwIfCanceled()
            grant()
            if (changed) database.setTransactionSuccessful()
            transactionOpen = false
            database.endTransaction()
            return result
        } finally {
            try {
                authority.abandon(attempt)
            } finally {
                if (transactionOpen) database.endTransaction()
            }
        }
    }

    private fun recapture(signal: CancellationSignal, prepared: EndpointAttachmentCapture): EndpointAttachmentCapture {
        val expected = prepared.decision
        val current = capture(signal, expected.source.ref, expected.id, expected.revision, prepared.action)
        check(sameDecision(current.decision, expected) && current.owner == prepared.owner &&
            current.ownerOriginRowId == prepared.ownerOriginRowId && current.projection == prepared.projection &&
            current.plan == prepared.plan) { "Endpoint attachment inputs changed" }
        return current
    }

    private fun capture(
        signal: CancellationSignal,
        ref: EndpointRecordRef,
        decisionId: Long,
        decisionRevision: Long,
        action: EndpointAttachmentAction
    ): EndpointAttachmentCapture {
        require(decisionId > 0 && decisionRevision > 0)
        EndpointAttachmentSql.requireSchema(database, signal)
        val saved = checkNotNull(decisions.readInTransaction(signal, ref.source, ref.rowId)) {
            "Endpoint saved decision is absent"
        }
        check(saved.id == decisionId && saved.revision == decisionRevision && saved.source.ref == ref) {
            "Endpoint saved decision is stale"
        }
        val source = EndpointDecisionFingerprint.prepare(database, signal, ref)
        check(source.fingerprint == saved.source.fingerprint) { "Endpoint saved source is stale" }
        val target = checkNotNull(EndpointDecisionTargetReader.read(database, signal, saved.target.chainId)) {
            "Endpoint trusted target is absent"
        }
        check(sameTarget(target, saved.target) && target.family != EndpointDecisionTarget.Family.UNSUPPORTED) {
            "Endpoint trusted target differs or is unsupported"
        }
        val occupancy = EndpointAttachmentSql.occupancy(database, signal, saved)
        val owned = ref.source == EndpointInventorySource.OWNED
        val plan = EndpointAttachmentPolicy.plan(
            if (action == EndpointAttachmentAction.ADD_NEW) EndpointAttachmentPolicy.Action.ADD_NEW
            else EndpointAttachmentPolicy.Action.REATTACH_EXACT_OWNED,
            occupancy.owner, occupancy.projection, if (owned) ref.rowId else null, if (owned) ref.revision else null
        )
        signal.throwIfCanceled()
        return EndpointAttachmentCapture(saved, action, occupancy.owner, occupancy.originRowId, occupancy.projection, plan)
    }

    private fun presentResult(capture: EndpointAttachmentCapture, matched: Boolean): EndpointAttachmentResult {
        check(capture.alreadyPresent)
        val owner = checkNotNull(capture.owner)
        return EndpointAttachmentResult(
            EndpointAttachmentResult.Kind.ALREADY_PRESENT, owner.id, owner.revision,
            capture.plan.kind == EndpointAttachmentPolicy.Kind.ALREADY_ACTIVE, matched, false
        )
    }

    private fun sameTarget(a: EndpointDecisionTarget, b: EndpointDecisionTarget): Boolean =
        a.chainId == b.chainId && a.identityFingerprint == b.identityFingerprint &&
            a.family == b.family && a.expectedGenesis == b.expectedGenesis

    private fun sameDecision(a: EndpointRecoveryDecision, b: EndpointRecoveryDecision): Boolean =
        a.id == b.id && a.revision == b.revision && a.source.ref == b.source.ref &&
            a.source.fingerprint == b.source.fingerprint && a.candidate.name == b.candidate.name &&
            a.candidate.url == b.candidate.url && sameTarget(a.target, b.target)

    private inline fun <T> readTransaction(signal: CancellationSignal, read: () -> T): T {
        signal.throwIfCanceled()
        check(!database.inTransaction()) { "Endpoint attachment read cannot join an outer transaction" }
        database.beginTransactionNonExclusive()
        try {
            signal.throwIfCanceled()
            return read()
        } finally {
            database.endTransaction()
        }
    }
}
