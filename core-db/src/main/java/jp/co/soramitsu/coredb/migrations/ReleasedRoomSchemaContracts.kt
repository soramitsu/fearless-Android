package jp.co.soramitsu.coredb.migrations

private val ABSENT_OR_SQL_NULL_DEFAULT = setOf<String?>(null, "NULL")
private val ABSENT_OR_ZERO_DEFAULT = setOf<String?>(null, "0")
private val SQL_NULL_DEFAULT = setOf<String?>("NULL")
private val ZERO_DEFAULT = setOf<String?>("0")
private val SUBSTRATE_DEFAULT = setOf<String?>("'Substrate'")

private val RELEASED_CHAIN_CORE_COLUMNS = listOf(
    "name",
    "minSupportedVersion",
    "icon",
    "prefix",
    "isEthereumBased",
    "isTestNet",
    "hasCrowdloans",
    "supportStakingPool"
)

private val RELEASED_CHAIN_URL_COLUMNS = listOf(
    "staking_url",
    "staking_type",
    "history_url",
    "history_type",
    "crowdloans_url",
    "crowdloans_type"
)

private fun releasedVersion71ChainOrder(
    identityColumns: List<String>,
    featureColumnsBeforeUrls: List<String>,
    appendedColumns: List<String>
) = identityColumns +
    RELEASED_CHAIN_CORE_COLUMNS +
    featureColumnsBeforeUrls +
    RELEASED_CHAIN_URL_COLUMNS +
    appendedColumns

/**
 * Every distinct chains order shipped by a fresh database through v71.
 *
 * Room writes then-current entity fields in declaration order, while later
 * ALTER migrations append. These cohorts are frozen from released tags
 * spanning DB54 through DB71, plus the common <=57 upgrade path.
 */
internal val RELEASED_VERSION_71_CHAIN_COLUMN_ORDERS = listOf(
    releasedVersion71ChainOrder(
        identityColumns = listOf("id", "parentId"),
        featureColumnsBeforeUrls = emptyList(),
        appendedColumns = listOf(
            "isEthereumChain",
            "rank",
            "paraId",
            "isChainlinkProvider",
            "supportNft",
            "isUsesAppId",
            "identityChain",
            "remoteAssetsSource"
        )
    ),
    releasedVersion71ChainOrder(
        identityColumns = listOf("id", "parentId"),
        featureColumnsBeforeUrls = listOf("isEthereumChain"),
        appendedColumns = listOf(
            "rank",
            "paraId",
            "isChainlinkProvider",
            "supportNft",
            "isUsesAppId",
            "identityChain",
            "remoteAssetsSource"
        )
    ),
    releasedVersion71ChainOrder(
        identityColumns = listOf("id", "parentId", "rank"),
        featureColumnsBeforeUrls = listOf("isEthereumChain"),
        appendedColumns = listOf(
            "paraId",
            "isChainlinkProvider",
            "supportNft",
            "isUsesAppId",
            "identityChain",
            "remoteAssetsSource"
        )
    ),
    releasedVersion71ChainOrder(
        identityColumns = listOf("id", "paraId", "parentId", "rank"),
        featureColumnsBeforeUrls = listOf("isEthereumChain"),
        appendedColumns = listOf(
            "isChainlinkProvider",
            "supportNft",
            "isUsesAppId",
            "identityChain",
            "remoteAssetsSource"
        )
    ),
    releasedVersion71ChainOrder(
        identityColumns = listOf("id", "paraId", "parentId", "rank"),
        featureColumnsBeforeUrls = listOf(
            "isEthereumChain",
            "isChainlinkProvider"
        ),
        appendedColumns = listOf(
            "supportNft",
            "isUsesAppId",
            "identityChain",
            "remoteAssetsSource"
        )
    ),
    releasedVersion71ChainOrder(
        identityColumns = listOf("id", "paraId", "parentId", "rank"),
        featureColumnsBeforeUrls = listOf(
            "isEthereumChain",
            "isChainlinkProvider",
            "supportNft"
        ),
        appendedColumns = listOf(
            "isUsesAppId",
            "identityChain",
            "remoteAssetsSource"
        )
    ),
    releasedVersion71ChainOrder(
        identityColumns = listOf("id", "paraId", "parentId", "rank"),
        featureColumnsBeforeUrls = listOf(
            "isEthereumChain",
            "isChainlinkProvider",
            "supportNft",
            "isUsesAppId"
        ),
        appendedColumns = listOf(
            "identityChain",
            "remoteAssetsSource"
        )
    ),
    releasedVersion71ChainOrder(
        identityColumns = listOf("id", "paraId", "parentId", "rank"),
        featureColumnsBeforeUrls = listOf(
            "isEthereumChain",
            "isChainlinkProvider",
            "supportNft",
            "isUsesAppId",
            "identityChain"
        ),
        appendedColumns = listOf("remoteAssetsSource")
    ),
    releasedVersion71ChainOrder(
        identityColumns = listOf("id", "paraId", "parentId", "rank"),
        featureColumnsBeforeUrls = listOf(
            "isEthereumChain",
            "isChainlinkProvider",
            "supportNft",
            "isUsesAppId",
            "identityChain",
            "remoteAssetsSource"
        ),
        appendedColumns = emptyList()
    )
)

internal val RELEASED_VERSION_71_META_ACCOUNT_COLUMN_ORDERS = listOf(
    listOf(
        "id",
        "substratePublicKey",
        "substrateCryptoType",
        "substrateAccountId",
        "ethereumPublicKey",
        "ethereumAddress",
        "name",
        "isSelected",
        "position",
        "isBackedUp",
        "googleBackupAddress",
        "initialized"
    ),
    listOf(
        "substratePublicKey",
        "substrateCryptoType",
        "substrateAccountId",
        "ethereumPublicKey",
        "ethereumAddress",
        "name",
        "isSelected",
        "position",
        "id",
        "isBackedUp",
        "googleBackupAddress",
        "initialized"
    ),
    listOf(
        "substratePublicKey",
        "substrateCryptoType",
        "substrateAccountId",
        "ethereumPublicKey",
        "ethereumAddress",
        "name",
        "isSelected",
        "position",
        "isBackedUp",
        "googleBackupAddress",
        "id",
        "initialized"
    ),
    listOf(
        "substratePublicKey",
        "substrateCryptoType",
        "substrateAccountId",
        "ethereumPublicKey",
        "ethereumAddress",
        "name",
        "isSelected",
        "position",
        "isBackedUp",
        "googleBackupAddress",
        "initialized",
        "id"
    )
)

private fun releasedVersion71ChainExplicitDefaults(
    cohortIndex: Int
): Map<String, String> = buildMap {
    check(cohortIndex in RELEASED_VERSION_71_CHAIN_COLUMN_ORDERS.indices)
    if (cohortIndex < 1) put("isEthereumChain", "0")
    if (cohortIndex < 4) put("isChainlinkProvider", "0")
    if (cohortIndex < 5) put("supportNft", "0")
    if (cohortIndex < 6) put("isUsesAppId", "0")
    if (cohortIndex < 7) put("identityChain", "NULL")
    if (cohortIndex < 8) put("remoteAssetsSource", "NULL")
}

internal val RELEASED_VERSION_71_CHAIN_COLUMN_COHORTS =
    RELEASED_VERSION_71_CHAIN_COLUMN_ORDERS.mapIndexed { cohortIndex, columnOrder ->
        ReleasedRoomColumnOrderCohort(
            columnNames = columnOrder,
            explicitDefaultValues = releasedVersion71ChainExplicitDefaults(
                cohortIndex
            )
        )
    }

internal val RELEASED_VERSION_71_META_ACCOUNT_COLUMN_COHORTS =
    RELEASED_VERSION_71_META_ACCOUNT_COLUMN_ORDERS.mapIndexed { cohortIndex, columnOrder ->
        val explicitDefaults = when (cohortIndex) {
            0, 1 -> mapOf(
                "isBackedUp" to "0",
                "googleBackupAddress" to "NULL",
                "initialized" to "0"
            )

            2 -> mapOf("initialized" to "0")
            3 -> emptyMap()
            else -> error("Unexpected released meta-account cohort")
        }
        ReleasedRoomColumnOrderCohort(
            columnNames = columnOrder,
            explicitDefaultValues = explicitDefaults
        )
    }

internal val RELEASED_VERSION_76_UPGRADED_CHAIN_COLUMN_ORDERS =
    RELEASED_VERSION_71_CHAIN_COLUMN_ORDERS.map { version71Order ->
        version71Order + listOf(
            "ecosystem",
            "androidMinAppVersion",
            "tonBridgeUrl",
            "xcm"
        )
    }

internal val RELEASED_VERSION_76_UPGRADED_CHAIN_COLUMN_COHORTS =
    RELEASED_VERSION_71_CHAIN_COLUMN_COHORTS.map { version71Cohort ->
        ReleasedRoomColumnOrderCohort(
            columnNames = version71Cohort.columnNames + listOf(
                "ecosystem",
                "androidMinAppVersion",
                "tonBridgeUrl",
                "xcm"
            ),
            explicitDefaultValues =
            version71Cohort.explicitDefaultValues + mapOf(
                "ecosystem" to "'Substrate'",
                "androidMinAppVersion" to "NULL",
                "tonBridgeUrl" to "NULL",
                "xcm" to "NULL"
            )
        )
    }

internal val RELEASED_ROOM_SCHEMA_OBJECTS_V76 = setOf(
    ReleasedRoomSchemaObjectSpec("table", "address_book", "address_book"),
    ReleasedRoomSchemaObjectSpec("table", "sqlite_sequence", "sqlite_sequence"),
    ReleasedRoomSchemaObjectSpec(
        "index",
        "index_address_book_address_chainId",
        "address_book"
    ),
    ReleasedRoomSchemaObjectSpec("table", "assets", "assets"),
    ReleasedRoomSchemaObjectSpec("index", "sqlite_autoindex_assets_1", "assets"),
    ReleasedRoomSchemaObjectSpec("index", "index_assets_chainId", "assets"),
    ReleasedRoomSchemaObjectSpec("index", "index_assets_metaId", "assets"),
    ReleasedRoomSchemaObjectSpec("table", "token_price", "token_price"),
    ReleasedRoomSchemaObjectSpec(
        "index",
        "sqlite_autoindex_token_price_1",
        "token_price"
    ),
    ReleasedRoomSchemaObjectSpec("table", "phishing", "phishing"),
    ReleasedRoomSchemaObjectSpec(
        "index",
        "sqlite_autoindex_phishing_1",
        "phishing"
    ),
    ReleasedRoomSchemaObjectSpec("table", "storage", "storage"),
    ReleasedRoomSchemaObjectSpec(
        "index",
        "sqlite_autoindex_storage_1",
        "storage"
    ),
    ReleasedRoomSchemaObjectSpec(
        "table",
        "account_staking_accesses",
        "account_staking_accesses"
    ),
    ReleasedRoomSchemaObjectSpec(
        "index",
        "sqlite_autoindex_account_staking_accesses_1",
        "account_staking_accesses"
    ),
    ReleasedRoomSchemaObjectSpec("table", "total_reward", "total_reward"),
    ReleasedRoomSchemaObjectSpec(
        "index",
        "sqlite_autoindex_total_reward_1",
        "total_reward"
    ),
    ReleasedRoomSchemaObjectSpec("table", "operations", "operations"),
    ReleasedRoomSchemaObjectSpec(
        "index",
        "sqlite_autoindex_operations_1",
        "operations"
    ),
    ReleasedRoomSchemaObjectSpec("table", "chains", "chains"),
    ReleasedRoomSchemaObjectSpec("index", "sqlite_autoindex_chains_1", "chains"),
    ReleasedRoomSchemaObjectSpec("table", "chain_nodes", "chain_nodes"),
    ReleasedRoomSchemaObjectSpec(
        "index",
        "sqlite_autoindex_chain_nodes_1",
        "chain_nodes"
    ),
    ReleasedRoomSchemaObjectSpec(
        "index",
        "index_chain_nodes_chainId",
        "chain_nodes"
    ),
    ReleasedRoomSchemaObjectSpec("table", "chain_assets", "chain_assets"),
    ReleasedRoomSchemaObjectSpec(
        "index",
        "sqlite_autoindex_chain_assets_1",
        "chain_assets"
    ),
    ReleasedRoomSchemaObjectSpec(
        "index",
        "index_chain_assets_chainId",
        "chain_assets"
    ),
    ReleasedRoomSchemaObjectSpec(
        "table",
        "favorite_chains",
        "favorite_chains"
    ),
    ReleasedRoomSchemaObjectSpec(
        "index",
        "sqlite_autoindex_favorite_chains_1",
        "favorite_chains"
    ),
    ReleasedRoomSchemaObjectSpec("table", "chain_runtimes", "chain_runtimes"),
    ReleasedRoomSchemaObjectSpec(
        "index",
        "sqlite_autoindex_chain_runtimes_1",
        "chain_runtimes"
    ),
    ReleasedRoomSchemaObjectSpec(
        "index",
        "index_chain_runtimes_chainId",
        "chain_runtimes"
    ),
    ReleasedRoomSchemaObjectSpec("table", "meta_accounts", "meta_accounts"),
    ReleasedRoomSchemaObjectSpec(
        "index",
        "index_meta_accounts_substrateAccountId",
        "meta_accounts"
    ),
    ReleasedRoomSchemaObjectSpec(
        "index",
        "index_meta_accounts_ethereumAddress",
        "meta_accounts"
    ),
    ReleasedRoomSchemaObjectSpec(
        "index",
        "index_meta_accounts_tonPublicKey",
        "meta_accounts"
    ),
    ReleasedRoomSchemaObjectSpec("table", "chain_accounts", "chain_accounts"),
    ReleasedRoomSchemaObjectSpec(
        "index",
        "sqlite_autoindex_chain_accounts_1",
        "chain_accounts"
    ),
    ReleasedRoomSchemaObjectSpec(
        "index",
        "index_chain_accounts_chainId",
        "chain_accounts"
    ),
    ReleasedRoomSchemaObjectSpec(
        "index",
        "index_chain_accounts_metaId",
        "chain_accounts"
    ),
    ReleasedRoomSchemaObjectSpec(
        "index",
        "index_chain_accounts_accountId",
        "chain_accounts"
    ),
    ReleasedRoomSchemaObjectSpec(
        "table",
        "chain_explorers",
        "chain_explorers"
    ),
    ReleasedRoomSchemaObjectSpec(
        "index",
        "sqlite_autoindex_chain_explorers_1",
        "chain_explorers"
    ),
    ReleasedRoomSchemaObjectSpec(
        "index",
        "index_chain_explorers_chainId",
        "chain_explorers"
    ),
    ReleasedRoomSchemaObjectSpec("table", "chain_types", "chain_types"),
    ReleasedRoomSchemaObjectSpec(
        "index",
        "sqlite_autoindex_chain_types_1",
        "chain_types"
    ),
    ReleasedRoomSchemaObjectSpec(
        "table",
        "nomis_wallet_score",
        "nomis_wallet_score"
    ),
    ReleasedRoomSchemaObjectSpec("table", "allpools", "allpools"),
    ReleasedRoomSchemaObjectSpec(
        "index",
        "sqlite_autoindex_allpools_1",
        "allpools"
    ),
    ReleasedRoomSchemaObjectSpec("table", "userpools", "userpools"),
    ReleasedRoomSchemaObjectSpec(
        "index",
        "sqlite_autoindex_userpools_1",
        "userpools"
    ),
    ReleasedRoomSchemaObjectSpec(
        "index",
        "index_userpools_accountAddress",
        "userpools"
    ),
    ReleasedRoomSchemaObjectSpec("table", "ton_connection", "ton_connection"),
    ReleasedRoomSchemaObjectSpec(
        "index",
        "sqlite_autoindex_ton_connection_1",
        "ton_connection"
    ),
    ReleasedRoomSchemaObjectSpec(
        "table",
        "room_master_table",
        "room_master_table"
    ),
    ReleasedRoomSchemaObjectSpec(
        "table",
        "android_metadata",
        "android_metadata"
    )
)

private val LEGACY_USERS_RECOVERY_SCHEMA_OBJECTS = setOf(
    ReleasedRoomSchemaObjectSpec(
        "table",
        "legacy_users_recovery",
        "legacy_users_recovery"
    ),
    ReleasedRoomSchemaObjectSpec(
        "index",
        "sqlite_autoindex_legacy_users_recovery_1",
        "legacy_users_recovery"
    )
)

internal val RELEASED_ROOM_SCHEMA_OBJECTS_V76_WITH_LEGACY_USERS_RECOVERY =
    RELEASED_ROOM_SCHEMA_OBJECTS_V76 + LEGACY_USERS_RECOVERY_SCHEMA_OBJECTS

internal val RELEASED_LEGACY_USERS_RECOVERY_TABLE = ReleasedRoomTableSpec(
    name = "legacy_users_recovery",
    columns = listOf(
        ReleasedRoomColumnSpec(
            "address",
            "TEXT",
            notNull = true,
            primaryKeyPosition = 1
        ),
        ReleasedRoomColumnSpec("username", "TEXT", notNull = true),
        ReleasedRoomColumnSpec("publicKey", "TEXT", notNull = true),
        ReleasedRoomColumnSpec("cryptoType", "INTEGER", notNull = true),
        ReleasedRoomColumnSpec("position", "INTEGER", notNull = true),
        ReleasedRoomColumnSpec("recoveryState", "TEXT", notNull = true)
    )
)

/**
 * Frozen from core-db/schemas/jp.co.soramitsu.coredb.AppDatabase/76.json.
 *
 * The additional allowed defaults are exact variants produced by released
 * migrations which used ALTER TABLE. A database created fresh at version 76
 * has no declared defaults for these columns, while an upgraded database
 * retains the released ALTER default in SQLite's table metadata.
 */
internal val RELEASED_ROOM_SCHEMA_V76 = listOf(
    ReleasedRoomTableSpec(
        name = "address_book",
        columns = listOf(
            ReleasedRoomColumnSpec("address", "TEXT", notNull = true),
            ReleasedRoomColumnSpec("name", "TEXT"),
            ReleasedRoomColumnSpec("chainId", "TEXT", notNull = true),
            ReleasedRoomColumnSpec("created", "INTEGER", notNull = true),
            ReleasedRoomColumnSpec(
                "id",
                "INTEGER",
                notNull = true,
                primaryKeyPosition = 1
            )
        ),
        alternateColumnLayouts = listOf(
            // Migration 44 -> 45 created the generated id first and later
            // migrations never rebuilt this table.
            listOf(
                ReleasedRoomColumnSpec(
                    "id",
                    "INTEGER",
                    notNull = true,
                    primaryKeyPosition = 1
                ),
                ReleasedRoomColumnSpec(
                    "address",
                    "TEXT",
                    notNull = true
                ),
                ReleasedRoomColumnSpec("name", "TEXT"),
                ReleasedRoomColumnSpec(
                    "chainId",
                    "TEXT",
                    notNull = true
                ),
                ReleasedRoomColumnSpec(
                    "created",
                    "INTEGER",
                    notNull = true
                )
            )
        ),
        indexes = setOf(
            ReleasedRoomIndexSpec(
                name = "index_address_book_address_chainId",
                unique = true,
                columns = listOf("address", "chainId")
            )
        )
    ),
    ReleasedRoomTableSpec(
        name = "assets",
        columns = listOf(
            ReleasedRoomColumnSpec(
                "id",
                "TEXT",
                notNull = true,
                primaryKeyPosition = 1
            ),
            ReleasedRoomColumnSpec(
                "chainId",
                "TEXT",
                notNull = true,
                primaryKeyPosition = 2
            ),
            ReleasedRoomColumnSpec(
                "accountId",
                "BLOB",
                notNull = true,
                primaryKeyPosition = 3
            ),
            ReleasedRoomColumnSpec(
                "metaId",
                "INTEGER",
                notNull = true,
                primaryKeyPosition = 4
            ),
            ReleasedRoomColumnSpec("tokenPriceId", "TEXT"),
            ReleasedRoomColumnSpec("freeInPlanks", "TEXT"),
            ReleasedRoomColumnSpec("reservedInPlanks", "TEXT"),
            ReleasedRoomColumnSpec("miscFrozenInPlanks", "TEXT"),
            ReleasedRoomColumnSpec("feeFrozenInPlanks", "TEXT"),
            ReleasedRoomColumnSpec("bondedInPlanks", "TEXT"),
            ReleasedRoomColumnSpec("redeemableInPlanks", "TEXT"),
            ReleasedRoomColumnSpec("unbondingInPlanks", "TEXT"),
            ReleasedRoomColumnSpec(
                "sortIndex",
                "INTEGER",
                notNull = true,
                allowedDefaultValues = ABSENT_OR_ZERO_DEFAULT
            ),
            ReleasedRoomColumnSpec(
                "enabled",
                "INTEGER",
                allowedDefaultValues = ABSENT_OR_SQL_NULL_DEFAULT
            ),
            ReleasedRoomColumnSpec(
                "markedNotNeed",
                "INTEGER",
                notNull = true,
                allowedDefaultValues = ABSENT_OR_ZERO_DEFAULT
            ),
            ReleasedRoomColumnSpec("chainAccountName", "TEXT"),
            ReleasedRoomColumnSpec("status", "TEXT")
        ),
        indexes = setOf(
            ReleasedRoomIndexSpec(
                name = "index_assets_chainId",
                columns = listOf("chainId")
            ),
            ReleasedRoomIndexSpec(
                name = "index_assets_metaId",
                columns = listOf("metaId")
            )
        )
    ),
    ReleasedRoomTableSpec(
        name = "token_price",
        columns = listOf(
            ReleasedRoomColumnSpec(
                "priceId",
                "TEXT",
                notNull = true,
                primaryKeyPosition = 1
            ),
            ReleasedRoomColumnSpec("fiatSymbol", "TEXT", notNull = true),
            ReleasedRoomColumnSpec("fiatRate", "TEXT"),
            ReleasedRoomColumnSpec("recentRateChange", "TEXT")
        )
    ),
    ReleasedRoomTableSpec(
        name = "phishing",
        columns = listOf(
            ReleasedRoomColumnSpec(
                "address",
                "TEXT",
                notNull = true,
                primaryKeyPosition = 1
            ),
            ReleasedRoomColumnSpec("name", "TEXT"),
            ReleasedRoomColumnSpec(
                "type",
                "TEXT",
                notNull = true,
                primaryKeyPosition = 2
            ),
            ReleasedRoomColumnSpec("subtype", "TEXT")
        )
    ),
    ReleasedRoomTableSpec(
        name = "storage",
        columns = listOf(
            ReleasedRoomColumnSpec(
                "storageKey",
                "TEXT",
                notNull = true,
                primaryKeyPosition = 2
            ),
            ReleasedRoomColumnSpec("content", "TEXT"),
            ReleasedRoomColumnSpec(
                "chainId",
                "TEXT",
                notNull = true,
                primaryKeyPosition = 1
            )
        )
    ),
    ReleasedRoomTableSpec(
        name = "account_staking_accesses",
        columns = listOf(
            ReleasedRoomColumnSpec(
                "chainId",
                "TEXT",
                notNull = true,
                primaryKeyPosition = 1
            ),
            ReleasedRoomColumnSpec(
                "chainAssetId",
                "TEXT",
                notNull = true,
                primaryKeyPosition = 2
            ),
            ReleasedRoomColumnSpec(
                "accountId",
                "BLOB",
                notNull = true,
                primaryKeyPosition = 3
            ),
            ReleasedRoomColumnSpec("stashId", "BLOB"),
            ReleasedRoomColumnSpec("controllerId", "BLOB")
        )
    ),
    ReleasedRoomTableSpec(
        name = "total_reward",
        columns = listOf(
            ReleasedRoomColumnSpec(
                "accountAddress",
                "TEXT",
                notNull = true,
                primaryKeyPosition = 1
            ),
            ReleasedRoomColumnSpec("totalReward", "TEXT", notNull = true)
        )
    ),
    ReleasedRoomTableSpec(
        name = "operations",
        columns = listOf(
            ReleasedRoomColumnSpec(
                "id",
                "TEXT",
                notNull = true,
                primaryKeyPosition = 1
            ),
            ReleasedRoomColumnSpec(
                "address",
                "TEXT",
                notNull = true,
                primaryKeyPosition = 2
            ),
            ReleasedRoomColumnSpec(
                "chainId",
                "TEXT",
                notNull = true,
                primaryKeyPosition = 3
            ),
            ReleasedRoomColumnSpec(
                "chainAssetId",
                "TEXT",
                notNull = true,
                primaryKeyPosition = 4
            ),
            ReleasedRoomColumnSpec("time", "INTEGER", notNull = true),
            ReleasedRoomColumnSpec("status", "INTEGER", notNull = true),
            ReleasedRoomColumnSpec("source", "INTEGER", notNull = true),
            ReleasedRoomColumnSpec(
                "operationType",
                "INTEGER",
                notNull = true
            ),
            ReleasedRoomColumnSpec("module", "TEXT"),
            ReleasedRoomColumnSpec("call", "TEXT"),
            ReleasedRoomColumnSpec("amount", "TEXT"),
            ReleasedRoomColumnSpec("sender", "TEXT"),
            ReleasedRoomColumnSpec("receiver", "TEXT"),
            ReleasedRoomColumnSpec("hash", "TEXT"),
            ReleasedRoomColumnSpec("fee", "TEXT"),
            ReleasedRoomColumnSpec("isReward", "INTEGER"),
            ReleasedRoomColumnSpec("era", "INTEGER"),
            ReleasedRoomColumnSpec("validator", "TEXT"),
            ReleasedRoomColumnSpec(
                "liquidityFee",
                "TEXT",
                allowedDefaultValues = ABSENT_OR_SQL_NULL_DEFAULT
            ),
            ReleasedRoomColumnSpec(
                "market",
                "TEXT",
                allowedDefaultValues = ABSENT_OR_SQL_NULL_DEFAULT
            ),
            ReleasedRoomColumnSpec(
                "targetAssetId",
                "TEXT",
                allowedDefaultValues = ABSENT_OR_SQL_NULL_DEFAULT
            ),
            ReleasedRoomColumnSpec(
                "targetAmount",
                "TEXT",
                allowedDefaultValues = ABSENT_OR_SQL_NULL_DEFAULT
            )
        )
    ),
    ReleasedRoomTableSpec(
        name = "chains",
        columns = listOf(
            ReleasedRoomColumnSpec(
                "id",
                "TEXT",
                notNull = true,
                primaryKeyPosition = 1
            ),
            ReleasedRoomColumnSpec("paraId", "TEXT"),
            ReleasedRoomColumnSpec("parentId", "TEXT"),
            ReleasedRoomColumnSpec("rank", "INTEGER"),
            ReleasedRoomColumnSpec("name", "TEXT", notNull = true),
            ReleasedRoomColumnSpec("minSupportedVersion", "TEXT"),
            ReleasedRoomColumnSpec("icon", "TEXT", notNull = true),
            ReleasedRoomColumnSpec("prefix", "INTEGER", notNull = true),
            ReleasedRoomColumnSpec(
                "isEthereumBased",
                "INTEGER",
                notNull = true
            ),
            ReleasedRoomColumnSpec("isTestNet", "INTEGER", notNull = true),
            ReleasedRoomColumnSpec(
                "hasCrowdloans",
                "INTEGER",
                notNull = true
            ),
            ReleasedRoomColumnSpec(
                "supportStakingPool",
                "INTEGER",
                notNull = true
            ),
            ReleasedRoomColumnSpec(
                "isEthereumChain",
                "INTEGER",
                notNull = true
            ),
            ReleasedRoomColumnSpec(
                "isChainlinkProvider",
                "INTEGER",
                notNull = true
            ),
            ReleasedRoomColumnSpec(
                "supportNft",
                "INTEGER",
                notNull = true
            ),
            ReleasedRoomColumnSpec(
                "isUsesAppId",
                "INTEGER",
                notNull = true
            ),
            ReleasedRoomColumnSpec("identityChain", "TEXT"),
            ReleasedRoomColumnSpec(
                "ecosystem",
                "TEXT",
                notNull = true
            ),
            ReleasedRoomColumnSpec("androidMinAppVersion", "TEXT"),
            ReleasedRoomColumnSpec("remoteAssetsSource", "TEXT"),
            ReleasedRoomColumnSpec("tonBridgeUrl", "TEXT"),
            ReleasedRoomColumnSpec("xcm", "TEXT"),
            ReleasedRoomColumnSpec("staking_url", "TEXT"),
            ReleasedRoomColumnSpec("staking_type", "TEXT"),
            ReleasedRoomColumnSpec("history_url", "TEXT"),
            ReleasedRoomColumnSpec("history_type", "TEXT"),
            ReleasedRoomColumnSpec("crowdloans_url", "TEXT"),
            ReleasedRoomColumnSpec("crowdloans_type", "TEXT")
        ),
        alternateColumnLayouts = listOf(
            // Fresh 73/74/75 databases with 75 -> 76 appending xcm.
            listOf(
                ReleasedRoomColumnSpec(
                    "id",
                    "TEXT",
                    notNull = true,
                    primaryKeyPosition = 1
                ),
                ReleasedRoomColumnSpec("paraId", "TEXT"),
                ReleasedRoomColumnSpec("parentId", "TEXT"),
                ReleasedRoomColumnSpec("rank", "INTEGER"),
                ReleasedRoomColumnSpec("name", "TEXT", notNull = true),
                ReleasedRoomColumnSpec("minSupportedVersion", "TEXT"),
                ReleasedRoomColumnSpec("icon", "TEXT", notNull = true),
                ReleasedRoomColumnSpec("prefix", "INTEGER", notNull = true),
                ReleasedRoomColumnSpec(
                    "isEthereumBased",
                    "INTEGER",
                    notNull = true
                ),
                ReleasedRoomColumnSpec(
                    "isTestNet",
                    "INTEGER",
                    notNull = true
                ),
                ReleasedRoomColumnSpec(
                    "hasCrowdloans",
                    "INTEGER",
                    notNull = true
                ),
                ReleasedRoomColumnSpec(
                    "supportStakingPool",
                    "INTEGER",
                    notNull = true
                ),
                ReleasedRoomColumnSpec(
                    "isEthereumChain",
                    "INTEGER",
                    notNull = true
                ),
                ReleasedRoomColumnSpec(
                    "isChainlinkProvider",
                    "INTEGER",
                    notNull = true
                ),
                ReleasedRoomColumnSpec(
                    "supportNft",
                    "INTEGER",
                    notNull = true
                ),
                ReleasedRoomColumnSpec(
                    "isUsesAppId",
                    "INTEGER",
                    notNull = true
                ),
                ReleasedRoomColumnSpec("identityChain", "TEXT"),
                ReleasedRoomColumnSpec(
                    "ecosystem",
                    "TEXT",
                    notNull = true
                ),
                ReleasedRoomColumnSpec("androidMinAppVersion", "TEXT"),
                ReleasedRoomColumnSpec("remoteAssetsSource", "TEXT"),
                ReleasedRoomColumnSpec("tonBridgeUrl", "TEXT"),
                ReleasedRoomColumnSpec("staking_url", "TEXT"),
                ReleasedRoomColumnSpec("staking_type", "TEXT"),
                ReleasedRoomColumnSpec("history_url", "TEXT"),
                ReleasedRoomColumnSpec("history_type", "TEXT"),
                ReleasedRoomColumnSpec("crowdloans_url", "TEXT"),
                ReleasedRoomColumnSpec("crowdloans_type", "TEXT"),
                ReleasedRoomColumnSpec(
                    "xcm",
                    "TEXT",
                    allowedDefaultValues = SQL_NULL_DEFAULT
                )
            ),
            // A fresh 72 database with 72 -> 73 and 75 -> 76 appends.
            listOf(
                ReleasedRoomColumnSpec(
                    "id",
                    "TEXT",
                    notNull = true,
                    primaryKeyPosition = 1
                ),
                ReleasedRoomColumnSpec("paraId", "TEXT"),
                ReleasedRoomColumnSpec("parentId", "TEXT"),
                ReleasedRoomColumnSpec("rank", "INTEGER"),
                ReleasedRoomColumnSpec("name", "TEXT", notNull = true),
                ReleasedRoomColumnSpec("minSupportedVersion", "TEXT"),
                ReleasedRoomColumnSpec("icon", "TEXT", notNull = true),
                ReleasedRoomColumnSpec("prefix", "INTEGER", notNull = true),
                ReleasedRoomColumnSpec(
                    "isEthereumBased",
                    "INTEGER",
                    notNull = true
                ),
                ReleasedRoomColumnSpec(
                    "isTestNet",
                    "INTEGER",
                    notNull = true
                ),
                ReleasedRoomColumnSpec(
                    "hasCrowdloans",
                    "INTEGER",
                    notNull = true
                ),
                ReleasedRoomColumnSpec(
                    "supportStakingPool",
                    "INTEGER",
                    notNull = true
                ),
                ReleasedRoomColumnSpec(
                    "isEthereumChain",
                    "INTEGER",
                    notNull = true
                ),
                ReleasedRoomColumnSpec(
                    "isChainlinkProvider",
                    "INTEGER",
                    notNull = true
                ),
                ReleasedRoomColumnSpec(
                    "supportNft",
                    "INTEGER",
                    notNull = true
                ),
                ReleasedRoomColumnSpec(
                    "isUsesAppId",
                    "INTEGER",
                    notNull = true
                ),
                ReleasedRoomColumnSpec("identityChain", "TEXT"),
                ReleasedRoomColumnSpec(
                    "ecosystem",
                    "TEXT",
                    notNull = true
                ),
                ReleasedRoomColumnSpec("androidMinAppVersion", "TEXT"),
                ReleasedRoomColumnSpec("remoteAssetsSource", "TEXT"),
                ReleasedRoomColumnSpec("staking_url", "TEXT"),
                ReleasedRoomColumnSpec("staking_type", "TEXT"),
                ReleasedRoomColumnSpec("history_url", "TEXT"),
                ReleasedRoomColumnSpec("history_type", "TEXT"),
                ReleasedRoomColumnSpec("crowdloans_url", "TEXT"),
                ReleasedRoomColumnSpec("crowdloans_type", "TEXT"),
                ReleasedRoomColumnSpec(
                    "tonBridgeUrl",
                    "TEXT",
                    allowedDefaultValues = SQL_NULL_DEFAULT
                ),
                ReleasedRoomColumnSpec(
                    "xcm",
                    "TEXT",
                    allowedDefaultValues = SQL_NULL_DEFAULT
                )
            ),
            // The released 71 layout with all later fields ALTER-appended.
            listOf(
                ReleasedRoomColumnSpec(
                    "id",
                    "TEXT",
                    notNull = true,
                    primaryKeyPosition = 1
                ),
                ReleasedRoomColumnSpec("paraId", "TEXT"),
                ReleasedRoomColumnSpec("parentId", "TEXT"),
                ReleasedRoomColumnSpec("rank", "INTEGER"),
                ReleasedRoomColumnSpec("name", "TEXT", notNull = true),
                ReleasedRoomColumnSpec("minSupportedVersion", "TEXT"),
                ReleasedRoomColumnSpec("icon", "TEXT", notNull = true),
                ReleasedRoomColumnSpec("prefix", "INTEGER", notNull = true),
                ReleasedRoomColumnSpec(
                    "isEthereumBased",
                    "INTEGER",
                    notNull = true
                ),
                ReleasedRoomColumnSpec(
                    "isTestNet",
                    "INTEGER",
                    notNull = true
                ),
                ReleasedRoomColumnSpec(
                    "hasCrowdloans",
                    "INTEGER",
                    notNull = true
                ),
                ReleasedRoomColumnSpec(
                    "supportStakingPool",
                    "INTEGER",
                    notNull = true
                ),
                ReleasedRoomColumnSpec(
                    "isEthereumChain",
                    "INTEGER",
                    notNull = true
                ),
                ReleasedRoomColumnSpec(
                    "isChainlinkProvider",
                    "INTEGER",
                    notNull = true
                ),
                ReleasedRoomColumnSpec(
                    "supportNft",
                    "INTEGER",
                    notNull = true
                ),
                ReleasedRoomColumnSpec(
                    "isUsesAppId",
                    "INTEGER",
                    notNull = true
                ),
                ReleasedRoomColumnSpec("identityChain", "TEXT"),
                ReleasedRoomColumnSpec("remoteAssetsSource", "TEXT"),
                ReleasedRoomColumnSpec("staking_url", "TEXT"),
                ReleasedRoomColumnSpec("staking_type", "TEXT"),
                ReleasedRoomColumnSpec("history_url", "TEXT"),
                ReleasedRoomColumnSpec("history_type", "TEXT"),
                ReleasedRoomColumnSpec("crowdloans_url", "TEXT"),
                ReleasedRoomColumnSpec("crowdloans_type", "TEXT"),
                ReleasedRoomColumnSpec(
                    "ecosystem",
                    "TEXT",
                    notNull = true,
                    allowedDefaultValues = SUBSTRATE_DEFAULT
                ),
                ReleasedRoomColumnSpec(
                    "androidMinAppVersion",
                    "TEXT",
                    allowedDefaultValues = SQL_NULL_DEFAULT
                ),
                ReleasedRoomColumnSpec(
                    "tonBridgeUrl",
                    "TEXT",
                    allowedDefaultValues = SQL_NULL_DEFAULT
                ),
                ReleasedRoomColumnSpec(
                    "xcm",
                    "TEXT",
                    allowedDefaultValues = SQL_NULL_DEFAULT
                )
            ),
            // Databases upgraded from v53 or earlier keep the v53 chains
            // rebuild order. The v57-v76 fields are ALTER-appended.
            listOf(
                ReleasedRoomColumnSpec(
                    "id",
                    "TEXT",
                    notNull = true,
                    primaryKeyPosition = 1
                ),
                ReleasedRoomColumnSpec("parentId", "TEXT"),
                ReleasedRoomColumnSpec("name", "TEXT", notNull = true),
                ReleasedRoomColumnSpec("minSupportedVersion", "TEXT"),
                ReleasedRoomColumnSpec("icon", "TEXT", notNull = true),
                ReleasedRoomColumnSpec(
                    "prefix",
                    "INTEGER",
                    notNull = true
                ),
                ReleasedRoomColumnSpec(
                    "isEthereumBased",
                    "INTEGER",
                    notNull = true
                ),
                ReleasedRoomColumnSpec(
                    "isTestNet",
                    "INTEGER",
                    notNull = true
                ),
                ReleasedRoomColumnSpec(
                    "hasCrowdloans",
                    "INTEGER",
                    notNull = true
                ),
                ReleasedRoomColumnSpec(
                    "supportStakingPool",
                    "INTEGER",
                    notNull = true
                ),
                ReleasedRoomColumnSpec("staking_url", "TEXT"),
                ReleasedRoomColumnSpec("staking_type", "TEXT"),
                ReleasedRoomColumnSpec("history_url", "TEXT"),
                ReleasedRoomColumnSpec("history_type", "TEXT"),
                ReleasedRoomColumnSpec("crowdloans_url", "TEXT"),
                ReleasedRoomColumnSpec("crowdloans_type", "TEXT"),
                ReleasedRoomColumnSpec(
                    "isEthereumChain",
                    "INTEGER",
                    notNull = true,
                    allowedDefaultValues = ZERO_DEFAULT
                ),
                ReleasedRoomColumnSpec("rank", "INTEGER"),
                ReleasedRoomColumnSpec("paraId", "TEXT"),
                ReleasedRoomColumnSpec(
                    "isChainlinkProvider",
                    "INTEGER",
                    notNull = true,
                    allowedDefaultValues = ZERO_DEFAULT
                ),
                ReleasedRoomColumnSpec(
                    "supportNft",
                    "INTEGER",
                    notNull = true,
                    allowedDefaultValues = ZERO_DEFAULT
                ),
                ReleasedRoomColumnSpec(
                    "isUsesAppId",
                    "INTEGER",
                    notNull = true,
                    allowedDefaultValues = ZERO_DEFAULT
                ),
                ReleasedRoomColumnSpec(
                    "identityChain",
                    "TEXT",
                    allowedDefaultValues = SQL_NULL_DEFAULT
                ),
                ReleasedRoomColumnSpec(
                    "remoteAssetsSource",
                    "TEXT",
                    allowedDefaultValues = SQL_NULL_DEFAULT
                ),
                ReleasedRoomColumnSpec(
                    "ecosystem",
                    "TEXT",
                    notNull = true,
                    allowedDefaultValues = SUBSTRATE_DEFAULT
                ),
                ReleasedRoomColumnSpec(
                    "androidMinAppVersion",
                    "TEXT",
                    allowedDefaultValues = SQL_NULL_DEFAULT
                ),
                ReleasedRoomColumnSpec(
                    "tonBridgeUrl",
                    "TEXT",
                    allowedDefaultValues = SQL_NULL_DEFAULT
                ),
                ReleasedRoomColumnSpec(
                    "xcm",
                    "TEXT",
                    allowedDefaultValues = SQL_NULL_DEFAULT
                )
            )
        ),
        alternateColumnOrderCohorts =
        RELEASED_VERSION_76_UPGRADED_CHAIN_COLUMN_COHORTS
    ),
    ReleasedRoomTableSpec(
        name = "chain_nodes",
        columns = listOf(
            ReleasedRoomColumnSpec(
                "chainId",
                "TEXT",
                notNull = true,
                primaryKeyPosition = 1
            ),
            ReleasedRoomColumnSpec(
                "url",
                "TEXT",
                notNull = true,
                primaryKeyPosition = 2
            ),
            ReleasedRoomColumnSpec("name", "TEXT", notNull = true),
            ReleasedRoomColumnSpec("isActive", "INTEGER", notNull = true),
            ReleasedRoomColumnSpec("isDefault", "INTEGER", notNull = true)
        ),
        indexes = setOf(
            ReleasedRoomIndexSpec(
                name = "index_chain_nodes_chainId",
                columns = listOf("chainId")
            )
        )
    ),
    ReleasedRoomTableSpec(
        name = "chain_assets",
        columns = listOf(
            ReleasedRoomColumnSpec(
                "id",
                "TEXT",
                notNull = true,
                primaryKeyPosition = 2
            ),
            ReleasedRoomColumnSpec("name", "TEXT"),
            ReleasedRoomColumnSpec("symbol", "TEXT", notNull = true),
            ReleasedRoomColumnSpec(
                "chainId",
                "TEXT",
                notNull = true,
                primaryKeyPosition = 1
            ),
            ReleasedRoomColumnSpec("icon", "TEXT", notNull = true),
            ReleasedRoomColumnSpec("priceId", "TEXT"),
            ReleasedRoomColumnSpec("staking", "TEXT", notNull = true),
            ReleasedRoomColumnSpec("precision", "INTEGER", notNull = true),
            ReleasedRoomColumnSpec("purchaseProviders", "TEXT"),
            ReleasedRoomColumnSpec("isUtility", "INTEGER"),
            ReleasedRoomColumnSpec("type", "TEXT"),
            ReleasedRoomColumnSpec("currencyId", "TEXT"),
            ReleasedRoomColumnSpec("existentialDeposit", "TEXT"),
            ReleasedRoomColumnSpec("color", "TEXT"),
            ReleasedRoomColumnSpec("isNative", "INTEGER"),
            ReleasedRoomColumnSpec("priceProvider", "TEXT"),
            ReleasedRoomColumnSpec(
                "coinbaseUrl",
                "TEXT",
                allowedDefaultValues = ABSENT_OR_SQL_NULL_DEFAULT
            )
        ),
        indexes = setOf(
            ReleasedRoomIndexSpec(
                name = "index_chain_assets_chainId",
                columns = listOf("chainId")
            )
        )
    ),
    ReleasedRoomTableSpec(
        name = "favorite_chains",
        columns = listOf(
            ReleasedRoomColumnSpec(
                "metaId",
                "INTEGER",
                notNull = true,
                primaryKeyPosition = 1
            ),
            ReleasedRoomColumnSpec(
                "chainId",
                "TEXT",
                notNull = true,
                primaryKeyPosition = 2
            ),
            ReleasedRoomColumnSpec(
                "isFavorite",
                "INTEGER",
                notNull = true,
                allowedDefaultValues = ABSENT_OR_ZERO_DEFAULT
            )
        )
    ),
    ReleasedRoomTableSpec(
        name = "chain_runtimes",
        columns = listOf(
            ReleasedRoomColumnSpec(
                "chainId",
                "TEXT",
                notNull = true,
                primaryKeyPosition = 1
            ),
            ReleasedRoomColumnSpec(
                "syncedVersion",
                "INTEGER",
                notNull = true
            ),
            ReleasedRoomColumnSpec(
                "remoteVersion",
                "INTEGER",
                notNull = true
            )
        ),
        indexes = setOf(
            ReleasedRoomIndexSpec(
                name = "index_chain_runtimes_chainId",
                columns = listOf("chainId")
            )
        )
    ),
    ReleasedRoomTableSpec(
        name = "meta_accounts",
        columns = listOf(
            ReleasedRoomColumnSpec("substratePublicKey", "BLOB"),
            ReleasedRoomColumnSpec("substrateCryptoType", "TEXT"),
            ReleasedRoomColumnSpec("substrateAccountId", "BLOB"),
            ReleasedRoomColumnSpec("ethereumPublicKey", "BLOB"),
            ReleasedRoomColumnSpec("ethereumAddress", "BLOB"),
            ReleasedRoomColumnSpec("tonPublicKey", "BLOB"),
            ReleasedRoomColumnSpec("name", "TEXT", notNull = true),
            ReleasedRoomColumnSpec("isSelected", "INTEGER", notNull = true),
            ReleasedRoomColumnSpec("position", "INTEGER", notNull = true),
            ReleasedRoomColumnSpec("isBackedUp", "INTEGER", notNull = true),
            ReleasedRoomColumnSpec("googleBackupAddress", "TEXT"),
            ReleasedRoomColumnSpec("initialized", "INTEGER", notNull = true),
            ReleasedRoomColumnSpec(
                "id",
                "INTEGER",
                notNull = true,
                primaryKeyPosition = 1
            )
        ),
        alternateColumnLayouts = listOf(
            // Ton 71 -> 72 recreates this table with the row-id first.
            listOf(
                ReleasedRoomColumnSpec(
                    "id",
                    "INTEGER",
                    notNull = true,
                    primaryKeyPosition = 1
                ),
                ReleasedRoomColumnSpec("substratePublicKey", "BLOB"),
                ReleasedRoomColumnSpec("substrateCryptoType", "TEXT"),
                ReleasedRoomColumnSpec("substrateAccountId", "BLOB"),
                ReleasedRoomColumnSpec("ethereumPublicKey", "BLOB"),
                ReleasedRoomColumnSpec("ethereumAddress", "BLOB"),
                ReleasedRoomColumnSpec("tonPublicKey", "BLOB"),
                ReleasedRoomColumnSpec("name", "TEXT", notNull = true),
                ReleasedRoomColumnSpec(
                    "isSelected",
                    "INTEGER",
                    notNull = true
                ),
                ReleasedRoomColumnSpec("position", "INTEGER", notNull = true),
                ReleasedRoomColumnSpec("isBackedUp", "INTEGER", notNull = true),
                ReleasedRoomColumnSpec("googleBackupAddress", "TEXT"),
                ReleasedRoomColumnSpec(
                    "initialized",
                    "INTEGER",
                    notNull = true
                )
            )
        ),
        indexes = setOf(
            ReleasedRoomIndexSpec(
                name = "index_meta_accounts_substrateAccountId",
                columns = listOf("substrateAccountId")
            ),
            ReleasedRoomIndexSpec(
                name = "index_meta_accounts_ethereumAddress",
                columns = listOf("ethereumAddress")
            ),
            ReleasedRoomIndexSpec(
                name = "index_meta_accounts_tonPublicKey",
                columns = listOf("tonPublicKey")
            )
        )
    ),
    ReleasedRoomTableSpec(
        name = "chain_accounts",
        columns = listOf(
            ReleasedRoomColumnSpec(
                "metaId",
                "INTEGER",
                notNull = true,
                primaryKeyPosition = 1
            ),
            ReleasedRoomColumnSpec(
                "chainId",
                "TEXT",
                notNull = true,
                primaryKeyPosition = 2
            ),
            ReleasedRoomColumnSpec("publicKey", "BLOB", notNull = true),
            ReleasedRoomColumnSpec("accountId", "BLOB", notNull = true),
            ReleasedRoomColumnSpec("cryptoType", "TEXT", notNull = true),
            ReleasedRoomColumnSpec("name", "TEXT", notNull = true),
            ReleasedRoomColumnSpec(
                "initialized",
                "INTEGER",
                notNull = true,
                allowedDefaultValues = ABSENT_OR_ZERO_DEFAULT
            )
        ),
        indexes = setOf(
            ReleasedRoomIndexSpec(
                name = "index_chain_accounts_chainId",
                columns = listOf("chainId")
            ),
            ReleasedRoomIndexSpec(
                name = "index_chain_accounts_metaId",
                columns = listOf("metaId")
            ),
            ReleasedRoomIndexSpec(
                name = "index_chain_accounts_accountId",
                columns = listOf("accountId")
            )
        )
    ),
    ReleasedRoomTableSpec(
        name = "chain_explorers",
        columns = listOf(
            ReleasedRoomColumnSpec(
                "chainId",
                "TEXT",
                notNull = true,
                primaryKeyPosition = 1
            ),
            ReleasedRoomColumnSpec(
                "type",
                "TEXT",
                notNull = true,
                primaryKeyPosition = 2
            ),
            ReleasedRoomColumnSpec("types", "TEXT", notNull = true),
            ReleasedRoomColumnSpec("url", "TEXT", notNull = true)
        ),
        indexes = setOf(
            ReleasedRoomIndexSpec(
                name = "index_chain_explorers_chainId",
                columns = listOf("chainId")
            )
        )
    ),
    ReleasedRoomTableSpec(
        name = "chain_types",
        columns = listOf(
            ReleasedRoomColumnSpec(
                "chainId",
                "TEXT",
                notNull = true,
                primaryKeyPosition = 1
            ),
            ReleasedRoomColumnSpec("typesConfig", "TEXT", notNull = true)
        )
    ),
    ReleasedRoomTableSpec(
        name = "nomis_wallet_score",
        columns = listOf(
            ReleasedRoomColumnSpec(
                "metaId",
                "INTEGER",
                notNull = true,
                primaryKeyPosition = 1
            ),
            ReleasedRoomColumnSpec("score", "INTEGER", notNull = true),
            ReleasedRoomColumnSpec("updated", "INTEGER", notNull = true),
            ReleasedRoomColumnSpec(
                "nativeBalanceUsd",
                "TEXT",
                notNull = true
            ),
            ReleasedRoomColumnSpec("holdTokensUsd", "TEXT", notNull = true),
            ReleasedRoomColumnSpec(
                "walletAgeInMonths",
                "INTEGER",
                notNull = true
            ),
            ReleasedRoomColumnSpec(
                "totalTransactions",
                "INTEGER",
                notNull = true
            ),
            ReleasedRoomColumnSpec(
                "rejectedTransactions",
                "INTEGER",
                notNull = true
            ),
            ReleasedRoomColumnSpec(
                "avgTransactionTimeInHours",
                "REAL",
                notNull = true
            ),
            ReleasedRoomColumnSpec(
                "maxTransactionTimeInHours",
                "REAL",
                notNull = true
            ),
            ReleasedRoomColumnSpec(
                "minTransactionTimeInHours",
                "REAL",
                notNull = true
            ),
            ReleasedRoomColumnSpec("scoredAt", "TEXT", notNull = true)
        )
    ),
    ReleasedRoomTableSpec(
        name = "allpools",
        columns = listOf(
            ReleasedRoomColumnSpec(
                "tokenIdBase",
                "TEXT",
                notNull = true,
                primaryKeyPosition = 1
            ),
            ReleasedRoomColumnSpec(
                "tokenIdTarget",
                "TEXT",
                notNull = true,
                primaryKeyPosition = 2
            ),
            ReleasedRoomColumnSpec("reserveBase", "TEXT", notNull = true),
            ReleasedRoomColumnSpec("reserveTarget", "TEXT", notNull = true),
            ReleasedRoomColumnSpec("totalIssuance", "TEXT", notNull = true),
            ReleasedRoomColumnSpec("reservesAccount", "TEXT", notNull = true)
        )
    ),
    ReleasedRoomTableSpec(
        name = "userpools",
        columns = listOf(
            ReleasedRoomColumnSpec(
                "userTokenIdBase",
                "TEXT",
                notNull = true,
                primaryKeyPosition = 1
            ),
            ReleasedRoomColumnSpec(
                "userTokenIdTarget",
                "TEXT",
                notNull = true,
                primaryKeyPosition = 2
            ),
            ReleasedRoomColumnSpec(
                "accountAddress",
                "TEXT",
                notNull = true,
                primaryKeyPosition = 3
            ),
            ReleasedRoomColumnSpec(
                "poolProvidersBalance",
                "TEXT",
                notNull = true
            )
        ),
        indexes = setOf(
            ReleasedRoomIndexSpec(
                name = "index_userpools_accountAddress",
                columns = listOf("accountAddress")
            )
        )
    ),
    ReleasedRoomTableSpec(
        name = "ton_connection",
        columns = listOf(
            ReleasedRoomColumnSpec(
                "metaId",
                "INTEGER",
                notNull = true,
                primaryKeyPosition = 1
            ),
            ReleasedRoomColumnSpec("clientId", "TEXT", notNull = true),
            ReleasedRoomColumnSpec("name", "TEXT", notNull = true),
            ReleasedRoomColumnSpec("icon", "TEXT", notNull = true),
            ReleasedRoomColumnSpec(
                "url",
                "TEXT",
                notNull = true,
                primaryKeyPosition = 2
            ),
            ReleasedRoomColumnSpec(
                "source",
                "TEXT",
                notNull = true,
                primaryKeyPosition = 3
            )
        )
    ),
    ReleasedRoomTableSpec(
        name = "room_master_table",
        columns = listOf(
            ReleasedRoomColumnSpec(
                "id",
                "INTEGER",
                primaryKeyPosition = 1
            ),
            ReleasedRoomColumnSpec("identity_hash", "TEXT")
        )
    )
)

internal val RELEASED_ROOM_SCHEMA_V76_WITH_LEGACY_USERS_RECOVERY =
    RELEASED_ROOM_SCHEMA_V76 + RELEASED_LEGACY_USERS_RECOVERY_TABLE
