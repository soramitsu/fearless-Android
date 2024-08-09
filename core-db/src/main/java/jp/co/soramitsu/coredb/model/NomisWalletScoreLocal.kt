package jp.co.soramitsu.coredb.model

import androidx.room.Entity
import androidx.room.ForeignKey
import java.math.BigDecimal
import jp.co.soramitsu.coredb.model.chain.MetaAccountLocal

@Entity(
    tableName = "nomis_wallet_score",
    primaryKeys = ["metaId"],
    foreignKeys = [
        ForeignKey(
            entity = MetaAccountLocal::class,
            parentColumns = ["id"],
            childColumns = ["metaId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class NomisWalletScoreLocal(
    val metaId: Long,
    val score: Int,
    val updated: Long,
    val nativeBalanceUsd: BigDecimal,
    val holdTokensUsd: BigDecimal,
    val walletAgeInMonths: Long,
    val totalTransactions: Long,
    val rejectedTransactions: Long,
    val avgTransactionTimeInHours: Double,
    val maxTransactionTimeInHours: Double,
    val minTransactionTimeInHours: Double,
    val scoredAt: String
) {
    companion object {
        fun loading(metaId: Long) = NomisWalletScoreLocal(
            metaId = metaId,
            score = -1,
            updated = 0,
            nativeBalanceUsd = BigDecimal.ZERO,
            holdTokensUsd = BigDecimal.ZERO,
            walletAgeInMonths = 0,
            totalTransactions = 0,
            rejectedTransactions = 0,
            avgTransactionTimeInHours = 0.0,
            maxTransactionTimeInHours = 0.0,
            minTransactionTimeInHours = 0.0,
            scoredAt = ""
        )

        fun error(metaId: Long) = NomisWalletScoreLocal(
            metaId = metaId,
            score = -2,
            updated = 0,
            nativeBalanceUsd = BigDecimal.ZERO,
            holdTokensUsd = BigDecimal.ZERO,
            walletAgeInMonths = 0,
            totalTransactions = 0,
            rejectedTransactions = 0,
            avgTransactionTimeInHours = 0.0,
            maxTransactionTimeInHours = 0.0,
            minTransactionTimeInHours = 0.0,
            scoredAt = ""
        )
    }
}