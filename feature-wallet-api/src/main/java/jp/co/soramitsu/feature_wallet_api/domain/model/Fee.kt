package jp.co.soramitsu.feature_wallet_api.domain.model

import java.math.BigInteger

class Fee(amountInPlanks: BigInteger?, val token: Asset.Token) {
    val amount = amountInPlanks?.toBigDecimal(scale = token.mantissa)
}