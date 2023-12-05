package jp.co.soramitsu.staking.impl.data.model

import java.math.BigInteger

data class Payout(
    val validatorAddress: String,
    val era: BigInteger,
    val amount: BigInteger
)
