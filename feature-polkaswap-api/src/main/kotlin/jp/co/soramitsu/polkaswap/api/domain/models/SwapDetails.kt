package jp.co.soramitsu.polkaswap.api.domain.models

import java.math.BigDecimal
import jp.co.soramitsu.wallet.impl.domain.model.Asset

data class SwapDetails(
    val amount: BigDecimal,
    val minMax: BigDecimal,
    val fromTokenOnToToken: BigDecimal,
    val toTokenOnFromToken: BigDecimal,
    val liquidityProviderFee: BigDecimal,
    val feeAsset: Asset,
    val bestDexId: Int
)
