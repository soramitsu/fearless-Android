package jp.co.soramitsu.feature_staking_impl.presentation.staking

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.asLiveData
import jp.co.soramitsu.common.utils.emitAll
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.sendEvent
import jp.co.soramitsu.common.utils.withLoading
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.model.NominatorSummary
import jp.co.soramitsu.feature_staking_impl.domain.model.NominatorSummary.Status.Inactive.Reason
import jp.co.soramitsu.feature_staking_impl.domain.rewards.RewardCalculator
import jp.co.soramitsu.feature_staking_impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.feature_staking_impl.presentation.common.StashSetup
import jp.co.soramitsu.feature_staking_impl.presentation.common.mapAssetToAssetModel
import jp.co.soramitsu.feature_staking_impl.presentation.mappers.mapPeriodReturnsToRewardEstimation
import jp.co.soramitsu.feature_staking_impl.presentation.staking.model.RewardEstimation
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatWithDefaultPrecision
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

sealed class StakingViewState

private const val PERIOD_MONTH = 30
private const val PERIOD_YEAR = 365

class ReturnsModel(
    val monthly: RewardEstimation,
    val yearly: RewardEstimation,
)

object ValidatorViewState : StakingViewState()

class NominatorSummaryModel(
    val status: NominatorSummary.Status,
    val totalStaked: String,
    val totalStakedFiat: String?,
    val totalRewards: String,
    val totalRewardsFiat: String?,
    val currentEraDisplay: String,
)

class NominatorViewState(
    private val nominatorState: StakingState.Stash.Nominator,
    private val currentAssetFlow: Flow<Asset>,
    private val stakingInteractor: StakingInteractor,
    private val resourceManager: ResourceManager,
    private val scope: CoroutineScope,
    private val errorDisplayer: (Throwable) -> Unit,
) : StakingViewState() {

    val nominatorSummaryLiveData = liveData<LoadingState<NominatorSummaryModel>> {
        emitAll(
            nominatorSummaryFlow()
                .withLoading()
                .flowOn(Dispatchers.Default)
        )
    }

    private val _showStatusAlertEvent = MutableLiveData<Event<Pair<String, String>>>()
    val showStatusAlertEvent: LiveData<Event<Pair<String, String>>> = _showStatusAlertEvent

    private val _showManageActionsEvent = MutableLiveData<Event<Unit>>()
    val showManageActionsEvent: LiveData<Event<Unit>> = _showManageActionsEvent

    fun syncStakingRewards() {
        scope.launch {
            val syncResult = stakingInteractor.syncStakingRewards(nominatorState.accountAddress)

            syncResult.exceptionOrNull()?.let { errorDisplayer(it) }
        }
    }

    fun statusClicked() {
        val nominatorSummaryModel = loadedNominatorSummaryOrNull() ?: return

        val titleAndMessage = getStatusAlertTitleAndMessage(nominatorSummaryModel.status)

        _showStatusAlertEvent.value = Event(titleAndMessage)
    }

    private suspend fun nominatorSummaryFlow(): Flow<NominatorSummaryModel> {
        return combine(
            stakingInteractor.observeNominatorSummary(nominatorState),
            currentAssetFlow
        ) { summary, asset ->
            val token = asset.token
            val tokenType = token.type

            NominatorSummaryModel(
                status = summary.status,
                totalStaked = summary.totalStaked.formatWithDefaultPrecision(tokenType),
                totalStakedFiat = token.fiatAmount(summary.totalStaked)?.formatAsCurrency(),
                totalRewards = summary.totalRewards.formatWithDefaultPrecision(tokenType),
                totalRewardsFiat = token.fiatAmount(summary.totalRewards)?.formatAsCurrency(),
                currentEraDisplay = resourceManager.getString(R.string.staking_era_index, summary.currentEra)
            )
        }
    }

    private fun getStatusAlertTitleAndMessage(status: NominatorSummary.Status): Pair<String, String> {
        val (titleRes, messageRes) = when (status) {
            is NominatorSummary.Status.Active -> R.string.staking_nominator_status_alert_active_title to R.string.staking_nominator_status_alert_active_message

            is NominatorSummary.Status.Election -> R.string.staking_nominator_status_election to R.string.staking_nominator_status_alert_election_message

            is NominatorSummary.Status.Waiting -> R.string.staking_nominator_status_waiting to R.string.staking_nominator_status_alert_waiting_message

            is NominatorSummary.Status.Inactive -> when (status.reason) {
                Reason.MIN_STAKE -> R.string.staking_nominator_status_alert_inactive_title to R.string.staking_nominator_status_alert_low_stake
                Reason.NO_ACTIVE_VALIDATOR -> R.string.staking_nominator_status_alert_inactive_title to R.string.staking_nominator_status_alert_no_validators
            }
        }

        return resourceManager.getString(titleRes) to resourceManager.getString(messageRes)
    }

    private fun loadedNominatorSummaryOrNull(): NominatorSummaryModel? {
        return when (val state = nominatorSummaryLiveData.value) {
            is LoadingState.Loaded<NominatorSummaryModel> -> state.data
            else -> null
        }
    }

    fun moreActionsClicked() {
        _showManageActionsEvent.sendEvent()
    }
}

class WelcomeViewState(
    private val setupStakingSharedState: SetupStakingSharedState,
    private val rewardCalculatorFactory: RewardCalculatorFactory,
    private val interactor: StakingInteractor,
    private val resourceManager: ResourceManager,
    private val router: StakingRouter,
    private val accountStakingState: StakingState,
    private val currentAssetFlow: Flow<Asset>,
    private val scope: CoroutineScope,
) : StakingViewState() {

    val enteredAmountFlow = MutableStateFlow(SetupStakingSharedState.DEFAULT_AMOUNT.toString())

    private val parsedAmountFlow = enteredAmountFlow.mapNotNull { it.toBigDecimalOrNull() }

    val assetLiveData = currentAssetFlow.map { mapAssetToAssetModel(it, resourceManager) }.asLiveData(scope)

    val amountFiat = parsedAmountFlow.combine(currentAssetFlow) { amount, asset -> asset.token.fiatAmount(amount)?.formatAsCurrency() }
        .filterNotNull()
        .asLiveData(scope)

    private val rewardCalculator = scope.async { rewardCalculatorFactory.create() }

    val returns: LiveData<ReturnsModel> = currentAssetFlow.combine(parsedAmountFlow) { asset, amount ->
        val monthly = rewardCalculator().calculateReturns(amount, PERIOD_MONTH, true)
        val yearly = rewardCalculator().calculateReturns(amount, PERIOD_YEAR, true)

        val monthlyEstimation = mapPeriodReturnsToRewardEstimation(monthly, asset.token, resourceManager)
        val yearlyEstimation = mapPeriodReturnsToRewardEstimation(yearly, asset.token, resourceManager)

        ReturnsModel(monthlyEstimation, yearlyEstimation)
    }.asLiveData(scope)

    fun nextClicked() {
        scope.launch {
            if (accountStakingState is StakingState.Stash.None) {
                val asset = currentAssetFlow.first()
                setupStakingSharedState.stashSetup = interactor.getExistingStashSetup(accountStakingState, asset)

                router.openRecommendedValidators()
            } else {
                setupStakingSharedState.stashSetup = StashSetup.defaultFromAmount(parsedAmountFlow.first())

                router.openSetupStaking()
            }
        }
    }

    private suspend fun rewardCalculator(): RewardCalculator {
        return rewardCalculator.await()
    }
}
