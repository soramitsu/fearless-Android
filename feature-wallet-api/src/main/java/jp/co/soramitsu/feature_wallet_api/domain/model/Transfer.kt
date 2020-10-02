package jp.co.soramitsu.feature_wallet_api.domain.model

import java.math.BigDecimal

class Transfer(
    val recipient: String,
    val amount: BigDecimal,
    val token: Asset.Token
) {

    val amountInPlanks = amount.scaleByPowerOfTen(token.mantissa).toBigIntegerExact()
}