package jp.co.soramitsu.coredb.recovery

import android.database.Cursor
import android.os.CancellationSignal
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import jp.co.soramitsu.coredb.migrations.LegacyNodeArchive

/** Shared read-only, cancellable admission; callers retain ownership of the connection. */
internal fun admittedLegacyEndpointArchive(
    database: SupportSQLiteDatabase,
    cancellationSignal: CancellationSignal
): SupportSQLiteDatabase? {
    cancellationSignal.throwIfCanceled()
    val cancellable = object : SupportSQLiteDatabase by database {
        override fun query(query: String): Cursor = this.query(SimpleSQLiteQuery(query))
        override fun query(query: String, bindArgs: Array<out Any?>): Cursor =
            this.query(SimpleSQLiteQuery(query, bindArgs))
        override fun query(query: SupportSQLiteQuery): Cursor {
            cancellationSignal.throwIfCanceled()
            return database.query(query, cancellationSignal)
        }
    }
    cancellable.query("PRAGMA encoding").use { cursor ->
        check(cursor.columnCount == 1 && cursor.moveToFirst() && cursor.getType(0) == Cursor.FIELD_TYPE_STRING &&
            cursor.getString(0) == "UTF-8" && !cursor.moveToNext()) {
            "Legacy endpoint inventory requires UTF-8 database encoding"
        }
    }
    val present = LegacyNodeArchive.requireSafe(cancellable)
    cancellationSignal.throwIfCanceled()
    return if (present) cancellable else null
}

internal fun Cursor.legacyEndpointStorage(column: String): LegacyEndpointStorage {
    val index = getColumnIndexOrThrow("${column}_type")
    check(getType(index) == Cursor.FIELD_TYPE_STRING) { "Archive storage metadata is unreadable" }
    return when (getString(index)) {
        "null" -> LegacyEndpointStorage.NULL
        "integer" -> LegacyEndpointStorage.INTEGER
        "real" -> LegacyEndpointStorage.REAL
        "text" -> LegacyEndpointStorage.TEXT
        "blob" -> LegacyEndpointStorage.BLOB
        else -> error("Archive storage metadata is unknown")
    }
}

