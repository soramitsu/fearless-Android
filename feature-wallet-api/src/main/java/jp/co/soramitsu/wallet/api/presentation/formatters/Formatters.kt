package jp.co.soramitsu.wallet.api.presentation.formatters

import java.math.BigDecimal
import jp.co.soramitsu.common.utils.format
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

fun BigDecimal.formatTokenAmount(chainAsset: Chain.Asset): String {
    return formatTokenAmount(chainAsset.symbolToShow)
}

fun BigDecimal.formatTokenAmount(tokenSymbol: String): String {
    return "${format()} ${tokenSymbol.uppercase()}"
}

fun BigDecimal.formatTokenChange(chainAsset: Chain.Asset, isIncome: Boolean): String {
    val withoutSign = formatTokenAmount(chainAsset)
    val sign = if (isIncome) '+' else '-'

    return sign + withoutSign
}
