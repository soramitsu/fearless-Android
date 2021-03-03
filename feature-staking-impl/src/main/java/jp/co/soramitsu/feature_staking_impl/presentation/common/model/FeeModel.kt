package jp.co.soramitsu.feature_staking_impl.presentation.common.model

import java.math.BigDecimal

class FeeModel(
    val fee: BigDecimal,
    val displayToken: String,
    val displayFiat: String?
)