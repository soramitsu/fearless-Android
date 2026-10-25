package jp.co.soramitsu.coredb.recovery.decision

import android.database.Cursor
import android.os.CancellationSignal
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import jp.co.soramitsu.coredb.migrations.EndpointRecoveryDecisionMigration

/** Fixed internal column expressions only; callers never supply SQL identifiers. */
internal fun decisionTextProjection(column: String, maximum: Int): String =
    "CASE WHEN typeof($column)='text' AND length(CAST($column AS BLOB))<=$maximum " +
        "THEN CAST($column AS BLOB) END"

internal fun Cursor.decisionText(index: Int, maximum: Int): String {
    check(getType(index) == Cursor.FIELD_TYPE_BLOB) { "Endpoint decision text storage or size is invalid" }
    val bytes = getBlob(index)
    check(bytes.size <= maximum) { "Endpoint decision text exceeds its bound" }
    return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
}

/** Reject triggers, extra indices, views and temporary shadows before any decision DML. */
internal fun requireDecisionSchema(db: SupportSQLiteDatabase, signal: CancellationSignal) {
    signal.throwIfCanceled()
    val namespace = EndpointRecoveryDecisionMigration.NAMESPACE
    db.query(SimpleSQLiteQuery("SELECT 1 FROM temp.sqlite_master WHERE $namespace LIMIT 1"), signal).use {
        check(!it.moveToFirst()) { "Endpoint decision namespace has a temporary collision" }
    }
    val expected = mapOf(
        "endpoint_recovery_decisions" to ("table" to EndpointRecoveryDecisionMigration.CREATE),
        "index_endpoint_recovery_decisions_sourceKind_sourceRowId" to
            ("index" to EndpointRecoveryDecisionMigration.CREATE_INDEX)
    )
    val found = mutableSetOf<String>()
    db.query(SimpleSQLiteQuery(
        "SELECT ${decisionTextProjection("type", 64)},${decisionTextProjection("name", 128)}," +
            "${decisionTextProjection("tbl_name", 128)},${decisionTextProjection("sql", 4096)} " +
            "FROM main.sqlite_master WHERE $namespace LIMIT 3"
    ), signal).use { cursor ->
        while (cursor.moveToNext()) {
            signal.throwIfCanceled()
            check(cursor.columnCount == 4 && found.size < 2) { "Endpoint decision namespace has extra objects" }
            val type = cursor.decisionText(0, 64)
            val name = cursor.decisionText(1, 128)
            val table = cursor.decisionText(2, 128)
            val sql = cursor.decisionText(3, 4096)
            check(found.add(name) && table == "endpoint_recovery_decisions" && expected[name] == (type to sql)) {
                "Endpoint decision schema differs"
            }
        }
    }
    check(found == expected.keys) { "Endpoint decision schema is incomplete" }
    signal.throwIfCanceled()
}
