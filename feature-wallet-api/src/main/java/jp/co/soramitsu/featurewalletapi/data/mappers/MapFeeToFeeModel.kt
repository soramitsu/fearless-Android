package jp.co.soramitsu.featurewalletapi.data.mappers

import java.math.BigDecimal
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.featurewalletapi.domain.model.Token
import jp.co.soramitsu.featurewalletapi.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.featurewalletapi.presentation.model.FeeModel

fun mapFeeToFeeModel(
    fee: BigDecimal,
    token: Token
) = FeeModel(
    fee = fee,
    displayToken = fee.formatTokenAmount(token.configuration),
    displayFiat = token.fiatAmount(fee)?.formatAsCurrency(token.fiatSymbol)
)
