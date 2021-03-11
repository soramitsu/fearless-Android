package jp.co.soramitsu.feature_wallet_api.domain.model

import java.math.BigDecimal

class Fee(
    val transferAmount: BigDecimal,
    val feeAmount: BigDecimal,
    val type: Token.Type
)