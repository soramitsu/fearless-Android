package jp.co.soramitsu.feature_staking_impl.domain.model

import java.math.BigInteger

data class PendingPayout(
    val validatorAddress: String,
    val era: BigInteger,
    val amount: BigInteger
)
