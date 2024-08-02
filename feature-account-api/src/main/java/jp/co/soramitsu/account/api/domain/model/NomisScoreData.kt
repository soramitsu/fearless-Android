package jp.co.soramitsu.account.api.domain.model

import java.math.BigDecimal

data class NomisScoreData(
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
    val scoredAt: Long?
){
    val isError = score == -2
    val isLoading = score == -1
}
