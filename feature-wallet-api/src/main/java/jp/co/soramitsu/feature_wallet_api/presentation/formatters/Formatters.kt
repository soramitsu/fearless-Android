package jp.co.soramitsu.feature_wallet_api.presentation.formatters

import jp.co.soramitsu.common.utils.format
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import java.math.BigDecimal

fun BigDecimal.formatTokenAmount(type: Token.Type): String {
    return formatTokenAmount(type.displayName)
}

fun BigDecimal.formatTokenAmount(tokenSymbol: String): String {
    return "${format()} $tokenSymbol"
}

fun BigDecimal.formatTokenChange(type: Token.Type, isIncome: Boolean): String {
    val withoutSign = formatTokenAmount(type)
    val sign = if (isIncome) '+' else '-'

    return sign + withoutSign
}
