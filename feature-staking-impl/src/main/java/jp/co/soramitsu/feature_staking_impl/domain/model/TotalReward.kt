package jp.co.soramitsu.feature_staking_impl.domain.model

import java.math.BigDecimal

class TotalReward(
    val accountAddress: String,
    val totalReward: BigDecimal
)
