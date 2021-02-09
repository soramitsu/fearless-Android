package jp.co.soramitsu.core_db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object AddPhishingAddressesTable : Migration(10, 11) {

    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE `phishing_addresses` (
            `publicKey` TEXT NOT NULL,
            PRIMARY KEY(`publicKey`) );
        """.trimIndent())
    }
}