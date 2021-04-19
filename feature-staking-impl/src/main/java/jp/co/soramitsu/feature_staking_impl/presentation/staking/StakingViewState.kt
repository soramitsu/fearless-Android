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
import jp.co.soramitsu.feature_staking_impl.domain.model.NominatorStatus
import jp.co.soramitsu.feature_staking_impl.domain.model.NominatorStatus.Inactive.Reason
import jp.co.soramitsu.feature_staking_impl.domain.model.StakerSummary
import jp.co.soramitsu.feature_staking_impl.domain.rewards.RewardCalculator
import jp.co.soramitsu.feature_staking_impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingSharedState
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
import kotlin.time.ExperimentalTime

sealed class StakingViewState

private const val PERIOD_MONTH = 30
private const val PERIOD_YEAR = 365

class ReturnsModel(
    val monthly: RewardEstimation,
    val yearly: RewardEstimation,
)

object ValidatorViewState : StakingViewState()

class NominatorSummaryModel<S>(
    val status: S,
    val totalStaked: String,
    val totalStakedFiat: String?,
    val totalRewards: String,
    val totalRewardsFiat: String?,
    val currentEraDisplay: String,
)

enum class ManageStakeAction {
    PAYOUTS, STUB
}

typealias TitleAndMessage = Pair<String, String>

abstract class StakeViewState<S>(
    private val stakeState: StakingState.Stash,
    private val currentAssetFlow: Flow<Asset>,
    private val stakingInteractor: StakingInteractor,
    private val resourceManager: ResourceManager,
    private val scope: CoroutineScope,
    private val router: StakingRouter,
    private val errorDisplayer: (Throwable) -> Unit,
    private val summaryFlowProvider: suspend (StakingState.Stash) -> Flow<StakerSummary<S>>,
    private val statusMessageProvider: (S) -> TitleAndMessage
) : StakingViewState() {

    val stakeSummaryLiveData = liveData<LoadingState<NominatorSummaryModel<S>>> {
        emitAll(
            summaryFlow()
                .withLoading()
                .flowOn(Dispatchers.Default)
        )
    }

    private val _showStatusAlertEvent = MutableLiveData<Event<Pair<String, String>>>()
    val showStatusAlertEvent: LiveData<Event<Pair<String, String>>> = _showStatusAlertEvent

    init {
        syncStakingRewards()
    }

    fun statusClicked() {
        val nominatorSummaryModel = loadedSummaryOrNull() ?: return

        val titleAndMessage = statusMessageProvider(nominatorSummaryModel.status)

        _showStatusAlertEvent.value = Event(titleAndMessage)
    }

    private fun syncStakingRewards() {
        scope.launch {
            val syncResult = stakingInteractor.syncStakingRewards(stakeState.accountAddress)

            syncResult.exceptionOrNull()?.let { errorDisplayer(it) }
        }
    }

    private fun loadedSummaryOrNull(): NominatorSummaryModel<S>? {
        return when (val state = stakeSummaryLiveData.value) {
            is LoadingState.Loaded<NominatorSummaryModel<S>> -> state.data
            else -> null
        }
    }

    private suspend fun summaryFlow(): Flow<NominatorSummaryModel<S>> {
        return combine(
            summaryFlowProvider(stakeState),
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
}

@OptIn(ExperimentalTime::class) class NominatorViewState(
    private val nominatorState: StakingState.Stash.Nominator,
    private val currentAssetFlow: Flow<Asset>,
    private val stakingInteractor: StakingInteractor,
    private val resourceManager: ResourceManager,
    private val scope: CoroutineScope,
    private val router: StakingRouter,
    private val errorDisplayer: (Throwable) -> Unit,
) : StakeViewState<NominatorStatus>(
    nominatorState, currentAssetFlow, stakingInteractor,
    resourceManager, scope, router, errorDisplayer,
    summaryFlowProvider = { stakingInteractor.observeNominatorSummary(nominatorState) },
    statusMessageProvider = { getNominatorStatusTitleAndMessage(resourceManager, it) }
) {


    private val _showManageActionsEvent = MutableLiveData<Event<Unit>>()
    val showManageActionsEvent: LiveData<Event<Unit>> = _showManageActionsEvent

    fun manageActionChosen(action: ManageStakeAction) {
        when (action) {
            ManageStakeAction.PAYOUTS -> router.openPayouts()
        }
    }


    fun moreActionsClicked() {
        _showManageActionsEvent.sendEvent()
    }
}

private fun getNominatorStatusTitleAndMessage(
    resourceManager: ResourceManager,
    status: NominatorStatus
): Pair<String, String> {
    val (titleRes, messageRes) = when (status) {
        is NominatorStatus.Active -> R.string.staking_nominator_status_alert_active_title to R.string.staking_nominator_status_alert_active_message

        is NominatorStatus.Election -> R.string.staking_nominator_status_election to R.string.staking_nominator_status_alert_election_message

        is NominatorStatus.Waiting -> R.string.staking_nominator_status_waiting to R.string.staking_nominator_status_alert_waiting_message

        is NominatorStatus.Inactive -> when (status.reason) {
            Reason.MIN_STAKE -> R.string.staking_nominator_status_alert_inactive_title to R.string.staking_nominator_status_alert_low_stake
            Reason.NO_ACTIVE_VALIDATOR -> R.string.staking_nominator_status_alert_inactive_title to R.string.staking_nominator_status_alert_no_validators
        }
    }

    return resourceManager.getString(titleRes) to resourceManager.getString(messageRes)
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
    private val errorDisplayer: (String) -> Unit,
) : StakingViewState() {

    private val currentSetupProgress = setupStakingSharedState.get<SetupStakingProcess.Initial>()

    val enteredAmountFlow = MutableStateFlow(currentSetupProgress.defaultAmount.toString())

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
                val existingStashSetup = interactor.getExistingStashSetup(accountStakingState)

                if (interactor.isAccountInApp(existingStashSetup.controllerAddress)) {
                    setupStakingSharedState.set(currentSetupProgress.next(asset.bonded, existingStashSetup))

                    router.openRecommendedValidators()
                } else {
                    errorDisplayer(resourceManager.getString(R.string.staking_no_controller_account, existingStashSetup.controllerAddress))
                }
            } else {
                setupStakingSharedState.set(currentSetupProgress.next(parsedAmountFlow.first()))

                router.openSetupStaking()
            }
        }
    }

    private suspend fun rewardCalculator(): RewardCalculator {
        return rewardCalculator.await()
    }
}
