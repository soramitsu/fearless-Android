package jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios

import androidx.lifecycle.LiveData
import java.math.BigDecimal
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.data.StakingType
import jp.co.soramitsu.staking.impl.data.repository.datasource.ParachainStakingStoriesDataSourceImpl
import jp.co.soramitsu.staking.impl.data.repository.datasource.StakingStoriesDataSourceImpl
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.alerts.AlertsInteractor
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.staking.impl.domain.rewards.SoraStakingRewardsScenario
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.staking.impl.presentation.staking.main.di.StakingViewStateFactory
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.staking.impl.scenarios.parachain.StakingParachainScenarioInteractor
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow

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
    private val stakingPoolInteractor: StakingPoolInteractor,
    private val stakingParachainStoriesDataSourceImpl: ParachainStakingStoriesDataSourceImpl,
    private val stakingStoriesDataSourceImpl: StakingStoriesDataSourceImpl,
    private val setupStakingSharedState: SetupStakingSharedState,
    private val router: StakingRouter,
    private val validationExecutor: ValidationExecutor,
    private val soraStakingRewardsScenario: SoraStakingRewardsScenario
) {

    /** Each selection gets fresh observations bound to the supplied cancellable owner. */
    fun createViewModel(stakingType: StakingType, scope: CoroutineScope): StakingScenarioViewModel {
        val scopedBaseViewModel = object : BaseStakingViewModel by baseViewModel {
            override val stakingStateScope: CoroutineScope = scope

            override suspend fun captureAlertSource(state: StakingState, type: StakingType): () -> Boolean {
                val sourceIsCurrent = baseViewModel.captureAlertSource(state, type)
                return { scope.isActive && sourceIsCurrent() }
            }
        }
        return when (stakingType) {
            StakingType.PARACHAIN -> StakingParachainScenarioViewModel(
                stakingInteractor,
                parachainInteractor,
                resourceManager,
                scopedBaseViewModel,
                stakingViewStateFactory,
                stakingParachainStoriesDataSourceImpl,
                rewardCalculatorFactory,
                setupStakingSharedState,
                router
            )
            StakingType.RELAYCHAIN -> StakingRelaychainScenarioViewModel(
                stakingInteractor,
                relaychainInteractor,
                resourceManager,
                scopedBaseViewModel,
                alertsInteractor,
                stakingViewStateFactory,
                stakingStoriesDataSourceImpl,
                state,
                rewardCalculatorFactory,
                setupStakingSharedState,
                router,
                validationExecutor,
                soraStakingRewardsScenario
            )
            StakingType.POOL -> StakingPoolViewModel(
                stakingPoolInteractor,
                stakingInteractor,
                resourceManager,
                rewardCalculatorFactory,
                scopedBaseViewModel
            )
        }
    }

}

interface BaseStakingViewModel {
    val stakingStateScope: CoroutineScope
    fun openCurrentValidators(isCurrent: () -> Boolean = { true })
    fun openChangeValidators(isCurrent: () -> Boolean = { true })
    fun redeemAlertClicked(isCurrent: () -> Boolean = { true })
    fun bondMoreAlertClicked(isCurrent: () -> Boolean = { true })
    fun showError(throwable: Throwable)
    fun showError(text: String)
    fun openStakingBalance(collatorAddress: String, isCurrent: () -> Boolean = { true })

    /** Capture the observed source before asynchronous alert production. */
    suspend fun captureAlertSource(state: StakingState, type: StakingType): () -> Boolean

    val enteredAmountEvent: Flow<Event<BigDecimal>>
}
