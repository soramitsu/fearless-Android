package jp.co.soramitsu.feature_staking_api.domain.model

import java.math.BigInteger

data class Round(
    val current: BigInteger,
    val first: BigInteger,
    val length: BigInteger
)
