package jp.co.soramitsu.coredb.recovery.decision

import android.os.CancellationSignal
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteStatement
import jp.co.soramitsu.coredb.migrations.EndpointArchiveFormat
import jp.co.soramitsu.coredb.migrations.EndpointRecoveryDecisionMigration
import jp.co.soramitsu.coredb.recovery.EndpointInventorySource
import jp.co.soramitsu.coredb.recovery.EndpointRecordRef
import jp.co.soramitsu.coredb.recovery.OWNED_ENDPOINT_CREATE
import jp.co.soramitsu.coredb.recovery.OWNED_ENDPOINT_INDEX_CREATE
import jp.co.soramitsu.coredb.recovery.selection.EndpointMutationLane
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Shared real-SQL contract. Concrete host/native adapters supply an empty disposable database. */
abstract class EndpointSelectionStoreContract {
    interface Database : AutoCloseable {
        val db: SupportSQLiteDatabase
        fun reopen()
    }
    protected abstract fun openDatabase(): Database

    @Test fun selectsExactProjectionAndAcknowledgesAfterTransactionEnd() = runBlocking<Unit> {
        fixture { f ->
            val before = image(f.db)
            val capture = f.prepare()
            assertEquals(before, image(f.db))
            val result = f.commit(capture)
            assertEquals(EndpointSelectionResult.Kind.SELECTED, result.kind)
            assertEquals(f.request.decision, result.decision)
            assertEquals(f.request.owner, result.owner)
            assertTrue(f.lane.isLatest(result.command))
            assertFalse(f.lane.isCurrent(result.command))
            assertFalse(f.db.inTransaction())
            assertEquals(listOf("-9/0", "-8/1", "11/1"), flags(f.db))
            assertEquals(before.filterKeys { it != "chain_nodes" }, image(f.db).filterKeys { it != "chain_nodes" })
            assertNodesEqualApartFromActive(before.getValue("chain_nodes"), image(f.db).getValue("chain_nodes"))
        }
    }

    @Test fun alreadySelectedConsumesReadOnlyGrantWithoutDmlOrSuccessMark() = runBlocking<Unit> {
        fixture { f ->
            f.commit(f.prepare())
            val before = image(f.db)
            val noWrites = object : SupportSQLiteDatabase by f.db {
                override fun compileStatement(sql: String): SupportSQLiteStatement =
                    error("Idempotent path attempted SQL write")
                override fun setTransactionSuccessful(): Unit = error("Idempotent path marked a write transaction")
            }
            val store = EndpointSelectionStore(noWrites, f.lane)
            val capture = f.prepare(store)
            val result = f.commit(capture, store)
            assertEquals(EndpointSelectionResult.Kind.ALREADY_SELECTED, result.kind)
            assertEquals(before, image(f.db))
        }
    }

    @Test fun missingPreviousSelectionKeepsDefaultsAndOtherChain() = runBlocking<Unit> {
        fixture { f ->
            f.db.execSQL("UPDATE chain_nodes SET isActive=0 WHERE rowid=-9")
            f.commit(f.prepare())
            assertEquals(listOf("-9/0", "-8/1", "11/1"), flags(f.db))
            assertEquals(1L, scalar(f.db, "SELECT isDefault FROM chain_nodes WHERE rowid=-9"))
        }
    }

    @Test fun currentArchiveRemainsValidAfterLiveSelectionChanges() = runBlocking<Unit> {
        fixture(EndpointInventorySource.CURRENT) { f ->
            val before = image(f.db)
            f.commit(f.prepare())
            assertEquals(EndpointSelectionResult.Kind.ALREADY_SELECTED, f.commit(f.prepare()).kind)
            val after = image(f.db)
            assertEquals(before.filterKeys { it != "chain_nodes" }, after.filterKeys { it != "chain_nodes" })
        }
    }

    @Test fun firstDmlRevokeCancelAndReplacementRollBackEveryCell() = runBlocking<Unit> {
        for (mode in listOf("revoke", "cancel", "replace", "abandon", "sql")) fixture { f ->
            val signal = CancellationSignal()
            val authority = EndpointSelectionAuthority(f.lane)
            var attempt: EndpointSelectionAuthority.Attempt? = null
            var writes = 0
            val wrapper = wrapped(f.db, afterWrite = {
                if (++writes == 1) {
                    when (mode) {
                    "revoke" -> authority.revoke()
                    "cancel" -> signal.cancel()
                    "replace" -> f.lane.admit(listOf(CHAIN))
                    "abandon" -> authority.abandon(checkNotNull(attempt))
                    else -> error("Injected failure after real first DML")
                }
                }
            })
            val store = EndpointSelectionStore(wrapper, f.lane)
            val capture = f.prepare(store)
            attempt = authority.newAttempt(capture)
            val before = image(f.db)
            rejected { store.commit(signal, capture, authority, checkNotNull(attempt)) }
            assertTrue(writes >= 1)
            assertEquals(before, image(f.db))
            assertFalse(f.db.inTransaction())
            rejected { store.commit(CancellationSignal(), capture, authority, checkNotNull(attempt)) }
            assertEquals(before, image(f.db))
        }
    }

    @Test fun secondWriteFailureRollsBackRealFirstWrite() = runBlocking<Unit> {
        fixture { f ->
            var writes = 0
            val wrapper = wrapped(f.db, beforeWrite = { if (++writes == 2) error("Injected second SQL failure") })
            val store = EndpointSelectionStore(wrapper, f.lane)
            val before = image(f.db)
            rejected { f.commit(f.prepare(store), store) }
            assertEquals(2, writes)
            assertEquals(before, image(f.db))
            assertFalse(f.db.inTransaction())
            assertEquals(EndpointSelectionResult.Kind.SELECTED, f.commit(f.prepare()).kind)
        }
    }

    @Test fun postGrantPhysicalFailureNeverAcknowledgesOrReplays() = runBlocking<Unit> {
        fixture { f ->
            val capture = f.prepare()
            val authority = EndpointSelectionAuthority(f.lane)
            val attempt = authority.newAttempt(capture)
            val wrapper = object : SupportSQLiteDatabase by f.db {
                override fun endTransaction() {
                    f.db.endTransaction()
                error("Injected completion delivery failure")
                }
            }
            rejected {
                EndpointSelectionStore(wrapper, f.lane).commit(CancellationSignal(), capture, authority, checkNotNull(attempt))
            }
            assertFalse(f.db.inTransaction())
            assertEquals(listOf("-9/0", "-8/1", "11/1"), flags(f.db))
            rejected {
                EndpointSelectionStore(f.db, f.lane).commit(CancellationSignal(), capture, authority, checkNotNull(attempt))
            }
            assertEquals(EndpointSelectionResult.Kind.ALREADY_SELECTED, f.commit(f.prepare()).kind)
        }
    }

    @Test fun postGrantRetirementMayPrecedePhysicalCompletion() = runBlocking<Unit> {
        fixture { f ->
            val capture = f.prepare()
            val authority = EndpointSelectionAuthority(f.lane)
            val attempt = authority.newAttempt(capture)
            val wrapper = object : SupportSQLiteDatabase by f.db {
                override fun setTransactionSuccessful() {
                    assertFalse(Thread.holdsLock(f.lane.stateLock))
                    authority.revoke()
                    f.lane.admit(listOf(CHAIN))
                    f.db.setTransactionSuccessful()
                }
            }
            val result = EndpointSelectionStore(wrapper, f.lane).commit(CancellationSignal(), capture, authority, attempt)
            assertEquals(EndpointSelectionResult.Kind.SELECTED, result.kind)
            assertFalse(f.lane.isLatest(result.command))
            assertFalse(authority.isCurrent())
            assertEquals(listOf("-9/0", "-8/1", "11/1"), flags(f.db))
        }
    }

    @Test fun changedDecisionSourceTargetAndOwnerRejectHeldCapture() = runBlocking<Unit> {
        val changes = listOf(
            "UPDATE endpoint_recovery_decisions SET revision=revision+1",
            "UPDATE endpoint_recovery_decisions SET candidateName='Changed'",
            "UPDATE custom_endpoint_records SET revision=revision+1",
            "UPDATE custom_endpoint_records SET originalSnapshotRowId=19",
            "UPDATE custom_endpoint_records SET attached=0",
            "UPDATE custom_endpoint_records SET deleted=1",
            "UPDATE custom_endpoint_records SET name='Changed'",
            "UPDATE chains SET isTestNet=1 WHERE id='$CHAIN'",
            "UPDATE chain_nodes SET name='Changed' WHERE rowid=-8",
            "UPDATE chain_nodes SET rowid=17 WHERE rowid=-8",
            "UPDATE chain_nodes SET isDefault=1 WHERE rowid=-8",
            "DELETE FROM chain_nodes WHERE rowid=-8",
            "DELETE FROM custom_endpoint_records",
            "DELETE FROM endpoint_recovery_decisions"
        )
        for (change in changes) fixture { f ->
            val capture = f.prepare()
            f.db.execSQL(change)
            val before = image(f.db)
            rejected { f.commit(capture) }
            assertEquals(before, image(f.db))
        }
    }

    @Test fun changedLivePreviousRowRejectsExactCandidateAndOwner() = runBlocking<Unit> {
        for (change in listOf(
            "UPDATE chain_nodes SET name='Changed prior' WHERE rowid=-9",
            "UPDATE chain_nodes SET url='wss://changed.invalid/' WHERE rowid=-9",
            "UPDATE chain_nodes SET rowid=20 WHERE rowid=-9",
            "UPDATE chain_nodes SET isDefault=0 WHERE rowid=-9",
            "UPDATE chain_nodes SET isActive=0 WHERE rowid=-9"
        )) fixture { f ->
            val capture = f.prepare()
            f.db.execSQL(change)
            val before = image(f.db)
            rejected { f.commit(capture) }
            assertEquals(before, image(f.db))
        }
    }

    @Test fun unrelatedChainChangeDoesNotInvalidateExactSelection() = runBlocking<Unit> {
        fixture { f ->
            val capture = f.prepare()
            f.lane.admit(listOf(OTHER))
            f.db.execSQL("UPDATE chain_nodes SET name='Other changed',isActive=0 WHERE rowid=11")
            val before = image(f.db)
            assertEquals(EndpointSelectionResult.Kind.SELECTED, f.commit(capture).kind)
            val rows = image(f.db).getValue("chain_nodes")
            assertEquals(before.getValue("chain_nodes").last(), rows.last())
        }
    }

    @Test fun processOrderRejectsHeldCaptureEvenWhenRowsReturnToSameValues() = runBlocking<Unit> {
        fixture { f ->
            val capture = f.prepare()
            val before = image(f.db)
            f.lane.admit(listOf(CHAIN))
            f.db.execSQL("UPDATE chain_nodes SET name='Intermediate' WHERE rowid=-9")
            f.db.execSQL("UPDATE chain_nodes SET name='Original' WHERE rowid=-9")
            assertEquals(before, image(f.db))
            rejected { f.commit(capture) }
            assertEquals(before, image(f.db))
        }
    }

    @Test fun malformedFlagsAndMultipleSelectedRowsRejectWithoutMutation() = runBlocking<Unit> {
        for (sql in listOf(
            "UPDATE chain_nodes SET isActive=2 WHERE rowid=-9",
            "UPDATE chain_nodes SET isDefault=2 WHERE rowid=-9",
            "UPDATE chain_nodes SET isActive=X'31' WHERE rowid=-9",
            "UPDATE chain_nodes SET isDefault=X'31' WHERE rowid=-8",
            "UPDATE chain_nodes SET isActive=1 WHERE rowid=-8"
        )) fixture { f ->
            f.db.execSQL(sql)
            val before = image(f.db)
            rejected { f.prepare() }
            assertEquals(before, image(f.db))
        }
    }

    @Test fun displayedOwnerIdentityRevisionAndOriginAreExact() = runBlocking<Unit> {
        for (owner in listOf(EndpointSelectionOwner(1, 8, -7), EndpointSelectionOwner(-7, 9, -7), EndpointSelectionOwner(-7, 8, null))) {
            fixture { f ->
                val before = image(f.db)
                rejected {
                    EndpointSelectionStore(f.db, f.lane).prepare(CancellationSignal(), EndpointSelectionRequest(f.request.decision, owner), f.lane.admit(listOf(CHAIN)))
                }
                assertEquals(before, image(f.db))
            }
        }
    }

    @Test fun twoMegabytePreviousNameStreamsInBoundedChunks() = runBlocking<Unit> {
        fixture { f ->
            f.db.execSQL("UPDATE chain_nodes SET name=CAST(zeroblob(2097152) AS TEXT) WHERE rowid=-9")
            assertEquals(EndpointSelectionResult.Kind.SELECTED, f.commit(f.prepare()).kind)
            assertEquals(2097152L, scalar(f.db, "SELECT length(CAST(name AS BLOB)) FROM chain_nodes WHERE rowid=-9"))
        }
    }

    @Test fun previousPayloadOverBudgetAndNonTextStorageReject() = runBlocking<Unit> {
        for (expression in listOf("CAST(zeroblob(8388608) AS TEXT)", "X'4EFF00'")) fixture { f ->
            f.db.execSQL("UPDATE chain_nodes SET name=$expression WHERE rowid=-9")
            rejected { f.prepare() }
            assertEquals(listOf("-9/1", "-8/0", "11/1"), flags(f.db))
        }
    }

    @Test fun freshRequestMustStillUseCanonicalSupportedTransport() = runBlocking<Unit> {
        for (url in listOf("https://candidate.invalid/", "wss://candidate.invalid/#fragment", "wss://user@candidate.invalid/")) fixture(candidateUrl = url) { f ->
            val before = image(f.db)
            rejected { f.prepare() }
            assertEquals(before, image(f.db))
        }
    }

    @Test fun missingTargetAndUnsupportedTargetRejectWithoutMutation() = runBlocking<Unit> {
        for (mode in listOf("missing", "unsupported")) fixture { f ->
            if (mode == "missing") {
                f.db.execSQL("DELETE FROM chains WHERE id=?", arrayOf(CHAIN))
            } else {
                f.db.execSQL("UPDATE chains SET ecosystem='Unknown' WHERE id=?", arrayOf(CHAIN))
            }
            val before = image(f.db)
            rejected { f.prepare() }
            assertEquals(before, image(f.db))
        }
    }

    @Test fun outerTransactionAndCancelledPreparationReject() = runBlocking<Unit> {
        fixture { f ->
            val store = EndpointSelectionStore(f.db, f.lane)
            val before = image(f.db)
            val cancelled = CancellationSignal().also { it.cancel() }
            rejected { store.prepare(cancelled, f.request, f.lane.admit(listOf(CHAIN))) }
            f.db.beginTransaction()
            try {
                rejected { f.prepare(store) }
                assertTrue(f.db.inTransaction())
            } finally {
                f.db.endTransaction()
            }
            assertEquals(before, image(f.db))
        }
    }

    @Test fun schemaCollisionAndDisabledForeignKeysRemainRejected() = runBlocking<Unit> {
        for (change in listOf("CREATE TEMP TABLE chain_nodes(marker)", "CREATE TRIGGER unexpected AFTER UPDATE ON chain_nodes BEGIN SELECT 1; END", "PRAGMA foreign_keys=OFF")) fixture { f ->
            f.db.execSQL(change)
            val before = image(f.db)
            rejected { f.prepare() }
            assertEquals(before, image(f.db))
        }
    }

    @Test fun noCallbackFromStaleOperationCanWriteAfterNewerSelection() = runBlocking<Unit> {
        fixture { f ->
            val old = f.prepare()
            val newer = f.prepare()
            f.commit(newer)
            val before = image(f.db)
            rejected { f.commit(old) }
            assertEquals(before, image(f.db))
        }
    }

    @Test fun acknowledgedSelectionSurvivesReopenAndRetiresOldAuthority() = runBlocking<Unit> {
        fixture { f ->
            val capture = f.prepare()
            val authority = EndpointSelectionAuthority(f.lane)
            val attempt = authority.newAttempt(capture)
            val store = EndpointSelectionStore(f.db, f.lane)
            store.commit(CancellationSignal(), capture, authority, attempt)
            val before = image(f.db)
            f.lane.retire()
            f.opened.reopen()
            assertEquals(before, image(f.db))
            rejected { store.commit(CancellationSignal(), capture, authority, checkNotNull(attempt)) }
            val newLane = EndpointMutationLane()
            val restored = EndpointSelectionStore(f.db, newLane)
            val fresh = restored.prepare(CancellationSignal(), f.request, newLane.admit(listOf(CHAIN)))
            val freshAuthority = EndpointSelectionAuthority(newLane)
            assertEquals(
                EndpointSelectionResult.Kind.ALREADY_SELECTED,
                restored.commit(CancellationSignal(), fresh, freshAuthority, freshAuthority.newAttempt(fresh)).kind
            )
            assertEquals(before, image(f.db))
        }
    }

    @Test fun realPhysicalCommitOwnsLaneButNeverBlocksRevocationMonitor() = runBlocking<Unit> {
        fixture { f ->
            val capture = f.prepare()
            val authority = EndpointSelectionAuthority(f.lane)
            val attempt = authority.newAttempt(capture)
            val granted = CountDownLatch(1)
            val release = CountDownLatch(1)
            val observedNextTransaction = CountDownLatch(1)
            val wrapper = object : SupportSQLiteDatabase by f.db {
                override fun setTransactionSuccessful() {
                    assertFalse(Thread.holdsLock(f.lane.stateLock))
                    granted.countDown()
                    check(release.await(5, TimeUnit.SECONDS)) { "Commit release timed out" }
                    f.db.setTransactionSuccessful()
                }
            }
            val commit = async(Dispatchers.Default) {
                EndpointSelectionStore(wrapper, f.lane).commit(CancellationSignal(), capture, authority, attempt)
            }
            try {
                assertTrue(granted.await(5, TimeUnit.SECONDS))
                withTimeout(2000) { async(Dispatchers.Default) { authority.revoke() }.await() }
                val next = f.lane.admit(listOf(CHAIN))
                val observed = object : SupportSQLiteDatabase by f.db {
                    override fun beginTransactionNonExclusive() {
                        observedNextTransaction.countDown()
                        f.db.beginTransactionNonExclusive()
                    }
                }
                val preparation = async(Dispatchers.Default) {
                    EndpointSelectionStore(observed, f.lane).prepare(CancellationSignal(), f.request, next)
                }
                assertFalse(observedNextTransaction.await(100, TimeUnit.MILLISECONDS))
                release.countDown()
                assertEquals(EndpointSelectionResult.Kind.SELECTED, withTimeout(5000) { commit.await() }.kind)
                assertTrue(withTimeout(5000) { preparation.await() }.alreadySelected)
                assertTrue(observedNextTransaction.await(1, TimeUnit.SECONDS))
            } finally {
                release.countDown()
                withTimeout(5000) { commit.await() }
            }
        }
    }

    @Test fun attachedObservationReadsOwnerWithoutConsumingIntent() = runBlocking<Unit> {
        fixture { f ->
            val operation = f.lane.admit(listOf(CHAIN))
            val before = image(f.db)
            val noWrites = object : SupportSQLiteDatabase by f.db {
                override fun compileStatement(sql: String): SupportSQLiteStatement = error("Observation attempted DML")
                override fun setTransactionSuccessful(): Unit = error("Observation marked write transaction")
            }
            val observation = EndpointSelectionStore(noWrites, f.lane).observeAttached(CancellationSignal(), f.request.decision)
            assertEquals(f.request.owner, checkNotNull(observation.value).owner)
            assertTrue(observation.value!!.decision === f.request.decision)
            assertTrue(f.lane.isCurrent(observation.version))
            assertTrue(f.lane.isCurrent(operation))
            assertEquals(before, image(f.db))
        }
    }

    @Test fun attachedObservationAfterSelectionAndReopenKeepsExactOwner() = runBlocking<Unit> {
        for (source in listOf(EndpointInventorySource.OWNED, EndpointInventorySource.CURRENT)) fixture(source) { f ->
            f.commit(f.prepare())
            f.opened.reopen()
            val before = image(f.db)
            val observation = EndpointSelectionStore(f.db, f.lane).observeAttached(CancellationSignal(), f.request.decision)
            assertEquals(f.request.owner, checkNotNull(observation.value).owner)
            assertEquals(before, image(f.db))
        }
    }

    @Test fun attachedObservationRejectsMissingOrConflictingProjection() = runBlocking<Unit> {
        for (sql in listOf(
            "DELETE FROM chain_nodes WHERE rowid=-8",
            "UPDATE chain_nodes SET name='foreign projection' WHERE rowid=-8",
            "UPDATE chain_nodes SET isDefault=1 WHERE rowid=-8"
        )) fixture { f ->
            f.db.execSQL(sql)
            val before = image(f.db)
            val observation = EndpointSelectionStore(f.db, f.lane).observeAttached(CancellationSignal(), f.request.decision)
            assertEquals(null, observation.value)
            assertEquals(before, image(f.db))
        }
    }

    @Test fun currentArchiveObservationRequiresLiveAttachedOwner() = runBlocking<Unit> {
        for (sql in listOf(
            "UPDATE custom_endpoint_records SET attached=0 WHERE id=-7",
            "UPDATE custom_endpoint_records SET deleted=1 WHERE id=-7",
            "DELETE FROM custom_endpoint_records WHERE id=-7"
        )) fixture(EndpointInventorySource.CURRENT) { f ->
            f.db.execSQL(sql)
            val before = image(f.db)
            val observation = EndpointSelectionStore(f.db, f.lane).observeAttached(CancellationSignal(), f.request.decision)
            assertEquals(null, observation.value)
            assertEquals(before, image(f.db))
        }
    }

    @Test fun attachedObservationRejectsChangedSourceDecisionAndTarget() = runBlocking<Unit> {
        for (sql in listOf(
            "UPDATE custom_endpoint_records SET revision=9 WHERE id=-7",
            "UPDATE endpoint_recovery_decisions SET revision=revision+1",
            "UPDATE chains SET isTestNet=1 WHERE id='$CHAIN'"
        )) fixture { f ->
            f.db.execSQL(sql)
            val before = image(f.db)
            rejected { EndpointSelectionStore(f.db, f.lane).observeAttached(CancellationSignal(), f.request.decision) }
            assertEquals(before, image(f.db))
        }
    }

    @Test fun attachedObservationRejectsAmbiguousSelectionWithoutMutation() = runBlocking<Unit> {
        fixture { f ->
            f.db.execSQL("UPDATE chain_nodes SET isActive=1 WHERE rowid=-8")
            val before = image(f.db)
            rejected { EndpointSelectionStore(f.db, f.lane).observeAttached(CancellationSignal(), f.request.decision) }
            assertEquals(before, image(f.db))
        }
    }

    @Test fun attachedObservationRejectsOuterCanceledOrRetiredRead() = runBlocking<Unit> {
        fixture { f ->
            val store = EndpointSelectionStore(f.db, f.lane)
            val before = image(f.db)
            f.db.beginTransactionNonExclusive()
            try {
                rejected { store.observeAttached(CancellationSignal(), f.request.decision) }
            } finally {
                f.db.endTransaction()
            }
            rejected { store.observeAttached(CancellationSignal().apply { cancel() }, f.request.decision) }
            f.lane.retire()
            rejected { store.observeAttached(CancellationSignal(), f.request.decision) }
            assertEquals(before, image(f.db))
        }
    }

    @Test fun attachedObservationVersionTracksOverlappingIntents() = runBlocking<Unit> {
        fixture { f ->
            val store = EndpointSelectionStore(f.db, f.lane)
            val before = image(f.db)
            val first = store.observeAttached(CancellationSignal(), f.request.decision)
            f.lane.admit(listOf(OTHER))
            assertTrue(f.lane.isCurrent(first.version))
            val operation = f.lane.admit(listOf(CHAIN))
            assertFalse(f.lane.isCurrent(first.version))
            val second = store.observeAttached(CancellationSignal(), f.request.decision)
            f.lane.abandon(operation)
            assertFalse(f.lane.isCurrent(second.version))
            val third = store.observeAttached(CancellationSignal(), f.request.decision)
            f.lane.admitCatalogReplacement()
            assertFalse(f.lane.isCurrent(third.version))
            assertEquals(before, image(f.db))
        }
    }

    @Test fun selectionGrantRetiresPreparingObservation() = runBlocking<Unit> {
        fixture { f ->
            val capture = f.prepare()
            val observation = EndpointSelectionStore(f.db, f.lane).observeAttached(CancellationSignal(), f.request.decision)
            assertTrue(f.lane.isCurrent(observation.version))
            f.commit(capture)
            assertFalse(f.lane.isCurrent(observation.version))
        }
    }

    @Test fun attachedObservationRejectsCollisionAndHeldRead() = runBlocking<Unit> {
        fixture { f ->
            val wrapper = object : SupportSQLiteDatabase by f.db {
                override fun endTransaction() {
                    f.db.endTransaction()
                    f.lane.admit(listOf(CHAIN))
                }
            }
            val before = image(f.db)
            rejected { EndpointSelectionStore(wrapper, f.lane).observeAttached(CancellationSignal(), f.request.decision) }
            assertEquals(before, image(f.db))
            f.db.execSQL("CREATE TEMP TABLE chain_nodes(x INTEGER)")
            rejected { EndpointSelectionStore(f.db, f.lane).observeAttached(CancellationSignal(), f.request.decision) }
        }
    }

    private class Fixture(val opened: Database, val request: EndpointSelectionRequest) {
        val db: SupportSQLiteDatabase get() = opened.db
        val lane = EndpointMutationLane()
        suspend fun prepare(
            store: EndpointSelectionStore = EndpointSelectionStore(db, lane)
        ): EndpointSelectionCapture = store.prepare(CancellationSignal(), request, lane.admit(listOf(CHAIN)))
        suspend fun commit(
            capture: EndpointSelectionCapture,
            store: EndpointSelectionStore = EndpointSelectionStore(db, lane)
        ): EndpointSelectionResult {
            val authority = EndpointSelectionAuthority(lane)
            return store.commit(CancellationSignal(), capture, authority, authority.newAttempt(capture))
        }
    }

    private suspend fun fixture(
        source: EndpointInventorySource = EndpointInventorySource.OWNED,
        candidateUrl: String = "wss://candidate.invalid/Exact?token=synthetic",
        action: suspend (Fixture) -> Unit
    ) {
        openDatabase().use { opened ->
            val db = opened.db
            db.setForeignKeyConstraintsEnabled(true)
            db.execSQL("CREATE TABLE chains(id TEXT PRIMARY KEY NOT NULL,ecosystem TEXT,isEthereumBased INTEGER,isEthereumChain INTEGER,isTestNet INTEGER,history_type TEXT)")
            for (chain in listOf(CHAIN, OTHER)) db.execSQL("INSERT INTO chains VALUES(?,'Substrate',0,0,0,NULL)", arrayOf(chain))
            db.execSQL(
                "CREATE TABLE `chain_nodes` (`chainId` TEXT NOT NULL, `url` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                    "`isActive` INTEGER NOT NULL, `isDefault` INTEGER NOT NULL, PRIMARY KEY(`chainId`, `url`), " +
                    "FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL("CREATE INDEX `index_chain_nodes_chainId` ON `chain_nodes` (`chainId`)")
            db.execSQL("INSERT INTO chain_nodes(rowid,chainId,url,name,isActive,isDefault) VALUES(-9,?,'wss://original.invalid/','Original',1,1)", arrayOf(CHAIN))
            db.execSQL("INSERT INTO chain_nodes(rowid,chainId,url,name,isActive,isDefault) VALUES(-8,?,?,'Candidate 雪',0,0)", arrayOf(CHAIN, candidateUrl))
            db.execSQL("INSERT INTO chain_nodes(rowid,chainId,url,name,isActive,isDefault) VALUES(11,?,'wss://other.invalid/','Other',1,1)", arrayOf(OTHER))
            db.execSQL(OWNED_ENDPOINT_CREATE)
            db.execSQL(OWNED_ENDPOINT_INDEX_CREATE)
            db.execSQL("INSERT INTO custom_endpoint_records VALUES(-7,8,?,?,'Candidate 雪',1,0,-7)", arrayOf(CHAIN, candidateUrl))
            if (source == EndpointInventorySource.CURRENT) {
                db.version = 81
                check(EndpointArchiveFormat.CURRENT.capture(db, 81))
            }
            db.execSQL(EndpointRecoveryDecisionMigration.CREATE)
            db.execSQL(EndpointRecoveryDecisionMigration.CREATE_INDEX)
            db.version = 83
            val decisions = EndpointRecoveryDecisionStore(db)
            val ref = if (source == EndpointInventorySource.CURRENT) EndpointRecordRef(source, -9, null) else EndpointRecordRef(source, -7, 8)
            val saved = decisions.saveOffline(
                CancellationSignal(), decisions.prepareSource(CancellationSignal(), ref),
                EndpointDecisionCandidate("Candidate 雪", candidateUrl), checkNotNull(decisions.prepareTarget(CancellationSignal(), CHAIN)), null, EndpointDecisionAuthority()
            )
            action(Fixture(opened, EndpointSelectionRequest(saved, EndpointSelectionOwner(-7, 8, -7))))
            assertFalse(opened.db.inTransaction())
        }
    }

    private fun wrapped(
        db: SupportSQLiteDatabase,
        beforeWrite: () -> Unit = {
        },
        afterWrite: () -> Unit = {}
    ): SupportSQLiteDatabase = object : SupportSQLiteDatabase by db {
            override fun compileStatement(sql: String): SupportSQLiteStatement {
                val statement = db.compileStatement(sql)
                return object : SupportSQLiteStatement by statement {
                    override fun executeUpdateDelete(): Int {
                        beforeWrite()
                        return statement.executeUpdateDelete().also { afterWrite() }
                    }
                }
            }
        }

    private suspend fun rejected(action: suspend () -> Unit) {
        var failure: Exception? = null
        try {
            action()
        } catch (caught: Exception) {
            failure = caught
        }
        assertTrue("Expected fail-closed rejection", failure != null)
    }

    private fun scalar(db: SupportSQLiteDatabase, sql: String): Long = db.query(sql).use {
        check(it.moveToFirst())
        it.getLong(0).also { _ -> check(!it.moveToNext()) }
    }

    private fun flags(db: SupportSQLiteDatabase): List<String> =
        db.query("SELECT rowid,isActive FROM chain_nodes ORDER BY rowid").use {
        buildList { while (it.moveToNext()) add("${it.getLong(0)}/${it.getLong(1)}") }
    }

    private fun assertNodesEqualApartFromActive(before: List<String>, after: List<String>) {
        fun normalize(rows: List<String>) =
            rows.map { row -> row.split('|').filterIndexed { index, _ -> index != 3 }.joinToString("|") }
        assertEquals(normalize(before), normalize(after))
    }

    private fun image(db: SupportSQLiteDatabase): Map<String, List<String>> = buildMap {
        put("userVersion", listOf(scalar(db, "PRAGMA user_version").toString()))
        for (schema in listOf("main", "temp")) {
            put(
                "$schema.sqlite_master",
                db.query("SELECT type,name,tbl_name,hex(CAST(sql AS BLOB)) FROM $schema.sqlite_master ORDER BY type,name").use {
                buildList { while (it.moveToNext()) add((0..3).joinToString("|") { index -> it.getString(index) ?: "NULL" }) }
            }
            )
        }
        val tables = db.query("SELECT name FROM main.sqlite_master WHERE type='table' ORDER BY name").use {
            buildList { while (it.moveToNext()) add(it.getString(0)) }
        }
        for (table in tables) {
            check(table.matches(Regex("[A-Za-z0-9_]+")))
            val columns = db.query("PRAGMA main.table_info(`$table`)").use {
                buildList { while (it.moveToNext()) add(it.getString(1)) }
            }
            val projection = columns.joinToString(",") { "typeof(`$it`),hex(CAST(`$it` AS BLOB))" }
            put(
                table,
                db.query("SELECT $projection FROM main.`$table` ORDER BY rowid").use {
                buildList {
                    while (it.moveToNext()) add(columns.indices.joinToString("|") { index -> "${it.getString(index * 2)}:${it.getString(index * 2 + 1)}" })
                }
            }
            )
        }
    }

    private companion object {
        val CHAIN = "1".repeat(64)
        val OTHER = "2".repeat(64)
    }
}
