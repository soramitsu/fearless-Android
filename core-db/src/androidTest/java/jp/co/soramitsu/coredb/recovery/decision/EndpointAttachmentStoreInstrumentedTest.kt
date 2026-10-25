package jp.co.soramitsu.coredb.recovery.decision

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.os.CancellationSignal
import android.os.OperationCanceledException
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SupportSQLiteStatement
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import jp.co.soramitsu.coredb.APP_DATABASE_VERSION
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.migrations.EndpointArchiveFormat
import jp.co.soramitsu.coredb.migrations.EndpointRecoveryDecisionMigration
import jp.co.soramitsu.coredb.recovery.EndpointInventorySource
import jp.co.soramitsu.coredb.recovery.EndpointRecordRef
import jp.co.soramitsu.coredb.recovery.OWNED_ENDPOINT_CREATE
import jp.co.soramitsu.coredb.recovery.OWNED_ENDPOINT_INDEX_CREATE
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Exclusively owned core-test databases. Probe callbacks are synthetic; no RPC or app data. */
class EndpointAttachmentStoreInstrumentedTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val names = mutableListOf<String>()
    @After fun cleanup() { names.forEach(context::deleteDatabase) }

    @Test fun eachOfFiveSourcesAddsExactInactiveOwnershipWithoutChangingDecisionOrOriginalRows() {
        for (source in EndpointInventorySource.entries) fixture { db ->
            val decision = save(db, source)
            val before = image(db)
            val store = EndpointAttachmentStore(db)
            val prepared = prepare(store, decision)
            val authority = EndpointAttachmentAuthority()
            val attempt = authority.newAttempt(prepared)
            var requests = 0
            assertTrue(runBlocking { authority.requestOnce(attempt) { requests++; true } })
            val result = store.commitMatched(signal(), prepared, authority, attempt)
            assertEquals(1, requests)
            assertEquals(EndpointAttachmentResult.Kind.ADDED_INACTIVE, result.kind)
            assertTrue(result.ownerId > 0); assertEquals(1L, result.ownerRevision)
            assertFalse(result.isActive); assertTrue(result.matchedThisInvocation); assertFalse(result.requiresSourceRefresh)
            assertEquals(before.filterKeys { it !in setOf("chain_nodes", "custom_endpoint_records", "sqlite_sequence") },
                image(db).filterKeys { it !in setOf("chain_nodes", "custom_endpoint_records", "sqlite_sequence") })
            assertEquals(before.getValue("chain_nodes"), image(db).getValue("chain_nodes").filter { it.startsWith("-9/") })
            assertEquals(before.getValue("custom_endpoint_records"), image(db).getValue("custom_endpoint_records").filter { it.startsWith("-7/") })
            assertExactOwnerAndProjection(db, decision, result.ownerId, 1, 0)
            assertEquals("PENDING", EndpointRecoveryDecisionStore(db).read(signal(), source, decision.source.ref.rowId)!!.state)
            assertEquals(1L, scalar(db, "SELECT revision FROM endpoint_recovery_decisions"))
        }
    }

    @Test fun alreadyPresentInactiveAndActivePairsReturnUnverifiedObservationWithoutDmlOrReplay() {
        for (active in listOf(0, 1)) fixture { db ->
            val decision = save(db)
            attach(db, decision)
            db.execSQL("UPDATE chain_nodes SET isActive=? WHERE url=?", arrayOf<Any?>(active, decision.candidate.url))
            val before = image(db); val store = EndpointAttachmentStore(db)
            val prepared = prepare(store, decision); assertTrue(prepared.alreadyPresent)
            val authority = EndpointAttachmentAuthority(); val attempt = authority.newAttempt(prepared)
            val result = store.observePresent(signal(), prepared, authority, attempt)
            assertEquals(EndpointAttachmentResult.Kind.ALREADY_PRESENT, result.kind)
            assertEquals(active == 1, result.isActive); assertFalse(result.matchedThisInvocation)
            assertFalse(result.requiresSourceRefresh); assertEquals(before, image(db))
            var requests = 0
            assertThrows(IllegalStateException::class.java) { runBlocking { authority.requestOnce(attempt) { requests++; true } } }
            assertEquals(0, requests)
            assertThrows(IllegalStateException::class.java) { store.commitMatched(signal(), prepared, authority, attempt) }
            assertEquals(before, image(db))
        }
    }

    @Test fun matchedAlreadyPresentPairStillPreservesActiveSelectionAndEverySqlByte() = fixture { db ->
        val decision = save(db); attach(db, decision)
        db.execSQL("UPDATE chain_nodes SET isActive=1 WHERE url=?", arrayOf(decision.candidate.url))
        val before = image(db); val result = attach(db, decision)
        assertEquals(EndpointAttachmentResult.Kind.ALREADY_PRESENT, result.kind)
        assertTrue(result.isActive); assertTrue(result.matchedThisInvocation)
        assertEquals(before, image(db))
    }

    @Test fun explicitSameOwnedReattachAdvancesOnlyRevisionAndAttachmentAndMakesOldDecisionStale() = fixture { db ->
        val decision = save(db, EndpointInventorySource.OWNED, EndpointDecisionCandidate("Owned", OWNED_URL))
        val before = image(db)
        val result = attach(db, decision, EndpointAttachmentAction.REATTACH_EXACT_OWNED)
        assertEquals(EndpointAttachmentResult.Kind.REATTACHED_INACTIVE, result.kind)
        assertEquals(ROW, result.ownerId); assertEquals(2L, result.ownerRevision)
        assertTrue(result.requiresSourceRefresh); assertFalse(result.isActive)
        assertExactOwnerAndProjection(db, decision, ROW, 2, 0)
        assertEquals(ROW, scalar(db, "SELECT originalSnapshotRowId FROM custom_endpoint_records WHERE id=-7"))
        assertEquals(before.getValue("endpoint_recovery_decisions"), image(db).getValue("endpoint_recovery_decisions"))
        assertEquals(before.filterKeys { it !in setOf("chain_nodes", "custom_endpoint_records") },
            image(db).filterKeys { it !in setOf("chain_nodes", "custom_endpoint_records") })
        val attached = image(db)
        assertThrows(IllegalStateException::class.java) { prepare(EndpointAttachmentStore(db), decision, EndpointAttachmentAction.REATTACH_EXACT_OWNED) }
        assertEquals(attached, image(db))
        val choices = EndpointRecoveryDecisionStore(db)
        val newSource = choices.prepareSource(signal(), EndpointRecordRef(EndpointInventorySource.OWNED, ROW, 2))
        val refreshed = choices.saveOffline(signal(), newSource, decision.candidate, decision.target, 1, EndpointDecisionAuthority())
        assertEquals(2L, refreshed.revision)
        assertTrue(prepare(EndpointAttachmentStore(db), refreshed).alreadyPresent)
    }

    @Test fun pendingOrDeletedOtherOwnerAndDefaultProjectionCannotBeOverwrittenOrImplicitlyRevived() {
        for (kind in listOf("pending", "deleted", "default", "different-name", "unowned")) fixture { db ->
            val decision = save(db)
            when (kind) {
                "default", "unowned" -> db.execSQL("INSERT INTO chain_nodes VALUES(?,?,?,0,?)",
                    arrayOf<Any?>(CHAIN, decision.candidate.url, decision.candidate.name, if (kind == "default") 1 else 0))
                else -> db.execSQL("INSERT INTO custom_endpoint_records VALUES(17,3,?,?,?,0,?,NULL)",
                    arrayOf<Any?>(CHAIN, decision.candidate.url, if (kind == "different-name") "Someone else" else decision.candidate.name,
                        if (kind == "deleted") 1 else 0))
            }
            val before = image(db)
            for (action in EndpointAttachmentAction.entries) {
                assertThrows(IllegalStateException::class.java) { prepare(EndpointAttachmentStore(db), decision, action) }
                assertEquals(before, image(db))
            }
        }
    }

    @Test fun explicitReattachRejectsWrongSourceRevisionAndCheckedOverflowWithoutMutation() = fixture { db ->
        val decision = save(db, EndpointInventorySource.OWNED, EndpointDecisionCandidate("Owned", OWNED_URL))
        assertThrows(IllegalStateException::class.java) { prepare(EndpointAttachmentStore(db), decision) }
        db.execSQL("UPDATE custom_endpoint_records SET revision=? WHERE id=-7", arrayOf(Long.MAX_VALUE))
        val choices = EndpointRecoveryDecisionStore(db)
        val refreshed = choices.saveOffline(signal(), choices.prepareSource(signal(), EndpointRecordRef(EndpointInventorySource.OWNED, ROW, Long.MAX_VALUE)),
            decision.candidate, decision.target, 1, EndpointDecisionAuthority())
        val before = image(db)
        assertThrows(IllegalStateException::class.java) { prepare(EndpointAttachmentStore(db), refreshed, EndpointAttachmentAction.REATTACH_EXACT_OWNED) }
        assertEquals(before, image(db))
    }

    @Test fun changedSourceDecisionTargetOrOccupancyAfterMatchRejectsWholeWrite() {
        for (change in listOf(
            "UPDATE nodes SET name='Changed'",
            "UPDATE endpoint_recovery_decisions SET revision=2",
            "UPDATE endpoint_recovery_decisions SET candidateName='Changed'",
            "UPDATE chains SET isTestNet=1",
            "DELETE FROM chains",
            "INSERT INTO custom_endpoint_records VALUES(17,1,'$CHAIN','wss://candidate.invalid/Exact?x=1','Candidate 雪',0,1,NULL)"
        )) fixture { db ->
            val decision = save(db); val store = EndpointAttachmentStore(db); val prepared = prepare(store, decision)
            val authority = EndpointAttachmentAuthority(); val attempt = authority.newAttempt(prepared)
            assertTrue(runBlocking { authority.requestOnce(attempt) { true } })
            db.execSQL(change); val before = image(db)
            assertThrows(IllegalStateException::class.java) { store.commitMatched(signal(), prepared, authority, attempt) }
            assertEquals(before, image(db)); assertFalse(db.inTransaction())
        }
    }

    @Test fun existingObservationRevalidatesSourceDecisionTargetAndOccupancyAndCurrentAuthority() {
        for (change in listOf("UPDATE nodes SET name='Changed'", "UPDATE endpoint_recovery_decisions SET revision=2",
            "UPDATE chains SET isTestNet=1", "UPDATE custom_endpoint_records SET name='Changed' WHERE id>0", "REVOKE", "ABANDON")) fixture { db ->
            val decision = save(db); attach(db, decision)
            val store = EndpointAttachmentStore(db); val prepared = prepare(store, decision)
            val authority = EndpointAttachmentAuthority(); val attempt = authority.newAttempt(prepared)
            when (change) { "REVOKE" -> authority.revoke(); "ABANDON" -> authority.abandon(attempt); else -> db.execSQL(change) }
            val before = image(db)
            assertThrows(IllegalStateException::class.java) { store.observePresent(signal(), prepared, authority, attempt) }
            assertEquals(before, image(db))
        }
    }

    @Test fun revokeCancelSqlFailureOrAbandonAfterActualOwnerInsertRollsBackOwnerProjectionAndSequence() {
        for (failure in listOf("REVOKE", "CANCEL", "SQL", "ABANDON")) fixture { db ->
            val decision = save(db); val ordinary = EndpointAttachmentStore(db); val prepared = prepare(ordinary, decision)
            val authority = EndpointAttachmentAuthority(); val attempt = authority.newAttempt(prepared)
            assertTrue(runBlocking { authority.requestOnce(attempt) { true } })
            val cancel = signal(); val before = image(db); var inserts = 0
            val audited = object : SupportSQLiteDatabase by db {
                override fun compileStatement(sql: String): SupportSQLiteStatement {
                    val delegate = db.compileStatement(sql)
                    if (!sql.startsWith("INSERT OR ABORT INTO main.custom_endpoint_records")) return delegate
                    return object : SupportSQLiteStatement by delegate {
                        override fun executeInsert(): Long = delegate.executeInsert().also {
                            inserts++; assertEquals(2L, scalar(db, "SELECT COUNT(*) FROM custom_endpoint_records"))
                            when (failure) { "REVOKE" -> authority.revoke(); "CANCEL" -> cancel.cancel();
                                "SQL" -> throw SQLiteException("synthetic owner insert failure"); "ABANDON" -> authority.abandon(attempt) }
                        }
                    }
                }
            }
            val expected = when (failure) { "CANCEL" -> OperationCanceledException::class.java; "SQL" -> SQLiteException::class.java; else -> IllegalStateException::class.java }
            assertThrows(expected) { EndpointAttachmentStore(audited).commitMatched(cancel, prepared, authority, attempt) }
            assertEquals(1, inserts); assertFalse(db.inTransaction()); assertEquals(before, image(db))
            assertEquals(EndpointAttachmentResult.Kind.ADDED_INACTIVE, attach(db, decision).kind)
        }
    }

    @Test fun secondInsertFailureRollsBackFirstOwnershipInsertAndCanRetry() = fixture { db ->
        val decision = save(db); val store = EndpointAttachmentStore(db); val prepared = prepare(store, decision)
        val authority = EndpointAttachmentAuthority(); val attempt = authority.newAttempt(prepared)
        assertTrue(runBlocking { authority.requestOnce(attempt) { true } }); val before = image(db)
        val sentinel = SQLiteException("synthetic projection insert failure")
        val audited = object : SupportSQLiteDatabase by db {
            override fun compileStatement(sql: String): SupportSQLiteStatement {
                if (sql.startsWith("INSERT OR ABORT INTO main.chain_nodes")) {
                    assertEquals(2L, scalar(db, "SELECT COUNT(*) FROM custom_endpoint_records")); throw sentinel
                }
                return db.compileStatement(sql)
            }
        }
        assertSame(sentinel, assertThrows(SQLiteException::class.java) { EndpointAttachmentStore(audited).commitMatched(signal(), prepared, authority, attempt) })
        assertEquals(before, image(db)); assertFalse(db.inTransaction())
        attach(db, decision)
    }

    @Test fun mismatchedNoRequestAndForeignCaptureAttemptsNeverCommitAndLeaveSqlExact() = fixture { db ->
        val decision = save(db); val store = EndpointAttachmentStore(db); val prepared = prepare(store, decision)
        val before = image(db)
        for (matched in listOf<Boolean?>(null, false)) {
            val authority = EndpointAttachmentAuthority(); val attempt = authority.newAttempt(prepared)
            if (matched != null) assertFalse(runBlocking { authority.requestOnce(attempt) { false } })
            assertThrows(IllegalStateException::class.java) { store.commitMatched(signal(), prepared, authority, attempt) }
            assertEquals(before, image(db))
        }
        val authority = EndpointAttachmentAuthority(); val attempt = authority.newAttempt(prepared)
        assertTrue(runBlocking { authority.requestOnce(attempt) { true } })
        val equalButDifferentCapture = prepare(store, decision)
        assertThrows(IllegalArgumentException::class.java) { store.commitMatched(signal(), equalButDifferentCapture, authority, attempt) }
        assertEquals(before, image(db))
        assertThrows(IllegalArgumentException::class.java) { store.commitMatched(signal(), prepared, EndpointAttachmentAuthority(), attempt) }
        assertEquals(before, image(db))
    }

    @Test fun revocationAfterFinalGrantIsNonblockingWhilePhysicalCommitIsHeld() = fixture { db ->
        val decision = save(db); val store = EndpointAttachmentStore(db); val prepared = prepare(store, decision)
        val authority = EndpointAttachmentAuthority(); val attempt = authority.newAttempt(prepared)
        assertTrue(runBlocking { authority.requestOnce(attempt) { true } })
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val result = AtomicReference<EndpointAttachmentResult?>(); val failure = AtomicReference<Throwable?>()
        val held = object : SupportSQLiteDatabase by db {
            override fun endTransaction() { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)); db.endTransaction() }
        }
        val worker = Thread { try { result.set(EndpointAttachmentStore(held).commitMatched(signal(), prepared, authority, attempt)) } catch (e: Throwable) { failure.set(e) } }
        worker.start()
        try { assertTrue(entered.await(5, TimeUnit.SECONDS)); authority.revoke(); assertFalse(authority.isCurrent()); assertTrue(worker.isAlive) }
        finally { release.countDown(); worker.join(5_000) }
        assertFalse(worker.isAlive); assertNull(failure.get()); assertEquals(EndpointAttachmentResult.Kind.ADDED_INACTIVE, result.get()!!.kind)
        assertEquals(2L, scalar(db, "SELECT COUNT(*) FROM custom_endpoint_records"))
    }

    @Test fun oversizedMalformedAndBlobOwnerNameConflictsStayBoundedAndCannotClaimExistingOwnership() {
        for (value in listOf("CAST(X'4EFF00' AS TEXT)", "X'43616E64696461746520E99BAA'", "CAST(zeroblob(2097152) AS TEXT)")) fixture { db ->
            val decision = save(db)
            db.execSQL("INSERT INTO custom_endpoint_records VALUES(17,1,?,?,'Candidate 雪',0,0,NULL)", arrayOf<Any?>(CHAIN, decision.candidate.url))
            db.execSQL("UPDATE custom_endpoint_records SET name=$value WHERE id=17")
            var boundedQueries = 0
            val audited = object : SupportSQLiteDatabase by db {
                override fun query(query: SupportSQLiteQuery, cancellationSignal: CancellationSignal?): Cursor {
                    if (query.sql.contains("FROM main.custom_endpoint_records WHERE chainId")) {
                        assertTrue(query.sql.startsWith("SELECT CASE WHEN typeof(id)")); assertTrue(query.sql.contains("CAST(name AS BLOB)")); boundedQueries++
                    }
                    return db.query(query, cancellationSignal)
                }
            }
            val before = image(db)
            assertThrows(IllegalStateException::class.java) { prepare(EndpointAttachmentStore(audited), decision) }
            assertEquals(1, boundedQueries); assertEquals(before, image(db))
        }
    }

    @Test fun nonCanonicalOrShadowedProjectionAndForeignKeysOffRejectBeforeAnyMutation() {
        for (change in listOf("CREATE TEMP TABLE chain_nodes(marker)",
            "CREATE TRIGGER unrelated_node AFTER INSERT ON chain_nodes BEGIN SELECT 1; END", "PRAGMA foreign_keys=OFF")) fixture { db ->
            val decision = save(db); db.execSQL(change); val before = image(db)
            assertThrows(IllegalStateException::class.java) { prepare(EndpointAttachmentStore(db), decision) }
            assertEquals(before, image(db))
        }
    }

    @Test fun cancellationBeforeTransactionAndInsideOccupancyClosesCursorAndWritesNothing() = fixture { db ->
        val decision = save(db); val before = image(db)
        assertThrows(OperationCanceledException::class.java) {
            EndpointAttachmentStore(db).prepare(signal().apply { cancel() }, decision.source.ref, decision.id, decision.revision, EndpointAttachmentAction.ADD_NEW)
        }
        val cancel = signal(); var observed: Cursor? = null
        val audited = object : SupportSQLiteDatabase by db {
            override fun query(query: SupportSQLiteQuery, cancellationSignal: CancellationSignal?): Cursor = db.query(query, cancellationSignal).also {
                if (query.sql.contains("FROM main.custom_endpoint_records WHERE chainId")) { observed = it; cancel.cancel() }
            }
        }
        assertThrows(OperationCanceledException::class.java) { EndpointAttachmentStore(audited).prepare(cancel, decision.source.ref, decision.id, decision.revision, EndpointAttachmentAction.ADD_NEW) }
        assertNotNull(observed); assertTrue(observed!!.isClosed); assertFalse(db.inTransaction()); assertEquals(before, image(db))
    }

    @Test fun actualRoomNodeObserverDoesNotBlockProjectionSchemaAdmission() = runBlocking<Unit> {
        val room = Room.databaseBuilder(context, AppDatabase::class.java, name()).build()
        val firstEmission = kotlinx.coroutines.CompletableDeferred<Unit>()
        val observer = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            room.chainDao().nodesFlow("solana-mainnet").collect { firstEmission.complete(Unit) }
        }
        try {
            kotlinx.coroutines.withTimeout(5_000) { firstEmission.await() }
            val db = room.openHelper.writableDatabase
            db.beginTransactionNonExclusive()
            try {
                db.query("SELECT name,sql FROM temp.sqlite_master WHERE type='trigger' AND tbl_name='chain_nodes' ORDER BY name").use { rows ->
                    val found = mutableListOf<String>()
                    while (rows.moveToNext()) {
                        found += rows.getString(0)
                        android.util.Log.i("EndpointRoomObserver", rows.getString(1))
                    }
                    assertEquals(listOf("DELETE", "INSERT", "UPDATE").map { "room_table_modification_trigger_chain_nodes_$it" }, found)
                }
                EndpointAttachmentSql.requireSchema(db, signal())
            } finally { db.endTransaction() }
        } finally {
            observer.cancel()
            observer.join()
            room.close()
        }
    }

    @Test fun actualFreshRoom83ProjectionDdlPassesExactSchemaAdmission() {
        val room = Room.databaseBuilder(context, AppDatabase::class.java, name()).build()
        try {
            val db = room.openHelper.writableDatabase
            db.beginTransactionNonExclusive()
            try { EndpointAttachmentSql.requireSchema(db, signal()) } finally { db.endTransaction() }
            assertEquals(APP_DATABASE_VERSION, db.version)
        } finally { room.close() }
    }

    @Test fun actualReattachRevisionUpdateThenRevocationCancellationOrSqlFailureRollsBackExactOwner() {
        for (failure in listOf("REVOKE", "CANCEL", "SQL")) fixture { db ->
            val decision = save(db, EndpointInventorySource.OWNED, EndpointDecisionCandidate("Owned", OWNED_URL))
            val prepared = prepare(EndpointAttachmentStore(db), decision, EndpointAttachmentAction.REATTACH_EXACT_OWNED)
            val authority = EndpointAttachmentAuthority(); val attempt = authority.newAttempt(prepared)
            assertTrue(runBlocking { authority.requestOnce(attempt) { true } })
            val cancellation = signal(); val before = image(db); var updates = 0
            val audited = object : SupportSQLiteDatabase by db {
                override fun compileStatement(sql: String): SupportSQLiteStatement {
                    val delegate = db.compileStatement(sql)
                    if (!sql.startsWith("UPDATE OR ABORT main.custom_endpoint_records")) return delegate
                    return object : SupportSQLiteStatement by delegate {
                        override fun executeUpdateDelete(): Int = delegate.executeUpdateDelete().also {
                            assertEquals(1, it); updates++
                            assertEquals(2L, scalar(db, "SELECT revision FROM custom_endpoint_records WHERE id=-7"))
                            when (failure) { "REVOKE" -> authority.revoke(); "CANCEL" -> cancellation.cancel();
                                "SQL" -> throw SQLiteException("synthetic reattachment failure") }
                        }
                    }
                }
            }
            val expected = when (failure) { "CANCEL" -> OperationCanceledException::class.java; "SQL" -> SQLiteException::class.java; else -> IllegalStateException::class.java }
            assertThrows(expected) { EndpointAttachmentStore(audited).commitMatched(cancellation, prepared, authority, attempt) }
            assertEquals(1, updates); assertFalse(db.inTransaction()); assertEquals(before, image(db))
            assertEquals(EndpointAttachmentResult.Kind.REATTACHED_INACTIVE,
                attach(db, decision, EndpointAttachmentAction.REATTACH_EXACT_OWNED).kind)
        }
    }

    @Test fun unsupportedSavedTargetAndOuterTransactionRejectWithoutChangingSql() = fixture { db ->
        db.execSQL("UPDATE chains SET isEthereumChain=1")
        val unsupported = save(db)
        assertEquals(EndpointDecisionTarget.Family.UNSUPPORTED, unsupported.target.family)
        val before = image(db)
        assertThrows(IllegalStateException::class.java) { prepare(EndpointAttachmentStore(db), unsupported) }
        assertEquals(before, image(db))
        db.beginTransactionNonExclusive()
        try {
            assertThrows(IllegalStateException::class.java) { prepare(EndpointAttachmentStore(db), unsupported) }
            assertTrue(db.inTransaction()); assertEquals(before, image(db))
        } finally { db.endTransaction() }
        assertEquals(before, image(db))
    }

    @Test fun unmatchedRevokedAndAbandonedAttemptsOpenNoTransactionAndExecuteNoDml() {
        for (mode in listOf("NEW", "FALSE", "REVOKED", "ABANDONED")) fixture { db ->
            val decision = save(db); val prepared = prepare(EndpointAttachmentStore(db), decision)
            val authority = EndpointAttachmentAuthority(); val attempt = authority.newAttempt(prepared)
            when (mode) {
                "FALSE" -> assertFalse(runBlocking { authority.requestOnce(attempt) { false } })
                "REVOKED", "ABANDONED" -> {
                    assertTrue(runBlocking { authority.requestOnce(attempt) { true } })
                    if (mode == "REVOKED") authority.revoke() else authority.abandon(attempt)
                }
            }
            val before = image(db); var transactions = 0; var statements = 0; var dml = 0
            val audited = object : SupportSQLiteDatabase by db {
                override fun beginTransactionNonExclusive() { transactions++; db.beginTransactionNonExclusive() }
                override fun execSQL(sql: String) { dml++; db.execSQL(sql) }
                override fun execSQL(sql: String, bindArgs: Array<out Any?>) { dml++; db.execSQL(sql, bindArgs) }
                override fun compileStatement(sql: String): SupportSQLiteStatement {
                    statements++
                    val delegate = db.compileStatement(sql)
                    return object : SupportSQLiteStatement by delegate {
                        override fun execute() { dml++; delegate.execute() }
                        override fun executeInsert(): Long { dml++; return delegate.executeInsert() }
                        override fun executeUpdateDelete(): Int { dml++; return delegate.executeUpdateDelete() }
                    }
                }
            }
            assertThrows(IllegalStateException::class.java) {
                EndpointAttachmentStore(audited).commitMatched(signal(), prepared, authority, attempt)
            }
            assertEquals("transaction admission for $mode", 0, transactions)
            assertEquals("compiled statements for $mode", 0, statements)
            assertEquals("SQL writes for $mode", 0, dml)
            assertFalse(db.inTransaction()); assertEquals(before, image(db))
            var requests = 0
            assertThrows(IllegalStateException::class.java) { runBlocking { authority.requestOnce(attempt) { requests++; true } } }
            assertEquals(0, requests)
        }
    }

    @Test fun twoMatchedAttemptsContendForOneSqlKeyAndOnlyFirstPairCommitsWithoutOverwrite() = fixture { db ->
        val winner = save(db)
        val contender = save(db, EndpointInventorySource.CURRENT,
            EndpointDecisionCandidate("Different candidate", winner.candidate.url))
        val firstCapture = prepare(EndpointAttachmentStore(db), winner)
        val secondCapture = prepare(EndpointAttachmentStore(db), contender)
        assertFalse(firstCapture.alreadyPresent); assertFalse(secondCapture.alreadyPresent)
        val firstAuthority = EndpointAttachmentAuthority(); val secondAuthority = EndpointAttachmentAuthority()
        val firstAttempt = firstAuthority.newAttempt(firstCapture); val secondAttempt = secondAuthority.newAttempt(secondCapture)
        assertTrue(runBlocking { firstAuthority.requestOnce(firstAttempt) { true } })
        assertTrue(runBlocking { secondAuthority.requestOnce(secondAttempt) { true } })
        val before = image(db)
        val ownerSequenceRow = scalar(db, "SELECT rowid FROM sqlite_sequence WHERE name='custom_endpoint_records'")
        val inserted = CountDownLatch(1); val release = CountDownLatch(1); val contenderRequestedWriter = CountDownLatch(1)
        val contenderAcquiredWriter = CountDownLatch(1); val contenderDone = CountDownLatch(1)
        val firstDml = AtomicInteger(); val secondStatements = AtomicInteger(); val secondDml = AtomicInteger()
        val result = AtomicReference<EndpointAttachmentResult?>(); val rejectedResult = AtomicReference<EndpointAttachmentResult?>()
        val firstFailure = AtomicReference<Throwable?>(); val secondFailure = AtomicReference<Throwable?>()
        val firstDb = object : SupportSQLiteDatabase by db {
            override fun compileStatement(sql: String): SupportSQLiteStatement {
                val delegate = db.compileStatement(sql)
                return object : SupportSQLiteStatement by delegate {
                    override fun executeInsert(): Long = delegate.executeInsert().also {
                        firstDml.incrementAndGet()
                        if (sql.startsWith("INSERT OR ABORT INTO main.custom_endpoint_records")) {
                            assertEquals(2L, scalar(db, "SELECT COUNT(*) FROM custom_endpoint_records"))
                            inserted.countDown(); check(release.await(10, TimeUnit.SECONDS))
                        }
                    }
                }
            }
        }
        val secondDb = object : SupportSQLiteDatabase by db {
            override fun beginTransactionNonExclusive() {
                contenderRequestedWriter.countDown()
                db.beginTransactionNonExclusive()
                contenderAcquiredWriter.countDown()
            }
            override fun compileStatement(sql: String): SupportSQLiteStatement {
                secondStatements.incrementAndGet()
                val delegate = db.compileStatement(sql)
                return object : SupportSQLiteStatement by delegate {
                    override fun execute() { secondDml.incrementAndGet(); delegate.execute() }
                    override fun executeInsert(): Long { secondDml.incrementAndGet(); return delegate.executeInsert() }
                    override fun executeUpdateDelete(): Int { secondDml.incrementAndGet(); return delegate.executeUpdateDelete() }
                }
            }
        }
        val first = Thread {
            try { result.set(EndpointAttachmentStore(firstDb).commitMatched(signal(), firstCapture, firstAuthority, firstAttempt)) }
            catch (failure: Throwable) { firstFailure.set(failure) }
        }
        val second = Thread {
            try { rejectedResult.set(EndpointAttachmentStore(secondDb).commitMatched(signal(), secondCapture, secondAuthority, secondAttempt)) }
            catch (failure: Throwable) { secondFailure.set(failure) }
            finally { contenderDone.countDown() }
        }
        first.start()
        try {
            assertTrue(inserted.await(5, TimeUnit.SECONDS))
            second.start()
            assertTrue(contenderRequestedWriter.await(5, TimeUnit.SECONDS))
            // The first transaction still owns the writer connection; second cannot see its partial pair.
            assertEquals(1L, contenderAcquiredWriter.count); assertEquals(1L, contenderDone.count)
        } finally { release.countDown(); first.join(10_000); second.join(10_000) }
        assertFalse(first.isAlive); assertFalse(second.isAlive)
        assertNull(firstFailure.get()); assertNull(rejectedResult.get())
        assertTrue(secondFailure.get() is IllegalStateException)
        assertEquals(0L, contenderAcquiredWriter.count); assertEquals(0L, contenderDone.count)
        assertEquals(2, firstDml.get()); assertEquals(0, secondStatements.get()); assertEquals(0, secondDml.get())
        val committed = checkNotNull(result.get())
        assertEquals(EndpointAttachmentResult.Kind.ADDED_INACTIVE, committed.kind)
        assertExactOwnerAndProjection(db, winner, committed.ownerId, 1, 0)
        assertEquals(2L, scalar(db, "SELECT COUNT(*) FROM custom_endpoint_records"))
        assertEquals(2L, scalar(db, "SELECT COUNT(*) FROM chain_nodes"))
        assertEquals(committed.ownerId, scalar(db, "SELECT seq FROM sqlite_sequence WHERE name='custom_endpoint_records'"))
        val after = image(db)
        assertEquals(before.filterKeys { it !in setOf("chain_nodes", "custom_endpoint_records", "sqlite_sequence") },
            after.filterKeys { it !in setOf("chain_nodes", "custom_endpoint_records", "sqlite_sequence") })
        assertEquals(before.getValue("chain_nodes"), after.getValue("chain_nodes").filter { it.startsWith("-9/") })
        assertEquals(before.getValue("custom_endpoint_records"), after.getValue("custom_endpoint_records").filter { it.startsWith("-7/") })
        assertEquals(before.getValue("sqlite_sequence").filterNot { it.startsWith("$ownerSequenceRow/") },
            after.getValue("sqlite_sequence").filterNot { it.startsWith("$ownerSequenceRow/") })
        assertFalse(db.inTransaction())
    }

    private fun save(db: SupportSQLiteDatabase, source: EndpointInventorySource = EndpointInventorySource.LEGACY,
        candidate: EndpointDecisionCandidate = EndpointDecisionCandidate("Candidate 雪", "wss://candidate.invalid/Exact?x=1")): EndpointRecoveryDecision {
        val choices = EndpointRecoveryDecisionStore(db)
        return choices.saveOffline(signal(), choices.prepareSource(signal(), ref(source)), candidate,
            checkNotNull(choices.prepareTarget(signal(), CHAIN)), null, EndpointDecisionAuthority())
    }

    private fun prepare(store: EndpointAttachmentStore, decision: EndpointRecoveryDecision,
        action: EndpointAttachmentAction = EndpointAttachmentAction.ADD_NEW): EndpointAttachmentCapture =
        store.prepare(signal(), decision.source.ref, decision.id, decision.revision, action)

    private fun attach(db: SupportSQLiteDatabase, decision: EndpointRecoveryDecision,
        action: EndpointAttachmentAction = EndpointAttachmentAction.ADD_NEW): EndpointAttachmentResult {
        val store = EndpointAttachmentStore(db); val prepared = prepare(store, decision, action)
        val authority = EndpointAttachmentAuthority(); val attempt = authority.newAttempt(prepared)
        assertTrue(runBlocking { authority.requestOnce(attempt) { true } })
        return store.commitMatched(signal(), prepared, authority, attempt)
    }

    private fun assertExactOwnerAndProjection(db: SupportSQLiteDatabase, decision: EndpointRecoveryDecision, id: Long, revision: Long, active: Int) {
        db.query("SELECT revision,chainId,url,name,attached,deleted FROM custom_endpoint_records WHERE id=?", arrayOf(id)).use {
            assertTrue(it.moveToFirst()); assertEquals(revision, it.getLong(0)); assertEquals(CHAIN, it.getString(1))
            assertEquals(decision.candidate.url, it.getString(2)); assertEquals(decision.candidate.name, it.getString(3))
            assertEquals(1, it.getInt(4)); assertEquals(0, it.getInt(5)); assertFalse(it.moveToNext())
        }
        db.query("SELECT name,isDefault,isActive FROM chain_nodes WHERE chainId=? AND url=?", arrayOf<Any?>(CHAIN, decision.candidate.url)).use {
            assertTrue(it.moveToFirst()); assertEquals(decision.candidate.name, it.getString(0)); assertEquals(0, it.getInt(1)); assertEquals(active, it.getInt(2)); assertFalse(it.moveToNext())
        }
    }

    private fun fixture(block: (SupportSQLiteDatabase) -> Unit) {
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name()).callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onConfigure(db: SupportSQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Unexpected test upgrade")
            }).build())
        try { val db = helper.writableDatabase; seed(db); block(db) } finally { helper.close() }
    }

    private fun seed(db: SupportSQLiteDatabase) {
        db.version = 28
        db.execSQL("CREATE TABLE chains(id TEXT PRIMARY KEY NOT NULL,ecosystem TEXT,isEthereumBased INTEGER,isEthereumChain INTEGER,isTestNet INTEGER,history_type TEXT)")
        db.execSQL("INSERT INTO chains VALUES(?,'Substrate',0,0,0,NULL)", arrayOf(CHAIN))
        db.execSQL("CREATE TABLE `chain_nodes` (`chainId` TEXT NOT NULL, `url` TEXT NOT NULL, `name` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `isDefault` INTEGER NOT NULL, PRIMARY KEY(`chainId`, `url`), FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX `index_chain_nodes_chainId` ON `chain_nodes` (`chainId`)")
        db.execSQL("INSERT INTO chain_nodes(rowid,chainId,url,name,isActive,isDefault) VALUES(-9,?,'wss://original.invalid','Original',1,1)", arrayOf(CHAIN))
        db.execSQL("CREATE TABLE nodes(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,name TEXT NOT NULL,link TEXT NOT NULL,networkType INTEGER NOT NULL,isDefault INTEGER NOT NULL,isActive INTEGER NOT NULL)")
        db.execSQL("INSERT INTO nodes VALUES(-7,'Legacy','wss://raw.invalid',999,0,0)")
        db.execSQL(OWNED_ENDPOINT_CREATE); db.execSQL(OWNED_ENDPOINT_INDEX_CREATE)
        db.execSQL("INSERT INTO custom_endpoint_records VALUES(-7,1,?,?,'Owned',0,0,-7)", arrayOf<Any?>(CHAIN, OWNED_URL))
        assertTrue(EndpointArchiveFormat.HISTORICAL.capture(db, 39))
        assertTrue(EndpointArchiveFormat.UPGRADE_ENTRY.capture(db, 39))
        assertTrue(EndpointArchiveFormat.CURRENT.capture(db, 81))
        db.version = 82
        db.beginTransaction()
        try { EndpointRecoveryDecisionMigration.migrate(db); db.setTransactionSuccessful() } finally { db.endTransaction() }
        db.version = 83
    }

    /** Independent typed streaming oracle; no large raw cell is materialized in a CursorWindow. */
    private fun image(db: SupportSQLiteDatabase, includeDecisions: Boolean = true): Map<String, List<String>> {
        val tables = db.query("SELECT name FROM main.sqlite_master WHERE type='table' ORDER BY name").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        return buildMap {
            put("user_version", listOf(scalar(db, "PRAGMA user_version").toString()))
            for (schema in listOf("main", "temp")) {
                val filter = if (includeDecisions) "" else
                    " WHERE name NOT LIKE '%endpoint_recovery_decisions%' AND tbl_name!='endpoint_recovery_decisions'"
                put("$schema.sqlite_master", db.query(
                    "SELECT type,name,tbl_name,rootpage,hex(CAST(sql AS BLOB)) FROM $schema.sqlite_master$filter ORDER BY name"
                ).use { cursor -> buildList {
                    while (cursor.moveToNext()) add((0 until cursor.columnCount).joinToString("/") {
                        if (cursor.isNull(it)) "NULL" else cursor.getString(it)
                    })
                } })
            }
            for (table in tables) {
                if (!includeDecisions && table == "endpoint_recovery_decisions") continue
                val escaped = "`" + table.replace("`", "``") + "`"
                val columns = db.query("PRAGMA table_info($escaped)").use { cursor ->
                    buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
                }
                val filter = if (!includeDecisions && table == "sqlite_sequence") " WHERE name!='endpoint_recovery_decisions'" else ""
                val ids = db.query("SELECT rowid FROM $escaped$filter ORDER BY rowid").use { cursor ->
                    buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) }
                }
                val rows = mutableListOf<String>()
                for (id in ids) for (column in columns) {
                    val field = "`" + column.replace("`", "``") + "`"
                    val metadata = db.query("SELECT typeof($field),length(CAST($field AS BLOB))," +
                        "CASE WHEN typeof($field)='integer' THEN $field END," +
                        "CASE WHEN typeof($field)='real' THEN $field END FROM $escaped WHERE rowid=?", arrayOf(id)).use {
                        check(it.moveToFirst())
                        val kind = it.getString(0)
                        Triple(kind, it.getLong(1), when (kind) {
                            "integer" -> it.getLong(2).toString()
                            "real" -> java.lang.Double.doubleToRawLongBits(it.getDouble(3)).toString()
                            else -> ""
                        })
                    }
                    val digest = MessageDigest.getInstance("SHA-256")
                    if (metadata.first in listOf("text", "blob")) {
                        var offset = 0L
                        while (offset < metadata.second) {
                            val count = minOf(32768L, metadata.second - offset)
                            db.query("SELECT substr(CAST($field AS BLOB),?,?) FROM $escaped WHERE rowid=?",
                                arrayOf<Any?>(offset + 1, count, id)).use {
                                check(it.moveToFirst()); val bytes = it.getBlob(0); check(bytes.size.toLong() == count); digest.update(bytes)
                            }
                            offset += count
                        }
                    }
                    rows += "$id/$column/${metadata.first}/${metadata.second}/${metadata.third}/" +
                        digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
                }
                put(table, rows)
            }
        }
    }


    private fun name(): String = "endpoint-attachment-${UUID.randomUUID()}".also(names::add)
    private fun signal() = CancellationSignal()
    private fun ref(source: EndpointInventorySource) = EndpointRecordRef(source,
        if (source in setOf(EndpointInventorySource.LEGACY, EndpointInventorySource.OWNED)) ROW else -9L,
        if (source == EndpointInventorySource.OWNED) 1L else null)
    private fun scalar(db: SupportSQLiteDatabase, sql: String): Long = db.query(sql).use { check(it.moveToFirst()); it.getLong(0) }
    private companion object { const val ROW = -7L; const val OWNED_URL = "wss://owned.invalid"; val CHAIN = "1".repeat(64) }
}
