package jp.co.soramitsu.wallet.impl.presentation.model

import java.math.BigDecimal
import jp.co.soramitsu.common.utils.isNonNegative
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

class TokenModel(
    val configuration: Chain.Asset,
    val fiatRate: BigDecimal?,
    val fiatSymbol: String?,
    val recentRateChange: BigDecimal?
) {
    val rateChangeColorRes = determineChangeColor()

    private fun determineChangeColor(): Int {
        if (recentRateChange == null) return R.color.gray2

        return if (recentRateChange.isNonNegative) R.color.green else R.color.red
    }

    fun fiatAmount(tokenAmount: BigDecimal): BigDecimal? = fiatRate?.multiply(tokenAmount)
}
