package jp.co.soramitsu.coredb.migrations

import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException
import jp.co.soramitsu.coredb.migrations.fixtures.ReleasedSchemaFixtures
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * TEST-ONLY proposed bounded modern-endpoint capture acceptance.
 *
 * Every helper is exclusively in-memory. Frozen released APK DDL supplies the
 * starting schema; only actual intervening migrations prepare the target edge.
 * Synthetic raw-layout cases are explicitly named and are not released-writer
 * claims. No endpoint is contacted and no persistent app database/preferences
 * are used. These direct SQL edges do not qualify complete Room startup, UI
 * accessibility, process death, disk exhaustion or arbitrary huge payloads.
 *
 * Failures distinguish actual lost/coerced payloads, node-only guard rollback,
 * and merely missing immutable capture. Current wallet identity guards remain
 * strict. The oversized control intentionally remains outside this checkpoint.
 */
class ModernEndpointCaptureMigrationTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun version39PreservesAllOriginalRowsBeforeRegistryFiltering() = withVersion(39) {
        seedParent()
        node(-7, "wss://custom.invalid/one", "Owner 雪", 0, 0)
        node(19, "wss://registry.invalid/unused", "Original registry label", 0, 1)
        val before = rows(NODES)
        migrate(ChainAssetsMigration_39_40)
        assertRecoverable(before, "39->40 registry filtering")
        assertArchiveIndependentOfParents(before)
    }

    @Test
    fun version39RetainedCustomEndpointSurvivesLaterParentDeletion() = withVersion(39) {
        seedParent()
        node(-19, "wss://custom.invalid/retained", "Retained owner", 1, 0)
        val before = rows(NODES)
        migrate(ChainAssetsMigration_39_40)
        // The old edge already retains this custom node. The missing durable
        // ownership appears when the registry later removes its parent.
        assertEquals(payloads(before), payloads(rows(NODES)))
        setForeignKeyConstraintsEnabled(true)
        execSQL("DELETE FROM chains")
        assertEquals(0L, scalar("SELECT COUNT(*) FROM chain_nodes"))
        assertRecoverable(before, "39->40 followed by real parent CASCADE")
    }

    @Test
    fun version41CapturesCustomBytesBeforeParentRenameAndDrop() = withVersion(41) {
        seedParent()
        node(Long.MIN_VALUE, "wss://custom.invalid/41", byteArrayOf(0x4e, 0xff.toByte(), 0), 1, 0)
        val before = rows(NODES)
        setForeignKeyConstraintsEnabled(true)
        migrate(Migration_41_42)
        assertRecoverable(before, "41->42 parent rename/drop with foreign keys enabled")
        assertArchiveIndependentOfParents(before)
    }

    @Test
    fun version45SyntheticNoAffinityCellsSurviveTypedReconstruction() = withVersion(45) {
        seedParent()
        replaceNodesWithRawLayout()
        node(-1, "wss://custom.invalid/integer", 42L, 0, 0)
        node(0, "wss://custom.invalid/real", 1.25, 1, 0)
        execSQL("INSERT INTO chain_nodes(rowid,chainId,url,name,isActive,isDefault) " +
            "VALUES(?, ?, ?, CAST(X'4eff00' AS TEXT), 0, 0)",
            arrayOf(7L, CHAIN, "wss://custom.invalid/malformed"))
        val before = rows(NODES)
        assertEquals(listOf("integer", "real", "text"), before.map { it.cells[2].type })
        migrate(Migration_45_46)
        assertRecoverable(before, "45->46 typed TEXT staging of synthetic no-affinity source")
        assertArchiveIndependentOfParents(before)
    }

    @Test
    fun version53CapturesCustomBytesBeforeParentDrop() = withVersion(53) {
        seedParent()
        node(Long.MAX_VALUE, "wss://custom.invalid/53", "Owner 53 雪", 0, 0)
        val before = rows(NODES)
        setForeignKeyConstraintsEnabled(true)
        migrate(Migration_53_54)
        assertRecoverable(before, "53->54 parent drop with foreign keys enabled")
        assertArchiveIndependentOfParents(before)
    }

    @Test
    fun version76CapturesOrphanCustomEndpointBeforeCacheDeletion() = withVersion(76) {
        node(-9002, "wss://custom.invalid/orphan", "Owner whose registry parent vanished", 1, 0)
        val before = rows(NODES)
        val preferences = preferences()
        migrate(WalletSecretIntegrityMigration(preferences))
        assertEquals(SENTINEL_VALUE, preferences.getDecryptedString(SENTINEL_KEY))
        assertRecoverable(before, "76->77 deletion of dangling chain_nodes cache rows")
        assertArchiveIndependentOfParents(before)
    }

    @Test
    fun version76SyntheticRawRowsRetainNullDuplicatesStorageClassesAndBytes() = withVersion(76) {
        seedParent()
        replaceNodesWithRawLayout()
        node(-9002, "wss://custom.invalid/duplicate", 42L, 0, 0)
        node(-1, "wss://custom.invalid/duplicate", 1.25, 1, 0)
        node(0, "wss://custom.invalid/null", null, 0, 0)
        node(7, "wss://custom.invalid/blob", byteArrayOf(0, 0xff.toByte(), 0x5c), "unknown", null)
        execSQL("INSERT INTO chain_nodes(rowid,chainId,url,name,isActive,isDefault) " +
            "VALUES(?, ?, X'', CAST(X'4eff00' AS TEXT), 0, 0)", arrayOf(Long.MAX_VALUE, CHAIN))
        val before = rows(NODES)
        assertEquals(setOf("null", "integer", "real", "blob", "text"), before.map { it.cells[2].type }.toSet())
        migrate(WalletSecretIntegrityMigration(preferences()))
        assertRecoverable(before, "76->77 INSERT OR IGNORE/typed repair of synthetic raw layout")
        assertArchiveIndependentOfParents(before)
    }

    @Test
    fun version39BoundedUnknownFlagsDoNotBlockAfterExactCapture() = withVersion(39) {
        seedParent()
        node(-7, "wss://custom.invalid/flags", "Bounded original", "unknown", 0)
        assertNodeOnlyGuardCanComplete(ChainAssetsMigration_39_40, "invalid ownership or selection flags")
    }

    @Test
    fun version39BoundedMissingParentDoesNotBlockAfterExactCapture() = withVersion(39) {
        node(-7, "wss://custom.invalid/missing39", "Bounded orphan", 1, 0)
        assertNodeOnlyGuardCanComplete(ChainAssetsMigration_39_40, "missing chains")
    }

    @Test
    fun version45BoundedMissingParentDoesNotBlockAfterExactCapture() = withVersion(45) {
        node(-7, "wss://custom.invalid/missing45", "Bounded orphan", 0, 0)
        assertNodeOnlyGuardCanComplete(Migration_45_46, "missing chains")
    }

    @Test
    fun version39PayloadOutsideCurrentBudgetRollsBackWithoutLoss() = withVersion(39) {
        seedParent()
        val size = TON_UPGRADE_MAX_CELL_PAYLOAD_BYTES + 1
        execSQL("INSERT INTO chain_nodes(rowid,chainId,url,name,isActive,isDefault) " +
            "VALUES(-7, ?, 'wss://custom.invalid/oversized', zeroblob(?), 0, 0)", arrayOf(CHAIN, size))
        val schemaBefore = schema()
        val failure = assertThrows(MigrationPayloadLimitException::class.java) { migrate(ChainAssetsMigration_39_40) }
        assertTrue(failure.message.orEmpty().contains("chain_nodes.name"))
        assertEquals(schemaBefore, schema())
        assertEquals(39, version)
        assertEquals(1L, scalar("SELECT COUNT(*) FROM chain_nodes"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM chain_nodes WHERE rowid=-7 " +
            "AND typeof(name)='blob' AND length(name)=$size AND name=zeroblob($size)"))
        // This is preservation on rejection, not successful startup availability.
        assertFalse(tableExists(ARCHIVE))
    }

    @Test
    fun version76OrphanWalletIdentityStillRejectsAndRollsBackEverySqlChange() = withVersion(76) {
        seedParent()
        node(-7, "wss://custom.invalid/wallet-guard", "Bounded original", 1, 0)
        execSQL("INSERT INTO chain_accounts(metaId,chainId,publicKey,accountId,cryptoType,name,initialized) " +
            "VALUES(999, ?, X'0102', X'0102', 'SR25519', 'Orphan wallet identity', 1)", arrayOf(CHAIN))
        val accountFields = listOf("metaId", "chainId", "publicKey", "accountId", "cryptoType", "name", "initialized")
        fun accountRows(): List<Row> {
            val projection = accountFields.joinToString(",") { field ->
                "typeof(`$field`), CASE WHEN typeof(`$field`) IN ('text','blob') THEN hex(CAST(`$field` AS BLOB)) ELSE `$field` END"
            }
            return query("SELECT rowid,$projection FROM chain_accounts ORDER BY rowid").use { c -> buildList {
                while (c.moveToNext()) add(Row(c.getLong(0), accountFields.indices.map { i ->
                    val type = c.getString(1 + i * 2)
                    Cell(type, when (type) {
                        "null" -> null
                        "integer" -> c.getLong(2 + i * 2).toString()
                        "real" -> c.getDouble(2 + i * 2).toRawBits().toString()
                        "text", "blob" -> c.getString(2 + i * 2)
                        else -> error("Unexpected account storage class")
                    })
                }))
            } }
        }
        data class Violation(val child: String, val rowid: Long, val parent: String, val foreignKeyId: Int)
        fun violations(): List<Violation> = query("PRAGMA foreign_key_check").use { c -> buildList {
            while (c.moveToNext()) add(Violation(c.getString(0), c.getLong(1), c.getString(2), c.getInt(3)))
        } }
        val walletForeignKeyIds = query("PRAGMA foreign_key_list(chain_accounts)").use { c -> buildList {
            while (c.moveToNext()) {
                if (c.getString(2) == "meta_accounts" && c.getString(3) == "metaId" && c.getString(4) == "id") add(c.getInt(0))
            }
        } }
        assertEquals(1, walletForeignKeyIds.size)
        val before = rows(NODES)
        val schemaBefore = schema()
        val accountsBefore = accountRows()
        assertEquals(1, accountsBefore.size)
        assertEquals(7, accountsBefore.single().cells.size)
        assertEquals(Cell("integer", "999"), accountsBefore.single().cells.first())
        assertEquals(0L, scalar("SELECT COUNT(*) FROM meta_accounts"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM chains"))
        val violationsBefore = violations()
        assertEquals(listOf(Violation("chain_accounts", accountsBefore.single().sourceRowid, "meta_accounts", walletForeignKeyIds.single())), violationsBefore)
        val preferences = preferences()
        val preferencesBefore = preferences.snapshot()
        val failure = assertThrows(WalletPublicIdentityIntegrityException::class.java) { migrate(WalletSecretIntegrityMigration(preferences)) }
        assertEquals("The database contains a foreign-key violation", failure.message)
        assertEquals(before, rows(NODES))
        assertEquals(schemaBefore, schema())
        assertEquals(76, version)
        assertEquals(accountsBefore, accountRows())
        assertEquals(violationsBefore, violations())
        assertEquals(0L, scalar("SELECT COUNT(*) FROM meta_accounts"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM chains"))
        assertEquals(preferencesBefore, preferences.snapshot())
    }

    @Test
    fun version39RawAmbiguousRowsStayArchivedWithoutOperationalPromotion() = withVersion(39) {
        assertOrdinaryProjectionAfterCapture(ChainAssetsMigration_39_40)
    }

    @Test
    fun version45RawAmbiguousRowsStayArchivedWithoutOperationalPromotion() = withVersion(45) {
        assertOrdinaryProjectionAfterCapture(Migration_45_46)
    }

    private fun SupportSQLiteDatabase.assertOrdinaryProjectionAfterCapture(edge: Migration) {
        seedParent()
        replaceNodesWithRawLayout()
        node(-10, "wss://custom.invalid/duplicate", "First duplicate", 0, 0)
        node(-9, "wss://custom.invalid/duplicate", "Second duplicate", 1, 0)
        node(-8, "wss://custom.invalid/null-flag", "Null ownership", 0, null)
        node(-7, "wss://custom.invalid/null-name", null, 0, 0)
        node(-6, null, "Null endpoint", 0, 0)
        node(-5, "wss://custom.invalid/blob-name", byteArrayOf(0x4e, 0xff.toByte()), 0, 0)
        node(-4, "wss://custom.invalid/integer-name", 42L, 0, 0)
        node(-3, "wss://custom.invalid/real-name", 1.25, 0, 0)
        node(-2, "wss://custom.invalid/unknown-flag", "Unknown flag", "unknown", 0)
        node(17, "wss://custom.invalid/ordinary", "Ordinary owner 雪", 1, 0)
        val before = rows(NODES)
        val ordinary = before.single { it.sourceRowid == 17L }.cells
        migrate(edge)
        assertRecoverable(before, "raw capture before bounded operational projection")
        assertEquals("Only the ordinary existing row can remain operational", listOf(ordinary), payloads(rows(NODES)))
        assertArchiveIndependentOfParents(before)
    }

    private fun withVersion(target: Int, action: SupportSQLiteDatabase.() -> Unit) {
        val release = when (target) { 39 -> "2.0.3"; 41 -> "2.0.13"; 45 -> "2.1.0"; 53 -> "2.2.2"; 76 -> "4.2.0"; else -> error("Unreviewed target") }
        val expectedSource = when (target) { 39 -> 37; 41 -> 41; 45 -> 45; 53 -> 49; 76 -> 75; else -> error("Unreviewed target") }
        val fixture = ReleasedSchemaFixtures.load(context, release)
        assertEquals(expectedSource, fixture.databaseVersion)
        val callback = object : SupportSQLiteOpenHelper.Callback(fixture.databaseVersion) {
            override fun onCreate(db: SupportSQLiteDatabase) { fixture.statements.forEach(db::execSQL) }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Unexpected helper upgrade")
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(null).callback(callback).build()
        )
        try {
            val db = helper.writableDatabase
            db.setForeignKeyConstraintsEnabled(false)
            assertEquals(expectedSource, db.version)
            db.query("SELECT identity_hash FROM room_master_table WHERE id=42").use { cursor ->
                assertTrue(cursor.moveToFirst()); assertEquals(fixture.roomIdentityHash, cursor.getString(0)); assertFalse(cursor.moveToNext())
            }
            val preparation = if (target == 76) listOf(Migration_75_76) else
                HISTORICAL_SQL_MIGRATIONS_32_TO_71.filter { it.startVersion >= expectedSource && it.endVersion <= target }
            preparation.forEach { edge -> assertEquals(db.version, edge.startVersion); db.migrate(edge) }
            assertEquals(target, db.version)
            assertEquals(0L, db.scalar("SELECT COUNT(*) FROM chain_nodes"))
            assertEquals(0L, db.scalar("SELECT COUNT(*) FROM meta_accounts"))
            assertEquals(0L, db.scalar("SELECT COUNT(*) FROM chain_accounts"))
            assertFalse(db.tableExists(ARCHIVE))
            db.action()
        } finally { helper.close() }
    }

    private fun SupportSQLiteDatabase.seedParent() {
        val values = linkedMapOf<String, Any?>("id" to CHAIN, "name" to "Public fixture chain", "icon" to "fixture", "prefix" to 42,
            "isEthereumBased" to 0, "isTestNet" to 0, "hasCrowdloans" to 0, "supportStakingPool" to 0,
            "isEthereumChain" to 0, "isChainlinkProvider" to 0, "supportNft" to 0, "isUsesAppId" to 0, "ecosystem" to "Substrate")
        val columns = query("PRAGMA table_info(chains)").use { c -> buildSet { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) } }
        val present = values.filterKeys { it in columns }
        execSQL("INSERT INTO chains(" + present.keys.joinToString { "`$it`" } + ") VALUES(" + present.keys.joinToString { "?" } + ")", present.values.toTypedArray())
    }

    private fun SupportSQLiteDatabase.node(rowid: Long, url: Any?, name: Any?, active: Any?, default: Any?) {
        execSQL("INSERT INTO chain_nodes(rowid,chainId,url,name,isActive,isDefault) VALUES(?,?,?,?,?,?)",
            arrayOf(rowid, CHAIN, url, name, active, default))
    }

    private fun SupportSQLiteDatabase.replaceNodesWithRawLayout() {
        execSQL("DROP TABLE chain_nodes")
        execSQL("CREATE TABLE chain_nodes(chainId, url, name, isActive, isDefault, " +
            "FOREIGN KEY(chainId) REFERENCES chains(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
    }

    private fun SupportSQLiteDatabase.migrate(edge: Migration) {
        assertEquals(edge.startVersion, version)
        beginTransaction()
        try { edge.migrate(this); version = edge.endVersion; setTransactionSuccessful() } finally { endTransaction() }
    }

    private fun SupportSQLiteDatabase.assertNodeOnlyGuardCanComplete(edge: Migration, expectedMessage: String) {
        val before = rows(NODES)
        val schemaBefore = schema()
        val outcome = runCatching { migrate(edge) }
        if (outcome.isFailure) {
            val error = checkNotNull(outcome.exceptionOrNull())
            assertTrue("Unexpected failure: $error", error is IllegalStateException && error.message.orEmpty().contains(expectedMessage))
            assertEquals("Rejected edge must roll back exact endpoint values", before, rows(NODES))
            assertEquals(schemaBefore, schema())
            assertEquals(edge.startVersion, version)
            fail("NODE_ONLY_GUARD_BLOCKED_AVAILABILITY: ${edge.startVersion}->${edge.endVersion}; " +
                "original ${before.size} rows retained after rollback; guard=${error.message}")
        }
        assertRecoverable(before, "bounded node-only guard input")
        assertArchiveIndependentOfParents(before)
    }

    private fun SupportSQLiteDatabase.assertRecoverable(before: List<Row>, operation: String) {
        val archived = tableExists(ARCHIVE)
        val after = rows(if (archived) ARCHIVE else NODES)
        // Compare actual surviving values before testing a proposed table name.
        assertEquals("ACTUAL_PAYLOAD_LOSS_OR_COERCION after $operation; archivePresent=$archived", payloads(before), payloads(after))
        assertTrue("PAYLOADS_SURVIVE_BUT_IMMUTABLE_ARCHIVE_MISSING after $operation", archived)
        assertEquals("Original signed source rowids must remain exact", before, after)
        val columns = query("PRAGMA table_info($ARCHIVE)").use { c -> buildList {
            while (c.moveToNext()) add(listOf(c.getString(1), c.getString(2), c.getInt(3).toString(), if (c.isNull(4)) null else c.getString(4), c.getInt(5).toString()))
        } }
        assertEquals(listOf("source_rowid") + FIELDS, columns.map { it[0] })
        assertEquals("INTEGER", columns.first()[1]?.uppercase())
        assertEquals("1", columns.first()[4])
        assertEquals(null, columns.first()[3])
        columns.drop(1).forEach { column ->
            assertTrue("Raw archive cells must have no affinity", column[1]?.uppercase() in setOf("", "BLOB"))
            assertEquals(listOf("0", null, "0"), column.drop(2))
        }
        query("PRAGMA index_list($ARCHIVE)").use { assertFalse("No payload uniqueness constraint is permitted", it.moveToFirst()) }
        query("PRAGMA foreign_key_list($ARCHIVE)").use { assertFalse("Archive must have no parent FK", it.moveToFirst()) }
    }

    private fun SupportSQLiteDatabase.assertArchiveIndependentOfParents(before: List<Row>) {
        setForeignKeyConstraintsEnabled(true)
        execSQL("DELETE FROM chains")
        assertEquals(before, rows(ARCHIVE))
    }

    private data class Cell(val type: String, val value: String?)
    private data class Row(val sourceRowid: Long, val cells: List<Cell>)
    private fun payloads(rows: List<Row>): List<List<Cell>> = rows.map { it.cells }.sortedBy { it.toString() }

    private fun SupportSQLiteDatabase.rows(table: String): List<Row> {
        check(table == NODES || table == ARCHIVE)
        val id = if (table == NODES) "rowid" else "source_rowid"
        val projection = FIELDS.joinToString(",") { field ->
            "typeof(`$field`), CASE WHEN typeof(`$field`) IN ('text','blob') THEN hex(CAST(`$field` AS BLOB)) ELSE `$field` END"
        }
        return query("SELECT $id,$projection FROM `$table` ORDER BY $id").use { c -> buildList {
            while (c.moveToNext()) add(Row(c.getLong(0), FIELDS.indices.map { i ->
                val type = c.getString(1 + i * 2)
                val value = when (type) {
                    "null" -> null
                    "integer" -> c.getLong(2 + i * 2).toString()
                    "real" -> c.getDouble(2 + i * 2).toRawBits().toString()
                    "text", "blob" -> c.getString(2 + i * 2)
                    else -> error("Unexpected storage class")
                }
                Cell(type, value)
            }))
        } }
    }

    private fun SupportSQLiteDatabase.schema(): List<List<String?>> = query(
        "SELECT type,name,tbl_name,sql FROM sqlite_master UNION ALL SELECT type,name,tbl_name,sql FROM sqlite_temp_master ORDER BY 1,2,3"
    ).use { c -> buildList { while (c.moveToNext()) add((0..3).map { if (c.isNull(it)) null else c.getString(it) }) } }
    private fun SupportSQLiteDatabase.tableExists(name: String): Boolean = query("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", arrayOf(name)).use { it.moveToFirst() }
    private fun SupportSQLiteDatabase.scalar(sql: String): Long = query(sql).use { c -> assertTrue(c.moveToFirst()); val value = c.getLong(0); assertFalse(c.moveToNext()); value }
    private fun preferences() = HashMapEncryptedPreferences().apply { putEncryptedString(SENTINEL_KEY, SENTINEL_VALUE) }
    private fun HashMapEncryptedPreferences.snapshot(): Map<String, String> = keysWithPrefixes(setOf(""), 256, 1024, 65536, true).associateWith { checkNotNull(getDecryptedString(it)) }

    private companion object {
        const val NODES = "chain_nodes"
        const val ARCHIVE = "endpoint_archive_v1"
        const val CHAIN = "modern-capture-public-chain"
        const val SENTINEL_KEY = "modern-capture-unrelated"
        const val SENTINEL_VALUE = "public unrelated preference must remain exact"
        val FIELDS = listOf("chainId", "url", "name", "isActive", "isDefault")
    }
}
