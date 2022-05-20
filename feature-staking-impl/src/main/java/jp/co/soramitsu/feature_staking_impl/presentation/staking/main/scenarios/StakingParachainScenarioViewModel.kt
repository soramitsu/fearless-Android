package jp.co.soramitsu.feature_staking_impl.presentation.staking.main.scenarios

import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.presentation.mapLoading
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.withLoading
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.model.NetworkInfo
import jp.co.soramitsu.feature_staking_impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.feature_staking_impl.domain.scenarios.StakingParachainScenarioInteractor
import jp.co.soramitsu.feature_staking_impl.presentation.staking.alerts.model.AlertModel
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.StakingViewState
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.di.StakingViewStateFactory
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.model.StakingNetworkInfoModel
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
    private val baseViewModel: BaseStakingViewModel,
    private val stakingViewStateFactory: StakingViewStateFactory
) : StakingScenarioViewModel {

    override suspend fun stakingState(): Flow<LoadingState<StakingState>> =
        scenarioInteractor.getStakingStateFlow().withLoading()

    //    override fun getLoadingStakingState(): Flow<LoadingState<StakingState>> = stakingInteractor.selectionStateFlow()
//        .withLoading { (account, assetWithToken) ->
//            scenarioInteractor.selectedAccountStakingStateFlow(account, assetWithToken)
//        }
//
//    override fun getStakingViewStateFlow(): Flow<LoadingState<StakingViewState>> {
//        return getLoadingStakingState().mapLoading { }
//    }

    override suspend fun getStakingViewStateFlow(): Flow<LoadingState<StakingViewState>> {
        return stakingState().mapLoading { stakingState ->
            hashCode()
            when (stakingState) {
                is StakingState.Parachain.None -> {
                    stakingViewStateFactory.createParachainWelcomeViewState(
                        stakingInteractor.currentAssetFlow(),
                        baseViewModel.stakingStateScope,
                        baseViewModel::showError
                    )

                }
                else -> error("Wrong state")
            }
        }
    }

    override suspend fun getRewardCalculator() = rewardCalculatorFactory.createSubquery()

    override suspend fun networkInfo(): Flow<LoadingState<StakingNetworkInfoModel>> {
        return combine(
            scenarioInteractor.observeNetworkInfoState().map { it as NetworkInfo.Parachain },
            stakingInteractor.currentAssetFlow()
        ) { networkInfo, asset ->
            val minimumStake = asset.token.amountFromPlanks(networkInfo.minimumStake)
            val minimumStakeFormatted = minimumStake.formatTokenAmount(asset.token.configuration)

            val minimumStakeFiat = asset.token.fiatAmount(minimumStake)?.formatAsCurrency(asset.token.fiatSymbol)

            val lockupPeriod = resourceManager.getQuantityString(R.plurals.staking_main_lockup_period_value, networkInfo.lockupPeriodInDays)
                .format(networkInfo.lockupPeriodInDays)

            StakingNetworkInfoModel.Parachain(lockupPeriod, minimumStakeFormatted, minimumStakeFiat)
        }.withLoading()
    }

    override suspend fun alerts(): Flow<LoadingState<List<AlertModel>>> {
        return flowOf<List<AlertModel>> { emptyList() }.withLoading()
    }

}
