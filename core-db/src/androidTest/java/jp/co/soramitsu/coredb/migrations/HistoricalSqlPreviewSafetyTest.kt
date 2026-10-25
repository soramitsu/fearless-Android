package jp.co.soramitsu.coredb.migrations

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteException
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.coredb.migrations.fixtures.ReleasedSchemaFixtures
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real SQLite rollback qualification, using an exact released DB30 fixture
 * followed by the real 30 -> 31 edge. SQL-only public fields are synthetic.
 * Full 32 -> 71 previews use FK off, as the intended caller does; generic
 * savepoint tests additionally exercise FK on. No secret store is constructed.
 */
class HistoricalSqlPreviewSafetyTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val createdDatabases = mutableSetOf<String>()

    @After
    fun removeOwnedTestDatabases() {
        createdDatabases.forEach(context::deleteDatabase)
    }

    @Test
    fun actualSqlGraphSuccessRestoresEveryTableAndOuterTransactionCanCommit() {
        withVersion31("graph-success", foreignKeys = false) {
            val before = snapshot()
            var committed: DatabaseSnapshot? = null
            transaction {
                val result = withRollbackOnlySqlPreview(this) {
                    runActualSqlGraph()
                    execSQL("UPDATE temp.owned_preview_marker SET note = 'preview changed'")
                    // Room keeps the original user_version through every migration preflight.
                    TonUpgradeSqlPreflight.requireSafeToMutate(this)
                    version = 71
                    assertEquals(71, version)
                    "preview complete"
                }
                assertEquals("preview complete", result)
                assertEquals(before, snapshot())
                assertTrue(inTransaction())
                execSQL("UPDATE meta_accounts SET name = 'committed after preview' WHERE id = 1")
                committed = snapshot()
            }
            assertFalse(inTransaction())
            assertEquals(checkNotNull(committed), snapshot())
        }
    }

    @Test
    fun actualSqlGraphLateFailureRestoresEveryTableAndOuterTransactionCanCommit() {
        withVersion31("graph-failure", foreignKeys = false) {
            val before = snapshot()
            val original = SQLiteException("owned late preview failure")
            var committed: DatabaseSnapshot? = null
            transaction {
                val actual = assertThrows(SQLiteException::class.java) {
                    withRollbackOnlySqlPreview(this) {
                        runActualSqlGraph()
                        TonUpgradeSqlPreflight.requireSafeToMutate(this)
                        execSQL("CREATE TEMP TABLE owned_late_preview(value TEXT)")
                        execSQL("INSERT INTO temp.owned_late_preview VALUES ('must disappear')")
                        version = 71
                        throw original
                    }
                }
                assertSame(original, actual)
                assertTrue(actual.suppressed.isEmpty())
                assertEquals(before, snapshot())
                execSQL("UPDATE temp.owned_preview_marker SET note = 'committed after failure'")
                committed = snapshot()
            }
            assertFalse(inTransaction())
            assertEquals(checkNotNull(committed), snapshot())
        }
    }

    @Test
    fun prototypeEthereumPayloadPaddingCoversNullShortExactAndLongValuesWithoutPersisting() {
        listOf(null to null, 33 to 19, 65 to 20, 80 to 40).forEachIndexed { index, (keyBytes, addressBytes) ->
            withVersion31("padding-$index", foreignKeys = false) {
                execSQL(
                    "UPDATE meta_accounts SET ethereumPublicKey = ?, ethereumAddress = ? WHERE id = 1",
                    arrayOf(keyBytes?.let { ByteArray(it) { 4 } }, addressBytes?.let { ByteArray(it) { 5 } })
                )
                val before = snapshot()
                transaction {
                    withRollbackOnlySqlPreview(this) {
                        // Prototype the proposed payload-only upper bound. The
                        // production guard is not wired by this test fixture.
                        execSQL(
                            "UPDATE meta_accounts SET ethereumPublicKey = CASE " +
                                "WHEN ethereumPublicKey IS NULL OR length(CAST(ethereumPublicKey AS BLOB)) < 65 " +
                                "THEN zeroblob(65) ELSE ethereumPublicKey END, ethereumAddress = CASE " +
                                "WHEN ethereumAddress IS NULL OR length(CAST(ethereumAddress AS BLOB)) < 20 " +
                                "THEN zeroblob(20) ELSE ethereumAddress END"
                        )
                        assertEquals(maxOf(keyBytes ?: 0, 65).toLong(), scalar("SELECT length(ethereumPublicKey) FROM meta_accounts"))
                        assertEquals(maxOf(addressBytes ?: 0, 20).toLong(), scalar("SELECT length(ethereumAddress) FROM meta_accounts"))
                        runActualSqlGraph()
                        TonUpgradeSqlPreflight.requireSafeToMutate(this)
                        val failure = assertThrows(TonUpgradeSqlIntegrityException::class.java) {
                            TonUpgradeSqlPreflight.requireSafeToMutate(
                                this,
                                payloadLimits = TonUpgradePayloadLimits.PRODUCTION.copy(maximumCellBytes = 64)
                            )
                        }
                        assertTrue(failure.message.orEmpty().contains("ethereumPublicKey"))
                    }
                    assertEquals(before, snapshot())
                }
                assertEquals(before, snapshot())
            }
        }
    }

    @Test
    fun nestedPreviewsWithForeignKeysOnRestoreTheirOwnMainAndTemporaryState() {
        withVersion31("nested", foreignKeys = true) {
            val before = snapshot()
            transaction {
                withRollbackOnlySqlPreview(this) {
                    execSQL("UPDATE chains SET name = 'outer preview' WHERE id = ?", arrayOf(CUSTOM_CHAIN))
                    execSQL("CREATE TEMP TABLE owned_outer_preview(value TEXT)")
                    execSQL("INSERT INTO temp.owned_outer_preview VALUES ('outer value')")
                    val outerState = snapshot()
                    withRollbackOnlySqlPreview(this) {
                        execSQL("DELETE FROM chains WHERE id = ?", arrayOf(CUSTOM_CHAIN))
                        assertEquals(0L, scalar("SELECT COUNT(*) FROM chain_nodes WHERE chainId = '$CUSTOM_CHAIN'"))
                        execSQL("UPDATE temp.owned_outer_preview SET value = 'inner value'")
                        execSQL("CREATE TEMP TABLE owned_inner_preview(value BLOB)")
                        execSQL("INSERT INTO temp.owned_inner_preview VALUES (X'010203')")
                        version = 71
                    }
                    assertEquals(outerState, snapshot())
                }
                assertEquals(before, snapshot())
                assertEquals(1L, scalar("PRAGMA foreign_keys"))
            }
            assertEquals(before, snapshot())
        }
    }

    @Test
    fun failedPreviewWithForeignKeysOnRestoresCascadeAndTemporaryRows() {
        withVersion31("fk-on-failure", foreignKeys = true) {
            val before = snapshot()
            val original = SQLiteException("owned FK-on preview failure")
            transaction {
                val actual = assertThrows(SQLiteException::class.java) {
                    withRollbackOnlySqlPreview(this) {
                        execSQL("DELETE FROM chains WHERE id = ?", arrayOf(CUSTOM_CHAIN))
                        assertEquals(0L, scalar("SELECT COUNT(*) FROM chain_nodes WHERE chainId = '$CUSTOM_CHAIN'"))
                        execSQL("DELETE FROM temp.owned_preview_marker")
                        version = 40
                        throw original
                    }
                }
                assertSame(original, actual)
                assertEquals(before, snapshot())
                assertEquals(1L, scalar("PRAGMA foreign_keys"))
            }
            assertEquals(before, snapshot())
        }
    }

    @Test
    fun version36SqlFailureUnwindsNestedTransactionAndPreventsOuterCommit() {
        assertNestedMigrationFailureFailsClosed(36, FixAssetsMigration_36_37, "CREATE TABLE")
    }

    @Test
    fun version37SqlFailureUnwindsNestedTransactionAndPreventsOuterCommit() {
        assertNestedMigrationFailureFailsClosed(37, DifferentCurrenciesMigrations_37_38, "CREATE TABLE")
    }

    @Test
    fun version38SqlFailureUnwindsNestedTransactionAndPreventsOuterCommit() {
        assertNestedMigrationFailureFailsClosed(38, AssetsMigration_38_39, "CREATE TABLE")
    }

    private fun assertNestedMigrationFailureFailsClosed(version: Int, edge: Migration, failPrefix: String) {
        withVersion31("nested-failure-$version", foreignKeys = false) {
            transaction {
                HISTORICAL_SQL_MIGRATIONS_32_TO_71.filter { it.startVersion < version }.forEach { it.migrate(this) }
                this.version = version
            }
            val beforeOuter = snapshot()
            val original = SQLiteException("owned version $version SQL failure")
            var nestedDepth = 0
            var intercepted = false
            val realDatabase = this
            val failingDatabase = object : SupportSQLiteDatabase by realDatabase {
                override fun beginTransaction() {
                    realDatabase.beginTransaction()
                    nestedDepth += 1
                }

                override fun endTransaction() {
                    try {
                        realDatabase.endTransaction()
                    } finally {
                        nestedDepth -= 1
                    }
                }

                override fun execSQL(sql: String) {
                    if (!intercepted && sql.trimStart().startsWith(failPrefix)) {
                        assertEquals(1, nestedDepth)
                        intercepted = true
                        throw original
                    }
                    realDatabase.execSQL(sql)
                }
            }
            beginTransaction()
            try {
                execSQL("UPDATE meta_accounts SET name = 'must roll back with outer transaction' WHERE id = 1")
                val beforePreview = snapshot()
                val actual = assertThrows(SQLiteException::class.java) {
                    withRollbackOnlySqlPreview(this) { edge.migrate(failingDatabase) }
                }
                assertTrue(intercepted)
                assertSame(original, actual)
                assertTrue(actual.suppressed.isEmpty())
                assertEquals(0, nestedDepth)
                assertTrue(inTransaction())
                assertEquals(beforePreview, snapshot())
                // Android marks the enclosing transaction failed when the
                // nested edge ends without success, even if a caller tries this.
                setTransactionSuccessful()
            } finally {
                endTransaction()
            }
            assertFalse(inTransaction())
            assertEquals(beforeOuter, snapshot())
            transaction { execSQL("UPDATE meta_accounts SET name = name WHERE id = 1") }
            assertEquals(beforeOuter, snapshot())
        }
    }

    private fun withVersion31(suffix: String, foreignKeys: Boolean, action: SupportSQLiteDatabase.() -> Unit) {
        val schema = ReleasedSchemaFixtures.load(context, "2.0.0")
        assertEquals(30, schema.databaseVersion)
        val name = "historical-sql-preview-$suffix"
        check(createdDatabases.add(name))
        ReleasedSchemaFixtures.createDatabase(context, name, schema)
        val callback = object : SupportSQLiteOpenHelper.Callback(30) {
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
            assertEquals(30, database.version)
            database.transaction {
                MigrateTablesToV2_30_31.migrate(this)
                version = 31
                seedPublicRows()
            }
            database.execSQL("CREATE TEMP TABLE owned_preview_marker(note TEXT, bytes BLOB)")
            database.execSQL("INSERT INTO temp.owned_preview_marker VALUES ('original 一', X'0001FF')")
            database.action()
        } finally {
            helper.close()
        }
    }

    private fun SupportSQLiteDatabase.seedPublicRows() {
        execSQL(
            "INSERT INTO meta_accounts(id, substratePublicKey, substrateCryptoType, substrateAccountId, " +
                "ethereumPublicKey, ethereumAddress, name, isSelected, position) VALUES (1, ?, 'SR25519', ?, ?, ?, ?, 1, 0)",
            arrayOf(ByteArray(32) { 1 }, ByteArray(32) { 1 }, ByteArray(65) { 2 }, ByteArray(20) { 3 }, "Synthetic public wallet")
        )
        listOf(CUSTOM_CHAIN, DEFAULT_CHAIN).forEach { chain ->
            execSQL(
                "INSERT INTO chains(id, name, icon, prefix, isEthereumBased, isTestNet, hasCrowdloans) " +
                    "VALUES (?, ?, 'synthetic-icon', 2, 0, 0, 0)",
                arrayOf(chain, "Synthetic chain ${chain.first()}")
            )
        }
        execSQL(
            "INSERT INTO chain_nodes(chainId, url, name, isActive, isDefault) VALUES (?, ?, ?, 1, 0)",
            arrayOf(CUSTOM_CHAIN, "wss://custom-preview.invalid/rpc", "Custom '一' endpoint")
        )
        execSQL(
            "INSERT INTO chain_nodes(chainId, url, name, isActive, isDefault) VALUES (?, ?, ?, 1, 1)",
            arrayOf(DEFAULT_CHAIN, "wss://default-preview.invalid/rpc", "Selected default")
        )
        execSQL(
            "INSERT INTO chain_accounts(metaId, chainId, publicKey, accountId, cryptoType) VALUES (1, ?, ?, ?, 'SR25519')",
            arrayOf(DEFAULT_CHAIN, ByteArray(32) { 6 }, ByteArray(32) { 6 })
        )
        query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
    }

    private fun SupportSQLiteDatabase.runActualSqlGraph() {
        var next = 32
        HISTORICAL_SQL_MIGRATIONS_32_TO_71.forEach { edge ->
            assertEquals(next, edge.startVersion)
            edge.migrate(this)
            next = edge.endVersion
        }
        assertEquals(71, next)
    }

    private fun SupportSQLiteDatabase.transaction(action: SupportSQLiteDatabase.() -> Unit) {
        beginTransaction()
        try {
            action()
            setTransactionSuccessful()
        } finally {
            endTransaction()
        }
    }

    private data class Cell(val type: Int, val value: String?)
    private data class DatabaseSnapshot(
        val version: Int,
        val foreignKeys: Long,
        val schemas: Map<String, List<List<Cell>>>,
        val tables: Map<String, List<List<Cell>>>
    )

    private fun SupportSQLiteDatabase.snapshot(): DatabaseSnapshot {
        val schemas = linkedMapOf<String, List<List<Cell>>>()
        val tables = linkedMapOf<String, List<List<Cell>>>()
        listOf("main" to "sqlite_master", "temp" to "sqlite_temp_master").forEach { (schema, master) ->
            schemas[schema] = readRows("SELECT type, name, tbl_name, rootpage, sql FROM $master")
            query("SELECT name FROM $master WHERE type = 'table' ORDER BY name").use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0)
                    val escaped = name.replace("`", "``")
                    tables["$schema.$name"] = readRows("SELECT * FROM `$schema`.`$escaped`")
                }
            }
        }
        return DatabaseSnapshot(version, scalar("PRAGMA foreign_keys"), schemas, tables)
    }

    private fun SupportSQLiteDatabase.readRows(sql: String): List<List<Cell>> = query(sql).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(List(cursor.columnCount) { index ->
                    val type = cursor.getType(index)
                    Cell(type, when (type) {
                        Cursor.FIELD_TYPE_NULL -> null
                        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index).toString()
                        Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index).toRawBits().toString()
                        Cursor.FIELD_TYPE_STRING -> cursor.getString(index)
                        Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(index).joinToString("") { "%02x".format(it) }
                        else -> error("Unexpected SQLite storage class")
                    })
                })
            }
        }.sortedBy { row -> row.joinToString("") { cell -> "${cell.type}:${cell.value?.length}:${cell.value};" } }
    }

    private fun SupportSQLiteDatabase.scalar(sql: String): Long = query(sql).use { cursor ->
        assertTrue(cursor.moveToFirst())
        val result = cursor.getLong(0)
        assertFalse(cursor.moveToNext())
        result
    }

    private companion object {
        val CUSTOM_CHAIN = "a".repeat(64)
        val DEFAULT_CHAIN = "b".repeat(64)
    }
}
