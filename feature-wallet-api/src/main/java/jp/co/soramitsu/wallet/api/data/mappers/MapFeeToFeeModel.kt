package jp.co.soramitsu.wallet.api.data.mappers

import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.wallet.api.presentation.model.FeeModel
import jp.co.soramitsu.wallet.impl.domain.model.Token
import java.math.BigDecimal

fun mapFeeToFeeModel(
    fee: BigDecimal,
    token: Token
) = FeeModel(
    fee = fee,
    displayToken = fee.formatCryptoDetail(token.configuration.symbolToShow),
    displayFiat = token.fiatAmount(fee)?.formatFiat(token.fiatSymbol)
)
