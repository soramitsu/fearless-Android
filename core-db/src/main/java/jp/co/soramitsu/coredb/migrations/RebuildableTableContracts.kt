package jp.co.soramitsu.coredb.migrations

/**
 * Room's own DDL for a table, with `${TABLE_NAME}` where Room puts the name.
 */
internal data class RebuildDdl(
    val createSql: String,
    val indexSql: List<String>
) {
    fun createSql(tableName: String): String =
        createSql.replace(ROOM_TABLE_NAME_PLACEHOLDER, tableName)

    fun indexSql(tableName: String): List<String> =
        indexSql.map { it.replace(ROOM_TABLE_NAME_PLACEHOLDER, tableName) }
}

/** What a rebuildable table holds, which decides what a rebuild carries over. */
internal enum class RebuildableRows {
    /** Network and registry data the app fetches again. */
    CACHE,

    /** The user's choices (asset visibility and order, favourites, contacts). */
    USER
}

internal data class RebuildableTableSpec(
    val contract: ReleasedRoomTableSpec,
    val ddl: RebuildDdl,
    val rows: RebuildableRows
)

/**
 * Child rows of a cache that the app can never reach once their parent row
 * is gone; [RebuildableTableRepair] deletes them.
 */
internal data class DanglingCacheRows(
    val childTable: String,
    val childColumns: List<String>,
    val parentTable: String,
    val parentColumns: List<String>
) {
    init {
        require(childColumns.isNotEmpty())
        require(childColumns.size == parentColumns.size)
    }
}

/**
 * Tables the version 76 integrity pass keeps to exact released layouts and
 * never rebuilds: the wallet identity, the per-chain accounts, TON Connect
 * sessions bound to stored secrets, and Room's own bookkeeping.
 */
internal val RELEASED_V76_WALLET_IDENTITY_TABLES = setOf(
    "meta_accounts",
    "chain_accounts",
    "ton_connection",
    "room_master_table",
    "legacy_users_recovery"
)

private val RELEASED_V76_USER_ROW_TABLES = setOf(
    "address_book",
    "assets",
    "favorite_chains"
)

private const val ROOM_TABLE_NAME_PLACEHOLDER = "\${TABLE_NAME}"

private val RELEASED_V76_REBUILD_DDL = mapOf(
    "address_book" to RebuildDdl(
        createSql = "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`address` TEXT NOT NULL, " +
            "`name` TEXT, `chainId` TEXT NOT NULL, `created` INTEGER NOT NULL, " +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)",
        indexSql = listOf(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_address_book_address_chainId` ON `\${TABLE_NAME}` (`address`, " +
                "`chainId`)"
        )
    ),
    "assets" to RebuildDdl(
        createSql = "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`id` TEXT NOT NULL, " +
            "`chainId` TEXT NOT NULL, `accountId` BLOB NOT NULL, " +
            "`metaId` INTEGER NOT NULL, `tokenPriceId` TEXT, `freeInPlanks` TEXT, " +
            "`reservedInPlanks` TEXT, `miscFrozenInPlanks` TEXT, " +
            "`feeFrozenInPlanks` TEXT, `bondedInPlanks` TEXT, " +
            "`redeemableInPlanks` TEXT, `unbondingInPlanks` TEXT, " +
            "`sortIndex` INTEGER NOT NULL, `enabled` INTEGER, " +
            "`markedNotNeed` INTEGER NOT NULL, `chainAccountName` TEXT, " +
            "`status` TEXT, PRIMARY KEY(`id`, `chainId`, `accountId`, `metaId`), " +
            "FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
        indexSql = listOf(
            "CREATE INDEX IF NOT EXISTS `index_assets_chainId` ON `\${TABLE_NAME}` (`chainId`)",
            "CREATE INDEX IF NOT EXISTS `index_assets_metaId` ON `\${TABLE_NAME}` (`metaId`)"
        )
    ),
    "token_price" to RebuildDdl(
        createSql = "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`priceId` TEXT NOT NULL, " +
            "`fiatSymbol` TEXT NOT NULL, `fiatRate` TEXT, `recentRateChange` TEXT, " +
            "PRIMARY KEY(`priceId`))",
        indexSql = emptyList()
    ),
    "phishing" to RebuildDdl(
        createSql = "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`address` TEXT NOT NULL, " +
            "`name` TEXT, `type` TEXT NOT NULL, `subtype` TEXT, " +
            "PRIMARY KEY(`address`, `type`))",
        indexSql = emptyList()
    ),
    "storage" to RebuildDdl(
        createSql = "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`storageKey` TEXT NOT NULL, " +
            "`content` TEXT, `chainId` TEXT NOT NULL, PRIMARY KEY(`chainId`, " +
            "`storageKey`))",
        indexSql = emptyList()
    ),
    "account_staking_accesses" to RebuildDdl(
        createSql = "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`chainId` TEXT NOT NULL, " +
            "`chainAssetId` TEXT NOT NULL, `accountId` BLOB NOT NULL, " +
            "`stashId` BLOB, `controllerId` BLOB, PRIMARY KEY(`chainId`, " +
            "`chainAssetId`, `accountId`))",
        indexSql = emptyList()
    ),
    "total_reward" to RebuildDdl(
        createSql = "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`accountAddress` TEXT NOT NULL, " +
            "`totalReward` TEXT NOT NULL, PRIMARY KEY(`accountAddress`))",
        indexSql = emptyList()
    ),
    "operations" to RebuildDdl(
        createSql = "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`id` TEXT NOT NULL, " +
            "`address` TEXT NOT NULL, `chainId` TEXT NOT NULL, " +
            "`chainAssetId` TEXT NOT NULL, `time` INTEGER NOT NULL, " +
            "`status` INTEGER NOT NULL, `source` INTEGER NOT NULL, " +
            "`operationType` INTEGER NOT NULL, `module` TEXT, `call` TEXT, " +
            "`amount` TEXT, `sender` TEXT, `receiver` TEXT, `hash` TEXT, `fee` TEXT, " +
            "`isReward` INTEGER, `era` INTEGER, `validator` TEXT, " +
            "`liquidityFee` TEXT, `market` TEXT, `targetAssetId` TEXT, " +
            "`targetAmount` TEXT, PRIMARY KEY(`id`, `address`, `chainId`, " +
            "`chainAssetId`))",
        indexSql = emptyList()
    ),
    "chains" to RebuildDdl(
        createSql = "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`id` TEXT NOT NULL, " +
            "`paraId` TEXT, `parentId` TEXT, `rank` INTEGER, `name` TEXT NOT NULL, " +
            "`minSupportedVersion` TEXT, `icon` TEXT NOT NULL, " +
            "`prefix` INTEGER NOT NULL, `isEthereumBased` INTEGER NOT NULL, " +
            "`isTestNet` INTEGER NOT NULL, `hasCrowdloans` INTEGER NOT NULL, " +
            "`supportStakingPool` INTEGER NOT NULL, " +
            "`isEthereumChain` INTEGER NOT NULL, " +
            "`isChainlinkProvider` INTEGER NOT NULL, `supportNft` INTEGER NOT NULL, " +
            "`isUsesAppId` INTEGER NOT NULL, `identityChain` TEXT, " +
            "`ecosystem` TEXT NOT NULL, `androidMinAppVersion` TEXT, " +
            "`remoteAssetsSource` TEXT, `tonBridgeUrl` TEXT, `xcm` TEXT, " +
            "`staking_url` TEXT, `staking_type` TEXT, `history_url` TEXT, " +
            "`history_type` TEXT, `crowdloans_url` TEXT, `crowdloans_type` TEXT, " +
            "PRIMARY KEY(`id`))",
        indexSql = emptyList()
    ),
    "chain_nodes" to RebuildDdl(
        createSql = "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`chainId` TEXT NOT NULL, " +
            "`url` TEXT NOT NULL, `name` TEXT NOT NULL, `isActive` INTEGER NOT NULL, " +
            "`isDefault` INTEGER NOT NULL, PRIMARY KEY(`chainId`, `url`), " +
            "FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        indexSql = listOf(
            "CREATE INDEX IF NOT EXISTS `index_chain_nodes_chainId` ON `\${TABLE_NAME}` (`chainId`)"
        )
    ),
    "chain_assets" to RebuildDdl(
        createSql = "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`id` TEXT NOT NULL, " +
            "`name` TEXT, `symbol` TEXT NOT NULL, `chainId` TEXT NOT NULL, " +
            "`icon` TEXT NOT NULL, `priceId` TEXT, `staking` TEXT NOT NULL, " +
            "`precision` INTEGER NOT NULL, `purchaseProviders` TEXT, " +
            "`isUtility` INTEGER, `type` TEXT, `currencyId` TEXT, " +
            "`existentialDeposit` TEXT, `color` TEXT, `isNative` INTEGER, " +
            "`priceProvider` TEXT, `coinbaseUrl` TEXT, PRIMARY KEY(`chainId`, `id`), " +
            "FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        indexSql = listOf(
            "CREATE INDEX IF NOT EXISTS `index_chain_assets_chainId` ON `\${TABLE_NAME}` (`chainId`)"
        )
    ),
    "favorite_chains" to RebuildDdl(
        createSql = "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`metaId` INTEGER NOT NULL, " +
            "`chainId` TEXT NOT NULL, `isFavorite` INTEGER NOT NULL, " +
            "PRIMARY KEY(`metaId`, `chainId`), " +
            "FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED, " +
            "FOREIGN KEY(`metaId`) REFERENCES `meta_accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        indexSql = emptyList()
    ),
    "chain_runtimes" to RebuildDdl(
        createSql = "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`chainId` TEXT NOT NULL, " +
            "`syncedVersion` INTEGER NOT NULL, `remoteVersion` INTEGER NOT NULL, " +
            "PRIMARY KEY(`chainId`))",
        indexSql = listOf(
            "CREATE INDEX IF NOT EXISTS `index_chain_runtimes_chainId` ON `\${TABLE_NAME}` (`chainId`)"
        )
    ),
    "chain_explorers" to RebuildDdl(
        createSql = "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`chainId` TEXT NOT NULL, " +
            "`type` TEXT NOT NULL, `types` TEXT NOT NULL, `url` TEXT NOT NULL, " +
            "PRIMARY KEY(`chainId`, `type`), " +
            "FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        indexSql = listOf(
            "CREATE INDEX IF NOT EXISTS `index_chain_explorers_chainId` ON `\${TABLE_NAME}` (`chainId`)"
        )
    ),
    "chain_types" to RebuildDdl(
        createSql = "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`chainId` TEXT NOT NULL, " +
            "`typesConfig` TEXT NOT NULL, PRIMARY KEY(`chainId`))",
        indexSql = emptyList()
    ),
    "nomis_wallet_score" to RebuildDdl(
        createSql = "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`metaId` INTEGER NOT NULL, " +
            "`score` INTEGER NOT NULL, `updated` INTEGER NOT NULL, " +
            "`nativeBalanceUsd` TEXT NOT NULL, `holdTokensUsd` TEXT NOT NULL, " +
            "`walletAgeInMonths` INTEGER NOT NULL, " +
            "`totalTransactions` INTEGER NOT NULL, " +
            "`rejectedTransactions` INTEGER NOT NULL, " +
            "`avgTransactionTimeInHours` REAL NOT NULL, " +
            "`maxTransactionTimeInHours` REAL NOT NULL, " +
            "`minTransactionTimeInHours` REAL NOT NULL, `scoredAt` TEXT NOT NULL, " +
            "PRIMARY KEY(`metaId`), " +
            "FOREIGN KEY(`metaId`) REFERENCES `meta_accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        indexSql = emptyList()
    ),
    "allpools" to RebuildDdl(
        createSql = "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`tokenIdBase` TEXT NOT NULL, " +
            "`tokenIdTarget` TEXT NOT NULL, `reserveBase` TEXT NOT NULL, " +
            "`reserveTarget` TEXT NOT NULL, `totalIssuance` TEXT NOT NULL, " +
            "`reservesAccount` TEXT NOT NULL, PRIMARY KEY(`tokenIdBase`, " +
            "`tokenIdTarget`))",
        indexSql = emptyList()
    ),
    "userpools" to RebuildDdl(
        createSql = "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`userTokenIdBase` TEXT NOT NULL, " +
            "`userTokenIdTarget` TEXT NOT NULL, `accountAddress` TEXT NOT NULL, " +
            "`poolProvidersBalance` TEXT NOT NULL, PRIMARY KEY(`userTokenIdBase`, " +
            "`userTokenIdTarget`, `accountAddress`), FOREIGN KEY(`userTokenIdBase`, " +
            "`userTokenIdTarget`) REFERENCES `allpools`(`tokenIdBase`, " +
            "`tokenIdTarget`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        indexSql = listOf(
            "CREATE INDEX IF NOT EXISTS `index_userpools_accountAddress` ON `\${TABLE_NAME}` (`accountAddress`)"
        )
    )
)

/**
 * Every other version 76 table. A layout, foreign key or index that no
 * release is known to have written is repaired from Room's version 76 DDL
 * (frozen from core-db/schemas/jp.co.soramitsu.coredb.AppDatabase/76.json,
 * identical in 81.json), never a reason to stop the upgrade (addendum U0.5).
 */
internal val RELEASED_V76_REBUILDABLE_TABLES: List<RebuildableTableSpec> =
    RELEASED_ROOM_SCHEMA_V76
        .filter { it.name !in RELEASED_V76_WALLET_IDENTITY_TABLES }
        .map { contract ->
            RebuildableTableSpec(
                contract = contract,
                ddl = RELEASED_V76_REBUILD_DDL.getValue(contract.name),
                rows = if (contract.name in RELEASED_V76_USER_ROW_TABLES) {
                    RebuildableRows.USER
                } else {
                    RebuildableRows.CACHE
                }
            )
        }

/**
 * Registry and pool caches whose parent row disappeared. Shipped 4.x builds
 * dropped the chain registry in 71 -> 72 with enforcement off, and their
 * chain sync only loads children through a parent row, so the nodes,
 * explorers and assets of networks the registry later dropped stay forever.
 */
internal val RELEASED_V76_DANGLING_CACHE_ROWS = listOf(
    DanglingCacheRows("chain_nodes", listOf("chainId"), "chains", listOf("id")),
    DanglingCacheRows("chain_assets", listOf("chainId"), "chains", listOf("id")),
    DanglingCacheRows("chain_explorers", listOf("chainId"), "chains", listOf("id")),
    DanglingCacheRows(
        "userpools",
        listOf("userTokenIdBase", "userTokenIdTarget"),
        "allpools",
        listOf("tokenIdBase", "tokenIdTarget")
    ),
    DanglingCacheRows("nomis_wallet_score", listOf("metaId"), "meta_accounts", listOf("id"))
)
