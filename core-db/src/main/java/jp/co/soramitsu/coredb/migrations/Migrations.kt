package jp.co.soramitsu.coredb.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val Migration_51_52 = object : Migration(51, 52) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE assets RENAME TO _assets")
        database.execSQL("DROP TABLE IF EXISTS assets")
        // new table with nullable enabled field
        database.execSQL(
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
            PRIMARY KEY(`id`, `chainId`, `accountId`, `metaId`), 
            FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE 
            )
            """.trimIndent()
        )

        database.execSQL(
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
            a.chainAccountName
            FROM _assets a
            """.trimIndent()
        )
        database.execSQL(
            """
            UPDATE assets SET enabled = NULL WHERE enabled = 1
            """.trimIndent()
        )
        database.execSQL("DROP TABLE IF EXISTS _assets")

        database.execSQL("CREATE INDEX IF NOT EXISTS `index_assets_metaId` ON `assets` (`metaId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_assets_chainId` ON `assets` (`chainId`)")
    }
}


val Migration_50_51 = object : Migration(50, 51) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
             CREATE TABLE IF NOT EXISTS `sora_card` (
             `id` TEXT NOT NULL, 
             `accessToken` TEXT NOT NULL, 
             `refreshToken` TEXT NOT NULL, 
             `accessTokenExpirationTime` INTEGER NOT NULL, 
             `kycStatus` TEXT NOT NULL,
             PRIMARY KEY(`id`)
             )
            """.trimIndent()
        )
    }
}

val Migration_49_50 = object : Migration(49, 50) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE chain_assets ADD COLUMN `name` TEXT DEFAULT NULL")
    }
}

val Migration_48_49 = object : Migration(48, 49) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE chain_assets ADD COLUMN `isNative` INTEGER DEFAULT NULL")
    }
}

val Migration_47_48 = object : Migration(47, 48) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE chain_assets ADD COLUMN `color` TEXT DEFAULT NULL")
    }
}

val Migration_46_47 = object : Migration(46, 47) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE operations ADD COLUMN `liquidityFee` TEXT DEFAULT NULL")
        database.execSQL("ALTER TABLE operations ADD COLUMN `market` TEXT DEFAULT NULL")
        database.execSQL("ALTER TABLE operations ADD COLUMN `targetAssetId` TEXT DEFAULT NULL")
        database.execSQL("ALTER TABLE operations ADD COLUMN `targetAmount` TEXT DEFAULT NULL")

        database.execSQL("DELETE FROM operations")
    }
}

val Migration_45_46 = object : Migration(45, 46) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // on some devices FOREIGN KEY(`chainId`) REFERENCES to `_chains` table.
        // So we need to recreate all the tables with new FK which were created after the renaming chains to _chains (Migration_41_42)
        // assets - done in Migration_42_43
        // chain_assets - done in Migration_42_43
        // chain_explorers - done here
        // chain_nodes - done here
        // chain_accounts - done here

        // delete all data related to chains and assets - emulating cold start with existing accounts
        database.execSQL("DELETE FROM chains")
        database.execSQL("DELETE FROM chain_assets")
        database.execSQL("DELETE FROM assets")

        database.execSQL("DROP TABLE IF EXISTS chain_nodes")
        database.execSQL(
            """
             CREATE TABLE IF NOT EXISTS `chain_nodes` (
             `chainId` TEXT NOT NULL, 
             `url` TEXT NOT NULL, 
             `name` TEXT NOT NULL, 
             `isActive` INTEGER NOT NULL, 
             `isDefault` INTEGER NOT NULL, 
             PRIMARY KEY(`chainId`, `url`), 
             FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE 
             )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_nodes_chainId` ON `chain_nodes` (`chainId`)")

        database.execSQL("DROP TABLE IF EXISTS chain_explorers")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `chain_explorers` (
            `chainId` TEXT NOT NULL,
            `type` TEXT NOT NULL,
            `types` TEXT NOT NULL,
            `url` TEXT NOT NULL,
            PRIMARY KEY(`chainId`, `type`),
            FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_explorers_chainId` ON `chain_explorers` (`chainId`)")

        database.execSQL("DROP TABLE chain_accounts")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `chain_accounts` (
            `metaId` INTEGER NOT NULL,
            `chainId` TEXT NOT NULL,
            `publicKey` BLOB NOT NULL,
            `accountId` BLOB NOT NULL,
            `cryptoType` TEXT NOT NULL,
            `name` TEXT NOT NULL,
            PRIMARY KEY(`metaId`, `chainId`),
            FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION  DEFERRABLE INITIALLY DEFERRED,
            FOREIGN KEY(`metaId`) REFERENCES `meta_accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE 
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_accounts_chainId` ON `chain_accounts` (`chainId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_accounts_metaId` ON `chain_accounts` (`metaId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_accounts_accountId` ON `chain_accounts` (`accountId`)")
    }
}

val Migration_44_45 = object : Migration(44, 45) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
             CREATE TABLE IF NOT EXISTS `address_book` (
             `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
             `address` TEXT NOT NULL, 
             `name` TEXT, 
             `chainId` TEXT NOT NULL, 
             `created` INTEGER NOT NULL
             )
            """.trimIndent()
        )
    }
}

val Migration_43_44 = object : Migration(43, 44) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("DROP TABLE IF EXISTS phishing_addresses")
        database.execSQL(
            """
             CREATE TABLE IF NOT EXISTS `phishing` (
             `address` TEXT NOT NULL, 
             `name` TEXT, 
             `type` TEXT NOT NULL, 
             `subtype` TEXT, 
             PRIMARY KEY(`address`, `type`)
             )
            """.trimIndent()
        )
    }
}

val Migration_42_43 = object : Migration(42, 43) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("DROP TABLE IF EXISTS chain_assets")

        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `chain_assets` (
            `id` TEXT NOT NULL,
            `symbol` TEXT NOT NULL,
            `displayName` TEXT,
            `chainId` TEXT NOT NULL,
            `icon` TEXT NOT NULL,
            `precision` INTEGER NOT NULL,
            `priceId` TEXT,
            `staking` TEXT NOT NULL,
            `priceProviders` TEXT,
            `isUtility` INTEGER,
            `type` TEXT,
            `currencyId` TEXT,
            `existentialDeposit` TEXT,
            PRIMARY KEY(`chainId`, `id`),
            FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_assets_chainId` ON `chain_assets` (`chainId`)")

        database.execSQL("DROP TABLE IF EXISTS assets")
        database.execSQL(
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
            `enabled` INTEGER NOT NULL DEFAULT 1, 
            `markedNotNeed` INTEGER NOT NULL DEFAULT 0, 
            `chainAccountName` TEXT, 
            PRIMARY KEY(`id`, `chainId`, `accountId`, `metaId`), 
            FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE 
            )
            """.trimIndent()
        )

        database.execSQL("CREATE INDEX IF NOT EXISTS `index_assets_metaId` ON `assets` (`metaId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_assets_chainId` ON `assets` (`chainId`)")

        database.execSQL("DROP TABLE IF EXISTS tokens")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `token_price` (
            `priceId` TEXT NOT NULL, 
            `fiatRate` TEXT, 
            `fiatSymbol` TEXT, 
            `recentRateChange` TEXT, 
            PRIMARY KEY(`priceId`)
            )
            """.trimIndent()
        )
    }
}

val Migration_41_42 = object : Migration(41, 42) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE chains RENAME TO _chains")
        database.execSQL("DROP TABLE IF EXISTS chains")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `chains` (
            `id` TEXT NOT NULL,
            `parentId` TEXT,
            `name` TEXT NOT NULL,
            `minSupportedVersion` TEXT,
            `icon` TEXT NOT NULL,
            `prefix` INTEGER NOT NULL,
            `isEthereumBased` INTEGER NOT NULL,
            `isTestNet` INTEGER NOT NULL,
            `hasCrowdloans` INTEGER NOT NULL,
            `supportStakingPool` INTEGER NOT NULL,
            `url` TEXT,
            `overridesCommon` INTEGER,
            `staking_url` TEXT,
            `staking_type` TEXT,
            `history_url` TEXT,
            `history_type` TEXT,
            `crowdloans_url` TEXT,
            `crowdloans_type` TEXT,
            
            PRIMARY KEY(`id`))
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO chains SELECT 
            c.id,
            c.parentId,
            c.name,
            c.minSupportedVersion,
            c.icon,
            c.prefix,
            c.isEthereumBased,
            c.isTestNet,
            c.hasCrowdloans,
            0 as `supportStakingPool`,
            c.url,
            c.overridesCommon,
            c.staking_url,
            c.staking_type,
            c.history_url,
            c.history_type,
            c.crowdloans_url,
            c.crowdloans_type
            FROM _chains c
            """.trimIndent()
        )
        database.execSQL("DROP TABLE IF EXISTS _chains")
    }
}

val AssetsMigration_40_41 = object : Migration(40, 41) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE assets RENAME TO _assets")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `assets` (
            `tokenSymbol` TEXT NOT NULL, 
            `chainId` TEXT NOT NULL, 
            `accountId` BLOB NOT NULL, 
            `metaId` INTEGER NOT NULL, 
            `freeInPlanks` TEXT, 
            `reservedInPlanks` TEXT, 
            `miscFrozenInPlanks` TEXT, 
            `feeFrozenInPlanks` TEXT, 
            `bondedInPlanks` TEXT, 
            `redeemableInPlanks` TEXT, 
            `unbondingInPlanks` TEXT, 
            `sortIndex` INTEGER NOT NULL DEFAULT 0, 
            `enabled` INTEGER NOT NULL DEFAULT 1, 
            `markedNotNeed` INTEGER NOT NULL DEFAULT 0, 
            `chainAccountName` TEXT, 
            PRIMARY KEY(`tokenSymbol`, `chainId`, `accountId`, `metaId`), 
            FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE 
            )
            """.trimIndent()
        )

        database.execSQL(
            """
            INSERT INTO assets SELECT 
                a.tokenSymbol,
                a.chainId,
                a.accountId,
                a.metaId,
                a.freeInPlanks,
                a.reservedInPlanks,
                a.miscFrozenInPlanks,
                a.feeFrozenInPlanks,
                a.bondedInPlanks,
                a.redeemableInPlanks,
                a.unbondingInPlanks, 
                0 as `sortIndex`, 
                1 as `enabled`, 
                0 as `markedNotNeed`,
                null as `chainAccountName` 
            FROM _assets a
            """.trimIndent()
        )
        database.execSQL("DROP TABLE _assets")

        database.execSQL("CREATE INDEX IF NOT EXISTS `index_assets_metaId` ON `assets` (`metaId`)")
    }
}

val ChainAssetsMigration_39_40 = object : Migration(39, 40) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("DELETE FROM chain_explorers")
        database.execSQL("DELETE FROM chain_assets")
        database.execSQL("DELETE FROM chain_nodes")

        database.execSQL("DROP TABLE IF EXISTS chains")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `chains` (
            `id` TEXT NOT NULL,
            `parentId` TEXT,
            `name` TEXT NOT NULL,
            `minSupportedVersion` TEXT,
            `icon` TEXT NOT NULL,
            `prefix` INTEGER NOT NULL,
            `isEthereumBased` INTEGER NOT NULL,
            `isTestNet` INTEGER NOT NULL,
            `hasCrowdloans` INTEGER NOT NULL,
            `url` TEXT,
            `overridesCommon` INTEGER,
            `staking_url` TEXT,
            `staking_type` TEXT,
            `history_url` TEXT,
            `history_type` TEXT,
            `crowdloans_url` TEXT,
            `crowdloans_type` TEXT,
            PRIMARY KEY(`id`))
            """.trimIndent()
        )
    }
}

val AssetsMigration_38_39 = object : Migration(38, 39) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.beginTransaction()

        database.execSQL("ALTER TABLE assets RENAME TO _assets")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `assets` (
            `tokenSymbol` TEXT NOT NULL, 
            `chainId` TEXT NOT NULL, 
            `accountId` BLOB NOT NULL, 
            `metaId` INTEGER NOT NULL, 
            `freeInPlanks` TEXT, 
            `reservedInPlanks` TEXT, 
            `miscFrozenInPlanks` TEXT, 
            `feeFrozenInPlanks` TEXT, 
            `bondedInPlanks` TEXT, 
            `redeemableInPlanks` TEXT, 
            `unbondingInPlanks` TEXT, 
            `sortIndex` INTEGER NOT NULL DEFAULT 0, 
            `enabled` INTEGER NOT NULL DEFAULT 1, 
            `chainAccountName` TEXT, 
            PRIMARY KEY(`tokenSymbol`, `chainId`, `accountId`, `metaId`), 
            FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE 
            )
            """.trimIndent()
        )

        database.execSQL(
            """
            INSERT INTO assets SELECT 
                a.tokenSymbol,
                a.chainId,
                a.accountId,
                a.metaId,
                a.freeInPlanks,
                a.reservedInPlanks,
                a.miscFrozenInPlanks,
                a.feeFrozenInPlanks,
                a.bondedInPlanks,
                a.redeemableInPlanks,
                a.unbondingInPlanks, 
                0 as `sortIndex`, 
                1 as `enabled`, 
                null as `chainAccountName` 
            FROM _assets a
            """.trimIndent()
        )
        database.execSQL("DROP TABLE _assets")

        database.execSQL("CREATE INDEX IF NOT EXISTS `index_assets_metaId` ON `assets` (`metaId`)")

        database.setTransactionSuccessful()
        database.endTransaction()
    }
}

val DifferentCurrenciesMigrations_37_38 = object : Migration(37, 38) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.beginTransaction()

        database.execSQL("DROP TABLE tokens")
        database.execSQL(
            """
                CREATE TABLE IF NOT EXISTS `tokens` (
                `symbol` TEXT NOT NULL,
                `fiatRate` TEXT,
                `fiatSymbol` TEXT,
                `recentRateChange` TEXT,
                PRIMARY KEY(`symbol`)
                )
            """.trimIndent()
        )
        database.setTransactionSuccessful()
        database.endTransaction()
    }
}

val FixAssetsMigration_36_37 = object : Migration(36, 37) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.beginTransaction()

        database.execSQL("ALTER TABLE assets RENAME TO _assets")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `assets` (
            `tokenSymbol` TEXT NOT NULL, 
            `chainId` TEXT NOT NULL, 
            `accountId` BLOB NOT NULL, 
            `metaId` INTEGER NOT NULL, 
            `freeInPlanks` TEXT NOT NULL, 
            `reservedInPlanks` TEXT NOT NULL, 
            `miscFrozenInPlanks` TEXT NOT NULL, 
            `feeFrozenInPlanks` TEXT NOT NULL, 
            `bondedInPlanks` TEXT NOT NULL, 
            `redeemableInPlanks` TEXT NOT NULL, 
            `unbondingInPlanks` TEXT NOT NULL, 
            `sortIndex` INTEGER NOT NULL DEFAULT 0, 
            `enabled` INTEGER NOT NULL DEFAULT 1, 
            `chainAccountName` TEXT, 
            PRIMARY KEY(`tokenSymbol`, `chainId`, `accountId`, `metaId`), 
            FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE 
            )
            """.trimIndent()
        )

        database.execSQL(
            """
            INSERT INTO assets SELECT 
                a.tokenSymbol,
                a.chainId,
                a.accountId,
                a.metaId,
                a.freeInPlanks,
                a.reservedInPlanks,
                a.miscFrozenInPlanks,
                a.feeFrozenInPlanks,
                a.bondedInPlanks,
                a.redeemableInPlanks,
                a.unbondingInPlanks, 
                0 as `sortIndex`, 
                1 as `enabled`, 
                null as `chainAccountName` 
            FROM _assets a
            """.trimIndent()
        )
        database.execSQL("DROP TABLE _assets")

        database.execSQL("CREATE INDEX IF NOT EXISTS `index_assets_metaId` ON `assets` (`metaId`)")

        database.setTransactionSuccessful()
        database.endTransaction()
    }
}

val RemoveLegacyData_35_36 = object : Migration(35, 36) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("DROP TABLE chain_accounts")

        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `chain_accounts` (
            `metaId` INTEGER NOT NULL,
            `chainId` TEXT NOT NULL,
            `publicKey` BLOB NOT NULL,
            `accountId` BLOB NOT NULL,
            `cryptoType` TEXT NOT NULL,
            `name` TEXT NOT NULL,
            PRIMARY KEY(`metaId`, `chainId`),
            FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION  DEFERRABLE INITIALLY DEFERRED,
            FOREIGN KEY(`metaId`) REFERENCES `meta_accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE 
            )
            """.trimIndent()
        )

        database.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_accounts_chainId` ON `chain_accounts` (`chainId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_accounts_metaId` ON `chain_accounts` (`metaId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_accounts_accountId` ON `chain_accounts` (`accountId`)")

        // remove `networkType` INTEGER NOT NULL
        database.execSQL("ALTER TABLE users RENAME TO _users")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `users` (
                `address` TEXT NOT NULL, 
                `username` TEXT NOT NULL, 
                `publicKey` TEXT NOT NULL, 
                `cryptoType` INTEGER NOT NULL, 
                `position` INTEGER NOT NULL, 
                PRIMARY KEY(`address`)
            )
            """.trimIndent()
        )
        database.execSQL("INSERT INTO users SELECT address, username, publicKey, cryptoType, position FROM _users")
        database.execSQL("DROP TABLE _users")
    }
}

val AddChainExplorersTable_33_34 = object : Migration(33, 34) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `chain_explorers` (
            `chainId` TEXT NOT NULL,
            `type` TEXT NOT NULL,
            `types` TEXT NOT NULL,
            `url` TEXT NOT NULL,
            PRIMARY KEY(`chainId`, `type`),
            FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_explorers_chainId` ON `chain_explorers` (`chainId`)")
    }
}

val MigrateTablesToV2_32_33 = object : Migration(32, 33) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // assets
        database.execSQL("DROP TABLE assets")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `assets` (
            `tokenSymbol` TEXT NOT NULL,
            `chainId` TEXT NOT NULL,
            `accountId` BLOB NOT NULL,
            `metaId` INTEGER NOT NULL,
            `freeInPlanks` TEXT NOT NULL,
            `reservedInPlanks` TEXT NOT NULL,
            `miscFrozenInPlanks` TEXT NOT NULL,
            `feeFrozenInPlanks` TEXT NOT NULL,
            `bondedInPlanks` TEXT NOT NULL,
            `redeemableInPlanks` TEXT NOT NULL,
            `unbondingInPlanks` TEXT NOT NULL,
            PRIMARY KEY(`tokenSymbol`, `chainId`, `accountId`),
            FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_assets_metaId` ON `assets` (`metaId`)")
    }
}

val MigrateTablesToV2_30_31 = object : Migration(30, 31) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE chain_nodes ADD COLUMN `isActive` INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE chain_nodes ADD COLUMN `isDefault` INTEGER NOT NULL DEFAULT 1")

        database.execSQL("DROP TABLE nodes")
    }
}

val MigrateTablesToV2_29_30 = object : Migration(29, 30) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // assets
        database.execSQL("DROP TABLE assets")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `assets` (
            `tokenSymbol` TEXT NOT NULL,
            `chainId` TEXT NOT NULL,
            `accountId` BLOB NOT NULL,
            `metaId` INTEGER NOT NULL,
            `freeInPlanks` TEXT NOT NULL,
            `reservedInPlanks` TEXT NOT NULL,
            `miscFrozenInPlanks` TEXT NOT NULL,
            `feeFrozenInPlanks` TEXT NOT NULL,
            `bondedInPlanks` TEXT NOT NULL,
            `redeemableInPlanks` TEXT NOT NULL,
            `unbondingInPlanks` TEXT NOT NULL,
            PRIMARY KEY(`tokenSymbol`, `chainId`, `accountId`)
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_assets_metaId` ON `assets` (`metaId`)")

        // storage
        database.execSQL("DROP TABLE storage")
        database.execSQL(
            """
                CREATE TABLE IF NOT EXISTS `storage` (
                `storageKey` TEXT NOT NULL,
                `content` TEXT,
                `chainId` TEXT NOT NULL,
                PRIMARY KEY(`chainId`, `storageKey`)
                )
            """.trimIndent()
        )

        // tokens
        database.execSQL("DROP TABLE tokens")
        database.execSQL(
            """
                CREATE TABLE IF NOT EXISTS `tokens` (
                `symbol` TEXT NOT NULL,
                `dollarRate` TEXT,
                `recentRateChange` TEXT,
                PRIMARY KEY(`symbol`)
                )
            """.trimIndent()
        )

        // staking state
        database.execSQL("DROP TABLE account_staking_accesses")
        database.execSQL(
            """
                CREATE TABLE IF NOT EXISTS `account_staking_accesses` (
                `chainId` TEXT NOT NULL,
                `chainAssetId` TEXT NOT NULL,
                `accountId` BLOB NOT NULL,
                `stashId` BLOB,
                `controllerId` BLOB,
                PRIMARY KEY(`chainId`, `chainAssetId`, `accountId`)
                )
            """.trimIndent()
        )

        // operationsMi
        database.execSQL("DROP TABLE operations")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `operations` (`id` TEXT NOT NULL,
            `address` TEXT NOT NULL,
            `chainId` TEXT NOT NULL,
            `chainAssetId` TEXT NOT NULL,
            `time` INTEGER NOT NULL,
            `status` INTEGER NOT NULL,
            `source` INTEGER NOT NULL,
            `operationType` INTEGER NOT NULL,
            `module` TEXT,
            `call` TEXT,
            `amount` TEXT,
            `sender` TEXT,
            `receiver` TEXT,
            `hash` TEXT,
            `fee` TEXT,
            `isReward` INTEGER,
            `era` INTEGER,
            `validator` TEXT,
            PRIMARY KEY(`id`, `address`, `chainId`, `chainAssetId`)
            )
            """.trimIndent()
        )

        database.execSQL("DROP TABLE IF EXISTS chain_assets")

        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `chain_assets` (
            `id` TEXT NOT NULL,
            `chainId` TEXT NOT NULL,
            `name` TEXT NOT NULL,
            `icon` TEXT NOT NULL,
            `precision` INTEGER NOT NULL,
            `priceId` TEXT,
            `staking` TEXT NOT NULL,
            `priceProviders` TEXT,
            `nativeChainId` TEXT,
            PRIMARY KEY(`chainId`, `id`),
            FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_assets_chainId` ON `chain_assets` (`chainId`)")
    }
}

val AddChainRegistryTables_27_28 = object : Migration(27, 28) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("DROP TABLE IF EXISTS chains")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `chains` (
            `id` TEXT NOT NULL,
            `parentId` TEXT,
            `name` TEXT NOT NULL,
            `icon` TEXT NOT NULL,
            `prefix` INTEGER NOT NULL,
            `isEthereumBased` INTEGER NOT NULL,
            `isTestNet` INTEGER NOT NULL,
            `hasCrowdloans` INTEGER NOT NULL,
            `url` TEXT,
            `overridesCommon` INTEGER,
            `staking_url` TEXT,
            `staking_type` TEXT,
            `history_url` TEXT,
            `history_type` TEXT,
            `crowdloans_url` TEXT,
            `crowdloans_type` TEXT,
            PRIMARY KEY(`id`))
            """.trimIndent()
        )

        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `chain_nodes` (
            `chainId` TEXT NOT NULL,
            `url` TEXT NOT NULL,
            `name` TEXT NOT NULL,
            PRIMARY KEY(`chainId`, `url`),
            FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL("""CREATE INDEX IF NOT EXISTS `index_chain_nodes_chainId` ON `chain_nodes` (`chainId`)""")

        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `chain_assets` (
            `id` INTEGER NOT NULL,
            `chainId` TEXT NOT NULL,
            `name` TEXT NOT NULL,
            `symbol` TEXT NOT NULL,
            `precision` INTEGER NOT NULL,
            `priceId` TEXT,
            `staking` TEXT NOT NULL,
            PRIMARY KEY(`chainId`, `id`),
            FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_assets_chainId` ON `chain_assets` (`chainId`)")

        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `chain_runtimes` (
            `chainId` TEXT NOT NULL,
            `syncedVersion` INTEGER NOT NULL,
            `remoteVersion` INTEGER NOT NULL, 
            PRIMARY KEY(`chainId`)
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_runtimes_chainId` ON `chain_runtimes` (`chainId`)")

        database.execSQL("DROP TABLE IF EXISTS `runtimeCache`")

        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `meta_accounts` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `substratePublicKey` BLOB NOT NULL,
            `substrateCryptoType` TEXT NOT NULL,
            `substrateAccountId` BLOB NOT NULL,
            `ethereumPublicKey` BLOB,
            `ethereumAddress` BLOB,
            `name` TEXT NOT NULL,
            `isSelected` INTEGER NOT NULL,
            `position` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_meta_accounts_substrateAccountId` ON `meta_accounts` (`substrateAccountId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_meta_accounts_ethereumAddress` ON `meta_accounts` (`ethereumAddress`)")

        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `chain_accounts` (
            `metaId` INTEGER NOT NULL,
            `chainId` TEXT NOT NULL,
            `publicKey` BLOB NOT NULL,
            `accountId` BLOB NOT NULL,
            `cryptoType` TEXT NOT NULL,
            PRIMARY KEY(`metaId`, `chainId`),
            FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION  DEFERRABLE INITIALLY DEFERRED,
            FOREIGN KEY(`metaId`) REFERENCES `meta_accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE 
            )
            """.trimIndent()
        )

        database.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_accounts_chainId` ON `chain_accounts` (`chainId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_accounts_metaId` ON `chain_accounts` (`metaId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_accounts_accountId` ON `chain_accounts` (`accountId`)")
    }
}

val AddOperationsTablesToDb_23_24 = object : Migration(23, 24) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
                DROP TABLE IF EXISTS `transactions`
            """.trimIndent()
        )

        database.execSQL(
            """
                CREATE TABLE IF NOT EXISTS `operations` (
                `id` TEXT NOT NULL,
                `address` TEXT NOT NULL,
                `time` INTEGER NOT NULL,
                `tokenType` INTEGER NOT NULL,
                `status` INTEGER NOT NULL,
                `source` INTEGER NOT NULL,
                `operationType` INTEGER NOT NULL,
                `module` TEXT,
                `call` TEXT,
                `amount` TEXT,
                `sender` TEXT,
                `receiver` TEXT,
                `hash` TEXT,
                `fee` TEXT,
                `isReward` INTEGER,
                `era` INTEGER,
                `validator` TEXT,
                PRIMARY KEY(`id`, `address`)
            )
            """.trimIndent()
        )
    }
}

val RemoveStakingRewardsTable_22_23 = object : Migration(22, 23) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("DROP TABLE IF EXISTS `staking_rewards`")

        // totalReward nullable -> not null
        database.execSQL("DROP TABLE IF EXISTS `total_reward`")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `total_reward` (
                `accountAddress` TEXT NOT NULL, 
                `totalReward` TEXT  NOT NULL, 
                 PRIMARY KEY(`accountAddress`))
            """.trimIndent()
        )
    }
}

val AddTotalRewardsTableToDb_21_22 = object : Migration(21, 22) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `total_reward` (
                `accountAddress` TEXT NOT NULL, 
                `totalReward` TEXT, 
                 PRIMARY KEY(`accountAddress`))
            """.trimIndent()
        )
    }
}

val RemoveAccountForeignKeyFromAsset_17_18 = object : Migration(17, 18) {

    override fun migrate(database: SupportSQLiteDatabase) {
        database.beginTransaction()

        database.execSQL("DROP INDEX IF EXISTS index_assets_accountAddress")
        database.execSQL("ALTER TABLE assets RENAME TO _assets")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `assets` (
                `token` INTEGER NOT NULL,
                `accountAddress` TEXT NOT NULL,
                `freeInPlanks` TEXT NOT NULL,
                `reservedInPlanks` TEXT NOT NULL,
                `miscFrozenInPlanks` TEXT NOT NULL,
                `feeFrozenInPlanks` TEXT NOT NULL,
                `bondedInPlanks` TEXT NOT NULL,
                `redeemableInPlanks` TEXT NOT NULL,
                `unbondingInPlanks` TEXT NOT NULL,
                PRIMARY KEY(`token`, `accountAddress`),
                FOREIGN KEY(`token`)
                REFERENCES `tokens`(`type`) ON UPDATE NO ACTION ON DELETE NO ACTION
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX `index_assets_accountAddress` ON `assets` (`accountAddress`)")
        database.execSQL("INSERT INTO assets SELECT * FROM _assets")
        database.execSQL("DROP TABLE _assets")

        database.setTransactionSuccessful()
        database.endTransaction()
    }
}

val ChangePrimaryKeyForRewards_16_17 = object : Migration(16, 17) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("DROP TABLE staking_rewards")

        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `staking_rewards` (
            `accountAddress` TEXT NOT NULL,
            `eventId` TEXT NOT NULL,
            `blockNumber` INTEGER NOT NULL,
            `extrinsicIndex` INTEGER NOT NULL,
            `extrinsicHash` TEXT NOT NULL,
            `moduleId` TEXT NOT NULL,
            `params` TEXT NOT NULL,
            `eventIdx` INTEGER NOT NULL,
            `eventIndex` TEXT NOT NULL,
            `amountInPlanks` TEXT NOT NULL,
            `blockTimestamp` INTEGER NOT NULL,
            `slashKton` TEXT NOT NULL,
            PRIMARY KEY(`accountAddress`, `blockNumber`, `eventIdx`))
            """.trimIndent()
        )

        database.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_staking_rewards_accountAddress` ON `staking_rewards` (`accountAddress`)
            """.trimIndent()
        )
    }
}

val AddStakingRewardsTable_15_16 = object : Migration(15, 16) {

    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `staking_rewards` (
                `accountAddress` TEXT NOT NULL,
                `eventId` TEXT NOT NULL,
                `blockNumber` INTEGER NOT NULL,
                `extrinsicIndex` INTEGER NOT NULL,
                `extrinsicHash` TEXT NOT NULL,
                `moduleId` TEXT NOT NULL,
                `params` TEXT NOT NULL,
                `eventIndex` TEXT NOT NULL,
                `amountInPlanks` TEXT NOT NULL,
                `blockTimestamp` INTEGER NOT NULL,
                `slashKton` TEXT NOT NULL,
                PRIMARY KEY(`accountAddress`, `blockNumber`, `extrinsicIndex`)
            )
            """.trimIndent()
        )

        database.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_staking_rewards_accountAddress` ON `staking_rewards` (`accountAddress`)
            """.trimIndent()
        )
    }
}

val AddAccountStakingTable_14_15 = object : Migration(14, 15) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `account_staking_accesses` (
                `address` TEXT NOT NULL,
                `stashId` BLOB,
                `controllerId` BLOB,
                PRIMARY KEY(`address`),
                FOREIGN KEY(`address`) REFERENCES `users`(`address`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }
}

val AddNetworkTypeToStorageCache_13_14 = object : Migration(13, 14) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("DROP TABLE storage")

        database.execSQL(
            """
            CREATE TABLE `storage` (
                `storageKey` TEXT NOT NULL,
                `networkType` INTEGER NOT NULL,
                `content` TEXT,
                `runtimeVersion` INTEGER NOT NULL,
                PRIMARY KEY(`storageKey`, `networkType`)
            )
        """
        )
    }
}

val AddStorageCacheTable_12_13 = object : Migration(12, 13) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE `storage` (
                `storageKey` TEXT NOT NULL,
                `content` TEXT,
                `runtimeVersion` INTEGER NOT NULL,
                PRIMARY KEY(`storageKey`)
            )
            """.trimIndent()
        )
    }
}

val AddRuntimeCacheTable_11_12 = object : Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE `runtimeCache` (
                `networkName` TEXT NOT NULL PRIMARY KEY,
                `latestKnownVersion` INTEGER NOT NULL,
                `latestAppliedVersion` INTEGER NOT NULL,
                `typesVersion` INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

val AddPhishingAddressesTable_10_11 = object : Migration(10, 11) {

    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE `phishing_addresses` (
            `publicKey` TEXT NOT NULL,
            PRIMARY KEY(`publicKey`) );
            """.trimIndent()
        )
    }
}

val AddTokenTable_9_10 = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE `tokens` (
            `type` INTEGER NOT NULL,
            `dollarRate` TEXT,
            `recentRateChange` TEXT,
            PRIMARY KEY(`type`) );
            """.trimIndent()
        )

        database.execSQL("DROP TABLE assets")

        database.execSQL(
            """
            CREATE TABLE `assets` (
            `token` INTEGER NOT NULL,
            `accountAddress` TEXT NOT NULL,
            `freeInPlanks` TEXT NOT NULL,
            `reservedInPlanks` TEXT NOT NULL,
            `miscFrozenInPlanks` TEXT NOT NULL,
            `feeFrozenInPlanks` TEXT NOT NULL,
            `bondedInPlanks` TEXT NOT NULL,
            `redeemableInPlanks` TEXT NOT NULL,
            `unbondingInPlanks` TEXT NOT NULL,
            PRIMARY KEY(`token`, `accountAddress`),
            FOREIGN KEY(`accountAddress`) REFERENCES `users`(`address`) ON UPDATE NO ACTION ON DELETE CASCADE,
            FOREIGN KEY(`token`) REFERENCES `tokens`(`type`) ON UPDATE NO ACTION ON DELETE NO ACTION );"""
                .trimIndent()
        )

        database.execSQL("CREATE INDEX index_assets_accountAddress ON assets(accountAddress);")
    }
}
