package jp.co.soramitsu.coredb.migrations

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.Locale

/** Preserves public legacy identities without copying or interpreting their payloads. */
internal object LegacyUsersRecoveryMigration {
    enum class Source { ABSENT, CANONICAL, RAW_ARCHIVE }

    /** Read-only admission before any later migration can replace encrypted preferences. */
    fun requireSafeSourceAndTarget(database: SupportSQLiteDatabase): Source {
        requireNoTemporaryShadow(database)
        requireTargetAbsent(database)
        if (!requireSafeRawArchive(database)) return Source.ABSENT
        return if (releasedRoomTableShape(database, SOURCE_SCHEMA) == ReleasedRoomTableShape.MATCHES) {
            Source.CANONICAL
        } else {
            Source.RAW_ARCHIVE
        }
    }

    /**
     * This named archive is not an active wallet table or rebuildable cache. Only its
     * bounded metadata is read; unknown columns, nullability and stored values stay intact.
     */
    fun requireSafeRawArchive(database: SupportSQLiteDatabase): Boolean {
        requireNoTemporaryShadow(database)
        val objects = readSourceObjects(database)
        if (objects.isEmpty()) return false
        check(objects.size == 2) { "Legacy users has unsupported schema objects" }
        val table = objects.singleOrNull { it.type == "table" && it.name == SOURCE }
        check(table != null && table.owner == SOURCE) { "Legacy users is not an exact named ordinary table" }
        check(table.sql != null && table.sql.trimStart().uppercase(Locale.US).startsWith("CREATE TABLE ")) {
            "Legacy users is not an ordinary table with bounded schema SQL"
        }
        val index = objects.singleOrNull { it.type == "index" && it.name == SOURCE_INDEX }
        check(index != null && index.owner == SOURCE && index.sql == null) {
            "Legacy users has an unsupported primary-key owner"
        }
        database.query("PRAGMA main.foreign_key_list('$SOURCE')").use { cursor ->
            check(!cursor.moveToFirst()) { "Legacy users declares unsupported foreign keys" }
        }
        requireOwnedPrimaryKey(database)
        return true
    }

    /** Room owns the normal transaction; a direct caller receives the same atomic DDL. */
    fun migrate(database: SupportSQLiteDatabase) {
        val ownsTransaction = !database.inTransaction()
        if (ownsTransaction) {
            requireSafeSourceAndTarget(database)
            database.beginTransaction()
        }
        var primaryFailure: Throwable? = null
        try {
            // Recheck after acquiring a direct-call transaction, before the first DDL.
            when (requireSafeSourceAndTarget(database)) {
                Source.CANONICAL -> {
                    database.execSQL("ALTER TABLE main.`$SOURCE` RENAME TO `$TARGET`")
                    database.execSQL(
                        "ALTER TABLE main.`$TARGET` ADD COLUMN `recoveryState` TEXT NOT NULL " +
                            "DEFAULT '$PRESERVED_STATE'"
                    )
                }
                Source.ABSENT, Source.RAW_ARCHIVE -> database.execSQL(
                    """
                    CREATE TABLE main.`$TARGET` (
                        `address` TEXT NOT NULL,
                        `username` TEXT NOT NULL,
                        `publicKey` TEXT NOT NULL,
                        `cryptoType` INTEGER NOT NULL,
                        `position` INTEGER NOT NULL,
                        `recoveryState` TEXT NOT NULL DEFAULT '$PRESERVED_STATE',
                        PRIMARY KEY(`address`)
                    )
                    """.trimIndent()
                )
            }
            if (ownsTransaction) database.setTransactionSuccessful()
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            if (ownsTransaction) {
                try {
                    database.endTransaction()
                } catch (cleanupFailure: Throwable) {
                    val original = primaryFailure
                    if (original == null) throw cleanupFailure
                    if (cleanupFailure !== original) original.addSuppressed(cleanupFailure)
                }
            }
        }
    }

    private fun requireNoTemporaryShadow(database: SupportSQLiteDatabase) {
        database.query(
            "SELECT 1 FROM sqlite_temp_master WHERE " +
                "name COLLATE NOCASE IN ('$SOURCE', '$SOURCE_INDEX', '$TARGET', '$TARGET_INDEX') " +
                "OR tbl_name COLLATE NOCASE IN ('$SOURCE', '$TARGET') LIMIT 1"
        ).use { cursor ->
            check(!cursor.moveToFirst()) { "Legacy users recovery has a temporary namespace collision" }
        }
    }

    private fun requireTargetAbsent(database: SupportSQLiteDatabase) {
        database.query(
            "SELECT 1 FROM main.sqlite_master WHERE " +
                "name COLLATE NOCASE IN ('$TARGET', '$TARGET_INDEX') " +
                "OR tbl_name = '$TARGET' COLLATE NOCASE LIMIT 1"
        ).use { cursor ->
            check(!cursor.moveToFirst()) { "Legacy users recovery target already exists" }
        }
    }

    private data class SchemaObject(val type: String, val name: String, val owner: String, val sql: String?)

    private fun readSourceObjects(database: SupportSQLiteDatabase): List<SchemaObject> {
        val projections = listOf("type", "name", "tbl_name").joinToString { column ->
            "CASE WHEN typeof($column) = 'text' AND length(CAST($column AS BLOB)) <= $MAX_IDENTIFIER_BYTES " +
                "THEN $column ELSE NULL END AS `$column`"
        }
        return database.query(
            "SELECT $projections, " +
                "CASE WHEN typeof(sql) = 'text' AND length(CAST(sql AS BLOB)) <= $MAX_SCHEMA_BYTES " +
                "THEN sql ELSE NULL END AS sql, typeof(sql) AS sql_type " +
                "FROM main.sqlite_master WHERE " +
                "name COLLATE NOCASE IN ('$SOURCE', '$SOURCE_INDEX') " +
                "OR tbl_name = '$SOURCE' COLLATE NOCASE ORDER BY rowid LIMIT 3"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    check(size < 2) { "Legacy users has extra schema objects" }
                    val type = cursor.requireText("type")
                    val name = cursor.requireText("name")
                    val owner = cursor.requireText("tbl_name")
                    val definitionType = cursor.requireText("sql_type")
                    val definition = if (definitionType == "null") {
                        null
                    } else {
                        check(definitionType == "text") { "Legacy users has invalid schema SQL storage" }
                        cursor.requireText("sql")
                    }
                    add(SchemaObject(type, name, owner, definition))
                }
            }
        }
    }

    private fun requireOwnedPrimaryKey(database: SupportSQLiteDatabase) {
        database.query("PRAGMA main.index_list('$SOURCE')").use { cursor ->
            check(cursor.moveToFirst()) { "Legacy users primary-key index is missing" }
            check(
                cursor.requireText("name") == SOURCE_INDEX &&
                    cursor.requireText("origin") == "pk" &&
                    cursor.requireInteger("unique") == 1L &&
                    cursor.requireInteger("partial") == 0L
            ) { "Legacy users has incompatible primary-key index metadata" }
            check(!cursor.moveToNext()) { "Legacy users has extra indexes" }
        }
    }

    private fun Cursor.requireText(name: String): String {
        val index = getColumnIndexOrThrow(name)
        check(getType(index) == Cursor.FIELD_TYPE_STRING) { "Legacy users has unsafe $name metadata" }
        return getString(index)
    }

    private fun Cursor.requireInteger(name: String): Long {
        val index = getColumnIndexOrThrow(name)
        check(getType(index) == Cursor.FIELD_TYPE_INTEGER) { "Legacy users has unsafe $name metadata" }
        return getLong(index)
    }

    private const val SOURCE = "users"
    private const val SOURCE_INDEX = "sqlite_autoindex_users_1"
    private const val TARGET = "legacy_users_recovery"
    private const val TARGET_INDEX = "sqlite_autoindex_legacy_users_recovery_1"
    private const val PRESERVED_STATE = "PRESERVED_PENDING_REVIEW"
    private const val MAX_IDENTIFIER_BYTES = 256
    private const val MAX_SCHEMA_BYTES = 262_144
    private val SOURCE_SCHEMA = ReleasedRoomTableSpec(
        name = SOURCE,
        columns = listOf(
            ReleasedRoomColumnSpec("address", "TEXT", notNull = true, primaryKeyPosition = 1),
            ReleasedRoomColumnSpec("username", "TEXT", notNull = true),
            ReleasedRoomColumnSpec("publicKey", "TEXT", notNull = true),
            ReleasedRoomColumnSpec("cryptoType", "INTEGER", notNull = true),
            ReleasedRoomColumnSpec("position", "INTEGER", notNull = true)
        )
    )
}
