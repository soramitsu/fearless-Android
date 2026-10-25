package jp.co.soramitsu.coredb.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * A pending cohort's local IDs must remain bound to the exact source wallet IDs and historical
 * positions after process death. Existing v79 reservations stay nullable and require explicit
 * reconciliation; this migration never infers source identity from an absent journal.
 */
internal object PortableWalletOriginReservationMigration : Migration(79, 80) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `portable_wallet_reservations` ADD COLUMN `portableIdHex` TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE `portable_wallet_reservations` ADD COLUMN `sourcePosition` INTEGER DEFAULT NULL")
        PortableWalletReservationMigration.installInsertFence(db)
    }
}
