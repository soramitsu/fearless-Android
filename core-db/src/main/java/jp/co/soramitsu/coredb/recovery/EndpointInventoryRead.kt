package jp.co.soramitsu.coredb.recovery

import android.database.Cursor
import android.os.CancellationSignal
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import jp.co.soramitsu.coredb.migrations.EndpointArchiveFormat
import jp.co.soramitsu.coredb.migrations.UpgradeEntryEndpointArchive

internal data class EndpointSourceSpec(
    val table: String,
    val rowId: String,
    val fields: Map<EndpointField, String>,
    val archive: EndpointArchiveFormat? = null,
    val header: String? = null
)

private val MODERN_FIELDS = linkedMapOf(EndpointField.NAME to "name", EndpointField.ENDPOINT to "url",
    EndpointField.CHAIN_REFERENCE to "chainId", EndpointField.IS_DEFAULT to "isDefault", EndpointField.IS_ACTIVE to "isActive")
private val SOURCE_SPECS = mapOf(
    EndpointInventorySource.LEGACY to EndpointSourceSpec("main.nodes", "rowid", linkedMapOf(
        EndpointField.NAME to "name", EndpointField.ENDPOINT to "link", EndpointField.NETWORK_TYPE to "networkType",
        EndpointField.IS_DEFAULT to "isDefault", EndpointField.IS_ACTIVE to "isActive")),
    EndpointInventorySource.HISTORICAL to EndpointSourceSpec("main.endpoint_archive_v1", "source_rowid", MODERN_FIELDS,
        EndpointArchiveFormat.HISTORICAL, "main.endpoint_archive_header_v1"),
    EndpointInventorySource.UPGRADE_ENTRY to EndpointSourceSpec("main.upgrade_entry_endpoint_archive_v1", "source_rowid", MODERN_FIELDS,
        EndpointArchiveFormat.UPGRADE_ENTRY, "main.upgrade_entry_endpoint_archive_header_v1"),
    EndpointInventorySource.CURRENT to EndpointSourceSpec("main.current_endpoint_archive_v1", "source_rowid", MODERN_FIELDS,
        EndpointArchiveFormat.CURRENT, "main.current_endpoint_archive_header_v1"),
    EndpointInventorySource.OWNED to EndpointSourceSpec("main.custom_endpoint_records", "id", linkedMapOf(
        EndpointField.NAME to "name", EndpointField.ENDPOINT to "url", EndpointField.CHAIN_REFERENCE to "chainId"))
)
internal fun endpointSourceSpec(source: EndpointInventorySource): EndpointSourceSpec = SOURCE_SPECS.getValue(source)

internal data class EndpointReadSession(
    val database: SupportSQLiteDatabase,
    val spec: EndpointSourceSpec,
    val provenance: EndpointSourceProvenance?
)

/** No transaction, version change or connection ownership is introduced by admission. */
internal fun admitEndpointSource(
    database: SupportSQLiteDatabase,
    signal: CancellationSignal,
    source: EndpointInventorySource
): EndpointReadSession? {
    signal.throwIfCanceled()
    val spec = endpointSourceSpec(source)
    if (source == EndpointInventorySource.LEGACY) {
        return admittedLegacyEndpointArchive(database, signal)?.let { EndpointReadSession(it, spec, null) }
    }
    val cancellable = object : SupportSQLiteDatabase by database {
        override fun query(query: String): Cursor = this.query(SimpleSQLiteQuery(query))
        override fun query(query: String, bindArgs: Array<out Any?>): Cursor = this.query(SimpleSQLiteQuery(query, bindArgs))
        override fun query(query: SupportSQLiteQuery): Cursor {
            signal.throwIfCanceled()
            return database.query(query, signal)
        }
    }
    cancellable.query("PRAGMA encoding").use {
        check(it.columnCount == 1 && it.moveToFirst() && it.getType(0) == Cursor.FIELD_TYPE_STRING &&
            it.getString(0) == "UTF-8" && !it.moveToNext()) { "Endpoint inventory requires UTF-8 database encoding" }
    }
    val present = if (source == EndpointInventorySource.OWNED) requireOwnedEndpointSchema(cancellable)
        else checkNotNull(spec.archive).requireSafe(cancellable)
    signal.throwIfCanceled()
    if (!present) return null
    val provenance = spec.header?.let { header ->
        cancellable.query("SELECT source_edge_version,source_user_version FROM $header WHERE id=1 LIMIT 2").use { cursor ->
            check(cursor.moveToFirst() && cursor.columnCount == 2) { "Endpoint provenance is missing" }
            val edge = cursor.endpointInteger(0)
            val original = cursor.endpointInteger(1)
            check(edge in 0..Int.MAX_VALUE.toLong() && original in 0..Int.MAX_VALUE.toLong() && !cursor.moveToNext()) {
                "Endpoint provenance is invalid"
            }
            if (source == EndpointInventorySource.UPGRADE_ENTRY) {
                check(UpgradeEntryEndpointArchive.admitsStoredCapture(original.toInt(), edge.toInt())) {
                    "Upgrade-entry endpoint stored source provenance differs"
                }
            }
            EndpointSourceProvenance(edge.toInt(), original.toInt())
        }
    }
    signal.throwIfCanceled()
    return EndpointReadSession(cancellable, spec, provenance)
}

internal const val OWNED_ENDPOINT_CREATE = "CREATE TABLE `custom_endpoint_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
    "`revision` INTEGER NOT NULL, `chainId` TEXT NOT NULL, `url` TEXT NOT NULL, `name` TEXT NOT NULL, " +
    "`attached` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, `originalSnapshotRowId` INTEGER)"
internal const val OWNED_ENDPOINT_INDEX_CREATE = "CREATE UNIQUE INDEX `index_custom_endpoint_records_chainId_url` ON `custom_endpoint_records` (`chainId`, `url`)"
private const val OWNED_NAMESPACE = "name='custom_endpoint_records' COLLATE NOCASE OR tbl_name='custom_endpoint_records' COLLATE NOCASE OR " +
    "name='index_custom_endpoint_records_chainId_url' COLLATE NOCASE OR tbl_name='index_custom_endpoint_records_chainId_url' COLLATE NOCASE"

private fun boundedSchemaText(column: String, cap: Int) =
    "CASE WHEN typeof($column)='text' AND length(CAST($column AS BLOB))<=$cap THEN CAST($column AS BLOB) ELSE NULL END"

/** Exact production namespace, including the explicit unique index and absence of a parent FK. */
internal fun requireOwnedEndpointSchema(database: SupportSQLiteDatabase): Boolean {
    database.query("SELECT 1 FROM temp.sqlite_master WHERE $OWNED_NAMESPACE LIMIT 1").use {
        check(!it.moveToFirst()) { "Owned endpoint namespace has a temporary collision" }
    }
    val found = linkedMapOf<String, String>()
    database.query("SELECT ${boundedSchemaText("type", 256)},${boundedSchemaText("name", 256)}," +
        "${boundedSchemaText("tbl_name", 256)},${boundedSchemaText("sql", 4096)} " +
        "FROM main.sqlite_master WHERE $OWNED_NAMESPACE LIMIT 3").use { cursor ->
        while (cursor.moveToNext()) {
            check(found.size < 2 && cursor.columnCount == 4) { "Owned endpoint namespace has extra objects" }
            val type = cursor.endpointSchemaText(0, 256)
            val name = cursor.endpointSchemaText(1, 256)
            val owner = cursor.endpointSchemaText(2, 256)
            val sql = cursor.endpointSchemaText(3, 4096)
            check(owner == "custom_endpoint_records" && found.put(name, sql) == null) { "Owned endpoint namespace is ambiguous" }
            check((name == "custom_endpoint_records" && type == "table" && sql == OWNED_ENDPOINT_CREATE) ||
                (name == "index_custom_endpoint_records_chainId_url" && type == "index" && sql == OWNED_ENDPOINT_INDEX_CREATE)) {
                "Owned endpoint schema differs from the supported definition"
            }
        }
    }
    if (found.isEmpty()) return false
    check(found.keys == setOf("custom_endpoint_records", "index_custom_endpoint_records_chainId_url")) {
        "Owned endpoint namespace is incomplete"
    }
    return true
}

private fun Cursor.endpointSchemaText(index: Int, cap: Int): String {
    check(getType(index) == Cursor.FIELD_TYPE_BLOB) { "Endpoint schema metadata is unreadable" }
    val bytes = getBlob(index)
    check(bytes.size <= cap) { "Endpoint schema metadata exceeds its bound" }
    return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
}

internal fun Cursor.endpointInteger(index: Int): Long {
    check(getType(index) == Cursor.FIELD_TYPE_INTEGER) { "Endpoint integer metadata is unreadable" }
    return getLong(index)
}

internal fun Cursor.endpointStorage(prefix: String): EndpointStorage {
    val index = getColumnIndexOrThrow("${prefix}_type")
    check(getType(index) == Cursor.FIELD_TYPE_STRING) { "Endpoint storage metadata is unreadable" }
    return when (getString(index)) {
        "null" -> EndpointStorage.NULL
        "integer" -> EndpointStorage.INTEGER
        "real" -> EndpointStorage.REAL
        "text" -> EndpointStorage.TEXT
        "blob" -> EndpointStorage.BLOB
        else -> error("Endpoint storage class is unsupported")
    }
}

internal val OWNED_ENDPOINT_PROJECTION = listOf(
    "CASE WHEN typeof(revision)='integer' THEN revision END AS owned_revision",
    "CASE WHEN typeof(attached)='integer' THEN attached END AS owned_attached",
    "CASE WHEN typeof(deleted)='integer' THEN deleted END AS owned_deleted",
    "typeof(originalSnapshotRowId) AS owned_origin_type",
    "CASE WHEN typeof(originalSnapshotRowId)='integer' THEN originalSnapshotRowId END AS owned_origin"
).joinToString()

internal fun Cursor.endpointOwnership(): Pair<Long, EndpointOwnership> {
    val revision = endpointInteger(getColumnIndexOrThrow("owned_revision"))
    val attached = endpointInteger(getColumnIndexOrThrow("owned_attached"))
    val deleted = endpointInteger(getColumnIndexOrThrow("owned_deleted"))
    check(revision > 0 && attached in 0L..1L && deleted in 0L..1L && attached + deleted <= 1) {
        "Owned endpoint revision or status is invalid"
    }
    val typeIndex = getColumnIndexOrThrow("owned_origin_type")
    check(getType(typeIndex) == Cursor.FIELD_TYPE_STRING) { "Owned endpoint origin storage is unreadable" }
    val originIndex = getColumnIndexOrThrow("owned_origin")
    val origin = when (getString(typeIndex)) {
        "null" -> { check(isNull(originIndex)); null }
        "integer" -> endpointInteger(originIndex)
        else -> error("Owned endpoint origin is invalid")
    }
    return revision to EndpointOwnership(attached == 1L, deleted == 1L, origin)
}

internal fun endpointPageProjection(column: String, prefix: String): String = listOf(
    "typeof($column) AS ${prefix}_type",
    "CASE WHEN typeof($column) IN ('text','blob') THEN length(CAST($column AS BLOB)) END AS ${prefix}_bytes",
    "CASE WHEN typeof($column)='text' AND length(CAST($column AS BLOB))<=2048 THEN CAST($column AS BLOB) END AS ${prefix}_text",
    "CASE WHEN typeof($column)='integer' THEN $column END AS ${prefix}_integer",
    "CASE WHEN typeof($column)='real' THEN $column END AS ${prefix}_real"
).joinToString()

internal fun Cursor.endpointPageValue(prefix: String): EndpointValue {
    val storage = endpointStorage(prefix)
    val lengthIndex = getColumnIndexOrThrow("${prefix}_bytes")
    val textIndex = getColumnIndexOrThrow("${prefix}_text")
    val integerIndex = getColumnIndexOrThrow("${prefix}_integer")
    val realIndex = getColumnIndexOrThrow("${prefix}_real")
    check((storage == EndpointStorage.INTEGER) == !isNull(integerIndex) &&
        (storage == EndpointStorage.REAL) == !isNull(realIndex)) { "Endpoint scalar projection differs" }
    val length = if (storage == EndpointStorage.TEXT || storage == EndpointStorage.BLOB)
        endpointInteger(lengthIndex).also { check(it >= 0) } else { check(isNull(lengthIndex)); null }
    if (storage != EndpointStorage.TEXT) check(isNull(textIndex)) { "Endpoint page projected unsupported payload" }
    return when (storage) {
        EndpointStorage.NULL -> EndpointValue.Null
        EndpointStorage.INTEGER -> EndpointValue.Integer(endpointInteger(integerIndex))
        EndpointStorage.REAL -> {
            check(getType(realIndex) == Cursor.FIELD_TYPE_FLOAT) { "Endpoint real projection differs" }
            EndpointValue.Real(getDouble(realIndex))
        }
        EndpointStorage.BLOB -> EndpointValue.Blob(checkNotNull(length))
        EndpointStorage.TEXT -> {
            val bytes = if (isNull(textIndex)) null else {
                check(getType(textIndex) == Cursor.FIELD_TYPE_BLOB) { "Endpoint text projection differs" }
                getBlob(textIndex).also { check(it.size <= EndpointInventoryReader.MAXIMUM_TEXT_BYTES) }
            }
            val decoded = decodeLegacyEndpointText(LegacyEndpointStorage.TEXT, length, bytes, EndpointInventoryReader.MAXIMUM_TEXT_BYTES)
            EndpointValue.Text(checkNotNull(length), when (decoded.state) {
                LegacyEndpointTextState.AVAILABLE -> EndpointTextState.AVAILABLE
                LegacyEndpointTextState.OVERSIZED -> EndpointTextState.OVERSIZED
                LegacyEndpointTextState.INVALID_UTF8 -> EndpointTextState.INVALID_UTF8
                else -> error("Endpoint TEXT decoder returned an invalid state")
            }, decoded.value)
        }
    }
}
