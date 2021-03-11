package jp.co.soramitsu.feature_wallet_impl.presentation.model

import java.math.BigDecimal

data class AssetModel(
    val token: TokenModel,
    val total: BigDecimal,
    val dollarAmount: BigDecimal?,
    val locked: BigDecimal,
    val bonded: BigDecimal,
    val frozen: BigDecimal,
    val reserved: BigDecimal,
    val redeemable: BigDecimal,
    val unbonding: BigDecimal,
    val available: BigDecimal
)