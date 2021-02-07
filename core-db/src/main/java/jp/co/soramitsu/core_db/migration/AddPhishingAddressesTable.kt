package jp.co.soramitsu.core_db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object AddPhishingAddressesTable : Migration(10, 11) {

    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE `phishing_addresses` (
            `id` INTEGER NOT NULL,
            `publicKey` TEXT NOT NULL,
            PRIMARY KEY(`id`) );
        """.trimIndent())
    }
}