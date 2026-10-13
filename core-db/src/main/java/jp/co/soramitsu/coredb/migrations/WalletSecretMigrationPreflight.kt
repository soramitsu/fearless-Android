package jp.co.soramitsu.coredb.migrations

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityRecovery
import jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryStateIntegrityException
import jp.co.soramitsu.fearless_utils.extensions.toHexString

/**
 * Read-only validation that must run before a Room migration durably mutates
 * encrypted preferences. Room can roll a multi-edge SQL upgrade back to its
 * original version, but it cannot roll external preference storage back.
 */
internal object WalletSecretMigrationPreflight {

    fun requireSafeToMutate(
        database: SupportSQLiteDatabase,
        encryptedPreferences: EncryptedPreferences,
        includeTonPublicKey: Boolean,
        rowLimits: WalletMigrationRowLimits =
            WalletMigrationRowLimits.PRODUCTION
    ) {
        // Both probes deliberately precede every encrypted-preference lookup.
        // A database above either bound must fail with zero cross-store reads
        // or writes, rather than partially migrating earlier wallet rows.
        requireBoundedMigrationTableRows(
            database = database,
            tableName = "meta_accounts",
            maximumRows = rowLimits.maxWalletRows,
            rowDescription = "Wallet rows"
        )
        requireBoundedMigrationTableRows(
            database = database,
            tableName = "chain_accounts",
            maximumRows = rowLimits.maxChainAccountRows,
            rowDescription = "Chain-account rows"
        )
        preflightRootRecoveryMarkers(
            database = database,
            encryptedPreferences = encryptedPreferences,
            maximumRows = rowLimits.maxWalletRows
        )
        preflightChainAccountRecoveryMarkers(
            database = database,
            encryptedPreferences = encryptedPreferences,
            maximumRows = rowLimits.maxChainAccountRows
        )
        forEachBoundedWalletPublicIdentity(
            database = database,
            includeTonPublicKey = includeTonPublicKey,
            maximumRows = rowLimits.maxWalletRows
        ) {
            // Force every version-required public-identity projection to be
            // present and safely readable before preference writes begin.
        }
    }

    fun hasValidPublicIdentityRecovery(
        encryptedPreferences: EncryptedPreferences,
        metaId: Long,
        activeSecretKey: String
    ): Boolean {
        val markerKey = WalletPublicIdentityRecovery.keyFor(
            metaId = metaId,
            activeSecretKey = activeSecretKey
        )
        if (!encryptedPreferences.hasKey(markerKey)) return false

        if (
            encryptedPreferences.getDecryptedString(markerKey) !=
            WalletPublicIdentityRecovery.MARKER_VALUE
        ) {
            throw WalletRecoveryStateIntegrityException(
                "A wallet public-identity recovery marker is invalid"
            )
        }
        return true
    }

    private fun preflightRootRecoveryMarkers(
        database: SupportSQLiteDatabase,
        encryptedPreferences: EncryptedPreferences,
        maximumRows: Int
    ) {
        val walletId = boundedPositiveIntegerProjection(
            column = "id",
            alias = BOUNDED_WALLET_ID
        )
        database.query(
            "SELECT $walletId FROM meta_accounts ORDER BY rowid ASC " +
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
                val metaId = cursor.readPositiveInteger(
                    alias = BOUNDED_WALLET_ID,
                    errorMessage = "A wallet row has no safe positive integer id"
                )
                rootActiveSecretKeys(metaId).forEach { activeSecretKey ->
                    hasValidPublicIdentityRecovery(
                        encryptedPreferences = encryptedPreferences,
                        metaId = metaId,
                        activeSecretKey = activeSecretKey
                    )
                }
            }
        }
    }

    private fun preflightChainAccountRecoveryMarkers(
        database: SupportSQLiteDatabase,
        encryptedPreferences: EncryptedPreferences,
        maximumRows: Int
    ) {
        val walletId = boundedPositiveIntegerProjection(
            column = "metaId",
            alias = BOUNDED_CHAIN_WALLET_ID
        )
        val accountId = boundedBlobProjection(
            column = "accountId",
            alias = BOUNDED_CHAIN_ACCOUNT_ID,
            maxBytes = MAX_CHAIN_ACCOUNT_ID_READ_BYTES
        )
        val publicKey = boundedBlobProjection(
            column = "publicKey",
            alias = BOUNDED_CHAIN_PUBLIC_KEY,
            maxBytes = MAX_SUBSTRATE_PUBLIC_KEY_BYTES
        )
        val cryptoType = boundedTextProjection(
            column = "cryptoType",
            alias = BOUNDED_CHAIN_CRYPTO_TYPE,
            maxBytes = MAX_CRYPTO_TYPE_BYTES
        )
        database.query(
            "SELECT $walletId, $accountId, $publicKey, $cryptoType " +
                "FROM chain_accounts ORDER BY rowid ASC " +
                "LIMIT ${maximumRows + 1}"
        ).use { cursor ->
            var rowCount = 0
            while (cursor.moveToNext()) {
                if (rowCount == maximumRows) {
                    throw WalletPublicIdentityIntegrityException(
                        "Chain-account rows exceed the safe migration row limit"
                    )
                }
                rowCount += 1
                val metaId = cursor.readPositiveInteger(
                    alias = BOUNDED_CHAIN_WALLET_ID,
                    errorMessage =
                    "A chain-account row has no safe positive integer wallet id"
                )
                val boundedAccountId = cursor.readBoundedBlob(
                    alias = BOUNDED_CHAIN_ACCOUNT_ID,
                    maxBytes = MAX_CHAIN_ACCOUNT_ID_READ_BYTES
                )
                val exactAccountId = boundedAccountId.value
                if (
                    !boundedAccountId.hasExpectedStorageClass ||
                    boundedAccountId.isOversized ||
                    exactAccountId == null
                ) {
                    throw WalletPublicIdentityIntegrityException(
                        "A chain-account id has no safe bounded BLOB identity"
                    )
                }
                val activeSecretKey =
                    "$metaId:${exactAccountId.toHexString()}:ACCESS_SECRETS"
                cursor.readBoundedBlob(
                    alias = BOUNDED_CHAIN_PUBLIC_KEY,
                    maxBytes = MAX_SUBSTRATE_PUBLIC_KEY_BYTES
                )
                cursor.readBoundedText(
                    alias = BOUNDED_CHAIN_CRYPTO_TYPE,
                    maxBytes = MAX_CRYPTO_TYPE_BYTES
                )
                hasValidPublicIdentityRecovery(
                    encryptedPreferences = encryptedPreferences,
                    metaId = metaId,
                    activeSecretKey = activeSecretKey
                )
            }
        }
    }

    private fun boundedPositiveIntegerProjection(
        column: String,
        alias: String
    ): String {
        val storageClass = "typeof($column)"
        return "$storageClass AS ${alias}StorageClass, " +
            "CASE WHEN $storageClass = 'integer' THEN $column " +
            "ELSE NULL END AS $alias"
    }

    private fun Cursor.readPositiveInteger(
        alias: String,
        errorMessage: String
    ): Long {
        val storageClassIndex =
            getColumnIndexOrThrow("${alias}StorageClass")
        val valueIndex = getColumnIndexOrThrow(alias)
        if (
            getString(storageClassIndex) != SQLITE_INTEGER_STORAGE_CLASS ||
            isNull(valueIndex) ||
            getType(valueIndex) != Cursor.FIELD_TYPE_INTEGER ||
            getLong(valueIndex) <= 0L
        ) {
            throw WalletPublicIdentityIntegrityException(errorMessage)
        }
        return getLong(valueIndex)
    }

    private fun rootActiveSecretKeys(metaId: Long): List<String> = listOf(
        "$metaId:ACCESS_SECRETS",
        "$metaId:SUBSTRATE_SECRETS",
        "$metaId:ETHEREUM_SECRETS",
        "$metaId:TON_SECRETS"
    )

    private const val MAX_CHAIN_ACCOUNT_ID_READ_BYTES = 64
    private const val BOUNDED_WALLET_ID = "boundedWalletId"
    private const val BOUNDED_CHAIN_WALLET_ID = "boundedChainWalletId"
    private const val BOUNDED_CHAIN_ACCOUNT_ID = "boundedChainAccountId"
    private const val BOUNDED_CHAIN_PUBLIC_KEY = "boundedChainPublicKey"
    private const val BOUNDED_CHAIN_CRYPTO_TYPE = "boundedChainCryptoType"
    private const val SQLITE_INTEGER_STORAGE_CLASS = "integer"
}
