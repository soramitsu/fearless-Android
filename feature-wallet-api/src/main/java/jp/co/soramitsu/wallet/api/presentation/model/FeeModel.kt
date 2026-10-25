package jp.co.soramitsu.wallet.api.presentation.model

import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState
import jp.co.soramitsu.wallet.impl.domain.model.FiatAmount
import jp.co.soramitsu.wallet.impl.domain.model.Token
import java.math.BigDecimal

/**
 * A known crypto fee valued in the token's captured fiat context. Unavailable fiat does not erase
 * the crypto estimate. Its owner must still admit the originating request/selection before display;
 * this immutable value does not observe selection changes or claim to be current.
 */
class FeeModel(val fee: BigDecimal, val token: Token) {
    val fiatContext = token.fiatContext
    val fiatAmountState = token.fiatAmountState(AssetAmountState.Known(fee))
    val fiatAmount = fiatContext?.let { FiatAmount(it.currencyId, fiatAmountState) }
    val displayToken = fee.formatCryptoDetail(token.configuration.symbol)
    val displayFiat: String? = (fiatAmountState as? AssetAmountState.Known<BigDecimal>)?.let { known ->
        fiatContext?.takeIf { it.isValid }?.let { context ->
            context.displayUnit + known.value.formatFiat()
        }
    }

    /** Revalues a changed canonical input while retaining this class's reference equality contract. */
    fun copy(fee: BigDecimal = this.fee, token: Token = this.token): FeeModel = FeeModel(fee, token)
}
