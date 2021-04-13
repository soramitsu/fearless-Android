package jp.co.soramitsu.feature_wallet_api.presentation.formatters

import jp.co.soramitsu.common.utils.DEFAULT_PRECISION
import jp.co.soramitsu.common.utils.format
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import java.math.BigDecimal

fun BigDecimal.formatWithMaxPrecision(type: Token.Type): String {
    return formatTokenAmount(type, type.maximumPrecision)
}

fun BigDecimal.formatWithDefaultPrecision(type: Token.Type): String {
    return formatTokenAmount(type, DEFAULT_PRECISION)
}

fun BigDecimal.formatTokenAmount(type: Token.Type, precision: Int): String {
    return "${format(precision)} ${type.displayName}"
}

fun BigDecimal.formatTokenChange(type: Token.Type, isIncome: Boolean, precision: Int = DEFAULT_PRECISION): String {
    val withoutSign = formatTokenAmount(type, precision)
    val sign = if (isIncome) '+' else '-'

    return sign + withoutSign
}
