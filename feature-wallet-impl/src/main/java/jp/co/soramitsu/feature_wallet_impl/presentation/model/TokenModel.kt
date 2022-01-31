package jp.co.soramitsu.feature_wallet_impl.presentation.model

import jp.co.soramitsu.common.utils.isNonNegative
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import java.math.BigDecimal

class TokenModel(
    val configuration: Chain.Asset,
    val dollarRate: BigDecimal?,
    val recentRateChange: BigDecimal?
) {
    val rateChangeColorRes = determineChangeColor()

    private fun determineChangeColor(): Int {
        if (recentRateChange == null) return R.color.gray2

        return if (recentRateChange.isNonNegative) R.color.green else R.color.red
    }

    fun fiatAmount(tokenAmount: BigDecimal): BigDecimal? = dollarRate?.multiply(tokenAmount)
}
