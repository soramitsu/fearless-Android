package jp.co.soramitsu.coredb.recovery.decision

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.os.CancellationSignal
import android.os.OperationCanceledException
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
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
import java.util.concurrent.atomic.AtomicReference
import jp.co.soramitsu.coredb.APP_DATABASE_VERSION
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.migrations.EndpointArchiveFormat
import jp.co.soramitsu.coredb.migrations.EndpointRecoveryDecisionMigration
import jp.co.soramitsu.coredb.migrations.FiatCurrencyIdentityMigration
import jp.co.soramitsu.coredb.recovery.EndpointInventorySource
import jp.co.soramitsu.coredb.recovery.EndpointRecordRef
import jp.co.soramitsu.coredb.recovery.OWNED_ENDPOINT_CREATE
import jp.co.soramitsu.coredb.recovery.OWNED_ENDPOINT_INDEX_CREATE
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Only exclusively owned core-test databases; no app files, wallet secrets or network. */
class EndpointRecoveryDecisionStoreInstrumentedTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val names = mutableListOf<String>()
    @get:Rule val migrations = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java, emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )
    @After fun cleanup() { names.forEach(context::deleteDatabase) }

    @Test fun allFiveSourcesWithSameSignedIdPersistSeparatePendingDecisionsAndLeaveRawSqlExact() = fixture { db ->
        val store = EndpointRecoveryDecisionStore(db)
        val before = image(db)
        val target = checkNotNull(store.prepareTarget(signal(), CHAIN))
        for (source in EndpointInventorySource.entries) {
            val binding = store.prepareSource(signal(), ref(source))
            val result = store.saveOffline(signal(), binding, candidate(), target, null, EndpointDecisionAuthority())
            assertEquals(1L, result.revision)
            assertEquals("PENDING", result.state)
            assertEquals(source, result.source.ref.source)
            assertEquals(candidate().url, store.read(signal(), source, ROW)!!.candidate.url)
        }
        assertEquals(5L, scalar(db, "SELECT COUNT(*) FROM endpoint_recovery_decisions"))
        assertEquals(before, image(db))
    }

    @Test fun updateKeepsStableIdAndChecksRevisionConflictAndOverflowWithoutChangingOtherSql() = fixture { db ->
        val store = EndpointRecoveryDecisionStore(db)
        val source = store.prepareSource(signal(), ref())
        val target = checkNotNull(store.prepareTarget(signal(), CHAIN))
        val first = store.saveOffline(signal(), source, candidate(), target, null, EndpointDecisionAuthority())
        val second = store.saveOffline(signal(), source, EndpointDecisionCandidate("Edited", "wss://offline.invalid/Edited"),
            target, first.revision, EndpointDecisionAuthority())
        assertEquals(first.id, second.id); assertEquals(2L, second.revision)
        val exact = image(db, includeDecisions = true)
        for (expected in listOf(null, 1L)) {
            assertThrows(IllegalStateException::class.java) {
                store.saveOffline(signal(), source, candidate(), target, expected, EndpointDecisionAuthority())
            }
            assertEquals(exact, image(db, includeDecisions = true))
        }
        db.execSQL("UPDATE endpoint_recovery_decisions SET revision=?", arrayOf(Long.MAX_VALUE))
        val exhausted = image(db, includeDecisions = true)
        assertThrows(IllegalStateException::class.java) {
            store.saveOffline(signal(), source, candidate(), target, Long.MAX_VALUE, EndpointDecisionAuthority())
        }
        assertEquals(exhausted, image(db, includeDecisions = true))
    }

    @Test fun sameRevisionPayloadOrStatusMutationAndNewOwnedRevisionRejectPreparedBinding() = fixture { db ->
        val store = EndpointRecoveryDecisionStore(db)
        val target = checkNotNull(store.prepareTarget(signal(), CHAIN))
        val source = store.prepareSource(signal(), ref())
        for (change in listOf("name='Changed'", "attached=1", "deleted=1", "revision=2")) {
            db.execSQL("UPDATE custom_endpoint_records SET name='Owned',attached=0,deleted=0,revision=1")
            db.execSQL("UPDATE custom_endpoint_records SET $change")
            val before = image(db, includeDecisions = true)
            assertThrows(IllegalStateException::class.java) {
                store.saveOffline(signal(), source, candidate(), target, null, EndpointDecisionAuthority())
            }
            assertEquals(before, image(db, includeDecisions = true))
        }
    }

    @Test fun capturedMissingParentRemainsPendingButChangedPresentParentRejects() = fixture { db ->
        val store = EndpointRecoveryDecisionStore(db)
        val source = store.prepareSource(signal(), ref())
        val target = checkNotNull(store.prepareTarget(signal(), CHAIN))
        db.execSQL("UPDATE chains SET isTestNet=1")
        assertThrows(IllegalStateException::class.java) {
            store.saveOffline(signal(), source, candidate(), target, null, EndpointDecisionAuthority())
        }
        db.execSQL("DELETE FROM chains")
        assertNull(store.prepareTarget(signal(), CHAIN))
        val before = image(db)
        val result = store.saveOffline(signal(), source, candidate(), target, null, EndpointDecisionAuthority())
        assertEquals("PENDING", result.state); assertEquals(CHAIN, result.target.chainId)
        assertEquals(before, image(db))
        db.query("PRAGMA foreign_key_list('endpoint_recovery_decisions')").use { assertFalse(it.moveToFirst()) }
    }

    @Test fun realInsertThenRevocationBeforeFinalGrantRollsBackEveryDecisionByte() = fixture { db ->
        val authority = EndpointDecisionAuthority()
        failAfterInsert(db, authority, afterInsert = { authority.revoke() }, expected = IllegalStateException::class.java)
    }

    @Test fun realInsertThenSqlFailureRollsBackAndFreshOperationCanRetry() = fixture { db ->
        failAfterInsert(db, EndpointDecisionAuthority(), { throw SQLiteException("synthetic decision SQL failure") },
            SQLiteException::class.java)
        val store = EndpointRecoveryDecisionStore(db)
        val result = store.saveOffline(signal(), store.prepareSource(signal(), ref()), candidate(),
            checkNotNull(store.prepareTarget(signal(), CHAIN)), null, EndpointDecisionAuthority())
        assertEquals(1L, result.revision)
    }

    @Test fun realInsertThenCancellationBeforeGrantRollsBack() = fixture { db ->
        val cancellation = signal()
        failAfterInsert(db, EndpointDecisionAuthority(), { cancellation.cancel() }, OperationCanceledException::class.java, cancellation)
    }

    @Test fun revocationAfterGrantReturnsWhilePhysicalCommitIsHeldAndResultHasNoAuthority() = fixture { db ->
        val ordinary = EndpointRecoveryDecisionStore(db)
        val source = ordinary.prepareSource(signal(), ref())
        val target = checkNotNull(ordinary.prepareTarget(signal(), CHAIN))
        val authority = EndpointDecisionAuthority()
        val granted = CountDownLatch(1); val release = CountDownLatch(1)
        val result = AtomicReference<EndpointRecoveryDecision?>()
        val failure = AtomicReference<Throwable?>()
        val held = object : SupportSQLiteDatabase by db {
            override fun endTransaction() {
                granted.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                db.endTransaction()
            }
        }
        val worker = Thread {
            try {
                result.set(EndpointRecoveryDecisionStore(held).saveOffline(signal(), source, candidate(), target, null, authority))
            } catch (caught: Throwable) { failure.set(caught) }
        }
        worker.start()
        try {
            assertTrue(granted.await(5, TimeUnit.SECONDS))
            authority.revoke()
            assertFalse(authority.isCurrent())
            assertTrue(worker.isAlive)
        } finally {
            release.countDown()
            worker.join(5_000)
        }
        assertFalse(worker.isAlive); assertNull(failure.get())
        assertEquals("PENDING", checkNotNull(result.get()).state)
        assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM endpoint_recovery_decisions"))
        assertThrows(IllegalStateException::class.java) {
            ordinary.saveOffline(signal(), source, candidate(), target, 1, authority)
        }
    }

    @Test fun malformedRawTextAndBlobTypedValuesRemainFingerprintableAndStorageClassChangesTheBinding() = fixture { db ->
        val store = EndpointRecoveryDecisionStore(db)
        db.execSQL("UPDATE custom_endpoint_records SET name=CAST(X'4EFF00' AS TEXT)")
        val text = store.prepareSource(signal(), ref())
        db.execSQL("UPDATE custom_endpoint_records SET name=X'4EFF00'")
        val blob = store.prepareSource(signal(), ref())
        assertNotEquals(text.fingerprint, blob.fingerprint)
        db.execSQL("UPDATE custom_endpoint_records SET name=CAST(X'4EFF00' AS TEXT)")
        assertEquals(text.fingerprint, store.prepareSource(signal(), ref()).fingerprint)
        val before = image(db)
        store.saveOffline(signal(), text, candidate(), checkNotNull(store.prepareTarget(signal(), CHAIN)),
            null, EndpointDecisionAuthority())
        assertEquals(before, image(db))
    }

    @Test fun aggregateFingerprintBudgetRejectsBeforeAnyPayloadQueryAndPreCanceledReadWritesNothing() = fixture { db ->
        db.execSQL("UPDATE custom_endpoint_records SET name=zeroblob(?),url=zeroblob(?)",
            arrayOf(4 * 1024 * 1024, 4 * 1024 * 1024))
        var payloadQueries = 0
        val audited = object : SupportSQLiteDatabase by db {
            override fun query(query: SupportSQLiteQuery, cancellationSignal: CancellationSignal?): Cursor {
                if (query.sql.contains("THEN substr(CAST(")) payloadQueries++
                return db.query(query, cancellationSignal)
            }
        }
        val before = image(db, includeDecisions = true)
        assertThrows(IllegalStateException::class.java) {
            EndpointRecoveryDecisionStore(audited).prepareSource(signal(), ref())
        }
        assertEquals(0, payloadQueries)
        assertThrows(OperationCanceledException::class.java) {
            EndpointRecoveryDecisionStore(db).prepareSource(signal().apply { cancel() }, ref())
        }
        assertEquals(before, image(db, includeDecisions = true))
    }

    @Test fun integerRealAndRawArchiveFlagsBindObservedTypedValuesAndCanceledChunkClosesCursor() {
        val fingerprints = mutableListOf<String>()
        val variants = listOf(arrayOf<Any?>(1, 999, -1), arrayOf<Any?>(1.5, 999, -1), arrayOf<Any?>(1.5, 0, -1))
        for (values in variants) emptyDatabase { db ->
            // Capture each typed source through production accounting; never forge archive metadata.
            seed(db, currentArchiveValues = values)
            val before = image(db, includeDecisions = true)
            fingerprints += EndpointRecoveryDecisionStore(db).prepareSource(signal(), ref(EndpointInventorySource.CURRENT)).fingerprint
            assertEquals(before, image(db, includeDecisions = true))
        }
        assertNotEquals(fingerprints[0], fingerprints[1])
        assertNotEquals(fingerprints[1], fingerprints[2])
        fixture { db ->
            db.execSQL("UPDATE custom_endpoint_records SET name=zeroblob(131072)")
            val cancellation = signal()
            var intercepted: Cursor? = null
            val audit = object : SupportSQLiteDatabase by db {
                override fun query(query: SupportSQLiteQuery, cancellationSignal: CancellationSignal?): Cursor {
                    val cursor = db.query(query, cancellationSignal)
                    if (query.sql.contains("THEN substr(CAST(")) {
                        intercepted = cursor
                        cancellation.cancel()
                    }
                    return cursor
                }
            }
            val before = image(db, includeDecisions = true)
            assertThrows(OperationCanceledException::class.java) {
                EndpointRecoveryDecisionStore(audit).prepareSource(cancellation, ref())
            }
            assertTrue(checkNotNull(intercepted).isClosed)
            assertFalse(db.inTransaction())
            assertEquals(before, image(db, includeDecisions = true))
        }
    }

    @Test fun changedRawArchivePayloadAccountingRejectsBeforeFingerprintAndLeavesSqlExact() = fixture { db ->
        db.execSQL("UPDATE current_endpoint_archive_v1 SET name=1,isDefault=999,isActive=-1")
        val before = image(db, includeDecisions = true)
        val failure = assertThrows(IllegalStateException::class.java) {
            EndpointRecoveryDecisionStore(db).prepareSource(signal(), ref(EndpointInventorySource.CURRENT))
        }
        assertEquals("Modern endpoint archive payload accounting changed", failure.message)
        assertFalse(db.inTransaction())
        assertEquals(before, image(db, includeDecisions = true))
    }

    @Test fun decisionUniqueKeyAndMalformedIntegerMetadataCannotBypassStoreAdmission() = fixture { db ->
        val store = EndpointRecoveryDecisionStore(db)
        val binding = store.prepareSource(signal(), ref())
        val target = checkNotNull(store.prepareTarget(signal(), CHAIN))
        store.saveOffline(signal(), binding, candidate(), target, null, EndpointDecisionAuthority())
        val before = image(db, includeDecisions = true)
        assertThrows(SQLiteException::class.java) {
            db.execSQL("INSERT INTO endpoint_recovery_decisions " +
                "(revision,sourceKind,sourceRowId,sourceRevision,sourceFingerprint,candidateName,candidateUrl," +
                "targetChainId,targetIdentityFingerprint,targetFamily,targetExpectedGenesis,state) " +
                "SELECT revision,sourceKind,sourceRowId,sourceRevision,sourceFingerprint,candidateName,candidateUrl," +
                "targetChainId,targetIdentityFingerprint,targetFamily,targetExpectedGenesis,state FROM endpoint_recovery_decisions")
        }
        assertEquals(before, image(db, includeDecisions = true))
        db.execSQL("UPDATE endpoint_recovery_decisions SET revision=CAST(zeroblob(4194304) AS TEXT)")
        val malformed = image(db, includeDecisions = true)
        assertThrows(IllegalStateException::class.java) {
            store.read(signal(), EndpointInventorySource.OWNED, ROW)
        }
        assertEquals(malformed, image(db, includeDecisions = true))
    }

    @Test fun unsafeDecisionTextAndExtraTriggerAreRejectedBeforeAnyNewMutation() = fixture { db ->
        val store = EndpointRecoveryDecisionStore(db)
        val binding = store.prepareSource(signal(), ref())
        val target = checkNotNull(store.prepareTarget(signal(), CHAIN))
        store.saveOffline(signal(), binding, candidate(), target, null, EndpointDecisionAuthority())
        db.execSQL("UPDATE endpoint_recovery_decisions SET candidateUrl=CAST(zeroblob(9000) AS TEXT)")
        assertThrows(IllegalStateException::class.java) { store.read(signal(), EndpointInventorySource.OWNED, ROW) }
        db.execSQL("UPDATE endpoint_recovery_decisions SET candidateUrl='wss://offline.invalid'")
        db.execSQL("CREATE TRIGGER unexpected_decision AFTER UPDATE ON endpoint_recovery_decisions BEGIN DELETE FROM custom_endpoint_records; END")
        val before = image(db, includeDecisions = true)
        assertThrows(IllegalStateException::class.java) {
            store.saveOffline(signal(), binding, candidate(), target, 1, EndpointDecisionAuthority())
        }
        assertEquals(before, image(db, includeDecisions = true))
    }

    @Test fun actualRoom82To83AndSecondOpenKeepOwnershipAndPersistOfflineDecisionWithoutParentFk() {
        val name = name()
        migrations.createDatabase(name, 82).use { db ->
            db.execSQL("INSERT INTO custom_endpoint_records(id,revision,chainId,url,name,attached,deleted,originalSnapshotRowId) " +
                "VALUES(-7,1,'orphan','wss://offline.invalid','Owned',0,0,NULL)")
        }
        repeat(2) {
            val room = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(EndpointRecoveryDecisionMigration, FiatCurrencyIdentityMigration).build()
            try {
                val db = room.openHelper.writableDatabase
                assertEquals(APP_DATABASE_VERSION, db.version)
                requireDecisionSchema(db, signal())
                assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM custom_endpoint_records"))
                db.query("PRAGMA foreign_key_list('endpoint_recovery_decisions')").use { cursor -> assertFalse(cursor.moveToFirst()) }
                val store = EndpointRecoveryDecisionStore(db)
                if (it == 0) {
                    assertEquals(0L, scalar(db, "SELECT COUNT(*) FROM endpoint_recovery_decisions"))
                    val before = image(db)
                    val source = store.prepareSource(signal(), ref())
                    // Capture from the real catalog projection before explicit fixture parent loss.
                    val chainFields = db.query("PRAGMA table_info(chains)").use { cursor ->
                        buildList { while (cursor.moveToNext()) {
                            add(cursor.getString(cursor.getColumnIndexOrThrow("name")) to
                                cursor.getString(cursor.getColumnIndexOrThrow("type")))
                        } }
                    }
                    val values = chainFields.map { (name, type) ->
                        val value: Any? = when (name) {
                            "id" -> CHAIN
                            "ecosystem" -> "Substrate"
                            else -> if (type == "INTEGER") 0 else ""
                        }
                        value
                    }.toTypedArray()
                    db.execSQL("INSERT INTO chains (${chainFields.joinToString { "`${it.first}`" }}) " +
                        "VALUES (${chainFields.joinToString { "?" }})", values)
                    val target = checkNotNull(store.prepareTarget(signal(), CHAIN))
                    db.execSQL("DELETE FROM chains WHERE id=?", arrayOf(CHAIN))
                    val decision = store.saveOffline(signal(), source, candidate(), target, null, EndpointDecisionAuthority())
                    assertEquals(1L, decision.revision)
                    assertEquals(before, image(db))
                } else {
                    val decision = checkNotNull(store.read(signal(), EndpointInventorySource.OWNED, ROW))
                    assertEquals(1L, decision.revision); assertEquals(candidate().url, decision.candidate.url)
                    assertEquals(CHAIN, decision.target.chainId)
                    assertEquals(0L, scalar(db, "SELECT COUNT(*) FROM chains"))
                    assertEquals("PENDING", decision.state)
                }
            } finally { room.close() }
        }
    }

    @Test fun freshRoom83ExactDecisionSchemaMatchesDirectMigrationDefinition() {
        val room = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val db = room.openHelper.writableDatabase
            assertEquals(APP_DATABASE_VERSION, db.version)
            requireDecisionSchema(db, signal())
            val actual = db.query("SELECT sql FROM main.sqlite_master WHERE name='endpoint_recovery_decisions'").use {
                check(it.moveToFirst()); it.getString(0)
            }
            assertEquals(EndpointRecoveryDecisionMigration.CREATE, actual)
            assertNull(EndpointRecoveryDecisionStore(db).read(signal(), EndpointInventorySource.CURRENT, Long.MIN_VALUE))
        } finally { room.close() }
    }

    @Test fun migrationCollisionAndSecondDdlFailureLeaveSchemaExactAndHealthyRetrySucceeds() = emptyDatabase { db ->
        db.version = 82
        for (schema in listOf("main", "temp")) {
            db.execSQL("CREATE TABLE $schema.endpoint_recovery_decisions(marker INTEGER)")
            val before = image(db, includeDecisions = true)
            db.beginTransaction()
            try {
                assertThrows(IllegalStateException::class.java) { EndpointRecoveryDecisionMigration.migrate(db) }
            } finally { db.endTransaction() }
            assertEquals(before, image(db, includeDecisions = true))
            db.execSQL("DROP TABLE $schema.endpoint_recovery_decisions")
        }
        val before = image(db, includeDecisions = true)
        var injected = false
        val sentinel = SQLiteException("synthetic second decision DDL")
        val audited = object : SupportSQLiteDatabase by db {
            override fun execSQL(sql: String) {
                if (sql == EndpointRecoveryDecisionMigration.CREATE_INDEX) {
                    assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM main.sqlite_master WHERE name='endpoint_recovery_decisions'"))
                    injected = true
                    throw sentinel
                }
                db.execSQL(sql)
            }
        }
        db.beginTransaction()
        try {
            assertSame(sentinel, assertThrows(SQLiteException::class.java) { EndpointRecoveryDecisionMigration.migrate(audited) })
        } finally { db.endTransaction() }
        assertTrue(injected)
        assertEquals(before, image(db, includeDecisions = true))
        db.beginTransaction()
        try { EndpointRecoveryDecisionMigration.migrate(db); db.setTransactionSuccessful() } finally { db.endTransaction() }
        requireDecisionSchema(db, signal())
        assertEquals(82, db.version) // A migration function itself never assigns user_version.
    }

    private fun <T : Throwable> failAfterInsert(
        db: SupportSQLiteDatabase, authority: EndpointDecisionAuthority, afterInsert: () -> Unit,
        expected: Class<T>, cancellation: CancellationSignal = signal()
    ) {
        val ordinary = EndpointRecoveryDecisionStore(db)
        val binding = ordinary.prepareSource(signal(), ref())
        val target = checkNotNull(ordinary.prepareTarget(signal(), CHAIN))
        val before = image(db, includeDecisions = true)
        var inserted = false
        val audited = object : SupportSQLiteDatabase by db {
            override fun compileStatement(sql: String): SupportSQLiteStatement {
                val statement = db.compileStatement(sql)
                return object : SupportSQLiteStatement by statement {
                    override fun executeInsert(): Long = statement.executeInsert().also {
                        inserted = true
                        assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM endpoint_recovery_decisions"))
                        afterInsert()
                    }
                }
            }
        }
        assertThrows(expected) {
            EndpointRecoveryDecisionStore(audited).saveOffline(cancellation, binding, candidate(), target, null, authority)
        }
        assertTrue(inserted); assertFalse(db.inTransaction())
        assertEquals(before, image(db, includeDecisions = true))
    }

    private fun fixture(block: (SupportSQLiteDatabase) -> Unit) = emptyDatabase { db ->
            seed(db)
            block(db)
    }

    private fun emptyDatabase(block: (SupportSQLiteDatabase) -> Unit) {
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name()).callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Unexpected fixture upgrade")
            }).build())
        try {
            val db = helper.writableDatabase
            block(db)
        } finally { helper.close() }
    }

    private fun seed(db: SupportSQLiteDatabase, currentArchiveValues: Array<out Any?>? = null) {
            db.version = 28
            db.execSQL("CREATE TABLE nodes(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,name TEXT NOT NULL,link TEXT NOT NULL,networkType INTEGER NOT NULL,isDefault INTEGER NOT NULL,isActive INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE chain_nodes (chainId,url,name,isActive,isDefault)")
            db.execSQL(OWNED_ENDPOINT_CREATE); db.execSQL(OWNED_ENDPOINT_INDEX_CREATE)
            db.execSQL("INSERT INTO nodes VALUES(-7,'Legacy','wss://offline.invalid',999,0,1)")
            db.execSQL("INSERT INTO chain_nodes(rowid,chainId,url,name,isActive,isDefault) VALUES(-7,?,'wss://offline.invalid','Archive',1,0)", arrayOf(CHAIN))
            db.execSQL("INSERT INTO custom_endpoint_records VALUES(-7,1,?,'wss://offline.invalid','Owned',0,0,-7)", arrayOf(CHAIN))
            assertTrue(EndpointArchiveFormat.HISTORICAL.capture(db, 39))
            assertTrue(EndpointArchiveFormat.UPGRADE_ENTRY.capture(db, 39))
            if (currentArchiveValues != null) {
                require(currentArchiveValues.size == 3)
                db.execSQL("UPDATE chain_nodes SET name=?,isDefault=?,isActive=?", currentArchiveValues)
            }
            assertTrue(EndpointArchiveFormat.CURRENT.capture(db, 81))
            db.execSQL("CREATE TABLE chains(id TEXT PRIMARY KEY NOT NULL,ecosystem TEXT,isEthereumBased INTEGER,isEthereumChain INTEGER,isTestNet INTEGER,history_type TEXT)")
            db.execSQL("INSERT INTO chains VALUES(?,'Substrate',0,0,0,NULL)", arrayOf(CHAIN))
            db.version = 82
            db.beginTransaction()
            try { EndpointRecoveryDecisionMigration.migrate(db); db.setTransactionSuccessful() } finally { db.endTransaction() }
            db.version = 83
    }

    /** Independent typed streaming oracle; no large raw cell is materialized in a CursorWindow. */
    private fun image(db: SupportSQLiteDatabase, includeDecisions: Boolean = false): Map<String, List<String>> {
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

    private fun name(): String = "endpoint-decision-${UUID.randomUUID()}".also(names::add)
    private fun signal() = CancellationSignal()
    private fun ref(source: EndpointInventorySource = EndpointInventorySource.OWNED) =
        EndpointRecordRef(source, ROW, if (source == EndpointInventorySource.OWNED) 1L else null)
    private fun candidate() = EndpointDecisionCandidate("Exact 雪\u0000", "wss://offline.invalid/Path?Exact=Yes")
    private fun scalar(db: SupportSQLiteDatabase, sql: String): Long = db.query(sql).use { check(it.moveToFirst()); it.getLong(0) }
    private companion object { const val ROW = -7L; val CHAIN = "1".repeat(64) }
}
