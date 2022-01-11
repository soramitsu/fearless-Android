package jp.co.soramitsu.feature_wallet_api.presentation.formatters

import jp.co.soramitsu.common.utils.format
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import java.math.BigDecimal

fun BigDecimal.formatTokenAmount(chainAsset: Chain.Asset): String {
    return formatTokenAmount(chainAsset.symbol)
}

fun BigDecimal.formatTokenAmount(tokenSymbol: String): String {
    return "${format()} $tokenSymbol"
}

fun BigDecimal.formatTokenChange(chainAsset: Chain.Asset, isIncome: Boolean): String {
    val withoutSign = formatTokenAmount(chainAsset)
    val sign = if (isIncome) '+' else '-'

    return sign + withoutSign
}
