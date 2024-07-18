package jp.co.soramitsu.coredb.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val Migration_66_67 = object : Migration(66, 67) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE meta_accounts SET initialized = 0")
        db.execSQL("UPDATE chain_accounts SET initialized = 0")
        db.execSQL("DELETE FROM assets")
    }
}

val Migration_65_66 = object : Migration(65, 66) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE meta_accounts ADD COLUMN `initialized` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE chain_accounts ADD COLUMN `initialized` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("DELETE FROM assets")
        db.execSQL("ALTER TABLE chains ADD COLUMN `identityChain` TEXT NULL DEFAULT NULL")
    }
}

val Migration_64_65 = object : Migration(64, 65) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DELETE FROM storage")
    }
}

val Migration_63_64 = object : Migration(63, 64) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chains ADD COLUMN `isUsesAppId` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("DELETE FROM storage")
    }
}

val Migration_62_63 = object : Migration(62, 63) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE assets ADD COLUMN `status` TEXT NULL")
        db.execSQL("UPDATE assets SET `status` = 'Frozen' where id == '8f79aa5a-9f31-442c-ac96-01ff80b105e0'")
    }
}

val Migration_61_62 = object : Migration(61, 62) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chains ADD COLUMN `supportNft` INTEGER NOT NULL DEFAULT 0")
    }
}

val Migration_60_61 = object : Migration(60, 61) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chains ADD COLUMN `isChainlinkProvider` INTEGER NOT NULL DEFAULT 0")

        db.execSQL("DROP TABLE IF EXISTS chain_assets")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `chain_assets` (
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
            `ethereumType` TEXT DEFAULT NULL,
            `priceProvider` TEXT, 
            PRIMARY KEY(`chainId`, `id`), 
            FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_assets_chainId` ON `chain_assets` (`chainId`)")
    }
}

val Migration_59_60 = object : Migration(59, 60) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chains ADD COLUMN `paraId` TEXT NULL")
    }
}

val Migration_58_59 = object : Migration(58, 59) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chains ADD COLUMN `rank` INTEGER NULL")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `favorite_chains` (
            `metaId` INTEGER NOT NULL,
            `chainId` TEXT NOT NULL,
            `isFavorite` INTEGER NOT NULL DEFAULT 0,
            PRIMARY KEY(`metaId`, `chainId`),
            FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION  DEFERRABLE INITIALLY DEFERRED,
            FOREIGN KEY(`metaId`) REFERENCES `meta_accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE 
            )
            """.trimIndent()
        )
    }
}

val Migration_57_58 = object : Migration(57, 58) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chains ADD COLUMN `isEthereumChain` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE chain_assets ADD COLUMN `ethereumType` TEXT DEFAULT NULL")
    }
}

val Migration_56_57 = object : Migration(56, 57) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE meta_accounts ADD COLUMN `isBackedUp` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE meta_accounts ADD COLUMN `googleBackupAddress` TEXT DEFAULT NULL")
    }
}

val Migration_55_56 = object : Migration(55, 56) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS chain_assets")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `chain_assets` (
            `id` TEXT NOT NULL, 
            `name` TEXT, 
            `symbol` TEXT NOT NULL, 
            `chainId` TEXT NOT NULL, 
            `icon` TEXT NOT NULL, 
            `priceId` TEXT, 
            `staking` TEXT NOT NULL, 
            `precision` INTEGER NOT NULL, 
            `priceProviders` TEXT, 
            `isUtility` INTEGER, 
            `type` TEXT, 
            `currencyId` TEXT, 
            `existentialDeposit` TEXT, 
            `color` TEXT, 
            `isNative` INTEGER, 
            PRIMARY KEY(`chainId`, `id`), 
            FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_assets_chainId` ON `chain_assets` (`chainId`)")
    }
}

val Migration_54_55 = object : Migration(54, 55) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `_address_book`")
        db.execSQL("CREATE TABLE `_address_book` AS SELECT * FROM `address_book`")
        db.execSQL("DELETE FROM `address_book` where `id` NOT IN (SELECT `id` FROM `_address_book` GROUP BY `address`, `chainId`)")
        db.execSQL("DROP TABLE IF EXISTS `_address_book`")

        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS `index_address_book_address_chainId` ON `address_book` (`address`, `chainId`)
            """.trimIndent()
        )
    }
}

val Migration_53_54 = object : Migration(53, 54) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS _chains")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `_chains` (
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
            `staking_url` TEXT,
            `staking_type` TEXT,
            `history_url` TEXT,
            `history_type` TEXT,
            `crowdloans_url` TEXT,
            `crowdloans_type` TEXT,
            
            PRIMARY KEY(`id`))
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO _chains SELECT 
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
            c.staking_url,
            c.staking_type,
            c.history_url,
            c.history_type,
            c.crowdloans_url,
            c.crowdloans_type
            FROM chains c
            """.trimIndent()
        )

        db.execSQL("DROP TABLE IF EXISTS chains")
        db.execSQL(
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
            `staking_url` TEXT,
            `staking_type` TEXT,
            `history_url` TEXT,
            `history_type` TEXT,
            `crowdloans_url` TEXT,
            `crowdloans_type` TEXT,
            
            PRIMARY KEY(`id`))
            """.trimIndent()
        )

        db.execSQL(
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
            c.staking_url,
            c.staking_type,
            c.history_url,
            c.history_type,
            c.crowdloans_url,
            c.crowdloans_type
            FROM _chains c
            """.trimIndent()
        )
        db.execSQL("DROP TABLE IF EXISTS _chains")

        // to be sure that foreign keys to Chain table is correct we recreate them

        // chain_nodes
        db.execSQL("DROP TABLE IF EXISTS _chain_nodes")
        db.execSQL(
            """
             CREATE TABLE IF NOT EXISTS `_chain_nodes` (
             `chainId` TEXT NOT NULL, 
             `url` TEXT NOT NULL, 
             `name` TEXT NOT NULL, 
             `isActive` INTEGER NOT NULL, 
             `isDefault` INTEGER NOT NULL, 
             PRIMARY KEY(`chainId`, `url`)
             )
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT INTO _chain_nodes SELECT 
            cn.chainId,
            cn.url,
            cn.name,
            cn.isActive,
            cn.isDefault
            FROM chain_nodes cn
            """.trimIndent()
        )

        db.execSQL("DROP TABLE IF EXISTS chain_nodes")
        db.execSQL(
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
        db.execSQL(
            """
            INSERT INTO chain_nodes SELECT 
            cn.chainId,
            cn.url,
            cn.name,
            cn.isActive,
            cn.isDefault
            FROM _chain_nodes cn
            """.trimIndent()
        )
        db.execSQL("DROP TABLE IF EXISTS _chain_nodes")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_nodes_chainId` ON `chain_nodes` (`chainId`)")

        // assets
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
            a.chainAccountName
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
            PRIMARY KEY(`id`, `chainId`, `accountId`, `metaId`), 
            FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE 
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
            a.chainAccountName
            FROM _assets a
            """.trimIndent()
        )
        db.execSQL("DROP TABLE IF EXISTS _assets")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_assets_metaId` ON `assets` (`metaId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_assets_chainId` ON `assets` (`chainId`)")

        // chain_explorers
        db.execSQL("DROP TABLE IF EXISTS _chain_explorers")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `_chain_explorers` (
            `chainId` TEXT NOT NULL,
            `type` TEXT NOT NULL,
            `types` TEXT NOT NULL,
            `url` TEXT NOT NULL,
            PRIMARY KEY(`chainId`, `type`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO _chain_explorers SELECT 
            ce.chainId,
            ce.type,
            ce.types,
            ce.url
            FROM chain_explorers ce
            """.trimIndent()
        )

        db.execSQL("DROP TABLE IF EXISTS chain_explorers")
        db.execSQL(
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
        db.execSQL(
            """
            INSERT INTO chain_explorers SELECT 
            ce.chainId,
            ce.type,
            ce.types,
            ce.url
            FROM _chain_explorers ce
            """.trimIndent()
        )
        db.execSQL("DROP TABLE IF EXISTS _chain_explorers")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_explorers_chainId` ON `chain_explorers` (`chainId`)")

        // chain_accounts
        db.execSQL("DROP TABLE IF EXISTS _chain_accounts")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `_chain_accounts` (
            `metaId` INTEGER NOT NULL,
            `chainId` TEXT NOT NULL,
            `publicKey` BLOB NOT NULL,
            `accountId` BLOB NOT NULL,
            `cryptoType` TEXT NOT NULL,
            `name` TEXT NOT NULL,
            PRIMARY KEY(`metaId`, `chainId`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO _chain_accounts SELECT 
            ca.metaId,
            ca.chainId,
            ca.publicKey,
            ca.accountId,
            ca.cryptoType,
            ca.name
            FROM chain_accounts ca
            """.trimIndent()
        )

        db.execSQL("DROP TABLE IF EXISTS chain_accounts")
        db.execSQL(
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

        db.execSQL(
            """
            INSERT INTO chain_accounts SELECT 
            ca.metaId,
            ca.chainId,
            ca.publicKey,
            ca.accountId,
            ca.cryptoType,
            ca.name
            FROM _chain_accounts ca
            """.trimIndent()
        )
        db.execSQL("DROP TABLE IF EXISTS _chain_accounts")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_accounts_chainId` ON `chain_accounts` (`chainId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_accounts_metaId` ON `chain_accounts` (`metaId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_accounts_accountId` ON `chain_accounts` (`accountId`)")

        // chain_assets
        db.execSQL("DROP TABLE IF EXISTS _chain_assets")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `_chain_assets` (
            `id` TEXT NOT NULL,
            `name` TEXT, 
            `symbol` TEXT NOT NULL, 
            `displayName` TEXT, 
            `chainId` TEXT NOT NULL, 
            `icon` TEXT NOT NULL, 
            `priceId` TEXT, 
            `staking` TEXT NOT NULL, 
            `precision` INTEGER NOT NULL, 
            `priceProviders` TEXT, 
            `isUtility` INTEGER, 
            `type` TEXT, 
            `currencyId` TEXT, 
            `existentialDeposit` TEXT, 
            `color` TEXT, 
            `isNative` INTEGER, 
            PRIMARY KEY(`chainId`, `id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO _chain_assets SELECT 
            ca.id,
            ca.name,
            ca.symbol,
            ca.displayName,
            ca.chainId,
            ca.icon,
            ca.priceId,
            ca.staking,
            ca.precision,
            ca.priceProviders,
            ca.isUtility,
            ca.type,
            ca.currencyId,
            ca.existentialDeposit,
            ca.color,
            ca.isNative
            FROM chain_assets ca
            """.trimIndent()
        )

        db.execSQL("DROP TABLE IF EXISTS chain_assets")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `chain_assets` (
            `id` TEXT NOT NULL,
            `name` TEXT, 
            `symbol` TEXT NOT NULL, 
            `displayName` TEXT, 
            `chainId` TEXT NOT NULL, 
            `icon` TEXT NOT NULL, 
            `priceId` TEXT, 
            `staking` TEXT NOT NULL, 
            `precision` INTEGER NOT NULL, 
            `priceProviders` TEXT, 
            `isUtility` INTEGER, 
            `type` TEXT, 
            `currencyId` TEXT, 
            `existentialDeposit` TEXT, 
            `color` TEXT, 
            `isNative` INTEGER, 
            PRIMARY KEY(`chainId`, `id`), 
            FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO chain_assets SELECT 
            ca.id,
            ca.name,
            ca.symbol,
            ca.displayName,
            ca.chainId,
            ca.icon,
            ca.priceId,
            ca.staking,
            ca.precision,
            ca.priceProviders,
            ca.isUtility,
            ca.type,
            ca.currencyId,
            ca.existentialDeposit,
            ca.color,
            ca.isNative
            FROM _chain_assets ca
            """.trimIndent()
        )
        db.execSQL("DROP TABLE IF EXISTS _chain_assets")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_assets_chainId` ON `chain_assets` (`chainId`)")
    }
}

val Migration_52_53 = object : Migration(52, 53) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
             CREATE TABLE IF NOT EXISTS `chain_types` (
             `chainId` TEXT NOT NULL, 
             `typesConfig` TEXT NOT NULL,
             PRIMARY KEY(`chainId`)
             )
            """.trimIndent()
        )
    }
}

val Migration_51_52 = object : Migration(51, 52) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE assets RENAME TO _assets")
        db.execSQL("DROP TABLE IF EXISTS assets")
        // new table with nullable enabled field
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
            PRIMARY KEY(`id`, `chainId`, `accountId`, `metaId`), 
            FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE 
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
            a.chainAccountName
            FROM _assets a
            """.trimIndent()
        )
        db.execSQL(
            """
            UPDATE assets SET enabled = NULL WHERE enabled = 1
            """.trimIndent()
        )
        db.execSQL("DROP TABLE IF EXISTS _assets")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_assets_metaId` ON `assets` (`metaId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_assets_chainId` ON `assets` (`chainId`)")
    }
}

val Migration_50_51 = object : Migration(50, 51) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
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
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chain_assets ADD COLUMN `name` TEXT DEFAULT NULL")
    }
}

val Migration_48_49 = object : Migration(48, 49) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chain_assets ADD COLUMN `isNative` INTEGER DEFAULT NULL")
    }
}

val Migration_47_48 = object : Migration(47, 48) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chain_assets ADD COLUMN `color` TEXT DEFAULT NULL")
    }
}

val Migration_46_47 = object : Migration(46, 47) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE operations ADD COLUMN `liquidityFee` TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE operations ADD COLUMN `market` TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE operations ADD COLUMN `targetAssetId` TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE operations ADD COLUMN `targetAmount` TEXT DEFAULT NULL")

        db.execSQL("DELETE FROM operations")
    }
}

val Migration_45_46 = object : Migration(45, 46) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // on some devices FOREIGN KEY(`chainId`) REFERENCES to `_chains` table.
        // So we need to recreate all the tables with new FK which were created after the renaming chains to _chains (Migration_41_42)
        // assets - done in Migration_42_43
        // chain_assets - done in Migration_42_43
        // chain_explorers - done here
        // chain_nodes - done here
        // chain_accounts - done here

        // delete all data related to chains and assets - emulating cold start with existing accounts
        db.execSQL("DELETE FROM chains")
        db.execSQL("DELETE FROM chain_assets")
        db.execSQL("DELETE FROM assets")

        db.execSQL("DROP TABLE IF EXISTS chain_nodes")
        db.execSQL(
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
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_nodes_chainId` ON `chain_nodes` (`chainId`)")

        db.execSQL("DROP TABLE IF EXISTS chain_explorers")
        db.execSQL(
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
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_explorers_chainId` ON `chain_explorers` (`chainId`)")

        db.execSQL("DROP TABLE chain_accounts")
        db.execSQL(
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
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_accounts_chainId` ON `chain_accounts` (`chainId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_accounts_metaId` ON `chain_accounts` (`metaId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_accounts_accountId` ON `chain_accounts` (`accountId`)")
    }
}

val Migration_44_45 = object : Migration(44, 45) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
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
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS phishing_addresses")
        db.execSQL(
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
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS chain_assets")

        db.execSQL(
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
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_assets_chainId` ON `chain_assets` (`chainId`)")

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
            `enabled` INTEGER NOT NULL DEFAULT 1, 
            `markedNotNeed` INTEGER NOT NULL DEFAULT 0, 
            `chainAccountName` TEXT, 
            PRIMARY KEY(`id`, `chainId`, `accountId`, `metaId`), 
            FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE 
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_assets_metaId` ON `assets` (`metaId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_assets_chainId` ON `assets` (`chainId`)")

        db.execSQL("DROP TABLE IF EXISTS tokens")
        db.execSQL(
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
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chains RENAME TO _chains")
        db.execSQL("DROP TABLE IF EXISTS chains")
        db.execSQL(
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
        db.execSQL(
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
        db.execSQL("DROP TABLE IF EXISTS _chains")
    }
}

val AssetsMigration_40_41 = object : Migration(40, 41) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE assets RENAME TO _assets")
        db.execSQL(
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

        db.execSQL(
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
        db.execSQL("DROP TABLE _assets")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_assets_metaId` ON `assets` (`metaId`)")
    }
}

val ChainAssetsMigration_39_40 = object : Migration(39, 40) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DELETE FROM chain_explorers")
        db.execSQL("DELETE FROM chain_assets")
        db.execSQL("DELETE FROM chain_nodes")

        db.execSQL("DROP TABLE IF EXISTS chains")
        db.execSQL(
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
    override fun migrate(db: SupportSQLiteDatabase) {
        db.beginTransaction()

        db.execSQL("ALTER TABLE assets RENAME TO _assets")
        db.execSQL(
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

        db.execSQL(
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
        db.execSQL("DROP TABLE _assets")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_assets_metaId` ON `assets` (`metaId`)")

        db.setTransactionSuccessful()
        db.endTransaction()
    }
}

val DifferentCurrenciesMigrations_37_38 = object : Migration(37, 38) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.beginTransaction()

        db.execSQL("DROP TABLE tokens")
        db.execSQL(
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
        db.setTransactionSuccessful()
        db.endTransaction()
    }
}

val FixAssetsMigration_36_37 = object : Migration(36, 37) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.beginTransaction()

        db.execSQL("ALTER TABLE assets RENAME TO _assets")
        db.execSQL(
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

        db.execSQL(
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
        db.execSQL("DROP TABLE _assets")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_assets_metaId` ON `assets` (`metaId`)")

        db.setTransactionSuccessful()
        db.endTransaction()
    }
}

val RemoveLegacyData_35_36 = object : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE chain_accounts")

        db.execSQL(
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

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_accounts_chainId` ON `chain_accounts` (`chainId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_accounts_metaId` ON `chain_accounts` (`metaId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_accounts_accountId` ON `chain_accounts` (`accountId`)")

        // remove `networkType` INTEGER NOT NULL
        db.execSQL("ALTER TABLE users RENAME TO _users")
        db.execSQL(
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
        db.execSQL("INSERT INTO users SELECT address, username, publicKey, cryptoType, position FROM _users")
        db.execSQL("DROP TABLE _users")
    }
}

val AddChainExplorersTable_33_34 = object : Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
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
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_explorers_chainId` ON `chain_explorers` (`chainId`)")
    }
}

val MigrateTablesToV2_32_33 = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // assets
        db.execSQL("DROP TABLE assets")
        db.execSQL(
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
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_assets_metaId` ON `assets` (`metaId`)")
    }
}

val MigrateTablesToV2_30_31 = object : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chain_nodes ADD COLUMN `isActive` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE chain_nodes ADD COLUMN `isDefault` INTEGER NOT NULL DEFAULT 1")

        db.execSQL("DROP TABLE nodes")
    }
}

val MigrateTablesToV2_29_30 = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // assets
        db.execSQL("DROP TABLE assets")
        db.execSQL(
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
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_assets_metaId` ON `assets` (`metaId`)")

        // storage
        db.execSQL("DROP TABLE storage")
        db.execSQL(
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
        db.execSQL("DROP TABLE tokens")
        db.execSQL(
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
        db.execSQL("DROP TABLE account_staking_accesses")
        db.execSQL(
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
        db.execSQL("DROP TABLE operations")
        db.execSQL(
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

        db.execSQL("DROP TABLE IF EXISTS chain_assets")

        db.execSQL(
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
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_assets_chainId` ON `chain_assets` (`chainId`)")
    }
}

val AddChainRegistryTables_27_28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS chains")
        db.execSQL(
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

        db.execSQL(
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
        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_chain_nodes_chainId` ON `chain_nodes` (`chainId`)""")

        db.execSQL(
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
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_assets_chainId` ON `chain_assets` (`chainId`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `chain_runtimes` (
            `chainId` TEXT NOT NULL,
            `syncedVersion` INTEGER NOT NULL,
            `remoteVersion` INTEGER NOT NULL, 
            PRIMARY KEY(`chainId`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_runtimes_chainId` ON `chain_runtimes` (`chainId`)")

        db.execSQL("DROP TABLE IF EXISTS `runtimeCache`")

        db.execSQL(
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
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_meta_accounts_substrateAccountId` ON `meta_accounts` (`substrateAccountId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_meta_accounts_ethereumAddress` ON `meta_accounts` (`ethereumAddress`)")

        db.execSQL(
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

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_accounts_chainId` ON `chain_accounts` (`chainId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_accounts_metaId` ON `chain_accounts` (`metaId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_accounts_accountId` ON `chain_accounts` (`accountId`)")
    }
}

val AddOperationsTablesToDb_23_24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
                DROP TABLE IF EXISTS `transactions`
            """.trimIndent()
        )

        db.execSQL(
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
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `staking_rewards`")

        // totalReward nullable -> not null
        db.execSQL("DROP TABLE IF EXISTS `total_reward`")
        db.execSQL(
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
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
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

    override fun migrate(db: SupportSQLiteDatabase) {
        db.beginTransaction()

        db.execSQL("DROP INDEX IF EXISTS index_assets_accountAddress")
        db.execSQL("ALTER TABLE assets RENAME TO _assets")
        db.execSQL(
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
        db.execSQL("CREATE INDEX `index_assets_accountAddress` ON `assets` (`accountAddress`)")
        db.execSQL("INSERT INTO assets SELECT * FROM _assets")
        db.execSQL("DROP TABLE _assets")

        db.setTransactionSuccessful()
        db.endTransaction()
    }
}

val ChangePrimaryKeyForRewards_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE staking_rewards")

        db.execSQL(
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

        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_staking_rewards_accountAddress` ON `staking_rewards` (`accountAddress`)
            """.trimIndent()
        )
    }
}

val AddStakingRewardsTable_15_16 = object : Migration(15, 16) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
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

        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_staking_rewards_accountAddress` ON `staking_rewards` (`accountAddress`)
            """.trimIndent()
        )
    }
}

val AddAccountStakingTable_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
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
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE storage")

        db.execSQL(
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
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
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
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
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

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE `phishing_addresses` (
            `publicKey` TEXT NOT NULL,
            PRIMARY KEY(`publicKey`) );
            """.trimIndent()
        )
    }
}

val AddTokenTable_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE `tokens` (
            `type` INTEGER NOT NULL,
            `dollarRate` TEXT,
            `recentRateChange` TEXT,
            PRIMARY KEY(`type`) );
            """.trimIndent()
        )

        db.execSQL("DROP TABLE assets")

        db.execSQL(
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

        db.execSQL("CREATE INDEX index_assets_accountAddress ON assets(accountAddress);")
    }
}
