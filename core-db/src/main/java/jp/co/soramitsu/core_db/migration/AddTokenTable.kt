package jp.co.soramitsu.core_db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object AddTokenTable : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE `tokens` (
            `type` INTEGER NOT NULL,
            `dollarRate` TEXT,
            `recentRateChange` TEXT,
            PRIMARY KEY(`type`) );
        """.trimIndent())

        database.execSQL("DROP TABLE assets")

        database.execSQL("""
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
            .trimIndent())

        database.execSQL("CREATE INDEX index_assets_accountAddress ON assets(accountAddress);")
    }
}