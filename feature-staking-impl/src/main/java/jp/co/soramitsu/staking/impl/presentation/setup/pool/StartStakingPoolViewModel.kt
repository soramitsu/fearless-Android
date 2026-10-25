package jp.co.soramitsu.staking.impl.presentation.setup.pool

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.AlertViewState
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolJoinFlowState
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.presentation.mappers.mapPeriodReturnsToRewardEstimation
import jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios.PERIOD_YEAR
import jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios.calculatePoolStakingReturns
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.staking.impl.scenarios.relaychain.HOURS_IN_DAY
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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

    private val mainReview = flowStateProvider.mainState.capture()
    private val review = flowStateProvider.poolContextReview(mainReview)
    private val assetDeferred = viewModelScope.async { requireNotNull(mainReview.value?.asset) }
    private val chainDeferred = viewModelScope.async { requireNotNull(mainReview.value?.chain) }

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
                val rewardCalculator = rewardCalculatorFactory.create(asset.token.configuration)
                val yearly = rewardCalculator.calculatePoolStakingReturns(
                    BigDecimal.ONE,
                    PERIOD_YEAR,
                    chain.id
                )

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
        if (review.revoke()) router.back()
    }

    fun onDismissed() {
        review.revoke()
    }

    override fun onCleared() {
        review.revoke()
        super.onCleared()
    }

    fun onInstructionsClick() {
        review.withCurrent {
            router.openWebViewer(
                resourceManager.getString(R.string.pool_staking_title),
                BuildConfig.STAKING_POOL_WIKI
            )
        }
    }

    fun onJoinPool() {
        review.withCurrent {
            val setupState = flowStateProvider.joinFlowState
            val original = setupState.capture()
            val receipt = if (original.value?.context?.let(flowStateProvider::isPoolContextCurrent) == true) {
                original.takeIf(setupState::isEditable)
            } else {
                setupState.replaceIfCurrent(original, StakingPoolJoinFlowState(context = mainReview))
            } ?: return@withCurrent
            review.withCurrent {
                if (setupState.isCurrent(receipt)) router.openSetupStakingPool()
            }
        }
    }

    fun onCreatePool() {
        val draft = review.withCurrent { flowStateProvider.createFlowState.capture() } ?: return
        viewModelScope.launch {
            try {
                val limitHasReached = poolsLimitHasReachedDeferred.await()
                currentCoroutineContext().ensureActive()
                review.withCurrent {
                    if (!flowStateProvider.createFlowState.isCurrent(draft)) return@withCurrent
                    if (limitHasReached) {
                        router.openAlert(
                            AlertViewState(
                                title = resourceManager.getString(R.string.pools_limit_has_reached_error_title),
                                message = resourceManager.getString(R.string.pools_limit_has_reached_error_message),
                                buttonText = resourceManager.getString(R.string.common_got_it),
                                iconRes = R.drawable.ic_status_warning_16
                            )
                        )
                    } else {
                        val receipt = flowStateProvider.prepareCreateDraft(mainReview, draft) ?: return@withCurrent
                        review.withCurrent {
                            if (flowStateProvider.createFlowState.isCurrent(receipt)) router.openCreatePoolSetup()
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                review.withCurrent {
                    if (flowStateProvider.createFlowState.isCurrent(draft)) showError(error)
                }
            }
        }
    }
}
