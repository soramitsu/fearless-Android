package jp.co.soramitsu.wallet.api.data.mappers

import java.math.BigDecimal
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.wallet.impl.domain.model.Token
import jp.co.soramitsu.wallet.api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.wallet.api.presentation.model.FeeModel

fun mapFeeToFeeModel(
    fee: BigDecimal,
    token: Token
) = FeeModel(
    fee = fee,
    displayToken = fee.formatTokenAmount(token.configuration),
    displayFiat = token.fiatAmount(fee)?.formatAsCurrency(token.fiatSymbol)
)
