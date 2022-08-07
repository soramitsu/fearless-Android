package jp.co.soramitsu.feature_staking_impl.presentation.staking.main

import jp.co.soramitsu.common.domain.model.StoryGroup
import jp.co.soramitsu.common.presentation.StakingStoryModel
import jp.co.soramitsu.common.presentation.StoryElement
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.format
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.model.NetworkInfo
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.model.StakingNetworkInfoModel
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount

fun transformNetworkInfo(resourceManager: ResourceManager, asset: Asset, networkInfo: NetworkInfo): StakingNetworkInfoModel {
    val minimumStake = asset.token.amountFromPlanks(networkInfo.minimumStake)
    val minimumStakeFormatted = minimumStake.formatTokenAmount(asset.token.configuration)

    val minimumStakeFiat = asset.token.fiatAmount(minimumStake)?.formatAsCurrency(asset.token.fiatSymbol)

    val lockupPeriod = resourceManager.getQuantityString(R.plurals.staking_main_lockup_period_value, networkInfo.lockupPeriodInDays)
        .format(networkInfo.lockupPeriodInDays)

    return when (networkInfo) {
        is NetworkInfo.RelayChain -> {
            val totalStake = asset.token.amountFromPlanks(networkInfo.totalStake)
            val totalStakeFormatted = totalStake.formatTokenAmount(asset.token.configuration)

            val totalStakeFiat = asset.token.fiatAmount(totalStake)?.formatAsCurrency(asset.token.fiatSymbol)

            StakingNetworkInfoModel.RelayChain(
                lockupPeriod,
                minimumStakeFormatted,
                minimumStakeFiat,
                totalStakeFormatted,
                totalStakeFiat,
                networkInfo.nominatorsCount.format()
            )
        }
        is NetworkInfo.Parachain -> {
            StakingNetworkInfoModel.Parachain(lockupPeriod, minimumStakeFormatted, minimumStakeFiat)
        }
    }
}

fun transformStories(story: StoryGroup.Staking): StakingStoryModel = with(story) {
    val elements = elements.map { StoryElement.Staking(it.titleRes, it.bodyRes, it.url) }
    StakingStoryModel(titleRes, iconSymbol, elements)
}
