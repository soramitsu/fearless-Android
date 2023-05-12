package jp.co.soramitsu.staking.impl.presentation.staking.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.base.TitleAndMessage
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.asLiveData
import jp.co.soramitsu.common.utils.formatAsPercentage
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.withLoading
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.ext.accountFromMapKey
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.staking.api.domain.model.CandidateInfo
import jp.co.soramitsu.staking.api.domain.model.CandidateInfoStatus
import jp.co.soramitsu.staking.api.domain.model.Round
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.getSelectedChain
import jp.co.soramitsu.staking.impl.domain.model.DelegatorStatus
import jp.co.soramitsu.staking.impl.domain.model.NominatorStatus
import jp.co.soramitsu.staking.impl.domain.model.NominatorStatus.Inactive.Reason
import jp.co.soramitsu.staking.impl.domain.model.StakeSummary
import jp.co.soramitsu.staking.impl.domain.model.StashNoneStatus
import jp.co.soramitsu.staking.impl.domain.model.ValidatorStatus
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculator
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.staking.impl.domain.rewards.SoraStakingRewardsScenario
import jp.co.soramitsu.staking.impl.domain.validations.welcome.WelcomeStakingValidationPayload
import jp.co.soramitsu.staking.impl.domain.validations.welcome.WelcomeStakingValidationSystem
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.staking.impl.presentation.mappers.mapPeriodReturnsToRewardEstimation
import jp.co.soramitsu.staking.impl.presentation.staking.main.model.RewardEstimation
import jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios.PERIOD_MONTH
import jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios.PERIOD_YEAR
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.CollatorDetailsParcelModel
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.CollatorStakeParcelModel
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.IdentityParcelModel
import jp.co.soramitsu.staking.impl.scenarios.parachain.StakingParachainScenarioInteractor
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.wallet.api.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset as CoreAsset

@Deprecated("All ViewStates should be provided and created in staking type aware ViewModels")
sealed class StakingViewStateOld

data class ReturnsModel(
    val monthly: RewardEstimation,
    val yearly: RewardEstimation
) {
    companion object
}

val ReturnsModel.Companion.default
    get() = ReturnsModel(
        RewardEstimation("", "", ""),
        RewardEstimation("", "", "")
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

@Deprecated("All ViewStates should be provided and created in staking type aware ViewModels")
sealed class StakeViewState<S>(
    private val stakeState: StakingState,
    protected val currentAssetFlow: Flow<Asset>,
    protected val stakingInteractor: StakingInteractor,
    protected val resourceManager: ResourceManager,
    protected val scope: CoroutineScope,
    protected val router: StakingRouter,
    protected val errorDisplayer: (Throwable) -> Unit,
    protected val summaryFlowProvider: suspend (StakingState) -> Flow<StakeSummary<S>>,
    protected val statusMessageProvider: (S) -> TitleAndMessage,
    private val availableManageActions: Set<ManageStakeAction>
) : StakingViewStateOld() {

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

    protected fun syncStakingRewards() {
        hashCode()
        scope.launch {
            val syncResult = stakingInteractor.syncStakingRewards(stakeState.chain.id, stakeState.rewardsAddress)

            syncResult.exceptionOrNull()?.let { errorDisplayer(it) }
        }
    }

    @ExperimentalCoroutinesApi
    protected open suspend fun summaryFlow(): Flow<StakeSummaryModel<S>> {
        return currentAssetFlow.flatMapLatest { asset ->
            summaryFlowProvider(stakeState).map { asset to it }
        }.map { (asset, summary) ->
            val token = asset.token
            val tokenType = token.configuration

            StakeSummaryModel(
                status = summary.status,
                totalStaked = summary.totalStaked.formatCryptoDetail(tokenType.symbolToShow),
                totalStakedFiat = token.fiatAmount(summary.totalStaked)?.formatFiat(token.fiatSymbol),
                totalRewards = summary.totalReward.formatCryptoDetail(tokenType.symbolToShow),
                totalRewardsFiat = token.fiatAmount(summary.totalReward)?.formatFiat(token.fiatSymbol),
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

@Deprecated("All ViewStates should be provided and created in staking type aware ViewModels")
class ValidatorViewState(
    validatorState: StakingState.Stash.Validator,
    currentAssetFlow: Flow<Asset>,
    stakingInteractor: StakingInteractor,
    relayChainScenarioInteractor: StakingRelayChainScenarioInteractor,
    resourceManager: ResourceManager,
    scope: CoroutineScope,
    router: StakingRouter,
    errorDisplayer: (Throwable) -> Unit
) : StakeViewState<ValidatorStatus>(
    validatorState, currentAssetFlow, stakingInteractor,
    resourceManager, scope, router, errorDisplayer,
    summaryFlowProvider = { relayChainScenarioInteractor.observeValidatorSummary(validatorState).shareIn(scope, SharingStarted.Eagerly, replay = 1) },
    statusMessageProvider = { getValidatorStatusTitleAndMessage(resourceManager, it) },
    availableManageActions = ManageStakeAction.values().toSet() - ManageStakeAction.VALIDATORS
) {
    init {
        syncStakingRewards()
    }
}

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

@Deprecated("All ViewStates should be provided and created in staking type aware ViewModels")
class StashNoneViewState(
    stashState: StakingState.Stash.None,
    currentAssetFlow: Flow<Asset>,
    stakingInteractor: StakingInteractor,
    relayChainScenarioInteractor: StakingRelayChainScenarioInteractor,
    resourceManager: ResourceManager,
    scope: CoroutineScope,
    router: StakingRouter,
    errorDisplayer: (Throwable) -> Unit
) : StakeViewState<StashNoneStatus>(
    stashState, currentAssetFlow, stakingInteractor,
    resourceManager, scope, router, errorDisplayer,
    summaryFlowProvider = { relayChainScenarioInteractor.observeStashSummary(stashState).shareIn(scope, SharingStarted.Eagerly, replay = 1) },
    statusMessageProvider = { getStashStatusTitleAndMessage(resourceManager, it) },
    availableManageActions = ManageStakeAction.values().toSet() - ManageStakeAction.PAYOUTS
) {
    init {
        syncStakingRewards()
    }
}

@Deprecated("All ViewStates should be provided and created in staking type aware ViewModels")
private fun getStashStatusTitleAndMessage(
    resourceManager: ResourceManager,
    status: StashNoneStatus
): Pair<String, String> {
    val (titleRes, messageRes) = when (status) {
        StashNoneStatus.INACTIVE -> R.string.staking_nominator_status_alert_inactive_title to R.string.staking_bonded_inactive
    }

    return resourceManager.getString(titleRes) to resourceManager.getString(messageRes)
}

@Deprecated("All ViewStates should be provided and created in staking type aware ViewModels")
open class NominatorViewState(
    nominatorState: StakingState.Stash.Nominator,
    currentAssetFlow: Flow<Asset>,
    stakingInteractor: StakingInteractor,
    relayChainScenarioInteractor: StakingRelayChainScenarioInteractor,
    resourceManager: ResourceManager,
    scope: CoroutineScope,
    router: StakingRouter,
    errorDisplayer: (Throwable) -> Unit
) : StakeViewState<NominatorStatus>(
    nominatorState, currentAssetFlow, stakingInteractor,
    resourceManager, scope, router, errorDisplayer,
    summaryFlowProvider = { relayChainScenarioInteractor.observeNominatorSummary(nominatorState).shareIn(scope, SharingStarted.Eagerly, replay = 1) },
    statusMessageProvider = { getNominatorStatusTitleAndMessage(resourceManager, it) },
    availableManageActions = ManageStakeAction.values().toSet()
) {
    init {
        syncStakingRewards()
    }
}

@Deprecated("All ViewStates should be provided and created in staking type aware ViewModels")
class SoraNominatorViewState(
    private val nominatorState: StakingState.Stash.Nominator,
    currentAssetFlow: Flow<Asset>,
    stakingInteractor: StakingInteractor,
    relayChainScenarioInteractor: StakingRelayChainScenarioInteractor,
    private val soraStakingRewardsScenario: SoraStakingRewardsScenario,
    resourceManager: ResourceManager,
    scope: CoroutineScope,
    router: StakingRouter,
    errorDisplayer: (Throwable) -> Unit
) : NominatorViewState(nominatorState, currentAssetFlow, stakingInteractor, relayChainScenarioInteractor, resourceManager, scope, router, errorDisplayer) {
    @ExperimentalCoroutinesApi
    override suspend fun summaryFlow(): Flow<StakeSummaryModel<NominatorStatus>> {
        return currentAssetFlow.flatMapLatest { asset ->
            summaryFlowProvider(nominatorState).map { asset to it }
        }.map { (asset, summary) ->
            val token = asset.token
            val rewardToken = soraStakingRewardsScenario.getRewardAsset()
            val tokenType = token.configuration

            StakeSummaryModel(
                status = summary.status,
                totalStaked = summary.totalStaked.formatCryptoDetail(tokenType.symbolToShow),
                totalStakedFiat = token.fiatAmount(summary.totalStaked)?.formatFiat(token.fiatSymbol),
                totalRewards = "N/A",
                totalRewardsFiat = rewardToken.fiatAmount(summary.totalReward)?.formatFiat(rewardToken.fiatSymbol),
                currentEraDisplay = resourceManager.getString(R.string.staking_era_title, summary.currentEra)
            )
        }
    }
}

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

private fun getDelegatorStatusTitleAndMessage(
    resourceManager: ResourceManager,
    status: DelegatorStatus
): Pair<String, String> { // todo fix
    val (titleRes, messageRes) = when (status) {
        is DelegatorStatus.Active -> R.string.staking_nominator_status_alert_active_title to R.string.staking_nominator_status_alert_active_message

        is DelegatorStatus.Waiting -> R.string.staking_nominator_status_waiting to R.string.staking_nominator_status_alert_waiting_message

        is DelegatorStatus.Inactive -> when (status.reason) {
            DelegatorStatus.Inactive.Reason.MIN_STAKE ->
                R.string.staking_nominator_status_alert_inactive_title to R.string.staking_nominator_status_alert_low_stake
            DelegatorStatus.Inactive.Reason.NO_ACTIVE_VALIDATOR ->
                R.string.staking_nominator_status_alert_inactive_title to R.string.staking_nominator_status_alert_no_validators
        }
    }

    return resourceManager.getString(titleRes) to resourceManager.getString(messageRes)
}

@Deprecated("All ViewStates should be provided and created in staking type aware ViewModels")
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
) : StakingViewStateOld(), Validatable by validationExecutor {

    protected val currentSetupProgress by lazy { setupStakingSharedState.get<SetupStakingProcess.Initial>() }

    val enteredAmountFlow = MutableStateFlow("")

    protected val parsedAmountFlow =
        enteredAmountFlow.mapNotNull { it.toBigDecimalOrNull() ?: BigDecimal.ZERO }.stateIn(scope, SharingStarted.Eagerly, BigDecimal.ZERO)

    protected abstract val rewardCalculator: Deferred<RewardCalculator>

    abstract val returns: Flow<ReturnsModel>

    abstract fun infoActionClicked()
    abstract fun nextClicked()

    protected val _showRewardEstimationEvent = MutableLiveData<Event<StakingRewardEstimationBottomSheet.Payload>>()
    val showRewardEstimationEvent: LiveData<Event<StakingRewardEstimationBottomSheet.Payload>> = _showRewardEstimationEvent

    protected suspend fun rewardCalculator(): RewardCalculator {
        return rewardCalculator.await()
    }

    val assetLiveData = currentAssetFlow.map { mapAssetToAssetModel(it, resourceManager) }.asLiveData(scope)

    val amountFiat = parsedAmountFlow.combine(currentAssetFlow) { amount, asset -> asset.token.fiatAmount(amount)?.formatFiat(asset.token.fiatSymbol) }
        .asLiveData(scope)

    init {
        scope.launch {
            setupStakingSharedState.setupStakingProcess.filterIsInstance<SetupStakingProcess.Initial>().collect {
                enteredAmountFlow.value = it.defaultAmount.toString()
            }
        }
    }
}

@Deprecated("All ViewStates should be provided and created in staking type aware ViewModels")
open class RelaychainWelcomeViewState(
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
    val chainId = currentAssetFlow.filter { it.token.configuration.staking == CoreAsset.StakingType.RELAYCHAIN }.map { it.token.configuration.chainId }

    override val rewardCalculator = scope.async { rewardCalculatorFactory.create(currentAssetFlow.first().token.configuration) }

    override val returns: Flow<ReturnsModel> = currentAssetFlow.combine(parsedAmountFlow) { asset, amount ->
        val chainId = asset.token.configuration.chainId
        val monthly = rewardCalculator().calculateReturns(amount, PERIOD_MONTH, true, chainId)
        val yearly = rewardCalculator().calculateReturns(amount, PERIOD_YEAR, true, chainId)

        val monthlyEstimation = mapPeriodReturnsToRewardEstimation(monthly, asset.token, resourceManager)
        val yearlyEstimation = mapPeriodReturnsToRewardEstimation(yearly, asset.token, resourceManager)

        ReturnsModel(monthlyEstimation, yearlyEstimation)
    }.cancellable().shareIn(scope, SharingStarted.Eagerly, replay = 1)

    override fun infoActionClicked() {
        scope.launch {
            val rewardCalculator = rewardCalculator()

            val chainId = chainId.first()
            val maxAPY = rewardCalculator.calculateMaxAPY(chainId)
            val avgAPY = rewardCalculator.calculateAvgAPY()

            val payload = StakingRewardEstimationBottomSheet.Payload(
                maxAPY.formatAsPercentage(),
                avgAPY.formatAsPercentage(),
                R.string.staking_reward_info_max,
                R.string.staking_reward_info_avg
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
                validationFailureTransformer = { welcomeStakingValidationFailure(it, resourceManager) }
            ) {
                setupStakingSharedState.set(currentSetupProgress.fullFlow(SetupStakingProcess.SetupStep.Stash(amount)))

                router.openSetupStaking()
            }
        }
    }
}

@Deprecated("All ViewStates should be provided and created in staking type aware ViewModels")
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
    val chainId = currentAssetFlow.map { it.token.configuration.chainId }

    override val rewardCalculator = scope.async { rewardCalculatorFactory.createSubquery() }

    override val returns: Flow<ReturnsModel> = currentAssetFlow.combine(parsedAmountFlow) { asset, amount ->
        val chainId = asset.token.configuration.chainId
        val monthly = rewardCalculator().calculateReturns(amount, PERIOD_MONTH, true, chainId)
        val yearly = rewardCalculator().calculateReturns(amount, PERIOD_YEAR, true, chainId)

        val monthlyEstimation = mapPeriodReturnsToRewardEstimation(monthly, asset.token, resourceManager)
        val yearlyEstimation = mapPeriodReturnsToRewardEstimation(yearly, asset.token, resourceManager)

        ReturnsModel(monthlyEstimation, yearlyEstimation)
    }.distinctUntilChanged().cancellable().shareIn(scope, SharingStarted.Eagerly, replay = 1)

    override fun infoActionClicked() {
        scope.launch {
            val rewardCalculator = rewardCalculator()

            val chainId = chainId.first()
            val maxAPY = rewardCalculator.calculateMaxAPY(chainId)
            val avgAPY = rewardCalculator.calculateAvgAPY()

            val payload = StakingRewardEstimationBottomSheet.Payload(
                maxAPY.formatAsPercentage(),
                avgAPY.formatAsPercentage(),
                R.string.staking_reward_info_apr_max,
                R.string.staking_reward_info_apr_avg
            )

            _showRewardEstimationEvent.value = Event(payload)
        }
    }

    override fun nextClicked() {
        scope.launch {
            val amount = parsedAmountFlow.first()
            setupStakingSharedState.set(currentSetupProgress.fullFlow(SetupStakingProcess.SetupStep.Parachain(amount)))
            router.openSetupStaking()
        }
    }
}

@Deprecated("All ViewStates should be provided and created in staking type aware ViewModels")
class StakingPoolWelcomeViewState(
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
    val chainId = currentAssetFlow.filter { it.token.configuration.staking == CoreAsset.StakingType.RELAYCHAIN }.map { it.token.configuration.chainId }

    override val rewardCalculator = scope.async { rewardCalculatorFactory.create(currentAssetFlow.first().token.configuration) }

    override val returns: Flow<ReturnsModel> = currentAssetFlow.combine(parsedAmountFlow) { asset, amount ->
        val chainId = asset.token.configuration.chainId
        val monthly = rewardCalculator().calculateReturns(amount, PERIOD_MONTH, true, chainId)
        val yearly = rewardCalculator().calculateReturns(amount, PERIOD_YEAR, true, chainId)

        val monthlyEstimation = mapPeriodReturnsToRewardEstimation(monthly, asset.token, resourceManager)
        val yearlyEstimation = mapPeriodReturnsToRewardEstimation(yearly, asset.token, resourceManager)

        ReturnsModel(monthlyEstimation, yearlyEstimation)
    }.cancellable().shareIn(scope, SharingStarted.Eagerly, replay = 1)

    override fun infoActionClicked() {
        scope.launch {
            val rewardCalculator = rewardCalculator()

            val chainId = chainId.first()
            val maxAPY = rewardCalculator.calculateMaxAPY(chainId)
            val avgAPY = rewardCalculator.calculateAvgAPY()

            val payload = StakingRewardEstimationBottomSheet.Payload(
                maxAPY.formatAsPercentage(),
                avgAPY.formatAsPercentage(),
                R.string.staking_reward_info_max,
                R.string.staking_reward_info_avg
            )

            _showRewardEstimationEvent.value = Event(payload)
        }
    }

    override fun nextClicked() {
        scope.launch {
            router.openStakingPoolWelcome()
        }
    }
}

class SoraWelcomeViewState(
    setupStakingSharedState: SetupStakingSharedState,
    rewardCalculatorFactory: RewardCalculatorFactory,
    resourceManager: ResourceManager,
    router: StakingRouter,
    currentAssetFlow: Flow<Asset>,
    scope: CoroutineScope,
    errorDisplayer: (String) -> Unit,
    validationSystem: WelcomeStakingValidationSystem,
    validationExecutor: ValidationExecutor,
    private val soraStakingRewardsScenario: SoraStakingRewardsScenario
) : RelaychainWelcomeViewState(
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
    override val returns: Flow<ReturnsModel> = currentAssetFlow.combine(parsedAmountFlow) { asset, amount ->
        val rewardAsset = soraStakingRewardsScenario.getRewardAsset()

        val chainId = asset.token.configuration.chainId
        val monthly = rewardCalculator().calculateReturns(amount, PERIOD_MONTH, true, chainId)
        val yearly = rewardCalculator().calculateReturns(amount, PERIOD_YEAR, true, chainId)

        val monthlyEstimation = mapPeriodReturnsToRewardEstimation(monthly, rewardAsset, resourceManager)
        val yearlyEstimation = mapPeriodReturnsToRewardEstimation(yearly, rewardAsset, resourceManager)

        ReturnsModel(monthlyEstimation, yearlyEstimation)
    }.cancellable().shareIn(scope, SharingStarted.Eagerly, replay = 1)
}

@Deprecated("All ViewStates should be provided and created in staking type aware ViewModels")
class DelegatorViewState(
    private val delegatorState: StakingState.Parachain.Delegator,
    val welcomeViewState: ParachainWelcomeViewState,
    currentAssetFlow: Flow<Asset>,
    stakingInteractor: StakingInteractor,
    private val parachainScenarioInteractor: StakingParachainScenarioInteractor,
    private val rewardCalculatorFactory: RewardCalculatorFactory,
    resourceManager: ResourceManager,
    scope: CoroutineScope,
    router: StakingRouter,
    errorDisplayer: (Throwable) -> Unit
) : StakeViewState<DelegatorStatus>(
    delegatorState, currentAssetFlow, stakingInteractor,
    resourceManager, scope, router, errorDisplayer,
    summaryFlowProvider = { emptyFlow() },
    statusMessageProvider = { getDelegatorStatusTitleAndMessage(resourceManager, it) },
    availableManageActions = ManageStakeAction.values().toSet()
) {

    val delegations = currentAssetFlow.filter { it.token.configuration.staking == CoreAsset.StakingType.PARACHAIN }.map { asset ->
        val chainId = asset.token.configuration.chainId
        val collatorsIds = delegatorState.delegations.map { it.collatorId }
        val chain = stakingInteractor.getSelectedChain()

        val collatorsNamesMap = parachainScenarioInteractor.getIdentities(collatorsIds).let { map ->
            map.map {
                chain.accountFromMapKey(it.key) to it.value
            }
        }.toMap()
        val candidateInfos = parachainScenarioInteractor.getCandidateInfos(chainId, collatorsIds)

        val readyToUnlockCollatorIds = parachainScenarioInteractor.getCollatorIdsWithReadyToUnlockingTokens(collatorsIds, delegatorState.accountId)
        val apyMap = rewardCalculatorFactory.createSubquery().getApy(delegatorState.delegations.map { it.collatorId })

        delegatorState.delegations.mapNotNull { collator ->
            val collatorIdHex = collator.collatorId.toHexString(false)
            val identity = collatorsNamesMap[collatorIdHex]
            val candidateInfo = candidateInfos[collatorIdHex] ?: return@mapNotNull null

            val staked = asset.token.amountFromPlanks(collator.delegatedAmountInPlanks)
            val rewarded = asset.token.amountFromPlanks(collator.rewardedAmountInPlanks)
            val rewardApy = apyMap[collatorIdHex].orZero()

            val currentBlock = stakingInteractor.currentBlockNumber()
            val currentRound = parachainScenarioInteractor.getCurrentRound(chainId).getOrNull() ?: return@mapNotNull null
            val hoursInRound = parachainScenarioInteractor.hoursInRound[chainId] ?: 0
            val millisecondsTillTheEndOfRound = calculateTimeTillTheEndOfRound(currentRound, currentBlock, hoursInRound)

            val leaveCandidatesDelayInRounds = parachainScenarioInteractor.getLeaveCandidatesDelay().getOrNull() ?: return@mapNotNull null
            val roundWhenCandidateWillLeave = (candidateInfo.status as? CandidateInfoStatus.LEAVING)?.leavingBlock?.let { it + leaveCandidatesDelayInRounds }
            val roundsTillCandidateWillLeave = roundWhenCandidateWillLeave?.let { it - currentRound.current.toLong() }
            val hoursTillCandidateWillLeave = roundsTillCandidateWillLeave?.times(hoursInRound)
            val millisecondsTillCandidateWillLeave = hoursTillCandidateWillLeave?.times(60)?.times(60)?.times(1000)

            val isReadyToUnlock = readyToUnlockCollatorIds.any { it.contentEquals(collator.collatorId) }

            CollatorDelegationModel(
                collatorId = collator.collatorId,
                collatorAddress = collator.collatorId.toHexString(true),
                collatorName = identity?.display ?: collatorIdHex,
                staked = staked.formatCryptoDetail(asset.token.configuration.symbolToShow),
                stakedFiat = staked.applyFiatRate(asset.fiatAmount)?.formatFiat(asset.token.fiatSymbol),
                rewardApy = rewardApy.formatAsPercentage(),
                rewardedFiat = rewarded.applyFiatRate(asset.fiatAmount)?.formatFiat(asset.token.fiatSymbol),
                status = candidateInfo.toModelStatus(millisecondsTillTheEndOfRound, millisecondsTillCandidateWillLeave, isReadyToUnlock),
                candidateInfo
            )
        }
    }.withLoading().cancellable()

    private fun calculateTimeTillTheEndOfRound(currentRound: Round, currentBlock: BigInteger, hoursInRound: Int): Long {
        val currentRoundFinishAtBlock = currentRound.first + currentRound.length
        val blocksTillTheEndOfRound = currentRoundFinishAtBlock - currentBlock
        val secondsInRound = (hoursInRound * 60 * 60).toBigDecimal()
        val secondsInBlock = secondsInRound / currentRound.length.toBigDecimal()
        val secondsTillTheEndOfRound = blocksTillTheEndOfRound.toBigDecimal() * secondsInBlock
        val millisecondsTillTheEndOfRound = secondsTillTheEndOfRound * BigDecimal(1000)
        return millisecondsTillTheEndOfRound.toLong()
    }

    fun openCollatorInfo(model: CollatorDelegationModel) {
        scope.launch {
            val identity = parachainScenarioInteractor.getIdentity(model.collatorId)
            val apy = rewardCalculatorFactory.createSubquery().getApyFor(model.collatorId)
            router.openCollatorDetails(
                CollatorDetailsParcelModel(
                    model.collatorId.toHexString(true),
                    CollatorStakeParcelModel(
                        status = model.collator.status,
                        selfBonded = model.collator.bond,
                        delegations = model.collator.delegationCount.toInt(),
                        totalStake = model.collator.totalCounted,
                        minBond = model.collator.lowestTopDelegationAmount,
                        estimatedRewards = apy
                    ),
                    identity?.let {
                        IdentityParcelModel(
                            display = it.display,
                            legal = it.legal,
                            web = it.web,
                            riot = it.riot,
                            email = it.email,
                            pgpFingerprint = it.pgpFingerprint,
                            image = it.image,
                            twitter = it.twitter
                        )
                    },
                    model.collator.request.orEmpty()
                )
            )
        }
    }

    @Suppress("ArrayInDataClass")
    data class CollatorDelegationModel(
        val collatorId: AccountId,
        val collatorAddress: String,
        val collatorName: String,
        val staked: String,
        val stakedFiat: String?,
        val rewardApy: String,
        val rewardedFiat: String?,
        val status: Status,
        val collator: CandidateInfo
    ) {
        sealed class Status {
            class Active(val nextRoundTimeLeft: Long) : Status()
            object Inactive : Status()
            class Leaving(val collatorLeaveTimeLeft: Long?) : Status()
            object Idle : Status()
            object ReadyToUnlock : Status()
        }
    }
}

fun CandidateInfo.toModelStatus(
    millisecondsTillTheEndOfRound: Long,
    millisecondsTillCandidateWillLeave: Long?,
    isReadyToUnlock: Boolean
): DelegatorViewState.CollatorDelegationModel.Status {
    return when {
        isReadyToUnlock -> DelegatorViewState.CollatorDelegationModel.Status.ReadyToUnlock
        status == CandidateInfoStatus.ACTIVE -> DelegatorViewState.CollatorDelegationModel.Status.Active(millisecondsTillTheEndOfRound)
        status == CandidateInfoStatus.EMPTY -> DelegatorViewState.CollatorDelegationModel.Status.Inactive
        status is CandidateInfoStatus.LEAVING -> DelegatorViewState.CollatorDelegationModel.Status.Leaving(millisecondsTillCandidateWillLeave)
        status == CandidateInfoStatus.IDLE -> DelegatorViewState.CollatorDelegationModel.Status.Idle
        else -> DelegatorViewState.CollatorDelegationModel.Status.Idle
    }
}

// todo stub
object Pool : StakingViewStateOld()
