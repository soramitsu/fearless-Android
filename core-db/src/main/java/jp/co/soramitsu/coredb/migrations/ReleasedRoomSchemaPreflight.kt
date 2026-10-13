package jp.co.soramitsu.coredb.migrations

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.Locale

/**
 * A frozen column definition from a released Room schema.
 *
 * [allowedDefaultValues] is deliberately a set: databases upgraded through an
 * older `ALTER TABLE ... DEFAULT ...` migration can retain a released default
 * which is absent from a database created fresh at the same Room version.
 */
internal data class ReleasedRoomColumnSpec(
    val name: String,
    val type: String,
    val notNull: Boolean = false,
    val primaryKeyPosition: Int = 0,
    val allowedDefaultValues: Set<String?> = setOf(null)
)

internal data class ReleasedRoomIndexSpec(
    val name: String,
    val unique: Boolean = false,
    val partial: Boolean = false,
    val columns: List<String>
)

/**
 * One exact released physical column-order cohort.
 *
 * [explicitDefaultValues] contains the non-absent SQLite `dflt_value` text
 * which was appended by migrations for this specific order. An omitted column
 * means the exact absence of a declared default. Keeping order and defaults
 * together prevents accepting cross-products which no released database could
 * have produced.
 */
internal data class ReleasedRoomColumnOrderCohort(
    val columnNames: List<String>,
    val explicitDefaultValues: Map<String, String> = emptyMap()
)

internal data class ReleasedRoomTableSpec(
    val name: String,
    val columns: List<ReleasedRoomColumnSpec>,
    val alternateColumnLayouts: List<List<ReleasedRoomColumnSpec>> =
        emptyList(),
    val indexes: Set<ReleasedRoomIndexSpec> = emptySet(),
    val alternateColumnOrderCohorts: List<ReleasedRoomColumnOrderCohort> =
        emptyList()
)

internal data class ReleasedRoomSchemaObjectSpec(
    val type: String,
    val name: String,
    val tableName: String
)

/**
 * Rejects missing and additional persistent schema objects before any PRAGMA
 * traversal. This also excludes triggers/views that could make a later write
 * fail after external preference state has already committed.
 */
internal fun requireExactReleasedRoomSchemaObjects(
    database: SupportSQLiteDatabase,
    context: String,
    allowedObjectSets: Set<Set<ReleasedRoomSchemaObjectSpec>>,
    maximumSchemaObjects: Int,
    exceptionFactory: (String) -> RuntimeException
) {
    check(allowedObjectSets.isNotEmpty())
    check(maximumSchemaObjects > 0)
    allowedObjectSets.forEach { expected ->
        check(expected.size <= maximumSchemaObjects)
        expected.forEach { schemaObject ->
            check(schemaObject.type == "table" || schemaObject.type == "index")
            check(isSafeReleasedIdentifier(schemaObject.name))
            check(isSafeReleasedIdentifier(schemaObject.tableName))
        }
    }

    val boundedType = boundedTextProjection(
        column = "type",
        alias = RELEASED_BOUNDED_SCHEMA_OBJECT_TYPE,
        maxBytes = RELEASED_MAX_OBJECT_TYPE_BYTES
    )
    val boundedName = boundedTextProjection(
        column = "name",
        alias = RELEASED_BOUNDED_SCHEMA_OBJECT_NAME,
        maxBytes = RELEASED_MAX_IDENTIFIER_BYTES
    )
    val boundedTableName = boundedTextProjection(
        column = "tbl_name",
        alias = RELEASED_BOUNDED_SCHEMA_TABLE_NAME,
        maxBytes = RELEASED_MAX_IDENTIFIER_BYTES
    )
    val actual = linkedSetOf<ReleasedRoomSchemaObjectSpec>()
    database.query(
        "SELECT $boundedType, $boundedName, $boundedTableName " +
            "FROM sqlite_master ORDER BY rowid ASC " +
            "LIMIT ${maximumSchemaObjects + 1}"
    ).use { cursor ->
        var rowCount = 0
        while (cursor.moveToNext()) {
            if (rowCount == maximumSchemaObjects) {
                throw exceptionFactory(
                    "$context SQLite schema objects exceed the safe bound"
                )
            }
            rowCount += 1
            val observed = ReleasedRoomSchemaObjectSpec(
                type = cursor.requireReleasedText(
                    alias = RELEASED_BOUNDED_SCHEMA_OBJECT_TYPE,
                    maxBytes = RELEASED_MAX_OBJECT_TYPE_BYTES,
                    message = "$context has an unsafe schema object type",
                    exceptionFactory = exceptionFactory
                ),
                name = cursor.requireReleasedText(
                    alias = RELEASED_BOUNDED_SCHEMA_OBJECT_NAME,
                    maxBytes = RELEASED_MAX_IDENTIFIER_BYTES,
                    message = "$context has an unsafe schema object name",
                    exceptionFactory = exceptionFactory
                ),
                tableName = cursor.requireReleasedText(
                    alias = RELEASED_BOUNDED_SCHEMA_TABLE_NAME,
                    maxBytes = RELEASED_MAX_IDENTIFIER_BYTES,
                    message = "$context has an unsafe schema table name",
                    exceptionFactory = exceptionFactory
                )
            )
            if (!actual.add(observed)) {
                throw exceptionFactory(
                    "$context contains duplicate SQLite schema objects"
                )
            }
        }
    }
    if (allowedObjectSets.none { expected -> actual == expected }) {
        throw exceptionFactory(
            "$context has missing, extra, or incompatible SQLite schema objects"
        )
    }
}

/**
 * Bounded, read-only validation of the table structures Room will validate.
 *
 * Foreign keys are intentionally validated by [requireBoundedForeignKeyCheck],
 * whose representation also supports composite keys. Callers that can mutate
 * storage outside Room's SQL transaction must invoke both guards first.
 */
internal fun requireExactReleasedRoomSchema(
    database: SupportSQLiteDatabase,
    context: String,
    tables: List<ReleasedRoomTableSpec>,
    exceptionFactory: (String) -> RuntimeException
) {
    requireValidReleasedContract(tables)
    tables.forEach { expected ->
        val actual = readReleasedRoomTable(
            database = database,
            context = context,
            tableName = expected.name,
            exceptionFactory = exceptionFactory
        )
        requireExactReleasedColumns(
            context = context,
            table = expected,
            actual = actual,
            exceptionFactory = exceptionFactory
        )
        requireExactReleasedIndexes(
            database = database,
            context = context,
            table = expected,
            exceptionFactory = exceptionFactory
        )
    }
}

private fun requireValidReleasedContract(
    tables: List<ReleasedRoomTableSpec>
) {
    check(tables.isNotEmpty())
    check(tables.map(ReleasedRoomTableSpec::name).toSet().size == tables.size)
    tables.forEach { table ->
        check(isSafeReleasedIdentifier(table.name))
        val allowedLayouts = table.allowedColumnLayouts()
        if (table.alternateColumnOrderCohorts.isNotEmpty()) {
            (listOf(table.columns) + table.alternateColumnLayouts)
                .flatten()
                .forEach { column ->
                    check(column.allowedDefaultValues.size == 1)
                }
        }
        allowedLayouts.forEach { layout ->
            check(layout.isNotEmpty())
            check(
                layout.map(ReleasedRoomColumnSpec::name).toSet().size ==
                    layout.size
            )
            layout.forEach { column ->
                check(isSafeReleasedIdentifier(column.name))
                check(column.type.isNotEmpty())
                check(column.primaryKeyPosition in 0..layout.size)
                check(column.allowedDefaultValues.isNotEmpty())
            }
        }
        check(table.indexes.map(ReleasedRoomIndexSpec::name).toSet().size ==
            table.indexes.size)
        table.indexes.forEach { index ->
            check(isSafeReleasedIdentifier(index.name))
            check(index.columns.isNotEmpty())
            check(index.columns.size <= RELEASED_MAX_INDEX_COLUMNS)
            index.columns.forEach { column ->
                check(
                    table.allowedColumnLayouts().all { layout ->
                        layout.any { it.name == column }
                    }
                )
            }
        }
    }
}

private fun readReleasedRoomTable(
    database: SupportSQLiteDatabase,
    context: String,
    tableName: String,
    exceptionFactory: (String) -> RuntimeException
): List<ReleasedDatabaseColumn> {
    requireReleasedOrdinaryTable(
        database = database,
        context = context,
        tableName = tableName,
        exceptionFactory = exceptionFactory
    )
    val boundedName = boundedTextProjection(
        column = "name",
        alias = RELEASED_BOUNDED_COLUMN_NAME,
        maxBytes = RELEASED_MAX_IDENTIFIER_BYTES
    )
    val boundedType = boundedTextProjection(
        column = "type",
        alias = RELEASED_BOUNDED_COLUMN_TYPE,
        maxBytes = RELEASED_MAX_COLUMN_TYPE_BYTES
    )
    val boundedDefault = boundedTextProjection(
        column = "dflt_value",
        alias = RELEASED_BOUNDED_COLUMN_DEFAULT,
        maxBytes = RELEASED_MAX_COLUMN_DEFAULT_BYTES
    )
    val result = mutableListOf<ReleasedDatabaseColumn>()
    database.query(
        "SELECT cid, $boundedName, $boundedType, `notnull`, " +
            "$boundedDefault, pk FROM pragma_table_info(" +
            "'${safeReleasedIdentifier(tableName)}') ORDER BY cid ASC " +
            "LIMIT ${RELEASED_MAX_TABLE_COLUMNS + 1}"
    ).use { cursor ->
        while (cursor.moveToNext()) {
            if (result.size == RELEASED_MAX_TABLE_COLUMNS) {
                throw exceptionFactory(
                    "$context $tableName exceeds the bounded column count"
                )
            }
            val position = cursor.requireReleasedInteger(
                column = "cid",
                message = "$context $tableName has an invalid column id",
                exceptionFactory = exceptionFactory
            )
            if (position != result.size.toLong()) {
                throw exceptionFactory(
                    "$context $tableName has an incompatible column order"
                )
            }
            val name = cursor.requireReleasedText(
                alias = RELEASED_BOUNDED_COLUMN_NAME,
                maxBytes = RELEASED_MAX_IDENTIFIER_BYTES,
                message = "$context $tableName has an unsafe column name",
                exceptionFactory = exceptionFactory
            )
            result += ReleasedDatabaseColumn(
                position = position.toInt(),
                name = name,
                type = cursor.requireReleasedText(
                    alias = RELEASED_BOUNDED_COLUMN_TYPE,
                    maxBytes = RELEASED_MAX_COLUMN_TYPE_BYTES,
                    message = "$context $tableName.$name has an unsafe type",
                    exceptionFactory = exceptionFactory
                ).uppercase(Locale.US),
                notNull = cursor.requireReleasedBoolean(
                    column = "notnull",
                    message = "$context $tableName.$name has invalid nullability",
                    exceptionFactory = exceptionFactory
                ),
                defaultValue = cursor.readReleasedNullableText(
                    alias = RELEASED_BOUNDED_COLUMN_DEFAULT,
                    maxBytes = RELEASED_MAX_COLUMN_DEFAULT_BYTES,
                    message = "$context $tableName.$name has an unsafe default",
                    exceptionFactory = exceptionFactory
                ),
                primaryKeyPosition = cursor.requireReleasedPosition(
                    column = "pk",
                    maximum = RELEASED_MAX_TABLE_COLUMNS,
                    message = "$context $tableName.$name has an invalid primary key position",
                    exceptionFactory = exceptionFactory
                )
            )
        }
    }
    if (result.isEmpty()) {
        throw exceptionFactory("$context $tableName has no readable columns")
    }
    return result
}

private fun requireExactReleasedColumns(
    context: String,
    table: ReleasedRoomTableSpec,
    actual: List<ReleasedDatabaseColumn>,
    exceptionFactory: (String) -> RuntimeException
) {
    val matchesReleasedLayout =
        table.allowedColumnLayouts().any { expected ->
            releasedColumnsMatch(actual = actual, expected = expected)
        }
    if (matchesReleasedLayout) return
    val expectedNames = table.allowedColumnLayouts()
        .flatMap { layout -> layout.map(ReleasedRoomColumnSpec::name) }
        .toSet()
    val firstUnexpected = actual.firstOrNull { it.name !in expectedNames }
    if (firstUnexpected != null) {
        throw exceptionFactory(
            "$context ${table.name} contains unexpected column " +
                firstUnexpected.name
        )
    }
    val missing = table.columns.firstOrNull { expected ->
        actual.none { observed -> observed.name == expected.name }
    }
    if (missing != null) {
        throw exceptionFactory(
            "$context ${table.name} is missing required column ${missing.name}"
        )
    }
    throw exceptionFactory(
        "$context ${table.name} has incompatible ordered column definitions"
    )
}

private fun releasedColumnsMatch(
    actual: List<ReleasedDatabaseColumn>,
    expected: List<ReleasedRoomColumnSpec>
): Boolean {
    if (actual.size != expected.size) return false
    expected.forEachIndexed { position, column ->
        val observed = actual[position]
        if (
            observed.position != position ||
            observed.name != column.name ||
            observed.type != column.type.uppercase(Locale.US) ||
            observed.notNull != column.notNull ||
            observed.primaryKeyPosition != column.primaryKeyPosition ||
            observed.defaultValue !in column.allowedDefaultValues
        ) {
            return false
        }
    }
    return true
}

private fun ReleasedRoomTableSpec.allowedColumnLayouts():
    List<List<ReleasedRoomColumnSpec>> {
    val explicitLayouts = listOf(columns) + alternateColumnLayouts
    val canonicalNames = columns.map(ReleasedRoomColumnSpec::name)
    check(canonicalNames.toSet().size == canonicalNames.size)
    val canonicalByName = columns.associateBy(ReleasedRoomColumnSpec::name)
    val reorderedLayouts = alternateColumnOrderCohorts.map { cohort ->
        val order = cohort.columnNames
        check(order.size == canonicalNames.size)
        check(order.toSet().size == order.size)
        check(order.toSet() == canonicalNames.toSet())
        check(cohort.explicitDefaultValues.keys.all(canonicalByName::containsKey))
        cohort.explicitDefaultValues.values.forEach { defaultValue ->
            check(defaultValue.isNotEmpty())
        }
        order.map { columnName ->
            val canonical = canonicalByName.getValue(columnName)
            canonical.copy(
                allowedDefaultValues = setOf(
                    cohort.explicitDefaultValues[columnName]
                )
            )
        }
    }
    return (explicitLayouts + reorderedLayouts).distinct()
}

private fun requireExactReleasedIndexes(
    database: SupportSQLiteDatabase,
    context: String,
    table: ReleasedRoomTableSpec,
    exceptionFactory: (String) -> RuntimeException
) {
    val actual = readReleasedExplicitIndexes(
        database = database,
        context = context,
        tableName = table.name,
        exceptionFactory = exceptionFactory
    )
    if (actual != table.indexes) {
        throw exceptionFactory(
            "$context ${table.name} has incompatible explicit indexes"
        )
    }
}

private fun readReleasedExplicitIndexes(
    database: SupportSQLiteDatabase,
    context: String,
    tableName: String,
    exceptionFactory: (String) -> RuntimeException
): Set<ReleasedRoomIndexSpec> {
    val boundedName = boundedTextProjection(
        column = "name",
        alias = RELEASED_BOUNDED_INDEX_NAME,
        maxBytes = RELEASED_MAX_IDENTIFIER_BYTES
    )
    val boundedOrigin = boundedTextProjection(
        column = "origin",
        alias = RELEASED_BOUNDED_INDEX_ORIGIN,
        maxBytes = RELEASED_MAX_INDEX_ORIGIN_BYTES
    )
    val result = mutableSetOf<ReleasedRoomIndexSpec>()
    database.query(
        "SELECT $boundedName, `unique`, $boundedOrigin, partial " +
            "FROM pragma_index_list('${safeReleasedIdentifier(tableName)}') " +
            "ORDER BY seq ASC LIMIT ${RELEASED_MAX_INDEXES + 1}"
    ).use { cursor ->
        var rowCount = 0
        while (cursor.moveToNext()) {
            if (rowCount == RELEASED_MAX_INDEXES) {
                throw exceptionFactory(
                    "$context $tableName exceeds the bounded index count"
                )
            }
            rowCount += 1
            val origin = cursor.requireReleasedText(
                alias = RELEASED_BOUNDED_INDEX_ORIGIN,
                maxBytes = RELEASED_MAX_INDEX_ORIGIN_BYTES,
                message = "$context $tableName has an unsafe index origin",
                exceptionFactory = exceptionFactory
            )
            if (origin != RELEASED_EXPLICIT_INDEX_ORIGIN) continue

            val indexName = cursor.requireReleasedText(
                alias = RELEASED_BOUNDED_INDEX_NAME,
                maxBytes = RELEASED_MAX_IDENTIFIER_BYTES,
                message = "$context $tableName has an unsafe index name",
                exceptionFactory = exceptionFactory
            )
            val index = ReleasedRoomIndexSpec(
                name = indexName,
                unique = cursor.requireReleasedBoolean(
                    column = "unique",
                    message = "$context $indexName has invalid uniqueness",
                    exceptionFactory = exceptionFactory
                ),
                partial = cursor.requireReleasedBoolean(
                    column = "partial",
                    message = "$context $indexName has invalid partial state",
                    exceptionFactory = exceptionFactory
                ),
                columns = readReleasedIndexColumns(
                    database = database,
                    context = context,
                    indexName = indexName,
                    exceptionFactory = exceptionFactory
                )
            )
            if (!result.add(index)) {
                throw exceptionFactory(
                    "$context $tableName contains duplicate explicit indexes"
                )
            }
        }
    }
    return result
}

private fun readReleasedIndexColumns(
    database: SupportSQLiteDatabase,
    context: String,
    indexName: String,
    exceptionFactory: (String) -> RuntimeException
): List<String> {
    val boundedName = boundedTextProjection(
        column = "name",
        alias = RELEASED_BOUNDED_INDEX_COLUMN,
        maxBytes = RELEASED_MAX_IDENTIFIER_BYTES
    )
    val boundedCollation = boundedTextProjection(
        column = "coll",
        alias = RELEASED_BOUNDED_INDEX_COLLATION,
        maxBytes = RELEASED_MAX_INDEX_COLLATION_BYTES
    )
    val result = mutableListOf<String>()
    database.query(
        "SELECT seqno, cid, $boundedName, `desc`, $boundedCollation, `key` " +
            "FROM pragma_index_xinfo(" +
            "'${safeReleasedIdentifier(indexName)}') WHERE `key` = 1 " +
            "ORDER BY seqno ASC " +
            "LIMIT ${RELEASED_MAX_INDEX_COLUMNS + 1}"
    ).use { cursor ->
        while (cursor.moveToNext()) {
            if (result.size == RELEASED_MAX_INDEX_COLUMNS) {
                throw exceptionFactory(
                    "$context $indexName exceeds the bounded index-column count"
                )
            }
            val sequence = cursor.requireReleasedInteger(
                column = "seqno",
                message = "$context $indexName has an invalid column sequence",
                exceptionFactory = exceptionFactory
            )
            val columnId = cursor.requireReleasedInteger(
                column = "cid",
                message = "$context $indexName has an invalid column id",
                exceptionFactory = exceptionFactory
            )
            val descending = cursor.requireReleasedBoolean(
                column = "desc",
                message = "$context $indexName has invalid sort direction",
                exceptionFactory = exceptionFactory
            )
            val isKey = cursor.requireReleasedBoolean(
                column = "key",
                message = "$context $indexName has invalid key metadata",
                exceptionFactory = exceptionFactory
            )
            val collation = cursor.requireReleasedText(
                alias = RELEASED_BOUNDED_INDEX_COLLATION,
                maxBytes = RELEASED_MAX_INDEX_COLLATION_BYTES,
                message = "$context $indexName has unsafe collation metadata",
                exceptionFactory = exceptionFactory
            )
            if (
                sequence != result.size.toLong() ||
                columnId < 0L ||
                descending ||
                !isKey ||
                collation != RELEASED_BINARY_COLLATION
            ) {
                throw exceptionFactory(
                    "$context $indexName has incompatible indexed-column metadata"
                )
            }
            result += cursor.requireReleasedText(
                alias = RELEASED_BOUNDED_INDEX_COLUMN,
                maxBytes = RELEASED_MAX_IDENTIFIER_BYTES,
                message = "$context $indexName has an unsafe indexed column",
                exceptionFactory = exceptionFactory
            )
        }
    }
    return result
}

private fun requireReleasedOrdinaryTable(
    database: SupportSQLiteDatabase,
    context: String,
    tableName: String,
    exceptionFactory: (String) -> RuntimeException
) {
    val boundedType = boundedTextProjection(
        column = "type",
        alias = RELEASED_BOUNDED_OBJECT_TYPE,
        maxBytes = RELEASED_MAX_OBJECT_TYPE_BYTES
    )
    val boundedSql = boundedTextProjection(
        column = "sql",
        alias = RELEASED_BOUNDED_OBJECT_SQL,
        maxBytes = RELEASED_MAX_SCHEMA_SQL_BYTES
    )
    database.query(
        "SELECT $boundedType, $boundedSql FROM sqlite_master " +
            "WHERE name = ? COLLATE BINARY ORDER BY rowid ASC LIMIT 2",
        arrayOf<Any?>(tableName)
    ).use { cursor ->
        if (!cursor.moveToFirst()) {
            throw exceptionFactory(
                "$context is missing required table $tableName"
            )
        }
        val type = cursor.requireReleasedText(
            alias = RELEASED_BOUNDED_OBJECT_TYPE,
            maxBytes = RELEASED_MAX_OBJECT_TYPE_BYTES,
            message = "$context $tableName has an unsafe schema object type",
            exceptionFactory = exceptionFactory
        )
        val sql = cursor.requireReleasedText(
            alias = RELEASED_BOUNDED_OBJECT_SQL,
            maxBytes = RELEASED_MAX_SCHEMA_SQL_BYTES,
            message = "$context $tableName has unsafe schema SQL",
            exceptionFactory = exceptionFactory
        )
        if (
            type != RELEASED_TABLE_OBJECT_TYPE ||
            !sql.trimStart().uppercase(Locale.US)
                .startsWith(RELEASED_CREATE_TABLE_PREFIX)
        ) {
            throw exceptionFactory(
                "$context $tableName is not a compatible ordinary SQLite table"
            )
        }
        requireNoUnsupportedReleasedTableClauses(
            sql = sql,
            context = context,
            tableName = tableName,
            exceptionFactory = exceptionFactory
        )
        if (cursor.moveToNext()) {
            throw exceptionFactory(
                "$context $tableName has duplicate sqlite_master definitions"
            )
        }
    }
}

private fun requireNoUnsupportedReleasedTableClauses(
    sql: String,
    context: String,
    tableName: String,
    exceptionFactory: (String) -> RuntimeException
) {
    val tokens = releasedSqlTokens(
        sql = sql,
        context = context,
        tableName = tableName,
        exceptionFactory = exceptionFactory
    )
    val unsupported = tokens.firstOrNull {
        it in RELEASED_UNSUPPORTED_TABLE_TOKENS
    }
    if (unsupported != null) {
        throw exceptionFactory(
            "$context $tableName contains unsupported released-schema " +
                "clause $unsupported"
        )
    }
}

private fun releasedSqlTokens(
    sql: String,
    context: String,
    tableName: String,
    exceptionFactory: (String) -> RuntimeException
): List<String> {
    val tokens = mutableListOf<String>()
    var index = 0
    while (index < sql.length) {
        val current = sql[index]
        when {
            current == '\'' || current == '"' || current == '`' -> {
                val quote = current
                index += 1
                var terminated = false
                while (index < sql.length) {
                    if (sql[index] == quote) {
                        if (
                            index + 1 < sql.length &&
                            sql[index + 1] == quote
                        ) {
                            index += 2
                        } else {
                            index += 1
                            terminated = true
                            break
                        }
                    } else {
                        index += 1
                    }
                }
                if (!terminated) {
                    throw exceptionFactory(
                        "$context $tableName has unterminated quoted schema SQL"
                    )
                }
            }
            current == '[' -> {
                index += 1
                while (index < sql.length && sql[index] != ']') {
                    index += 1
                }
                if (index == sql.length) {
                    throw exceptionFactory(
                        "$context $tableName has unterminated quoted schema SQL"
                    )
                }
                index += 1
            }
            current == '-' &&
                index + 1 < sql.length &&
                sql[index + 1] == '-' -> {
                index += 2
                while (index < sql.length && sql[index] != '\n') {
                    index += 1
                }
            }
            current == '/' &&
                index + 1 < sql.length &&
                sql[index + 1] == '*' -> {
                index += 2
                var terminated = false
                while (index + 1 < sql.length) {
                    if (sql[index] == '*' && sql[index + 1] == '/') {
                        index += 2
                        terminated = true
                        break
                    }
                    index += 1
                }
                if (!terminated) {
                    throw exceptionFactory(
                        "$context $tableName has unterminated schema SQL comment"
                    )
                }
            }
            current == '_' || current.isLetter() -> {
                val start = index
                index += 1
                while (
                    index < sql.length &&
                    (sql[index] == '_' || sql[index].isLetterOrDigit())
                ) {
                    index += 1
                }
                tokens += sql.substring(start, index).uppercase(Locale.US)
            }
            else -> index += 1
        }
    }
    return tokens
}

private fun Cursor.requireReleasedInteger(
    column: String,
    message: String,
    exceptionFactory: (String) -> RuntimeException
): Long {
    val index = getColumnIndexOrThrow(column)
    if (isNull(index) || getType(index) != Cursor.FIELD_TYPE_INTEGER) {
        throw exceptionFactory(message)
    }
    return getLong(index)
}

private fun Cursor.requireReleasedBoolean(
    column: String,
    message: String,
    exceptionFactory: (String) -> RuntimeException
): Boolean = when (
    val value = requireReleasedInteger(column, message, exceptionFactory)
) {
    0L -> false
    1L -> true
    else -> throw exceptionFactory(message)
}

private fun Cursor.requireReleasedPosition(
    column: String,
    maximum: Int,
    message: String,
    exceptionFactory: (String) -> RuntimeException
): Int {
    val value = requireReleasedInteger(column, message, exceptionFactory)
    if (value !in 0L..maximum.toLong()) {
        throw exceptionFactory(message)
    }
    return value.toInt()
}

private fun Cursor.requireReleasedText(
    alias: String,
    maxBytes: Int,
    message: String,
    exceptionFactory: (String) -> RuntimeException
): String = readReleasedNullableText(
    alias = alias,
    maxBytes = maxBytes,
    message = message,
    exceptionFactory = exceptionFactory
) ?: throw exceptionFactory(message)

private fun Cursor.readReleasedNullableText(
    alias: String,
    maxBytes: Int,
    message: String,
    exceptionFactory: (String) -> RuntimeException
): String? {
    val bounded = readBoundedText(alias = alias, maxBytes = maxBytes)
    if (!bounded.isPresent) return null
    if (
        !bounded.hasExpectedStorageClass ||
        bounded.isOversized ||
        bounded.value == null
    ) {
        throw exceptionFactory(message)
    }
    return bounded.value
}

private fun safeReleasedIdentifier(identifier: String): String {
    check(isSafeReleasedIdentifier(identifier))
    return identifier
}

private fun isSafeReleasedIdentifier(identifier: String): Boolean =
    identifier.isNotEmpty() &&
        identifier.all { it == '_' || it.isLetterOrDigit() }

private data class ReleasedDatabaseColumn(
    val position: Int,
    val name: String,
    val type: String,
    val notNull: Boolean,
    val defaultValue: String?,
    val primaryKeyPosition: Int
)

private const val RELEASED_MAX_TABLE_COLUMNS = 64
private const val RELEASED_MAX_INDEXES = 16
private const val RELEASED_MAX_INDEX_COLUMNS = 8
private const val RELEASED_MAX_IDENTIFIER_BYTES = 256
private const val RELEASED_MAX_COLUMN_TYPE_BYTES = 64
private const val RELEASED_MAX_COLUMN_DEFAULT_BYTES = 1_024
private const val RELEASED_MAX_INDEX_ORIGIN_BYTES = 16
private const val RELEASED_MAX_INDEX_COLLATION_BYTES = 64
private const val RELEASED_MAX_OBJECT_TYPE_BYTES = 16
private const val RELEASED_MAX_SCHEMA_SQL_BYTES = 262_144

private const val RELEASED_EXPLICIT_INDEX_ORIGIN = "c"
private const val RELEASED_BINARY_COLLATION = "BINARY"
private const val RELEASED_TABLE_OBJECT_TYPE = "table"
private const val RELEASED_CREATE_TABLE_PREFIX = "CREATE TABLE"

private val RELEASED_UNSUPPORTED_TABLE_TOKENS = setOf(
    "AS",
    "CHECK",
    "COLLATE",
    "CONFLICT",
    "GENERATED",
    "STRICT",
    "UNIQUE",
    "WITHOUT"
)

private const val RELEASED_BOUNDED_COLUMN_NAME =
    "releasedBoundedColumnName"
private const val RELEASED_BOUNDED_COLUMN_TYPE =
    "releasedBoundedColumnType"
private const val RELEASED_BOUNDED_COLUMN_DEFAULT =
    "releasedBoundedColumnDefault"
private const val RELEASED_BOUNDED_INDEX_NAME =
    "releasedBoundedIndexName"
private const val RELEASED_BOUNDED_INDEX_ORIGIN =
    "releasedBoundedIndexOrigin"
private const val RELEASED_BOUNDED_INDEX_COLUMN =
    "releasedBoundedIndexColumn"
private const val RELEASED_BOUNDED_INDEX_COLLATION =
    "releasedBoundedIndexCollation"
private const val RELEASED_BOUNDED_OBJECT_TYPE =
    "releasedBoundedObjectType"
private const val RELEASED_BOUNDED_OBJECT_SQL =
    "releasedBoundedObjectSql"
private const val RELEASED_BOUNDED_SCHEMA_OBJECT_TYPE =
    "releasedBoundedSchemaObjectType"
private const val RELEASED_BOUNDED_SCHEMA_OBJECT_NAME =
    "releasedBoundedSchemaObjectName"
private const val RELEASED_BOUNDED_SCHEMA_TABLE_NAME =
    "releasedBoundedSchemaTableName"
