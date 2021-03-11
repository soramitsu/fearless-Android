package jp.co.soramitsu.feature_wallet_api.presentation.formatters

import java.math.BigDecimal
import jp.co.soramitsu.common.utils.format
import jp.co.soramitsu.feature_wallet_api.domain.model.Token

fun BigDecimal.formatWithMaxPrecision(type: Token.Type): String {
    return "${format(type.maximumPrecision)} ${type.displayName}"
}

fun BigDecimal.formatWithDefaultPrecision(type: Token.Type): String {
    return "${format()} ${type.displayName}"
}
