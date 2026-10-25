package jp.co.soramitsu.coredb.migrations

import androidx.sqlite.db.SupportSQLiteDatabase

/** The exact SQL-only phase of 71-to-72, shared with rollback-only future-adoption previews. */
internal object TonSchemaMigration {
    fun migrate(db: SupportSQLiteDatabase) {
        migrateMetaAccounts(db)
        recreateChainsAndAssets(db)
        recreateTokenPrice(db)
    }

    private fun migrateMetaAccounts(db: SupportSQLiteDatabase) {
        val dependentTables = listOf(
            "chain_accounts",
            "favorite_chains",
            "nomis_wallet_score"
        )

        dependentTables.forEach { tableName ->
            db.execSQL("DROP TABLE IF EXISTS `_ton_migration_$tableName`")
            db.execSQL(
                "CREATE TEMP TABLE `_ton_migration_$tableName` AS SELECT * FROM `$tableName`"
            )
        }

        db.execSQL("DROP TABLE IF EXISTS _meta_accounts")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `_meta_accounts` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `substratePublicKey` BLOB,
            `substrateCryptoType` TEXT,
            `substrateAccountId` BLOB,
            `ethereumPublicKey` BLOB,
            `ethereumAddress` BLOB,
            `tonPublicKey` BLOB,
            `name` TEXT NOT NULL,
            `isSelected` INTEGER NOT NULL,
            `position` INTEGER NOT NULL,
            `isBackedUp` INTEGER NOT NULL,
            `googleBackupAddress` TEXT,
            `initialized` INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT INTO _meta_accounts SELECT 
            m.id,
            m.substratePublicKey,
            m.substrateCryptoType,
            m.substrateAccountId,
            m.ethereumPublicKey,
            m.ethereumAddress,
            NULL as `tonPublicKey`,
            m.name,
            m.isSelected,
            m.position,
            m.isBackedUp,
            m.googleBackupAddress,
            m.initialized
            FROM meta_accounts m
            """.trimIndent()
        )
        db.execSQL("DROP TABLE IF EXISTS `meta_accounts`")
        db.execSQL("ALTER TABLE _meta_accounts RENAME TO meta_accounts")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_meta_accounts_substrateAccountId` ON `meta_accounts` (`substrateAccountId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_meta_accounts_ethereumAddress` ON `meta_accounts` (`ethereumAddress`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_meta_accounts_tonPublicKey` ON `meta_accounts` (`tonPublicKey`)")

        dependentTables.forEach { tableName ->
            db.execSQL(
                "INSERT OR REPLACE INTO `$tableName` SELECT * FROM `_ton_migration_$tableName`"
            )
            db.execSQL("DROP TABLE `_ton_migration_$tableName`")
        }
    }

    private fun recreateChainsAndAssets(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE chains ADD COLUMN `ecosystem` TEXT NOT NULL DEFAULT 'Substrate'"
        )
        db.execSQL(
            """
            UPDATE chains
            SET ecosystem = CASE
                WHEN isEthereumChain = 1 THEN 'Ethereum'
                WHEN isEthereumBased = 1 THEN 'EthereumBased'
                ELSE 'Substrate'
            END
            """.trimIndent()
        )
        db.execSQL("ALTER TABLE chains ADD COLUMN `androidMinAppVersion` TEXT NULL DEFAULT NULL")

        db.execSQL("DROP TABLE IF EXISTS `_chain_assets`")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `_chain_assets` (
            `id` TEXT NOT NULL, 
            `name` TEXT, 
            `symbol` TEXT NOT NULL, 
            `chainId` TEXT NOT NULL, 
            `icon` TEXT NOT NULL, 
            `priceId` TEXT, 
            `staking` TEXT NOT NULL, 
            `precision` INTEGER NOT NULL, 
            `purchaseProviders` TEXT, 
            `isUtility` INTEGER, 
            `type` TEXT, 
            `currencyId` TEXT, 
            `existentialDeposit` TEXT, 
            `color` TEXT, 
            `isNative` INTEGER, 
            `priceProvider` TEXT, 
            PRIMARY KEY(`chainId`, `id`), 
            FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `_chain_assets` SELECT
            a.id,
            a.name,
            a.symbol,
            a.chainId,
            a.icon,
            a.priceId,
            a.staking,
            a.precision,
            a.purchaseProviders,
            a.isUtility,
            a.type,
            a.currencyId,
            a.existentialDeposit,
            a.color,
            a.isNative,
            a.priceProvider
            FROM chain_assets a
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `chain_assets`")
        db.execSQL("ALTER TABLE `_chain_assets` RENAME TO `chain_assets`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_assets_chainId` ON `chain_assets` (`chainId`)")

        db.execSQL("DROP TABLE IF EXISTS _assets")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `_assets` (
            `id` TEXT NOT NULL, 
            `chainId` TEXT NOT NULL, 
            `accountId` BLOB NOT NULL, 
            `metaId` INTEGER NOT NULL, 
            `tokenPriceId` TEXT, 
            `freeInPlanks` TEXT, 
            `reservedInPlanks` TEXT, 
            `miscFrozenInPlanks` TEXT, 
            `feeFrozenInPlanks` TEXT, 
            `bondedInPlanks` TEXT, 
            `redeemableInPlanks` TEXT, 
            `unbondingInPlanks` TEXT, 
            `sortIndex` INTEGER NOT NULL DEFAULT 0, 
            `enabled` INTEGER DEFAULT NULL, 
            `markedNotNeed` INTEGER NOT NULL DEFAULT 0, 
            `chainAccountName` TEXT, 
            `status` TEXT NULL,
            PRIMARY KEY(`id`, `chainId`, `accountId`, `metaId`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO _assets SELECT 
            a.id,
            a.chainId,
            a.accountId,
            a.metaId,
            a.tokenPriceId,
            a.freeInPlanks,
            a.reservedInPlanks,
            a.miscFrozenInPlanks,
            a.feeFrozenInPlanks,
            a.bondedInPlanks,
            a.redeemableInPlanks,
            a.unbondingInPlanks,
            a.sortIndex,
            a.enabled,
            a.markedNotNeed,
            a.chainAccountName,
            a.status
            FROM assets a
            """.trimIndent()
        )

        db.execSQL("DROP TABLE IF EXISTS assets")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `assets` (
            `id` TEXT NOT NULL, 
            `chainId` TEXT NOT NULL, 
            `accountId` BLOB NOT NULL, 
            `metaId` INTEGER NOT NULL, 
            `tokenPriceId` TEXT, 
            `freeInPlanks` TEXT, 
            `reservedInPlanks` TEXT, 
            `miscFrozenInPlanks` TEXT, 
            `feeFrozenInPlanks` TEXT, 
            `bondedInPlanks` TEXT, 
            `redeemableInPlanks` TEXT, 
            `unbondingInPlanks` TEXT, 
            `sortIndex` INTEGER NOT NULL DEFAULT 0, 
            `enabled` INTEGER DEFAULT NULL, 
            `markedNotNeed` INTEGER NOT NULL DEFAULT 0, 
            `chainAccountName` TEXT,
            `status` TEXT NULL,
            PRIMARY KEY(`id`, `chainId`, `accountId`, `metaId`), 
            FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION 
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT INTO assets SELECT 
            a.id,
            a.chainId,
            a.accountId,
            a.metaId,
            a.tokenPriceId,
            a.freeInPlanks,
            a.reservedInPlanks,
            a.miscFrozenInPlanks,
            a.feeFrozenInPlanks,
            a.bondedInPlanks,
            a.redeemableInPlanks,
            a.unbondingInPlanks,
            a.sortIndex,
            a.enabled,
            a.markedNotNeed,
            a.chainAccountName,
            a.status
            FROM _assets a
            """.trimIndent()
        )
        db.execSQL("DROP TABLE IF EXISTS _assets")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_assets_metaId` ON `assets` (`metaId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_assets_chainId` ON `assets` (`chainId`)")
    }

    private fun recreateTokenPrice(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `token_price`")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `token_price` (
            `priceId` TEXT NOT NULL, 
            `fiatSymbol` TEXT NOT NULL, 
            `fiatRate` TEXT, 
            `recentRateChange` TEXT, 
            PRIMARY KEY(`priceId`)
            )
            """.trimIndent()
        )
    }

}
