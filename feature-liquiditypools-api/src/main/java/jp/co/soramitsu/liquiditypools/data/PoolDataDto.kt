package jp.co.soramitsu.liquiditypools.data

import java.math.BigInteger

data class PoolDataDto(
    val baseAssetId: String,
    val assetId: String,
    val reservesFirst: BigInteger,
    val reservesSecond: BigInteger,
    val totalIssuance: BigInteger,
    val poolProvidersBalance: BigInteger,
    val reservesAccount: String,
)
