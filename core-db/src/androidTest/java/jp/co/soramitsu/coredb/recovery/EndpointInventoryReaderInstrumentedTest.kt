package jp.co.soramitsu.coredb.recovery

import android.content.Context
import android.database.Cursor
import android.database.CursorWrapper
import android.database.sqlite.SQLiteDatabase
import android.os.CancellationSignal
import android.os.OperationCanceledException
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.APP_DATABASE_VERSION
import jp.co.soramitsu.coredb.migrations.EndpointArchiveFormat
import jp.co.soramitsu.coredb.migrations.UpgradeEntryEndpointArchive
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Actual SQLite in exclusively owned fixtures; no app database, preferences or network. */
class EndpointInventoryReaderInstrumentedTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val names = mutableListOf<String>()
    @After fun removeOwnedDatabases() { names.forEach(context::deleteDatabase) }

    @Test fun allFiveSourcesKeepCoincidentIdsAndExactValuesWithoutUrlDeduplication() = withDatabase { db ->
        seedAll(db, listOf(7))
        checkedRead(db) { audit ->
            val rows = EndpointInventorySource.values().map { source ->
                val page = EndpointInventoryReader(audit).readPage(CancellationSignal(), source)
                assertTrue(page.present); assertEquals(1, page.records.size)
                val row = page.records.single(); assertEquals(source, row.ref.source); assertEquals(7L, row.ref.rowId)
                assertEquals("Name 7 雪\u0000", (row.values.getValue(EndpointField.NAME) as EndpointValue.Text).value)
                assertEquals("wss://offline.invalid/7?Exact=Yes", (row.values.getValue(EndpointField.ENDPOINT) as EndpointValue.Text).value)
                if (source == EndpointInventorySource.LEGACY) {
                    assertEquals(EndpointValue.Integer(999), row.values[EndpointField.NETWORK_TYPE]); assertNull(row.ownership)
                } else assertEquals("chain-7", (row.values.getValue(EndpointField.CHAIN_REFERENCE) as EndpointValue.Text).value)
                if (source == EndpointInventorySource.OWNED) {
                    assertEquals(1L, row.ref.revision); assertEquals(EndpointOwnership(false, false, 7), row.ownership)
                    assertFalse(EndpointField.IS_ACTIVE in row.values)
                }
                row
            }
            assertEquals(5, rows.map { it.ref }.toSet().size)
        }
    }

    @Test fun absentEmptyAndMissingRowsRemainDistinctForEverySource() = withDatabase { db ->
        checkedRead(db) { audit -> EndpointInventorySource.values().forEach { source ->
            val page = EndpointInventoryReader(audit).readPage(CancellationSignal(), source)
            assertFalse(page.present)
            assertEquals(EndpointChunkState.SOURCE_ABSENT, EndpointValueChunkReader(audit)
                .readChunk(CancellationSignal(), ref(source, 1), EndpointField.NAME).state)
        } }
        seedAll(db, emptyList())
        checkedRead(db) { audit -> EndpointInventorySource.values().forEach { source ->
            val page = EndpointInventoryReader(audit).readPage(CancellationSignal(), source)
            assertTrue(page.present); assertTrue(page.records.isEmpty()); assertNull(page.nextAfterRowId)
            assertEquals(EndpointChunkState.ROW_ABSENT, EndpointValueChunkReader(audit)
                .readChunk(CancellationSignal(), ref(source, 1), EndpointField.NAME).state)
        } }
    }

    @Test fun signedMinimumMaximumAndBothKeysetDirectionsWorkForAllSources() = withDatabase { db ->
        val ids = listOf(Long.MIN_VALUE, -7L, 0L, 2L, Long.MAX_VALUE); seedAll(db, ids)
        checkedRead(db) { audit -> EndpointInventorySource.values().forEach { source ->
            val reader = EndpointInventoryReader(audit)
            val first = reader.readPage(CancellationSignal(), source, pageSize = 2)
            assertEquals(ids.take(2), first.records.map { it.ref.rowId }); assertNull(first.previousBeforeRowId)
            val middle = reader.readPage(CancellationSignal(), source, afterExclusiveRowId = first.nextAfterRowId, pageSize = 2)
            assertEquals(ids.subList(2, 4), middle.records.map { it.ref.rowId })
            val last = reader.readPage(CancellationSignal(), source, afterExclusiveRowId = middle.nextAfterRowId, pageSize = 2)
            assertEquals(listOf(Long.MAX_VALUE), last.records.map { it.ref.rowId }); assertNull(last.nextAfterRowId)
            assertEquals(middle, reader.readPage(CancellationSignal(), source, beforeExclusiveRowId = last.previousBeforeRowId, pageSize = 2))
            assertEquals(first, reader.readPage(CancellationSignal(), source, beforeExclusiveRowId = middle.previousBeforeRowId, pageSize = 2))
            assertTrue(reader.readPage(CancellationSignal(), source, afterExclusiveRowId = Long.MAX_VALUE).records.isEmpty())
            assertTrue(reader.readPage(CancellationSignal(), source, beforeExclusiveRowId = Long.MIN_VALUE).records.isEmpty())
        } }
    }

    @Test fun allRawStorageClassesAndMalformedFlagBytesStayTypedAndInspectable() = withDatabase { db ->
        createSources(db)
        listOf("NULL", "-9223372036854775808", "1.25", "CAST(X'4EFF00' AS TEXT)", "X'00FF'").forEachIndexed { i, literal ->
            db.execSQL("INSERT INTO chain_nodes(rowid,chainId,url,name,isActive,isDefault) VALUES(${i + 1},$literal,$literal,$literal,$literal,$literal)")
        }
        captureAll(db)
        checkedRead(db) { audit -> MODERN.forEach { source ->
            val page = EndpointInventoryReader(audit).readPage(CancellationSignal(), source)
            assertEquals(5, page.records.size)
            page.records.forEachIndexed { index, row -> row.values.forEach { (field, value) ->
                val chunk = EndpointValueChunkReader(audit).readChunk(CancellationSignal(), row.ref, field)
                when (index) {
                    0 -> { assertEquals(EndpointValue.Null, value); assertEquals(EndpointChunkState.NULL_VALUE, chunk.state) }
                    1 -> { assertEquals(EndpointValue.Integer(Long.MIN_VALUE), value); assertEquals(Long.MIN_VALUE, chunk.integerValue); assertNull(chunk.bytes) }
                    2 -> { assertEquals(EndpointValue.Real(1.25), value); assertEquals(1.25, checkNotNull(chunk.realValue), 0.0); assertNull(chunk.bytes) }
                    3 -> { assertEquals(EndpointValue.Text(3, EndpointTextState.INVALID_UTF8, null), value); assertArrayEquals(byteArrayOf(78, -1, 0), chunk.bytes) }
                    4 -> { assertEquals(EndpointValue.Blob(2), value); assertArrayEquals(byteArrayOf(0, -1), chunk.bytes) }
                }
            } }
        } }
    }

    @Test fun legacyRawNetworkAndFlagStorageIsInspectableWithoutOrdinalCoercion() = withDatabase { db ->
        createSources(db)
        db.execSQL("INSERT INTO nodes VALUES(1,CAST(X'80' AS TEXT),X'00FF','unknown-network',X'01FF',1.25)")
        checkedRead(db) { audit ->
            val row = EndpointInventoryReader(audit).readPage(CancellationSignal(), EndpointInventorySource.LEGACY).records.single()
            assertEquals(EndpointValue.Text(15, EndpointTextState.AVAILABLE, "unknown-network"), row.values[EndpointField.NETWORK_TYPE])
            assertEquals(EndpointValue.Real(1.25), row.values[EndpointField.IS_ACTIVE])
            assertArrayEquals(byteArrayOf(1, -1), EndpointValueChunkReader(audit)
                .readChunk(CancellationSignal(), row.ref, EndpointField.IS_DEFAULT).bytes)
            assertArrayEquals("unknown-network".toByteArray(), EndpointValueChunkReader(audit)
                .readChunk(CancellationSignal(), row.ref, EndpointField.NETWORK_TYPE).bytes)
        }
    }

    @Test fun aggregateRowsAboveCursorWindowCapacityUseOnlyBoundedPageAndChunkPayloads() = withDatabase { db ->
        val text = "A".repeat(1_048_576); seedAll(db, listOf(1), text)
        checkedRead(db) { audit -> EndpointInventorySource.values().forEach { source ->
            val row = EndpointInventoryReader(audit).readPage(CancellationSignal(), source).records.single()
            listOf(EndpointField.NAME, EndpointField.ENDPOINT).forEach { field ->
                assertEquals(EndpointValue.Text(1_048_576, EndpointTextState.OVERSIZED, null), row.values[field])
                listOf(0L, 512L, 1_048_539L, 1_048_576L).forEach { offset ->
                    val part = EndpointValueChunkReader(audit).readChunk(CancellationSignal(), row.ref, field, offset)
                    assertArrayEquals(ByteArray(minOf(512L, 1_048_576L - offset).toInt()) { 65 }, part.bytes)
                }
            }
        } }
    }

    @Test fun exact2048ByteTextBoundaryAndSplitUtf8ChunksRemainExact() = withDatabase { db ->
        val exact = "雪".repeat(682) + "é"; seedAll(db, listOf(1), exact)
        checkedRead(db) { audit -> EndpointInventorySource.values().forEach { source ->
            val row = EndpointInventoryReader(audit).readPage(CancellationSignal(), source).records.single()
            assertEquals(EndpointValue.Text(2048, EndpointTextState.AVAILABLE, exact), row.values[EndpointField.NAME])
            val first = EndpointValueChunkReader(audit).readChunk(CancellationSignal(), row.ref, EndpointField.NAME)
            val second = EndpointValueChunkReader(audit).readChunk(CancellationSignal(), row.ref, EndpointField.NAME, 512)
            assertArrayEquals(exact.toByteArray().copyOfRange(0, 512), first.bytes)
            assertArrayEquals(exact.toByteArray().copyOfRange(512, 1024), second.bytes)
        } }
    }

    @Test fun emptyTextAndBlobHaveExactZeroLengthPartsWithoutInventingNull() = withDatabase { db ->
        createSources(db)
        db.execSQL("INSERT INTO nodes VALUES(1,'',X'',0,0,0)")
        db.execSQL("INSERT INTO chain_nodes VALUES('',X'','',0,0)")
        db.execSQL("INSERT INTO custom_endpoint_records VALUES(1,1,'',X'','',0,0,NULL)")
        captureAll(db)
        checkedRead(db) { audit -> EndpointInventorySource.values().forEach { source ->
            val row = EndpointInventoryReader(audit).readPage(CancellationSignal(), source).records.single()
            assertEquals(EndpointValue.Text(0, EndpointTextState.AVAILABLE, ""), row.values[EndpointField.NAME])
            assertEquals(EndpointValue.Blob(0), row.values[EndpointField.ENDPOINT])
            listOf(EndpointField.NAME, EndpointField.ENDPOINT).forEach { field ->
                val part = EndpointValueChunkReader(audit).readChunk(CancellationSignal(), row.ref, field)
                assertEquals(EndpointChunkState.AVAILABLE, part.state); assertArrayEquals(byteArrayOf(), part.bytes)
                assertEquals(0L, part.totalByteLength); assertNull(part.nextByteOffset); assertNull(part.previousByteOffset)
            }
        } }
    }

    @Test fun pageLimitIs25ByDefaultAnd50AtMaximumAcrossAllSources() = withDatabase { db ->
        seedAll(db, (1L..51L).toList())
        checkedRead(db) { audit -> EndpointInventorySource.values().forEach { source ->
            val reader = EndpointInventoryReader(audit)
            val default = reader.readPage(CancellationSignal(), source)
            assertEquals((1L..25L).toList(), default.records.map { it.ref.rowId }); assertEquals(25L, default.nextAfterRowId)
            val maximum = reader.readPage(CancellationSignal(), source, pageSize = 50)
            assertEquals((1L..50L).toList(), maximum.records.map { it.ref.rowId }); assertEquals(50L, maximum.nextAfterRowId)
            val tail = reader.readPage(CancellationSignal(), source, afterExclusiveRowId = maximum.nextAfterRowId, pageSize = 50)
            assertEquals(listOf(51L), tail.records.map { it.ref.rowId }); assertNull(tail.nextAfterRowId)
        } }
    }

    @Test fun utf16DatabaseIsRejectedBeforeAnySourcePayloadRead() = withDatabase(encoding = "UTF-16le") { db ->
        db.query("PRAGMA encoding").use { assertTrue(it.moveToFirst()); assertEquals("UTF-16le", it.getString(0)) }
        checkedRead(db) { audit -> EndpointInventorySource.values().forEach { source ->
            val error = assertThrows(IllegalStateException::class.java) {
                EndpointInventoryReader(audit).readPage(CancellationSignal(), source)
            }
            assertTrue(checkNotNull(error.message).contains("requires UTF-8 database encoding"))
            val chunkError = assertThrows(IllegalStateException::class.java) {
                EndpointValueChunkReader(audit).readChunk(CancellationSignal(), ref(source, 1), EndpointField.NAME)
            }
            assertTrue(checkNotNull(chunkError.message).contains("requires UTF-8 database encoding"))
        }
            assertEquals(List(10) { "PRAGMA encoding" }, audit.queries)
        }
    }

    @Test fun entryReadUsesStoredFirstEdgeAt82WhileMigrationValidationStaysStrict() {
        listOf(28 to 39, 39 to 39, 40 to 41, 71 to 76, 76 to 76).forEach { (original, edge) -> withDatabase { db ->
            createSources(db); db.version = original
            db.execSQL("INSERT INTO chain_nodes VALUES('chain','url','name',0,0)")
            assertTrue(EndpointArchiveFormat.UPGRADE_ENTRY.capture(db, edge)); db.version = 82
            checkedRead(db) { audit ->
                val page = EndpointInventoryReader(audit).readPage(CancellationSignal(), EndpointInventorySource.UPGRADE_ENTRY)
                assertEquals(EndpointSourceProvenance(edge, original), page.provenance)
                assertEquals("name", (page.records.single().values[EndpointField.NAME] as EndpointValue.Text).value)
            }
            val before = fingerprint(db.path)
            val rejected = assertThrows(IllegalStateException::class.java) { UpgradeEntryEndpointArchive.requireSafeForSource(db) }
            assertEquals("Upgrade-entry endpoint snapshot is not valid for this source version", rejected.message)
            assertEquals(before, fingerprint(db.path))
        } }
    }

    @Test fun entryReadRejectsInternallyWrongOrImpossibleProvenanceWithoutChangingBytes() {
        listOf("source_user_version=77", "source_user_version=40", "source_edge_version=41", "source_user_version='invalid'").forEach { update ->
            withDatabase { db -> seedAll(db, listOf(1)); db.execSQL("UPDATE upgrade_entry_endpoint_archive_header_v1 SET $update")
                checkedRead(db) { audit ->
                    assertThrows(IllegalStateException::class.java) { EndpointInventoryReader(audit)
                        .readPage(CancellationSignal(), EndpointInventorySource.UPGRADE_ENTRY) }
                    assertTrue(EndpointInventoryReader(audit).readPage(CancellationSignal(), EndpointInventorySource.HISTORICAL).present)
                }
            }
        }
    }

    @Test fun pendingDeletedAndMaximumRevisionOwnershipRemainDistinctFromRawFlags() = withDatabase { db ->
        seedAll(db, listOf(1, 2, 3))
        db.execSQL("UPDATE custom_endpoint_records SET revision=9223372036854775807,attached=1 WHERE id=1")
        db.execSQL("UPDATE custom_endpoint_records SET deleted=1 WHERE id=3")
        checkedRead(db) { audit ->
            val rows = EndpointInventoryReader(audit).readPage(CancellationSignal(), EndpointInventorySource.OWNED).records
            assertEquals(listOf(EndpointOwnership(true, false, 1), EndpointOwnership(false, false, 2), EndpointOwnership(false, true, 3)), rows.map { it.ownership })
            assertEquals(Long.MAX_VALUE, rows.first().ref.revision)
            assertEquals(Long.MAX_VALUE, EndpointValueChunkReader(audit)
                .readChunk(CancellationSignal(), rows.first().ref, EndpointField.NAME).observedRevision)
        }
    }

    @Test fun editDetachDeleteAndRevivalInvalidateEarlierPageAndAdjacentChunkReferences() = withDatabase { db ->
        seedAll(db, listOf(1), "a".repeat(1200))
        val reader = EndpointInventoryReader(db)
        val ref = reader.readPage(CancellationSignal(), EndpointInventorySource.OWNED).records.single().ref
        assertEquals(512, checkNotNull(EndpointValueChunkReader(db).readChunk(CancellationSignal(), ref, EndpointField.NAME).bytes).size)
        listOf("name='edited',revision=2", "attached=0,revision=3", "deleted=1,revision=4", "attached=1,deleted=0,revision=5").forEachIndexed { index, update ->
            db.execSQL("UPDATE custom_endpoint_records SET $update WHERE id=1")
            checkedRead(db) { audit ->
                val result = EndpointValueChunkReader(audit).readChunk(CancellationSignal(), ref, EndpointField.NAME, 512)
                assertEquals(EndpointChunkState.STALE_REVISION, result.state); assertEquals(ref, result.ref)
                assertEquals(index + 2L, result.observedRevision); assertNull(result.bytes); assertNull(result.ownership)
                assertEquals(1, audit.queries.count { it.startsWith("SELECT id AS endpoint_rowid") })
            }
        }
    }

    @Test fun writerBetweenAdmissionAndChunkQueryCannotExposeNewRevisionPayload() = withDatabase { db ->
        seedAll(db, listOf(1)); val ref = EndpointRecordRef(EndpointInventorySource.OWNED, 1, 1)
        SQLiteDatabase.openDatabase(checkNotNull(db.path), null, SQLiteDatabase.OPEN_READWRITE).use { writer ->
            db.execSQL("PRAGMA query_only=ON")
            try {
                val audit = AuditedQueries(db)
                audit.beforeQuery = { sql, _ -> if (sql.startsWith("SELECT id AS endpoint_rowid")) {
                    writer.execSQL("UPDATE custom_endpoint_records SET name='new payload',revision=2 WHERE id=1")
                } }
                val chunk = EndpointValueChunkReader(audit).readChunk(CancellationSignal(), ref, EndpointField.NAME)
                assertEquals(EndpointChunkState.STALE_REVISION, chunk.state); assertNull(chunk.bytes)
                assertEquals(2L, chunk.observedRevision)
                assertEquals(1, audit.queries.count { it.startsWith("SELECT id AS endpoint_rowid") })
            } finally { db.execSQL("PRAGMA query_only=OFF") }
        }
    }

    @Test fun malformedOwnedScalarMetadataFailsBoundedlyWithoutPayloadOrCoercion() {
        listOf("revision=CAST(zeroblob(2097153) AS TEXT)", "revision=0", "attached=2", "attached=1,deleted=1",
            "originalSnapshotRowId=X'FF'").forEach { update -> withDatabase { db ->
            seedAll(db, listOf(1)); db.execSQL("UPDATE custom_endpoint_records SET $update")
            checkedRead(db) { audit ->
                assertThrows(IllegalStateException::class.java) { EndpointInventoryReader(audit)
                    .readPage(CancellationSignal(), EndpointInventorySource.OWNED) }
                assertThrows(IllegalStateException::class.java) { EndpointValueChunkReader(audit)
                    .readChunk(CancellationSignal(), EndpointRecordRef(EndpointInventorySource.OWNED, 1, 1), EndpointField.NAME) }
            }
        } }
    }

    @Test fun partialExtraAndTemporaryOwnedSchemaAreLocalReadErrors() {
        listOf("DROP INDEX index_custom_endpoint_records_chainId_url",
            "CREATE TRIGGER unexpected_owned_trigger AFTER UPDATE ON custom_endpoint_records BEGIN SELECT 1; END",
            "CREATE TEMP TABLE custom_endpoint_records(value)").forEach { change -> withDatabase { db ->
            seedAll(db, listOf(1)); db.execSQL(change)
            checkedRead(db) { audit ->
                assertThrows(IllegalStateException::class.java) { EndpointInventoryReader(audit)
                    .readPage(CancellationSignal(), EndpointInventorySource.OWNED) }
                assertEquals(1, EndpointInventoryReader(audit).readPage(CancellationSignal(), EndpointInventorySource.LEGACY).records.size)
            }
        } }
    }

    @Test fun cancellationAtAdmissionPageAndChunkNeverPublishesPartialDataAndClosesCursors() = withDatabase { db ->
        seedAll(db, listOf(1))
        checkedRead(db) { audit ->
            val cancelled = CancellationSignal().also { it.cancel() }
            assertThrows(OperationCanceledException::class.java) { EndpointInventoryReader(audit).readPage(cancelled, EndpointInventorySource.OWNED) }
            assertTrue(audit.queries.isEmpty())
        }
        listOf("admission", "page", "chunk").forEach { stage -> checkedRead(db) { audit ->
            val signal = CancellationSignal(); var intercepted: Cursor? = null
            audit.beforeQuery = { sql, supplied ->
                assertSame(signal, supplied)
                if (stage == "admission" && sql.contains("FROM main.sqlite_master")) supplied.cancel()
            }
            audit.wrapCursor = { sql, cursor ->
                if (stage != "admission" && sql.startsWith("SELECT id AS endpoint_rowid")) object : CursorWrapper(cursor) {
                    override fun getBlob(columnIndex: Int): ByteArray = super.getBlob(columnIndex).also { signal.cancel() }
                }.also { intercepted = it } else cursor
            }
            assertThrows(OperationCanceledException::class.java) {
                if (stage == "chunk") EndpointValueChunkReader(audit).readChunk(signal,
                    EndpointRecordRef(EndpointInventorySource.OWNED, 1, 1), EndpointField.NAME)
                else EndpointInventoryReader(audit).readPage(signal, EndpointInventorySource.OWNED)
            }
            if (stage != "admission") assertTrue(checkNotNull(intercepted).isClosed)
        } }
    }

    @Test fun freshCurrentRoomOwnedSchemaIsAdmittedWithoutMigrationSpecificDdlAssumptions() {
        val room = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val db = room.openHelper.writableDatabase
            assertEquals(APP_DATABASE_VERSION, db.version)
            val page = EndpointInventoryReader(db).readPage(CancellationSignal(), EndpointInventorySource.OWNED)
            assertTrue(page.present); assertTrue(page.records.isEmpty())
        } finally { room.close() }
    }

    private fun createSources(db: SupportSQLiteDatabase) {
        db.version = 28
        db.execSQL("CREATE TABLE nodes(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,name TEXT NOT NULL,link TEXT NOT NULL,networkType INTEGER NOT NULL,isDefault INTEGER NOT NULL,isActive INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE chain_nodes (chainId,url,name,isActive,isDefault)")
        db.execSQL(OWNED_ENDPOINT_CREATE); db.execSQL(OWNED_ENDPOINT_INDEX_CREATE)
    }
    private fun seedAll(db: SupportSQLiteDatabase, ids: List<Long>, text: String? = null) {
        createSources(db)
        ids.forEach { id ->
            val name = text ?: "Name $id 雪\u0000"; val url = text ?: "wss://offline.invalid/$id?Exact=Yes"; val chain = text ?: "chain-$id"
            db.execSQL("INSERT INTO nodes VALUES(?,?,?,?,?,?)", arrayOf(id, name, url, 999, 0, 1))
            db.execSQL("INSERT INTO chain_nodes(rowid,chainId,url,name,isActive,isDefault) VALUES(?,?,?,?,1,0)", arrayOf(id, chain, url, name))
            db.execSQL("INSERT INTO custom_endpoint_records(id,revision,chainId,url,name,attached,deleted,originalSnapshotRowId) VALUES(?,1,?,?,?,0,0,?)", arrayOf(id, chain, url, name, id))
        }
        captureAll(db)
    }
    private fun captureAll(db: SupportSQLiteDatabase) {
        assertTrue(EndpointArchiveFormat.HISTORICAL.capture(db, 39))
        assertTrue(EndpointArchiveFormat.UPGRADE_ENTRY.capture(db, 39))
        assertTrue(EndpointArchiveFormat.CURRENT.capture(db, 81))
        db.version = 82
    }
    private fun ref(source: EndpointInventorySource, id: Long) = EndpointRecordRef(source, id,
        if (source == EndpointInventorySource.OWNED) 1 else null)
    private fun withDatabase(encoding: String? = null, block: (SupportSQLiteDatabase) -> Unit) {
        val name = "endpoint-inventory-${UUID.randomUUID()}"; names += name
        if (encoding != null) {
            check(encoding == "UTF-16le")
            val path = context.getDatabasePath(name)
            check(path.parentFile?.let { it.isDirectory || it.mkdirs() } == true)
            // Establish encoding before Android creates its locale metadata table.
            SQLiteDatabase.openDatabase(path.path, null,
                SQLiteDatabase.CREATE_IF_NECESSARY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { seed ->
                seed.execSQL("PRAGMA encoding='UTF-16le'")
                seed.execSQL("CREATE TABLE encoding_fixture_marker(value INTEGER)")
                seed.version = 1
            }
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name).callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Unexpected fixture upgrade")
            }).build())
        try { block(helper.writableDatabase) } finally { helper.close() }
    }
    private fun checkedRead(db: SupportSQLiteDatabase, block: (AuditedQueries) -> Unit) {
        val before = fingerprint(db.path)
        db.execSQL("PRAGMA query_only=ON")
        try { block(AuditedQueries(db)) } finally { db.execSQL("PRAGMA query_only=OFF") }
        assertEquals(before, fingerprint(db.path))
    }
    private fun fingerprint(path: String?): Map<String, String> = listOf("", "-wal").mapNotNull { suffix ->
        val file = File(checkNotNull(path) + suffix)
        if (!file.exists()) null else {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input -> val buffer = ByteArray(16 * 1024)
                while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) } }
            suffix to digest.digest().joinToString("") { "%02x".format(it) }
        }
    }.toMap()
    private class AuditedQueries(private val db: SupportSQLiteDatabase) : SupportSQLiteDatabase by db {
        val queries = mutableListOf<String>()
        var beforeQuery: (String, CancellationSignal) -> Unit = { _, _ -> }
        var wrapCursor: (String, Cursor) -> Cursor = { _, cursor -> cursor }
        override fun query(query: SupportSQLiteQuery, cancellationSignal: CancellationSignal?): Cursor {
            val signal = checkNotNull(cancellationSignal); queries += query.sql; beforeQuery(query.sql, signal)
            return wrapCursor(query.sql, db.query(query, signal))
        }
        override fun query(query: String): Cursor = error("Missing cancellation")
        override fun query(query: String, bindArgs: Array<out Any?>): Cursor = error("Missing cancellation")
        override fun query(query: SupportSQLiteQuery): Cursor = error("Missing cancellation")
        override fun execSQL(sql: String) = error("Unexpected reader mutation")
        override fun execSQL(sql: String, bindArgs: Array<out Any?>) = error("Unexpected reader mutation")
        override fun beginTransaction() = error("Unexpected reader transaction")
        override fun beginTransactionNonExclusive() = error("Unexpected reader transaction")
        override fun setTransactionSuccessful() = error("Unexpected reader transaction")
        override fun endTransaction() = error("Unexpected reader transaction")
        override fun close() = error("Unexpected connection close")
    }
    private companion object {
        val MODERN = listOf(EndpointInventorySource.HISTORICAL, EndpointInventorySource.UPGRADE_ENTRY, EndpointInventorySource.CURRENT)
    }
}
