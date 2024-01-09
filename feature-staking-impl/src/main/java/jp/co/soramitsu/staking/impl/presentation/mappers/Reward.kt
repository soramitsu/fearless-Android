package jp.co.soramitsu.staking.impl.presentation.mappers

import androidx.annotation.StringRes
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatAsPercentage
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.domain.rewards.PeriodReturns
import jp.co.soramitsu.staking.impl.presentation.staking.main.model.RewardEstimation
import jp.co.soramitsu.wallet.impl.domain.model.Token

enum class RewardSuffix(@StringRes val suffixResourceId: Int?) {
    None(null),
    APY(R.string.staking_apy),
    APR(R.string.staking_apr)
}

fun mapPeriodReturnsToRewardEstimation(
    periodReturns: PeriodReturns,
    token: Token,
    resourceManager: ResourceManager,
    rewardSuffix: RewardSuffix = RewardSuffix.None
): RewardEstimation {
    val gainFormatted = periodReturns.gainPercentage.formatAsPercentage()
    val gainWithSuffix = rewardSuffix.suffixResourceId?.let { resourceManager.getString(it, gainFormatted) } ?: gainFormatted

    return RewardEstimation(
        amount = periodReturns.gainAmount.formatCryptoDetail(token.configuration.symbol),
        fiatAmount = token.fiatAmount(periodReturns.gainAmount)?.formatFiat(token.fiatSymbol),
        gain = gainWithSuffix
    )
}
