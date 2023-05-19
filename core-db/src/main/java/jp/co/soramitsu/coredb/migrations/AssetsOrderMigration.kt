package jp.co.soramitsu.coredb.migrations

import android.annotation.SuppressLint
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.coredb.model.chain.MetaAccountLocal
import jp.co.soramitsu.shared_utils.runtime.AccountId
import java.math.BigDecimal
import java.math.BigInteger

class AssetsOrderMigration : Migration(34, 35) {

    private fun selectSortingModelsSqlQuery(metaId: Long) = """
        SELECT 
            a.tokenSymbol, a.chainId, a.accountId, a.freeInPlanks, a.reservedInPlanks, 
            t.dollarRate, 
            ca.precision, 
            c.isTestNet, c.name 
        FROM assets AS a 
            inner join tokens as t ON a.tokenSymbol = t.symbol 
            inner join chain_assets as ca ON ca.chainId = a.chainId 
            inner join chains as c ON c.id = a.chainId 
        WHERE a.metaId = $metaId
    """.trimIndent()

    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE assets ADD COLUMN `sortIndex` INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE assets ADD COLUMN `enabled` INTEGER NOT NULL DEFAULT 1")

        val accounts = getAccountIds(database)
        accounts.forEach { metaId ->
            val models = database.getMigrationModels(metaId)

            val sortedModels = models.sortedWith(
                compareByDescending<SortingModel> { it.totalBalance > BigDecimal.ZERO }
                    .thenByDescending { it.totalFiat ?: BigDecimal.ZERO }
                    .thenBy { it.isTestNet }
                    .thenByDescending { it.isPolkadotOrKusama }
                    .thenBy { it.chainName }
            )

            sortedModels.forEachIndexed { index, sortingModel ->
                database.updateSortIndexForAsset(sortingModel, index)
            }
        }
    }

    @SuppressLint("Range")
    private fun getAccountIds(database: SupportSQLiteDatabase): List<Long> {
        return database.query("SELECT id FROM meta_accounts").let {
            it.map {
                getLong(getColumnIndex(MetaAccountLocal.Table.Column.ID))
            }
        }
    }

    @SuppressLint("Range")
    private fun SupportSQLiteDatabase.getMigrationModels(metaId: Long): List<SortingModel> {
        val polkadotChainId = "91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3"
        val kusamaChainId = "b0a8d493285c2df73290dfb7e61f870f17b41801197a149ca93654499ea3dafe"

        return query(selectSortingModelsSqlQuery(metaId)).let {
            it.map {
                val tokenSymbol = getString(getColumnIndex("tokenSymbol"))
                val chainId = getString(getColumnIndex("chainId"))
                val accountId = getBlob(getColumnIndex("accountId"))

                val freeInPlanks = BigInteger(getString(getColumnIndex("freeInPlanks")))
                val reservedInPlanks = BigInteger(getString(getColumnIndex("reservedInPlanks")))
                val dollarRate = getString(getColumnIndex("dollarRate"))?.let { dollarRate -> BigDecimal(dollarRate) }
                val precision = getInt(getColumnIndex("precision"))
                val isTestNet = getInt(getColumnIndex("isTestNet"))
                val name = getString(getColumnIndex("name"))

                val totalBalanceInPlanks = freeInPlanks + reservedInPlanks
                val totalBalance = totalBalanceInPlanks.toBigDecimal(precision)

                SortingModel(
                    tokenSymbol = tokenSymbol,
                    chainId = chainId,
                    accountId = accountId,
                    totalBalance = totalBalance,
                    totalFiat = dollarRate?.multiply(totalBalance),
                    isTestNet = isTestNet == 1,
                    isPolkadotOrKusama = chainId == polkadotChainId || chainId == kusamaChainId,
                    chainName = name
                )
            }
        }
    }

    private fun SupportSQLiteDatabase.updateSortIndexForAsset(asset: SortingModel, index: Int) {
        val contentValues = ContentValues().apply {
            put("sortIndex", index)
        }

        update(
            "assets",
            SQLiteDatabase.CONFLICT_REPLACE,
            contentValues,
            "tokenSymbol=? AND chainId=? AND accountId=?",
            arrayOf(asset.tokenSymbol, asset.chainId, asset.accountId)
        )
    }

    private data class SortingModel(
        val tokenSymbol: String,
        val chainId: String,
        val accountId: AccountId,

        val totalBalance: BigDecimal,
        val totalFiat: BigDecimal?,
        val isTestNet: Boolean,
        val isPolkadotOrKusama: Boolean,
        val chainName: String
    )
}
