package jp.co.soramitsu.featurestakingimpl.presentation.mappers

import androidx.annotation.StringRes
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.formatAsPercentage
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.featurestakingimpl.domain.rewards.PeriodReturns
import jp.co.soramitsu.featurestakingimpl.presentation.staking.main.model.RewardEstimation
import jp.co.soramitsu.featurewalletapi.domain.model.Token
import jp.co.soramitsu.featurewalletapi.presentation.formatters.formatTokenAmount

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
        amount = periodReturns.gainAmount.formatTokenAmount(token.configuration),
        fiatAmount = token.fiatAmount(periodReturns.gainAmount)?.formatAsCurrency(token.fiatSymbol),
        gain = gainWithSuffix
    )
}
