package jp.co.soramitsu.wallet.impl.presentation.model

import jp.co.soramitsu.common.utils.isNonNegative
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState
import jp.co.soramitsu.wallet.impl.domain.model.FiatAmount
import jp.co.soramitsu.wallet.impl.domain.model.Token
import java.math.BigDecimal

/**
 * Presentation of one captured token. Its fiat unit is historical until its owner admits the
 * originating selection/snapshot; currency-ID equality alone cannot establish currentness.
 * Copies retain the canonical input, so changing that input recomputes quote admission.
 */
data class TokenModel(val token: Token) {
    val configuration = token.configuration
    val fiatContext = token.fiatContext
    val quoteCurrencyId = token.quoteCurrencyId
    val quotePriceId = token.quotePriceId
    val fiatRateState = token.fiatRateState
    val recentRateChangeState = token.recentRateChangeState
    val fiatRate = token.fiatRate
    val fiatSymbol = token.fiatSymbol
    val recentRateChange = token.recentRateChange
    val rateChangeColorRes = determineChangeColor()

    private fun determineChangeColor(): Int {
        if (recentRateChange == null) return R.color.gray2

        return if (recentRateChange.isNonNegative) R.color.green else R.color.red
    }

    /** Numeric compatibility only; use [valuedAmount] when the result leaves this model. */
    fun fiatAmount(tokenAmount: BigDecimal): BigDecimal? = token.fiatAmount(tokenAmount)

    /** Retains missing/damaged quantities and price availability without manufacturing zero. */
    fun fiatAmountState(tokenAmount: AssetAmountState<BigDecimal>): AssetAmountState<BigDecimal> =
        token.fiatAmountState(tokenAmount)

    /** Carries the captured fiat identity together with its admitted amount state. */
    fun valuedAmount(tokenAmount: AssetAmountState<BigDecimal>): FiatAmount? = token.valuedAmount(tokenAmount)
}
