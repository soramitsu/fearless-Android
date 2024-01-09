package jp.co.soramitsu.wallet.api.data.mappers

import java.math.BigDecimal
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.wallet.api.presentation.model.FeeModel
import jp.co.soramitsu.wallet.impl.domain.model.Token

fun mapFeeToFeeModel(
    fee: BigDecimal,
    token: Token
) = FeeModel(
    fee = fee,
    displayToken = fee.formatCryptoDetail(token.configuration.symbol),
    displayFiat = token.fiatAmount(fee)?.formatFiat(token.fiatSymbol)
)
