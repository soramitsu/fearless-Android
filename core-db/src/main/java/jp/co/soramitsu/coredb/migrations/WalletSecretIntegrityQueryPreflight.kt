package jp.co.soramitsu.coredb.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException

/**
 * Read-only query preflight for the version 76 wallet-secret integrity pass.
 *
 * The migration writes encrypted preferences while Room owns a separate SQL
 * transaction. Every database query that can still fail must therefore be
 * compiled and fully traversed before the first preference mutation.
 */
internal object WalletSecretIntegrityQueryPreflight {

    fun requireReadable(
        database: SupportSQLiteDatabase,
        rowLimits: WalletMigrationRowLimits =
            WalletMigrationRowLimits.PRODUCTION,
        foreignKeyCheckLimits: MigrationForeignKeyCheckLimits =
            WALLET_INTEGRITY_FOREIGN_KEY_CHECK_LIMITS
    ) {
        requireExactReleasedRoomSchemaObjects(
            database = database,
            context = "Wallet-secret v76 preflight",
            allowedObjectSets = setOf(
                RELEASED_ROOM_SCHEMA_OBJECTS_V76,
                RELEASED_ROOM_SCHEMA_OBJECTS_V76_WITH_LEGACY_USERS_RECOVERY
            ),
            maximumSchemaObjects =
            RELEASED_ROOM_SCHEMA_OBJECTS_V76_WITH_LEGACY_USERS_RECOVERY.size
        ) { message ->
            WalletPublicIdentityIntegrityException(message)
        }
        val releasedSchema = if (
            hasLegacyUsersRecoveryLedger(database)
        ) {
            RELEASED_ROOM_SCHEMA_V76_WITH_LEGACY_USERS_RECOVERY
        } else {
            RELEASED_ROOM_SCHEMA_V76
        }
        requireExactReleasedRoomSchema(
            database = database,
            context = "Wallet-secret v76 preflight",
            tables = releasedSchema
        ) { message ->
            WalletPublicIdentityIntegrityException(message)
        }
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
        preflightMetaAccountIdentities(
            database = database,
            maximumRows = rowLimits.maxWalletRows
        )
        preflightChainAccountIdentities(
            database = database,
            maximumRows = rowLimits.maxChainAccountRows
        )
        requireBoundedForeignKeyCheck(
            database = database,
            limits = foreignKeyCheckLimits,
            previouslyBoundedRows = mapOf(
                "chain_accounts" to rowLimits.maxChainAccountRows
            )
        ) { message ->
            WalletPublicIdentityIntegrityException(message)
        }
    }

    private fun preflightMetaAccountIdentities(
        database: SupportSQLiteDatabase,
        maximumRows: Int
    ) {
        forEachBoundedWalletPublicIdentity(
            database = database,
            includeTonPublicKey = true,
            maximumRows = maximumRows
        ) {
            // Traversal is the preflight: the bounded projection deliberately
            // materializes every public-identity field used later.
        }
    }

    private fun preflightChainAccountIdentities(
        database: SupportSQLiteDatabase,
        maximumRows: Int
    ) {
        val metaId = boundedIntegerProjection(
            column = "metaId",
            alias = BOUNDED_CHAIN_META_ID
        )
        val accountId = boundedBlobProjection(
            column = "accountId",
            alias = BOUNDED_CHAIN_ACCOUNT_ID,
            maxBytes = MAX_CHAIN_ACCOUNT_ID_READ_BYTES
        )
        val publicKey = boundedBlobProjection(
            column = "chain_accounts.publicKey",
            alias = BOUNDED_CHAIN_PUBLIC_KEY,
            maxBytes = MAX_CHAIN_ACCOUNT_PUBLIC_KEY_BYTES
        )
        val cryptoType = boundedTextProjection(
            column = "chain_accounts.cryptoType",
            alias = BOUNDED_CHAIN_CRYPTO_TYPE,
            maxBytes = MAX_CRYPTO_TYPE_BYTES
        )
        val ecosystem = boundedTextProjection(
            column = "chains.ecosystem",
            alias = BOUNDED_CHAIN_ECOSYSTEM,
            maxBytes = MAX_ECOSYSTEM_BYTES
        )

        database.query(
            "SELECT $metaId, $accountId, $publicKey, $cryptoType, $ecosystem " +
                "FROM chain_accounts " +
                "INNER JOIN chains ON chains.id = chain_accounts.chainId " +
                "ORDER BY chain_accounts.rowid ASC " +
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
                val boundedMetaId = cursor.readBoundedInteger(
                    BOUNDED_CHAIN_META_ID
                )
                if (
                    !boundedMetaId.hasExpectedStorageClass ||
                    boundedMetaId.value == null ||
                    boundedMetaId.value <= 0L
                ) {
                    throw WalletPublicIdentityIntegrityException(
                        "A chain-account row has no safe positive integer wallet id"
                    )
                }
                val boundedAccountId = cursor.readBoundedBlob(
                    alias = BOUNDED_CHAIN_ACCOUNT_ID,
                    maxBytes = MAX_CHAIN_ACCOUNT_ID_READ_BYTES
                )
                if (
                    !boundedAccountId.hasExpectedStorageClass ||
                    boundedAccountId.isOversized ||
                    boundedAccountId.value == null
                ) {
                    throw WalletPublicIdentityIntegrityException(
                        "A chain-account id has no safe bounded BLOB identity"
                    )
                }
                cursor.readBoundedBlob(
                    alias = BOUNDED_CHAIN_PUBLIC_KEY,
                    maxBytes = MAX_CHAIN_ACCOUNT_PUBLIC_KEY_BYTES
                ).also {
                    if (
                        !it.hasExpectedStorageClass ||
                        it.isOversized ||
                        it.value == null
                    ) {
                        throw WalletPublicIdentityIntegrityException(
                            "A chain-account public key has no safe bounded BLOB identity"
                        )
                    }
                }
                cursor.readBoundedText(
                    alias = BOUNDED_CHAIN_CRYPTO_TYPE,
                    maxBytes = MAX_CRYPTO_TYPE_BYTES
                ).also {
                    if (
                        !it.hasExpectedStorageClass ||
                        it.isOversized ||
                        it.value == null
                    ) {
                        throw WalletPublicIdentityIntegrityException(
                            "A chain-account crypto type has no safe bounded identity"
                        )
                    }
                }
                cursor.readBoundedText(
                    alias = BOUNDED_CHAIN_ECOSYSTEM,
                    maxBytes = MAX_ECOSYSTEM_BYTES
                ).also {
                    if (
                        !it.hasExpectedStorageClass ||
                        it.isOversized ||
                        it.value == null
                    ) {
                        throw WalletPublicIdentityIntegrityException(
                            "A chain-account ecosystem has no safe bounded identity"
                        )
                    }
                }
            }
        }
    }

    private fun hasLegacyUsersRecoveryLedger(
        database: SupportSQLiteDatabase
    ): Boolean {
        return database.query(
            "SELECT 1 FROM sqlite_master " +
                "WHERE type = 'table' " +
                "AND name = 'legacy_users_recovery' LIMIT 1"
        ).use { cursor ->
            cursor.moveToFirst()
        }
    }

    private const val MAX_CHAIN_ACCOUNT_ID_READ_BYTES = 64
    private const val MAX_CHAIN_ACCOUNT_PUBLIC_KEY_BYTES = 33
    private const val MAX_ECOSYSTEM_BYTES = 32
    private const val BOUNDED_CHAIN_META_ID =
        "preflightBoundedChainMetaId"
    private const val BOUNDED_CHAIN_ACCOUNT_ID =
        "preflightBoundedChainAccountId"
    private const val BOUNDED_CHAIN_PUBLIC_KEY =
        "preflightBoundedChainPublicKey"
    private const val BOUNDED_CHAIN_CRYPTO_TYPE =
        "preflightBoundedChainCryptoType"
    private const val BOUNDED_CHAIN_ECOSYSTEM =
        "preflightBoundedChainEcosystem"
}
