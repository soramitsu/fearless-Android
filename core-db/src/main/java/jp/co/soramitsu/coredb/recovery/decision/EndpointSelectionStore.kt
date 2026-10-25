package jp.co.soramitsu.coredb.recovery.decision

import android.os.CancellationSignal
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import jp.co.soramitsu.common.data.network.endpoint.EndpointProbeFamily
import jp.co.soramitsu.common.data.network.endpoint.EndpointProbeRequest
import jp.co.soramitsu.coredb.recovery.selection.EndpointMutationLane

/**
 * Explicit durable selection of an already attached exact owned endpoint. Calls belong on IO.
 * Preparation and commit separately own the mutation lane; no monitor covers SQL waits or commit.
 * No probe, attachment, owner/decision revision, preference or connectivity state is changed here.
 */
class EndpointSelectionStore(private val database: SupportSQLiteDatabase, private val lane: EndpointMutationLane) {
    private val decisions = EndpointRecoveryDecisionStore(database)

    /** Read-only eligibility. The observed owner is rendered before a later explicit Select action. */
    suspend fun observeAttached(
        signal: CancellationSignal,
        decision: EndpointRecoveryDecision
    ): EndpointMutationLane.RuntimeRead<EndpointSelectionRequest?> = lane.readRuntime(decision.target.chainId) {
        readTransaction(signal) {
            EndpointAttachmentSql.requireSchema(database, signal)
            val saved = checkNotNull(decisions.readInTransaction(signal, decision.source.ref.source, decision.source.ref.rowId))
            check(sameDecision(decision, saved)) { "Endpoint saved decision changed" }
            val source = EndpointDecisionFingerprint.prepare(database, signal, decision.source.ref)
            check(source.fingerprint == decision.source.fingerprint) { "Endpoint saved source is stale" }
            val target = checkNotNull(EndpointDecisionTargetReader.read(database, signal, decision.target.chainId))
            check(sameTarget(target, decision.target)) { "Endpoint trusted target changed" }
            val family = when (target.family) {
                EndpointDecisionTarget.Family.SUBSTRATE -> EndpointProbeFamily.SUBSTRATE
                EndpointDecisionTarget.Family.SOLANA -> EndpointProbeFamily.SOLANA
                EndpointDecisionTarget.Family.UNSUPPORTED -> return@readTransaction null
            }
            EndpointProbeRequest(family, decision.candidate.url, checkNotNull(target.expectedGenesis))
            val occupancy = EndpointAttachmentSql.occupancy(database, signal, saved)
            val owner = occupancy.owner ?: return@readTransaction null
            val projection = occupancy.projection ?: return@readTransaction null
            if (owner.attached != 1L || owner.deleted != 0L || !owner.payloadMatches ||
                projection.isDefault != 0L || projection.isActive !in 0L..1L || !projection.payloadMatches
            ) {
                return@readTransaction null
            }
            val rowId = EndpointSelectionSql.targetRowId(database, signal, saved)
            val selected = EndpointSelectionSql.selected(database, signal, target.chainId)
            check((projection.isActive == 1L) == (selected?.rowId == rowId))
            signal.throwIfCanceled()
            EndpointSelectionRequest(decision, EndpointSelectionOwner(owner.id, owner.revision, occupancy.originRowId))
        }
    }

    suspend fun prepare(
        signal: CancellationSignal,
        request: EndpointSelectionRequest,
        operation: EndpointMutationLane.Operation
    ): EndpointSelectionCapture = lane.withOperation(operation) {
        lane.requireChain(operation, request.decision.target.chainId)
        readTransaction(signal) { capture(signal, request, operation) }
    }

    /** A consumed final grant may physically finish after revocation. Only successful transaction end acknowledges it. */
    suspend fun commit(
        signal: CancellationSignal,
        prepared: EndpointSelectionCapture,
        authority: EndpointSelectionAuthority,
        attempt: EndpointSelectionAuthority.Attempt
    ): EndpointSelectionResult {
        authority.requireCurrent(attempt, prepared)
        try {
            return lane.withOperation(prepared.operation) {
                authority.requireCurrent(attempt, prepared)
                signal.throwIfCanceled()
                check(!database.inTransaction()) { "Endpoint selection cannot join an outer transaction" }
                database.beginTransactionNonExclusive()
                var transactionOpen = true
                try {
                    signal.throwIfCanceled()
                    val current = capture(signal, prepared.request, prepared.operation)
                    check(current.targetRowId == prepared.targetRowId && current.selected == prepared.selected) {
                        "Endpoint selected row changed"
                    }
                    val decision = current.request.decision
                    if (!current.alreadySelected) {
                        val cleared = database.compileStatement(
                            "UPDATE OR ABORT main.chain_nodes SET isActive=0 WHERE chainId=? AND isActive=1"
                        ).use {
                            SimpleSQLiteQuery.bind(it, arrayOf<Any?>(decision.target.chainId))
                            it.executeUpdateDelete()
                        }
                        check(cleared == if (current.selected == null) 0 else 1)
                        signal.throwIfCanceled()
                        database.compileStatement(
                            "UPDATE OR ABORT main.chain_nodes SET isActive=1 WHERE rowid=? AND chainId=? AND url=? AND isActive=0"
                        ).use {
                            SimpleSQLiteQuery.bind(it, arrayOf<Any?>(current.targetRowId, decision.target.chainId, decision.candidate.url))
                            check(it.executeUpdateDelete() == 1) { "Endpoint selection write differs" }
                        }
                    }
                    val occupancy = EndpointAttachmentSql.occupancy(database, signal, decision)
                    requireOwner(current.request, occupancy)
                    check(occupancy.projection == EndpointAttachmentPolicy.Projection(0, 1, true))
                    check(EndpointSelectionSql.selected(database, signal, decision.target.chainId)?.rowId == current.targetRowId)
                    val result = EndpointSelectionResult(
                        if (current.alreadySelected) EndpointSelectionResult.Kind.ALREADY_SELECTED else EndpointSelectionResult.Kind.SELECTED,
                        prepared
                    )
                    signal.throwIfCanceled()
                    authority.commitOnce(attempt, prepared) {
                        if (!current.alreadySelected) database.setTransactionSuccessful()
                        transactionOpen = false
                        database.endTransaction()
                    }
                    result
                } finally {
                    if (transactionOpen) database.endTransaction()
                }
            }
        } finally {
            authority.abandon(attempt)
        }
    }

    private fun capture(
        signal: CancellationSignal,
        request: EndpointSelectionRequest,
        operation: EndpointMutationLane.Operation
    ): EndpointSelectionCapture {
        lane.requireChain(operation, request.decision.target.chainId)
        EndpointAttachmentSql.requireSchema(database, signal)
        val expected = request.decision
        val saved = checkNotNull(decisions.readInTransaction(signal, expected.source.ref.source, expected.source.ref.rowId)) {
            "Endpoint saved decision is absent"
        }
        check(sameDecision(expected, saved)) { "Endpoint saved decision changed" }
        val source = EndpointDecisionFingerprint.prepare(database, signal, expected.source.ref)
        check(source.fingerprint == expected.source.fingerprint) { "Endpoint saved source is stale" }
        val target = checkNotNull(EndpointDecisionTargetReader.read(database, signal, expected.target.chainId)) {
            "Endpoint trusted target is absent"
        }
        check(sameTarget(target, expected.target)) { "Endpoint trusted target changed" }
        val family = when (target.family) {
            EndpointDecisionTarget.Family.SUBSTRATE -> EndpointProbeFamily.SUBSTRATE
            EndpointDecisionTarget.Family.SOLANA -> EndpointProbeFamily.SOLANA
            EndpointDecisionTarget.Family.UNSUPPORTED -> error("Endpoint selection family is unsupported")
        }
        // Reuse the existing pure, canonical transport admission. Construction performs no I/O.
        EndpointProbeRequest(family, expected.candidate.url, checkNotNull(target.expectedGenesis))
        val occupancy = EndpointAttachmentSql.occupancy(database, signal, saved)
        requireOwner(request, occupancy)
        val projection = checkNotNull(occupancy.projection) { "Endpoint selection projection is absent" }
        check(projection.isDefault == 0L && projection.isActive in 0L..1L && projection.payloadMatches) {
            "Endpoint selection projection differs"
        }
        val rowId = EndpointSelectionSql.targetRowId(database, signal, saved)
        val selected = EndpointSelectionSql.selected(database, signal, target.chainId)
        val projectionSelected = projection.isActive == 1L
        check(projectionSelected == (selected?.rowId == rowId))
        signal.throwIfCanceled()
        return EndpointSelectionCapture(request, operation, rowId, selected)
    }

    private fun requireOwner(request: EndpointSelectionRequest, occupancy: EndpointAttachmentSql.Occupancy) {
        val owner = request.owner
        check(
            occupancy.owner == EndpointAttachmentPolicy.Owner(owner.id, owner.revision, 1, 0, true) &&
            occupancy.originRowId == owner.originalSnapshotRowId
        ) { "Endpoint displayed ownership changed" }
    }

    private fun sameDecision(a: EndpointRecoveryDecision, b: EndpointRecoveryDecision): Boolean =
        a.id == b.id && a.revision == b.revision && a.source.ref == b.source.ref &&
            a.source.fingerprint == b.source.fingerprint && a.candidate.name == b.candidate.name &&
            a.candidate.url == b.candidate.url && sameTarget(a.target, b.target)

    private fun sameTarget(a: EndpointDecisionTarget, b: EndpointDecisionTarget): Boolean =
        a.chainId == b.chainId && a.identityFingerprint == b.identityFingerprint &&
            a.family == b.family && a.expectedGenesis == b.expectedGenesis

    private inline fun <T> readTransaction(signal: CancellationSignal, read: () -> T): T {
        signal.throwIfCanceled()
        check(!database.inTransaction()) { "Endpoint selection read cannot join an outer transaction" }
        database.beginTransactionNonExclusive()
        try {
            signal.throwIfCanceled()
            return read()
        } finally {
            database.endTransaction()
        }
    }
}
