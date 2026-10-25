package jp.co.soramitsu.coredb.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val PREVIOUS_VERSION = 80
private const val NORMALIZED_VERSION = 81

/**
 * Releases 3.5.x through 4.2.0 (DB 68-75) hid every empty asset automatically once the wallet
 * held any balance, and stored that as `enabled = 0`, the same value a deliberate user hide
 * writes. Every surviving asset row comes from such a build because 65 -> 68 emptied the table,
 * and the automatic hide only ever selected rows whose balance was unloaded or zero.
 *
 * A row that is still hidden and still empty in every balance column returns to the nullable
 * automatic state, so the portfolio zero rule and later deposits decide its visibility again.
 * Any funded, negative (load error) or unparsable component keeps the row hidden: it may be a
 * deliberate hide of a funded asset, which a migration must never reveal on the user's behalf.
 * Rows marked as not needed keep their explicit state as well.
 *
 * Only `enabled` changes. Nothing is deleted, no secret is read, the schema is unchanged and a
 * second run matches no row.
 */
internal object LegacyAutoHideNormalizationMigration : Migration(PREVIOUS_VERSION, NORMALIZED_VERSION) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            UPDATE `assets` SET `enabled` = NULL
            WHERE `enabled` = 0
              AND `markedNotNeed` = 0
              AND (`freeInPlanks` IS NULL OR `freeInPlanks` = '0')
              AND (`reservedInPlanks` IS NULL OR `reservedInPlanks` = '0')
              AND (`miscFrozenInPlanks` IS NULL OR `miscFrozenInPlanks` = '0')
              AND (`feeFrozenInPlanks` IS NULL OR `feeFrozenInPlanks` = '0')
              AND (`bondedInPlanks` IS NULL OR `bondedInPlanks` = '0')
              AND (`redeemableInPlanks` IS NULL OR `redeemableInPlanks` = '0')
              AND (`unbondingInPlanks` IS NULL OR `unbondingInPlanks` = '0')
            """.trimIndent()
        )
    }
}
