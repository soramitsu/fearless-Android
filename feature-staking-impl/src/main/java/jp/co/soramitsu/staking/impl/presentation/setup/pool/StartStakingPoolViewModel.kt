package jp.co.soramitsu.staking.impl.presentation.setup.pool

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.AlertViewState
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolCreateFlowState
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolJoinFlowState
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.presentation.mappers.mapPeriodReturnsToRewardEstimation
import jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios.PERIOD_YEAR
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.staking.impl.scenarios.relaychain.HOURS_IN_DAY
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class StartStakingPoolViewModel @Inject constructor(
    private val relayChainScenarioInteractor: StakingRelayChainScenarioInteractor,
    private val stakingSharedState: StakingSharedState,
    private val stakingPoolInteractor: StakingPoolInteractor,
    private val resourceManager: ResourceManager,
    private val rewardCalculatorFactory: RewardCalculatorFactory,
    private val router: StakingRouter,
    private val flowStateProvider: StakingPoolSharedStateProvider
) : BaseViewModel() {

    private val assetDeferred = viewModelScope.async { stakingSharedState.currentAssetFlow().first() }
    private val chainDeferred = viewModelScope.async { stakingSharedState.assetWithChain.first().chain }

    init {
        viewModelScope.launch {
            val rewardsPayoutDelayDeferred = async {
                val hours = relayChainScenarioInteractor.stakePeriodInHours()
                resourceManager.getQuantityString(R.plurals.common_hours_format, hours, hours)
            }
            val unstakingPeriodDeferred = async {
                val lockupPeriodInHours = relayChainScenarioInteractor.unstakingPeriod()
                if (lockupPeriodInHours > HOURS_IN_DAY) {
                    val inDays = lockupPeriodInHours / HOURS_IN_DAY
                    resourceManager.getQuantityString(R.plurals.common_days_format, inDays, inDays)
                } else {
                    resourceManager.getQuantityString(
                        R.plurals.common_hours_format,
                        lockupPeriodInHours,
                        lockupPeriodInHours
                    )
                }
            }
            val asset = assetDeferred.await()
            val chain = chainDeferred.await()
            val yearlyReturnsDeferred = async {
                // todo hardcoded returns for demo
                val kusamaOnTestNodeChainId =
                    "51cdb4b3101904a9d234d126656d33cd17518249819b510a03d6c90d0a019611"
                val polkadotOnTestNodeChainId =
                    "4f77f65b21b1f396c1555850be6f21e2b1f36c26b94dbcbfec976901c9f08bf3"
                val chainId =
                    if (chain.id == kusamaOnTestNodeChainId || chain.id == polkadotOnTestNodeChainId) {
                        polkadotChainId
                    } else {
                        chain.id
                    }
                val rewardCalculator = rewardCalculatorFactory.create(asset.token.configuration)
                val yearly =
                    rewardCalculator.calculateReturns(BigDecimal.ONE, PERIOD_YEAR, true, chainId)

                mapPeriodReturnsToRewardEstimation(
                    yearly,
                    asset.token,
                    resourceManager
                )
            }
            state.update { prevState ->
                prevState.copy(
                    assetName = asset.token.configuration.symbol,
                    rewardsPayoutDelay = rewardsPayoutDelayDeferred.await(),
                    unstakingPeriod = unstakingPeriodDeferred.await(),
                    yearlyEstimatedEarnings = yearlyReturnsDeferred.await().gain
                )
            }
        }
    }

    private val poolsLimitHasReachedDeferred = viewModelScope.async {
        val chain = chainDeferred.await()
        val possiblePools = stakingPoolInteractor.getPossiblePools(chain.id)
        val poolsCount = stakingPoolInteractor.getPoolsCount(chain.id)
        poolsCount >= possiblePools
    }

    val state: MutableStateFlow<SetupStakingPoolViewState> =
        MutableStateFlow(
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

    fun onInstructionsClick() {
        router.openWebViewer(
            resourceManager.getString(R.string.pool_staking_title),
            BuildConfig.STAKING_POOL_WIKI
        )
    }

    fun onJoinPool() {
        val setupState = flowStateProvider.joinFlowState
        if (setupState.get() == null) {
            setupState.set(StakingPoolJoinFlowState())
        }
        router.openSetupStakingPool()
    }

    fun onCreatePool() {
        viewModelScope.launch {
            val limitHasReached = poolsLimitHasReachedDeferred.await()
            if (limitHasReached) {
                router.openAlert(
                    AlertViewState(
                        title = resourceManager.getString(R.string.pools_limit_has_reached_error_title),
                        message = resourceManager.getString(R.string.pools_limit_has_reached_error_message),
                        buttonText = resourceManager.getString(R.string.common_got_it),
                        iconRes = R.drawable.ic_status_warning_16
                    )
                )
                return@launch
            }
            val setupState = flowStateProvider.createFlowState
            if (setupState.get() == null) {
                setupState.set(StakingPoolCreateFlowState())
            }
            router.openCreatePoolSetup()
        }
    }
}
