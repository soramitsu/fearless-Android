package jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios

import java.math.BigDecimal
import java.math.RoundingMode
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.domain.model.StoryGroup
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.formatAsPercentage
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.common.utils.nullIfEmpty
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.withLoading
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.data.StakingType
import jp.co.soramitsu.staking.api.data.SyntheticStakingType
import jp.co.soramitsu.staking.api.data.syntheticStakingType
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.data.repository.datasource.StakingStoriesDataSource
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.alerts.Alert
import jp.co.soramitsu.staking.impl.domain.alerts.AlertsInteractor
import jp.co.soramitsu.staking.impl.domain.model.NetworkInfo
import jp.co.soramitsu.staking.impl.domain.model.NominatorStatus
import jp.co.soramitsu.staking.impl.domain.model.StakeSummary
import jp.co.soramitsu.staking.impl.domain.model.StashNoneStatus
import jp.co.soramitsu.staking.impl.domain.model.ValidatorStatus
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.staking.impl.domain.rewards.SoraStakingRewardsScenario
import jp.co.soramitsu.staking.impl.domain.validations.balance.BalanceAccountRequiredValidation
import jp.co.soramitsu.staking.impl.domain.validations.balance.ManageStakingValidationFailure
import jp.co.soramitsu.staking.impl.domain.validations.balance.ManageStakingValidationPayload
import jp.co.soramitsu.staking.impl.domain.validations.welcome.WelcomeStakingMaxNominatorsValidation
import jp.co.soramitsu.staking.impl.domain.validations.welcome.WelcomeStakingValidationFailure
import jp.co.soramitsu.staking.impl.domain.validations.welcome.WelcomeStakingValidationPayload
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.staking.impl.presentation.mappers.mapPeriodReturnsToRewardEstimation
import jp.co.soramitsu.staking.impl.presentation.staking.alerts.model.AlertModel
import jp.co.soramitsu.staking.impl.presentation.staking.main.ManageStakeAction
import jp.co.soramitsu.staking.impl.presentation.staking.main.ReturnsModel
import jp.co.soramitsu.staking.impl.presentation.staking.main.StakingRewardEstimationBottomSheet
import jp.co.soramitsu.staking.impl.presentation.staking.main.StakingViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.StakingViewStateOld
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.EstimatedEarningsViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.StakeInfoViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.StakeStatus
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.default
import jp.co.soramitsu.staking.impl.presentation.staking.main.default
import jp.co.soramitsu.staking.impl.presentation.staking.main.di.StakingViewStateFactory
import jp.co.soramitsu.staking.impl.presentation.staking.main.model.StakingNetworkInfoModel
import jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios.StakingScenarioViewModel.Companion.WAITING_ICON
import jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios.StakingScenarioViewModel.Companion.WARNING_ICON
import jp.co.soramitsu.staking.impl.presentation.staking.main.welcomeStakingValidationFailure
import jp.co.soramitsu.staking.impl.scenarios.relaychain.HOURS_IN_DAY
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch

class StakingRelaychainScenarioViewModel(
    private val stakingInteractor: StakingInteractor,
    private val scenarioInteractor: StakingRelayChainScenarioInteractor,
    private val resourceManager: ResourceManager,
    private val baseViewModel: BaseStakingViewModel,
    private val alertsInteractor: AlertsInteractor,
    private val stakingViewStateFactory: StakingViewStateFactory,
    private val storiesDataSource: StakingStoriesDataSource,
    stakingSharedState: StakingSharedState,
    private val rewardCalculatorFactory: RewardCalculatorFactory,
    private val setupStakingSharedState: SetupStakingSharedState,
    private val router: StakingRouter,
    private val validationExecutor: ValidationExecutor,
    private val soraStakingRewardsScenario: SoraStakingRewardsScenario
) : StakingScenarioViewModel {

    companion object {
        const val STAKE_EXTRA_MULTIPLIER = 1.15 // allow to be not at the bottom of reward list and not be excluded soon
    }

    override val enteredAmountFlow: MutableStateFlow<BigDecimal?> = MutableStateFlow(BigDecimal.TEN)

    private val currentAssetFlow = stakingInteractor.currentAssetFlow()
    private val defaultAmountInputState = AmountInputViewState(
        tokenName = "...",
        tokenImage = "",
        totalBalance = resourceManager.getString(R.string.common_balance_format, "..."),
        fiatAmount = "",
        tokenAmount = BigDecimal.TEN
    )

    private val amountInputViewState = combine(enteredAmountFlow, currentAssetFlow) { amount, asset ->
        val tokenBalance = asset.transferable.formatCrypto(asset.token.configuration.symbol)
        val fiatAmount = amount?.applyFiatRate(asset.token.fiatRate)?.formatFiat(asset.token.fiatSymbol)

        AmountInputViewState(
            tokenName = asset.token.configuration.symbol,
            tokenImage = asset.token.configuration.iconUrl,
            totalBalance = resourceManager.getString(R.string.common_balance_format, tokenBalance),
            fiatAmount = fiatAmount,
            tokenAmount = amount.orZero()
        )
    }.stateIn(baseViewModel.stakingStateScope, SharingStarted.WhileSubscribed(5_000), defaultAmountInputState)

    private val estimatedEarnings = combine(enteredAmountFlow, currentAssetFlow) { amount, asset ->
        calculateReturns(asset, amount.orZero())
    }.stateIn(baseViewModel.stakingStateScope, SharingStarted.WhileSubscribed(5_000), ReturnsModel.default)

    private val welcomeStakingValidationSystem = ValidationSystem(
        CompositeValidation(
            validations = listOf(
                WelcomeStakingMaxNominatorsValidation(
                    stakingScenarioInteractor = scenarioInteractor,
                    errorProducer = { WelcomeStakingValidationFailure.MAX_NOMINATORS_REACHED },
                    isAlreadyNominating = { false },
                    sharedState = stakingSharedState
                )
            )
        )
    )

    override val stakingStateFlow: Flow<StakingState> =
        scenarioInteractor.stakingStateFlow().shareIn(baseViewModel.stakingStateScope, SharingStarted.Eagerly, 1)

    init {
        baseViewModel.enteredAmountEvent.onEach { event ->
            enteredAmountFlow.value = event.peekContent().setScale(5, RoundingMode.HALF_DOWN)
        }.launchIn(baseViewModel.stakingStateScope)
    }

    override val stakingViewStateFlowOld: Flow<StakingViewStateOld> =
        stakingStateFlow.distinctUntilChanged().map { stakingState ->
            when (stakingState) {
                is StakingState.Stash.Nominator -> stakingViewStateFactory.createNominatorViewState(
                    stakingState,
                    stakingInteractor.currentAssetFlow(),
                    baseViewModel.stakingStateScope,
                    baseViewModel::showError
                )

                is StakingState.Stash.None -> stakingViewStateFactory.createStashNoneState(
                    stakingInteractor.currentAssetFlow(),
                    stakingState,
                    baseViewModel.stakingStateScope,
                    baseViewModel::showError
                )

                is StakingState.NonStash -> stakingViewStateFactory.createRelayChainWelcomeViewState(
                    stakingInteractor.currentAssetFlow(),
                    baseViewModel.stakingStateScope,
                    welcomeStakingValidationSystem = welcomeStakingValidationSystem,
                    baseViewModel::showError
                )

                is StakingState.Stash.Validator -> stakingViewStateFactory.createValidatorViewState(
                    stakingState,
                    stakingInteractor.currentAssetFlow(),
                    baseViewModel.stakingStateScope,
                    baseViewModel::showError
                )

                else -> error("Wrong state")
            }
        }.shareIn(baseViewModel.stakingStateScope, SharingStarted.Lazily, 1)

    @Deprecated("Don't use this method, use the getStakingViewStateFlow instead")
    override suspend fun getStakingViewStateFlowOld(): Flow<StakingViewStateOld> {
        return stakingStateFlow.distinctUntilChanged().map { stakingState ->
            when (stakingState) {
                is StakingState.Stash.Nominator -> stakingViewStateFactory.createNominatorViewState(
                    stakingState,
                    stakingInteractor.currentAssetFlow(),
                    baseViewModel.stakingStateScope,
                    baseViewModel::showError
                )

                is StakingState.Stash.None -> stakingViewStateFactory.createStashNoneState(
                    stakingInteractor.currentAssetFlow(),
                    stakingState,
                    baseViewModel.stakingStateScope,
                    baseViewModel::showError
                )

                is StakingState.NonStash -> stakingViewStateFactory.createRelayChainWelcomeViewState(
                    stakingInteractor.currentAssetFlow(),
                    baseViewModel.stakingStateScope,
                    welcomeStakingValidationSystem = welcomeStakingValidationSystem,
                    baseViewModel::showError
                )

                is StakingState.Stash.Validator -> stakingViewStateFactory.createValidatorViewState(
                    stakingState,
                    stakingInteractor.currentAssetFlow(),
                    baseViewModel.stakingStateScope,
                    baseViewModel::showError
                )

                else -> error("Wrong state")
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun getStakingViewStateFlow(): Flow<StakingViewState> {
        return stakingStateFlow.distinctUntilChanged().flatMapLatest { stakingState ->
            when (stakingState) {
                is StakingState.NonStash -> welcomeStateFlow()
                is StakingState.Stash.Nominator -> relayStakeStateFlow(
                    stakingState,
                    RelayStakeKind.NOMINATOR,
                    scenarioInteractor.observeNominatorSummary(stakingState),
                    statusMapper = { summary, era -> mapNominatorStakeStatus(summary.status, era) },
                    statusInfoMapper = ::nominatorStatusInfo
                )
                is StakingState.Stash.Validator -> relayStakeStateFlow(
                    stakingState,
                    RelayStakeKind.VALIDATOR,
                    scenarioInteractor.observeValidatorSummary(stakingState),
                    statusMapper = { summary, era -> mapValidatorStakeStatus(summary.status, era) },
                    statusInfoMapper = ::validatorStatusInfo
                )
                is StakingState.Stash.None -> relayStakeStateFlow(
                    stakingState,
                    RelayStakeKind.STASH_NONE,
                    scenarioInteractor.observeStashSummary(stakingState),
                    statusMapper = { summary, era -> mapStashNoneStakeStatus(summary.status, era) },
                    statusInfoMapper = ::stashNoneStatusInfo
                )
                else -> error("StakingRelaychainScenarioViewModel received ${stakingState::class.simpleName}")
            }
        }
    }

    private fun welcomeStateFlow(): Flow<StakingViewState> {
        return combine(amountInputViewState, estimatedEarnings) { inputState, returns ->
            StakingViewState.RelayChain.Welcome(returns.toEstimatedEarnings(inputState))
        }
    }

    private fun <S> relayStakeStateFlow(
        stakingState: StakingState.Stash,
        stakeKind: RelayStakeKind,
        summaryFlow: Flow<StakeSummary<S>>,
        statusMapper: (StakeSummary<S>, String) -> StakeStatus,
        statusInfoMapper: (S) -> StakingViewState.StatusInfo
    ): Flow<StakingViewState> {
        return combine(currentAssetFlow, summaryFlow) { asset, summary ->
            val eraDisplay = resourceManager.getString(R.string.staking_era_title, summary.currentEra)
            val rewardToken = if (asset.token.configuration.syntheticStakingType() == SyntheticStakingType.SORA) {
                soraStakingRewardsScenario.getRewardAsset()
            } else {
                asset.token
            }
            val defaultState = StakeInfoViewState.RelayChainStakeInfoViewState.default(resourceManager)
            val staked = summary.totalStaked.orZero()

            val viewState = defaultState.copy(
                staked = defaultState.staked.copy(
                    value = staked.formatCryptoDetail(asset.token.configuration.symbol),
                    additionalValue = asset.token.fiatAmount(staked)?.formatFiat(asset.token.fiatSymbol)
                ),
                rewarded = defaultState.rewarded.copy(
                    value = summary.totalReward.formatCryptoDetail(rewardToken.configuration.symbol),
                    additionalValue = rewardToken.fiatAmount(summary.totalReward)?.formatFiat(rewardToken.fiatSymbol)
                ),
                status = statusMapper(summary, eraDisplay)
            )

            StakingViewState.RelayChain.Stake(
                stakeInfoViewState = viewState,
                manageActions = relayManageActions(stakeKind, stakingState.supportsPayouts()),
                statusInfo = statusInfoMapper(summary.status)
            )
        }.onStart {
            baseViewModel.stakingStateScope.launch {
                stakingInteractor.syncStakingRewards(stakingState.chain.id, stakingState.rewardsAddress)
                    .exceptionOrNull()
                    ?.let(baseViewModel::showError)
            }
        }
    }

    private suspend fun calculateReturns(asset: jp.co.soramitsu.wallet.impl.domain.model.Asset, amount: BigDecimal): ReturnsModel {
        val calculator = rewardCalculatorFactory.create(asset.token.configuration)
        val chainId = asset.token.configuration.chainId
        val monthly = calculator.calculateReturns(amount, PERIOD_MONTH, isCompound = true, chainId = chainId)
        val yearly = calculator.calculateReturns(amount, PERIOD_YEAR, isCompound = true, chainId = chainId)
        val rewardToken = if (asset.token.configuration.syntheticStakingType() == SyntheticStakingType.SORA) {
            soraStakingRewardsScenario.getRewardAsset()
        } else {
            asset.token
        }

        return ReturnsModel(
            mapPeriodReturnsToRewardEstimation(monthly, rewardToken, resourceManager),
            mapPeriodReturnsToRewardEstimation(yearly, rewardToken, resourceManager)
        )
    }

    override suspend fun rewardEstimationPayload(): StakingRewardEstimationBottomSheet.Payload {
        val asset = currentAssetFlow.first()
        val calculator = rewardCalculatorFactory.create(asset.token.configuration)

        return StakingRewardEstimationBottomSheet.Payload(
            calculator.calculateMaxAPY(asset.token.configuration.chainId).formatAsPercentage(),
            calculator.calculateAvgAPY().formatAsPercentage(),
            R.string.staking_reward_info_max,
            R.string.staking_reward_info_avg
        )
    }

    override fun startStaking() {
        baseViewModel.stakingStateScope.launch {
            val amount = enteredAmountFlow.first().orZero()
            validationExecutor.requireValid(
                validationSystem = welcomeStakingValidationSystem,
                payload = WelcomeStakingValidationPayload(),
                errorDisplayer = { it.message?.let(baseViewModel::showError) },
                validationFailureTransformer = { welcomeStakingValidationFailure(it, resourceManager) }
            ) {
                val initial = setupStakingSharedState.getOrNull<SetupStakingProcess.Initial>()
                    ?.takeIf { it.stakingType == StakingType.RELAYCHAIN }
                    ?: SetupStakingProcess.Initial(StakingType.RELAYCHAIN)
                setupStakingSharedState.set(initial.fullFlow(SetupStakingProcess.SetupStep.Stash(amount)))
                router.openSetupStaking()
            }
        }
    }

    private fun ReturnsModel.toEstimatedEarnings(inputState: AmountInputViewState): EstimatedEarningsViewState {
        val monthly = monthly.gain.nullIfEmpty()?.let {
            TitleValueViewState(it, monthly.amount.nullIfEmpty(), monthly.fiatAmount)
        }
        val yearly = yearly.gain.nullIfEmpty()?.let {
            TitleValueViewState(it, yearly.amount.nullIfEmpty(), yearly.fiatAmount)
        }

        return EstimatedEarningsViewState(monthly, yearly, inputState)
    }

    private fun nominatorStatusInfo(status: NominatorStatus): StakingViewState.StatusInfo {
        val (titleRes, messageRes) = when (status) {
            NominatorStatus.Active -> R.string.staking_nominator_status_alert_active_title to R.string.staking_nominator_status_alert_active_message
            is NominatorStatus.Waiting -> R.string.staking_nominator_status_waiting to R.string.staking_nominator_status_alert_waiting_message
            is NominatorStatus.Inactive -> when (status.reason) {
                NominatorStatus.Inactive.Reason.MIN_STAKE ->
                    R.string.staking_nominator_status_alert_inactive_title to R.string.staking_nominator_status_alert_low_stake
                NominatorStatus.Inactive.Reason.NO_ACTIVE_VALIDATOR ->
                    R.string.staking_nominator_status_alert_inactive_title to R.string.staking_nominator_status_alert_no_validators
            }
        }
        return StakingViewState.StatusInfo(resourceManager.getString(titleRes), resourceManager.getString(messageRes))
    }

    private fun validatorStatusInfo(status: ValidatorStatus): StakingViewState.StatusInfo {
        val (titleRes, messageRes) = when (status) {
            ValidatorStatus.ACTIVE -> R.string.staking_nominator_status_alert_active_title to R.string.staking_nominator_status_alert_active_message
            ValidatorStatus.INACTIVE -> R.string.staking_nominator_status_alert_inactive_title to R.string.staking_nominator_status_alert_no_validators
        }
        return StakingViewState.StatusInfo(resourceManager.getString(titleRes), resourceManager.getString(messageRes))
    }

    private fun stashNoneStatusInfo(status: StashNoneStatus): StakingViewState.StatusInfo {
        val (titleRes, messageRes) = when (status) {
            StashNoneStatus.INACTIVE -> R.string.staking_nominator_status_alert_inactive_title to R.string.staking_bonded_inactive
        }
        return StakingViewState.StatusInfo(resourceManager.getString(titleRes), resourceManager.getString(messageRes))
    }

    override suspend fun networkInfo(): Flow<LoadingState<StakingNetworkInfoModel>> {
        return combine(
            scenarioInteractor.observeNetworkInfoState().map { it as NetworkInfo.RelayChain },
            stakingInteractor.currentAssetFlow()
        ) { networkInfo, asset ->

            val minStakeMultiplier: Double = if (networkInfo.shouldUseMinimumStakeMultiplier) {
                STAKE_EXTRA_MULTIPLIER // 15% increase
            } else {
                1.0
            }

            val minimumStake = asset.token.amountFromPlanks(networkInfo.minimumStake) * BigDecimal(minStakeMultiplier)
            val minimumStakeFormatted = minimumStake.formatCryptoDetail(asset.token.configuration.symbol)

            val minimumStakeFiat = asset.token.fiatAmount(minimumStake)?.formatFiat(asset.token.fiatSymbol)

            val lockupPeriod = if (networkInfo.lockupPeriodInHours > HOURS_IN_DAY) {
                val inDays = networkInfo.lockupPeriodInHours / HOURS_IN_DAY
                resourceManager.getQuantityString(R.plurals.common_days_format, inDays, inDays)
            } else {
                resourceManager.getQuantityString(R.plurals.common_hours_format, networkInfo.lockupPeriodInHours, networkInfo.lockupPeriodInHours)
            }
            val totalStake = asset.token.amountFromPlanks(networkInfo.totalStake)
            val totalStakeFormatted = totalStake.formatCryptoDetail(asset.token.configuration.symbol)

            val totalStakeFiat = asset.token.fiatAmount(totalStake)?.formatFiat(asset.token.fiatSymbol)

            StakingNetworkInfoModel.RelayChain(
                lockupPeriod,
                minimumStakeFormatted,
                minimumStakeFiat,
                totalStakeFormatted,
                totalStakeFiat,
                networkInfo.nominatorsCount.toString()
            )
        }.withLoading()
    }

    override suspend fun alerts(): Flow<LoadingState<List<AlertModel>>> {
        return stakingStateFlow.flatMapLatest {
            alertsInteractor.getAlertsFlow(it)
        }.mapList(::mapAlertToAlertModel).withLoading().inBackground()
    }

    private fun mapAlertToAlertModel(alert: Alert): AlertModel {
        return when (alert) {
            is Alert.ChangeValidators -> {
                AlertModel(
                    WARNING_ICON,
                    resourceManager.getString(R.string.staking_alert_change_validators),
                    resourceManager.getString(R.string.staking_nominator_status_alert_no_validators),
                    AlertModel.Type.CallToAction { baseViewModel.openCurrentValidators() }
                )
            }

            is Alert.AllValidatorsAreOversubscribed -> {
                AlertModel(
                    WARNING_ICON,
                    resourceManager.getString(R.string.staking_alert_change_validators),
                    resourceManager.getString(R.string.staking_your_oversubscribed_message),
                    AlertModel.Type.CallToAction { baseViewModel.openCurrentValidators() }
                )
            }

            is Alert.RedeemTokens -> {
                AlertModel(
                    WARNING_ICON,
                    resourceManager.getString(R.string.staking_alert_redeem_title),
                    formatAlertTokenAmount(alert.amount, alert.token),
                    AlertModel.Type.CallToAction { baseViewModel.redeemAlertClicked() }
                )
            }

            is Alert.BondMoreTokens -> {
                val existentialDepositDisplay = formatAlertTokenAmount(alert.minimalStake, alert.token)

                AlertModel(
                    WARNING_ICON,
                    resourceManager.getString(R.string.staking_alert_bond_more_title),
                    resourceManager.getString(R.string.staking_alert_bond_more_message, existentialDepositDisplay),
                    AlertModel.Type.CallToAction { baseViewModel.bondMoreAlertClicked() }
                )
            }

            is Alert.WaitingForNextEra -> AlertModel(
                WAITING_ICON,
                resourceManager.getString(R.string.staking_nominator_status_alert_waiting_message),
                resourceManager.getString(R.string.staking_alert_start_next_era_message),
                AlertModel.Type.Info
            )

            is Alert.SetValidators -> AlertModel(
                WARNING_ICON,
                resourceManager.getString(R.string.staking_set_validators_title),
                resourceManager.getString(R.string.staking_set_validators_message),
                AlertModel.Type.CallToAction { baseViewModel.openChangeValidators() }
            )

            else -> error("Wrong alert type")
        }
    }

    override fun stakingStoriesFlow(): Flow<List<StoryGroup.Staking>> {
        return storiesDataSource.getStoriesFlow()
    }

    override suspend fun getBondMoreValidationSystem(): ValidationSystem<ManageStakingValidationPayload, ManageStakingValidationFailure> {
        return ValidationSystem(
            CompositeValidation(
                validations = listOf(
                    BalanceAccountRequiredValidation(
                        scenarioInteractor,
                        accountAddressExtractor = { payload -> payload.stashState?.stashAddress },
                        errorProducer = ManageStakingValidationFailure::StashRequired
                    )
                )
            )
        )
    }

    override suspend fun getRedeemValidationSystem(): ValidationSystem<ManageStakingValidationPayload, ManageStakingValidationFailure> {
        return ValidationSystem(
            CompositeValidation(
                validations = listOf(
                    BalanceAccountRequiredValidation(
                        scenarioInteractor,
                        accountAddressExtractor = { payload -> payload.stashState?.controllerAddress },
                        errorProducer = ManageStakingValidationFailure::ControllerRequired
                    )
                )
            )
        )
    }
}

internal enum class RelayStakeKind {
    NOMINATOR,
    VALIDATOR,
    STASH_NONE
}

internal fun relayManageActions(
    stakeKind: RelayStakeKind,
    payoutsSupported: Boolean
): Set<ManageStakeAction> {
    val initialActions = when (stakeKind) {
        RelayStakeKind.NOMINATOR -> ManageStakeAction.entries.toSet()
        RelayStakeKind.VALIDATOR -> ManageStakeAction.entries.toSet() - ManageStakeAction.VALIDATORS
        RelayStakeKind.STASH_NONE -> ManageStakeAction.entries.toSet() - ManageStakeAction.PAYOUTS
    }

    return if (payoutsSupported) {
        initialActions + ManageStakeAction.PAYOUTS
    } else {
        initialActions - ManageStakeAction.PAYOUTS
    }
}

internal fun mapNominatorStakeStatus(status: NominatorStatus, eraDisplay: String): StakeStatus {
    return when (status) {
        NominatorStatus.Active -> StakeStatus.Active(eraDisplay)
        is NominatorStatus.Waiting -> StakeStatus.Waiting(status.timeLeft)
        is NominatorStatus.Inactive -> StakeStatus.Inactive(eraDisplay)
    }
}

internal fun mapValidatorStakeStatus(status: ValidatorStatus, eraDisplay: String): StakeStatus {
    return when (status) {
        ValidatorStatus.ACTIVE -> StakeStatus.Active(eraDisplay)
        ValidatorStatus.INACTIVE -> StakeStatus.Inactive(eraDisplay)
    }
}

internal fun mapStashNoneStakeStatus(status: StashNoneStatus, eraDisplay: String): StakeStatus {
    return when (status) {
        StashNoneStatus.INACTIVE -> StakeStatus.Inactive(eraDisplay)
    }
}

private fun StakingState.Stash.supportsPayouts(): Boolean {
    val supportedTypes = setOf(
        Chain.ExternalApi.Section.Type.SUBQUERY,
        Chain.ExternalApi.Section.Type.SUBSQUID,
        Chain.ExternalApi.Section.Type.SORA
    )
    return chain.externalApi?.staking?.type in supportedTypes
}
