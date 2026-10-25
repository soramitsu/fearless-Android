package jp.co.soramitsu.coredb.migrations

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock

/**
 * JVM stand-in for the few bounded queries the wallet-secret passes run.
 * Rows are keyed by column name; the cursor answers every alias a bounded
 * projection (`typeof(col) AS <alias>StorageClass`, ...) declares for that
 * column, so the production readers run unchanged against it.
 */
internal class FakeWalletDatabase(
    private val tables: Map<String, List<Map<String, Any?>>>
) {

    val database: SupportSQLiteDatabase = mock {
        on { query(any<String>()) } doAnswer { invocation ->
            cursorFor(invocation.arguments[0] as String)
        }
        on { query(any<String>(), any<Array<Any?>>()) } doAnswer { invocation ->
            cursorFor(invocation.arguments[0] as String, (invocation.arguments[1] as Array<*>).toList())
        }
    }

    private fun cursorFor(sql: String, arguments: List<Any?> = emptyList()): Cursor {
        val table = FROM_TABLE.find(sql)?.groupValues?.get(1)
            ?: error("Unsupported query: $sql")
        val rows = filteredRows(table, sql, arguments)
        val columnByAlias = BOUNDED_ALIAS.findAll(sql).associate { match ->
            match.groupValues[2] to match.groupValues[1].substringAfterLast('.')
        }
        val columnNames = columnByAlias.keys.flatMap { alias ->
            listOf("${alias}StorageClass", "${alias}ByteLength", alias)
        } + (if ("rowid AS revalidationRowId" in sql) listOf("revalidationRowId") else emptyList()) +
            if (sql.startsWith("SELECT 1 ")) listOf("1") else emptyList()
        var position = -1

        fun value(index: Int): Any? {
            val name = columnNames[index]
            if (name == "revalidationRowId") return rows[position].getValue("rowid")
            if (name == "1") return 1L
            val alias = columnByAlias.keys.first {
                name == it || name == "${it}StorageClass" || name == "${it}ByteLength"
            }
            val raw = rows[position][columnByAlias.getValue(alias)]
            return when (name) {
                "${alias}StorageClass" -> when (raw) {
                    null -> "null"
                    is Long, is Int -> "integer"
                    is ByteArray -> "blob"
                    is String -> "text"
                    else -> error("Unsupported value $raw")
                }

                "${alias}ByteLength" -> when (raw) {
                    is ByteArray -> raw.size.toLong()
                    is String -> raw.toByteArray(Charsets.UTF_8).size.toLong()
                    else -> null
                }

                else -> raw
            }
        }

        return mock {
            on { moveToFirst() } doAnswer {
                position = 0
                rows.isNotEmpty()
            }
            on { moveToNext() } doAnswer {
                position += 1
                position < rows.size
            }
            on { getColumnIndexOrThrow(any()) } doAnswer { invocation ->
                columnNames.indexOf(invocation.arguments[0] as String).also {
                    check(it >= 0) { "Unknown column ${invocation.arguments[0]}" }
                }
            }
            on { isNull(any()) } doAnswer { value(it.arguments[0] as Int) == null }
            on { getType(any()) } doAnswer {
                when (value(it.arguments[0] as Int)) {
                    null -> Cursor.FIELD_TYPE_NULL
                    is Long, is Int -> Cursor.FIELD_TYPE_INTEGER
                    is ByteArray -> Cursor.FIELD_TYPE_BLOB
                    else -> Cursor.FIELD_TYPE_STRING
                }
            }
            on { getString(any()) } doAnswer { value(it.arguments[0] as Int) as String? }
            on { getLong(any()) } doAnswer { (value(it.arguments[0] as Int) as Number).toLong() }
            on { getBlob(any()) } doAnswer { value(it.arguments[0] as Int) as ByteArray? }
        }
    }

    private fun filteredRows(
        table: String,
        sql: String,
        arguments: List<Any?>
    ): List<Map<String, Any?>> {
        var rows = tables[table].orEmpty().mapIndexed { index, row ->
            mapOf("rowid" to index + 1L) + row
        }
        if ("WHERE id = ?" in sql) rows = rows.filter { it["id"] == arguments[0] }
        if ("WHERE metaId = ?" in sql) rows = rows.filter { it["metaId"] == arguments[0] }
        if ("AND rowid > ?" in sql) {
            rows = rows.filter {
                (it.getValue("rowid") as Number).toLong() > (arguments[1] as Number).toLong()
            }
        }
        if ("WHERE typeof(metaId) != 'integer' OR metaId <= 0" in sql) {
            rows = rows.filter {
            val id = it["metaId"]
                id !is Long && id !is Int || (id as Number).toLong() <= 0
            }
        }
        Regex("LIMIT (\\d+)").find(sql)?.groupValues?.get(1)?.toInt()?.let { rows = rows.take(it) }
        return rows
    }

    companion object {
        private val FROM_TABLE = Regex("""FROM\s+`?(\w+)`?""")
        private val BOUNDED_ALIAS = Regex("""typeof\(([\w.]+)\) AS (\w+)StorageClass""")

        fun empty() = FakeWalletDatabase(emptyMap())
    }
}
