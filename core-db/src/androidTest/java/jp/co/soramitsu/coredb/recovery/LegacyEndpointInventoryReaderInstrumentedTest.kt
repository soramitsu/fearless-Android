package jp.co.soramitsu.coredb.recovery

import android.database.Cursor
import android.database.CursorWrapper
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.os.CancellationSignal
import android.os.OperationCanceledException
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Draft: real SQLite, query_only enforcement and audited cancellation. No runtime result is claimed. */
class LegacyEndpointInventoryReaderInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val names = mutableListOf<String>()

    @After
    fun removeOnlyOwnedFixtureDatabases() { names.forEach(context::deleteDatabase) }

    @Test
    fun allFourReleasedCohortsReadExactTextWithoutChangingArchiveBytes() {
        COHORTS.forEach { (order, activeDefault) -> withDatabase { database ->
            database.createNodes(order, activeDefault)
            database.insert(23, "Saved 雪\u0000é", "wss://offline.invalid/Exact?Case=Yes", 15, 0, 1)
            val original = fingerprint(database.path)
            database.readOnly { audit ->
                val page = LegacyEndpointInventoryReader(audit).readPage(CancellationSignal())
                assertTrue(page.archivePresent)
                assertEquals(1, page.records.size)
                val row = page.records.single()
                assertEquals(23L, row.originalRowId)
                assertEquals("Saved 雪\u0000é", row.name.value)
                assertEquals("wss://offline.invalid/Exact?Case=Yes", row.endpoint.value)
                assertEquals(15L, row.networkType.value)
                assertEquals(0L, row.isDefault.value)
                assertEquals(1L, row.isActive.value)
                assertNull(page.previousBeforeRowId)
                assertNull(page.nextAfterRowId)
                assertTrue(audit.queries.any { it.contains("FROM pragma_table_info(") })
            }
            assertEquals(original, fingerprint(database.path))
        } }
    }

    @Test
    fun absentAndEmptyArchivesRemainDistinctWithoutInventingRows() = withDatabase { database ->
        database.readOnly { audit ->
            assertEquals(LegacyEndpointPage(false, emptyList(), null, null),
                LegacyEndpointInventoryReader(audit).readPage(CancellationSignal()))
        }
        database.createNodes()
        database.readOnly { audit ->
            assertEquals(LegacyEndpointPage(true, emptyList(), null, null),
                LegacyEndpointInventoryReader(audit).readPage(CancellationSignal()))
        }
    }

    @Test
    fun forwardAndBackwardPagesPreserveNegativeMinimumAndMaximumRowIds() = withDatabase { database ->
        database.createNodes()
        val ids = listOf(Long.MIN_VALUE, -9L, 0L, 2L, Long.MAX_VALUE)
        ids.forEach { database.insert(it) }
        database.readOnly { audit ->
            val reader = LegacyEndpointInventoryReader(audit)
            val first = reader.readPage(CancellationSignal(), pageSize = 2)
            assertEquals(ids.take(2), first.records.map { it.originalRowId })
            assertNull(first.previousBeforeRowId)
            assertEquals(-9L, first.nextAfterRowId)
            val middle = reader.readPage(CancellationSignal(), afterExclusiveRowId = first.nextAfterRowId, pageSize = 2)
            assertEquals(listOf(0L, 2L), middle.records.map { it.originalRowId })
            assertEquals(0L, middle.previousBeforeRowId)
            assertEquals(2L, middle.nextAfterRowId)
            val last = reader.readPage(CancellationSignal(), afterExclusiveRowId = middle.nextAfterRowId, pageSize = 2)
            assertEquals(listOf(Long.MAX_VALUE), last.records.map { it.originalRowId })
            assertEquals(Long.MAX_VALUE, last.previousBeforeRowId)
            assertNull(last.nextAfterRowId)
            val previous = reader.readPage(CancellationSignal(), beforeExclusiveRowId = last.previousBeforeRowId, pageSize = 2)
            assertEquals(middle, previous)
            assertEquals(first, reader.readPage(CancellationSignal(), beforeExclusiveRowId = previous.previousBeforeRowId, pageSize = 2))
            listOf(
                reader.readPage(CancellationSignal(), afterExclusiveRowId = Long.MAX_VALUE),
                reader.readPage(CancellationSignal(), beforeExclusiveRowId = Long.MIN_VALUE)
            ).forEach { page ->
                assertTrue(page.archivePresent)
                assertTrue(page.records.isEmpty())
                assertNull(page.previousBeforeRowId)
                assertNull(page.nextAfterRowId)
            }
        }
    }

    @Test
    fun oversizedTextAndBlobAreDisclosedWithoutCursorWindowPayloadOrLostLaterRow() = withDatabase { database ->
        database.createNodes()
        val bytes = 1_048_577
        database.execSQL("INSERT INTO nodes VALUES(1,CAST(zeroblob($bytes) AS TEXT),zeroblob($bytes),15,0,1)")
        database.insert(2, "Next", "wss://next.invalid")
        val original = fingerprint(database.path)
        database.readOnly { audit ->
            val reader = LegacyEndpointInventoryReader(audit)
            val first = reader.readPage(CancellationSignal(), pageSize = 1)
            val row = first.records.single()
            assertEquals(LegacyEndpointTextState.OVERSIZED, row.name.state)
            assertEquals(bytes.toLong(), row.name.byteLength)
            assertNull(row.name.value)
            assertEquals(LegacyEndpointStorage.BLOB, row.endpoint.storage)
            assertEquals(LegacyEndpointTextState.UNSUPPORTED_STORAGE, row.endpoint.state)
            assertEquals(bytes.toLong(), row.endpoint.byteLength)
            assertNull(row.endpoint.value)
            val next = reader.readPage(CancellationSignal(), afterExclusiveRowId = first.nextAfterRowId, pageSize = 1)
            assertEquals("wss://next.invalid", next.records.single().endpoint.value)
        }
        assertEquals(original, fingerprint(database.path))
    }

    @Test
    fun invalidUtf8AndUnsupportedOrdinalFlagStorageRemainExplicitAndOpaque() = withDatabase { database ->
        database.createNodes()
        database.execSQL("INSERT INTO nodes VALUES(7,CAST(X'80' AS TEXT),'offline','unknown-network',X'00FF',2)")
        database.readOnly { audit ->
            val row = LegacyEndpointInventoryReader(audit).readPage(CancellationSignal()).records.single()
            assertEquals(LegacyEndpointStorage.TEXT, row.name.storage)
            assertEquals(LegacyEndpointTextState.INVALID_UTF8, row.name.state)
            assertEquals(1L, row.name.byteLength)
            assertNull(row.name.value)
            assertEquals(LegacyEndpointStorage.TEXT, row.networkType.storage)
            assertEquals(15L, row.networkType.byteLength)
            assertNull(row.networkType.value)
            assertEquals(LegacyEndpointStorage.BLOB, row.isDefault.storage)
            assertEquals(2L, row.isDefault.byteLength)
            assertNull(row.isDefault.value)
            assertEquals(2L, row.isActive.value)
        }
    }

    @Test
    fun exactUtf8ByteBoundaryAndEmptyValuesDoNotNormalizeText() = withDatabase { database ->
        database.createNodes()
        val exact = "雪".repeat(682) + "é"
        assertEquals(2048, exact.toByteArray(Charsets.UTF_8).size)
        database.insert(1, "", exact)
        database.insert(2, "", exact + "x")
        database.readOnly { audit ->
            val rows = LegacyEndpointInventoryReader(audit).readPage(CancellationSignal()).records
            assertEquals("", rows[0].name.value)
            assertEquals(LegacyEndpointTextState.AVAILABLE, rows[0].name.state)
            assertEquals(exact, rows[0].endpoint.value)
            assertEquals(LegacyEndpointTextState.OVERSIZED, rows[1].endpoint.state)
            assertEquals(2049L, rows[1].endpoint.byteLength)
            assertNull(rows[1].endpoint.value)
        }
    }

    @Test
    fun largeArchiveUsesBoundedKeysetQueriesAndNoWholeCountOffsetOrPayloadList() = withDatabase { database ->
        database.createNodes()
        database.beginTransaction()
        try {
            repeat(1001) { database.insert(it.toLong() + 1) }
            database.setTransactionSuccessful()
        } finally { database.endTransaction() }
        database.readOnly { audit ->
            val reader = LegacyEndpointInventoryReader(audit)
            val first = reader.readPage(CancellationSignal(), pageSize = 50)
            assertEquals((1L..50L).toList(), first.records.map { it.originalRowId })
            val next = reader.readPage(CancellationSignal(), afterExclusiveRowId = first.nextAfterRowId, pageSize = 50)
            assertEquals((51L..100L).toList(), next.records.map { it.originalRowId })
            val pages = audit.queries.filter { it.startsWith("SELECT rowid AS archive_rowid") }
            assertEquals(2, pages.size)
            assertTrue(pages.all { it.endsWith("LIMIT 51") })
            assertTrue(pages[1].contains("WHERE rowid > ? ORDER BY rowid ASC"))
            assertFalse(audit.queries.any { Regex("\\b(COUNT|OFFSET)\\b", RegexOption.IGNORE_CASE).containsMatchIn(it) })
            assertFalse(audit.queries.any { it.contains("SELECT *", ignoreCase = true) })
        }
    }

    @Test
    fun preCancelledSignalAndInvalidPageArgumentsNeverStartMetadataQueries() = withDatabase { database ->
        database.createNodes()
        database.readOnly { audit ->
            val reader = LegacyEndpointInventoryReader(audit)
            val cancelled = CancellationSignal().apply { cancel() }
            assertThrows(OperationCanceledException::class.java) { reader.readPage(cancelled) }
            listOf(0, -1, 51).forEach { size ->
                assertThrows(IllegalArgumentException::class.java) { reader.readPage(CancellationSignal(), pageSize = size) }
            }
            assertThrows(IllegalArgumentException::class.java) {
                reader.readPage(CancellationSignal(), afterExclusiveRowId = 0, beforeExclusiveRowId = 2)
            }
            assertTrue(audit.queries.isEmpty())
        }
    }

    @Test
    fun cancellationDuringMetadataIsForwardedToActualSQLiteAndDoesNotBecomeAbsent() = withDatabase { database ->
        database.createNodes()
        val signal = CancellationSignal()
        database.readOnly { audit ->
            audit.beforeQuery = { _, supplied ->
                assertSame(signal, supplied)
                if (audit.queries.size == 2) supplied.cancel()
            }
            assertThrows(OperationCanceledException::class.java) {
                LegacyEndpointInventoryReader(audit).readPage(signal)
            }
            assertEquals(2, audit.queries.size)
            assertFalse(audit.queries.any { it.startsWith("SELECT rowid AS archive_rowid") })
        }
    }

    @Test
    fun cancellationDuringRowDeliveryClosesCursorAndNeverReturnsPartialPage() = withDatabase { database ->
        database.createNodes()
        database.insert(1)
        database.insert(2)
        val signal = CancellationSignal()
        var pageCursor: Cursor? = null
        database.readOnly { audit ->
            audit.wrapCursor = { sql, cursor ->
                if (sql.startsWith("SELECT rowid AS archive_rowid")) object : CursorWrapper(cursor) {
                    private var moves = 0
                    override fun moveToNext(): Boolean {
                        val result = super.moveToNext()
                        if (++moves == 2) signal.cancel()
                        return result
                    }
                }.also { pageCursor = it } else cursor
            }
            assertThrows(OperationCanceledException::class.java) {
                LegacyEndpointInventoryReader(audit).readPage(signal)
            }
            assertNotNull(pageCursor)
            assertTrue(checkNotNull(pageCursor).isClosed)
        }
    }

    @Test
    fun unsupportedMetadataIsAnErrorWithArchiveIntactAndFreshValidReadCanRecover() = withDatabase { database ->
        database.createNodes()
        database.insert(1)
        database.execSQL("CREATE TEMP TABLE NoDeS(note TEXT)")
        val original = fingerprint(database.path)
        database.readOnly { audit ->
            assertThrows(IllegalStateException::class.java) {
                LegacyEndpointInventoryReader(audit).readPage(CancellationSignal())
            }
        }
        assertEquals(original, fingerprint(database.path))
        database.execSQL("DROP TABLE temp.NoDeS")
        database.execSQL("CREATE INDEX unsupported_archive_index ON nodes(link)")
        database.readOnly { audit ->
            assertThrows(IllegalStateException::class.java) {
                LegacyEndpointInventoryReader(audit).readPage(CancellationSignal())
            }
        }
        database.execSQL("DROP INDEX unsupported_archive_index")
        database.readOnly { audit ->
            assertEquals(1L, LegacyEndpointInventoryReader(audit).readPage(CancellationSignal()).records.single().originalRowId)
        }
    }

    @Test
    fun metadataAndPageSqlErrorsPropagateWithoutFabricatedEmptySuccess() = withDatabase { database ->
        database.createNodes()
        database.insert(1)
        listOf(false, true).forEach { failPage -> database.readOnly { audit ->
            val failure = SQLException("Synthetic inventory read failure")
            audit.beforeQuery = { sql, _ ->
                if (!failPage || sql.startsWith("SELECT rowid AS archive_rowid")) throw failure
            }
            val thrown = assertThrows(SQLException::class.java) {
                LegacyEndpointInventoryReader(audit).readPage(CancellationSignal())
            }
            assertSame(failure, thrown)
        } }
    }

    @Test
    fun unsupportedDatabaseEncodingIsLocalReadErrorAndLeavesRawArchiveExact() = withDatabase(encoding = "UTF-16le") { database ->
        database.createNodes()
        database.insert(1, "Saved 雪", "wss://offline.invalid/Exact")
        database.query("PRAGMA encoding").use { assertTrue(it.moveToFirst()); assertEquals("UTF-16le", it.getString(0)) }
        val original = fingerprint(database.path)
        database.readOnly { audit ->
            assertThrows(IllegalStateException::class.java) {
                LegacyEndpointInventoryReader(audit).readPage(CancellationSignal())
            }
            assertEquals(listOf("PRAGMA encoding"), audit.queries)
        }
        assertEquals(original, fingerprint(database.path))
    }

    @Test
    fun chunkReadsAllCohortsBeyondActualCursorWindowCapacityWithExactDatabaseAndWalBytes() {
        COHORTS.forEachIndexed { cohort, (order, activeDefault) -> withDatabase { database ->
            assertTrue(database.enableWriteAheadLogging())
            database.createNodes(order, activeDefault)
            // Initialize the read connection before the large insert: its first PRAGMA may truncate a checkpointed WAL.
            database.query("PRAGMA query_only").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
            val bytes = ByteArray(8 * 1024 * 1024 + 17) { (it % 251).toByte() }
            val id = if (cohort % 2 == 0) Long.MIN_VALUE else Long.MAX_VALUE
            database.execSQL("INSERT INTO nodes(id,name,link,networkType,isDefault,isActive) VALUES(?,CAST(? AS TEXT),?,15,0,1)",
                arrayOf(id, bytes, bytes))
            assertTrue(File(checkNotNull(database.path) + "-wal").length() > 0L)
            val original = databaseAndWalFingerprint(database.path)
            database.readOnly { audit ->
                assertEquals(original, databaseAndWalFingerprint(database.path))
                val reader = LegacyEndpointValueChunkReader(audit)
                LegacyEndpointField.values().forEach { field ->
                    listOf(0L, 4096L, bytes.size.toLong() - 111, bytes.size.toLong()).forEach { offset ->
                        val result = reader.readChunk(CancellationSignal(), id, field, offset)
                        val end = minOf(bytes.size.toLong(), offset + 512).toInt()
                        assertEquals(LegacyEndpointChunkState.AVAILABLE, result.state)
                        assertEquals(if (field == LegacyEndpointField.NAME) LegacyEndpointStorage.TEXT else LegacyEndpointStorage.BLOB, result.storage)
                        assertEquals(bytes.size.toLong(), result.totalByteLength)
                        assertArrayEquals(bytes.copyOfRange(offset.toInt(), end), result.bytes)
                        assertEquals(end.toLong(), result.endByteOffsetExclusive)
                        assertEquals(if (end < bytes.size) end.toLong() else null, result.nextByteOffset)
                    }
                }
                val failure = assertThrows(RuntimeException::class.java) {
                    database.query(SimpleSQLiteQuery("SELECT CAST(name AS BLOB) FROM main.nodes WHERE rowid = ?", arrayOf(id)),
                        CancellationSignal()).use { cursor -> assertTrue(cursor.moveToFirst()); cursor.getBlob(0) }
                }
                // API26 reports an unreadable CursorWindow row as IllegalStateException; newer APIs use SQLiteException.
                assertTrue(failure is SQLiteException || failure is IllegalStateException)
                assertTrue(failure.message.orEmpty().contains("CursorWindow", ignoreCase = true) ||
                    failure.message.orEmpty().contains("Row too big", ignoreCase = true))
                assertEquals(original, databaseAndWalFingerprint(database.path))
                assertTrue(audit.queries.filter { it.startsWith("SELECT rowid AS chunk_rowid") }.all {
                    it.endsWith("WHERE rowid = ? LIMIT 1") && it.contains("substr(CAST(")
                })
            }
            assertEquals(original, databaseAndWalFingerprint(database.path))
        } }
    }

    @Test
    fun chunkPreservesNulInvalidUtf8AndSplitUnicodeWithoutReplacementOrNormalization() = withDatabase { database ->
        database.createNodes()
        val bytes = ByteArray(511) { 65 } + "雪é".toByteArray(Charsets.UTF_8) + byteArrayOf(0, -128, -1) + "\u202e\\x80".toByteArray(Charsets.UTF_8)
        database.execSQL("INSERT INTO nodes VALUES(1,CAST(? AS TEXT),?,15,0,1)", arrayOf(bytes, bytes))
        val original = databaseAndWalFingerprint(database.path)
        database.readOnly { audit ->
            LegacyEndpointField.values().forEach { field ->
                val reader = LegacyEndpointValueChunkReader(audit)
                val first = reader.readChunk(CancellationSignal(), 1, field)
                val last = reader.readChunk(CancellationSignal(), 1, field, checkNotNull(first.nextByteOffset))
                assertArrayEquals(bytes.copyOfRange(0, 512), first.bytes)
                assertArrayEquals(bytes.copyOfRange(512, bytes.size), last.bytes)
                assertNull(last.nextByteOffset)
                assertEquals(bytes.size.toLong(), last.endByteOffsetExclusive)
            }
        }
        assertEquals(original, databaseAndWalFingerprint(database.path))
    }

    @Test
    fun chunkMissingArchiveRowEmptyValueAndOffsetValidationRemainDistinct() = withDatabase { database ->
        database.readOnly { audit ->
            assertEquals(LegacyEndpointChunkState.ARCHIVE_ABSENT,
                LegacyEndpointValueChunkReader(audit).readChunk(CancellationSignal(), 1, LegacyEndpointField.NAME).state)
        }
        database.createNodes()
        database.insert(1, "", "")
        val original = databaseAndWalFingerprint(database.path)
        database.readOnly { audit ->
            val reader = LegacyEndpointValueChunkReader(audit)
            assertEquals(LegacyEndpointChunkState.ROW_ABSENT, reader.readChunk(CancellationSignal(), 2, LegacyEndpointField.NAME).state)
            val empty = reader.readChunk(CancellationSignal(), 1, LegacyEndpointField.NAME)
            assertEquals(LegacyEndpointChunkState.AVAILABLE, empty.state)
            assertEquals(0L, empty.totalByteLength)
            assertArrayEquals(byteArrayOf(), empty.bytes)
            assertThrows(IllegalArgumentException::class.java) { reader.readChunk(CancellationSignal(), 1, LegacyEndpointField.NAME, 1) }
            val before = audit.queries.size
            listOf(-1L, Long.MIN_VALUE, Long.MAX_VALUE).forEach { offset ->
                assertThrows(IllegalArgumentException::class.java) { reader.readChunk(CancellationSignal(), 1, LegacyEndpointField.NAME, offset) }
            }
            assertThrows(OperationCanceledException::class.java) {
                reader.readChunk(CancellationSignal().apply { cancel() }, 1, LegacyEndpointField.NAME)
            }
            assertEquals(before, audit.queries.size)
        }
        assertEquals(original, databaseAndWalFingerprint(database.path))
    }

    @Test
    fun chunkEmptyTextAndBlobAndNonzeroEofReadAcrossAllFourCohorts() {
        COHORTS.forEach { (order, activeDefault) -> withDatabase { database ->
            assertTrue(database.enableWriteAheadLogging())
            database.createNodes(order, activeDefault)
            val rows = listOf(
                Triple(Long.MIN_VALUE, true, byteArrayOf()), Triple(-1L, false, byteArrayOf()),
                Triple(1L, true, ByteArray(513) { 65 }), Triple(Long.MAX_VALUE, false, ByteArray(513) { 66 })
            )
            rows.forEach { (id, nameIsText, bytes) ->
                val projections = if (nameIsText) "CAST(? AS TEXT),?" else "?,CAST(? AS TEXT)"
                database.execSQL("INSERT INTO nodes(id,name,link,networkType,isDefault,isActive) VALUES(?,$projections,15,0,1)",
                    arrayOf(id, bytes, bytes))
            }
            val original = databaseAndWalFingerprint(database.path)
            database.readOnly { audit ->
                val reader = LegacyEndpointValueChunkReader(audit)
                rows.forEach { (id, nameIsText, bytes) ->
                    LegacyEndpointField.values().forEach { field ->
                        val textStorage = if (field == LegacyEndpointField.NAME) nameIsText else !nameIsText
                        listOf(0L, bytes.size.toLong()).distinct().forEach { offset ->
                            val chunk = reader.readChunk(CancellationSignal(), id, field, offset)
                            val end = minOf(bytes.size.toLong(), offset + 512)
                            assertEquals(LegacyEndpointChunkState.AVAILABLE, chunk.state)
                            assertEquals(if (textStorage) LegacyEndpointStorage.TEXT else LegacyEndpointStorage.BLOB, chunk.storage)
                            assertEquals(bytes.size.toLong(), chunk.totalByteLength)
                            assertArrayEquals(bytes.copyOfRange(offset.toInt(), end.toInt()), chunk.bytes)
                            assertEquals(end, chunk.endByteOffsetExclusive)
                            assertEquals(if (offset > 0) maxOf(0L, offset - 512) else null, chunk.previousByteOffset)
                            assertEquals(if (end < bytes.size) end else null, chunk.nextByteOffset)
                        }
                    }
                }
            }
            assertEquals(original, databaseAndWalFingerprint(database.path))
        } }
    }

    @Test
    fun chunkCancellationDuringMetadataQueryAndPayloadNeverReturnsPartialSuccess() = withDatabase { database ->
        database.createNodes()
        database.insert(1)
        val original = databaseAndWalFingerprint(database.path)
        listOf("metadata", "query", "payload").forEach { stage ->
            val signal = CancellationSignal()
            var payloadCursor: Cursor? = null
            database.readOnly { audit ->
                audit.beforeQuery = { sql, supplied ->
                    assertSame(signal, supplied)
                    if (stage == "metadata" && audit.queries.size == 2 || stage == "query" && sql.startsWith("SELECT rowid AS chunk_rowid")) supplied.cancel()
                }
                if (stage == "payload") audit.wrapCursor = { sql, cursor ->
                    if (sql.startsWith("SELECT rowid AS chunk_rowid")) object : CursorWrapper(cursor) {
                        override fun getBlob(columnIndex: Int): ByteArray = super.getBlob(columnIndex).also { signal.cancel() }
                    }.also { payloadCursor = it } else cursor
                }
                assertThrows(OperationCanceledException::class.java) {
                    LegacyEndpointValueChunkReader(audit).readChunk(signal, 1, LegacyEndpointField.NAME)
                }
                if (stage == "payload") assertTrue(checkNotNull(payloadCursor).isClosed)
            }
        }
        assertEquals(original, databaseAndWalFingerprint(database.path))
    }

    @Test
    fun chunkReusesSchemaAdmissionAndPropagatesMetadataAndProjectionFailures() = withDatabase { database ->
        database.createNodes()
        database.insert(1)
        listOf("CREATE TEMP TABLE NoDeS(note TEXT)" to "DROP TABLE temp.NoDeS",
            "CREATE INDEX unexpected_index ON nodes(link)" to "DROP INDEX unexpected_index",
            "CREATE TRIGGER unexpected_trigger AFTER UPDATE ON nodes BEGIN SELECT 1; END" to "DROP TRIGGER unexpected_trigger"
        ).forEach { (create, drop) ->
            database.execSQL(create)
            val original = databaseAndWalFingerprint(database.path)
            database.readOnly { audit ->
                assertThrows(IllegalStateException::class.java) {
                    LegacyEndpointValueChunkReader(audit).readChunk(CancellationSignal(), 1, LegacyEndpointField.NAME)
                }
                assertFalse(audit.queries.any { it.startsWith("SELECT rowid AS chunk_rowid") })
            }
            assertEquals(original, databaseAndWalFingerprint(database.path))
            database.execSQL(drop)
        }
        listOf(false, true).forEach { projection -> database.readOnly { audit ->
            val failure = SQLException("Synthetic chunk failure")
            audit.beforeQuery = { sql, _ -> if (!projection || sql.startsWith("SELECT rowid AS chunk_rowid")) throw failure }
            assertSame(failure, assertThrows(SQLException::class.java) {
                LegacyEndpointValueChunkReader(audit).readChunk(CancellationSignal(), 1, LegacyEndpointField.NAME)
            })
        } }
    }

    @Test
    fun chunkRejectsNonUtf8ArchiveEncodingBeforeAnyPayloadRead() = withDatabase(encoding = "UTF-16le") { database ->
        database.createNodes()
        database.insert(1)
        val original = databaseAndWalFingerprint(database.path)
        database.readOnly { audit ->
            assertThrows(IllegalStateException::class.java) {
                LegacyEndpointValueChunkReader(audit).readChunk(CancellationSignal(), 1, LegacyEndpointField.NAME)
            }
            assertEquals(listOf("PRAGMA encoding"), audit.queries)
        }
        assertEquals(original, databaseAndWalFingerprint(database.path))
    }

    private fun databaseAndWalFingerprint(path: String?): Map<String, String?> {
        val databasePath = checkNotNull(path)
        return listOf(databasePath, databasePath + "-wal").associateWith { name ->
            if (File(name).exists()) fingerprint(name) else null
        }
    }

    private fun withDatabase(encoding: String? = null, action: (SupportSQLiteDatabase) -> Unit) {
        val name = "legacy-endpoint-inventory-${UUID.randomUUID()}"
        names += name
        if (encoding != null) {
            check(encoding == "UTF-16le")
            val path = context.getDatabasePath(name)
            check(path.parentFile?.let { it.isDirectory || it.mkdirs() } == true)
            // Establish encoding before Android's locale metadata table can make it immutable.
            SQLiteDatabase.openDatabase(path.path, null,
                SQLiteDatabase.CREATE_IF_NECESSARY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { seed ->
                seed.execSQL("PRAGMA encoding='UTF-16le'")
                seed.execSQL("CREATE TABLE encoding_fixture_marker(value INTEGER)")
                seed.version = 1
            }
        }
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Unexpected test upgrade")
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(callback).build())
        try { action(helper.writableDatabase) } finally { helper.close() }
    }

    private fun SupportSQLiteDatabase.createNodes(order: List<String> = COHORTS[0].first, activeDefault: Boolean = false) {
        val columns = order.map { column -> when (column) {
            "id" -> "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL"
            "name", "link" -> "$column TEXT NOT NULL"
            "isActive" -> "isActive INTEGER NOT NULL" + if (activeDefault) " DEFAULT 0" else ""
            else -> "$column INTEGER NOT NULL"
        } }
        execSQL("CREATE TABLE nodes(${columns.joinToString()})")
    }

    private fun SupportSQLiteDatabase.insert(id: Long, name: String = "Saved", link: String = "wss://offline.invalid", network: Long = 15, default: Long = 0, active: Long = 1) {
        execSQL("INSERT INTO nodes(id,name,link,networkType,isDefault,isActive) VALUES(?,?,?,?,?,?)",
            arrayOf(id, name, link, network, default, active))
    }

    private fun SupportSQLiteDatabase.readOnly(action: (AuditedQueries) -> Unit) {
        execSQL("PRAGMA query_only=ON")
        try {
            query("PRAGMA query_only").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
            action(AuditedQueries(this))
        } finally { execSQL("PRAGMA query_only=OFF") }
    }

    private class AuditedQueries(private val database: SupportSQLiteDatabase) : SupportSQLiteDatabase by database {
        val queries = mutableListOf<String>()
        var beforeQuery: (String, CancellationSignal) -> Unit = { _, _ -> }
        var wrapCursor: (String, Cursor) -> Cursor = { _, cursor -> cursor }
        override fun query(query: SupportSQLiteQuery, cancellationSignal: CancellationSignal?): Cursor {
            val signal = checkNotNull(cancellationSignal) { "Every metadata and page query must carry cancellation" }
            queries += query.sql
            beforeQuery(query.sql, signal)
            return wrapCursor(query.sql, database.query(query, signal))
        }
        override fun query(query: String): Cursor = error("Missing query cancellation")
        override fun query(query: String, bindArgs: Array<out Any?>): Cursor = error("Missing query cancellation")
        override fun query(query: SupportSQLiteQuery): Cursor = error("Missing query cancellation")
        override fun execSQL(sql: String) = error("Reader must not mutate SQL")
        override fun execSQL(sql: String, bindArgs: Array<out Any?>) = error("Reader must not mutate SQL")
        override fun beginTransaction() = error("Reader must not own transactions")
        override fun beginTransactionNonExclusive() = error("Reader must not own transactions")
        override fun setTransactionSuccessful() = error("Reader must not own transactions")
        override fun endTransaction() = error("Reader must not own transactions")
        override fun close() = error("Reader must not close caller connection")
    }

    private fun fingerprint(path: String?): String {
        val digest = MessageDigest.getInstance("SHA-256")
        File(checkNotNull(path)).inputStream().use { input ->
            val chunk = ByteArray(16 * 1024)
            while (true) { val count = input.read(chunk); if (count < 0) break; digest.update(chunk, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        val COHORTS = listOf(
            listOf("id", "name", "link", "networkType", "isDefault", "isActive") to false,
            listOf("name", "link", "networkType", "isDefault", "isActive", "id") to false,
            listOf("id", "name", "link", "networkType", "isDefault", "isActive") to true,
            listOf("name", "link", "networkType", "isDefault", "id", "isActive") to true
        )
    }
}
