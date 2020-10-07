package jp.co.soramitsu.feature_wallet_api.domain.model

import java.math.BigInteger

class Fee(feeInPlanks: BigInteger?, val token: Asset.Token) {
    val fee = feeInPlanks?.toBigDecimal(scale = token.mantissa)
}