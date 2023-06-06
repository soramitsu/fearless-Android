package jp.co.soramitsu.wallet.api.presentation.formatters

import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.core.utils.amountFromPlanks
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset as CoreAsset

fun BigInteger.formatCryptoDetailFromPlanks(chainAsset: CoreAsset, withSymbol: Boolean = true): String {
    val useSymbol = chainAsset.symbolToShow.takeIf { withSymbol }
    return chainAsset.amountFromPlanks(this).formatCryptoDetail(useSymbol)
}

fun BigInteger.formatCryptoFromPlanks(chainAsset: CoreAsset): String {
    return chainAsset.amountFromPlanks(this).formatCrypto(chainAsset.symbolToShow)
}

fun String.formatSigned(positive: Boolean): String {
    val sign = if (positive) '+' else '-'

    return sign + this
}
