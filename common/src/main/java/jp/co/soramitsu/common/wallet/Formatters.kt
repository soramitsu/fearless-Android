package jp.co.soramitsu.common.wallet

import jp.co.soramitsu.common.utils.format
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import java.math.BigDecimal

fun BigDecimal.formatAsToken(type: Token.Type): String {
    return "${format(precision = type.maximumPrecision)} ${type.displayName}"
}