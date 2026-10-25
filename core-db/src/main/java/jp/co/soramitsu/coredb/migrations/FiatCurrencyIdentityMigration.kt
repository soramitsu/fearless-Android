package jp.co.soramitsu.coredb.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Preserve legacy quotes byte-for-byte; only a fresh provider response can supply their currency identity. */
internal object FiatCurrencyIdentityMigration : Migration(83, 84) {
    const val ADD_CURRENCY_ID = "ALTER TABLE token_price ADD COLUMN fiatCurrencyId TEXT"

    override fun migrate(db: SupportSQLiteDatabase) {
        check(db.inTransaction()) { "Fiat currency identity migration requires a transaction" }
        db.execSQL(ADD_CURRENCY_ID)
    }
}
