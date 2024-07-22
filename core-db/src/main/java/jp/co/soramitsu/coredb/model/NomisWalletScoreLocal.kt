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
    ],
)
data class NomisWalletScoreLocal(
    val metaId: Long,
    val score: Int,
    val updated: Long,
    val nativeBalanceUsd: BigDecimal,
    val holdTokensUsd: BigDecimal,
    val walletAge: Long,
    val totalTransactions: Long,
    val rejectedTransactions: Long,
    val avgTransactionTime: Long,
    val maxTransactionTime: Long,
    val minTransactionTime: Long
)