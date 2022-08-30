package jp.co.soramitsu.staking.impl.presentation.setup

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.mappers.mapPeriodReturnsToRewardEstimation
import jp.co.soramitsu.staking.impl.presentation.setup.compose.SetupStakingPoolViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios.PERIOD_YEAR
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class StartStakingPoolViewModel @Inject constructor(
    private val relayChainScenarioInteractor: StakingRelayChainScenarioInteractor,
    private val stakingSharedState: StakingSharedState,
    private val resourceManager: ResourceManager,
    private val rewardCalculatorFactory: RewardCalculatorFactory,
    private val router: StakingRouter
) : BaseViewModel() {

    private val yearlyReturnsFlow = flowOf {
        val asset = stakingSharedState.currentAssetFlow().first()
        val chainId = asset.token.configuration.chainId
        val rewardCalculator = rewardCalculatorFactory.createManual(chainId)

        val yearly = rewardCalculator.calculateReturns(BigDecimal.ONE, PERIOD_YEAR, true, chainId)

        mapPeriodReturnsToRewardEstimation(yearly, asset.token, resourceManager)
    }

    private val unstakingPeriodFlow = flowOf {
        val lockupPeriod = relayChainScenarioInteractor.unstakingPeriod()
        resourceManager.getQuantityString(R.plurals.staking_main_lockup_period_value, lockupPeriod, lockupPeriod)
    }

    private val rewardsPayoutDelayFlow = flowOf {
        val hours = relayChainScenarioInteractor.stakePeriodInHours()
        resourceManager.getQuantityString(R.plurals.common_hours_format, hours, hours)
    }

    val state = combine(rewardsPayoutDelayFlow, unstakingPeriodFlow, yearlyReturnsFlow) { rewardsPayoutDelay, unstakingPeriod, yearlyReturns ->
        val asset = stakingSharedState.currentAssetFlow().first()

        SetupStakingPoolViewState(
            ToolbarViewState(
                resourceManager.getString(R.string.pool_staking_title),
                R.drawable.ic_arrow_back_24dp
            ),
            asset.token.configuration.id,
            rewardsPayoutDelay,
            yearlyReturns.gain,
            unstakingPeriod
        )
    }.stateIn(
        this.viewModelScope,
        SharingStarted.Eagerly,
        SetupStakingPoolViewState(
            ToolbarViewState(
                resourceManager.getString(R.string.pool_staking_title),
                R.drawable.ic_arrow_back_24dp
            ),
            "...",
            "...",
            "...",
            "..."
        )
    )

    fun onBackClick() {
        router.back()
    }

    fun onInstructionsClick() {}

    fun onJoinPool() {
        router.openSetupStakingPool()
    }

    fun onCreatePool() {}
}
