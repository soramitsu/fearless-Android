package jp.co.soramitsu.coredb.migrations

import androidx.sqlite.db.SupportSQLiteDatabase

internal data class RebuildableTableRepairReport(
    val created: List<String>,
    val rebuilt: List<String>,
    val reindexed: List<String>,
    val skipped: List<String>,
    val danglingCacheRowsDeleted: Map<String, Int>
) {
    val changedSchema: Boolean
        get() = created.isNotEmpty() || rebuilt.isNotEmpty() || reindexed.isNotEmpty()
}

/**
 * Puts the rebuildable tables of a version 76 database into a shape the
 * wallet-secret preflight accepts, before it runs (addendum K10, U0.1, U0.5).
 *
 * Shipped 4.x builds left layouts the release candidate's frozen contract did
 * not know, and chain-registry children whose parent row they had dropped.
 * Neither says anything about wallet keys, yet the preflight stopped every
 * such user at "Wallet recovery required". Only tables in
 * [RELEASED_V76_REBUILDABLE_TABLES] are touched; the identity tables keep
 * their exact checks.
 *
 * - A missing table is created from Room's DDL.
 * - A table whose columns, definition or foreign keys match no released
 *   layout is rebuilt from Room's DDL. Rows are copied by column name; cache
 *   rows that do not fit are dropped, user rows keep their place with a
 *   zero value in a required column the old table did not fill.
 * - A table with other explicit indexes gets exactly Room's indexes.
 * - Registry and pool cache rows whose parent row is gone are deleted.
 *
 * Every statement runs inside Room's upgrade transaction and before the first
 * preference write, so a failure rolls the database back unchanged. A table
 * that already matches is never written. Room runs migrations with
 * foreign-key enforcement off; with enforcement on, dropping a parent table
 * would cascade, so tables are then left to the strict checks instead.
 */
internal object RebuildableTableRepair {

    fun repair(
        database: SupportSQLiteDatabase,
        tables: List<RebuildableTableSpec> = RELEASED_V76_REBUILDABLE_TABLES,
        danglingCacheRows: List<DanglingCacheRows> = RELEASED_V76_DANGLING_CACHE_ROWS,
        foreignKeyCheckLimits: MigrationForeignKeyCheckLimits = WALLET_INTEGRITY_FOREIGN_KEY_CHECK_LIMITS
    ): RebuildableTableRepairReport {
        val enforcingForeignKeys = isEnforcingForeignKeys(database)
        val created = mutableListOf<String>()
        val rebuilt = mutableListOf<String>()
        val reindexed = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        // Tables known to be in a released shape now; only their dangling
        // rows can be deleted with statements that name their columns.
        val canonicalTables = mutableSetOf<String>()

        tables.forEach { table ->
            val name = table.contract.name
            val shape = releasedRoomTableShape(database, table.contract)
            val foreignKeysMatch = shape == ReleasedRoomTableShape.MISSING ||
                shape == ReleasedRoomTableShape.NOT_A_TABLE ||
                hasReleasedForeignKeys(database, name, foreignKeyCheckLimits)
            when {
                shape == ReleasedRoomTableShape.NOT_A_TABLE -> skipped += name

                shape == ReleasedRoomTableShape.MISSING -> {
                    if (create(database, table)) {
                        created += name
                        canonicalTables += name
                    } else {
                        skipped += name
                    }
                }

                shape == ReleasedRoomTableShape.LAYOUT_DIFFERS || !foreignKeysMatch -> {
                    if (!enforcingForeignKeys && rebuild(database, table)) {
                        rebuilt += name
                        canonicalTables += name
                    } else {
                        skipped += name
                    }
                }

                shape == ReleasedRoomTableShape.INDEXES_DIFFER -> {
                    if (reindex(database, table)) {
                        reindexed += name
                        canonicalTables += name
                    } else {
                        skipped += name
                    }
                }

                else -> canonicalTables += name
            }
        }

        val deleted = linkedMapOf<String, Int>()
        danglingCacheRows
            .filter { rows ->
                rows.childTable in canonicalTables &&
                    readColumnNames(database, rows.parentTable)
                        .containsAll(rows.parentColumns)
            }
            .forEach { rows ->
                val count = deleteDanglingRows(database, rows)
                if (count > 0) deleted[rows.childTable] = count
            }
        return RebuildableTableRepairReport(
            created = created,
            rebuilt = rebuilt,
            reindexed = reindexed,
            skipped = skipped,
            danglingCacheRowsDeleted = deleted
        )
    }

    private fun create(
        database: SupportSQLiteDatabase,
        table: RebuildableTableSpec
    ): Boolean {
        val name = table.contract.name
        database.execSQL(table.ddl.createSql(name))
        dropExplicitIndexes(database, name) ?: return false
        table.ddl.indexSql(name).forEach(database::execSQL)
        return true
    }

    private fun rebuild(
        database: SupportSQLiteDatabase,
        table: RebuildableTableSpec
    ): Boolean {
        val name = table.contract.name
        val work = "$REPAIR_TABLE_PREFIX$name"
        if (objectExists(database, work)) return false
        val existing = readColumnNames(database, name)
        val copied = table.contract.columns.mapNotNull { column ->
            copiedValue(column, column.name in existing, table.rows)
                ?.let { value -> column.name to value }
        }

        database.execSQL(table.ddl.createSql(work))
        if (copied.isNotEmpty()) {
            // A user row must survive intact or reject the whole migration transaction.
            val insert = if (table.rows == RebuildableRows.USER) "INSERT" else "INSERT OR IGNORE"
            database.execSQL(
                "$insert INTO `$work` (" +
                    copied.joinToString { (column, _) -> "`$column`" } +
                    ") SELECT " +
                    copied.joinToString { (_, value) -> value } +
                    " FROM `$name`"
            )
        }
        // Dropping the table drops its indexes; renaming the copy keeps
        // every child's REFERENCES clause pointing at the same name.
        database.execSQL("DROP TABLE `$name`")
        database.execSQL("ALTER TABLE `$work` RENAME TO `$name`")
        table.ddl.indexSql(name).forEach(database::execSQL)
        return true
    }

    private fun reindex(
        database: SupportSQLiteDatabase,
        table: RebuildableTableSpec
    ): Boolean {
        val name = table.contract.name
        dropExplicitIndexes(database, name) ?: return false
        table.ddl.indexSql(name).forEach(database::execSQL)
        return true
    }

    /**
     * The value a rebuild copies into [column], or null to leave it to the
     * new table's default. A required column of a user row is never left
     * empty, because SQLite would then drop the whole row.
     */
    private fun copiedValue(
        column: ReleasedRoomColumnSpec,
        existed: Boolean,
        rows: RebuildableRows
    ): String? {
        val fillsRequiredUserValue = rows == RebuildableRows.USER &&
            column.notNull &&
            column.primaryKeyPosition == 0
        return when {
            existed && fillsRequiredUserValue ->
                "COALESCE(`${column.name}`, ${zeroValue(column.type)})"

            existed -> "`${column.name}`"
            fillsRequiredUserValue -> zeroValue(column.type)
            else -> null
        }
    }

    private fun zeroValue(type: String): String = when (type.uppercase()) {
        "INTEGER" -> "0"
        "REAL" -> "0.0"
        "BLOB" -> "X''"
        else -> "''"
    }

    private fun deleteDanglingRows(
        database: SupportSQLiteDatabase,
        rows: DanglingCacheRows
    ): Int {
        val child = rows.childTable
        val parent = rows.parentTable
        // Matches SQLite's rule: a child key with a NULL part is no violation.
        val keyPresent = rows.childColumns.joinToString(" AND ") { column ->
            "`$child`.`$column` IS NOT NULL"
        }
        val parentMatches = rows.childColumns.zip(rows.parentColumns)
            .joinToString(" AND ") { (childColumn, parentColumn) ->
                "`$parent`.`$parentColumn` = `$child`.`$childColumn`"
            }
        return database.compileStatement(
            "DELETE FROM `$child` WHERE $keyPresent AND NOT EXISTS " +
                "(SELECT 1 FROM `$parent` WHERE $parentMatches)"
        ).use { statement -> statement.executeUpdateDelete() }
    }

    private fun hasReleasedForeignKeys(
        database: SupportSQLiteDatabase,
        tableName: String,
        foreignKeyCheckLimits: MigrationForeignKeyCheckLimits
    ): Boolean {
        val expected = foreignKeyCheckLimits
            .expectedForeignKeysByTable[tableName]
            .orEmpty()
        return try {
            readBoundedForeignKeyDefinitions(
                database = database,
                tableName = tableName,
                maximumDefinitions = foreignKeyCheckLimits
                    .maximumForeignKeyDefinitionsPerTable,
                failure = ::IllegalStateException
            ) == expected
        } catch (_: IllegalStateException) {
            false
        }
    }

    /** Returns null when an index name cannot be quoted safely. */
    private fun dropExplicitIndexes(
        database: SupportSQLiteDatabase,
        tableName: String
    ): Unit? {
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
        val explicit = mutableListOf<String>()
        database.query(
            "SELECT $name, $origin FROM pragma_index_list('$tableName') " +
                "LIMIT ${MAX_INDEXES + 1}"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                if (explicit.size == MAX_INDEXES) return null
                val indexOrigin = cursor.readBoundedText(BOUNDED_INDEX_ORIGIN, MAX_INDEX_ORIGIN_BYTES)
                if (indexOrigin.value != EXPLICIT_INDEX_ORIGIN) continue
                val indexName = cursor.readBoundedText(BOUNDED_INDEX_NAME, MAX_IDENTIFIER_BYTES)
                    .value
                    ?.takeIf { it.isSafeIdentifier() }
                    ?: return null
                explicit += indexName
            }
        }
        explicit.forEach { database.execSQL("DROP INDEX `$it`") }
        return Unit
    }

    private fun readColumnNames(
        database: SupportSQLiteDatabase,
        tableName: String
    ): Set<String> {
        val name = boundedTextProjection(
            column = "name",
            alias = BOUNDED_COLUMN_NAME,
            maxBytes = MAX_IDENTIFIER_BYTES
        )
        val names = linkedSetOf<String>()
        database.query(
            "SELECT $name FROM pragma_table_info('$tableName') " +
                "LIMIT ${MAX_COLUMNS + 1}"
        ).use { cursor ->
            while (cursor.moveToNext() && names.size < MAX_COLUMNS) {
                cursor.readBoundedText(BOUNDED_COLUMN_NAME, MAX_IDENTIFIER_BYTES)
                    .value
                    ?.let(names::add)
            }
        }
        return names
    }

    private fun objectExists(
        database: SupportSQLiteDatabase,
        name: String
    ): Boolean {
        return database.query(
            "SELECT 1 FROM sqlite_master WHERE name = ? COLLATE NOCASE LIMIT 1",
            arrayOf<Any?>(name)
        ).use { cursor -> cursor.moveToFirst() }
    }

    private fun isEnforcingForeignKeys(database: SupportSQLiteDatabase): Boolean {
        return database.query("PRAGMA foreign_keys").use { cursor ->
            cursor.moveToFirst() && cursor.getLong(0) != 0L
        }
    }

    private fun String.isSafeIdentifier(): Boolean =
        isNotEmpty() && all { it == '_' || it.isLetterOrDigit() }

    private const val REPAIR_TABLE_PREFIX = "_repair_"
    private const val EXPLICIT_INDEX_ORIGIN = "c"
    private const val MAX_COLUMNS = 64
    private const val MAX_INDEXES = 16
    private const val MAX_IDENTIFIER_BYTES = 256
    private const val MAX_INDEX_ORIGIN_BYTES = 16
    private const val BOUNDED_INDEX_NAME = "repairBoundedIndexName"
    private const val BOUNDED_INDEX_ORIGIN = "repairBoundedIndexOrigin"
    private const val BOUNDED_COLUMN_NAME = "repairBoundedColumnName"
}
