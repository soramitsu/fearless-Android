package jp.co.soramitsu.core_db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
