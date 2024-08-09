package jp.co.soramitsu.common.data.network.nomis

import com.google.gson.annotations.SerializedName

data class NomisResponse(
    val data: NomisResponseData
)

data class NomisResponseData(
    val address: String,
    val score: Double,
    val stats: NomisStats
)

data class NomisStats(
    val nativeBalanceUSD: Double,
    val holdTokensBalanceUSD: Double,
    @SerializedName("walletAge")
    val walletAgeInMonths: Long,
    val totalTransactions: Long,
    val totalRejectedTransactions: Long,
    @SerializedName("averageTransactionTime")
    val averageTransactionTimeInHours: Double,
    @SerializedName("maxTransactionTime")
    val maxTransactionTimeInHours: Double,
    @SerializedName("minTransactionTime")
    val minTransactionTimeInHours: Double,
    val scoredAt: String
)
