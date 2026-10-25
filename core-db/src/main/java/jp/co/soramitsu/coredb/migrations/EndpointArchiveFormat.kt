package jp.co.soramitsu.coredb.migrations

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.Locale

/** Fixed, source-owned formats for historical and current endpoint snapshots. */
internal class EndpointArchiveFormat private constructor(
    private val ARCHIVE: String,
    private val HEADER: String,
    private val SCHEMA: String,
    private val EDGES: Set<Int>
) {
    private val MAX_METADATA_BYTES = 16_384
    private val MAX_NAME_BYTES = 256
    private val FIELDS = listOf("chainId", "url", "name", "isActive", "isDefault")
    private val MAX_ROWS = TON_UPGRADE_FOREIGN_KEY_CHECK_LIMITS.maximumRowsByTable.getValue("chain_nodes")
    private val NAMES = listOf(ARCHIVE, HEADER, SCHEMA)
    private val NAME_PREDICATE = NAMES.joinToString(" OR ") { "name = '$it' COLLATE NOCASE OR tbl_name = '$it' COLLATE NOCASE" }
    private val SOURCE_PREDICATE = "name = 'chain_nodes' COLLATE NOCASE OR tbl_name = 'chain_nodes' COLLATE NOCASE"
    private val CREATE = linkedMapOf(
        ARCHIVE to "CREATE TABLE $ARCHIVE (source_rowid INTEGER PRIMARY KEY, chainId, url, name, isActive, isDefault)",
        HEADER to "CREATE TABLE $HEADER (id INTEGER PRIMARY KEY, format_version INTEGER NOT NULL, source_edge_version INTEGER NOT NULL, source_user_version INTEGER NOT NULL, source_row_count INTEGER NOT NULL, source_payload_bytes INTEGER NOT NULL, source_schema_count INTEGER NOT NULL)",
        SCHEMA to "CREATE TABLE $SCHEMA (source_schema_rowid INTEGER PRIMARY KEY, type, name, tbl_name, sql)"
    )

    val schemaObjects: Set<ReleasedRoomSchemaObjectSpec> = NAMES.map {
        ReleasedRoomSchemaObjectSpec("table", it, it)
    }.toSet()

    /**
     * Returns true only for a new capture; false means an admitted earliest capture
     * was retained or the source cache was absent.
     * Joins Room's transaction, or owns a transaction for a direct caller. A failure
     * in a caller-owned transaction must propagate so that caller rolls it back.
     * sourceEdgeVersion is distinct from Room's still-original PRAGMA user_version.
     */
    fun capture(
        database: SupportSQLiteDatabase,
        sourceEdgeVersion: Int,
        payloadLimits: TonUpgradePayloadLimits = TonUpgradePayloadLimits.PRODUCTION
    ): Boolean {
        require(sourceEdgeVersion in EDGES) { "Unsupported modern endpoint capture edge" }
        val ownsTransaction = !database.inTransaction()
        if (ownsTransaction) database.beginTransaction()
        var failure: Throwable? = null
        try {
            val captured = if (requireSafe(database)) {
                false
            } else {
                captureFirst(database, sourceEdgeVersion, payloadLimits)
            }
            if (ownsTransaction) database.setTransactionSuccessful()
            return captured
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            if (ownsTransaction) {
                try {
                    database.endTransaction()
                } catch (cleanup: Throwable) {
                    if (failure == null) throw cleanup else failure.addSuppressed(cleanup)
                }
            }
        }
    }

    /** Read-only: absence is false; partial, colliding or malformed archives throw. */
    fun requireSafe(database: SupportSQLiteDatabase): Boolean {
        requireNoTemporaryObjects(database, NAME_PREDICATE)
        val objects = readSchema(database, "main.sqlite_master", "rowid", NAME_PREDICATE, 3)
        if (objects.isEmpty()) return false
        check(objects.size == 3 && objects.map { it.name }.toSet() == NAMES.toSet()) {
            "Modern endpoint archive has partial or colliding schema objects"
        }
        objects.forEach { row ->
            check(row.type == "table" && row.owner == row.name && row.sql == CREATE[row.name]) {
                "Modern endpoint archive has an unsupported exact table definition"
            }
        }
        val header = readHeader(database)
        val sourceSchema = readSchema(database, "main.$SCHEMA", "source_schema_rowid", "1", 3)
        requireSupportedSourceSchema(sourceSchema)
        check(sourceSchema.size.toLong() == header.schemaCount) { "Modern endpoint archive schema count changed" }
        val count = boundedCount(database, ARCHIVE)
        check(count == header.rowCount) { "Modern endpoint archive row count changed" }
        val bytes = requireBoundedHistoricalPayload(database, ARCHIVE, FIELDS, MAX_ROWS, 0L, TonUpgradePayloadLimits.PRODUCTION)
        check(bytes == header.payloadBytes) { "Modern endpoint archive payload accounting changed" }
        return true
    }

    private fun captureFirst(database: SupportSQLiteDatabase, edge: Int, limits: TonUpgradePayloadLimits): Boolean {
        requireNoTemporaryObjects(database, SOURCE_PREDICATE)
        val sourceSchema = readSchema(database, "main.sqlite_master", "rowid", SOURCE_PREDICATE, 3)
        if (sourceSchema.isEmpty()) return false
        val canonical = requireSupportedSourceSchema(sourceSchema)
        requireSourceColumns(database, canonical, sourceSchema)
        val count = boundedCount(database, "chain_nodes")
        // Retain the existing chain_nodes.<column> budget diagnostics, including name.
        val bytes = requireBoundedHistoricalPayload(database, "chain_nodes", FIELDS, MAX_ROWS, 0L, limits)
        val userVersion = singleInteger(database, "PRAGMA main.user_version")
        check(userVersion in 0..Int.MAX_VALUE.toLong()) { "Modern endpoint source version is invalid" }
        CREATE.values.forEach(database::execSQL)
        database.execSQL(
            "INSERT INTO main.$ARCHIVE (source_rowid, ${FIELDS.joinToString()}) " +
                "SELECT _rowid_, ${FIELDS.joinToString()} FROM main.chain_nodes"
        )
        database.execSQL(
            "INSERT INTO main.$SCHEMA (source_schema_rowid, type, name, tbl_name, sql) " +
                "SELECT rowid, type, name, tbl_name, sql FROM main.sqlite_master WHERE $SOURCE_PREDICATE"
        )
        check(boundedCount(database, ARCHIVE) == count) { "Modern endpoint copy row count differs" }
        val unequal = FIELDS.joinToString(" OR ") { column ->
            "typeof(a.$column) != typeof(s.$column) OR " +
                "CASE WHEN typeof(s.$column) IN ('text','blob') " +
                "THEN NOT (CAST(a.$column AS BLOB) IS CAST(s.$column AS BLOB)) " +
                "ELSE NOT (a.$column IS s.$column) END"
        }
        database.query(
            "SELECT 1 FROM main.chain_nodes AS s LEFT JOIN main.$ARCHIVE AS a ON a.source_rowid = s._rowid_ " +
                "WHERE a.source_rowid IS NULL OR ($unequal) LIMIT 1"
        ).use { check(!it.moveToFirst()) { "Modern endpoint copy changed a row identity, storage class or value" } }
        check(readSchema(database, "main.$SCHEMA", "source_schema_rowid", "1", 3) == sourceSchema) {
            "Modern endpoint copy changed source schema provenance"
        }
        // The complete marker is last: a missing header row is never an accepted archive.
        database.execSQL(
            "INSERT INTO main.$HEADER (id, format_version, source_edge_version, source_user_version, source_row_count, source_payload_bytes, source_schema_count) VALUES (1,1,?,?,?,?,?)",
            arrayOf<Any?>(edge, userVersion, count, bytes, sourceSchema.size)
        )
        check(requireSafe(database)) { "Modern endpoint capture did not produce a complete archive" }
        return true
    }

    private fun requireSourceColumns(database: SupportSQLiteDatabase, canonical: Boolean, sourceSchema: List<ModernEndpointSchemaRow>) {
        // The admitted DDL has no hidden/generated/rowid-shadowing columns. Confirm the
        // actual catalog's order/types/constraints with bounded scalar metadata too.
        val reordered = ddlTokens(checkNotNull(sourceSchema.single { it.type == "table" }.sql)) == REPAIR_SOURCE_DDL
        val names = if (reordered) listOf("url", "chainId", "isDefault", "isActive", "name") else FIELDS
        val expectedTypes = names.map { if (!canonical) "" else if (it in listOf("isActive", "isDefault")) "INTEGER" else "TEXT" }
        database.query(
            "SELECT cid, ${boundedText("name", MAX_NAME_BYTES)}, ${boundedText("type", MAX_NAME_BYTES)}, " +
                "`notnull`, ${boundedText("dflt_value", MAX_NAME_BYTES)}, pk " +
                "FROM pragma_table_info('chain_nodes') ORDER BY cid LIMIT 6"
        ).use { cursor ->
            var index = 0
            while (cursor.moveToNext()) {
                check(index < 5 && cursor.integer(0) == index.toLong()) { "Modern endpoint source has unexpected columns" }
                check(cursor.text(1) == names[index] && cursor.text(2).uppercase(Locale.US) == expectedTypes[index]) {
                    "Modern endpoint source column identity or type differs"
                }
                check(cursor.integer(3) == if (canonical) 1L else 0L) { "Modern endpoint source nullability differs" }
                val default = if (cursor.isNull(4)) null else cursor.text(4)
                check(default == null || canonical && names[index] == "isActive" && default == "0" || canonical && names[index] == "isDefault" && default == "1") {
                    "Modern endpoint source default differs"
                }
                check(cursor.integer(5) == if (!canonical) 0L else when (names[index]) { "chainId" -> 1L; "url" -> 2L; else -> 0L }) {
                    "Modern endpoint source primary key differs"
                }
                index++
            }
            check(index == 5) { "Modern endpoint source is missing columns" }
        }
    }

    private fun requireNoTemporaryObjects(database: SupportSQLiteDatabase, predicate: String) {
        database.query("SELECT 1 FROM sqlite_temp_master WHERE $predicate LIMIT 1").use {
            check(!it.moveToFirst()) { "Modern endpoint archive has a temporary namespace collision" }
        }
    }

    private fun boundedCount(database: SupportSQLiteDatabase, table: String): Long {
        val count = singleInteger(database, "SELECT COUNT(*) FROM (SELECT 1 FROM main.$table LIMIT ${MAX_ROWS + 1})")
        if (count > MAX_ROWS) throw MigrationPayloadLimitException("Historical $table rows exceed the safe migration limit")
        check(count >= 0L) { "Modern endpoint archive row count is invalid" }
        return count
    }

    private fun singleInteger(database: SupportSQLiteDatabase, sql: String): Long = database.query(sql).use {
        check(it.moveToFirst()) { "Modern endpoint archive scalar result is missing" }
        val value = it.integer(0)
        check(!it.moveToNext()) { "Modern endpoint archive scalar result is ambiguous" }
        value
    }

    private fun readHeader(database: SupportSQLiteDatabase): Header = database.query(
        "SELECT id, format_version, source_edge_version, source_user_version, source_row_count, source_payload_bytes, source_schema_count FROM main.$HEADER LIMIT 2"
    ).use { cursor ->
        check(cursor.moveToFirst()) { "Modern endpoint archive has no complete header" }
        val values = (0..6).map { cursor.integer(it) }
        check(values[0] == 1L && values[1] == 1L && values[2] in EDGES.map(Int::toLong) &&
            values[3] in 0..Int.MAX_VALUE.toLong() && values[4] in 0..MAX_ROWS.toLong() &&
            values[5] in 0..TonUpgradePayloadLimits.PRODUCTION.maximumTotalBytes && values[6] in 1L..3L) {
            "Modern endpoint archive header is unsupported"
        }
        check(!cursor.moveToNext()) { "Modern endpoint archive header is ambiguous" }
        Header(values[4], values[5], values[6])
    }

    private data class Header(val rowCount: Long, val payloadBytes: Long, val schemaCount: Long)

    private fun readSchema(
        database: SupportSQLiteDatabase,
        table: String,
        rowid: String,
        predicate: String,
        maximum: Int
    ): List<ModernEndpointSchemaRow> = database.query(
        "SELECT $rowid, ${boundedText("type", MAX_NAME_BYTES)}, ${boundedText("name", MAX_NAME_BYTES)}, " +
            "${boundedText("tbl_name", MAX_NAME_BYTES)}, typeof(sql), ${boundedText("sql", MAX_METADATA_BYTES)} " +
            "FROM $table WHERE $predicate ORDER BY $rowid LIMIT ${maximum + 1}"
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                check(size < maximum) { "Modern endpoint archive has too many owned schema objects" }
                val sqlType = cursor.text(4)
                check(sqlType == "text" || sqlType == "null") { "Modern endpoint archive has unsafe SQL metadata" }
                val sql = if (sqlType == "null") null else cursor.text(5)
                add(ModernEndpointSchemaRow(cursor.integer(0), cursor.text(1), cursor.text(2), cursor.text(3), sql))
            }
        }
    }

    private fun boundedText(column: String, bytes: Int) =
        "CASE WHEN typeof($column) = 'text' AND length(CAST($column AS BLOB)) <= $bytes THEN $column ELSE NULL END"

    private fun Cursor.integer(index: Int): Long {
        check(getType(index) == Cursor.FIELD_TYPE_INTEGER) { "Modern endpoint archive has unsafe integer metadata" }
        return getLong(index)
    }

    private fun Cursor.text(index: Int): String {
        check(getType(index) == Cursor.FIELD_TYPE_STRING) { "Modern endpoint archive has unsafe text metadata" }
        return getString(index)
    }

    /** Narrow, source-only grammar. No SQLite metadata SQL is ever executed. */
    internal fun requireSupportedSourceSchema(rows: List<ModernEndpointSchemaRow>): Boolean {
        check(rows.size in 1..3 && rows.map { it.rowid }.toSet().size == rows.size && rows.map { it.name }.toSet().size == rows.size) {
            "Modern endpoint source schema is missing or ambiguous"
        }
        val table = rows.singleOrNull { it.type == "table" && it.name == "chain_nodes" && it.owner == "chain_nodes" }
            ?: error("Modern endpoint source is not the exact named ordinary main table")
        val tokens = ddlTokens(checkNotNull(table.sql))
        val reordered = tokens == REPAIR_SOURCE_DDL
        val canonical = tokens in CANONICAL_SOURCE_DDL || reordered
        check(canonical || tokens in RAW_SOURCE_DDL) { "Modern endpoint source DDL is outside the bounded cohort" }
        val expected = mutableSetOf("chain_nodes")
        if (canonical) expected += "sqlite_autoindex_chain_nodes_1"
        rows.filter { it !== table }.forEach { row ->
            check(row.type == "index" && row.owner == "chain_nodes") { "Modern endpoint source has an unsupported owned object" }
            when (row.name) {
                "sqlite_autoindex_chain_nodes_1" -> check(canonical && row.sql == null) { "Modern endpoint source primary index differs" }
                "index_chain_nodes_chainId" -> {
                    check(canonical && !reordered && row.sql != null && ddlTokens(row.sql) == EXPLICIT_INDEX_DDL) { "Modern endpoint source explicit index differs" }
                    expected += row.name
                }
                "index_chain_nodes_url" -> {
                    check(reordered && row.sql != null && ddlTokens(row.sql) == REPAIR_INDEX_DDL) { "Modern endpoint source repair index differs" }
                    expected += row.name
                }
                else -> error("Modern endpoint source has an unsupported index")
            }
        }
        check(rows.map { it.name }.toSet() == expected) { "Modern endpoint source is missing its declared primary index" }
        return canonical
    }

    private fun ddlTokens(sql: String): List<String> {
        check(sql.length <= MAX_METADATA_BYTES) { "Modern endpoint source DDL exceeds the metadata budget" }
        val tokens = mutableListOf<String>()
        var index = 0
        while (index < sql.length) {
            val char = sql[index]
            when {
                char.isWhitespace() -> index++
                char in "()," -> { tokens += char.toString(); index++ }
                char == '`' || char == '"' || char == '[' -> {
                    val end = if (char == '[') ']' else char
                    val start = ++index
                    while (index < sql.length && sql[index] != end) index++
                    check(index < sql.length) { "Modern endpoint source has unterminated identifier metadata" }
                    val identifier = sql.substring(start, index++)
                    check(identifier.isNotEmpty() && identifier.all { it == '_' || it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }) {
                        "Modern endpoint source has unsupported identifier metadata"
                    }
                    tokens += identifier.uppercase(Locale.US)
                }
                char == '_' || char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' -> {
                    val start = index++
                    while (index < sql.length && (sql[index] == '_' || sql[index] in 'a'..'z' || sql[index] in 'A'..'Z' || sql[index] in '0'..'9')) index++
                    tokens += sql.substring(start, index).uppercase(Locale.US)
                }
                else -> error("Modern endpoint source has unsupported DDL tokens")
            }
        }
        if (tokens.take(5) == listOf("CREATE", "TABLE", "IF", "NOT", "EXISTS") ||
            tokens.take(5) == listOf("CREATE", "INDEX", "IF", "NOT", "EXISTS")) {
            repeat(3) { tokens.removeAt(2) }
        }
        return tokens
    }

    private val EXPLICIT_INDEX_DDL = ddlTokens("CREATE INDEX index_chain_nodes_chainId ON chain_nodes (chainId)")
    private val REPAIR_INDEX_DDL = ddlTokens("CREATE INDEX index_chain_nodes_url ON chain_nodes (url)")
    // Explicit existing RebuildableTableRepairTest cohort; copy remains by column name.
    private val REPAIR_SOURCE_DDL = ddlTokens("CREATE TABLE chain_nodes (url TEXT NOT NULL, chainId TEXT NOT NULL, isDefault INTEGER NOT NULL DEFAULT 1, isActive INTEGER NOT NULL, name TEXT NOT NULL, PRIMARY KEY(chainId,url), FOREIGN KEY(chainId) REFERENCES chains(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
    private val CANONICAL_SOURCE_DDL = buildSet {
        val columns = "chainId TEXT NOT NULL, url TEXT NOT NULL, name TEXT NOT NULL"
        for (parent in listOf("chains", "_chains")) {
            val constraints = "PRIMARY KEY(chainId,url), FOREIGN KEY(chainId) REFERENCES $parent(id) ON UPDATE NO ACTION ON DELETE CASCADE"
            add(ddlTokens("CREATE TABLE chain_nodes ($columns, isActive INTEGER NOT NULL, isDefault INTEGER NOT NULL, $constraints)"))
            // SQLite inserts actual <=31 ALTER-added columns before the existing constraints.
            add(ddlTokens("CREATE TABLE chain_nodes ($columns, isActive INTEGER NOT NULL DEFAULT 0, isDefault INTEGER NOT NULL DEFAULT 1, $constraints)"))
        }
    }
    private val RAW_SOURCE_DDL = buildSet {
        add(ddlTokens("CREATE TABLE chain_nodes (chainId,url,name,isActive,isDefault)"))
        for (parent in listOf("chains", "_chains")) add(ddlTokens(
            "CREATE TABLE chain_nodes (chainId,url,name,isActive,isDefault, FOREIGN KEY(chainId) REFERENCES $parent(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
        ))
    }

    companion object {
        val HISTORICAL = EndpointArchiveFormat(
            "endpoint_archive_v1", "endpoint_archive_header_v1", "endpoint_archive_schema_v1",
            setOf(39, 41, 45, 53, 71, 76)
        )
        val UPGRADE_ENTRY = EndpointArchiveFormat(
            "upgrade_entry_endpoint_archive_v1", "upgrade_entry_endpoint_archive_header_v1", "upgrade_entry_endpoint_archive_schema_v1",
            setOf(39, 41, 45, 53, 71, 76)
        )
        val CURRENT = EndpointArchiveFormat(
            "current_endpoint_archive_v1", "current_endpoint_archive_header_v1", "current_endpoint_archive_schema_v1",
            setOf(81)
        )
    }

}

