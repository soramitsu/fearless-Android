package jp.co.soramitsu.feature_wallet_api.presentation.model

import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import java.math.BigDecimal
import java.math.BigInteger

data class AmountModel(
    val token: String,
    val fiat: String?
)

fun mapAmountToAmountModel(
    amountInPlanks: BigInteger,
    asset: Asset
): AmountModel = mapAmountToAmountModel(
    amount = asset.token.amountFromPlanks(amountInPlanks).orZero(),
    asset = asset
)

fun mapAmountToAmountModel(
    amount: BigDecimal,
    asset: Asset
): AmountModel {
    val token = asset.token

    val fiatAmount = token.fiatAmount(amount)

    return AmountModel(
        token = amount.formatTokenAmount(token.configuration),
        fiat = fiatAmount?.formatAsCurrency(token.fiatSymbol)
    )
}
