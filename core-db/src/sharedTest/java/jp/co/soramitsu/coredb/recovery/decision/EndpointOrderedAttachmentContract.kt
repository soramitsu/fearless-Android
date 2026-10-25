package jp.co.soramitsu.coredb.recovery.decision

import android.os.CancellationSignal
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteStatement
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

/** Same real-SQL attachment contract for standalone host and future core-test native adapters. */
abstract class EndpointOrderedAttachmentContract {
    protected abstract fun openDatabase(): EndpointSelectionStoreContract.Database

    @Test fun boundCaptureCannotBypassLaneAndAcknowledgesPhysicalCommit() = runBlocking<Unit> {
        fixture { f ->
            val before = image(f.db)
            val capture = f.prepare()
            assertEquals(before, image(f.db))
            val attempt = f.authority.newAttempt(capture)
            assertTrue(f.authority.requestOnce(attempt) { true })
            assertTrue(
                runCatching {
                EndpointAttachmentStore(f.db).commitMatched(CancellationSignal(), capture, f.authority, attempt)
            }.isFailure
            )
            assertEquals(before, image(f.db))
            val result = f.store.commitMatched(CancellationSignal(), capture, f.authority, attempt)
            assertEquals(EndpointAttachmentResult.Kind.ADDED_INACTIVE, result.kind)
            assertFalse(f.db.inTransaction())
            assertEquals(0L, scalar(f.db, "SELECT isActive FROM chain_nodes WHERE url='wss://candidate.invalid/Exact?token=synthetic'"))
            assertEquals(
                before.filterKeys { it !in setOf("chain_nodes", "custom_endpoint_records", "sqlite_sequence") },
                image(f.db).filterKeys { it !in setOf("chain_nodes", "custom_endpoint_records", "sqlite_sequence") }
            )
        }
    }

    @Test fun supersededBeforeProbeNeverInvokesItsCallback() = runBlocking<Unit> {
        fixture { f ->
            val capture = f.prepare()
            val attempt = f.authority.newAttempt(capture)
            val before = image(f.db)
            f.lane.admit(listOf(CHAIN))
            var invoked = false
            assertTrue(
                runCatching {
                    f.authority.requestOnce(attempt) {
                invoked = true

            true
            }
            }.isFailure
            )
            assertFalse(invoked)
            assertEquals(before, image(f.db))
        }
    }

    @Test fun heldProbeCallbackHasNoLaneMonitorAndRejectsStaleCompletion() = runBlocking<Unit> {
        fixture { f ->
            val capture = f.prepare()
            val attempt = f.authority.newAttempt(capture)
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val request = async(Dispatchers.Default) {
                runCatching {
                    f.authority.requestOnce(attempt) {
                    assertFalse(Thread.holdsLock(f.lane.stateLock))
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    true
                }
                }
            }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                withTimeout(2000) { async(Dispatchers.Default) { f.lane.admit(listOf(CHAIN)) }.await() }
                release.countDown()
                assertTrue(withTimeout(5000) { request.await() }.isFailure)
                assertTrue(runCatching { f.store.commitMatched(CancellationSignal(), capture, f.authority, attempt) }.isFailure)
            } finally {
                release.countDown()

                withTimeout(5000) { request.await() }
            }
        }
    }

    @Test fun firstRealInsertThenNewIntentRollsBackOwnerAndProjection() = runBlocking<Unit> {
        fixture { f ->
            val capture = f.prepare()
            val attempt = f.authority.newAttempt(capture)
            assertTrue(f.authority.requestOnce(attempt) { true })
            val before = image(f.db)
            var writes = 0
            val db = object : SupportSQLiteDatabase by f.db {
                override fun compileStatement(sql: String): SupportSQLiteStatement {
                    val statement = f.db.compileStatement(sql)
                    return object : SupportSQLiteStatement by statement {
                        override fun executeInsert(): Long = statement.executeInsert().also {
                            assertFalse(Thread.holdsLock(f.lane.stateLock))
                            if (++writes == 1) f.lane.admit(listOf(CHAIN))
                        }
                    }
                }
            }
            assertTrue(
                runCatching {
                OrderedEndpointAttachmentStore(db, f.lane).commitMatched(CancellationSignal(), capture, f.authority, attempt)
            }.isFailure
            )
            assertTrue(writes >= 1)
            assertEquals(before, image(f.db))
            assertFalse(f.db.inTransaction())
        }
    }

    @Test fun grantedPhysicalCommitRetainsLaneButNeverItsMonitor() = runBlocking<Unit> {
        fixture { f ->
            val capture = f.prepare()
            val attempt = f.authority.newAttempt(capture)
            assertTrue(f.authority.requestOnce(attempt) { true })
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val nextEntered = CountDownLatch(1)
            val db = object : SupportSQLiteDatabase by f.db {
                override fun setTransactionSuccessful() {
                    assertFalse(Thread.holdsLock(f.lane.stateLock))
                    entered.countDown()

                    check(release.await(5, TimeUnit.SECONDS))
                    f.db.setTransactionSuccessful()
                }
            }
            val commit = async(Dispatchers.Default) {
                OrderedEndpointAttachmentStore(db, f.lane).commitMatched(CancellationSignal(), capture, f.authority, attempt)
            }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                withTimeout(2000) { async(Dispatchers.Default) { f.authority.revoke() }.await() }
                val next = f.lane.admit(listOf(CHAIN))
                val queued = async(Dispatchers.Default) { f.lane.withOperation(next) { nextEntered.countDown() } }
                assertFalse(nextEntered.await(100, TimeUnit.MILLISECONDS))
                release.countDown()
                assertEquals(EndpointAttachmentResult.Kind.ADDED_INACTIVE, withTimeout(5000) { commit.await() }.kind)
                withTimeout(5000) { queued.await() }
                assertTrue(nextEntered.await(1, TimeUnit.SECONDS))
                assertFalse(f.lane.isLatest(checkNotNull(capture.mutation)))
            } finally {
                release.countDown()

                withTimeout(5000) { commit.await() }
            }
        }
    }

    @Test fun alreadyPresentObservationIsReadOnlyAndConsumesItsLaneGrant() = runBlocking<Unit> {
        fixture { f ->
            val first = f.prepare()
            val firstAttempt = f.authority.newAttempt(first)
            f.authority.requestOnce(firstAttempt) { true }
            f.store.commitMatched(CancellationSignal(), first, f.authority, firstAttempt)
            val capture = f.prepare()
            assertTrue(capture.alreadyPresent)
            val before = image(f.db)
            val noWrites = object : SupportSQLiteDatabase by f.db {
                override fun compileStatement(sql: String): SupportSQLiteStatement = error("Observation attempted DML")
                override fun setTransactionSuccessful(): Unit = error("Observation marked transaction successful")
            }
            val attempt = f.authority.newAttempt(capture)
            assertTrue(
                runCatching {
                EndpointAttachmentStore(f.db).observePresent(CancellationSignal(), capture, f.authority, attempt)
            }.isFailure
            )
            val result = OrderedEndpointAttachmentStore(noWrites, f.lane).observePresent(CancellationSignal(), capture, f.authority, attempt)
            assertEquals(EndpointAttachmentResult.Kind.ALREADY_PRESENT, result.kind)
            assertFalse(f.lane.isCurrent(checkNotNull(capture.mutation)))
            assertEquals(before, image(f.db))
        }
    }

    @Test fun foreignLaneUnboundCaptureAndWrongChainFailClosed() = runBlocking<Unit> {
        fixture { f ->
            val before = image(f.db)
            val unbound = EndpointAttachmentStore(f.db).prepare(
                CancellationSignal(), f.decision.source.ref, f.decision.id,
                f.decision.revision, EndpointAttachmentAction.ADD_NEW
            )
            assertTrue(
                runCatching {
                f.store.observePresent(CancellationSignal(), unbound, f.authority, f.authority.newAttempt(unbound))
            }.isFailure
            )
            assertTrue(
                runCatching {
                    f.store.prepare(
                        CancellationSignal(), f.lane.admit(listOf("other")), f.decision.source.ref,
                f.decision.id, f.decision.revision, EndpointAttachmentAction.ADD_NEW
                    )
                }.isFailure
            )
            val capture = f.prepare()
            val attempt = f.authority.newAttempt(capture)
            f.authority.requestOnce(attempt) { true }
            assertTrue(
                runCatching {
                OrderedEndpointAttachmentStore(f.db, EndpointMutationLane()).commitMatched(CancellationSignal(), capture, f.authority, attempt)
            }.isFailure
            )
            assertEquals(before, image(f.db))
        }
    }

    @Test fun probeMatchedBeforeAbaOrDatabaseRetirementCannotCommit() = runBlocking<Unit> {
        for (retired in listOf(false, true)) fixture { f ->
            val capture = f.prepare()
            val attempt = f.authority.newAttempt(capture)
            f.authority.requestOnce(attempt) { true }
            val before = image(f.db)
            if (retired) {
                f.lane.retire()
            } else {
                f.lane.admit(listOf(CHAIN))

                f.lane.admit(listOf(CHAIN))
            }
            assertTrue(runCatching { f.store.commitMatched(CancellationSignal(), capture, f.authority, attempt) }.isFailure)
            assertEquals(before, image(f.db))
        }
    }

    private class Fixture(val db: SupportSQLiteDatabase, val decision: EndpointRecoveryDecision) {
        val lane = EndpointMutationLane()
        val authority = EndpointAttachmentAuthority()
        val store = OrderedEndpointAttachmentStore(db, lane)
        suspend fun prepare() = store.prepare(
            CancellationSignal(), lane.admit(listOf(CHAIN)), decision.source.ref,
            decision.id, decision.revision, EndpointAttachmentAction.ADD_NEW
        )
    }

    private suspend fun fixture(action: suspend (Fixture) -> Unit) {
        openDatabase().use { opened ->
            val db = opened.db
            db.setForeignKeyConstraintsEnabled(true)
            db.execSQL("CREATE TABLE chains(id TEXT PRIMARY KEY NOT NULL,ecosystem TEXT,isEthereumBased INTEGER,isEthereumChain INTEGER,isTestNet INTEGER,history_type TEXT)")
            db.execSQL("INSERT INTO chains VALUES(?,'Substrate',0,0,0,NULL)", arrayOf(CHAIN))
            db.execSQL(
                "CREATE TABLE `chain_nodes` (`chainId` TEXT NOT NULL, `url` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`isActive` INTEGER NOT NULL, `isDefault` INTEGER NOT NULL, PRIMARY KEY(`chainId`, `url`), " +
                "FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL("CREATE INDEX `index_chain_nodes_chainId` ON `chain_nodes` (`chainId`)")
            db.execSQL("INSERT INTO chain_nodes VALUES(?,'wss://default.invalid/','Default',1,1)", arrayOf(CHAIN))
            db.execSQL(OWNED_ENDPOINT_CREATE)

            db.execSQL(OWNED_ENDPOINT_INDEX_CREATE)
            db.execSQL("INSERT INTO custom_endpoint_records VALUES(-7,8,?,'wss://owned.invalid/','Original',0,0,-7)", arrayOf(CHAIN))
            db.execSQL(EndpointRecoveryDecisionMigration.CREATE)

            db.execSQL(EndpointRecoveryDecisionMigration.CREATE_INDEX)
            db.version = 83
            val decisions = EndpointRecoveryDecisionStore(db)
            val signal = CancellationSignal()
            val decision = decisions.saveOffline(
                signal, decisions.prepareSource(signal, EndpointRecordRef(EndpointInventorySource.OWNED, -7, 8)),
                EndpointDecisionCandidate("Candidate 雪", "wss://candidate.invalid/Exact?token=synthetic"),
                checkNotNull(decisions.prepareTarget(signal, CHAIN)), null, EndpointDecisionAuthority()
            )
            action(Fixture(db, decision))
            assertFalse(db.inTransaction())
        }
    }

    private fun scalar(db: SupportSQLiteDatabase, sql: String): Long = db.query(sql).use {
        check(it.moveToFirst())

        it.getLong(0).also { _ -> check(!it.moveToNext()) }
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

    private companion object { val CHAIN = "1".repeat(64) }
}
