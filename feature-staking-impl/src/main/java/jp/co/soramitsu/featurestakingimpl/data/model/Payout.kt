package jp.co.soramitsu.featurestakingimpl.data.model

import java.math.BigInteger

class Payout(
    val validatorAddress: String,
    val era: BigInteger,
    val amount: BigInteger
)
