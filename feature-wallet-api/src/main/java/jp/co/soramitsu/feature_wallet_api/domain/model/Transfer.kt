package jp.co.soramitsu.feature_wallet_api.domain.model

import java.math.BigDecimal
import java.math.BigInteger

class Transfer(
    val recipient: String,
    val amount: BigDecimal,
    val type: Token.Type
) {

    val amountInPlanks: BigInteger = type.planksFromAmount(amount)
}