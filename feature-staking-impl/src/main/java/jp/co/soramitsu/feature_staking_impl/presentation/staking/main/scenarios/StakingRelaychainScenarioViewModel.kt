package jp.co.soramitsu.feature_staking_impl.presentation.staking.main.scenarios

import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.format
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.model.NetworkInfo
import jp.co.soramitsu.feature_staking_impl.domain.scenarios.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.StakingInfoViewState
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.StakingViewState1
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.transformStories
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class StakingRelaychainScenarioViewModel(
    private val stakingInteractor: StakingInteractor,
    private val scenarioInteractor: StakingRelayChainScenarioInteractor,
    private val resourceManager: ResourceManager,
) : StakingScenarioViewModel {

    //    override fun getLoadingStakingState(): Flow<LoadingState<StakingState>> = stakingInteractor.selectionStateFlow()
//        .withLoading { (account, assetWithToken) ->
//            scenarioInteractor.selectedAccountStakingStateFlow(account, assetWithToken)
//        }
//
//    override fun getStakingViewStateFlow(): Flow<LoadingState<StakingViewState>> {
//        return getLoadingStakingState().mapLoading { }
//    }

    override suspend fun stakingInfoViewStateFlow(): Flow<StakingInfoViewState> {
        return combine(
            scenarioInteractor.getCurrentAsset(),
            scenarioInteractor.observeNetworkInfoState().map { it as NetworkInfo.RelayChain },
            scenarioInteractor.stakingStoriesFlow().map { it.map(::transformStories) }
        ) { asset, networkInfo, stories ->
            val name = asset.token.configuration.name
            val minimumStake = asset.token.amountFromPlanks(networkInfo.minimumStake)
            val minimumStakeFormatted = minimumStake.formatTokenAmount(asset.token.configuration)
            val minimumStakeFiat = asset.token.fiatAmount(minimumStake)?.formatAsCurrency(asset.token.fiatSymbol)
            val lockupPeriod = resourceManager.getQuantityString(R.plurals.staking_main_lockup_period_value, networkInfo.lockupPeriodInDays)
                .format(networkInfo.lockupPeriodInDays)
            val totalStake = asset.token.amountFromPlanks(networkInfo.totalStake)
            val totalStakeFormatted = totalStake.formatTokenAmount(asset.token.configuration)
            val totalStakedFiat = asset.token.fiatAmount(totalStake)?.formatAsCurrency(asset.token.fiatSymbol)

            StakingInfoViewState.RelayChain(
                name,
                stories,
                totalStakeFormatted,
                totalStakedFiat,
                minimumStakeFormatted,
                minimumStakeFiat,
                networkInfo.nominatorsCount.format(),
                lockupPeriod
            )
        }
    }

    override suspend fun stakingViewState(): Flow<StakingViewState1> {

    }
}
