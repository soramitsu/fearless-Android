package jp.co.soramitsu.feature_staking_impl.presentation.staking.model

import jp.co.soramitsu.common.utils.formatAsChange
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.wallet.formatWithDefaultPrecision
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import java.math.BigDecimal

class RewardEstimation(
    amount: BigDecimal,
    fiatAmount: BigDecimal?,
    percentageGain: BigDecimal,
    token: Token
) {
    val amount = amount.formatWithDefaultPrecision(token.type)
    val fiatAmount = fiatAmount?.formatAsCurrency()
    val gain = percentageGain.formatAsChange()
}