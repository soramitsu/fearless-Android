package jp.co.soramitsu.common.wallet

import jp.co.soramitsu.common.utils.format
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import java.math.BigDecimal

fun BigDecimal.formatWithMaxPrecision(type: Token.Type): String {
    return "${format(type.maximumPrecision)} ${type.displayName}"
}

fun BigDecimal.formatWithDefaultPrecision(type: Token.Type): String {
    return "${format()} ${type.displayName}"
}