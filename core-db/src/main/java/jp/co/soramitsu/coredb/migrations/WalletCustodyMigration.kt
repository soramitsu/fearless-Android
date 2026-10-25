package jp.co.soramitsu.coredb.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val PREVIOUS_VERSION = 77
private const val CUSTODY_VERSION = 78

/** Existing rows deliberately receive no marker: absent provenance is UNKNOWN. */
internal object WalletCustodyMigration : Migration(PREVIOUS_VERSION, CUSTODY_VERSION) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `wallet_custody` (
                `metaId` INTEGER NOT NULL,
                `kind` TEXT NOT NULL,
                `publicIdentitySha256` BLOB NOT NULL,
                PRIMARY KEY(`metaId`),
                FOREIGN KEY(`metaId`) REFERENCES `meta_accounts`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }
}
