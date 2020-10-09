package jp.co.soramitsu.feature_wallet_api.domain.model

import java.math.BigDecimal
import java.math.BigInteger

class Fee(
    val amountInPlanks: BigInteger?,
    val token: Asset.Token
) {
    constructor(
        amount: BigDecimal,
        token: Asset.Token
    ) : this(
        amount.scaleByPowerOfTen(token.mantissa).toBigIntegerExact(),
        token
    )

    val amount = amountInPlanks?.toBigDecimal(scale = token.mantissa)
}