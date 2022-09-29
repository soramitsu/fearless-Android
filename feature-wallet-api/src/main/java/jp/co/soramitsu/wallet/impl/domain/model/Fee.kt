package jp.co.soramitsu.wallet.impl.domain.model

import java.math.BigDecimal

class Fee(
    val transferAmount: BigDecimal,
    val feeAmount: BigDecimal
)
