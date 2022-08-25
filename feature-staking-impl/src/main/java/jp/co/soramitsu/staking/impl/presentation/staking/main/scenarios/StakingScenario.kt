package jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios

import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.data.StakingType
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.alerts.AlertsInteractor
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.staking.impl.presentation.staking.main.di.StakingViewStateFactory
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.staking.impl.scenarios.parachain.StakingParachainScenarioInteractor
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map

class StakingScenario(
    private val state: StakingSharedState,
    private val baseViewModel: BaseStakingViewModel,
    private val stakingInteractor: StakingInteractor,
    private val parachainInteractor: StakingParachainScenarioInteractor,
    private val relaychainInteractor: StakingRelayChainScenarioInteractor,
    private val rewardCalculatorFactory: RewardCalculatorFactory,
    private val resourceManager: ResourceManager,
    private val alertsInteractor: AlertsInteractor,
    private val stakingViewStateFactory: StakingViewStateFactory,
    private val stakingPoolInteractor: StakingPoolInteractor
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
            stakingViewStateFactory,
            state
        )
    }
    private val stakingPoolViewModel by lazy {
        StakingPoolViewModel(
            stakingPoolInteractor,
            stakingInteractor
        )
    }

    fun getViewModel(stakingType: StakingType): StakingScenarioViewModel {
        return when (stakingType) {
            StakingType.PARACHAIN -> parachainViewModel
            StakingType.RELAYCHAIN -> relaychainViewModel
            StakingType.POOL -> stakingPoolViewModel
            else -> error("StakingScenario.getViewModel")
        }
    }

    val viewModel = state.selectionItem.map {
        getViewModel(it.type)
    }
}

interface BaseStakingViewModel {
    val stakingStateScope: CoroutineScope
    fun openCurrentValidators()
    fun openChangeValidators()
    fun redeemAlertClicked()
    fun bondMoreAlertClicked()
    fun showError(throwable: Throwable)
    fun showError(text: String)
    fun openStakingBalance(collatorAddress: String)
}
