package jp.co.soramitsu.featurewalletapi.presentation.model

import java.math.BigDecimal

class FeeModel(
    val fee: BigDecimal,
    val displayToken: String,
    val displayFiat: String?
)
