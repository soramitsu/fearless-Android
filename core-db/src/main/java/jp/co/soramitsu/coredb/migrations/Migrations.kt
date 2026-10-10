package jp.co.soramitsu.coredb.migrations

import android.database.Cursor
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private data class HistoricalForeignKey(
    val parentTable: String,
    val childColumn: String,
    val parentColumn: String,
    val onUpdate: String,
    val onDelete: String
)

private fun migrationRowCount(
    db: SupportSQLiteDatabase,
    query: String,
    description: String
): Long {
    return db.query(query).use { cursor ->
        check(cursor.moveToFirst()) {
            "$description did not return a count"
        }
        check(cursor.getType(0) == Cursor.FIELD_TYPE_INTEGER) {
            "$description returned an invalid count"
        }
        val count = cursor.getLong(0)
        check(!cursor.moveToNext()) {
            "$description returned more than one count"
        }
        check(count >= 0L) {
            "$description returned a negative count"
        }
        count
    }
}

private fun requireMigrationRowCount(
    expected: Long,
    actual: Long,
    description: String
) {
    check(actual == expected) {
        "$description changed from $expected to $actual rows"
    }
}

private fun requireNoMigrationRows(
    db: SupportSQLiteDatabase,
    query: String,
    description: String
) {
    db.query(query).use { cursor ->
        check(!cursor.moveToFirst()) {
            description
        }
    }
}

private fun requireKnownHistoricalForeignKeys(
    db: SupportSQLiteDatabase,
    tableName: String,
    allowedVariants: Set<Set<HistoricalForeignKey>>
) {
    check(
        tableName.isNotEmpty() &&
            tableName.all { it == '_' || it.isLetterOrDigit() }
    )
    check(allowedVariants.isNotEmpty())
    val observed = linkedSetOf<HistoricalForeignKey>()
    db.query("PRAGMA foreign_key_list(`$tableName`)").use { cursor ->
        var rowCount = 0
        while (cursor.moveToNext()) {
            check(rowCount < MAX_HISTORICAL_FOREIGN_KEYS) {
                "$tableName has too many foreign keys"
            }
            rowCount += 1
            check(
                cursor.getType(cursor.getColumnIndexOrThrow("table")) ==
                    Cursor.FIELD_TYPE_STRING &&
                    cursor.getType(cursor.getColumnIndexOrThrow("from")) ==
                    Cursor.FIELD_TYPE_STRING &&
                    cursor.getType(cursor.getColumnIndexOrThrow("to")) ==
                    Cursor.FIELD_TYPE_STRING &&
                    cursor.getType(cursor.getColumnIndexOrThrow("on_update")) ==
                    Cursor.FIELD_TYPE_STRING &&
                    cursor.getType(cursor.getColumnIndexOrThrow("on_delete")) ==
                    Cursor.FIELD_TYPE_STRING
            ) {
                "$tableName has an unreadable foreign-key declaration"
            }
            check(
                observed.add(
                    HistoricalForeignKey(
                        parentTable = cursor.getString(
                            cursor.getColumnIndexOrThrow("table")
                        ),
                        childColumn = cursor.getString(
                            cursor.getColumnIndexOrThrow("from")
                        ),
                        parentColumn = cursor.getString(
                            cursor.getColumnIndexOrThrow("to")
                        ),
                        onUpdate = cursor.getString(
                            cursor.getColumnIndexOrThrow("on_update")
                        ),
                        onDelete = cursor.getString(
                            cursor.getColumnIndexOrThrow("on_delete")
                        )
                    )
                )
            ) {
                "$tableName has duplicate foreign-key declarations"
            }
        }
    }
    check(observed in allowedVariants) {
        "$tableName has an unknown historical foreign-key layout"
    }
}

private fun requireNoHistoricalForeignKeyViolations(
    db: SupportSQLiteDatabase,
    tableName: String
) {
    check(
        tableName.isNotEmpty() &&
            tableName.all { it == '_' || it.isLetterOrDigit() }
    )
    requireNoMigrationRows(
        db = db,
        query = "PRAGMA foreign_key_check(`$tableName`)",
        description = "$tableName contains foreign-key violations"
    )
}

private const val MAX_HISTORICAL_FOREIGN_KEYS = 8

/*
 * Historical 18 -> 19 added the now-obsolete nodes.isActive column while
 * moving the selected node from preferences. Preserve the schema change until
 * 30 -> 31 removes that legacy `nodes` table after the chain registry exists.
 */
val AddLegacyActiveNodeColumn_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE nodes ADD COLUMN `isActive` INTEGER NOT NULL DEFAULT 0"
        )
    }
}

/*
 * Versions 19 -> 21 and 24 -> 27 only refreshed historical bundled `nodes`
 * cache content. Those cache implementations and their preference migrators
 * no longer exist. Exact released 26/27 databases already include the newer
 * chain registry beside `nodes`, so 27 -> 28 only ensures the registry tables
 * exist; it must not replace their wallet-referenced `chains` rows. Explicit
 * adjacent edges keep durable wallet/account state intact. The obsolete
 * `nodes` table is removed at 30 -> 31 and disposable registry endpoints are
 * authoritatively refreshed at 39 -> 40.
 */
private fun legacyNodeCacheCompatibilityMigration(startVersion: Int) =
    object : Migration(startVersion, startVersion + 1) {
        override fun migrate(db: SupportSQLiteDatabase) = Unit
    }

val LegacyNodeCacheCompatibility_19_20 = legacyNodeCacheCompatibilityMigration(19)
val LegacyNodeCacheCompatibility_20_21 = legacyNodeCacheCompatibilityMigration(20)
val LegacyNodeCacheCompatibility_24_25 = legacyNodeCacheCompatibilityMigration(24)
val LegacyNodeCacheCompatibility_25_26 = legacyNodeCacheCompatibilityMigration(25)
val LegacyNodeCacheCompatibility_26_27 = legacyNodeCacheCompatibilityMigration(26)

val Migration_75_76 = object : Migration(75, 76) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chains ADD COLUMN `xcm` TEXT NULL DEFAULT NULL")
    }
}

val Migration_73_74 = object : Migration(73, 74) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chain_assets ADD COLUMN `coinbaseUrl` TEXT NULL DEFAULT NULL")
        db.execSQL("DROP TABLE IF EXISTS `sora_card`")
    }
}

val Migration_74_75 = object : Migration(74, 75) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `sora_card`")
    }
}

val Migration_72_73 = object : Migration(72, 73) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `ton_connection` (
            `metaId` INTEGER NOT NULL,
            `clientId` TEXT NOT NULL, 
            `name` TEXT NOT NULL, 
            `icon` TEXT NOT NULL, 
            `url` TEXT NOT NULL, 
            `source` TEXT NOT NULL,
            PRIMARY KEY(`metaId`, `url`, `source`), 
            FOREIGN KEY(`metaId`) REFERENCES `meta_accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE 
            )
            """.trimMargin()
        )

        db.execSQL("ALTER TABLE chains ADD COLUMN `tonBridgeUrl` TEXT NULL DEFAULT NULL")

        val legacyUserCount = migrationRowCount(
            db = db,
            query = "SELECT COUNT(*) FROM `users`",
            description = "Legacy user recovery source"
        )
        requireNoMigrationRows(
            db = db,
            query =
            "SELECT 1 FROM sqlite_master " +
                "WHERE type = 'table' AND name = 'legacy_users_recovery' " +
                "LIMIT 1",
            description = "Legacy user recovery ledger already exists"
        )
        db.execSQL(
            """
            CREATE TABLE `legacy_users_recovery` (
            `address` TEXT NOT NULL,
            `username` TEXT NOT NULL,
            `publicKey` TEXT NOT NULL,
            `cryptoType` INTEGER NOT NULL,
            `position` INTEGER NOT NULL,
            `recoveryState` TEXT NOT NULL,
            PRIMARY KEY(`address`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `legacy_users_recovery` (
                `address`,
                `username`,
                `publicKey`,
                `cryptoType`,
                `position`,
                `recoveryState`
            )
            SELECT
                `address`,
                `username`,
                `publicKey`,
                `cryptoType`,
                `position`,
                'PRESERVED_PENDING_REVIEW'
            FROM `users`
            """.trimIndent()
        )
        requireMigrationRowCount(
            expected = legacyUserCount,
            actual = migrationRowCount(
                db = db,
                query = "SELECT COUNT(*) FROM `legacy_users_recovery`",
                description = "Legacy user recovery ledger"
            ),
            description = "Legacy user recovery ledger"
        )
        db.execSQL("DROP TABLE `users`")
    }
}

val Migration_70_71 = object : Migration(70, 71) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chains ADD COLUMN `remoteAssetsSource` TEXT NULL DEFAULT NULL")
    }
}

val Migration_69_70 = object : Migration(69, 70) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DELETE FROM storage")
    }
}

val Migration_68_69 = object : Migration(68, 69) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `nomis_wallet_score` (
            `metaId` INTEGER NOT NULL,
            `score` INTEGER NOT NULL,
            `updated` INTEGER NOT NULL,
            `nativeBalanceUsd` TEXT NOT NULL,
            `holdTokensUsd` TEXT NOT NULL,
            `walletAgeInMonths` INTEGER NOT NULL,
            `totalTransactions` INTEGER NOT NULL,
            `rejectedTransactions` INTEGER NOT NULL,
            `avgTransactionTimeInHours` REAL NOT NULL,
            `maxTransactionTimeInHours` REAL NOT NULL,
            `minTransactionTimeInHours` REAL NOT NULL,
            `scoredAt` TEXT NOT NULL,
            PRIMARY KEY(`metaId`),
            FOREIGN KEY(`metaId`) REFERENCES `meta_accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE 
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `allpools` (
            `tokenIdBase` TEXT NOT NULL, 
            `tokenIdTarget` TEXT NOT NULL, 
            `reserveBase` TEXT NOT NULL, 
            `reserveTarget` TEXT NOT NULL, 
            `totalIssuance` TEXT NOT NULL, 
            `reservesAccount` TEXT NOT NULL, 
            PRIMARY KEY(`tokenIdBase`, `tokenIdTarget`))
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `userpools` (
            `userTokenIdBase` TEXT NOT NULL, 
            `userTokenIdTarget` TEXT NOT NULL,
            `accountAddress` TEXT NOT NULL,
            `poolProvidersBalance` TEXT NOT NULL,
            PRIMARY KEY(`userTokenIdBase`, `userTokenIdTarget`, `accountAddress`),
            FOREIGN KEY(`userTokenIdBase`, `userTokenIdTarget`) REFERENCES `allpools`(`tokenIdBase`, `tokenIdTarget`) ON UPDATE NO ACTION ON DELETE CASCADE 
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_userpools_accountAddress` ON `userpools` (`accountAddress`)")
    }
}

val Migration_67_68 = object : Migration(67, 68) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE meta_accounts SET initialized = 0")
        db.execSQL("UPDATE chain_accounts SET initialized = 0")
        db.execSQL("DELETE FROM assets")
    }
}

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
        val expectedRetainedRows = migrationRowCount(
            db = db,
            query =
            "SELECT COUNT(*) FROM (" +
                "SELECT 1 FROM `address_book` " +
                "GROUP BY `address`, `chainId`" +
                ")",
            description = "Address-book unique identity groups"
        )
        db.execSQL(
            """
            DELETE FROM `address_book`
            WHERE EXISTS (
                SELECT 1
                FROM `address_book` AS `newer`
                WHERE `newer`.`address` = `address_book`.`address`
                  AND `newer`.`chainId` = `address_book`.`chainId`
                  AND (
                      `newer`.`created` > `address_book`.`created`
                      OR (
                          `newer`.`created` = `address_book`.`created`
                          AND `newer`.`id` > `address_book`.`id`
                      )
                  )
            )
            """.trimIndent()
        )
        requireMigrationRowCount(
            expected = expectedRetainedRows,
            actual = migrationRowCount(
                db = db,
                query = "SELECT COUNT(*) FROM `address_book`",
                description = "Deduplicated address book"
            ),
            description = "Address-book deterministic deduplication"
        )
        requireNoMigrationRows(
            db = db,
            query =
            "SELECT 1 FROM `address_book` AS `older` " +
                "INNER JOIN `address_book` AS `newer` " +
                "ON `newer`.`address` = `older`.`address` " +
                "AND `newer`.`chainId` = `older`.`chainId` " +
                "AND (`newer`.`created` > `older`.`created` " +
                "OR (`newer`.`created` = `older`.`created` " +
                "AND `newer`.`id` > `older`.`id`)) LIMIT 1",
            description = "Address-book deduplication retained an older row"
        )

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
        db.execSQL("DROP TABLE IF EXISTS `sora_card`")
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

        val chainForeignKeyVariants = setOf("chains", "_chains").mapTo(
            linkedSetOf()
        ) { parentTable ->
            setOf(
                HistoricalForeignKey(
                    parentTable = parentTable,
                    childColumn = "chainId",
                    parentColumn = "id",
                    onUpdate = "NO ACTION",
                    onDelete = "CASCADE"
                )
            )
        }
        requireKnownHistoricalForeignKeys(
            db = db,
            tableName = "chain_nodes",
            allowedVariants = chainForeignKeyVariants
        )
        requireKnownHistoricalForeignKeys(
            db = db,
            tableName = "chain_explorers",
            allowedVariants = chainForeignKeyVariants
        )
        val chainAccountForeignKeyVariants = setOf("chains", "_chains").mapTo(
            linkedSetOf()
        ) { parentTable ->
            setOf(
                HistoricalForeignKey(
                    parentTable = parentTable,
                    childColumn = "chainId",
                    parentColumn = "id",
                    onUpdate = "NO ACTION",
                    onDelete = "NO ACTION"
                ),
                HistoricalForeignKey(
                    parentTable = "meta_accounts",
                    childColumn = "metaId",
                    parentColumn = "id",
                    onUpdate = "NO ACTION",
                    onDelete = "CASCADE"
                )
            )
        }
        requireKnownHistoricalForeignKeys(
            db = db,
            tableName = "chain_accounts",
            allowedVariants = chainAccountForeignKeyVariants
        )
        requireNoMigrationRows(
            db = db,
            query =
            "SELECT 1 FROM `chain_nodes` AS `child` " +
                "LEFT JOIN `chains` AS `parent` " +
                "ON `parent`.`id` = `child`.`chainId` " +
                "WHERE `parent`.`id` IS NULL LIMIT 1",
            description = "Chain-node rows reference missing chains"
        )
        requireNoMigrationRows(
            db = db,
            query =
            "SELECT 1 FROM `chain_explorers` AS `child` " +
                "LEFT JOIN `chains` AS `parent` " +
                "ON `parent`.`id` = `child`.`chainId` " +
                "WHERE `parent`.`id` IS NULL LIMIT 1",
            description = "Chain-explorer rows reference missing chains"
        )
        requireNoMigrationRows(
            db = db,
            query =
            "SELECT 1 FROM `chain_accounts` AS `child` " +
                "LEFT JOIN `chains` AS `chain_parent` " +
                "ON `chain_parent`.`id` = `child`.`chainId` " +
                "LEFT JOIN `meta_accounts` AS `meta_parent` " +
                "ON `meta_parent`.`id` = `child`.`metaId` " +
                "WHERE `chain_parent`.`id` IS NULL " +
                "OR `meta_parent`.`id` IS NULL LIMIT 1",
            description = "Chain-account rows reference missing public identities"
        )

        val chainCount = migrationRowCount(
            db = db,
            query = "SELECT COUNT(*) FROM `chains`",
            description = "Chains before 45 to 46 migration"
        )
        val chainNodeCount = migrationRowCount(
            db = db,
            query = "SELECT COUNT(*) FROM `chain_nodes`",
            description = "Chain nodes before 45 to 46 migration"
        )
        val chainExplorerCount = migrationRowCount(
            db = db,
            query = "SELECT COUNT(*) FROM `chain_explorers`",
            description = "Chain explorers before 45 to 46 migration"
        )
        val chainAccountCount = migrationRowCount(
            db = db,
            query = "SELECT COUNT(*) FROM `chain_accounts`",
            description = "Chain accounts before 45 to 46 migration"
        )

        db.execSQL("DROP TABLE IF EXISTS temp.`_migration_45_chain_nodes`")
        db.execSQL(
            """
            CREATE TEMP TABLE `_migration_45_chain_nodes` (
            `chainId` TEXT NOT NULL,
            `url` TEXT NOT NULL,
            `name` TEXT NOT NULL,
            `isActive` INTEGER NOT NULL,
            `isDefault` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO temp.`_migration_45_chain_nodes`
            (`chainId`, `url`, `name`, `isActive`, `isDefault`)
            SELECT `chainId`, `url`, `name`, `isActive`, `isDefault`
            FROM `chain_nodes`
            """.trimIndent()
        )
        requireMigrationRowCount(
            expected = chainNodeCount,
            actual = migrationRowCount(
                db = db,
                query = "SELECT COUNT(*) FROM temp.`_migration_45_chain_nodes`",
                description = "Chain-node migration snapshot"
            ),
            description = "Chain-node migration snapshot"
        )

        db.execSQL("DROP TABLE IF EXISTS temp.`_migration_45_chain_explorers`")
        db.execSQL(
            """
            CREATE TEMP TABLE `_migration_45_chain_explorers` (
            `chainId` TEXT NOT NULL,
            `type` TEXT NOT NULL,
            `types` TEXT NOT NULL,
            `url` TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO temp.`_migration_45_chain_explorers`
            (`chainId`, `type`, `types`, `url`)
            SELECT `chainId`, `type`, `types`, `url`
            FROM `chain_explorers`
            """.trimIndent()
        )
        requireMigrationRowCount(
            expected = chainExplorerCount,
            actual = migrationRowCount(
                db = db,
                query =
                "SELECT COUNT(*) FROM temp.`_migration_45_chain_explorers`",
                description = "Chain-explorer migration snapshot"
            ),
            description = "Chain-explorer migration snapshot"
        )

        db.execSQL("DROP TABLE IF EXISTS temp.`_migration_45_chain_accounts`")
        db.execSQL(
            """
            CREATE TEMP TABLE `_migration_45_chain_accounts` (
            `metaId` INTEGER NOT NULL,
            `chainId` TEXT NOT NULL,
            `publicKey` BLOB NOT NULL,
            `accountId` BLOB NOT NULL,
            `cryptoType` TEXT NOT NULL,
            `name` TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO temp.`_migration_45_chain_accounts`
            (`metaId`, `chainId`, `publicKey`, `accountId`, `cryptoType`, `name`)
            SELECT `metaId`, `chainId`, `publicKey`, `accountId`, `cryptoType`, `name`
            FROM `chain_accounts`
            """.trimIndent()
        )
        requireMigrationRowCount(
            expected = chainAccountCount,
            actual = migrationRowCount(
                db = db,
                query =
                "SELECT COUNT(*) FROM temp.`_migration_45_chain_accounts`",
                description = "Chain-account migration snapshot"
            ),
            description = "Chain-account migration snapshot"
        )

        // Runtime-derived balance and registry-asset caches are reconstructible.
        // Public chains, custom endpoints, and chain-account signing identities
        // are user state and must survive this repair edge.
        db.execSQL("DELETE FROM `chain_assets`")
        db.execSQL("DELETE FROM `assets`")

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
        db.execSQL(
            """
            INSERT INTO `chain_nodes`
            (`chainId`, `url`, `name`, `isActive`, `isDefault`)
            SELECT `chainId`, `url`, `name`, `isActive`, `isDefault`
            FROM temp.`_migration_45_chain_nodes`
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
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chain_explorers_chainId` ON `chain_explorers` (`chainId`)")
        db.execSQL(
            """
            INSERT INTO `chain_explorers`
            (`chainId`, `type`, `types`, `url`)
            SELECT `chainId`, `type`, `types`, `url`
            FROM temp.`_migration_45_chain_explorers`
            """.trimIndent()
        )

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
        db.execSQL(
            """
            INSERT INTO `chain_accounts`
            (`metaId`, `chainId`, `publicKey`, `accountId`, `cryptoType`, `name`)
            SELECT `metaId`, `chainId`, `publicKey`, `accountId`, `cryptoType`, `name`
            FROM temp.`_migration_45_chain_accounts`
            """.trimIndent()
        )

        requireMigrationRowCount(
            expected = chainCount,
            actual = migrationRowCount(
                db = db,
                query = "SELECT COUNT(*) FROM `chains`",
                description = "Chains after 45 to 46 migration"
            ),
            description = "Chain preservation during 45 to 46 migration"
        )
        requireMigrationRowCount(
            expected = chainNodeCount,
            actual = migrationRowCount(
                db = db,
                query = "SELECT COUNT(*) FROM `chain_nodes`",
                description = "Chain nodes after 45 to 46 migration"
            ),
            description = "Chain-node preservation during 45 to 46 migration"
        )
        requireMigrationRowCount(
            expected = chainExplorerCount,
            actual = migrationRowCount(
                db = db,
                query = "SELECT COUNT(*) FROM `chain_explorers`",
                description = "Chain explorers after 45 to 46 migration"
            ),
            description = "Chain-explorer preservation during 45 to 46 migration"
        )
        requireMigrationRowCount(
            expected = chainAccountCount,
            actual = migrationRowCount(
                db = db,
                query = "SELECT COUNT(*) FROM `chain_accounts`",
                description = "Chain accounts after 45 to 46 migration"
            ),
            description = "Chain-account preservation during 45 to 46 migration"
        )
        requireNoHistoricalForeignKeyViolations(db, "chain_nodes")
        requireNoHistoricalForeignKeyViolations(db, "chain_explorers")
        requireNoHistoricalForeignKeyViolations(db, "chain_accounts")

        db.execSQL("DROP TABLE temp.`_migration_45_chain_nodes`")
        db.execSQL("DROP TABLE temp.`_migration_45_chain_explorers`")
        db.execSQL("DROP TABLE temp.`_migration_45_chain_accounts`")
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
        // The registry is refreshable, but chain_accounts are wallet-owned
        // identities. Preserve the minimum parent rows they require before
        // rebuilding the registry; otherwise a legitimate released database
        // reaches 45 -> 46 with orphan chain accounts and cannot open.
        db.execSQL(
            "DROP TABLE IF EXISTS temp.`_migration_39_wallet_chains`"
        )
        db.execSQL(
            """
            CREATE TEMP TABLE `_migration_39_wallet_chains` AS
            SELECT
                c.id,
                c.parentId,
                c.name,
                c.icon,
                c.prefix,
                c.isEthereumBased,
                c.isTestNet,
                c.hasCrowdloans,
                c.url,
                c.overridesCommon,
                c.staking_url,
                c.staking_type,
                c.history_url,
                c.history_type,
                c.crowdloans_url,
                c.crowdloans_type
            FROM chains AS c
            WHERE EXISTS(
                SELECT 1
                FROM chain_accounts AS ca
                WHERE ca.chainId = c.id
            )
            """.trimIndent()
        )
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
        db.execSQL(
            """
            INSERT INTO chains(
                id,
                parentId,
                name,
                minSupportedVersion,
                icon,
                prefix,
                isEthereumBased,
                isTestNet,
                hasCrowdloans,
                url,
                overridesCommon,
                staking_url,
                staking_type,
                history_url,
                history_type,
                crowdloans_url,
                crowdloans_type
            )
            SELECT
                id,
                parentId,
                name,
                NULL,
                icon,
                prefix,
                isEthereumBased,
                isTestNet,
                hasCrowdloans,
                url,
                overridesCommon,
                staking_url,
                staking_type,
                history_url,
                history_type,
                crowdloans_url,
                crowdloans_type
            FROM temp.`_migration_39_wallet_chains`
            """.trimIndent()
        )
        db.execSQL(
            "DROP TABLE temp.`_migration_39_wallet_chains`"
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
        requireKnownHistoricalForeignKeys(
            db = db,
            tableName = "chain_accounts",
            allowedVariants = setOf(
                setOf(
                    HistoricalForeignKey(
                        parentTable = "chains",
                        childColumn = "chainId",
                        parentColumn = "id",
                        onUpdate = "NO ACTION",
                        onDelete = "NO ACTION"
                    ),
                    HistoricalForeignKey(
                        parentTable = "meta_accounts",
                        childColumn = "metaId",
                        parentColumn = "id",
                        onUpdate = "NO ACTION",
                        onDelete = "CASCADE"
                    )
                )
            )
        )
        requireNoMigrationRows(
            db = db,
            query =
            "SELECT 1 FROM `chain_accounts` AS `child` " +
                "LEFT JOIN `chains` AS `chain_parent` " +
                "ON `chain_parent`.`id` = `child`.`chainId` " +
                "LEFT JOIN `meta_accounts` AS `meta_parent` " +
                "ON `meta_parent`.`id` = `child`.`metaId` " +
                "WHERE `chain_parent`.`id` IS NULL " +
                "OR `meta_parent`.`id` IS NULL LIMIT 1",
            description = "Version 35 chain accounts reference missing identities"
        )
        val chainAccountCount = migrationRowCount(
            db = db,
            query = "SELECT COUNT(*) FROM `chain_accounts`",
            description = "Version 35 chain accounts"
        )
        db.execSQL("DROP TABLE IF EXISTS temp.`_migration_35_chain_accounts`")
        db.execSQL(
            """
            CREATE TEMP TABLE `_migration_35_chain_accounts` (
            `metaId` INTEGER NOT NULL,
            `chainId` TEXT NOT NULL,
            `publicKey` BLOB NOT NULL,
            `accountId` BLOB NOT NULL,
            `cryptoType` TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO temp.`_migration_35_chain_accounts`
            (`metaId`, `chainId`, `publicKey`, `accountId`, `cryptoType`)
            SELECT `metaId`, `chainId`, `publicKey`, `accountId`, `cryptoType`
            FROM `chain_accounts`
            """.trimIndent()
        )
        requireMigrationRowCount(
            expected = chainAccountCount,
            actual = migrationRowCount(
                db = db,
                query =
                "SELECT COUNT(*) FROM temp.`_migration_35_chain_accounts`",
                description = "Version 35 chain-account snapshot"
            ),
            description = "Version 35 chain-account snapshot"
        )

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
        db.execSQL(
            """
            INSERT INTO `chain_accounts`
            (`metaId`, `chainId`, `publicKey`, `accountId`, `cryptoType`, `name`)
            SELECT
                `metaId`,
                `chainId`,
                `publicKey`,
                `accountId`,
                `cryptoType`,
                ''
            FROM temp.`_migration_35_chain_accounts`
            """.trimIndent()
        )
        requireMigrationRowCount(
            expected = chainAccountCount,
            actual = migrationRowCount(
                db = db,
                query = "SELECT COUNT(*) FROM `chain_accounts`",
                description = "Version 36 chain accounts"
            ),
            description = "Chain-account preservation during 35 to 36 migration"
        )
        requireNoHistoricalForeignKeyViolations(db, "chain_accounts")
        db.execSQL("DROP TABLE temp.`_migration_35_chain_accounts`")

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
        // The released 26/27 schemas already contain chains, chain_nodes, and
        // chain_accounts. Dropping only their chains parent while Room has
        // foreign keys disabled leaves otherwise-valid wallet identities as
        // orphans and makes the later 31 -> 32 safety preflight reject the
        // database. Keep those released parent rows. The intentional registry
        // cache refresh at 39 -> 40 separately prunes nodes/assets while
        // retaining every chain referenced by a wallet-owned chain account.
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
