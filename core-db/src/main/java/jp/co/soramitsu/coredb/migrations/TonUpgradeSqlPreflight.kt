package jp.co.soramitsu.coredb.migrations

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.Locale

private typealias ColumnSpec = ReleasedRoomColumnSpec
private typealias IndexSpec = ReleasedRoomIndexSpec

private val RELEASED_ABSENT_OR_NULL_DEFAULT = setOf(null, "NULL")
private val RELEASED_ABSENT_OR_ZERO_DEFAULT = setOf(null, "0")

private val VERSION_71_AUTO_INDEX_TABLES = listOf(
    "account_staking_accesses",
    "allpools",
    "assets",
    "chain_accounts",
    "chain_assets",
    "chain_explorers",
    "chain_nodes",
    "chain_runtimes",
    "chain_types",
    "chains",
    "favorite_chains",
    "operations",
    "phishing",
    "storage",
    "token_price",
    "total_reward",
    "userpools"
)

private const val VERSION_71_MAX_SCHEMA_OBJECTS = 64

class TonUpgradeSqlIntegrityException(
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)

internal const val TON_UPGRADE_MAX_CELL_PAYLOAD_BYTES = 1_048_576L
internal const val TON_UPGRADE_MAX_ROW_PAYLOAD_BYTES = 4_194_304L
internal const val TON_UPGRADE_MAX_TOTAL_PAYLOAD_BYTES = 536_870_912L

internal data class TonUpgradePayloadLimits(
    val maximumCellBytes: Long,
    val maximumRowBytes: Long,
    val maximumTotalBytes: Long
) {
    init {
        require(maximumCellBytes in 1 until Long.MAX_VALUE)
        require(maximumRowBytes in 1 until Long.MAX_VALUE)
        require(maximumTotalBytes in 1 until Long.MAX_VALUE)
    }

    companion object {
        val PRODUCTION = TonUpgradePayloadLimits(
            maximumCellBytes = TON_UPGRADE_MAX_CELL_PAYLOAD_BYTES,
            maximumRowBytes = TON_UPGRADE_MAX_ROW_PAYLOAD_BYTES,
            maximumTotalBytes = TON_UPGRADE_MAX_TOTAL_PAYLOAD_BYTES
        )
    }
}

internal fun checkedTonUpgradePayloadByteSum(
    currentBytes: Long,
    additionalBytes: Long,
    context: String
): Long {
    if (
        currentBytes < 0L ||
        additionalBytes < 0L ||
        currentBytes > Long.MAX_VALUE - additionalBytes
    ) {
        throw TonUpgradeSqlIntegrityException(
            "$context exceeds the safe migration payload accounting range"
        )
    }
    return currentBytes + additionalBytes
}

/**
 * Read-only schema guard for the shipped database-71 to database-77 path.
 *
 * TON secret separation commits outside Room's SQL transaction. Every
 * deterministic DDL prerequisite that can be checked on database 71 therefore
 * has to be validated before the first encrypted-preference mutation.
 */
@Suppress(
    "ComplexCondition",
    "LargeClass",
    "NestedBlockDepth",
    "ThrowsCount"
)
internal object TonUpgradeSqlPreflight {

    fun requireSafeToMutate(
        database: SupportSQLiteDatabase,
        foreignKeyCheckLimits: MigrationForeignKeyCheckLimits =
            TON_UPGRADE_FOREIGN_KEY_CHECK_LIMITS,
        payloadLimits: TonUpgradePayloadLimits =
            TonUpgradePayloadLimits.PRODUCTION
    ) {
        try {
            requireExactReleasedRoomSchemaObjects(
                database = database,
                context = "Version 71",
                allowedObjectSets = VERSION_71_ALLOWED_SCHEMA_OBJECT_SETS,
                maximumSchemaObjects = VERSION_71_MAX_SCHEMA_OBJECTS
            ) { message ->
                TonUpgradeSqlIntegrityException(message)
            }
            val schemas = VERSION_71_TABLES.associate { table ->
                table.name to readExactTable(database, table.name)
            }
            requireFutureColumnsAbsent(schemas)

            VERSION_71_TABLES.forEach { table ->
                requireExactColumns(
                    context = "Version 71 ${table.name}",
                    actual = checkNotNull(schemas[table.name]),
                    expected = table.columns
                )
                requireExactForeignKeys(
                    database = database,
                    table = table
                )
                requireExactIndexes(
                    database = database,
                    table = table
                )
            }
            requireExactReleasedRoomSchema(
                database = database,
                context = "Version 71",
                tables = VERSION_71_RELEASED_ROOM_TABLES
            ) { message ->
                TonUpgradeSqlIntegrityException(message)
            }
            val copiedTableRowLimits = requireBoundedCopiedTablePayloads(
                database = database,
                foreignKeyCheckLimits = foreignKeyCheckLimits,
                payloadLimits = payloadLimits
            )

            RESERVED_WORK_TABLES.forEach { tableName ->
                requireObjectAbsent(database, tableName)
            }
            OPTIONAL_DROP_TARGETS.forEach { tableName ->
                requireOrdinaryTableOrAbsent(database, tableName)
            }
            requireCompatibleTonConnectionOrAbsent(database)
            requireBoundedForeignKeyCheck(
                database = database,
                limits = foreignKeyCheckLimits,
                previouslyBoundedRows = copiedTableRowLimits
            ) { message ->
                TonUpgradeSqlIntegrityException("Version 71 $message")
            }
        } catch (failure: TonUpgradeSqlIntegrityException) {
            throw failure
        } catch (failure: Exception) {
            throw TonUpgradeSqlIntegrityException(
                "Unable to verify the version 71 SQL migration prerequisites",
                failure
            )
        }
    }

    private fun requireBoundedCopiedTablePayloads(
        database: SupportSQLiteDatabase,
        foreignKeyCheckLimits: MigrationForeignKeyCheckLimits,
        payloadLimits: TonUpgradePayloadLimits
    ): Map<String, Int> {
        var totalPayloadBytes = 0L
        return VERSION_71_TABLES.associate { table ->
            val maximumRows = foreignKeyCheckLimits
                .maximumRowsByTable[table.name]
                ?: throw TonUpgradeSqlIntegrityException(
                    "No row limit is configured for copied table ${table.name}"
                )
            totalPayloadBytes = requireBoundedRowPayloads(
                database = database,
                table = table,
                maximumRows = maximumRows,
                startingTotalPayloadBytes = totalPayloadBytes,
                payloadLimits = payloadLimits
            )
            table.name to maximumRows
        }
    }

    private fun requireBoundedRowPayloads(
        database: SupportSQLiteDatabase,
        table: TableSpec,
        maximumRows: Int,
        startingTotalPayloadBytes: Long,
        payloadLimits: TonUpgradePayloadLimits
    ): Long {
        val payloadColumns = table.columns.mapIndexed { index, column ->
            "length(CAST(`${safeIdentifier(column.name)}` AS BLOB)) " +
                "AS `payload$index`"
        }
        val query = "SELECT ${payloadColumns.joinToString()} FROM `" +
            safeIdentifier(table.name) + "` LIMIT ${maximumRows + 1}"

        database.query(query).use { cursor ->
            var rowCount = 0
            var totalPayloadBytes = startingTotalPayloadBytes
            while (cursor.moveToNext()) {
                if (rowCount == maximumRows) {
                    throw TonUpgradeSqlIntegrityException(
                        "${table.name} rows exceed the safe migration limit"
                    )
                }
                rowCount += 1

                var rowPayloadBytes = 0L
                table.columns.forEachIndexed { index, column ->
                    val cellPayloadBytes = cursor.readNullablePayloadLength(
                        column = "payload$index",
                        context = "${table.name}.${column.name}"
                    ) ?: 0L
                    if (cellPayloadBytes > payloadLimits.maximumCellBytes) {
                        throw TonUpgradeSqlIntegrityException(
                            "${table.name}.${column.name} exceeds the safe " +
                                "migration payload limit"
                        )
                    }
                    rowPayloadBytes = checkedTonUpgradePayloadByteSum(
                        currentBytes = rowPayloadBytes,
                        additionalBytes = cellPayloadBytes,
                        context = "${table.name} row"
                    )
                    if (rowPayloadBytes > payloadLimits.maximumRowBytes) {
                        throw TonUpgradeSqlIntegrityException(
                            "${table.name} row exceeds the safe migration " +
                                "payload limit"
                        )
                    }
                }

                totalPayloadBytes = checkedTonUpgradePayloadByteSum(
                    currentBytes = totalPayloadBytes,
                    additionalBytes = rowPayloadBytes,
                    context = "Version 71 copied tables"
                )
                if (totalPayloadBytes > payloadLimits.maximumTotalBytes) {
                    throw TonUpgradeSqlIntegrityException(
                        "Version 71 copied tables exceed the safe cumulative " +
                            "migration payload limit"
                    )
                }
            }
            return totalPayloadBytes
        }
    }

    private fun Cursor.readNullablePayloadLength(
        column: String,
        context: String
    ): Long? {
        val index = getColumnIndexOrThrow(column)
        if (isNull(index)) return null
        if (getType(index) != Cursor.FIELD_TYPE_INTEGER) {
            throw TonUpgradeSqlIntegrityException(
                "$context payload length is not a SQLite integer"
            )
        }
        return getLong(index).also { length ->
            if (length < 0L) {
                throw TonUpgradeSqlIntegrityException(
                    "$context has a negative payload length"
                )
            }
        }
    }

    private fun readExactTable(
        database: SupportSQLiteDatabase,
        tableName: String
    ): List<DatabaseColumn> {
        requireOrdinaryTable(database, tableName)
        val name = boundedTextProjection(
            column = "name",
            alias = BOUNDED_COLUMN_NAME,
            maxBytes = MAX_IDENTIFIER_BYTES
        )
        val type = boundedTextProjection(
            column = "type",
            alias = BOUNDED_COLUMN_TYPE,
            maxBytes = MAX_COLUMN_TYPE_BYTES
        )
        val defaultValue = boundedTextProjection(
            column = "dflt_value",
            alias = BOUNDED_COLUMN_DEFAULT,
            maxBytes = MAX_COLUMN_DEFAULT_BYTES
        )
        val rows = mutableListOf<DatabaseColumn>()
        database.query(
            "SELECT cid, $name, $type, `notnull`, $defaultValue, pk " +
                "FROM pragma_table_info('${safeIdentifier(tableName)}') " +
                "ORDER BY cid ASC LIMIT ${MAX_TABLE_COLUMNS + 1}"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                if (rows.size == MAX_TABLE_COLUMNS) {
                    throw TonUpgradeSqlIntegrityException(
                        "$tableName exceeds the bounded migration column count"
                    )
                }
                val cid = cursor.requireInteger("cid", tableName)
                val exactName = cursor.requireText(
                    alias = BOUNDED_COLUMN_NAME,
                    maxBytes = MAX_IDENTIFIER_BYTES,
                    context = "$tableName column name"
                )
                val exactType = cursor.requireText(
                    alias = BOUNDED_COLUMN_TYPE,
                    maxBytes = MAX_COLUMN_TYPE_BYTES,
                    context = "$tableName column $exactName type"
                )
                cursor.readNullableText(
                    alias = BOUNDED_COLUMN_DEFAULT,
                    maxBytes = MAX_COLUMN_DEFAULT_BYTES,
                    context = "$tableName column $exactName default"
                )
                if (cid != rows.size.toLong()) {
                    throw TonUpgradeSqlIntegrityException(
                        "$tableName has an incompatible column order"
                    )
                }
                rows += DatabaseColumn(
                    position = cid.toInt(),
                    name = exactName,
                    type = exactType.uppercase(Locale.US),
                    notNull = cursor.requireBoolean(
                        column = "notnull",
                        context = tableName
                    ),
                    primaryKeyPosition = cursor.requireBoundedPosition(
                        column = "pk",
                        maximum = MAX_TABLE_COLUMNS,
                        context = tableName
                    )
                )
            }
        }
        if (rows.isEmpty()) {
            throw TonUpgradeSqlIntegrityException(
                "$tableName has no readable columns"
            )
        }
        return rows
    }

    private fun requireFutureColumnsAbsent(
        schemas: Map<String, List<DatabaseColumn>>
    ) {
        FUTURE_COLUMNS.forEach { (tableName, columns) ->
            val actualNames = checkNotNull(schemas[tableName])
                .mapTo(mutableSetOf(), DatabaseColumn::name)
            columns.forEach { futureColumn ->
                if (futureColumn in actualNames) {
                    throw TonUpgradeSqlIntegrityException(
                        "Version 71 $tableName already contains $futureColumn; " +
                            "a later migration would add it again"
                    )
                }
            }
        }
    }

    private fun requireExactColumns(
        context: String,
        actual: List<DatabaseColumn>,
        expected: List<ColumnSpec>
    ) {
        val actualByName = actual.associateBy(DatabaseColumn::name)
        expected.forEach { column ->
            val found = actualByName[column.name]
                ?: throw TonUpgradeSqlIntegrityException(
                    "$context is missing required column ${column.name}"
                )
            if (
                found.type != column.type ||
                found.notNull != column.notNull ||
                found.primaryKeyPosition != column.primaryKeyPosition
            ) {
                throw TonUpgradeSqlIntegrityException(
                    "$context column ${column.name} has an incompatible definition"
                )
            }
        }
        val expectedNames = expected.mapTo(mutableSetOf(), ColumnSpec::name)
        val unexpected = actual.firstOrNull { it.name !in expectedNames }
        if (unexpected != null) {
            throw TonUpgradeSqlIntegrityException(
                "$context contains unexpected column ${unexpected.name}"
            )
        }
        if (actual.size != expected.size) {
            throw TonUpgradeSqlIntegrityException(
                "$context has an incompatible column count"
            )
        }
    }

    private fun requireExactForeignKeys(
        database: SupportSQLiteDatabase,
        table: TableSpec
    ) {
        val actual = readForeignKeys(database, table.name)
        if (actual != table.foreignKeys) {
            throw TonUpgradeSqlIntegrityException(
                "Version 71 ${table.name} has incompatible foreign keys"
            )
        }
    }

    private fun readForeignKeys(
        database: SupportSQLiteDatabase,
        tableName: String
    ): Set<ForeignKeySpec> {
        val parentTable = boundedTextProjection(
            column = "`table`",
            alias = BOUNDED_FOREIGN_TABLE,
            maxBytes = MAX_IDENTIFIER_BYTES
        )
        val from = boundedTextProjection(
            column = "`from`",
            alias = BOUNDED_FOREIGN_FROM,
            maxBytes = MAX_IDENTIFIER_BYTES
        )
        val to = boundedTextProjection(
            column = "`to`",
            alias = BOUNDED_FOREIGN_TO,
            maxBytes = MAX_IDENTIFIER_BYTES
        )
        val onUpdate = boundedTextProjection(
            column = "on_update",
            alias = BOUNDED_FOREIGN_UPDATE,
            maxBytes = MAX_FOREIGN_ACTION_BYTES
        )
        val onDelete = boundedTextProjection(
            column = "on_delete",
            alias = BOUNDED_FOREIGN_DELETE,
            maxBytes = MAX_FOREIGN_ACTION_BYTES
        )
        val match = boundedTextProjection(
            column = "`match`",
            alias = BOUNDED_FOREIGN_MATCH,
            maxBytes = MAX_FOREIGN_ACTION_BYTES
        )
        val rows = mutableSetOf<ForeignKeySpec>()
        val foreignKeyIds = mutableSetOf<Long>()
        database.query(
            "SELECT id, seq, $parentTable, $from, $to, $onUpdate, " +
                "$onDelete, $match " +
                "FROM pragma_foreign_key_list('${safeIdentifier(tableName)}') " +
                "ORDER BY id ASC, seq ASC LIMIT ${MAX_FOREIGN_KEYS + 1}"
        ).use { cursor ->
            var rowCount = 0
            while (cursor.moveToNext()) {
                if (rowCount == MAX_FOREIGN_KEYS) {
                    throw TonUpgradeSqlIntegrityException(
                        "$tableName exceeds the bounded foreign-key count"
                    )
                }
                rowCount += 1
                val id = cursor.requireInteger("id", tableName)
                val sequence = cursor.requireInteger("seq", tableName)
                if (
                    id < 0L ||
                    id >= MAX_FOREIGN_KEYS.toLong() ||
                    sequence != 0L ||
                    !foreignKeyIds.add(id)
                ) {
                    throw TonUpgradeSqlIntegrityException(
                        "$tableName has an incompatible foreign-key grouping"
                    )
                }
                val spec = ForeignKeySpec(
                    parentTable = cursor.requireText(
                        BOUNDED_FOREIGN_TABLE,
                        MAX_IDENTIFIER_BYTES,
                        "$tableName foreign-key parent"
                    ),
                    from = cursor.requireText(
                        BOUNDED_FOREIGN_FROM,
                        MAX_IDENTIFIER_BYTES,
                        "$tableName foreign-key source"
                    ),
                    to = cursor.requireText(
                        BOUNDED_FOREIGN_TO,
                        MAX_IDENTIFIER_BYTES,
                        "$tableName foreign-key target"
                    ),
                    onUpdate = cursor.requireText(
                        BOUNDED_FOREIGN_UPDATE,
                        MAX_FOREIGN_ACTION_BYTES,
                        "$tableName foreign-key update action"
                    ),
                    onDelete = cursor.requireText(
                        BOUNDED_FOREIGN_DELETE,
                        MAX_FOREIGN_ACTION_BYTES,
                        "$tableName foreign-key delete action"
                    ),
                    match = cursor.requireText(
                        BOUNDED_FOREIGN_MATCH,
                        MAX_FOREIGN_ACTION_BYTES,
                        "$tableName foreign-key match"
                    )
                )
                if (!rows.add(spec)) {
                    throw TonUpgradeSqlIntegrityException(
                        "$tableName contains duplicate foreign-key definitions"
                    )
                }
            }
        }
        return rows
    }

    private fun requireExactIndexes(
        database: SupportSQLiteDatabase,
        table: TableSpec
    ) {
        val actual = readExplicitIndexes(database, table.name)
        if (actual != table.indexes) {
            throw TonUpgradeSqlIntegrityException(
                "Version 71 ${table.name} has incompatible explicit indexes"
            )
        }
    }

    private fun readExplicitIndexes(
        database: SupportSQLiteDatabase,
        tableName: String
    ): Set<IndexSpec> {
        val name = boundedTextProjection(
            column = "name",
            alias = BOUNDED_INDEX_NAME,
            maxBytes = MAX_IDENTIFIER_BYTES
        )
        val origin = boundedTextProjection(
            column = "origin",
            alias = BOUNDED_INDEX_ORIGIN,
            maxBytes = MAX_INDEX_ORIGIN_BYTES
        )
        val indexes = mutableSetOf<IndexSpec>()
        database.query(
            "SELECT $name, `unique`, $origin, partial " +
                "FROM pragma_index_list('${safeIdentifier(tableName)}') " +
                "ORDER BY seq ASC LIMIT ${MAX_INDEXES + 1}"
        ).use { cursor ->
            var rowCount = 0
            while (cursor.moveToNext()) {
                if (rowCount == MAX_INDEXES) {
                    throw TonUpgradeSqlIntegrityException(
                        "$tableName exceeds the bounded index count"
                    )
                }
                rowCount += 1
                val exactOrigin = cursor.requireText(
                    BOUNDED_INDEX_ORIGIN,
                    MAX_INDEX_ORIGIN_BYTES,
                    "$tableName index origin"
                )
                if (exactOrigin != EXPLICIT_INDEX_ORIGIN) continue

                val indexName = cursor.requireText(
                    BOUNDED_INDEX_NAME,
                    MAX_IDENTIFIER_BYTES,
                    "$tableName index name"
                )
                val index = IndexSpec(
                    name = indexName,
                    unique = cursor.requireBoolean(
                        column = "unique",
                        context = "$tableName index $indexName"
                    ),
                    partial = cursor.requireBoolean(
                        column = "partial",
                        context = "$tableName index $indexName"
                    ),
                    columns = readIndexColumns(database, indexName)
                )
                if (!indexes.add(index)) {
                    throw TonUpgradeSqlIntegrityException(
                        "$tableName contains duplicate explicit indexes"
                    )
                }
            }
        }
        return indexes
    }

    private fun readIndexColumns(
        database: SupportSQLiteDatabase,
        indexName: String
    ): List<String> {
        val name = boundedTextProjection(
            column = "name",
            alias = BOUNDED_INDEX_COLUMN,
            maxBytes = MAX_IDENTIFIER_BYTES
        )
        val columns = mutableListOf<String>()
        database.query(
            "SELECT seqno, cid, $name " +
                "FROM pragma_index_info('${safeIdentifier(indexName)}') " +
                "ORDER BY seqno ASC LIMIT ${MAX_INDEX_COLUMNS + 1}"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                if (columns.size == MAX_INDEX_COLUMNS) {
                    throw TonUpgradeSqlIntegrityException(
                        "$indexName exceeds the bounded index-column count"
                    )
                }
                if (
                    cursor.requireInteger("seqno", indexName) !=
                    columns.size.toLong()
                ) {
                    throw TonUpgradeSqlIntegrityException(
                        "$indexName has an incompatible column order"
                    )
                }
                cursor.requireInteger("cid", indexName)
                columns += cursor.requireText(
                    BOUNDED_INDEX_COLUMN,
                    MAX_IDENTIFIER_BYTES,
                    "$indexName column"
                )
            }
        }
        return columns
    }

    private fun requireCompatibleTonConnectionOrAbsent(
        database: SupportSQLiteDatabase
    ) {
        val schemaObject = readSchemaObject(database, TON_CONNECTION_TABLE)
            ?: return
        requireOrdinaryTable(TON_CONNECTION_TABLE, schemaObject)
        val columns = readExactTable(database, TON_CONNECTION_TABLE)
        requireExactColumns(
            context = "Pre-existing ton_connection",
            actual = columns,
            expected = TON_CONNECTION_COLUMNS
        )
        if (
            readForeignKeys(database, TON_CONNECTION_TABLE) !=
            TON_CONNECTION_FOREIGN_KEYS
        ) {
            throw TonUpgradeSqlIntegrityException(
                "Pre-existing ton_connection has incompatible foreign keys"
            )
        }
        if (readExplicitIndexes(database, TON_CONNECTION_TABLE).isNotEmpty()) {
            throw TonUpgradeSqlIntegrityException(
                "Pre-existing ton_connection has unexpected explicit indexes"
            )
        }
        requireExactReleasedRoomSchema(
            database = database,
            context = "Pre-existing ton_connection",
            tables = listOf(TON_CONNECTION_RELEASED_TABLE)
        ) { message ->
            TonUpgradeSqlIntegrityException(message)
        }
    }

    private fun requireObjectAbsent(
        database: SupportSQLiteDatabase,
        objectName: String
    ) {
        if (readSchemaObject(database, objectName) != null) {
            throw TonUpgradeSqlIntegrityException(
                "Version 71 contains reserved migration object $objectName"
            )
        }
    }

    private fun requireOrdinaryTableOrAbsent(
        database: SupportSQLiteDatabase,
        tableName: String
    ) {
        val schemaObject = readSchemaObject(database, tableName) ?: return
        requireOrdinaryTable(tableName, schemaObject)
    }

    private fun requireOrdinaryTable(
        database: SupportSQLiteDatabase,
        tableName: String
    ) {
        val schemaObject = readSchemaObject(database, tableName)
            ?: throw TonUpgradeSqlIntegrityException(
                "Version 71 is missing required table $tableName"
            )
        requireOrdinaryTable(tableName, schemaObject)
    }

    private fun requireOrdinaryTable(
        tableName: String,
        schemaObject: SchemaObject
    ) {
        if (
            schemaObject.type != TABLE_OBJECT_TYPE ||
            !schemaObject.sql.trimStart()
                .uppercase(Locale.US)
                .startsWith(CREATE_TABLE_PREFIX)
        ) {
            throw TonUpgradeSqlIntegrityException(
                "$tableName is not a compatible ordinary SQLite table"
            )
        }
    }

    private fun readSchemaObject(
        database: SupportSQLiteDatabase,
        objectName: String
    ): SchemaObject? {
        val objectType = boundedTextProjection(
            column = "type",
            alias = BOUNDED_SCHEMA_OBJECT_TYPE,
            maxBytes = MAX_SCHEMA_OBJECT_TYPE_BYTES
        )
        val schemaSql = boundedTextProjection(
            column = "sql",
            alias = BOUNDED_SCHEMA_SQL,
            maxBytes = MAX_SCHEMA_SQL_BYTES
        )
        database.query(
            "SELECT $objectType, $schemaSql " +
                "FROM sqlite_master WHERE name = ? COLLATE BINARY " +
                "ORDER BY rowid ASC LIMIT 2",
            arrayOf<Any?>(objectName)
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            val schemaObject = SchemaObject(
                type = cursor.requireText(
                    alias = BOUNDED_SCHEMA_OBJECT_TYPE,
                    maxBytes = MAX_SCHEMA_OBJECT_TYPE_BYTES,
                    context = "$objectName sqlite_master type"
                ),
                sql = cursor.requireText(
                    alias = BOUNDED_SCHEMA_SQL,
                    maxBytes = MAX_SCHEMA_SQL_BYTES,
                    context = "$objectName sqlite_master SQL"
                )
            )
            if (cursor.moveToNext()) {
                throw TonUpgradeSqlIntegrityException(
                    "$objectName has duplicate sqlite_master definitions"
                )
            }
            return schemaObject
        }
    }

    private fun Cursor.requireInteger(
        column: String,
        context: String
    ): Long {
        val index = getColumnIndexOrThrow(column)
        if (
            isNull(index) ||
            getType(index) != Cursor.FIELD_TYPE_INTEGER
        ) {
            throw TonUpgradeSqlIntegrityException(
                "$context has a non-integer $column value"
            )
        }
        return getLong(index)
    }

    private fun Cursor.requireBoolean(
        column: String,
        context: String
    ): Boolean = when (val value = requireInteger(column, context)) {
        0L -> false
        1L -> true
        else -> throw TonUpgradeSqlIntegrityException(
            "$context has an invalid SQLite boolean in $column"
        )
    }

    private fun Cursor.requireBoundedPosition(
        column: String,
        maximum: Int,
        context: String
    ): Int {
        val value = requireInteger(column, context)
        if (value !in 0L..maximum.toLong()) {
            throw TonUpgradeSqlIntegrityException(
                "$context has an out-of-range $column position"
            )
        }
        return value.toInt()
    }

    private fun Cursor.requireText(
        alias: String,
        maxBytes: Int,
        context: String
    ): String {
        return readNullableText(alias, maxBytes, context)
            ?: throw TonUpgradeSqlIntegrityException(
                "$context is unexpectedly null"
            )
    }

    private fun Cursor.readNullableText(
        alias: String,
        maxBytes: Int,
        context: String
    ): String? {
        val bounded = readBoundedText(alias, maxBytes)
        if (!bounded.isPresent) return null
        if (
            !bounded.hasExpectedStorageClass ||
            bounded.isOversized ||
            bounded.value == null
        ) {
            throw TonUpgradeSqlIntegrityException(
                "$context is not a safe bounded SQLite string"
            )
        }
        return bounded.value
    }

    private fun safeIdentifier(identifier: String): String {
        check(
            identifier.isNotEmpty() &&
                identifier.all { it == '_' || it.isLetterOrDigit() }
        )
        return identifier
    }

    private data class SchemaObject(
        val type: String,
        val sql: String
    )

    private data class DatabaseColumn(
        val position: Int,
        val name: String,
        val type: String,
        val notNull: Boolean,
        val primaryKeyPosition: Int
    )

    private data class ForeignKeySpec(
        val parentTable: String,
        val from: String,
        val to: String,
        val onUpdate: String = NO_ACTION,
        val onDelete: String,
        val match: String = NONE
    )

    private data class TableSpec(
        val name: String,
        val columns: List<ColumnSpec>,
        val alternateColumnLayouts: List<List<ColumnSpec>> = emptyList(),
        val foreignKeys: Set<ForeignKeySpec> = emptySet(),
        val indexes: Set<IndexSpec> = emptySet(),
        val alternateColumnOrderCohorts: List<ReleasedRoomColumnOrderCohort> =
            emptyList()
    )

    private val VERSION_71_TABLES = listOf(
        TableSpec(
            name = "meta_accounts",
            columns = listOf(
                ColumnSpec("substratePublicKey", "BLOB", notNull = true),
                ColumnSpec("substrateCryptoType", "TEXT", notNull = true),
                ColumnSpec("substrateAccountId", "BLOB", notNull = true),
                ColumnSpec("ethereumPublicKey", "BLOB"),
                ColumnSpec("ethereumAddress", "BLOB"),
                ColumnSpec("name", "TEXT", notNull = true),
                ColumnSpec("isSelected", "INTEGER", notNull = true),
                ColumnSpec("position", "INTEGER", notNull = true),
                ColumnSpec(
                    "isBackedUp",
                    "INTEGER",
                    notNull = true
                ),
                ColumnSpec("googleBackupAddress", "TEXT"),
                ColumnSpec(
                    "initialized",
                    "INTEGER",
                    notNull = true
                ),
                ColumnSpec(
                    "id",
                    "INTEGER",
                    notNull = true,
                    primaryKeyPosition = 1
                )
            ),
            indexes = setOf(
                IndexSpec(
                    name = "index_meta_accounts_substrateAccountId",
                    columns = listOf("substrateAccountId")
                ),
                IndexSpec(
                    name = "index_meta_accounts_ethereumAddress",
                    columns = listOf("ethereumAddress")
                )
            ),
            alternateColumnOrderCohorts =
            RELEASED_VERSION_71_META_ACCOUNT_COLUMN_COHORTS
        ),
        TableSpec(
            name = "chain_accounts",
            columns = listOf(
                ColumnSpec(
                    "metaId",
                    "INTEGER",
                    notNull = true,
                    primaryKeyPosition = 1
                ),
                ColumnSpec(
                    "chainId",
                    "TEXT",
                    notNull = true,
                    primaryKeyPosition = 2
                ),
                ColumnSpec("publicKey", "BLOB", notNull = true),
                ColumnSpec("accountId", "BLOB", notNull = true),
                ColumnSpec("cryptoType", "TEXT", notNull = true),
                ColumnSpec("name", "TEXT", notNull = true),
                ColumnSpec(
                    "initialized",
                    "INTEGER",
                    notNull = true,
                    allowedDefaultValues = RELEASED_ABSENT_OR_ZERO_DEFAULT
                )
            ),
            foreignKeys = setOf(
                ForeignKeySpec(
                    parentTable = "meta_accounts",
                    from = "metaId",
                    to = "id",
                    onDelete = CASCADE
                ),
                ForeignKeySpec(
                    parentTable = "chains",
                    from = "chainId",
                    to = "id",
                    onDelete = NO_ACTION
                )
            ),
            indexes = setOf(
                IndexSpec(
                    name = "index_chain_accounts_chainId",
                    columns = listOf("chainId")
                ),
                IndexSpec(
                    name = "index_chain_accounts_metaId",
                    columns = listOf("metaId")
                ),
                IndexSpec(
                    name = "index_chain_accounts_accountId",
                    columns = listOf("accountId")
                )
            )
        ),
        TableSpec(
            name = "favorite_chains",
            columns = listOf(
                ColumnSpec(
                    "metaId",
                    "INTEGER",
                    notNull = true,
                    primaryKeyPosition = 1
                ),
                ColumnSpec(
                    "chainId",
                    "TEXT",
                    notNull = true,
                    primaryKeyPosition = 2
                ),
                ColumnSpec(
                    "isFavorite",
                    "INTEGER",
                    notNull = true,
                    allowedDefaultValues = RELEASED_ABSENT_OR_ZERO_DEFAULT
                )
            ),
            foreignKeys = setOf(
                ForeignKeySpec(
                    parentTable = "meta_accounts",
                    from = "metaId",
                    to = "id",
                    onDelete = CASCADE
                ),
                ForeignKeySpec(
                    parentTable = "chains",
                    from = "chainId",
                    to = "id",
                    onDelete = NO_ACTION
                )
            )
        ),
        TableSpec(
            name = "nomis_wallet_score",
            columns = listOf(
                ColumnSpec(
                    "metaId",
                    "INTEGER",
                    notNull = true,
                    primaryKeyPosition = 1
                ),
                ColumnSpec("score", "INTEGER", notNull = true),
                ColumnSpec("updated", "INTEGER", notNull = true),
                ColumnSpec("nativeBalanceUsd", "TEXT", notNull = true),
                ColumnSpec("holdTokensUsd", "TEXT", notNull = true),
                ColumnSpec(
                    "walletAgeInMonths",
                    "INTEGER",
                    notNull = true
                ),
                ColumnSpec(
                    "totalTransactions",
                    "INTEGER",
                    notNull = true
                ),
                ColumnSpec(
                    "rejectedTransactions",
                    "INTEGER",
                    notNull = true
                ),
                ColumnSpec(
                    "avgTransactionTimeInHours",
                    "REAL",
                    notNull = true
                ),
                ColumnSpec(
                    "maxTransactionTimeInHours",
                    "REAL",
                    notNull = true
                ),
                ColumnSpec(
                    "minTransactionTimeInHours",
                    "REAL",
                    notNull = true
                ),
                ColumnSpec("scoredAt", "TEXT", notNull = true)
            ),
            foreignKeys = setOf(
                ForeignKeySpec(
                    parentTable = "meta_accounts",
                    from = "metaId",
                    to = "id",
                    onDelete = CASCADE
                )
            )
        ),
        TableSpec(
            name = "chains",
            columns = listOf(
                ColumnSpec(
                    "id",
                    "TEXT",
                    notNull = true,
                    primaryKeyPosition = 1
                ),
                ColumnSpec("paraId", "TEXT"),
                ColumnSpec("parentId", "TEXT"),
                ColumnSpec("rank", "INTEGER"),
                ColumnSpec("name", "TEXT", notNull = true),
                ColumnSpec("minSupportedVersion", "TEXT"),
                ColumnSpec("icon", "TEXT", notNull = true),
                ColumnSpec("prefix", "INTEGER", notNull = true),
                ColumnSpec("isEthereumBased", "INTEGER", notNull = true),
                ColumnSpec("isTestNet", "INTEGER", notNull = true),
                ColumnSpec("hasCrowdloans", "INTEGER", notNull = true),
                ColumnSpec("supportStakingPool", "INTEGER", notNull = true),
                ColumnSpec(
                    "isEthereumChain",
                    "INTEGER",
                    notNull = true
                ),
                ColumnSpec(
                    "isChainlinkProvider",
                    "INTEGER",
                    notNull = true
                ),
                ColumnSpec(
                    "supportNft",
                    "INTEGER",
                    notNull = true
                ),
                ColumnSpec(
                    "isUsesAppId",
                    "INTEGER",
                    notNull = true
                ),
                ColumnSpec("identityChain", "TEXT"),
                ColumnSpec("remoteAssetsSource", "TEXT"),
                ColumnSpec("staking_url", "TEXT"),
                ColumnSpec("staking_type", "TEXT"),
                ColumnSpec("history_url", "TEXT"),
                ColumnSpec("history_type", "TEXT"),
                ColumnSpec("crowdloans_url", "TEXT"),
                ColumnSpec("crowdloans_type", "TEXT")
            ),
            alternateColumnOrderCohorts =
            RELEASED_VERSION_71_CHAIN_COLUMN_COHORTS
        ),
        TableSpec(
            name = "chain_assets",
            columns = listOf(
                ColumnSpec(
                    "id",
                    "TEXT",
                    notNull = true,
                    primaryKeyPosition = 2
                ),
                ColumnSpec("name", "TEXT"),
                ColumnSpec("symbol", "TEXT", notNull = true),
                ColumnSpec(
                    "chainId",
                    "TEXT",
                    notNull = true,
                    primaryKeyPosition = 1
                ),
                ColumnSpec("icon", "TEXT", notNull = true),
                ColumnSpec("priceId", "TEXT"),
                ColumnSpec("staking", "TEXT", notNull = true),
                ColumnSpec("precision", "INTEGER", notNull = true),
                ColumnSpec("purchaseProviders", "TEXT"),
                ColumnSpec("isUtility", "INTEGER"),
                ColumnSpec("type", "TEXT"),
                ColumnSpec("currencyId", "TEXT"),
                ColumnSpec("existentialDeposit", "TEXT"),
                ColumnSpec("color", "TEXT"),
                ColumnSpec("isNative", "INTEGER"),
                ColumnSpec(
                    "ethereumType",
                    "TEXT",
                    allowedDefaultValues = RELEASED_ABSENT_OR_NULL_DEFAULT
                ),
                ColumnSpec("priceProvider", "TEXT")
            ),
            foreignKeys = setOf(
                ForeignKeySpec(
                    parentTable = "chains",
                    from = "chainId",
                    to = "id",
                    onDelete = CASCADE
                )
            ),
            indexes = setOf(
                IndexSpec(
                    name = "index_chain_assets_chainId",
                    columns = listOf("chainId")
                )
            )
        ),
        TableSpec(
            name = "assets",
            columns = listOf(
                ColumnSpec(
                    "id",
                    "TEXT",
                    notNull = true,
                    primaryKeyPosition = 1
                ),
                ColumnSpec(
                    "chainId",
                    "TEXT",
                    notNull = true,
                    primaryKeyPosition = 2
                ),
                ColumnSpec(
                    "accountId",
                    "BLOB",
                    notNull = true,
                    primaryKeyPosition = 3
                ),
                ColumnSpec(
                    "metaId",
                    "INTEGER",
                    notNull = true,
                    primaryKeyPosition = 4
                ),
                ColumnSpec("tokenPriceId", "TEXT"),
                ColumnSpec("freeInPlanks", "TEXT"),
                ColumnSpec("reservedInPlanks", "TEXT"),
                ColumnSpec("miscFrozenInPlanks", "TEXT"),
                ColumnSpec("feeFrozenInPlanks", "TEXT"),
                ColumnSpec("bondedInPlanks", "TEXT"),
                ColumnSpec("redeemableInPlanks", "TEXT"),
                ColumnSpec("unbondingInPlanks", "TEXT"),
                ColumnSpec(
                    "sortIndex",
                    "INTEGER",
                    notNull = true,
                    allowedDefaultValues = RELEASED_ABSENT_OR_ZERO_DEFAULT
                ),
                ColumnSpec(
                    "enabled",
                    "INTEGER",
                    allowedDefaultValues = RELEASED_ABSENT_OR_NULL_DEFAULT
                ),
                ColumnSpec(
                    "markedNotNeed",
                    "INTEGER",
                    notNull = true,
                    allowedDefaultValues = RELEASED_ABSENT_OR_ZERO_DEFAULT
                ),
                ColumnSpec("chainAccountName", "TEXT"),
                ColumnSpec("status", "TEXT")
            ),
            foreignKeys = setOf(
                ForeignKeySpec(
                    parentTable = "chains",
                    from = "chainId",
                    to = "id",
                    onDelete = CASCADE
                )
            ),
            indexes = setOf(
                IndexSpec(
                    name = "index_assets_metaId",
                    columns = listOf("metaId")
                ),
                IndexSpec(
                    name = "index_assets_chainId",
                    columns = listOf("chainId")
                )
            )
        ),
        TableSpec(
            name = "token_price",
            columns = listOf(
                ColumnSpec(
                    "priceId",
                    "TEXT",
                    notNull = true,
                    primaryKeyPosition = 1
                ),
                ColumnSpec("fiatRate", "TEXT"),
                ColumnSpec("fiatSymbol", "TEXT"),
                ColumnSpec("recentRateChange", "TEXT")
            )
        )
    )

    private val VERSION_71_RETAINED_ROOM_TABLES = listOf(
        ReleasedRoomTableSpec(
            name = "account_staking_accesses",
            columns = listOf(
                ColumnSpec(
                    "chainId",
                    "TEXT",
                    notNull = true,
                    primaryKeyPosition = 1
                ),
                ColumnSpec(
                    "chainAssetId",
                    "TEXT",
                    notNull = true,
                    primaryKeyPosition = 2
                ),
                ColumnSpec(
                    "accountId",
                    "BLOB",
                    notNull = true,
                    primaryKeyPosition = 3
                ),
                ColumnSpec("stashId", "BLOB"),
                ColumnSpec("controllerId", "BLOB")
            )
        ),
        ReleasedRoomTableSpec(
            name = "address_book",
            columns = listOf(
                ColumnSpec("address", "TEXT", notNull = true),
                ColumnSpec("name", "TEXT"),
                ColumnSpec("chainId", "TEXT", notNull = true),
                ColumnSpec("created", "INTEGER", notNull = true),
                ColumnSpec(
                    "id",
                    "INTEGER",
                    notNull = true,
                    primaryKeyPosition = 1
                )
            ),
            alternateColumnLayouts = listOf(
                // Migration 44 -> 45 created the generated id first.
                listOf(
                    ColumnSpec(
                        "id",
                        "INTEGER",
                        notNull = true,
                        primaryKeyPosition = 1
                    ),
                    ColumnSpec("address", "TEXT", notNull = true),
                    ColumnSpec("name", "TEXT"),
                    ColumnSpec("chainId", "TEXT", notNull = true),
                    ColumnSpec("created", "INTEGER", notNull = true)
                )
            ),
            indexes = setOf(
                IndexSpec(
                    name = "index_address_book_address_chainId",
                    unique = true,
                    columns = listOf("address", "chainId")
                )
            )
        ),
        ReleasedRoomTableSpec(
            name = "allpools",
            columns = listOf(
                ColumnSpec(
                    "tokenIdBase",
                    "TEXT",
                    notNull = true,
                    primaryKeyPosition = 1
                ),
                ColumnSpec(
                    "tokenIdTarget",
                    "TEXT",
                    notNull = true,
                    primaryKeyPosition = 2
                ),
                ColumnSpec("reserveBase", "TEXT", notNull = true),
                ColumnSpec("reserveTarget", "TEXT", notNull = true),
                ColumnSpec("totalIssuance", "TEXT", notNull = true),
                ColumnSpec("reservesAccount", "TEXT", notNull = true)
            )
        ),
        ReleasedRoomTableSpec(
            name = "chain_explorers",
            columns = listOf(
                ColumnSpec(
                    "chainId",
                    "TEXT",
                    notNull = true,
                    primaryKeyPosition = 1
                ),
                ColumnSpec(
                    "type",
                    "TEXT",
                    notNull = true,
                    primaryKeyPosition = 2
                ),
                ColumnSpec("types", "TEXT", notNull = true),
                ColumnSpec("url", "TEXT", notNull = true)
            ),
            indexes = setOf(
                IndexSpec(
                    name = "index_chain_explorers_chainId",
                    columns = listOf("chainId")
                )
            )
        ),
        ReleasedRoomTableSpec(
            name = "chain_nodes",
            columns = listOf(
                ColumnSpec(
                    "chainId",
                    "TEXT",
                    notNull = true,
                    primaryKeyPosition = 1
                ),
                ColumnSpec(
                    "url",
                    "TEXT",
                    notNull = true,
                    primaryKeyPosition = 2
                ),
                ColumnSpec("name", "TEXT", notNull = true),
                ColumnSpec("isActive", "INTEGER", notNull = true),
                ColumnSpec("isDefault", "INTEGER", notNull = true)
            ),
            indexes = setOf(
                IndexSpec(
                    name = "index_chain_nodes_chainId",
                    columns = listOf("chainId")
                )
            )
        ),
        ReleasedRoomTableSpec(
            name = "chain_runtimes",
            columns = listOf(
                ColumnSpec(
                    "chainId",
                    "TEXT",
                    notNull = true,
                    primaryKeyPosition = 1
                ),
                ColumnSpec("syncedVersion", "INTEGER", notNull = true),
                ColumnSpec("remoteVersion", "INTEGER", notNull = true)
            ),
            indexes = setOf(
                IndexSpec(
                    name = "index_chain_runtimes_chainId",
                    columns = listOf("chainId")
                )
            )
        ),
        ReleasedRoomTableSpec(
            name = "chain_types",
            columns = listOf(
                ColumnSpec(
                    "chainId",
                    "TEXT",
                    notNull = true,
                    primaryKeyPosition = 1
                ),
                ColumnSpec("typesConfig", "TEXT", notNull = true)
            )
        ),
        ReleasedRoomTableSpec(
            name = "operations",
            columns = listOf(
                ColumnSpec(
                    "id",
                    "TEXT",
                    notNull = true,
                    primaryKeyPosition = 1
                ),
                ColumnSpec(
                    "address",
                    "TEXT",
                    notNull = true,
                    primaryKeyPosition = 2
                ),
                ColumnSpec(
                    "chainId",
                    "TEXT",
                    notNull = true,
                    primaryKeyPosition = 3
                ),
                ColumnSpec(
                    "chainAssetId",
                    "TEXT",
                    notNull = true,
                    primaryKeyPosition = 4
                ),
                ColumnSpec("time", "INTEGER", notNull = true),
                ColumnSpec("status", "INTEGER", notNull = true),
                ColumnSpec("source", "INTEGER", notNull = true),
                ColumnSpec("operationType", "INTEGER", notNull = true),
                ColumnSpec("module", "TEXT"),
                ColumnSpec("call", "TEXT"),
                ColumnSpec("amount", "TEXT"),
                ColumnSpec("sender", "TEXT"),
                ColumnSpec("receiver", "TEXT"),
                ColumnSpec("hash", "TEXT"),
                ColumnSpec("fee", "TEXT"),
                ColumnSpec("isReward", "INTEGER"),
                ColumnSpec("era", "INTEGER"),
                ColumnSpec("validator", "TEXT"),
                ColumnSpec(
                    "liquidityFee",
                    "TEXT",
                    allowedDefaultValues = RELEASED_ABSENT_OR_NULL_DEFAULT
                ),
                ColumnSpec(
                    "market",
                    "TEXT",
                    allowedDefaultValues = RELEASED_ABSENT_OR_NULL_DEFAULT
                ),
                ColumnSpec(
                    "targetAssetId",
                    "TEXT",
                    allowedDefaultValues = RELEASED_ABSENT_OR_NULL_DEFAULT
                ),
                ColumnSpec(
                    "targetAmount",
                    "TEXT",
                    allowedDefaultValues = RELEASED_ABSENT_OR_NULL_DEFAULT
                )
            )
        ),
        ReleasedRoomTableSpec(
            name = "phishing",
            columns = listOf(
                ColumnSpec(
                    "address",
                    "TEXT",
                    notNull = true,
                    primaryKeyPosition = 1
                ),
                ColumnSpec("name", "TEXT"),
                ColumnSpec(
                    "type",
                    "TEXT",
                    notNull = true,
                    primaryKeyPosition = 2
                ),
                ColumnSpec("subtype", "TEXT")
            )
        ),
        ReleasedRoomTableSpec(
            name = "storage",
            columns = listOf(
                ColumnSpec(
                    "storageKey",
                    "TEXT",
                    notNull = true,
                    primaryKeyPosition = 2
                ),
                ColumnSpec("content", "TEXT"),
                ColumnSpec(
                    "chainId",
                    "TEXT",
                    notNull = true,
                    primaryKeyPosition = 1
                )
            )
        ),
        ReleasedRoomTableSpec(
            name = "total_reward",
            columns = listOf(
                ColumnSpec(
                    "accountAddress",
                    "TEXT",
                    notNull = true,
                    primaryKeyPosition = 1
                ),
                ColumnSpec("totalReward", "TEXT", notNull = true)
            )
        ),
        ReleasedRoomTableSpec(
            name = "userpools",
            columns = listOf(
                ColumnSpec(
                    "userTokenIdBase",
                    "TEXT",
                    notNull = true,
                    primaryKeyPosition = 1
                ),
                ColumnSpec(
                    "userTokenIdTarget",
                    "TEXT",
                    notNull = true,
                    primaryKeyPosition = 2
                ),
                ColumnSpec(
                    "accountAddress",
                    "TEXT",
                    notNull = true,
                    primaryKeyPosition = 3
                ),
                ColumnSpec(
                    "poolProvidersBalance",
                    "TEXT",
                    notNull = true
                )
            ),
            indexes = setOf(
                IndexSpec(
                    name = "index_userpools_accountAddress",
                    columns = listOf("accountAddress")
                )
            )
        ),
        ReleasedRoomTableSpec(
            name = "room_master_table",
            columns = listOf(
                ColumnSpec(
                    "id",
                    "INTEGER",
                    primaryKeyPosition = 1
                ),
                ColumnSpec("identity_hash", "TEXT")
            )
        )
    )

    private val VERSION_71_RELEASED_ROOM_TABLES =
        VERSION_71_TABLES.map { table ->
            ReleasedRoomTableSpec(
                name = table.name,
                columns = table.columns,
                alternateColumnLayouts = table.alternateColumnLayouts,
                indexes = table.indexes,
                alternateColumnOrderCohorts =
                table.alternateColumnOrderCohorts
            )
        } + VERSION_71_RETAINED_ROOM_TABLES

    private fun releasedSchemaTable(
        name: String
    ) = ReleasedRoomSchemaObjectSpec(
        type = "table",
        name = name,
        tableName = name
    )

    private fun releasedSchemaIndex(
        name: String,
        tableName: String
    ) = ReleasedRoomSchemaObjectSpec(
        type = "index",
        name = name,
        tableName = tableName
    )

    private val VERSION_71_REQUIRED_SCHEMA_OBJECTS =
        (
            VERSION_71_RELEASED_ROOM_TABLES.map { table ->
                releasedSchemaTable(table.name)
            } +
                releasedSchemaTable("android_metadata") +
                releasedSchemaTable("sqlite_sequence") +
                VERSION_71_RELEASED_ROOM_TABLES.flatMap { table ->
                    table.indexes.map { index ->
                        releasedSchemaIndex(index.name, table.name)
                    }
                } +
                VERSION_71_AUTO_INDEX_TABLES.map { tableName ->
                    releasedSchemaIndex(
                        name = "sqlite_autoindex_${tableName}_1",
                        tableName = tableName
                    )
                }
            ).toSet()

    private val VERSION_71_USERS_SCHEMA_OBJECTS = setOf(
        releasedSchemaTable("users"),
        releasedSchemaIndex("sqlite_autoindex_users_1", "users")
    )

    private val VERSION_71_SORA_CARD_SCHEMA_OBJECTS = setOf(
        releasedSchemaTable("sora_card"),
        releasedSchemaIndex("sqlite_autoindex_sora_card_1", "sora_card")
    )

    private val VERSION_71_TON_CONNECTION_SCHEMA_OBJECTS = setOf(
        releasedSchemaTable(TON_CONNECTION_TABLE),
        releasedSchemaIndex(
            "sqlite_autoindex_ton_connection_1",
            TON_CONNECTION_TABLE
        )
    )

    private val VERSION_71_ALLOWED_SCHEMA_OBJECT_SETS = setOf(
        VERSION_71_REQUIRED_SCHEMA_OBJECTS,
        VERSION_71_REQUIRED_SCHEMA_OBJECTS +
            VERSION_71_USERS_SCHEMA_OBJECTS,
        VERSION_71_REQUIRED_SCHEMA_OBJECTS +
            VERSION_71_SORA_CARD_SCHEMA_OBJECTS,
        VERSION_71_REQUIRED_SCHEMA_OBJECTS +
            VERSION_71_USERS_SCHEMA_OBJECTS +
            VERSION_71_SORA_CARD_SCHEMA_OBJECTS,
        VERSION_71_REQUIRED_SCHEMA_OBJECTS +
            VERSION_71_TON_CONNECTION_SCHEMA_OBJECTS,
        VERSION_71_REQUIRED_SCHEMA_OBJECTS +
            VERSION_71_USERS_SCHEMA_OBJECTS +
            VERSION_71_TON_CONNECTION_SCHEMA_OBJECTS,
        VERSION_71_REQUIRED_SCHEMA_OBJECTS +
            VERSION_71_SORA_CARD_SCHEMA_OBJECTS +
            VERSION_71_TON_CONNECTION_SCHEMA_OBJECTS,
        VERSION_71_REQUIRED_SCHEMA_OBJECTS +
            VERSION_71_USERS_SCHEMA_OBJECTS +
            VERSION_71_SORA_CARD_SCHEMA_OBJECTS +
            VERSION_71_TON_CONNECTION_SCHEMA_OBJECTS
    )

    private val TON_CONNECTION_COLUMNS = listOf(
        ColumnSpec(
            "metaId",
            "INTEGER",
            notNull = true,
            primaryKeyPosition = 1
        ),
        ColumnSpec("clientId", "TEXT", notNull = true),
        ColumnSpec("name", "TEXT", notNull = true),
        ColumnSpec("icon", "TEXT", notNull = true),
        ColumnSpec(
            "url",
            "TEXT",
            notNull = true,
            primaryKeyPosition = 2
        ),
        ColumnSpec(
            "source",
            "TEXT",
            notNull = true,
            primaryKeyPosition = 3
        )
    )

    private val TON_CONNECTION_FOREIGN_KEYS = setOf(
        ForeignKeySpec(
            parentTable = "meta_accounts",
            from = "metaId",
            to = "id",
            onDelete = CASCADE
        )
    )

    private val TON_CONNECTION_RELEASED_TABLE = ReleasedRoomTableSpec(
        name = TON_CONNECTION_TABLE,
        columns = TON_CONNECTION_COLUMNS
    )

    private val FUTURE_COLUMNS = mapOf(
        "meta_accounts" to setOf("tonPublicKey"),
        "chains" to setOf(
            "ecosystem",
            "androidMinAppVersion",
            "tonBridgeUrl",
            "xcm"
        ),
        "chain_assets" to setOf("coinbaseUrl")
    )

    private val RESERVED_WORK_TABLES = setOf(
        "_meta_accounts",
        "_chain_assets",
        "_assets",
        "_ton_migration_chain_accounts",
        "_ton_migration_favorite_chains",
        "_ton_migration_nomis_wallet_score"
    )

    private val OPTIONAL_DROP_TARGETS = setOf("users", "sora_card")

    private const val TON_CONNECTION_TABLE = "ton_connection"
    private const val TABLE_OBJECT_TYPE = "table"
    private const val CREATE_TABLE_PREFIX = "CREATE TABLE"
    private const val EXPLICIT_INDEX_ORIGIN = "c"
    private const val NO_ACTION = "NO ACTION"
    private const val CASCADE = "CASCADE"
    private const val NONE = "NONE"

    private const val MAX_TABLE_COLUMNS = 64
    private const val MAX_FOREIGN_KEYS = 16
    private const val MAX_INDEXES = 16
    private const val MAX_INDEX_COLUMNS = 8
    private const val MAX_IDENTIFIER_BYTES = 128
    private const val MAX_COLUMN_TYPE_BYTES = 64
    private const val MAX_COLUMN_DEFAULT_BYTES = 256
    private const val MAX_FOREIGN_ACTION_BYTES = 32
    private const val MAX_INDEX_ORIGIN_BYTES = 8
    private const val MAX_SCHEMA_OBJECT_TYPE_BYTES = 16
    private const val MAX_SCHEMA_SQL_BYTES = 16_384
    private const val BOUNDED_COLUMN_NAME = "boundedColumnName"
    private const val BOUNDED_COLUMN_TYPE = "boundedColumnType"
    private const val BOUNDED_COLUMN_DEFAULT = "boundedColumnDefault"
    private const val BOUNDED_FOREIGN_TABLE = "boundedForeignTable"
    private const val BOUNDED_FOREIGN_FROM = "boundedForeignFrom"
    private const val BOUNDED_FOREIGN_TO = "boundedForeignTo"
    private const val BOUNDED_FOREIGN_UPDATE = "boundedForeignUpdate"
    private const val BOUNDED_FOREIGN_DELETE = "boundedForeignDelete"
    private const val BOUNDED_FOREIGN_MATCH = "boundedForeignMatch"
    private const val BOUNDED_INDEX_NAME = "boundedIndexName"
    private const val BOUNDED_INDEX_ORIGIN = "boundedIndexOrigin"
    private const val BOUNDED_INDEX_COLUMN = "boundedIndexColumn"
    private const val BOUNDED_SCHEMA_OBJECT_TYPE = "boundedSchemaObjectType"
    private const val BOUNDED_SCHEMA_SQL = "boundedSchemaSql"
}
