package jp.co.soramitsu.feature_wallet_api.data.mappers

import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.feature_wallet_api.presentation.model.FeeModel
import java.math.BigDecimal

fun mapFeeToFeeModel(
    fee: BigDecimal,
    token: Token
) = FeeModel(
    fee = fee,
    displayToken = fee.formatTokenAmount(token.type),
    displayFiat = token.fiatAmount(fee)?.formatAsCurrency()
)
