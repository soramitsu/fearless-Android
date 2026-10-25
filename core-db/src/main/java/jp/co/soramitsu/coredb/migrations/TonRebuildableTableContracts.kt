package jp.co.soramitsu.coredb.migrations

/** DDL frozen from the official3.7.4 writer in androidTest/assets/migrations/3.7.4/app-database-v71.sql. */
private val RELEASED_V71_REBUILD_DDL = mapOf(
    "address_book" to RebuildDdl(
        createSql = "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`address` TEXT NOT NULL, `name` TEXT, `chainId` TEXT NOT NULL, `created` INTEGER NOT NULL, `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)",
        indexSql = listOf("CREATE UNIQUE INDEX IF NOT EXISTS `index_address_book_address_chainId` ON `\${TABLE_NAME}` (`address`, `chainId`)")
    ),
    "assets" to RebuildDdl(
        createSql = "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`id` TEXT NOT NULL, `chainId` TEXT NOT NULL, `accountId` BLOB NOT NULL, `metaId` INTEGER NOT NULL, `tokenPriceId` TEXT, `freeInPlanks` TEXT, `reservedInPlanks` TEXT, `miscFrozenInPlanks` TEXT, `feeFrozenInPlanks` TEXT, `bondedInPlanks` TEXT, `redeemableInPlanks` TEXT, `unbondingInPlanks` TEXT, `sortIndex` INTEGER NOT NULL, `enabled` INTEGER, `markedNotNeed` INTEGER NOT NULL, `chainAccountName` TEXT, `status` TEXT, PRIMARY KEY(`id`, `chainId`, `accountId`, `metaId`), FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        indexSql = listOf("CREATE INDEX IF NOT EXISTS `index_assets_chainId` ON `\${TABLE_NAME}` (`chainId`)", "CREATE INDEX IF NOT EXISTS `index_assets_metaId` ON `\${TABLE_NAME}` (`metaId`)")
    ),
    "token_price" to RebuildDdl(
        createSql = "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`priceId` TEXT NOT NULL, `fiatRate` TEXT, `fiatSymbol` TEXT, `recentRateChange` TEXT, PRIMARY KEY(`priceId`))",
        indexSql = listOf()
    ),
    "phishing" to RebuildDdl(
        createSql = "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`address` TEXT NOT NULL, `name` TEXT, `type` TEXT NOT NULL, `subtype` TEXT, PRIMARY KEY(`address`, `type`))",
        indexSql = listOf()
    ),
    "storage" to RebuildDdl(
        createSql = "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`storageKey` TEXT NOT NULL, `content` TEXT, `chainId` TEXT NOT NULL, PRIMARY KEY(`chainId`, `storageKey`))",
        indexSql = listOf()
    ),
    "account_staking_accesses" to RebuildDdl(
        createSql = "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`chainId` TEXT NOT NULL, `chainAssetId` TEXT NOT NULL, `accountId` BLOB NOT NULL, `stashId` BLOB, `controllerId` BLOB, PRIMARY KEY(`chainId`, `chainAssetId`, `accountId`))",
        indexSql = listOf()
    ),
    "total_reward" to RebuildDdl(
        createSql = "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`accountAddress` TEXT NOT NULL, `totalReward` TEXT NOT NULL, PRIMARY KEY(`accountAddress`))",
        indexSql = listOf()
    ),
    "operations" to RebuildDdl(
        createSql = "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`id` TEXT NOT NULL, `address` TEXT NOT NULL, `chainId` TEXT NOT NULL, `chainAssetId` TEXT NOT NULL, `time` INTEGER NOT NULL, `status` INTEGER NOT NULL, `source` INTEGER NOT NULL, `operationType` INTEGER NOT NULL, `module` TEXT, `call` TEXT, `amount` TEXT, `sender` TEXT, `receiver` TEXT, `hash` TEXT, `fee` TEXT, `isReward` INTEGER, `era` INTEGER, `validator` TEXT, `liquidityFee` TEXT, `market` TEXT, `targetAssetId` TEXT, `targetAmount` TEXT, PRIMARY KEY(`id`, `address`, `chainId`, `chainAssetId`))",
        indexSql = listOf()
    ),
    "chains" to RebuildDdl(
        createSql = "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`id` TEXT NOT NULL, `paraId` TEXT, `parentId` TEXT, `rank` INTEGER, `name` TEXT NOT NULL, `minSupportedVersion` TEXT, `icon` TEXT NOT NULL, `prefix` INTEGER NOT NULL, `isEthereumBased` INTEGER NOT NULL, `isTestNet` INTEGER NOT NULL, `hasCrowdloans` INTEGER NOT NULL, `supportStakingPool` INTEGER NOT NULL, `isEthereumChain` INTEGER NOT NULL, `isChainlinkProvider` INTEGER NOT NULL, `supportNft` INTEGER NOT NULL, `isUsesAppId` INTEGER NOT NULL, `identityChain` TEXT, `remoteAssetsSource` TEXT, `staking_url` TEXT, `staking_type` TEXT, `history_url` TEXT, `history_type` TEXT, `crowdloans_url` TEXT, `crowdloans_type` TEXT, PRIMARY KEY(`id`))",
        indexSql = listOf()
    ),
    "chain_nodes" to RebuildDdl(
        createSql = "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`chainId` TEXT NOT NULL, `url` TEXT NOT NULL, `name` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `isDefault` INTEGER NOT NULL, PRIMARY KEY(`chainId`, `url`), FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        indexSql = listOf("CREATE INDEX IF NOT EXISTS `index_chain_nodes_chainId` ON `\${TABLE_NAME}` (`chainId`)")
    ),
    "chain_assets" to RebuildDdl(
        createSql = "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`id` TEXT NOT NULL, `name` TEXT, `symbol` TEXT NOT NULL, `chainId` TEXT NOT NULL, `icon` TEXT NOT NULL, `priceId` TEXT, `staking` TEXT NOT NULL, `precision` INTEGER NOT NULL, `purchaseProviders` TEXT, `isUtility` INTEGER, `type` TEXT, `currencyId` TEXT, `existentialDeposit` TEXT, `color` TEXT, `isNative` INTEGER, `ethereumType` TEXT, `priceProvider` TEXT, PRIMARY KEY(`chainId`, `id`), FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        indexSql = listOf("CREATE INDEX IF NOT EXISTS `index_chain_assets_chainId` ON `\${TABLE_NAME}` (`chainId`)")
    ),
    "favorite_chains" to RebuildDdl(
        createSql = "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`metaId` INTEGER NOT NULL, `chainId` TEXT NOT NULL, `isFavorite` INTEGER NOT NULL, PRIMARY KEY(`metaId`, `chainId`), FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED, FOREIGN KEY(`metaId`) REFERENCES `meta_accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        indexSql = listOf()
    ),
    "chain_runtimes" to RebuildDdl(
        createSql = "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`chainId` TEXT NOT NULL, `syncedVersion` INTEGER NOT NULL, `remoteVersion` INTEGER NOT NULL, PRIMARY KEY(`chainId`))",
        indexSql = listOf("CREATE INDEX IF NOT EXISTS `index_chain_runtimes_chainId` ON `\${TABLE_NAME}` (`chainId`)")
    ),
    "chain_explorers" to RebuildDdl(
        createSql = "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`chainId` TEXT NOT NULL, `type` TEXT NOT NULL, `types` TEXT NOT NULL, `url` TEXT NOT NULL, PRIMARY KEY(`chainId`, `type`), FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        indexSql = listOf("CREATE INDEX IF NOT EXISTS `index_chain_explorers_chainId` ON `\${TABLE_NAME}` (`chainId`)")
    ),
    "chain_types" to RebuildDdl(
        createSql = "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`chainId` TEXT NOT NULL, `typesConfig` TEXT NOT NULL, PRIMARY KEY(`chainId`))",
        indexSql = listOf()
    ),
    "nomis_wallet_score" to RebuildDdl(
        createSql = "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`metaId` INTEGER NOT NULL, `score` INTEGER NOT NULL, `updated` INTEGER NOT NULL, `nativeBalanceUsd` TEXT NOT NULL, `holdTokensUsd` TEXT NOT NULL, `walletAgeInMonths` INTEGER NOT NULL, `totalTransactions` INTEGER NOT NULL, `rejectedTransactions` INTEGER NOT NULL, `avgTransactionTimeInHours` REAL NOT NULL, `maxTransactionTimeInHours` REAL NOT NULL, `minTransactionTimeInHours` REAL NOT NULL, `scoredAt` TEXT NOT NULL, PRIMARY KEY(`metaId`), FOREIGN KEY(`metaId`) REFERENCES `meta_accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        indexSql = listOf()
    ),
    "allpools" to RebuildDdl(
        createSql = "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`tokenIdBase` TEXT NOT NULL, `tokenIdTarget` TEXT NOT NULL, `reserveBase` TEXT NOT NULL, `reserveTarget` TEXT NOT NULL, `totalIssuance` TEXT NOT NULL, `reservesAccount` TEXT NOT NULL, PRIMARY KEY(`tokenIdBase`, `tokenIdTarget`))",
        indexSql = listOf()
    ),
    "userpools" to RebuildDdl(
        createSql = "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`userTokenIdBase` TEXT NOT NULL, `userTokenIdTarget` TEXT NOT NULL, `accountAddress` TEXT NOT NULL, `poolProvidersBalance` TEXT NOT NULL, PRIMARY KEY(`userTokenIdBase`, `userTokenIdTarget`, `accountAddress`), FOREIGN KEY(`userTokenIdBase`, `userTokenIdTarget`) REFERENCES `allpools`(`tokenIdBase`, `tokenIdTarget`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        indexSql = listOf("CREATE INDEX IF NOT EXISTS `index_userpools_accountAddress` ON `\${TABLE_NAME}` (`accountAddress`)")
    )
)

/** Wallet identities and Room metadata stay subject to exact released schema admission. */
internal val RELEASED_V71_REBUILDABLE_TABLES: List<RebuildableTableSpec> =
    TonUpgradeSqlPreflight.VERSION_71_RELEASED_ROOM_TABLES
        .filter { it.name in RELEASED_V71_REBUILD_DDL }
        .map { contract ->
            RebuildableTableSpec(
                contract = contract,
                ddl = RELEASED_V71_REBUILD_DDL.getValue(contract.name),
                rows = if (contract.name in setOf("address_book", "assets", "favorite_chains")) {
                    RebuildableRows.USER
                } else {
                    RebuildableRows.CACHE
                }
            )
        }
