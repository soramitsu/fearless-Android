package jp.co.soramitsu.feature_staking_impl.presentation.staking.main.scenarios

import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_staking_impl.data.StakingSharedState
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.alerts.AlertsInteractor
import jp.co.soramitsu.feature_staking_impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.feature_staking_impl.domain.scenarios.StakingParachainScenarioInteractor
import jp.co.soramitsu.feature_staking_impl.domain.scenarios.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.di.StakingViewStateFactory
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map

class StakingScenario(
    state: StakingSharedState,
    private val baseViewModel: BaseStakingViewModel,
    private val stakingInteractor: StakingInteractor,
    private val parachainInteractor: StakingParachainScenarioInteractor,
    private val relaychainInteractor: StakingRelayChainScenarioInteractor,
    private val rewardCalculatorFactory: RewardCalculatorFactory,
    private val resourceManager: ResourceManager,
    private val alertsInteractor: AlertsInteractor,
    private val stakingViewStateFactory: StakingViewStateFactory
) {
    private val parachainViewModel by lazy {
        StakingParachainScenarioViewModel(
            stakingInteractor,
            parachainInteractor,
            rewardCalculatorFactory,
            resourceManager,
            baseViewModel,
            stakingViewStateFactory
        )
    }
    private val relaychainViewModel by lazy {
        StakingRelaychainScenarioViewModel(
            stakingInteractor,
            relaychainInteractor,
            resourceManager,
            rewardCalculatorFactory,
            baseViewModel,
            alertsInteractor,
            stakingViewStateFactory
        )
    }

    fun getViewModel(stakingType: Chain.Asset.StakingType): StakingScenarioViewModel {
        return when (stakingType) {
            Chain.Asset.StakingType.PARACHAIN -> {
                parachainViewModel
            }
            Chain.Asset.StakingType.RELAYCHAIN -> {
                relaychainViewModel
            }
            else -> error("")
        }
    }

    val viewModel = state.assetWithChain.map {
        when (it.asset.staking) {
            Chain.Asset.StakingType.PARACHAIN -> {
                parachainViewModel
            }
            Chain.Asset.StakingType.RELAYCHAIN -> {
                relaychainViewModel
            }
            else -> error("")
        }
    }
}

interface BaseStakingViewModel {
    val stakingStateScope: CoroutineScope
    fun openCurrentValidators()
    fun redeemAlertClicked()
    fun bondMoreAlertClicked()
    fun showError(throwable: Throwable)
    fun showError(text: String)
}
