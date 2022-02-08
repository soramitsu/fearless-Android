package jp.co.soramitsu.feature_wallet_impl.presentation.model

import jp.co.soramitsu.common.utils.applyDollarRate
import java.math.BigDecimal

data class AssetModel(
    val metaId: Long?,
    val token: TokenModel,
    val total: BigDecimal,
    val dollarAmount: BigDecimal?,
    val locked: BigDecimal,
    val bonded: BigDecimal,
    val frozen: BigDecimal,
    val reserved: BigDecimal,
    val redeemable: BigDecimal,
    val unbonding: BigDecimal,
    val available: BigDecimal,
    val sortIndex: Int,
    val enabed: Boolean
) {
    val totalFiat = total.applyDollarRate(token.dollarRate)
    val availableFiat = available.applyDollarRate(token.dollarRate)
    val frozenFiat = frozen.applyDollarRate(token.dollarRate)
}
