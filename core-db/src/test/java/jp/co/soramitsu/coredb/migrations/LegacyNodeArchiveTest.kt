package jp.co.soramitsu.coredb.migrations

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class LegacyNodeArchiveTest {
    @Test
    fun `absent archive is false without reading rows or requiring a transaction`() {
        val fixture = Fixture(present = false)
        assertFalse(LegacyNodeArchive.requireSafe(fixture.database))
        assertTrue(fixture.queries.size == 2)
        fixture.assertReadOnlyMetadata()
    }

    @Test
    fun `released id first cohort has no declared defaults`() {
        val fixture = Fixture()
        assertTrue(LegacyNodeArchive.requireSafe(fixture.database))
        fixture.assertReadOnlyMetadata()
    }

    @Test
    fun `released fresh id last cohort has no declared defaults`() {
        val fixture = Fixture(order = FRESH_ID_LAST)
        assertTrue(LegacyNodeArchive.requireSafe(fixture.database))
        fixture.assertReadOnlyMetadata()
    }

    @Test
    fun `DB10 append cohort admits only the actual numeric active default`() {
        val fixture = Fixture(activeDefault = "0")
        assertTrue(LegacyNodeArchive.requireSafe(fixture.database))
        fixture.assertReadOnlyMetadata()
    }

    @Test
    fun `DB9 append cohort keeps id before the new active column`() {
        val fixture = Fixture(order = LEGACY_ID_BEFORE_ACTIVE, activeDefault = "0")
        assertTrue(LegacyNodeArchive.requireSafe(fixture.database))
        fixture.assertReadOnlyMetadata()
    }

    @Test
    fun `order and defaults are not admitted as an unshipped cross product`() {
        listOf(
            Fixture(order = FRESH_ID_LAST, activeDefault = "0"),
            Fixture(order = LEGACY_ID_BEFORE_ACTIVE),
            Fixture(activeDefault = "'0'"),
            Fixture(activeDefault = "1")
        ).forEach { fixture ->
            assertThrows(IllegalStateException::class.java) { LegacyNodeArchive.requireSafe(fixture.database) }
            fixture.assertReadOnlyMetadata()
        }
    }

    @Test
    fun `opaque endpoint values do not participate in metadata admission`() {
        // The double has no row query implementation. Any payload scan is an explicit failure.
        val fixture = Fixture()
        assertTrue(LegacyNodeArchive.requireSafe(fixture.database))
        fixture.assertReadOnlyMetadata()
    }

    @Test
    fun `temporary source shadow rejects before main metadata is read`() {
        val fixture = Fixture(temporaryShadow = true)
        assertThrows(IllegalStateException::class.java) { LegacyNodeArchive.requireSafe(fixture.database) }
        assertTrue(fixture.queries.size == 1)
        fixture.assertReadOnlyMetadata()
    }

    @Test
    fun `case variant source is not silently adopted as an exact archive`() {
        val fixture = Fixture(sourceName = "NoDeS")
        assertThrows(IllegalStateException::class.java) { LegacyNodeArchive.requireSafe(fixture.database) }
        fixture.assertReadOnlyMetadata()
    }

    @Test
    fun `source view or additional owned object remains unsupported`() {
        listOf(Fixture(sourceType = "view"), Fixture(extraObject = true)).forEach { fixture ->
            assertThrows(IllegalStateException::class.java) { LegacyNodeArchive.requireSafe(fixture.database) }
            fixture.assertReadOnlyMetadata()
        }
    }

    @Test
    fun `foreign key and index metadata reject without row scans`() {
        listOf(Fixture(foreignKey = true), Fixture(indexPresent = true)).forEach { fixture ->
            assertThrows(IllegalStateException::class.java) { LegacyNodeArchive.requireSafe(fixture.database) }
            fixture.assertReadOnlyMetadata()
        }
    }

    @Test
    fun `unsafe or oversized schema object metadata is rejected`() {
        val fixture = Fixture(unsafeName = true)
        assertThrows(IllegalStateException::class.java) { LegacyNodeArchive.requireSafe(fixture.database) }
        fixture.assertReadOnlyMetadata()
    }

    @Test
    fun `strict ordinary ddl checks reject unexpected constraints and virtual tables`() {
        listOf(
            "CREATE VIRTUAL TABLE nodes USING fts4(name)",
            ddl(ID_FIRST, null).replace("name TEXT NOT NULL", "name TEXT NOT NULL COLLATE NOCASE"),
            ddl(ID_FIRST, null).replace("name TEXT NOT NULL", "name TEXT NOT NULL CHECK(length(name)>0)")
        ).forEach { sql ->
            val fixture = Fixture(definition = sql)
            assertThrows(IllegalStateException::class.java) { LegacyNodeArchive.requireSafe(fixture.database) }
            fixture.assertReadOnlyMetadata()
        }
    }

    @Test
    fun `missing extra nullable and nonprimary identity columns are rejected`() {
        listOf(
            Fixture(order = ID_FIRST - "isActive"),
            Fixture(order = ID_FIRST + "unreleased"),
            Fixture(nullableName = true),
            Fixture(idPrimaryKey = false)
        ).forEach { fixture ->
            assertThrows(IllegalStateException::class.java) { LegacyNodeArchive.requireSafe(fixture.database) }
            fixture.assertReadOnlyMetadata()
        }
    }

    @Test
    fun `unknown schema IO failure propagates instead of being interpreted as absent`() {
        val failure = IllegalStateException("injected metadata IO failure")
        val fixture = Fixture(schemaFailure = failure)
        assertSame(failure, assertThrows(IllegalStateException::class.java) { LegacyNodeArchive.requireSafe(fixture.database) })
        fixture.assertReadOnlyMetadata()
    }

    @Test
    fun `strict column read failure propagates instead of being converted to a supported shape`() {
        val failure = IllegalStateException("injected column IO failure")
        val fixture = Fixture(columnFailure = failure)
        assertSame(failure, assertThrows(IllegalStateException::class.java) { LegacyNodeArchive.requireSafe(fixture.database) })
        fixture.assertReadOnlyMetadata()
    }

    private class Fixture(
        val present: Boolean = true,
        val order: List<String> = ID_FIRST,
        val activeDefault: String? = null,
        val definition: String = ddl(order, activeDefault),
        val sourceName: String = "nodes",
        val sourceType: String = "table",
        val temporaryShadow: Boolean = false,
        val extraObject: Boolean = false,
        val foreignKey: Boolean = false,
        val indexPresent: Boolean = false,
        val unsafeName: Boolean = false,
        val nullableName: Boolean = false,
        val idPrimaryKey: Boolean = true,
        val schemaFailure: RuntimeException? = null,
        val columnFailure: RuntimeException? = null
    ) {
        val queries = mutableListOf<String>()
        val database: SupportSQLiteDatabase = mock {
            on { query(any<String>()) } doAnswer { query(it.arguments[0] as String) }
            on { query(any<String>(), any<Array<Any?>>()) } doAnswer { query(it.arguments[0] as String) }
        }

        fun assertReadOnlyMetadata() {
            verify(database, never()).inTransaction()
            verify(database, never()).beginTransaction()
            verify(database, never()).execSQL(any<String>())
            verify(database, never()).execSQL(any<String>(), any<Array<Any?>>())
            assertTrue(queries.all { sql ->
                sql.contains("sqlite_master") || sql.contains("sqlite_temp_master") ||
                    sql.contains("PRAGMA main.") || sql.contains("pragma_table_info") || sql.contains("pragma_index_list")
            })
        }

        private fun query(sql: String): Cursor {
            queries += sql
            val rows: List<Map<String, Any?>> = when {
                sql.contains("sqlite_temp_master") -> truth(temporaryShadow)
                sql.contains("FROM main.sqlite_master") -> {
                    schemaFailure?.let { throw it }
                    if (!present) emptyList() else buildList {
                        add(mapOf("type" to sourceType, "name" to if (unsafeName) null else sourceName, "tbl_name" to sourceName))
                        if (extraObject) add(mapOf("type" to "index", "name" to "unowned_index", "tbl_name" to "nodes"))
                    }
                }
                sql.startsWith("PRAGMA main.foreign_key_list") -> truth(foreignKey)
                sql.startsWith("PRAGMA main.index_list") -> truth(indexPresent)
                sql.startsWith("SELECT type FROM sqlite_master") -> listOf(mapOf("type" to "table"))
                sql.contains("releasedBoundedObjectSql") -> listOf(bounded("releasedBoundedObjectType", "table") + bounded("releasedBoundedObjectSql", definition))
                sql.contains("pragma_table_info") -> {
                    columnFailure?.let { throw it }
                    order.mapIndexed { index, name ->
                        mapOf("cid" to index.toLong(), "notnull" to if (name == "name" && nullableName) 0L else 1L, "pk" to if (name == "id" && idPrimaryKey) 1L else 0L) +
                            bounded("releasedBoundedColumnName", name) + bounded("releasedBoundedColumnType", if (name in listOf("name", "link")) "TEXT" else "INTEGER") +
                            bounded("releasedBoundedColumnDefault", if (name == "isActive") activeDefault else null)
                    }
                }
                sql.contains("pragma_index_list") -> emptyList()
                else -> error("Archive admission must not read payload: $sql")
            }
            return cursor(rows)
        }
    }

    private companion object {
        val ID_FIRST = listOf("id", "name", "link", "networkType", "isDefault", "isActive")
        val FRESH_ID_LAST = listOf("name", "link", "networkType", "isDefault", "isActive", "id")
        val LEGACY_ID_BEFORE_ACTIVE = listOf("name", "link", "networkType", "isDefault", "id", "isActive")
        fun ddl(order: List<String>, activeDefault: String?): String = "CREATE TABLE nodes(" + order.joinToString { name ->
            when (name) {
                "id" -> "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL"
                "name", "link" -> "$name TEXT NOT NULL"
                else -> "$name INTEGER NOT NULL" + if (name == "isActive" && activeDefault != null) " DEFAULT $activeDefault" else ""
            }
        } + ")"
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
