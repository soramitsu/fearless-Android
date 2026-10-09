@file:Suppress("MagicNumber", "TooManyFunctions")

package jp.co.soramitsu.coredb.migrations

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase

internal data class MigrationForeignKeyColumnMapping(
    val from: String,
    val to: String
) {
    init {
        require(from.isSafeMigrationTableName())
        require(to.isSafeMigrationTableName())
    }
}

internal data class MigrationForeignKeyDefinition(
    val parentTable: String,
    val columns: List<MigrationForeignKeyColumnMapping>,
    val onUpdate: String = FOREIGN_KEY_NO_ACTION,
    val onDelete: String,
    val match: String = FOREIGN_KEY_MATCH_NONE
) {
    init {
        require(parentTable.isSafeMigrationTableName())
        require(columns.isNotEmpty())
        require(columns.distinct().size == columns.size)
        require(onUpdate.isAllowedForeignKeyAction())
        require(onDelete.isAllowedForeignKeyAction())
        require(match == FOREIGN_KEY_MATCH_NONE)
    }
}

/**
 * Bounds every database-controlled input traversed by a global
 * `pragma_foreign_key_check`.
 *
 * SQLite must inspect every child row to prove that a database has no foreign
 * key violations. `LIMIT 1` only bounds the result set, so callers must first
 * cap the schema, foreign-key definitions, and rows of every allowed child
 * table.
 */
@Suppress("LongParameterList")
internal data class MigrationForeignKeyCheckLimits(
    val maximumSchemaObjects: Int,
    val maximumOrdinaryTables: Int,
    val maximumForeignKeyDefinitionsPerTable: Int,
    val maximumRowsByTable: Map<String, Int>,
    val expectedForeignKeysByTable: Map<
        String,
        Set<MigrationForeignKeyDefinition>
    >,
    val optionalForeignKeyTables: Set<String> = emptySet(),
    val expectedParentPrimaryKeysByTable: Map<String, List<String>>
) {
    val allowedForeignKeyTables: Set<String>
        get() = expectedForeignKeysByTable.keys

    init {
        require(maximumSchemaObjects in 1 until Int.MAX_VALUE)
        require(maximumOrdinaryTables in 1..maximumSchemaObjects)
        require(maximumForeignKeyDefinitionsPerTable in 1 until Int.MAX_VALUE)
        require(maximumRowsByTable.isNotEmpty())
        require(expectedForeignKeysByTable.isNotEmpty())
        require(allowedForeignKeyTables.all(maximumRowsByTable::containsKey))
        require(optionalForeignKeyTables.all(allowedForeignKeyTables::contains))
        maximumRowsByTable.forEach { (tableName, maximumRows) ->
            require(tableName.isSafeMigrationTableName())
            require(maximumRows in 1 until Int.MAX_VALUE)
        }
        expectedForeignKeysByTable.forEach { (tableName, definitions) ->
            require(tableName.isSafeMigrationTableName())
            require(definitions.isNotEmpty())
            require(
                definitions.size <= maximumForeignKeyDefinitionsPerTable
            )
            require(
                definitions.sumOf { it.columns.size } <=
                    maximumForeignKeyDefinitionsPerTable
            )
        }
        require(expectedParentPrimaryKeysByTable.isNotEmpty())
        expectedParentPrimaryKeysByTable.forEach { (tableName, columns) ->
            require(tableName.isSafeMigrationTableName())
            require(columns.isNotEmpty())
            require(columns.distinct().size == columns.size)
            require(columns.all { it.isSafeMigrationTableName() })
        }
        require(
            expectedForeignKeysByTable.values
                .flatten()
                .mapTo(mutableSetOf(), MigrationForeignKeyDefinition::parentTable) ==
                expectedParentPrimaryKeysByTable.keys
        )
    }
}

internal val TON_UPGRADE_FOREIGN_KEY_CHECK_LIMITS =
    MigrationForeignKeyCheckLimits(
        maximumSchemaObjects = 128,
        maximumOrdinaryTables = 48,
        maximumForeignKeyDefinitionsPerTable = 2,
        maximumRowsByTable = linkedMapOf(
            "meta_accounts" to 8_192,
            "nomis_wallet_score" to 8_192,
            "chains" to 65_536,
            "chain_accounts" to 131_072,
            "token_price" to 262_144,
            "chain_nodes" to 262_144,
            "chain_assets" to 262_144,
            "chain_explorers" to 262_144,
            "favorite_chains" to 1_048_576,
            "assets" to 1_048_576,
            "userpools" to 1_048_576,
            "ton_connection" to 1_048_576
        ),
        expectedForeignKeysByTable = releasedForeignKeyDefinitions(
            assetsOnDelete = FOREIGN_KEY_CASCADE
        ),
        // Version 71 did not contain this table, but the released TON edge
        // deliberately accepts an already-compatible table left by a retry.
        optionalForeignKeyTables = setOf("ton_connection"),
        expectedParentPrimaryKeysByTable =
            releasedForeignKeyParentPrimaryKeys()
    )

internal val WALLET_INTEGRITY_FOREIGN_KEY_CHECK_LIMITS =
    MigrationForeignKeyCheckLimits(
        maximumSchemaObjects = 128,
        maximumOrdinaryTables = 48,
        maximumForeignKeyDefinitionsPerTable = 2,
        maximumRowsByTable = TON_UPGRADE_FOREIGN_KEY_CHECK_LIMITS
            .maximumRowsByTable
            .filterKeys(
                TON_UPGRADE_FOREIGN_KEY_CHECK_LIMITS
                    .allowedForeignKeyTables::contains
            ),
        expectedForeignKeysByTable = releasedForeignKeyDefinitions(
            assetsOnDelete = FOREIGN_KEY_NO_ACTION
        ),
        expectedParentPrimaryKeysByTable =
            releasedForeignKeyParentPrimaryKeys()
    )

internal fun requireBoundedForeignKeyCheck(
    database: SupportSQLiteDatabase,
    limits: MigrationForeignKeyCheckLimits,
    previouslyBoundedRows: Map<String, Int> = emptyMap(),
    failure: (String) -> IllegalStateException
) {
    previouslyBoundedRows.forEach { (tableName, maximumRows) ->
        require(tableName.isSafeMigrationTableName())
        require(maximumRows in 1 until Int.MAX_VALUE)
    }
    requireBoundedSchemaObjectCount(database, limits, failure)
    val ordinaryTables = readBoundedOrdinaryTableNames(
        database = database,
        limits = limits,
        failure = failure
    )
    requireExactForeignKeyDefinitions(
        database = database,
        ordinaryTables = ordinaryTables,
        limits = limits,
        failure = failure
    )
    requireExactParentPrimaryKeys(
        database = database,
        ordinaryTables = ordinaryTables,
        limits = limits,
        failure = failure
    )

    limits.maximumRowsByTable.forEach { (tableName, maximumRows) ->
        val previousLimit = previouslyBoundedRows[tableName]
        if (
            tableName in ordinaryTables &&
            (previousLimit == null || previousLimit > maximumRows)
        ) {
            requireBoundedTableRows(
                database = database,
                tableName = tableName,
                maximumRows = maximumRows,
                failure = failure
            )
        }
    }

    database.query(
        "SELECT 1 FROM pragma_foreign_key_check LIMIT 1"
    ).use { cursor ->
        if (cursor.moveToFirst()) {
            throw failure("The database contains a foreign-key violation")
        }
    }
}

private fun requireBoundedSchemaObjectCount(
    database: SupportSQLiteDatabase,
    limits: MigrationForeignKeyCheckLimits,
    failure: (String) -> IllegalStateException
) {
    database.query(
        "SELECT 1 FROM sqlite_master ORDER BY rowid ASC " +
            "LIMIT ${limits.maximumSchemaObjects + 1}"
    ).use { cursor ->
        var rowCount = 0
        while (cursor.moveToNext()) {
            if (rowCount == limits.maximumSchemaObjects) {
                throw failure(
                    "SQLite schema objects exceed the safe migration limit"
                )
            }
            rowCount += 1
        }
    }
}

private fun readBoundedOrdinaryTableNames(
    database: SupportSQLiteDatabase,
    limits: MigrationForeignKeyCheckLimits,
    failure: (String) -> IllegalStateException
): Set<String> {
    val boundedName = boundedTextProjection(
        column = "name",
        alias = BOUNDED_TABLE_NAME,
        maxBytes = MAX_TABLE_NAME_BYTES
    )
    val tableNames = linkedSetOf<String>()
    database.query(
        "SELECT $boundedName FROM sqlite_master " +
            "WHERE type = 'table' ORDER BY rowid ASC " +
            "LIMIT ${limits.maximumOrdinaryTables + 1}"
    ).use { cursor ->
        while (cursor.moveToNext()) {
            if (tableNames.size == limits.maximumOrdinaryTables) {
                throw failure(
                    "Ordinary SQLite tables exceed the safe migration limit"
                )
            }
            val name = cursor.readBoundedText(
                alias = BOUNDED_TABLE_NAME,
                maxBytes = MAX_TABLE_NAME_BYTES
            )
            val exactName = name.value
            if (
                !name.isPresent ||
                !name.hasExpectedStorageClass ||
                name.isOversized ||
                exactName == null ||
                !exactName.isSafeMigrationTableName() ||
                !tableNames.add(exactName)
            ) {
                throw failure(
                    "An ordinary SQLite table has an unsafe migration name"
                )
            }
        }
    }
    return tableNames
}

private fun requireBoundedTableRows(
    database: SupportSQLiteDatabase,
    tableName: String,
    maximumRows: Int,
    failure: (String) -> IllegalStateException
) {
    check(tableName.isSafeMigrationTableName())
    database.query(
        "SELECT 1 FROM `$tableName` LIMIT ${maximumRows + 1}"
    ).use { cursor ->
        var rowCount = 0
        while (cursor.moveToNext()) {
            if (rowCount == maximumRows) {
                throw failure(
                    "$tableName rows exceed the safe migration limit"
                )
            }
            rowCount += 1
        }
    }
}

private fun requireExactForeignKeyDefinitions(
    database: SupportSQLiteDatabase,
    ordinaryTables: Set<String>,
    limits: MigrationForeignKeyCheckLimits,
    failure: (String) -> IllegalStateException
) {
    ordinaryTables.forEach { tableName ->
        val expected = limits.expectedForeignKeysByTable[tableName]
        val actual = readBoundedForeignKeyDefinitions(
            database = database,
            tableName = tableName,
            maximumDefinitions =
                limits.maximumForeignKeyDefinitionsPerTable,
            failure = failure
        )
        when {
            expected == null && actual.isNotEmpty() -> {
                throw failure(
                    "Unexpected foreign-key child table $tableName cannot be " +
                        "safely checked"
                )
            }

            expected != null && actual != expected -> {
                throw failure(
                    "$tableName has missing, extra, or incompatible " +
                        "foreign-key definitions"
                )
            }
        }
    }

    val missingTables = limits.expectedForeignKeysByTable.keys
        .minus(limits.optionalForeignKeyTables)
        .minus(ordinaryTables)
    if (missingTables.isNotEmpty()) {
        throw failure(
            "Required foreign-key child table ${missingTables.first()} is missing"
        )
    }
}

@Suppress("LongMethod", "NestedBlockDepth")
private fun readBoundedForeignKeyDefinitions(
    database: SupportSQLiteDatabase,
    tableName: String,
    maximumDefinitions: Int,
    failure: (String) -> IllegalStateException
): Set<MigrationForeignKeyDefinition> {
    check(tableName.isSafeMigrationTableName())
    val parentTable = boundedTextProjection(
        column = "`table`",
        alias = BOUNDED_FOREIGN_KEY_PARENT,
        maxBytes = MAX_TABLE_NAME_BYTES
    )
    val from = boundedTextProjection(
        column = "`from`",
        alias = BOUNDED_FOREIGN_KEY_FROM,
        maxBytes = MAX_TABLE_NAME_BYTES
    )
    val to = boundedTextProjection(
        column = "`to`",
        alias = BOUNDED_FOREIGN_KEY_TO,
        maxBytes = MAX_TABLE_NAME_BYTES
    )
    val onUpdate = boundedTextProjection(
        column = "on_update",
        alias = BOUNDED_FOREIGN_KEY_ON_UPDATE,
        maxBytes = MAX_FOREIGN_KEY_ACTION_BYTES
    )
    val onDelete = boundedTextProjection(
        column = "on_delete",
        alias = BOUNDED_FOREIGN_KEY_ON_DELETE,
        maxBytes = MAX_FOREIGN_KEY_ACTION_BYTES
    )
    val match = boundedTextProjection(
        column = "`match`",
        alias = BOUNDED_FOREIGN_KEY_MATCH,
        maxBytes = MAX_FOREIGN_KEY_ACTION_BYTES
    )
    val rowsById = linkedMapOf<Long, MutableList<ObservedForeignKeyRow>>()
    var rowCount = 0
    database.query(
        "SELECT id, seq, $parentTable, $from, $to, $onUpdate, " +
            "$onDelete, $match " +
            "FROM pragma_foreign_key_list('$tableName') " +
            "ORDER BY id ASC, seq ASC LIMIT ${maximumDefinitions + 1}"
    ).use { cursor ->
        while (cursor.moveToNext()) {
            if (rowCount == maximumDefinitions) {
                throw failure(
                    "$tableName foreign keys exceed the safe migration limit"
                )
            }
            rowCount += 1
            val id = cursor.requireNonNegativeForeignKeyInteger(
                columnName = "id",
                tableName = tableName,
                failure = failure
            )
            val sequence = cursor.requireNonNegativeForeignKeyInteger(
                columnName = "seq",
                tableName = tableName,
                failure = failure
            )
            rowsById.getOrPut(id) { mutableListOf() } +=
                ObservedForeignKeyRow(
                    sequence = sequence,
                    parentTable = cursor.requireForeignKeyText(
                        BOUNDED_FOREIGN_KEY_PARENT,
                        MAX_TABLE_NAME_BYTES,
                        tableName,
                        failure
                    ),
                    from = cursor.requireForeignKeyText(
                        BOUNDED_FOREIGN_KEY_FROM,
                        MAX_TABLE_NAME_BYTES,
                        tableName,
                        failure
                    ),
                    to = cursor.requireForeignKeyText(
                        BOUNDED_FOREIGN_KEY_TO,
                        MAX_TABLE_NAME_BYTES,
                        tableName,
                        failure
                    ),
                    onUpdate = cursor.requireForeignKeyText(
                        BOUNDED_FOREIGN_KEY_ON_UPDATE,
                        MAX_FOREIGN_KEY_ACTION_BYTES,
                        tableName,
                        failure
                    ),
                    onDelete = cursor.requireForeignKeyText(
                        BOUNDED_FOREIGN_KEY_ON_DELETE,
                        MAX_FOREIGN_KEY_ACTION_BYTES,
                        tableName,
                        failure
                    ),
                    match = cursor.requireForeignKeyText(
                        BOUNDED_FOREIGN_KEY_MATCH,
                        MAX_FOREIGN_KEY_ACTION_BYTES,
                        tableName,
                        failure
                    )
                )
            if (rowsById.size > maximumDefinitions) {
                throw failure(
                    "$tableName foreign keys exceed the safe migration limit"
                )
            }
        }
    }

    val definitions = linkedSetOf<MigrationForeignKeyDefinition>()
    rowsById.values.forEach { rows ->
        val first = rows.first()
        rows.forEachIndexed { expectedSequence, row ->
            if (
                row.sequence != expectedSequence.toLong() ||
                row.parentTable != first.parentTable ||
                row.onUpdate != first.onUpdate ||
                row.onDelete != first.onDelete ||
                row.match != first.match
            ) {
                throw failure(
                    "$tableName has an incompatible foreign-key grouping"
                )
            }
        }
        val definition = try {
            MigrationForeignKeyDefinition(
                parentTable = first.parentTable,
                columns = rows.map { row ->
                    MigrationForeignKeyColumnMapping(
                        from = row.from,
                        to = row.to
                    )
                },
                onUpdate = first.onUpdate,
                onDelete = first.onDelete,
                match = first.match
            )
        } catch (_: IllegalArgumentException) {
            throw failure(
                "$tableName has an unsafe foreign-key definition"
            )
        }
        if (!definitions.add(definition)) {
            throw failure(
                "$tableName has duplicate foreign-key definitions"
            )
        }
    }
    return definitions
}

private fun requireExactParentPrimaryKeys(
    database: SupportSQLiteDatabase,
    ordinaryTables: Set<String>,
    limits: MigrationForeignKeyCheckLimits,
    failure: (String) -> IllegalStateException
) {
    limits.expectedParentPrimaryKeysByTable.forEach {
        (tableName, expectedColumns) ->
        if (tableName !in ordinaryTables) {
            throw failure(
                "Required foreign-key parent table $tableName is missing"
            )
        }
        val actualColumns = readBoundedPrimaryKeyColumns(
            database = database,
            tableName = tableName,
            maximumColumns = expectedColumns.size,
            failure = failure
        )
        if (actualColumns != expectedColumns) {
            throw failure(
                "$tableName has an incompatible foreign-key parent key"
            )
        }
    }
}

private fun readBoundedPrimaryKeyColumns(
    database: SupportSQLiteDatabase,
    tableName: String,
    maximumColumns: Int,
    failure: (String) -> IllegalStateException
): List<String> {
    check(tableName.isSafeMigrationTableName())
    val name = boundedTextProjection(
        column = "name",
        alias = BOUNDED_PRIMARY_KEY_COLUMN,
        maxBytes = MAX_TABLE_NAME_BYTES
    )
    val position = boundedIntegerProjection(
        column = "pk",
        alias = BOUNDED_PRIMARY_KEY_POSITION
    )
    val columns = mutableListOf<String>()
    database.query(
        "SELECT $name, $position FROM pragma_table_info('$tableName') " +
            "WHERE pk > 0 ORDER BY pk ASC LIMIT ${maximumColumns + 1}"
    ).use { cursor ->
        while (cursor.moveToNext()) {
            if (columns.size == maximumColumns) {
                throw failure(
                    "$tableName parent key exceeds the safe migration limit"
                )
            }
            val exactPosition = cursor.readBoundedInteger(
                BOUNDED_PRIMARY_KEY_POSITION
            )
            if (
                !exactPosition.hasExpectedStorageClass ||
                exactPosition.value != columns.size.toLong() + 1L
            ) {
                throw failure(
                    "$tableName has an incompatible parent-key order"
                )
            }
            columns += cursor.requireForeignKeyText(
                BOUNDED_PRIMARY_KEY_COLUMN,
                MAX_TABLE_NAME_BYTES,
                tableName,
                failure
            )
        }
    }
    return columns
}

private fun Cursor.requireNonNegativeForeignKeyInteger(
    columnName: String,
    tableName: String,
    failure: (String) -> IllegalStateException
): Long {
    val index = getColumnIndexOrThrow(columnName)
    if (getType(index) != Cursor.FIELD_TYPE_INTEGER) {
        throw failure(
            "$tableName has a non-integer foreign-key identifier"
        )
    }
    return getLong(index).also { value ->
        if (value < 0L) {
            throw failure(
                "$tableName has a negative foreign-key identifier"
            )
        }
    }
}

private fun Cursor.requireForeignKeyText(
    alias: String,
    maximumBytes: Int,
    tableName: String,
    failure: (String) -> IllegalStateException
): String {
    val bounded = readBoundedText(
        alias = alias,
        maxBytes = maximumBytes
    )
    val value = bounded.value
    if (
        !bounded.isPresent ||
        !bounded.hasExpectedStorageClass ||
        bounded.isOversized ||
        value.isNullOrEmpty()
    ) {
        throw failure(
            "$tableName has an unsafe foreign-key identifier or action"
        )
    }
    return value
}

private fun String.isSafeMigrationTableName(): Boolean {
    return isNotEmpty() && all { it == '_' || it.isLetterOrDigit() }
}

private data class ObservedForeignKeyRow(
    val sequence: Long,
    val parentTable: String,
    val from: String,
    val to: String,
    val onUpdate: String,
    val onDelete: String,
    val match: String
)

@Suppress("LongMethod")
private fun releasedForeignKeyDefinitions(
    assetsOnDelete: String
): Map<String, Set<MigrationForeignKeyDefinition>> {
    return linkedMapOf(
        "assets" to setOf(
            releasedForeignKey(
                parentTable = "chains",
                from = "chainId",
                to = "id",
                onDelete = assetsOnDelete
            )
        ),
        "chain_accounts" to setOf(
            releasedForeignKey(
                parentTable = "chains",
                from = "chainId",
                to = "id",
                onDelete = FOREIGN_KEY_NO_ACTION
            ),
            releasedForeignKey(
                parentTable = "meta_accounts",
                from = "metaId",
                to = "id",
                onDelete = FOREIGN_KEY_CASCADE
            )
        ),
        "chain_assets" to setOf(
            releasedForeignKey(
                parentTable = "chains",
                from = "chainId",
                to = "id",
                onDelete = FOREIGN_KEY_CASCADE
            )
        ),
        "chain_explorers" to setOf(
            releasedForeignKey(
                parentTable = "chains",
                from = "chainId",
                to = "id",
                onDelete = FOREIGN_KEY_CASCADE
            )
        ),
        "chain_nodes" to setOf(
            releasedForeignKey(
                parentTable = "chains",
                from = "chainId",
                to = "id",
                onDelete = FOREIGN_KEY_CASCADE
            )
        ),
        "favorite_chains" to setOf(
            releasedForeignKey(
                parentTable = "chains",
                from = "chainId",
                to = "id",
                onDelete = FOREIGN_KEY_NO_ACTION
            ),
            releasedForeignKey(
                parentTable = "meta_accounts",
                from = "metaId",
                to = "id",
                onDelete = FOREIGN_KEY_CASCADE
            )
        ),
        "nomis_wallet_score" to setOf(
            releasedForeignKey(
                parentTable = "meta_accounts",
                from = "metaId",
                to = "id",
                onDelete = FOREIGN_KEY_CASCADE
            )
        ),
        "ton_connection" to setOf(
            releasedForeignKey(
                parentTable = "meta_accounts",
                from = "metaId",
                to = "id",
                onDelete = FOREIGN_KEY_CASCADE
            )
        ),
        "userpools" to setOf(
            MigrationForeignKeyDefinition(
                parentTable = "allpools",
                columns = listOf(
                    MigrationForeignKeyColumnMapping(
                        from = "userTokenIdBase",
                        to = "tokenIdBase"
                    ),
                    MigrationForeignKeyColumnMapping(
                        from = "userTokenIdTarget",
                        to = "tokenIdTarget"
                    )
                ),
                onDelete = FOREIGN_KEY_CASCADE
            )
        )
    )
}

private fun releasedForeignKey(
    parentTable: String,
    from: String,
    to: String,
    onDelete: String
): MigrationForeignKeyDefinition {
    return MigrationForeignKeyDefinition(
        parentTable = parentTable,
        columns = listOf(
            MigrationForeignKeyColumnMapping(
                from = from,
                to = to
            )
        ),
        onDelete = onDelete
    )
}

private fun releasedForeignKeyParentPrimaryKeys(): Map<String, List<String>> {
    return linkedMapOf(
        "chains" to listOf("id"),
        "meta_accounts" to listOf("id"),
        "allpools" to listOf("tokenIdBase", "tokenIdTarget")
    )
}

internal const val FOREIGN_KEY_NO_ACTION = "NO ACTION"
internal const val FOREIGN_KEY_CASCADE = "CASCADE"
internal const val FOREIGN_KEY_MATCH_NONE = "NONE"

private fun String.isAllowedForeignKeyAction(): Boolean {
    return this == FOREIGN_KEY_NO_ACTION ||
        this == FOREIGN_KEY_CASCADE ||
        this == "RESTRICT" ||
        this == "SET NULL" ||
        this == "SET DEFAULT"
}

private const val MAX_TABLE_NAME_BYTES = 128
private const val MAX_FOREIGN_KEY_ACTION_BYTES = 32
private const val BOUNDED_TABLE_NAME = "boundedForeignKeyTableName"
private const val BOUNDED_FOREIGN_KEY_PARENT =
    "boundedForeignKeyParent"
private const val BOUNDED_FOREIGN_KEY_FROM = "boundedForeignKeyFrom"
private const val BOUNDED_FOREIGN_KEY_TO = "boundedForeignKeyTo"
private const val BOUNDED_FOREIGN_KEY_ON_UPDATE =
    "boundedForeignKeyOnUpdate"
private const val BOUNDED_FOREIGN_KEY_ON_DELETE =
    "boundedForeignKeyOnDelete"
private const val BOUNDED_FOREIGN_KEY_MATCH = "boundedForeignKeyMatch"
private const val BOUNDED_PRIMARY_KEY_COLUMN =
    "boundedForeignKeyPrimaryColumn"
private const val BOUNDED_PRIMARY_KEY_POSITION =
    "boundedForeignKeyPrimaryPosition"
