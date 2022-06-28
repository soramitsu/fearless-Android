package jp.co.soramitsu.feature_staking_impl.presentation.staking.main.scenarios

import javax.inject.Named
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.feature_staking_impl.data.StakingSharedState
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.alerts.AlertsInteractor
import jp.co.soramitsu.feature_staking_impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.BALANCE_REQUIRED_CONTROLLER
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.BALANCE_REQUIRED_STASH
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.BalanceAccountRequiredValidation
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.di.StakingViewStateFactory
import jp.co.soramitsu.feature_staking_impl.scenarios.parachain.StakingParachainScenarioInteractor
import jp.co.soramitsu.feature_staking_impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
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
    @Named(BALANCE_REQUIRED_CONTROLLER)
    controllerRequiredValidation: BalanceAccountRequiredValidation,
    @Named(BALANCE_REQUIRED_STASH)
    stashRequiredValidation: BalanceAccountRequiredValidation,
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

    val redeemValidationSystem = state.assetWithChain.map {
        val validations = when (it.asset.staking) {
            Chain.Asset.StakingType.PARACHAIN -> {
                listOf()
            }
            Chain.Asset.StakingType.RELAYCHAIN -> {
                listOf(controllerRequiredValidation)
            }
            else -> listOf()
        }

        ValidationSystem(
            CompositeValidation(
                validations = validations
            )
        )
    }

    val bondMoreValidationSystem = state.assetWithChain.map {
        val validations = when (it.asset.staking) {
            Chain.Asset.StakingType.PARACHAIN -> {
                listOf()
            }
            Chain.Asset.StakingType.RELAYCHAIN -> {
                listOf(stashRequiredValidation)
            }
            else -> listOf()
        }

        ValidationSystem(
            CompositeValidation(
                validations = validations
            )
        )
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
