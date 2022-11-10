package jp.co.soramitsu.wallet.api.presentation.formatters

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.utils.format
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks

fun BigDecimal.formatTokenAmount(chainAsset: Chain.Asset): String {
    return formatTokenAmount(chainAsset.symbolToShow)
}

fun BigInteger.tokenAmountFromPlanks(chainAsset: Chain.Asset): String {
    return chainAsset.amountFromPlanks(this).formatTokenAmount(chainAsset)
}

fun BigInteger.tokenAmountFromPlanks(asset: Asset): String {
    val chainAsset = asset.token.configuration
    return this.tokenAmountFromPlanks(chainAsset)
}

fun BigDecimal.formatTokenAmount(tokenSymbol: String): String {
    return "${format()} ${tokenSymbol.uppercase()}"
}

fun BigDecimal.formatTokenChange(chainAsset: Chain.Asset, isIncome: Boolean): String {
    val withoutSign = formatTokenAmount(chainAsset)
    val sign = if (isIncome) '+' else '-'

    return sign + withoutSign
}
