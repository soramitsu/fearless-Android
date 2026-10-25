package jp.co.soramitsu.coredb.migrations

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.coredb.migrations.fixtures.ReleasedSchemaFixtures
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Direct 39 -> 40 SQL edge qualification, starting with the exact released
 * DB37 fixture and the real 37 -> 39 migrations. No preferences or network.
 * This does not qualify earlier secret-write ordering or legacy node mapping.
 */
class ChainNodesRegistryRebuildMigrationTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val createdDatabases = mutableSetOf<String>()

    @After
    fun removeOwnedTestDatabases() {
        createdDatabases.forEach(context::deleteDatabase)
    }

    @Test
    fun customAndSelectedDefaultNodesSurviveWithForeignKeysOff() {
        withVersion39("fk-off", foreignKeys = false) {
            assertSourceRows()
            inTransaction { ChainAssetsMigration_39_40.migrate(this) }
            assertRetainedRows()
            assertEquals(0L, singleLong("PRAGMA foreign_keys"))
        }
    }

    @Test
    fun customAndSelectedDefaultParentsSurviveWithForeignKeysOnWithoutChainAccounts() {
        withVersion39("fk-on", foreignKeys = true) {
            assertSourceRows()
            assertEquals(0L, singleLong("SELECT COUNT(*) FROM chain_accounts"))
            query("PRAGMA foreign_key_list(chain_nodes)").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("chains", cursor.getString(cursor.getColumnIndexOrThrow("table")))
                assertEquals("CASCADE", cursor.getString(cursor.getColumnIndexOrThrow("on_delete")))
                assertFalse(cursor.moveToNext())
            }
            inTransaction { ChainAssetsMigration_39_40.migrate(this) }
            assertRetainedRows()
            assertEquals(1L, singleLong("PRAGMA foreign_keys"))
        }
    }

    @Test
    fun failedNodeRestoreRollsBackSourceAndCleansTemporaryTables() {
        withVersion39("restore-failure", foreignKeys = true) {
            assertSourceRows()
            val original = this
            val beforeSchema = schemaSnapshot()
            val beforeNodes = nodeIdentitySnapshot()
            var injected = 0
            val restoreFailure = object : SupportSQLiteDatabase by original {
                override fun execSQL(sql: String) {
                    if (sql.startsWith("INSERT INTO chain_nodes(")) {
                        // Inject at the actual restore after complete capture and parent rebuild.
                        assertEquals(0, injected++)
                        assertTrue(ModernEndpointArchive.requireSafe(original))
                        assertEquals(4L, original.singleLong("SELECT COUNT(*) FROM endpoint_archive_v1"))
                        assertEquals(0L, original.singleLong("SELECT COUNT(*) FROM chain_nodes"))
                        assertTrue(original.chainColumns().contains("minSupportedVersion"))
                        original.execSQL(
                            "CREATE TRIGGER reject_restored_node BEFORE INSERT ON chain_nodes " +
                                "BEGIN SELECT RAISE(ABORT, 'owned test restore failure'); END"
                        )
                    }
                    original.execSQL(sql)
                }
            }
            val failure = assertThrows(SQLiteException::class.java) {
                inTransaction { ChainAssetsMigration_39_40.migrate(restoreFailure) }
            }
            assertEquals(1, injected)
            assertTrue(failure.message.orEmpty().contains("owned test restore failure"))
            assertSourceRows()
            assertEquals(beforeSchema, schemaSnapshot())
            assertEquals(beforeNodes, nodeIdentitySnapshot())
            assertFalse(ModernEndpointArchive.requireSafe(this))
            assertTemporaryTablesAbsent()
            assertEquals(1L, singleLong("PRAGMA foreign_keys"))
            inTransaction { ChainAssetsMigration_39_40.migrate(this) }
            assertRetainedRows()
            assertTrue(ModernEndpointArchive.requireSafe(this))
        }
    }

    @Test
    fun unsupportedSourceTriggerRejectsBeforeCaptureWithoutChangingSource() {
        withVersion39("source-trigger", foreignKeys = true) {
            assertSourceRows()
            execSQL(
                "CREATE TRIGGER reject_restored_node BEFORE INSERT ON chain_nodes " +
                    "BEGIN SELECT RAISE(ABORT, 'owned test restore failure'); END"
            )
            val beforeSchema = schemaSnapshot()
            val beforeNodes = nodeIdentitySnapshot()
            val failure = assertThrows(IllegalStateException::class.java) {
                inTransaction { ChainAssetsMigration_39_40.migrate(this) }
            }
            assertTrue(failure.message.orEmpty().contains("too many owned schema objects"))
            assertEquals(beforeSchema, schemaSnapshot())
            assertEquals(beforeNodes, nodeIdentitySnapshot())
            assertFalse(ModernEndpointArchive.requireSafe(this))
            assertSourceRows()
            assertTemporaryTablesAbsent()
            assertEquals(1L, singleLong("PRAGMA foreign_keys"))
            execSQL("DROP TRIGGER reject_restored_node")
            inTransaction { ChainAssetsMigration_39_40.migrate(this) }
            assertRetainedRows()
            assertTrue(ModernEndpointArchive.requireSafe(this))
        }
    }

    @Test
    fun existingTemporaryWorkObjectIsRejectedWithoutReplacingIt() {
        withVersion39("work-collision", foreignKeys = true) {
            execSQL("CREATE TEMP TABLE _MIGRATION_39_RETAINED_NODES(marker TEXT NOT NULL)")
            execSQL("INSERT INTO temp._migration_39_retained_nodes VALUES ('owned collision marker')")
            val failure = assertThrows(IllegalStateException::class.java) {
                inTransaction { ChainAssetsMigration_39_40.migrate(this) }
            }
            assertTrue(failure.message.orEmpty().contains("work objects already exist"))
            query("SELECT marker FROM temp._migration_39_retained_nodes").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("owned collision marker", cursor.getString(0))
                assertFalse(cursor.moveToNext())
            }
            assertSourceRows()
            execSQL("DROP TABLE temp._migration_39_retained_nodes")
            assertTemporaryTablesAbsent()
        }
    }

    private fun withVersion39(
        suffix: String,
        foreignKeys: Boolean,
        action: SupportSQLiteDatabase.() -> Unit
    ) {
        val schema = ReleasedSchemaFixtures.load(context, "2.0.3")
        assertEquals(37, schema.databaseVersion)
        val name = "registry-node-rebuild-$suffix"
        check(createdDatabases.add(name))
        ReleasedSchemaFixtures.createDatabase(context, name, schema)
        val callback = object : SupportSQLiteOpenHelper.Callback(schema.databaseVersion) {
            override fun onCreate(db: SupportSQLiteDatabase) = error("Released fixture was not installed")
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                error("Unexpected setup migration: $oldVersion to $newVersion")
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(callback).build()
        )
        try {
            val database = helper.writableDatabase
            database.setForeignKeyConstraintsEnabled(foreignKeys)
            assertEquals(37, database.version)
            database.query("SELECT identity_hash FROM room_master_table WHERE id = 42").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(schema.roomIdentityHash, cursor.getString(0))
            }
            database.inTransaction {
                DifferentCurrenciesMigrations_37_38.migrate(this)
                AssetsMigration_38_39.migrate(this)
                version = 39
                seedNodes()
            }
            database.action()
        } finally {
            helper.close()
        }
    }

    private fun SupportSQLiteDatabase.inTransaction(action: SupportSQLiteDatabase.() -> Unit) {
        beginTransaction()
        try {
            action()
            setTransactionSuccessful()
        } finally {
            endTransaction()
        }
    }

    private fun SupportSQLiteDatabase.seedNodes() {
        assertEquals(0L, singleLong("SELECT COUNT(*) FROM chains"))
        assertEquals(0L, singleLong("SELECT COUNT(*) FROM chain_nodes"))
        ALL_NODES.forEach { node ->
            execSQL(
                "INSERT INTO chains(id, name, icon, prefix, isEthereumBased, isTestNet, hasCrowdloans, url) " +
                    "VALUES (?, ?, ?, 2, 0, 0, 0, ?)",
                arrayOf(node.chainId, "Parent ${node.chainId.first()}", "fixture-icon", "https://parent.invalid")
            )
            execSQL(
                "INSERT INTO chain_nodes(chainId, url, name, isActive, isDefault) VALUES (?, ?, ?, ?, ?)",
                arrayOf(node.chainId, node.url, node.name, node.active, node.default)
            )
        }
    }

    private fun SupportSQLiteDatabase.assertSourceRows() {
        assertEquals(39, version)
        assertEquals(ALL_NODES, readNodes())
        assertEquals(4L, singleLong("SELECT COUNT(*) FROM chains"))
        assertEquals(0L, singleLong("SELECT COUNT(*) FROM chain_accounts"))
        assertFalse(chainColumns().contains("minSupportedVersion"))
        assertHealthy()
    }

    private fun SupportSQLiteDatabase.assertRetainedRows() {
        assertEquals(ALL_NODES.filter { it.default == 0 || it.active == 1 }, readNodes())
        assertEquals(3L, singleLong("SELECT COUNT(*) FROM chains"))
        assertTrue(chainColumns().contains("minSupportedVersion"))
        query("SELECT id, name, icon, prefix, url, minSupportedVersion FROM chains ORDER BY id").use { cursor ->
            ALL_NODES.take(3).forEach { node ->
                assertTrue(cursor.moveToNext())
                assertEquals(node.chainId, cursor.getString(0))
                assertEquals("Parent ${node.chainId.first()}", cursor.getString(1))
                assertEquals("fixture-icon", cursor.getString(2))
                assertEquals(2, cursor.getInt(3))
                assertEquals("https://parent.invalid", cursor.getString(4))
                assertTrue(cursor.isNull(5))
            }
            assertFalse(cursor.moveToNext())
        }
        assertHealthy()
        assertTemporaryTablesAbsent()
    }

    private fun SupportSQLiteDatabase.readNodes(): List<NodeRow> =
        query("SELECT chainId, url, name, isActive, isDefault FROM chain_nodes ORDER BY chainId, url").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(NodeRow(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getInt(3), cursor.getInt(4)))
                }
            }
        }

    private fun SupportSQLiteDatabase.chainColumns(): Set<String> =
        query("PRAGMA table_info(chains)").use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
        }

    private fun SupportSQLiteDatabase.assertHealthy() {
        query("PRAGMA integrity_check").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("ok", cursor.getString(0))
            assertFalse(cursor.moveToNext())
        }
        query("PRAGMA foreign_key_check").use { cursor -> assertFalse(cursor.moveToFirst()) }
    }

    private fun SupportSQLiteDatabase.assertTemporaryTablesAbsent() {
        assertEquals(
            0L,
            singleLong(
                "SELECT COUNT(*) FROM sqlite_temp_master WHERE name IN " +
                    "('_migration_39_retained_nodes', '_migration_39_wallet_chains')"
            )
        )
    }

    private fun SupportSQLiteDatabase.schemaSnapshot(): List<List<String?>> = query(
        "SELECT rowid, type, name, tbl_name, sql FROM sqlite_master ORDER BY rowid"
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add((0..4).map { if (cursor.isNull(it)) null else cursor.getString(it) })
        }
    }

    // This fixture contains only ordinary TEXT/INTEGER values; include rowid and storage class.
    private fun SupportSQLiteDatabase.nodeIdentitySnapshot(): List<List<String?>> = query(
        "SELECT rowid, typeof(chainId), hex(chainId), typeof(url), hex(url), " +
            "typeof(name), hex(name), typeof(isActive), quote(isActive), " +
            "typeof(isDefault), quote(isDefault) FROM chain_nodes ORDER BY rowid"
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add((0..10).map { if (cursor.isNull(it)) null else cursor.getString(it) })
        }
    }

    private fun SupportSQLiteDatabase.singleLong(sql: String): Long = query(sql).use { cursor ->
        assertTrue(cursor.moveToFirst())
        val value = cursor.getLong(0)
        assertFalse(cursor.moveToNext())
        value
    }

    private data class NodeRow(val chainId: String, val url: String, val name: String, val active: Int, val default: Int)

    private companion object {
        val ALL_NODES = listOf(
            NodeRow("a".repeat(64), "wss://custom-active.invalid/rpc?tag=one", "Custom '一' active", 1, 0),
            NodeRow("b".repeat(64), "wss://custom-inactive.invalid/rpc", "Custom 二 inactive", 0, 0),
            NodeRow("c".repeat(64), "wss://chosen-default.invalid/rpc", "Chosen default", 1, 1),
            NodeRow("d".repeat(64), "wss://refreshable-default.invalid/rpc", "Refreshable default", 0, 1)
        )
    }
}
