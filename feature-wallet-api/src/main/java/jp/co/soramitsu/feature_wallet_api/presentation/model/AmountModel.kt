package jp.co.soramitsu.feature_wallet_api.presentation.model

import androidx.annotation.StringRes
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import java.math.BigDecimal
import java.math.BigInteger

data class AmountModel(
    val amount: BigDecimal,
    val token: String,
    val fiat: String?,
    @StringRes val titleResId: Int? = null,
)

fun mapAmountToAmountModel(
    amountInPlanks: BigInteger,
    asset: Asset,
    @StringRes titleResId: Int? = null,
): AmountModel = mapAmountToAmountModel(
    amount = asset.token.amountFromPlanks(amountInPlanks).orZero(),
    asset = asset,
    titleResId = titleResId
)

fun mapAmountToAmountModel(
    amount: BigDecimal,
    asset: Asset,
    @StringRes titleResId: Int? = null,
): AmountModel {
    val token = asset.token

    val fiatAmount = token.fiatAmount(amount)

    return AmountModel(
        amount = amount,
        token = amount.formatTokenAmount(token.configuration),
        fiat = fiatAmount?.formatAsCurrency(token.fiatSymbol),
        titleResId = titleResId
    )
}
