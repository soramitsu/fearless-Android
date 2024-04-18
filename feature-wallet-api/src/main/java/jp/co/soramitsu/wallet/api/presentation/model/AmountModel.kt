package jp.co.soramitsu.wallet.api.presentation.model

import androidx.annotation.StringRes
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks

data class AmountModel(
    val amount: BigDecimal,
    val token: String,
    val fiat: String?,
    @StringRes val titleResId: Int? = null
)

fun mapAmountToAmountModel(
    amountInPlanks: BigInteger,
    asset: Asset,
    @StringRes titleResId: Int? = null
): AmountModel = mapAmountToAmountModel(
    amount = asset.token.amountFromPlanks(amountInPlanks).orZero(),
    asset = asset,
    titleResId = titleResId
)

fun mapAmountToAmountModel(
    amount: BigDecimal,
    asset: Asset,
    @StringRes titleResId: Int? = null,
    useDetailCryptoFormat: Boolean = false
): AmountModel {
    val token = asset.token

    val fiatAmount = token.fiatAmount(amount)

    val tokenAmount = if (useDetailCryptoFormat) {
        amount.formatCryptoDetail(token.configuration.symbol)
    } else {
        amount.formatCrypto(token.configuration.symbol)
    }

    return AmountModel(
        amount = amount,
        token = tokenAmount,
        fiat = fiatAmount?.formatFiat(token.fiatSymbol),
        titleResId = titleResId
    )
}
