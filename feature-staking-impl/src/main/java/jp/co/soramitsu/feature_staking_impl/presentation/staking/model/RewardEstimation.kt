package jp.co.soramitsu.feature_staking_impl.presentation.staking.model

import jp.co.soramitsu.common.utils.formatAsChange
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.wallet.formatWithDefaultPrecision
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import java.math.BigDecimal

class RewardEstimation(
    _amount: BigDecimal,
    percentageGain: BigDecimal,
    token: Token
) {

    val amount = _amount.formatWithDefaultPrecision(token.type)
    val fiatAmount = token.fiatAmount(_amount)?.formatAsCurrency()
    val gain = percentageGain.formatAsChange()
}