package jp.co.soramitsu.feature_wallet_api.presentation.formatters

import jp.co.soramitsu.common.utils.format
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import java.math.BigDecimal

fun BigDecimal.formatTokenAmount(type: Token.Type): String {
    return "${format()} ${type.displayName}"
}

fun BigDecimal.formatTokenChange(type: Token.Type, isIncome: Boolean): String {
    val withoutSign = formatTokenAmount(type)
    val sign = if (isIncome) '+' else '-'

    return sign + withoutSign
}
