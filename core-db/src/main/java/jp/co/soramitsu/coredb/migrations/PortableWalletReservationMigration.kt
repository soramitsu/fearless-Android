package jp.co.soramitsu.coredb.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val PREVIOUS_VERSION = 78
private const val RESERVATION_VERSION = 79

/** A reservation has no wallet FK: an interrupted receive must survive without a wallet row. */
internal object PortableWalletReservationMigration : Migration(PREVIOUS_VERSION, RESERVATION_VERSION) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `portable_wallet_reservations` (
                `metaId` INTEGER NOT NULL,
                `operationId` TEXT NOT NULL,
                `afterImageSha256` TEXT NOT NULL,
                `idSetSha256` TEXT NOT NULL,
                `state` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`metaId`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_portable_wallet_reservations_operationId` " +
                "ON `portable_wallet_reservations` (`operationId`)"
        )
        installInsertFence(db)
    }

    /** Also installed on fresh databases, whose Room-created tables skip migrations. */
    fun installInsertFence(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS `portable_wallet_reserved_id_insert_fence`
            AFTER INSERT ON `meta_accounts`
            WHEN EXISTS(
                SELECT 1 FROM `portable_wallet_reservations` WHERE `metaId` = NEW.`id`
            )
            BEGIN
                SELECT RAISE(ABORT, 'portable wallet ID is reserved');
            END
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS `portable_wallet_reserved_id_update_fence`
            BEFORE UPDATE OF `id` ON `meta_accounts`
            WHEN NEW.`id` != OLD.`id` AND EXISTS(
                SELECT 1 FROM `portable_wallet_reservations` WHERE `metaId` = NEW.`id`
            )
            BEGIN
                SELECT RAISE(ABORT, 'portable wallet ID is reserved');
            END
            """.trimIndent()
        )
    }
}
