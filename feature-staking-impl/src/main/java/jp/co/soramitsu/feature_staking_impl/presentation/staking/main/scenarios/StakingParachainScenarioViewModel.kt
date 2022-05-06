package jp.co.soramitsu.feature_staking_impl.presentation.staking.main.scenarios

import androidx.lifecycle.LiveData
import java.math.BigDecimal
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.asLiveData
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.rewards.PeriodReturns
import jp.co.soramitsu.feature_staking_impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.feature_staking_impl.domain.scenarios.StakingParachainScenarioInteractor
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.feature_staking_impl.presentation.mappers.mapPeriodReturnsToRewardEstimation
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.PERIOD_MONTH
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.PERIOD_YEAR
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.ReturnsModel
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.StakingInfoViewState
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.StakingViewState1
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.transformStories
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class StakingParachainScenarioViewModel(
    private val stakingInteractor: StakingInteractor,
    private val scenarioInteractor: StakingParachainScenarioInteractor,
    private val rewardCalculatorFactory: RewardCalculatorFactory,
    private val resourceManager: ResourceManager,
    private val setupStakingSharedState: SetupStakingSharedState
) : StakingScenarioViewModel {

    //    override fun getLoadingStakingState(): Flow<LoadingState<StakingState>> = stakingInteractor.selectionStateFlow()
//        .withLoading { (account, assetWithToken) ->
//            scenarioInteractor.selectedAccountStakingStateFlow(account, assetWithToken)
//        }
//
//    override fun getStakingViewStateFlow(): Flow<LoadingState<StakingViewState>> {
//        return getLoadingStakingState().mapLoading { }
//    }

    private val rewardCalculator = rewardCalculatorFactory.createSubquery()

    override suspend fun stakingInfoViewStateFlow(): Flow<StakingInfoViewState> {
        return combine(
            scenarioInteractor.getCurrentAsset(),
            scenarioInteractor.observeNetworkInfoState(),
            scenarioInteractor.stakingStoriesFlow().map { it.map(::transformStories) }
        ) { asset, networkInfo, stories ->
            val name = asset.token.configuration.name
            val minimumStake = asset.token.amountFromPlanks(networkInfo.minimumStake)
            val minimumStakeFormatted = minimumStake.formatTokenAmount(asset.token.configuration)
            val minimumStakeFiat = asset.token.fiatAmount(minimumStake)?.formatAsCurrency(asset.token.fiatSymbol)
            val lockupPeriod = resourceManager.getQuantityString(R.plurals.staking_main_lockup_period_value, networkInfo.lockupPeriodInDays)
                .format(networkInfo.lockupPeriodInDays)

            StakingInfoViewState.Parachain(name, stories, minimumStakeFormatted, minimumStakeFiat, lockupPeriod)
        }
    }

    override suspend fun stakingViewState(): Flow<StakingViewState1> {
        scenarioInteractor.getStakingStateFlow()
            .map {
                when (it) {
                    is StakingState.Parachain.Collator -> StakingViewState1.Parachain.Collator()
                    is StakingState.Parachain.Delegator -> {
                        StakingViewState1.Parachain.Delegator()
                    }
                    is StakingState.Parachain.None -> {
                        val monthlyReturns = getMonthlyReturns()
                        monthlyReturns.gainAmount
                        StakingViewState1.StartStaking(

                        )
                    }
                    else -> error("Wrong state")
                }
            }
    }

    private val parsedAmountFLow = setupStakingSharedState.

    val returns: LiveData<ReturnsModel> = currentAssetFlow.combine(parsedAmountFlow) { asset, amount ->
        val monthly = rewardCalculator.calculateReturns(amount, PERIOD_MONTH, true)
        val yearly = rewardCalculator.calculateReturns(amount, PERIOD_YEAR, true)

        val monthlyEstimation = mapPeriodReturnsToRewardEstimation(monthly, asset.token, resourceManager)
        val yearlyEstimation = mapPeriodReturnsToRewardEstimation(yearly, asset.token, resourceManager)

        ReturnsModel(monthlyEstimation, yearlyEstimation)
    }.asLiveData(scope)
}
