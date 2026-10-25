package jp.co.soramitsu.coredb.migrations

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * DB30 introduced this index and both the DB31 Ethereum correction and DB34
 * asset-order migration depend on it to avoid one full asset-table scan per
 * wallet. Validate the released shape before either migration performs work
 * that cannot be cheaply bounded.
 */
internal fun requireReleasedAssetMetaIdIndex(
    database: SupportSQLiteDatabase,
    failure: (String) -> IllegalStateException
) {
    database.query(
        "SELECT 1 FROM pragma_index_list('assets') " +
            "LIMIT ${MAX_ASSET_INDEXES + 1}"
    ).use { cursor ->
        var indexCount = 0
        while (cursor.moveToNext()) {
            if (indexCount == MAX_ASSET_INDEXES) {
                throw failure(
                    "Assets indexes exceed the safe migration limit"
                )
            }
            indexCount += 1
        }
    }

    val indexName = boundedTextProjection(
        column = "name",
        alias = BOUNDED_ASSET_INDEX_NAME,
        maxBytes = MAX_SCHEMA_IDENTIFIER_BYTES
    )
    val indexOrigin = boundedTextProjection(
        column = "origin",
        alias = BOUNDED_ASSET_INDEX_ORIGIN,
        maxBytes = MAX_INDEX_ORIGIN_BYTES
    )
    val unique = boundedIntegerProjection(
        column = "`unique`",
        alias = BOUNDED_ASSET_INDEX_UNIQUE
    )
    val partial = boundedIntegerProjection(
        column = "partial",
        alias = BOUNDED_ASSET_INDEX_PARTIAL
    )
    database.query(
        "SELECT $indexName, $indexOrigin, $unique, $partial " +
            "FROM pragma_index_list('assets') WHERE name = ? " +
            "ORDER BY seq ASC LIMIT 2",
        arrayOf<Any?>(RELEASED_ASSET_META_ID_INDEX)
    ).use { cursor ->
        if (!cursor.moveToFirst()) {
            throw failure(
                "Assets table is missing its released wallet-id index"
            )
        }
        val exactName = cursor.requireReleasedIndexText(
            BOUNDED_ASSET_INDEX_NAME,
            MAX_SCHEMA_IDENTIFIER_BYTES,
            "asset wallet-id index name",
            failure
        )
        val exactOrigin = cursor.requireReleasedIndexText(
            BOUNDED_ASSET_INDEX_ORIGIN,
            MAX_INDEX_ORIGIN_BYTES,
            "asset wallet-id index origin",
            failure
        )
        val exactUnique = cursor.requireReleasedIndexInteger(
            BOUNDED_ASSET_INDEX_UNIQUE,
            "asset wallet-id index uniqueness",
            failure
        )
        val exactPartial = cursor.requireReleasedIndexInteger(
            BOUNDED_ASSET_INDEX_PARTIAL,
            "asset wallet-id index partial flag",
            failure
        )
        if (
            exactName != RELEASED_ASSET_META_ID_INDEX ||
            exactOrigin != EXPLICIT_INDEX_ORIGIN ||
            exactUnique != 0L ||
            exactPartial != 0L ||
            cursor.moveToNext()
        ) {
            throw failure(
                "Assets table has an incompatible wallet-id index"
            )
        }
    }

    val columnName = boundedTextProjection(
        column = "name",
        alias = BOUNDED_ASSET_INDEX_COLUMN,
        maxBytes = MAX_SCHEMA_IDENTIFIER_BYTES
    )
    database.query(
        "SELECT seqno, $columnName " +
            "FROM pragma_index_info('$RELEASED_ASSET_META_ID_INDEX') " +
            "ORDER BY seqno ASC LIMIT 2"
    ).use { cursor ->
        if (!cursor.moveToFirst()) {
            throw failure(
                "Asset wallet-id index has no readable column"
            )
        }
        val sequenceIndex = cursor.getColumnIndexOrThrow("seqno")
        if (
            cursor.getType(sequenceIndex) != Cursor.FIELD_TYPE_INTEGER ||
            cursor.getLong(sequenceIndex) != 0L ||
            cursor.requireReleasedIndexText(
                BOUNDED_ASSET_INDEX_COLUMN,
                MAX_SCHEMA_IDENTIFIER_BYTES,
                "asset wallet-id index column",
                failure
            ) != RELEASED_ASSET_META_ID_COLUMN ||
            cursor.moveToNext()
        ) {
            throw failure(
                "Asset wallet-id index does not have its released shape"
            )
        }
    }
}

private fun Cursor.requireReleasedIndexText(
    alias: String,
    maximumBytes: Int,
    context: String,
    failure: (String) -> IllegalStateException
): String {
    val bounded = readBoundedText(alias, maximumBytes)
    val value = bounded.value
    if (
        !bounded.isPresent ||
        !bounded.hasExpectedStorageClass ||
        bounded.isOversized ||
        value.isNullOrEmpty()
    ) {
        throw failure("$context is not safe bounded SQLite text")
    }
    return value
}

private fun Cursor.requireReleasedIndexInteger(
    alias: String,
    context: String,
    failure: (String) -> IllegalStateException
): Long {
    val bounded = readBoundedInteger(alias)
    val value = bounded.value
    if (
        !bounded.hasExpectedStorageClass ||
        value == null ||
        value !in 0L..1L
    ) {
        throw failure("$context is not a safe SQLite boolean")
    }
    return value
}

internal const val RELEASED_ASSET_META_ID_INDEX = "index_assets_metaId"
private const val RELEASED_ASSET_META_ID_COLUMN = "metaId"
private const val EXPLICIT_INDEX_ORIGIN = "c"
private const val MAX_ASSET_INDEXES = 16
private const val MAX_SCHEMA_IDENTIFIER_BYTES = 128
private const val MAX_INDEX_ORIGIN_BYTES = 8
private const val BOUNDED_ASSET_INDEX_NAME = "boundedAssetIndexName"
private const val BOUNDED_ASSET_INDEX_ORIGIN = "boundedAssetIndexOrigin"
private const val BOUNDED_ASSET_INDEX_UNIQUE = "boundedAssetIndexUnique"
private const val BOUNDED_ASSET_INDEX_PARTIAL = "boundedAssetIndexPartial"
private const val BOUNDED_ASSET_INDEX_COLUMN = "boundedAssetIndexColumn"
