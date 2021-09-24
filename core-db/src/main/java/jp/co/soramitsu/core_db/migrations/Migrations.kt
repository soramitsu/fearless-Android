package jp.co.soramitsu.core_db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import jp.co.soramitsu.common.utils.asBoolean
import jp.co.soramitsu.core_db.model.NodeLocal
import jp.co.soramitsu.core_db.prepopulate.nodes.defaultNodesInsertQuery

class UpdateDefaultNodesList(
    private val nodesList: List<NodeLocal>,
    fromVersion: Int,
) : Migration(fromVersion, fromVersion + 1) {

    init {
        require(nodesList.all { it.isActive.not() }) {
            "Nodes should not be active by default"
        }
    }

    /**
     * Replacing default set of nodes, taking care of active node:
     *      If active node is default one, then it will be changed to first default node from new set with the same network type
     *      If active node is not default, it will remain active, since it 100% wont be deleted
     *      If there are no active node (clean start), nothing will happen
     */
    override fun migrate(database: SupportSQLiteDatabase) {
        database.beginTransaction()

        val activeNodeLinkCursor = database.query("SELECT networkType, isDefault FROM nodes WHERE isActive = 1")

        val (activeNodeNetworkType, isActiveNodeDefault) = if (activeNodeLinkCursor.moveToNext()) {
            val networkType = activeNodeLinkCursor.getInt(activeNodeLinkCursor.getColumnIndex("networkType"))
            val isDefaultInt = activeNodeLinkCursor.getInt(activeNodeLinkCursor.getColumnIndex("isDefault"))

            networkType to isDefaultInt.asBoolean()
        } else null to null

        activeNodeLinkCursor.close()

        val modifiedNodesList = if (activeNodeNetworkType != null && isActiveNodeDefault == true) {
            val mutableNodesList = nodesList.toMutableList()

            val firstRelevantDefaultNode = nodesList.first { it.networkType == activeNodeNetworkType && it.isDefault }
            val indexOfRelevantNode = nodesList.indexOf(firstRelevantDefaultNode)

            mutableNodesList[indexOfRelevantNode] = firstRelevantDefaultNode.copy(isActive = true)

            mutableNodesList
        } else {
            nodesList
        }

        database.execSQL("DELETE FROM nodes WHERE isDefault = 1")
        database.execSQL(defaultNodesInsertQuery(modifiedNodesList))

        database.setTransactionSuccessful()
        database.endTransaction()
    }
}

val MigrateTablesToV2_27_28 = object : Migration(27, 28) {
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
                `accountId` BLOB NOT NULL,
                `stashId` BLOB,
                `controllerId` BLOB,
                PRIMARY KEY(`chainId`, `accountId`)
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
            `chainAssetId` INTEGER NOT NULL,
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
    }
}

val AddChainRegistryTables_25_26 = object : Migration(25, 26) {
    override fun migrate(database: SupportSQLiteDatabase) {
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
            `url` TEXT,
            `overridesCommon` INTEGER,
            `staking_url` TEXT,
            `staking_type` TEXT,
            `history_url` TEXT,
            `history_type` TEXT,
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
            `ethereumAddress` TEXT,
            `name` TEXT NOT NULL,
            `isSelected` INTEGER NOT NULL)
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
            PRIMARY KEY(`metaId`,
            `chainId`),
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

@Suppress("ClassName")
class MoveActiveNodeTrackingToDb_18_19(private val migrator: PrefsToDbActiveNodeMigrator) : Migration(18, 19) {

    override fun migrate(database: SupportSQLiteDatabase) {
        database.beginTransaction()

        database.execSQL("ALTER TABLE nodes ADD COLUMN `isActive` INTEGER NOT NULL DEFAULT 0")

        migrator.migrate(database)

        database.setTransactionSuccessful()
        database.endTransaction()
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
