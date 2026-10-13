@file:Suppress("MagicNumber")

package jp.co.soramitsu.coredb.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException

/**
 * Exact schema contract for the released DB31 state.
 *
 * The 31 -> 32 edge writes encrypted preferences outside Room's SQL
 * transaction. Any deterministic retained-schema failure later in the same
 * upgrade must therefore be rejected before that first external write.
 */
internal object Db31UpgradeSqlPreflight {

    fun requireSafeBeforePreferenceAccess(
        database: SupportSQLiteDatabase
    ) {
        requireExactSchemaObjects(database)
        requireExactReleasedRoomSchema(
            database = database,
            context = "Version 31",
            tables = VERSION_31_TABLES
        ) { message ->
            WalletPublicIdentityIntegrityException(message)
        }
        requireBoundedForeignKeyCheck(
            database = database,
            limits = DB31_UPGRADE_FOREIGN_KEY_CHECK_LIMITS
        ) { message ->
            WalletPublicIdentityIntegrityException("Version 31 $message")
        }
    }

    private fun requireExactSchemaObjects(
        database: SupportSQLiteDatabase
    ) {
        val objectType = boundedTextProjection(
            column = "type",
            alias = BOUNDED_SCHEMA_OBJECT_TYPE,
            maxBytes = MAX_SCHEMA_IDENTIFIER_BYTES
        )
        val objectName = boundedTextProjection(
            column = "name",
            alias = BOUNDED_SCHEMA_OBJECT_NAME,
            maxBytes = MAX_SCHEMA_IDENTIFIER_BYTES
        )
        val tableName = boundedTextProjection(
            column = "tbl_name",
            alias = BOUNDED_SCHEMA_TABLE_NAME,
            maxBytes = MAX_SCHEMA_IDENTIFIER_BYTES
        )
        val actual = linkedSetOf<ReleasedSchemaObject>()
        database.query(
            "SELECT $objectType, $objectName, $tableName " +
                "FROM sqlite_master ORDER BY rowid ASC " +
                "LIMIT ${VERSION_31_SCHEMA_OBJECTS.size + 1}"
        ).use { cursor ->
            var rowCount = 0
            while (cursor.moveToNext()) {
                if (rowCount == VERSION_31_SCHEMA_OBJECTS.size) {
                    fail(
                        "Version 31 SQLite schema objects exceed the released count"
                    )
                }
                rowCount += 1
                val observed = ReleasedSchemaObject(
                    type = cursor.requireSchemaIdentifier(
                        BOUNDED_SCHEMA_OBJECT_TYPE
                    ),
                    name = cursor.requireSchemaIdentifier(
                        BOUNDED_SCHEMA_OBJECT_NAME
                    ),
                    tableName = cursor.requireSchemaIdentifier(
                        BOUNDED_SCHEMA_TABLE_NAME
                    )
                )
                if (!actual.add(observed)) {
                    fail("Version 31 contains duplicate schema objects")
                }
            }
        }
        if (actual != VERSION_31_SCHEMA_OBJECTS) {
            val firstMissing = (VERSION_31_SCHEMA_OBJECTS - actual)
                .minByOrNull { "${it.type}:${it.name}:${it.tableName}" }
            val firstUnexpected = (actual - VERSION_31_SCHEMA_OBJECTS)
                .minByOrNull { "${it.type}:${it.name}:${it.tableName}" }
            val details = listOfNotNull(
                firstMissing?.let {
                    "missing ${it.type} ${it.name} on ${it.tableName}"
                },
                firstUnexpected?.let {
                    "unexpected ${it.type} ${it.name} on ${it.tableName}"
                }
            ).joinToString(separator = "; ")
            fail(
                "Version 31 has missing, extra, or incompatible schema objects" +
                    details.takeIf(String::isNotEmpty)
                        ?.let { ": $it" }.orEmpty()
            )
        }
    }

    private fun android.database.Cursor.requireSchemaIdentifier(
        alias: String
    ): String {
        val bounded = readBoundedText(
            alias = alias,
            maxBytes = MAX_SCHEMA_IDENTIFIER_BYTES
        )
        val value = bounded.value
        if (
            !bounded.isPresent ||
            !bounded.hasExpectedStorageClass ||
            bounded.isOversized ||
            value.isNullOrEmpty()
        ) {
            fail("Version 31 contains an unsafe SQLite schema identifier")
        }
        return value
    }

    private fun fail(message: String): Nothing {
        throw WalletPublicIdentityIntegrityException(message)
    }

    private data class ReleasedSchemaObject(
        val type: String,
        val name: String,
        val tableName: String
    )

    private fun table(name: String) = ReleasedSchemaObject(
        type = "table",
        name = name,
        tableName = name
    )

    private fun index(name: String, tableName: String) =
        ReleasedSchemaObject(
            type = "index",
            name = name,
            tableName = tableName
        )

    private fun column(
        name: String,
        type: String,
        notNull: Boolean = false,
        primaryKeyPosition: Int = 0,
        defaultValue: String? = null
    ) = ReleasedRoomColumnSpec(
        name = name,
        type = type,
        notNull = notNull,
        primaryKeyPosition = primaryKeyPosition,
        allowedDefaultValues = setOf(defaultValue)
    )

    private fun releasedIndex(
        name: String,
        vararg columns: String
    ) = ReleasedRoomIndexSpec(
        name = name,
        columns = columns.toList()
    )

    private val VERSION_31_TABLES = listOf(
        ReleasedRoomTableSpec(
            name = "account_staking_accesses",
            columns = listOf(
                column("chainId", "TEXT", true, 1),
                column("chainAssetId", "TEXT", true, 2),
                column("accountId", "BLOB", true, 3),
                column("stashId", "BLOB"),
                column("controllerId", "BLOB")
            )
        ),
        ReleasedRoomTableSpec(
            name = "assets",
            columns = listOf(
                column("tokenSymbol", "TEXT", true, 1),
                column("chainId", "TEXT", true, 2),
                column("accountId", "BLOB", true, 3),
                column("metaId", "INTEGER", true),
                column("freeInPlanks", "TEXT", true),
                column("reservedInPlanks", "TEXT", true),
                column("miscFrozenInPlanks", "TEXT", true),
                column("feeFrozenInPlanks", "TEXT", true),
                column("bondedInPlanks", "TEXT", true),
                column("redeemableInPlanks", "TEXT", true),
                column("unbondingInPlanks", "TEXT", true)
            ),
            indexes = setOf(
                releasedIndex("index_assets_metaId", "metaId")
            )
        ),
        ReleasedRoomTableSpec(
            name = "chain_accounts",
            columns = listOf(
                column("metaId", "INTEGER", true, 1),
                column("chainId", "TEXT", true, 2),
                column("publicKey", "BLOB", true),
                column("accountId", "BLOB", true),
                column("cryptoType", "TEXT", true)
            ),
            indexes = setOf(
                releasedIndex("index_chain_accounts_chainId", "chainId"),
                releasedIndex("index_chain_accounts_metaId", "metaId"),
                releasedIndex(
                    "index_chain_accounts_accountId",
                    "accountId"
                )
            )
        ),
        ReleasedRoomTableSpec(
            name = "chain_assets",
            columns = listOf(
                column("id", "TEXT", true, 2),
                column("chainId", "TEXT", true, 1),
                column("name", "TEXT", true),
                column("icon", "TEXT", true),
                column("priceId", "TEXT"),
                column("staking", "TEXT", true),
                column("precision", "INTEGER", true),
                column("priceProviders", "TEXT"),
                column("nativeChainId", "TEXT")
            ),
            alternateColumnLayouts = listOf(
                // The released 29 -> 30 migration rebuilt this table with
                // precision before the price/staking fields. Room validates
                // columns as a map, so both released physical layouts are
                // legitimate version-31 databases.
                listOf(
                    column("id", "TEXT", true, 2),
                    column("chainId", "TEXT", true, 1),
                    column("name", "TEXT", true),
                    column("icon", "TEXT", true),
                    column("precision", "INTEGER", true),
                    column("priceId", "TEXT"),
                    column("staking", "TEXT", true),
                    column("priceProviders", "TEXT"),
                    column("nativeChainId", "TEXT")
                )
            ),
            indexes = setOf(
                releasedIndex("index_chain_assets_chainId", "chainId")
            )
        ),
        ReleasedRoomTableSpec(
            name = "chain_nodes",
            columns = listOf(
                column("chainId", "TEXT", true, 1),
                column("url", "TEXT", true, 2),
                column("name", "TEXT", true),
                column(
                    "isActive",
                    "INTEGER",
                    notNull = true,
                    defaultValue = "0"
                ),
                column(
                    "isDefault",
                    "INTEGER",
                    notNull = true,
                    defaultValue = "1"
                )
            ),
            indexes = setOf(
                releasedIndex("index_chain_nodes_chainId", "chainId")
            )
        ),
        ReleasedRoomTableSpec(
            name = "chain_runtimes",
            columns = listOf(
                column("chainId", "TEXT", true, 1),
                column("syncedVersion", "INTEGER", true),
                column("remoteVersion", "INTEGER", true)
            ),
            indexes = setOf(
                releasedIndex("index_chain_runtimes_chainId", "chainId")
            )
        ),
        ReleasedRoomTableSpec(
            name = "chains",
            columns = listOf(
                column("id", "TEXT", true, 1),
                column("parentId", "TEXT"),
                column("name", "TEXT", true),
                column("icon", "TEXT", true),
                column("prefix", "INTEGER", true),
                column("isEthereumBased", "INTEGER", true),
                column("isTestNet", "INTEGER", true),
                column("hasCrowdloans", "INTEGER", true),
                column("url", "TEXT"),
                column("overridesCommon", "INTEGER"),
                column("staking_url", "TEXT"),
                column("staking_type", "TEXT"),
                column("history_url", "TEXT"),
                column("history_type", "TEXT"),
                column("crowdloans_url", "TEXT"),
                column("crowdloans_type", "TEXT")
            )
        ),
        ReleasedRoomTableSpec(
            name = "meta_accounts",
            columns = listOf(
                column("substratePublicKey", "BLOB", true),
                column("substrateCryptoType", "TEXT", true),
                column("substrateAccountId", "BLOB", true),
                column("ethereumPublicKey", "BLOB"),
                column("ethereumAddress", "BLOB"),
                column("name", "TEXT", true),
                column("isSelected", "INTEGER", true),
                column("position", "INTEGER", true),
                column("id", "INTEGER", true, 1)
            ),
            alternateColumnLayouts = listOf(
                // Released v28 databases placed the generated id first and
                // retained that physical order through 28 -> 31.
                listOf(
                    column("id", "INTEGER", true, 1),
                    column("substratePublicKey", "BLOB", true),
                    column("substrateCryptoType", "TEXT", true),
                    column("substrateAccountId", "BLOB", true),
                    column("ethereumPublicKey", "BLOB"),
                    column("ethereumAddress", "BLOB"),
                    column("name", "TEXT", true),
                    column("isSelected", "INTEGER", true),
                    column("position", "INTEGER", true)
                )
            ),
            indexes = setOf(
                releasedIndex(
                    "index_meta_accounts_substrateAccountId",
                    "substrateAccountId"
                ),
                releasedIndex(
                    "index_meta_accounts_ethereumAddress",
                    "ethereumAddress"
                )
            )
        ),
        ReleasedRoomTableSpec(
            name = "operations",
            columns = listOf(
                column("id", "TEXT", true, 1),
                column("address", "TEXT", true, 2),
                column("chainId", "TEXT", true, 3),
                column("chainAssetId", "TEXT", true, 4),
                column("time", "INTEGER", true),
                column("status", "INTEGER", true),
                column("source", "INTEGER", true),
                column("operationType", "INTEGER", true),
                column("module", "TEXT"),
                column("call", "TEXT"),
                column("amount", "TEXT"),
                column("sender", "TEXT"),
                column("receiver", "TEXT"),
                column("hash", "TEXT"),
                column("fee", "TEXT"),
                column("isReward", "INTEGER"),
                column("era", "INTEGER"),
                column("validator", "TEXT")
            )
        ),
        ReleasedRoomTableSpec(
            name = "phishing_addresses",
            columns = listOf(
                column("publicKey", "TEXT", true, 1)
            )
        ),
        ReleasedRoomTableSpec(
            name = "room_master_table",
            columns = listOf(
                column("id", "INTEGER", primaryKeyPosition = 1),
                column("identity_hash", "TEXT")
            )
        ),
        ReleasedRoomTableSpec(
            name = "storage",
            columns = listOf(
                column("storageKey", "TEXT", true, 2),
                column("content", "TEXT"),
                column("chainId", "TEXT", true, 1)
            )
        ),
        ReleasedRoomTableSpec(
            name = "tokens",
            columns = listOf(
                column("symbol", "TEXT", true, 1),
                column("dollarRate", "TEXT"),
                column("recentRateChange", "TEXT")
            )
        ),
        ReleasedRoomTableSpec(
            name = "total_reward",
            columns = listOf(
                column("accountAddress", "TEXT", true, 1),
                column("totalReward", "TEXT", true)
            )
        ),
        ReleasedRoomTableSpec(
            name = "users",
            columns = listOf(
                column("address", "TEXT", true, 1),
                column("username", "TEXT", true),
                column("publicKey", "TEXT", true),
                column("cryptoType", "INTEGER", true),
                column("position", "INTEGER", true),
                column("networkType", "INTEGER", true)
            )
        )
    )

    private val VERSION_31_SCHEMA_OBJECTS = setOf(
        table("account_staking_accesses"),
        table("android_metadata"),
        table("assets"),
        table("chain_accounts"),
        table("chain_assets"),
        table("chain_nodes"),
        table("chain_runtimes"),
        table("chains"),
        table("meta_accounts"),
        table("operations"),
        table("phishing_addresses"),
        table("room_master_table"),
        table("sqlite_sequence"),
        table("storage"),
        table("tokens"),
        table("total_reward"),
        table("users"),
        index(
            "sqlite_autoindex_account_staking_accesses_1",
            "account_staking_accesses"
        ),
        index("sqlite_autoindex_assets_1", "assets"),
        index("index_assets_metaId", "assets"),
        index("sqlite_autoindex_chain_accounts_1", "chain_accounts"),
        index("index_chain_accounts_chainId", "chain_accounts"),
        index("index_chain_accounts_metaId", "chain_accounts"),
        index("index_chain_accounts_accountId", "chain_accounts"),
        index("sqlite_autoindex_chain_assets_1", "chain_assets"),
        index("index_chain_assets_chainId", "chain_assets"),
        index("sqlite_autoindex_chain_nodes_1", "chain_nodes"),
        index("index_chain_nodes_chainId", "chain_nodes"),
        index("sqlite_autoindex_chain_runtimes_1", "chain_runtimes"),
        index("index_chain_runtimes_chainId", "chain_runtimes"),
        index("sqlite_autoindex_chains_1", "chains"),
        index("index_meta_accounts_substrateAccountId", "meta_accounts"),
        index("index_meta_accounts_ethereumAddress", "meta_accounts"),
        index("sqlite_autoindex_operations_1", "operations"),
        index(
            "sqlite_autoindex_phishing_addresses_1",
            "phishing_addresses"
        ),
        index("sqlite_autoindex_storage_1", "storage"),
        index("sqlite_autoindex_tokens_1", "tokens"),
        index("sqlite_autoindex_total_reward_1", "total_reward"),
        index("sqlite_autoindex_users_1", "users")
    )

    private const val MAX_SCHEMA_IDENTIFIER_BYTES = 128
    private const val BOUNDED_SCHEMA_OBJECT_TYPE =
        "boundedDb31SchemaObjectType"
    private const val BOUNDED_SCHEMA_OBJECT_NAME =
        "boundedDb31SchemaObjectName"
    private const val BOUNDED_SCHEMA_TABLE_NAME =
        "boundedDb31SchemaTableName"
}
