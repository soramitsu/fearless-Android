package jp.co.soramitsu.staking.impl.data.model

import java.math.BigInteger

data class PoolUnbonding(
    val era: BigInteger,
    val amount: BigInteger
)
