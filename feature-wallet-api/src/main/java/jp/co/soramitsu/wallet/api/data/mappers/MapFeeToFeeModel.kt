package jp.co.soramitsu.wallet.api.data.mappers

import java.math.BigDecimal
import jp.co.soramitsu.wallet.api.presentation.model.FeeModel
import jp.co.soramitsu.wallet.impl.domain.model.Token

/** Preserves the captured token and known crypto fee; selection admission remains with the owner. */
fun mapFeeToFeeModel(
    fee: BigDecimal,
    token: Token
) = FeeModel(fee = fee, token = token)
