package jp.co.soramitsu.feature_staking_impl.presentation.staking.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.base.TitleAndMessage
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.asLiveData
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.formatAsPercentage
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.withLoading
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.model.NominatorStatus
import jp.co.soramitsu.feature_staking_impl.domain.model.NominatorStatus.Inactive.Reason
import jp.co.soramitsu.feature_staking_impl.domain.model.StakeSummary
import jp.co.soramitsu.feature_staking_impl.domain.model.StashNoneStatus
import jp.co.soramitsu.feature_staking_impl.domain.model.ValidatorStatus
import jp.co.soramitsu.feature_staking_impl.domain.rewards.RewardCalculator
import jp.co.soramitsu.feature_staking_impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.feature_staking_impl.domain.validations.welcome.WelcomeStakingValidationPayload
import jp.co.soramitsu.feature_staking_impl.domain.validations.welcome.WelcomeStakingValidationSystem
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.feature_staking_impl.presentation.mappers.mapPeriodReturnsToRewardEstimation
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.model.RewardEstimation
import jp.co.soramitsu.feature_wallet_api.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

sealed class StakingViewState

private const val PERIOD_MONTH = 30
private const val PERIOD_YEAR = 365

class ReturnsModel(
    val monthly: RewardEstimation,
    val yearly: RewardEstimation,
)

class StakeSummaryModel<S>(
    val status: S,
    val totalStaked: String,
    val totalStakedFiat: String?,
    val totalRewards: String,
    val totalRewardsFiat: String?,
    val currentEraDisplay: String
)

typealias NominatorSummaryModel = StakeSummaryModel<NominatorStatus>
typealias ValidatorSummaryModel = StakeSummaryModel<ValidatorStatus>
typealias StashNoneSummaryModel = StakeSummaryModel<StashNoneStatus>

enum class ManageStakeAction {
    PAYOUTS, BALANCE, CONTROLLER, VALIDATORS, REWARD_DESTINATION
}

sealed class StakeViewState<S>(
    private val stakeState: StakingState.Stash,
    protected val currentAssetFlow: Flow<Asset>,
    protected val stakingInteractor: StakingInteractor,
    protected val resourceManager: ResourceManager,
    protected val scope: CoroutineScope,
    protected val router: StakingRouter,
    protected val errorDisplayer: (Throwable) -> Unit,
    protected val summaryFlowProvider: suspend (StakingState.Stash) -> Flow<StakeSummary<S>>,
    protected val statusMessageProvider: (S) -> TitleAndMessage,
    private val availableManageActions: Set<ManageStakeAction>
) : StakingViewState() {

    init {
        syncStakingRewards()
    }

    val manageStakingActionsButtonVisible = availableManageActions.isNotEmpty()

    private val _showManageActionsEvent = MutableLiveData<Event<ManageStakingBottomSheet.Payload>>()
    val showManageActionsEvent: LiveData<Event<ManageStakingBottomSheet.Payload>> = _showManageActionsEvent

    fun manageActionChosen(action: ManageStakeAction) {
        if (action !in availableManageActions) return

        when (action) {
            ManageStakeAction.PAYOUTS -> router.openPayouts()
            ManageStakeAction.BALANCE -> router.openStakingBalance()
            ManageStakeAction.CONTROLLER -> router.openControllerAccount()
            ManageStakeAction.VALIDATORS -> router.openCurrentValidators()
            ManageStakeAction.REWARD_DESTINATION -> router.openChangeRewardDestination()
        }
    }

    fun moreActionsClicked() {
        _showManageActionsEvent.value = Event(ManageStakingBottomSheet.Payload(availableManageActions))
    }

    val stakeSummaryFlow = flow { emitAll(summaryFlow()) }
        .withLoading()
        .inBackground()
        .shareIn(scope, SharingStarted.Eagerly, replay = 1)

    private val _showStatusAlertEvent = MutableLiveData<Event<Pair<String, String>>>()
    val showStatusAlertEvent: LiveData<Event<Pair<String, String>>> = _showStatusAlertEvent

    fun statusClicked() {
        val nominatorSummaryModel = loadedSummaryOrNull() ?: return

        val titleAndMessage = statusMessageProvider(nominatorSummaryModel.status)

        _showStatusAlertEvent.value = Event(titleAndMessage)
    }

    private fun syncStakingRewards() {
        scope.launch {
            val syncResult = stakingInteractor.syncStakingRewards(stakeState.chain.id, stakeState.stashAddress)

            syncResult.exceptionOrNull()?.let { errorDisplayer(it) }
        }
    }

    @ExperimentalCoroutinesApi
    private suspend fun summaryFlow(): Flow<StakeSummaryModel<S>> {
        return combine(
            summaryFlowProvider(stakeState),
            currentAssetFlow
        ) { summary, asset ->
            val token = asset.token
            val tokenType = token.configuration

            StakeSummaryModel(
                status = summary.status,
                totalStaked = summary.totalStaked.formatTokenAmount(tokenType),
                totalStakedFiat = token.fiatAmount(summary.totalStaked)?.formatAsCurrency(token.fiatSymbol),
                totalRewards = summary.totalReward.formatTokenAmount(tokenType),
                totalRewardsFiat = token.fiatAmount(summary.totalReward)?.formatAsCurrency(token.fiatSymbol),
                currentEraDisplay = resourceManager.getString(R.string.staking_era_title, summary.currentEra)
            )
        }
    }

    private fun loadedSummaryOrNull(): StakeSummaryModel<S>? {
        return when (val state = stakeSummaryFlow.replayCache.firstOrNull()) {
            is LoadingState.Loaded<StakeSummaryModel<S>> -> state.data
            else -> null
        }
    }
}

class ValidatorViewState(
    validatorState: StakingState.Stash.Validator,
    currentAssetFlow: Flow<Asset>,
    stakingInteractor: StakingInteractor,
    resourceManager: ResourceManager,
    scope: CoroutineScope,
    router: StakingRouter,
    errorDisplayer: (Throwable) -> Unit,
) : StakeViewState<ValidatorStatus>(
    validatorState, currentAssetFlow, stakingInteractor,
    resourceManager, scope, router, errorDisplayer,
    summaryFlowProvider = { stakingInteractor.observeValidatorSummary(validatorState) },
    statusMessageProvider = { getValidatorStatusTitleAndMessage(resourceManager, it) },
    availableManageActions = ManageStakeAction.values().toSet() - ManageStakeAction.VALIDATORS
)

private fun getValidatorStatusTitleAndMessage(
    resourceManager: ResourceManager,
    status: ValidatorStatus
): Pair<String, String> {
    val (titleRes, messageRes) = when (status) {
        ValidatorStatus.ACTIVE -> R.string.staking_nominator_status_alert_active_title to R.string.staking_nominator_status_alert_active_message

        ValidatorStatus.INACTIVE -> R.string.staking_nominator_status_alert_inactive_title to R.string.staking_nominator_status_alert_no_validators
    }

    return resourceManager.getString(titleRes) to resourceManager.getString(messageRes)
}

class StashNoneViewState(
    stashState: StakingState.Stash.None,
    currentAssetFlow: Flow<Asset>,
    stakingInteractor: StakingInteractor,
    resourceManager: ResourceManager,
    scope: CoroutineScope,
    router: StakingRouter,
    errorDisplayer: (Throwable) -> Unit,
) : StakeViewState<StashNoneStatus>(
    stashState, currentAssetFlow, stakingInteractor,
    resourceManager, scope, router, errorDisplayer,
    summaryFlowProvider = { stakingInteractor.observeStashSummary(stashState) },
    statusMessageProvider = { getStashStatusTitleAndMessage(resourceManager, it) },
    availableManageActions = ManageStakeAction.values().toSet() - ManageStakeAction.PAYOUTS
)

private fun getStashStatusTitleAndMessage(
    resourceManager: ResourceManager,
    status: StashNoneStatus
): Pair<String, String> {
    val (titleRes, messageRes) = when (status) {
        StashNoneStatus.INACTIVE -> R.string.staking_nominator_status_alert_inactive_title to R.string.staking_bonded_inactive
    }

    return resourceManager.getString(titleRes) to resourceManager.getString(messageRes)
}

class NominatorViewState(
    nominatorState: StakingState.Stash.Nominator,
    currentAssetFlow: Flow<Asset>,
    stakingInteractor: StakingInteractor,
    resourceManager: ResourceManager,
    scope: CoroutineScope,
    router: StakingRouter,
    errorDisplayer: (Throwable) -> Unit,
) : StakeViewState<NominatorStatus>(
    nominatorState, currentAssetFlow, stakingInteractor,
    resourceManager, scope, router, errorDisplayer,
    summaryFlowProvider = { stakingInteractor.observeNominatorSummary(nominatorState) },
    statusMessageProvider = { getNominatorStatusTitleAndMessage(resourceManager, it) },
    availableManageActions = ManageStakeAction.values().toSet()
)

private fun getNominatorStatusTitleAndMessage(
    resourceManager: ResourceManager,
    status: NominatorStatus
): Pair<String, String> {
    val (titleRes, messageRes) = when (status) {
        is NominatorStatus.Active -> R.string.staking_nominator_status_alert_active_title to R.string.staking_nominator_status_alert_active_message

        is NominatorStatus.Waiting -> R.string.staking_nominator_status_waiting to R.string.staking_nominator_status_alert_waiting_message

        is NominatorStatus.Inactive -> when (status.reason) {
            Reason.MIN_STAKE -> R.string.staking_nominator_status_alert_inactive_title to R.string.staking_nominator_status_alert_low_stake
            Reason.NO_ACTIVE_VALIDATOR -> R.string.staking_nominator_status_alert_inactive_title to R.string.staking_nominator_status_alert_no_validators
        }
    }

    return resourceManager.getString(titleRes) to resourceManager.getString(messageRes)
}

sealed class WelcomeViewState(
    protected val setupStakingSharedState: SetupStakingSharedState,
    protected val rewardCalculatorFactory: RewardCalculatorFactory,
    protected val resourceManager: ResourceManager,
    protected val router: StakingRouter,
    currentAssetFlow: Flow<Asset>,
    protected val scope: CoroutineScope,
    protected val errorDisplayer: (String) -> Unit,
    protected val validationSystem: WelcomeStakingValidationSystem,
    protected val validationExecutor: ValidationExecutor
) : StakingViewState(), Validatable by validationExecutor {

    protected val currentSetupProgress = setupStakingSharedState.get<SetupStakingProcess.Initial>()

    val enteredAmountFlow = MutableStateFlow(currentSetupProgress.defaultAmount.toString())

    protected val parsedAmountFlow = enteredAmountFlow.mapNotNull { it.toBigDecimalOrNull() }

    protected abstract val rewardCalculator: Deferred<RewardCalculator>

    abstract val returns: LiveData<ReturnsModel>

    abstract fun infoActionClicked()
    abstract fun nextClicked()

    protected val _showRewardEstimationEvent = MutableLiveData<Event<StakingRewardEstimationBottomSheet.Payload>>()
    val showRewardEstimationEvent: LiveData<Event<StakingRewardEstimationBottomSheet.Payload>> = _showRewardEstimationEvent

    protected suspend fun rewardCalculator(): RewardCalculator {
        return rewardCalculator.await()
    }

    val assetLiveData = currentAssetFlow.map { mapAssetToAssetModel(it, resourceManager) }.asLiveData(scope)

    val amountFiat = parsedAmountFlow.combine(currentAssetFlow) { amount, asset -> asset.token.fiatAmount(amount)?.formatAsCurrency(asset.token.fiatSymbol) }
        .asLiveData(scope)
}

class RelaychainWelcomeViewState(
    setupStakingSharedState: SetupStakingSharedState,
    rewardCalculatorFactory: RewardCalculatorFactory,
    resourceManager: ResourceManager,
    router: StakingRouter,
    currentAssetFlow: Flow<Asset>,
    scope: CoroutineScope,
    errorDisplayer: (String) -> Unit,
    validationSystem: WelcomeStakingValidationSystem,
    validationExecutor: ValidationExecutor
) : WelcomeViewState(
    setupStakingSharedState,
    rewardCalculatorFactory,
    resourceManager,
    router,
    currentAssetFlow,
    scope,
    errorDisplayer,
    validationSystem,
    validationExecutor
) {

    override val rewardCalculator = scope.async { rewardCalculatorFactory.createManual() }

    override val returns: LiveData<ReturnsModel> = currentAssetFlow.combine(parsedAmountFlow) { asset, amount ->
        val monthly = rewardCalculator().calculateReturns(amount, PERIOD_MONTH, true)
        val yearly = rewardCalculator().calculateReturns(amount, PERIOD_YEAR, true)

        val monthlyEstimation = mapPeriodReturnsToRewardEstimation(monthly, asset.token, resourceManager)
        val yearlyEstimation = mapPeriodReturnsToRewardEstimation(yearly, asset.token, resourceManager)

        ReturnsModel(monthlyEstimation, yearlyEstimation)
    }.asLiveData(scope)

    override fun infoActionClicked() {
        scope.launch {
            val rewardCalculator = rewardCalculator()

            val maxAPY = rewardCalculator.calculateMaxAPY()
            val avgAPY = rewardCalculator.calculateAvgAPY()

            val payload = StakingRewardEstimationBottomSheet.Payload(
                maxAPY.formatAsPercentage(),
                avgAPY.formatAsPercentage()
            )

            _showRewardEstimationEvent.value = Event(payload)
        }
    }

    override fun nextClicked() {
        scope.launch {
            val payload = WelcomeStakingValidationPayload()
            val amount = parsedAmountFlow.first()

            validationExecutor.requireValid(
                validationSystem = validationSystem,
                payload = payload,
                errorDisplayer = { it.message?.let(errorDisplayer) },
                validationFailureTransformer = { welcomeStakingValidationFailure(it, resourceManager) },
            ) {
                setupStakingSharedState.set(currentSetupProgress.fullFlow(amount))

                router.openSetupStaking()
            }
        }
    }
}

class ParachainWelcomeViewState(
    setupStakingSharedState: SetupStakingSharedState,
    rewardCalculatorFactory: RewardCalculatorFactory,
    resourceManager: ResourceManager,
    router: StakingRouter,
    currentAssetFlow: Flow<Asset>,
    scope: CoroutineScope,
    errorDisplayer: (String) -> Unit,
    validationSystem: WelcomeStakingValidationSystem,
    validationExecutor: ValidationExecutor
) : WelcomeViewState(
    setupStakingSharedState,
    rewardCalculatorFactory,
    resourceManager,
    router,
    currentAssetFlow,
    scope,
    errorDisplayer,
    validationSystem,
    validationExecutor
) {

    override val rewardCalculator = scope.async { rewardCalculatorFactory.createSubquery() }

    override val returns: LiveData<ReturnsModel> = currentAssetFlow.combine(parsedAmountFlow) { asset, amount ->
        val monthly = rewardCalculator().calculateReturns(amount, PERIOD_MONTH, true)
        val yearly = rewardCalculator().calculateReturns(amount, PERIOD_YEAR, true)

        val monthlyEstimation = mapPeriodReturnsToRewardEstimation(monthly, asset.token, resourceManager)
        val yearlyEstimation = mapPeriodReturnsToRewardEstimation(yearly, asset.token, resourceManager)

        ReturnsModel(monthlyEstimation, yearlyEstimation)
    }.asLiveData(scope)

    override fun infoActionClicked() {
        scope.launch {
            val rewardCalculator = rewardCalculator()

            val maxAPY = rewardCalculator.calculateMaxAPY()
            val avgAPY = rewardCalculator.calculateAvgAPY()

            val payload = StakingRewardEstimationBottomSheet.Payload(
                maxAPY.formatAsPercentage(),
                avgAPY.formatAsPercentage()
            )

            _showRewardEstimationEvent.value = Event(payload)
        }
    }

    override fun nextClicked() {
        scope.launch {
            val amount = parsedAmountFlow.first()
            setupStakingSharedState.set(currentSetupProgress.fullFlow(amount))
            router.openSetupStaking()
        }
    }
}

object DelegatorViewState : StakingViewState(){

}
object CollatorViewState : StakingViewState()
