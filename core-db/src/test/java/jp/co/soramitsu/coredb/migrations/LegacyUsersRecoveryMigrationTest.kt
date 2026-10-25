package jp.co.soramitsu.coredb.migrations

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock

class LegacyUsersRecoveryMigrationTest {
    @Test
    fun `absent users is read only admission without payload or transaction access`() {
        val fixture = Fixture(present = false)
        assertEquals(LegacyUsersRecoveryMigration.Source.ABSENT, fixture.classify())
        assertFalse(LegacyUsersRecoveryMigration.requireSafeRawArchive(fixture.database))
        assertTrue(fixture.statements.isEmpty())
        assertEquals(0, fixture.begins)
    }

    @Test
    fun `released five columns qualify only through the strict schema reader`() {
        val fixture = Fixture()
        assertEquals(LegacyUsersRecoveryMigration.Source.CANONICAL, fixture.classify())
        assertTrue(fixture.queries.any { it.contains("releasedBoundedObjectSql") })
        assertTrue(fixture.queries.any { it.contains("pragma_table_info") })
        assertTrue(fixture.statements.isEmpty())
    }

    @Test
    fun `unknown column remains an untouched raw source`() {
        val fixture = Fixture(publicKeyColumn = "unknownPublicKey")
        assertEquals(LegacyUsersRecoveryMigration.Source.RAW_ARCHIVE, fixture.classify())
        LegacyUsersRecoveryMigration.migrate(fixture.database)
        assertEquals(1, fixture.statements.size)
        assertTrue(fixture.statements.single().startsWith("CREATE TABLE main.`legacy_users_recovery`"))
    }

    @Test
    fun `nullable original field remains raw without reading its values`() {
        val fixture = Fixture(nullableName = true)
        assertEquals(LegacyUsersRecoveryMigration.Source.RAW_ARCHIVE, fixture.classify())
        fixture.assertMetadataOnly()
    }

    @Test
    fun `collation check and conflict clauses remain raw despite matching pragma columns`() {
        listOf(
            RELEASED_DDL.replace("username TEXT NOT NULL", "username TEXT NOT NULL COLLATE NOCASE"),
            RELEASED_DDL.replace("username TEXT NOT NULL", "username TEXT NOT NULL CHECK(length(username)>0)"),
            RELEASED_DDL.replace("PRIMARY KEY(address)", "PRIMARY KEY(address) ON CONFLICT IGNORE")
        ).forEach { ddl ->
            val fixture = Fixture(ddl = ddl)
            assertEquals(LegacyUsersRecoveryMigration.Source.RAW_ARCHIVE, fixture.classify())
            assertTrue(fixture.queries.none { it.contains("pragma_table_info") })
        }
    }

    @Test
    fun `released rename uses only qualified ddl and never copies deletes or coerces rows`() {
        val fixture = Fixture()
        LegacyUsersRecoveryMigration.migrate(fixture.database)
        assertEquals(2, fixture.statements.size)
        assertEquals("ALTER TABLE main.`users` RENAME TO `legacy_users_recovery`", fixture.statements.first())
        assertTrue(fixture.statements.last().contains("DEFAULT 'PRESERVED_PENDING_REVIEW'"))
        assertTrue(fixture.statements.none { sql -> listOf("INSERT", "UPDATE", "DELETE", "DROP", "REPLACE").any(sql::contains) })
        fixture.assertMetadataOnly()
    }

    @Test
    fun `missing source creates only an empty canonical ledger`() {
        val fixture = Fixture(present = false)
        LegacyUsersRecoveryMigration.migrate(fixture.database)
        assertEquals(1, fixture.statements.size)
        assertTrue(fixture.statements.single().startsWith("CREATE TABLE main.`legacy_users_recovery`"))
        assertFalse(fixture.statements.single().contains("IF NOT EXISTS"))
    }

    @Test
    fun `case variant source is never adopted as the exact named archive`() {
        val fixture = Fixture(sourceName = "UsErS")
        assertThrows(IllegalStateException::class.java) { fixture.classify() }
        assertTrue(fixture.statements.isEmpty())
    }

    @Test
    fun `views and virtual table definitions cannot become raw archives`() {
        listOf(Fixture(sourceType = "view"), Fixture(ddl = "CREATE VIRTUAL TABLE users USING fts4(address)")).forEach {
            assertThrows(IllegalStateException::class.java) { it.classify() }
            assertTrue(it.statements.isEmpty())
        }
    }

    @Test
    fun `extra source objects reject without changing or dropping them`() {
        val fixture = Fixture(extraObject = true)
        assertThrows(IllegalStateException::class.java) { LegacyUsersRecoveryMigration.migrate(fixture.database) }
        assertEquals(0, fixture.begins)
        assertTrue(fixture.statements.isEmpty())
    }

    @Test
    fun `declared foreign key rejects before any row or strict column traversal`() {
        val fixture = Fixture(foreignKey = true)
        assertThrows(IllegalStateException::class.java) { fixture.classify() }
        assertTrue(fixture.queries.none { it.contains("pragma_table_info") })
        fixture.assertMetadataOnly()
    }

    @Test
    fun `unique constraint autoindex is not mistaken for a primary key`() {
        val fixture = Fixture(indexOrigin = "u")
        assertThrows(IllegalStateException::class.java) { fixture.classify() }
        assertTrue(fixture.statements.isEmpty())
    }

    @Test
    fun `partial or nonunique owned index metadata is rejected`() {
        listOf(Fixture(indexPartial = 1L), Fixture(indexUnique = 0L)).forEach {
            assertThrows(IllegalStateException::class.java) { it.classify() }
        }
    }

    @Test
    fun `oversized schema sql is rejected at bounded metadata projection`() {
        val fixture = Fixture(oversizedDdl = true)
        assertThrows(IllegalStateException::class.java) { fixture.classify() }
        assertTrue(fixture.queries.none { it.contains("pragma_table_info") })
        assertTrue(fixture.statements.isEmpty())
    }

    @Test
    fun `temporary shadow fails before main source classification`() {
        val fixture = Fixture(temporaryCollision = true)
        assertThrows(IllegalStateException::class.java) { fixture.classify() }
        assertEquals(1, fixture.queries.size)
        assertTrue(fixture.queries.single().contains("COLLATE NOCASE"))
    }

    @Test
    fun `existing target fails before transaction or ddl without overwrite`() {
        val fixture = Fixture(targetCollision = true, outerTransaction = false)
        assertThrows(IllegalStateException::class.java) { LegacyUsersRecoveryMigration.migrate(fixture.database) }
        assertEquals(0, fixture.begins)
        assertTrue(fixture.statements.isEmpty())
    }

    @Test
    fun `target arriving before owned transaction is rechecked before ddl`() {
        val fixture = Fixture(targetAfterBegin = true, outerTransaction = false)
        assertThrows(IllegalStateException::class.java) { LegacyUsersRecoveryMigration.migrate(fixture.database) }
        assertEquals(1, fixture.begins)
        assertEquals(1, fixture.ends)
        assertEquals(0, fixture.successes)
        assertTrue(fixture.statements.isEmpty())
    }

    @Test
    fun `owned transaction succeeds only after both rename statements`() {
        val fixture = Fixture(outerTransaction = false)
        LegacyUsersRecoveryMigration.migrate(fixture.database)
        assertEquals(1, fixture.begins)
        assertEquals(1, fixture.ends)
        assertEquals(1, fixture.successes)
        assertEquals(2, fixture.statements.size)
    }

    @Test
    fun `post rename failure closes owned transaction without marking success`() {
        val failure = IllegalStateException("injected ADD failure")
        val fixture = Fixture(outerTransaction = false, mutationFailure = failure)
        assertSame(failure, assertThrows(IllegalStateException::class.java) { LegacyUsersRecoveryMigration.migrate(fixture.database) })
        assertEquals(1, fixture.begins)
        assertEquals(1, fixture.ends)
        assertEquals(0, fixture.successes)
    }

    @Test
    fun `caller transaction is never committed ended or nested on failure`() {
        val fixture = Fixture(mutationFailure = IllegalStateException("injected ADD failure"))
        assertThrows(IllegalStateException::class.java) { LegacyUsersRecoveryMigration.migrate(fixture.database) }
        assertEquals(0, fixture.begins)
        assertEquals(0, fixture.ends)
        assertEquals(0, fixture.successes)
    }

    @Test
    fun `cleanup failure is suppressed on the original mutation failure`() {
        val original = IllegalStateException("injected ADD failure")
        val cleanup = IllegalStateException("injected transaction cleanup failure")
        val fixture = Fixture(outerTransaction = false, mutationFailure = original, cleanupFailure = cleanup)
        assertSame(original, assertThrows(IllegalStateException::class.java) { LegacyUsersRecoveryMigration.migrate(fixture.database) })
        assertEquals(listOf(cleanup), original.suppressed.toList())
        assertEquals(1, fixture.ends)
        assertEquals(0, fixture.successes)
    }

    @Test
    fun `cleanup failure after successful ddl propagates instead of returning`() {
        val cleanup = IllegalStateException("injected transaction cleanup failure")
        val fixture = Fixture(outerTransaction = false, cleanupFailure = cleanup)
        assertSame(cleanup, assertThrows(IllegalStateException::class.java) { LegacyUsersRecoveryMigration.migrate(fixture.database) })
        assertEquals(1, fixture.ends)
        assertEquals(1, fixture.successes)
        assertEquals(2, fixture.statements.size)
    }

    @Test
    fun `metadata query failure propagates without being converted to an archive decision`() {
        val failure = IllegalStateException("injected schema read failure")
        val fixture = Fixture(readFailure = failure)
        assertSame(failure, assertThrows(IllegalStateException::class.java) { fixture.classify() })
        assertTrue(fixture.statements.isEmpty())
    }

    private class Fixture(
        val present: Boolean = true,
        val publicKeyColumn: String = "publicKey",
        val nullableName: Boolean = false,
        val ddl: String = RELEASED_DDL,
        val sourceName: String = "users",
        val sourceType: String = "table",
        val extraObject: Boolean = false,
        val foreignKey: Boolean = false,
        val indexOrigin: String = "pk",
        val indexUnique: Long = 1L,
        val indexPartial: Long = 0L,
        val oversizedDdl: Boolean = false,
        val temporaryCollision: Boolean = false,
        val targetCollision: Boolean = false,
        val targetAfterBegin: Boolean = false,
        val outerTransaction: Boolean = true,
        val mutationFailure: RuntimeException? = null,
        val cleanupFailure: RuntimeException? = null,
        val readFailure: RuntimeException? = null
    ) {
        val queries = mutableListOf<String>()
        val statements = mutableListOf<String>()
        var begins = 0
        var ends = 0
        var successes = 0
        val database: SupportSQLiteDatabase = mock {
            on { inTransaction() } doAnswer { outerTransaction || begins > ends }
            on { beginTransaction() } doAnswer { begins++; Unit }
            on { endTransaction() } doAnswer { ends++; cleanupFailure?.let { throw it }; Unit }
            on { setTransactionSuccessful() } doAnswer { successes++; Unit }
            on { query(any<String>()) } doAnswer { query(it.arguments[0] as String) }
            on { query(any<String>(), any<Array<Any?>>()) } doAnswer { query(it.arguments[0] as String) }
            on { execSQL(any<String>()) } doAnswer {
                val sql = it.arguments[0] as String
                statements += sql
                if (sql.contains("ADD COLUMN")) mutationFailure?.let { error -> throw error }
                Unit
            }
        }

        fun classify() = LegacyUsersRecoveryMigration.requireSafeSourceAndTarget(database)

        fun assertMetadataOnly() {
            assertTrue(queries.all { sql ->
                sql.contains("sqlite_master") || sql.contains("sqlite_temp_master") ||
                    sql.contains("PRAGMA main.") || sql.contains("pragma_table_info") || sql.contains("pragma_index_list")
            })
        }

        private fun query(sql: String): Cursor {
            queries += sql
            val rows: List<Map<String, Any?>> = when {
                sql.contains("sqlite_temp_master") -> truth(temporaryCollision)
                sql.startsWith("SELECT 1 FROM main.sqlite_master") -> truth(targetCollision || targetAfterBegin && begins > 0)
                sql.contains("AS sql_type") -> {
                    readFailure?.let { throw it }
                    if (!present) emptyList() else buildList {
                        add(mapOf("type" to sourceType, "name" to sourceName, "tbl_name" to sourceName, "sql" to if (oversizedDdl) null else ddl, "sql_type" to "text"))
                        add(mapOf("type" to "index", "name" to "sqlite_autoindex_users_1", "tbl_name" to sourceName, "sql" to null, "sql_type" to "null"))
                        if (extraObject) add(mapOf("type" to "trigger", "name" to "retained_trigger", "tbl_name" to "users", "sql" to "CREATE TRIGGER", "sql_type" to "text"))
                    }
                }
                sql.startsWith("PRAGMA main.foreign_key_list") -> truth(foreignKey)
                sql.startsWith("PRAGMA main.index_list") -> listOf(mapOf("name" to "sqlite_autoindex_users_1", "origin" to indexOrigin, "unique" to indexUnique, "partial" to indexPartial))
                sql.startsWith("SELECT type FROM sqlite_master") -> listOf(mapOf("type" to "table"))
                sql.contains("releasedBoundedObjectSql") -> listOf(bounded("releasedBoundedObjectType", "table") + bounded("releasedBoundedObjectSql", ddl))
                sql.contains("pragma_table_info") -> listOf("address", "username", publicKeyColumn, "cryptoType", "position").mapIndexed { index, name ->
                    mapOf("cid" to index.toLong(), "notnull" to if (index == 1 && nullableName) 0L else 1L, "pk" to if (index == 0) 1L else 0L) +
                        bounded("releasedBoundedColumnName", name) + bounded("releasedBoundedColumnType", if (index < 3) "TEXT" else "INTEGER") + bounded("releasedBoundedColumnDefault", null)
                }
                sql.contains("pragma_index_list") -> listOf(bounded("releasedBoundedIndexName", "sqlite_autoindex_users_1") + bounded("releasedBoundedIndexOrigin", "pk") + mapOf("unique" to 1L, "partial" to 0L))
                else -> error("Unexpected query: $sql")
            }
            return cursor(rows)
        }
    }

    private companion object {
        const val RELEASED_DDL = "CREATE TABLE users(address TEXT NOT NULL, username TEXT NOT NULL, publicKey TEXT NOT NULL, cryptoType INTEGER NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(address))"
        fun truth(value: Boolean) = if (value) listOf(mapOf("value" to 1L)) else emptyList()
        fun bounded(alias: String, value: String?): Map<String, Any?> = mapOf(
            alias to value, "${alias}StorageClass" to if (value == null) "null" else "text",
            "${alias}ByteLength" to value?.toByteArray()?.size?.toLong()
        )
        fun cursor(rows: List<Map<String, Any?>>): Cursor {
            val names = rows.flatMap { it.keys }.distinct()
            var row = -1
            fun value(index: Int) = rows[row][names[index]]
            return mock {
                on { moveToFirst() } doAnswer { row = 0; rows.isNotEmpty() }
                on { moveToNext() } doAnswer { ++row < rows.size }
                on { getColumnIndexOrThrow(any()) } doAnswer { checkNotNull(names.indexOf(it.arguments[0] as String).takeIf { index -> index >= 0 }) }
                on { isNull(any()) } doAnswer { value(it.arguments[0] as Int) == null }
                on { getType(any()) } doAnswer {
                    when (value(it.arguments[0] as Int)) {
                        null -> Cursor.FIELD_TYPE_NULL
                        is Long -> Cursor.FIELD_TYPE_INTEGER
                        else -> Cursor.FIELD_TYPE_STRING
                    }
                }
                on { getLong(any()) } doAnswer { value(it.arguments[0] as Int) as Long }
                on { getInt(any()) } doAnswer { (value(it.arguments[0] as Int) as Long).toInt() }
                on { getString(any()) } doAnswer { value(it.arguments[0] as Int) as String? }
            }
        }
    }
}
