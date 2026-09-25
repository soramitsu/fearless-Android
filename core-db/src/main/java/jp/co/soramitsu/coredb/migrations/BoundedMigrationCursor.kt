package jp.co.soramitsu.coredb.migrations

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.model.MetaAccountLocal

internal data class WalletMigrationRowLimits(
    val maxWalletRows: Int,
    val maxChainAccountRows: Int
) {
    init {
        require(maxWalletRows in 1 until Int.MAX_VALUE)
        require(maxChainAccountRows in 1 until Int.MAX_VALUE)
    }

    companion object {
        val PRODUCTION = WalletMigrationRowLimits(
            maxWalletRows = 8_192,
            maxChainAccountRows = 131_072
        )
    }
}

/**
 * Probes at most [maximumRows] + 1 small constant projections. This must run
 * before encrypted-preference reads so an attacker-sized database cannot make
 * a cross-store migration perform unbounded startup work.
 */
internal fun requireBoundedMigrationTableRows(
    database: SupportSQLiteDatabase,
    tableName: String,
    maximumRows: Int,
    rowDescription: String
) {
    require(maximumRows in 1 until Int.MAX_VALUE)
    require(
        tableName.isNotEmpty() &&
            tableName.all { it == '_' || it.isLetterOrDigit() }
    )

    database.query(
        "SELECT 1 FROM `$tableName` LIMIT ${maximumRows + 1}"
    ).use { cursor ->
        var rowCount = 0
        while (cursor.moveToNext()) {
            if (rowCount == maximumRows) {
                throw WalletPublicIdentityIntegrityException(
                    "$rowDescription exceeds the safe migration row limit"
                )
            }
            rowCount += 1
        }
    }
}

/**
 * Prevents a corrupt database row from making CursorWindow materialize an
 * attacker-sized identity value. The SQL CASE expression returns at most the
 * requested bound, while the separate byte length preserves absent/invalid
 * state without reading the original value.
 */
internal fun boundedBlobProjection(
    column: String,
    alias: String,
    maxBytes: Int
): String {
    require(maxBytes > 0)
    val storageClass = "typeof($column)"
    val byteLength =
        "CASE WHEN $storageClass = 'blob' THEN length($column) ELSE NULL END"
    return "$storageClass AS ${alias}StorageClass, " +
        "$byteLength AS ${alias}ByteLength, " +
        "CASE " +
        "WHEN $column IS NULL THEN NULL " +
        "WHEN $storageClass = 'blob' AND $byteLength <= $maxBytes THEN $column " +
        "ELSE X'' END AS $alias"
}

internal fun boundedTextProjection(
    column: String,
    alias: String,
    maxBytes: Int
): String {
    require(maxBytes > 0)
    val storageClass = "typeof($column)"
    val byteLength = "length(CAST($column AS BLOB))"
    val boundedByteLength =
        "CASE WHEN $storageClass = 'text' THEN $byteLength ELSE NULL END"
    return "$storageClass AS ${alias}StorageClass, " +
        "$boundedByteLength AS ${alias}ByteLength, " +
        "CASE " +
        "WHEN $column IS NULL THEN NULL " +
        "WHEN $storageClass = 'text' AND " +
        "$boundedByteLength <= $maxBytes THEN $column " +
        "ELSE '' END AS $alias"
}

/**
 * Reads SQLite INTEGER values without returning a dynamically typed TEXT/BLOB
 * payload through CursorWindow. Callers apply their own semantic range.
 */
internal fun boundedIntegerProjection(
    column: String,
    alias: String
): String {
    val storageClass = "typeof($column)"
    return "$storageClass AS ${alias}StorageClass, " +
        "CASE WHEN $storageClass = 'integer' THEN $column " +
        "ELSE NULL END AS $alias"
}

internal data class BoundedDatabaseBlob(
    val storageClass: String,
    val byteLength: Long?,
    val value: ByteArray?
) {
    val isPresent: Boolean
        get() = storageClass != SQLITE_NULL_STORAGE_CLASS

    val hasExpectedStorageClass: Boolean
        get() = storageClass == SQLITE_BLOB_STORAGE_CLASS

    val isOversized: Boolean
        get() = hasExpectedStorageClass && value == null

    fun valueOrInvalidPresentSentinel(): ByteArray? = when {
        !isPresent -> null
        !hasExpectedStorageClass || isOversized -> ByteArray(0)
        else -> value
    }
}

internal data class BoundedDatabaseText(
    val storageClass: String,
    val byteLength: Long?,
    val value: String?
) {
    val isPresent: Boolean
        get() = storageClass != SQLITE_NULL_STORAGE_CLASS

    val hasExpectedStorageClass: Boolean
        get() = storageClass == SQLITE_TEXT_STORAGE_CLASS

    val isOversized: Boolean
        get() = hasExpectedStorageClass && value == null
}

internal data class BoundedDatabaseInteger(
    val storageClass: String,
    val value: Long?
) {
    val hasExpectedStorageClass: Boolean
        get() = storageClass == SQLITE_INTEGER_STORAGE_CLASS
}

internal fun Cursor.readBoundedBlob(
    alias: String,
    maxBytes: Int
): BoundedDatabaseBlob {
    val storageClass = getString(
        getColumnIndexOrThrow("${alias}StorageClass")
    )
    check(storageClass in SQLITE_STORAGE_CLASSES) {
        "A database identity blob has an unknown SQLite storage class"
    }
    if (storageClass == SQLITE_NULL_STORAGE_CLASS) {
        return BoundedDatabaseBlob(
            storageClass = storageClass,
            byteLength = null,
            value = null
        )
    }
    if (storageClass != SQLITE_BLOB_STORAGE_CLASS) {
        return BoundedDatabaseBlob(
            storageClass = storageClass,
            byteLength = null,
            value = null
        )
    }

    val lengthIndex = getColumnIndexOrThrow("${alias}ByteLength")
    check(!isNull(lengthIndex)) {
        "A database identity blob has no byte length"
    }
    val byteLength = getLong(lengthIndex)
    check(byteLength >= 0L) {
        "A database identity blob has a negative length"
    }
    if (byteLength > maxBytes.toLong()) {
        return BoundedDatabaseBlob(
            storageClass = storageClass,
            byteLength = byteLength,
            value = null
        )
    }

    val valueIndex = getColumnIndexOrThrow(alias)
    check(getType(valueIndex) == Cursor.FIELD_TYPE_BLOB) {
        "A bounded database identity blob changed SQLite storage class"
    }
    val value = getBlob(valueIndex)
    check(value.size.toLong() == byteLength) {
        "A bounded database identity blob changed while it was read"
    }
    return BoundedDatabaseBlob(
        storageClass = storageClass,
        byteLength = byteLength,
        value = value
    )
}

internal fun Cursor.readBoundedText(
    alias: String,
    maxBytes: Int
): BoundedDatabaseText {
    val storageClass = getString(
        getColumnIndexOrThrow("${alias}StorageClass")
    )
    check(storageClass in SQLITE_STORAGE_CLASSES) {
        "A database identity string has an unknown SQLite storage class"
    }
    if (storageClass == SQLITE_NULL_STORAGE_CLASS) {
        return BoundedDatabaseText(
            storageClass = storageClass,
            byteLength = null,
            value = null
        )
    }
    if (storageClass != SQLITE_TEXT_STORAGE_CLASS) {
        return BoundedDatabaseText(
            storageClass = storageClass,
            byteLength = null,
            value = null
        )
    }

    val lengthIndex = getColumnIndexOrThrow("${alias}ByteLength")
    check(!isNull(lengthIndex)) {
        "A database identity string has no byte length"
    }
    val byteLength = getLong(lengthIndex)
    check(byteLength >= 0L) {
        "A database identity string has a negative length"
    }
    if (byteLength > maxBytes.toLong()) {
        return BoundedDatabaseText(
            storageClass = storageClass,
            byteLength = byteLength,
            value = null
        )
    }

    val valueIndex = getColumnIndexOrThrow(alias)
    check(getType(valueIndex) == Cursor.FIELD_TYPE_STRING) {
        "A bounded database identity string changed SQLite storage class"
    }
    val value = getString(valueIndex)
    check(value.toByteArray(Charsets.UTF_8).size.toLong() == byteLength) {
        "A bounded database identity string changed while it was read"
    }
    return BoundedDatabaseText(
        storageClass = storageClass,
        byteLength = byteLength,
        value = value
    )
}

internal fun Cursor.readBoundedInteger(
    alias: String
): BoundedDatabaseInteger {
    val storageClass = getString(
        getColumnIndexOrThrow("${alias}StorageClass")
    )
    check(storageClass in SQLITE_STORAGE_CLASSES) {
        "A database integer has an unknown SQLite storage class"
    }
    if (storageClass != SQLITE_INTEGER_STORAGE_CLASS) {
        return BoundedDatabaseInteger(
            storageClass = storageClass,
            value = null
        )
    }

    val valueIndex = getColumnIndexOrThrow(alias)
    check(
        !isNull(valueIndex) &&
            getType(valueIndex) == Cursor.FIELD_TYPE_INTEGER
    ) {
        "A bounded database integer changed SQLite storage class"
    }
    return BoundedDatabaseInteger(
        storageClass = storageClass,
        value = getLong(valueIndex)
    )
}

internal fun forEachBoundedWalletPublicIdentity(
    database: SupportSQLiteDatabase,
    includeTonPublicKey: Boolean,
    maximumRows: Int =
        WalletMigrationRowLimits.PRODUCTION.maxWalletRows,
    action: (WalletPublicIdentity) -> Unit
) {
    require(maximumRows in 1 until Int.MAX_VALUE)
    val projections = mutableListOf(
        boundedIntegerProjection(
            column = "id",
            alias = BOUNDED_WALLET_ID
        ),
        boundedBlobProjection(
            MetaAccountLocal.Table.Column.SUBSTRATE_PUBKEY,
            BOUNDED_SUBSTRATE_PUBLIC_KEY,
            MAX_SUBSTRATE_PUBLIC_KEY_BYTES
        ),
        boundedTextProjection(
            MetaAccountLocal.Table.Column.SUBSTRATE_CRYPTO_TYPE,
            BOUNDED_SUBSTRATE_CRYPTO_TYPE,
            MAX_CRYPTO_TYPE_BYTES
        ),
        boundedBlobProjection(
            MetaAccountLocal.Table.Column.SUBSTRATE_ACCOUNT_ID,
            BOUNDED_SUBSTRATE_ACCOUNT_ID,
            SUBSTRATE_ACCOUNT_ID_BYTES
        ),
        boundedBlobProjection(
            MetaAccountLocal.Table.Column.ETHEREUM_PUBKEY,
            BOUNDED_ETHEREUM_PUBLIC_KEY,
            ETHEREUM_PUBLIC_KEY_BYTES
        ),
        boundedBlobProjection(
            MetaAccountLocal.Table.Column.ETHEREUM_ADDRESS,
            BOUNDED_ETHEREUM_ADDRESS,
            ETHEREUM_ADDRESS_BYTES
        )
    )
    if (includeTonPublicKey) {
        projections += boundedBlobProjection(
            MetaAccountLocal.Table.Column.TON_PUBKEY,
            BOUNDED_TON_PUBLIC_KEY,
            TON_PUBLIC_KEY_BYTES
        )
    }

    database.query(
        "SELECT ${projections.joinToString()} " +
            // Ordering by a dynamically typed, corrupt id can force SQLite to
            // retain attacker-sized BLOB/TEXT values in a temporary sorter.
            // rowid preserves a stable bounded walk without materializing the
            // public identity being validated.
            "FROM meta_accounts ORDER BY rowid ASC " +
            "LIMIT ${maximumRows + 1}"
    ).use { cursor ->
        var rowCount = 0
        while (cursor.moveToNext()) {
            if (rowCount == maximumRows) {
                throw WalletPublicIdentityIntegrityException(
                    "Wallet rows exceed the safe migration row limit"
                )
            }
            rowCount += 1
            val walletId = cursor.readBoundedInteger(BOUNDED_WALLET_ID)
            val exactWalletId = walletId.value
            if (
                !walletId.hasExpectedStorageClass ||
                exactWalletId == null ||
                exactWalletId <= 0L
            ) {
                throw WalletPublicIdentityIntegrityException(
                    "A wallet row has no safe positive integer id"
                )
            }
            val cryptoType = cursor.readBoundedText(
                BOUNDED_SUBSTRATE_CRYPTO_TYPE,
                MAX_CRYPTO_TYPE_BYTES
            )
            action(
                WalletPublicIdentity(
                    metaId = exactWalletId,
                    substratePublicKey = cursor.readBoundedBlob(
                        BOUNDED_SUBSTRATE_PUBLIC_KEY,
                        MAX_SUBSTRATE_PUBLIC_KEY_BYTES
                    ).valueOrInvalidPresentSentinel(),
                    substrateCryptoType = cryptoType.value?.let {
                        try {
                            CryptoType.valueOf(it)
                        } catch (_: IllegalArgumentException) {
                            null
                        }
                    },
                    substrateCryptoTypeWasPresent = cryptoType.isPresent,
                    substrateAccountId = cursor.readBoundedBlob(
                        BOUNDED_SUBSTRATE_ACCOUNT_ID,
                        SUBSTRATE_ACCOUNT_ID_BYTES
                    ).valueOrInvalidPresentSentinel(),
                    ethereumPublicKey = cursor.readBoundedBlob(
                        BOUNDED_ETHEREUM_PUBLIC_KEY,
                        ETHEREUM_PUBLIC_KEY_BYTES
                    ).valueOrInvalidPresentSentinel(),
                    ethereumAddress = cursor.readBoundedBlob(
                        BOUNDED_ETHEREUM_ADDRESS,
                        ETHEREUM_ADDRESS_BYTES
                    ).valueOrInvalidPresentSentinel(),
                    tonPublicKey = if (includeTonPublicKey) {
                        cursor.readBoundedBlob(
                            BOUNDED_TON_PUBLIC_KEY,
                            TON_PUBLIC_KEY_BYTES
                        ).valueOrInvalidPresentSentinel()
                    } else {
                        null
                    }
                )
            )
        }
    }
}

internal const val SUBSTRATE_ACCOUNT_ID_BYTES = 32
internal const val MAX_SUBSTRATE_PUBLIC_KEY_BYTES = 33
internal const val ETHEREUM_PUBLIC_KEY_BYTES = 64
internal const val ETHEREUM_ADDRESS_BYTES = 20
internal const val TON_PUBLIC_KEY_BYTES = 32
internal const val MAX_CRYPTO_TYPE_BYTES = 32

private const val BOUNDED_SUBSTRATE_PUBLIC_KEY =
    "boundedSubstratePublicKey"
private const val BOUNDED_SUBSTRATE_CRYPTO_TYPE =
    "boundedSubstrateCryptoType"
private const val BOUNDED_SUBSTRATE_ACCOUNT_ID =
    "boundedSubstrateAccountId"
private const val BOUNDED_ETHEREUM_PUBLIC_KEY =
    "boundedEthereumPublicKey"
private const val BOUNDED_ETHEREUM_ADDRESS = "boundedEthereumAddress"
private const val BOUNDED_TON_PUBLIC_KEY = "boundedTonPublicKey"
private const val BOUNDED_WALLET_ID = "boundedWalletId"

private const val SQLITE_NULL_STORAGE_CLASS = "null"
private const val SQLITE_INTEGER_STORAGE_CLASS = "integer"
private const val SQLITE_REAL_STORAGE_CLASS = "real"
private const val SQLITE_TEXT_STORAGE_CLASS = "text"
private const val SQLITE_BLOB_STORAGE_CLASS = "blob"
private val SQLITE_STORAGE_CLASSES = setOf(
    SQLITE_NULL_STORAGE_CLASS,
    SQLITE_INTEGER_STORAGE_CLASS,
    SQLITE_REAL_STORAGE_CLASS,
    SQLITE_TEXT_STORAGE_CLASS,
    SQLITE_BLOB_STORAGE_CLASS
)
