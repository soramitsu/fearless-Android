package jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios

import java.math.BigDecimal
import java.math.BigInteger
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
import jp.co.soramitsu.common.utils.nullIfEmpty
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.withLoading
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.requireHexPrefix
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.runtime.ext.accountFromMapKey
import jp.co.soramitsu.staking.api.data.StakingType
import jp.co.soramitsu.staking.api.domain.model.CandidateInfo
import jp.co.soramitsu.staking.api.domain.model.CandidateInfoStatus
import jp.co.soramitsu.staking.api.domain.model.DelegatorStateStatus
import jp.co.soramitsu.staking.api.domain.model.Round
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.data.repository.datasource.ParachainStakingStoriesDataSourceImpl
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.getSelectedChain
import jp.co.soramitsu.staking.impl.domain.alerts.Alert
import jp.co.soramitsu.staking.impl.domain.model.NetworkInfo
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.staking.impl.domain.validations.balance.ManageStakingValidationFailure
import jp.co.soramitsu.staking.impl.domain.validations.balance.ManageStakingValidationPayload
import jp.co.soramitsu.staking.impl.presentation.staking.alerts.model.AlertModel
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.staking.impl.presentation.mappers.mapPeriodReturnsToRewardEstimation
import jp.co.soramitsu.staking.impl.presentation.staking.main.ReturnsModel
import jp.co.soramitsu.staking.impl.presentation.staking.main.StakingRewardEstimationBottomSheet
import jp.co.soramitsu.staking.impl.presentation.staking.main.StakingViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.StakingViewStateOld
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.CollatorStakeInfoViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.EstimatedEarningsViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.MAX_STAKING_TIMER_MILLIS
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.StakeInfoViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.StakeStatus
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.default
import jp.co.soramitsu.staking.impl.presentation.staking.main.default
import jp.co.soramitsu.staking.impl.presentation.staking.main.di.StakingViewStateFactory
import jp.co.soramitsu.staking.impl.presentation.staking.main.model.StakingNetworkInfoModel
import jp.co.soramitsu.staking.impl.scenarios.parachain.StakingParachainScenarioInteractor
import jp.co.soramitsu.staking.impl.scenarios.relaychain.HOURS_IN_DAY
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.CollatorDetailsParcelModel
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.CollatorStakeParcelModel
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.IdentityParcelModel
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch

class StakingParachainScenarioViewModel(
    private val stakingInteractor: StakingInteractor,
    private val scenarioInteractor: StakingParachainScenarioInteractor,
    private val resourceManager: ResourceManager,
    private val baseViewModel: BaseStakingViewModel,
    private val stakingViewStateFactory: StakingViewStateFactory,
    private val storiesDataSourceImpl: ParachainStakingStoriesDataSourceImpl,
    private val rewardCalculatorFactory: RewardCalculatorFactory,
    private val setupStakingSharedState: SetupStakingSharedState,
    private val router: StakingRouter
) : StakingScenarioViewModel {

    override val enteredAmountFlow = MutableStateFlow<BigDecimal?>(BigDecimal.TEN)

    private val currentAssetFlow = stakingInteractor.currentAssetFlow()
    private var lastReportedCollatorDataIssues = emptySet<CollatorDataIssue>()
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

    override val stakingStateFlow: Flow<StakingState> =
        scenarioInteractor.stakingStateFlow().shareIn(baseViewModel.stakingStateScope, SharingStarted.Eagerly, replay = 1)

    init {
        baseViewModel.enteredAmountEvent.onEach { event ->
            enteredAmountFlow.value = event.peekContent().setScale(5, RoundingMode.HALF_DOWN)
        }.launchIn(baseViewModel.stakingStateScope)
    }

    override val stakingViewStateFlowOld: Flow<StakingViewStateOld> = stakingStateFlow.map { stakingState ->
        when (stakingState) {
            is StakingState.Parachain.None -> {
                stakingViewStateFactory.createParachainWelcomeViewState(
                    stakingInteractor.currentAssetFlow(),
                    baseViewModel.stakingStateScope,
                    baseViewModel::showError
                )
            }

            is StakingState.Parachain.Delegator -> {
                stakingViewStateFactory.createDelegatorViewState(
                    stakingState,
                    stakingInteractor.currentAssetFlow(),
                    baseViewModel.stakingStateScope,
                    baseViewModel::showError
                )
            }

            else -> error("Wrong state")
        }
    }.shareIn(baseViewModel.stakingStateScope, SharingStarted.Lazily, replay = 1)

    @Deprecated("Don't use this method, use the getStakingViewStateFlow instead")
    override suspend fun getStakingViewStateFlowOld(): Flow<StakingViewStateOld> {
        return stakingStateFlow.map { stakingState ->
            when (stakingState) {
                is StakingState.Parachain.None -> {
                    stakingViewStateFactory.createParachainWelcomeViewState(
                        stakingInteractor.currentAssetFlow(),
                        baseViewModel.stakingStateScope,
                        baseViewModel::showError
                    )
                }

                is StakingState.Parachain.Delegator -> {
                    stakingViewStateFactory.createDelegatorViewState(
                        stakingState,
                        stakingInteractor.currentAssetFlow(),
                        baseViewModel.stakingStateScope,
                        baseViewModel::showError
                    )
                }

                else -> error("Wrong state")
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun getStakingViewStateFlow(): Flow<StakingViewState> {
        return stakingStateFlow.flatMapLatest { stakingState ->
            when (stakingState) {
                is StakingState.Parachain.None -> welcomeStateFlow()
                is StakingState.Parachain.Delegator -> combine(
                    amountInputViewState,
                    estimatedEarnings,
                    collatorStates(stakingState)
                ) { inputState, returns, delegations ->
                    StakingViewState.Parachain.Delegator(
                        delegations = delegations,
                        estimatedEarnings = returns.toEstimatedEarnings(inputState)
                    )
                }
                else -> error("StakingParachainScenarioViewModel received ${stakingState::class.simpleName}")
            }
        }
    }

    private fun welcomeStateFlow(): Flow<StakingViewState> {
        return combine(amountInputViewState, estimatedEarnings) { inputState, returns ->
            StakingViewState.Parachain.Welcome(returns.toEstimatedEarnings(inputState))
        }
    }

    private fun collatorStates(
        delegatorState: StakingState.Parachain.Delegator
    ): Flow<List<CollatorStakeInfoViewState>> {
        return currentAssetFlow.map { asset ->
            val chainId = asset.token.configuration.chainId
            val collatorIds = delegatorState.delegations.map { it.collatorId }
            val chain = stakingInteractor.getSelectedChain()
            val collatorNames = scenarioInteractor.getIdentities(collatorIds)
                .map { (accountIdHex, identity) -> chain.accountFromMapKey(accountIdHex) to identity }
                .toMap()
            val candidateInfos = scenarioInteractor.getCandidateInfos(chainId, collatorIds)
            val readyToUnlock = scenarioInteractor.getCollatorIdsWithReadyToUnlockingTokens(
                collatorIds,
                delegatorState.accountId
            )
            val apyByCollator = rewardCalculatorFactory.createSubquery().getApy(collatorIds)
            val currentBlock = runCatching { stakingInteractor.currentBlockNumber() }.getOrNull()
            val currentRound = runCatching { scenarioInteractor.getCurrentRound(chainId).getOrNull() }.getOrNull()
            val hoursInRound = scenarioInteractor.hoursInRound[chainId]
            val roundTimeLeft = if (currentBlock != null && currentRound != null && hoursInRound != null) {
                calculateRoundTimeLeftMillis(currentRound, currentBlock, hoursInRound)
            } else {
                null
            }
            val hasLeavingCandidate = candidateInfos.values.any { it.status is CandidateInfoStatus.LEAVING }
            val leaveDelay = if (hasLeavingCandidate) {
                runCatching { scenarioInteractor.getLeaveCandidatesDelay().getOrNull() }.getOrNull()
            } else {
                null
            }
            val dataIssues = buildSet {
                if (delegatorState.delegations.any { it.collatorId.toHexString(false) !in candidateInfos }) {
                    add(CollatorDataIssue.CANDIDATE_INFO)
                }
                if (currentBlock == null || currentRound == null || roundTimeLeft == null) {
                    add(CollatorDataIssue.CURRENT_ROUND)
                }
                if (hoursInRound == null || hoursInRound <= 0) {
                    add(CollatorDataIssue.ROUND_DURATION)
                }
                if (hasLeavingCandidate && (leaveDelay == null || leaveDelay < 0)) {
                    add(CollatorDataIssue.LEAVE_DELAY)
                }
            }
            reportCollatorDataIssues(dataIssues)

            attachCandidateInfoPreservingDelegations(
                delegatorState.delegations,
                candidateInfos
            ) { it.collatorId.toHexString(false) }.map { (delegation, candidateInfo) ->
                val collatorIdHex = delegation.collatorId.toHexString(false)
                val identity = collatorNames[collatorIdHex]
                val staked = asset.token.amountFromPlanks(delegation.delegatedAmountInPlanks)
                val rewarded = asset.token.amountFromPlanks(delegation.rewardedAmountInPlanks)
                val leavingTimeLeft = if (currentRound != null && hoursInRound != null && leaveDelay != null) {
                    calculateLeavingTimeLeftMillis(
                        currentRoundNumber = currentRound.current,
                        leavingRound = (candidateInfo?.status as? CandidateInfoStatus.LEAVING)?.leavingBlock,
                        hoursInRound = hoursInRound,
                        leaveDelay = leaveDelay
                    )
                } else {
                    null
                }
                val isReadyToUnlock = readyToUnlock.any { it.contentEquals(delegation.collatorId) }
                val defaultState = StakeInfoViewState.ParachainStakeInfoViewState.default(
                    resourceManager,
                    identity?.display ?: collatorIdHex
                )

                CollatorStakeInfoViewState(
                    collatorId = delegation.collatorId,
                    collatorAddress = delegation.collatorId.toHexString(true),
                    stakeInfo = defaultState.copy(
                        staked = defaultState.staked.copy(
                            value = staked.formatCryptoDetail(asset.token.configuration.symbol),
                            additionalValue = staked.applyFiatRate(asset.token.fiatRate)?.formatFiat(asset.token.fiatSymbol)
                        ),
                        rewards = defaultState.rewards.copy(
                            value = apyByCollator[collatorIdHex].orZero().formatAsPercentage(),
                            additionalValue = rewarded.applyFiatRate(asset.token.fiatRate)?.formatFiat(asset.token.fiatSymbol)
                        ),
                        status = mapCollatorStakeStatus(
                            candidateInfo?.status,
                            roundTimeLeft,
                            leavingTimeLeft,
                            isReadyToUnlock
                        )
                    ),
                    collator = candidateInfo
                )
            }
        }
    }

    private suspend fun calculateReturns(
        asset: jp.co.soramitsu.wallet.impl.domain.model.Asset,
        amount: BigDecimal
    ): ReturnsModel {
        val calculator = rewardCalculatorFactory.createSubquery()
        val chainId = asset.token.configuration.chainId
        val monthly = calculator.calculateReturns(amount, PERIOD_MONTH, isCompound = true, chainId = chainId)
        val yearly = calculator.calculateReturns(amount, PERIOD_YEAR, isCompound = true, chainId = chainId)

        return ReturnsModel(
            mapPeriodReturnsToRewardEstimation(monthly, asset.token, resourceManager),
            mapPeriodReturnsToRewardEstimation(yearly, asset.token, resourceManager)
        )
    }

    override suspend fun rewardEstimationPayload(): StakingRewardEstimationBottomSheet.Payload {
        val asset = currentAssetFlow.first()
        val calculator = rewardCalculatorFactory.createSubquery()

        return StakingRewardEstimationBottomSheet.Payload(
            calculator.calculateMaxAPY(asset.token.configuration.chainId).formatAsPercentage(),
            calculator.calculateAvgAPY().formatAsPercentage(),
            R.string.staking_reward_info_apr_max,
            R.string.staking_reward_info_apr_avg
        )
    }

    override fun startStaking() {
        baseViewModel.stakingStateScope.launch {
            val amount = enteredAmountFlow.first().orZero()
            val initial = setupStakingSharedState.getOrNull<SetupStakingProcess.Initial>()
                ?.takeIf { it.stakingType == StakingType.PARACHAIN }
                ?: SetupStakingProcess.Initial(StakingType.PARACHAIN)
            setupStakingSharedState.set(initial.fullFlow(SetupStakingProcess.SetupStep.Parachain(amount)))
            router.openSetupStaking()
        }
    }

    fun openCollatorInfo(model: CollatorStakeInfoViewState) {
        val collator = model.collator ?: run {
            baseViewModel.showError(resourceManager.getString(R.string.common_error_general_message))
            return
        }

        baseViewModel.stakingStateScope.launch {
            val identity = scenarioInteractor.getIdentity(model.collatorId)
            val apy = rewardCalculatorFactory.createSubquery().getApyFor(model.collatorId)
            router.openCollatorDetails(
                CollatorDetailsParcelModel(
                    model.collatorId.toHexString(true),
                    CollatorStakeParcelModel(
                        status = collator.status,
                        selfBonded = collator.bond,
                        delegations = collator.delegationCount.toInt(),
                        totalStake = collator.totalCounted,
                        minBond = collator.lowestTopDelegationAmount,
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
                    collator.request.orEmpty()
                )
            )
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

    private fun reportCollatorDataIssues(issues: Set<CollatorDataIssue>) {
        if (issues.isNotEmpty() && issues != lastReportedCollatorDataIssues) {
            baseViewModel.showError(resourceManager.getString(R.string.common_error_general_message))
        }

        lastReportedCollatorDataIssues = issues
    }

    override suspend fun networkInfo(): Flow<LoadingState<StakingNetworkInfoModel>> {
        return combine(
            scenarioInteractor.observeNetworkInfoState().map { it as NetworkInfo.Parachain },
            stakingInteractor.currentAssetFlow()
        ) { networkInfo, asset ->
            val minimumStake = asset.token.amountFromPlanks(networkInfo.minimumStake)
            val minimumStakeFormatted = minimumStake.formatCryptoDetail(asset.token.configuration.symbol)

            val minimumStakeFiat = asset.token.fiatAmount(minimumStake)?.formatFiat(asset.token.fiatSymbol)

            val lockupPeriod = if (networkInfo.lockupPeriodInHours > HOURS_IN_DAY) {
                val inDays = networkInfo.lockupPeriodInHours / HOURS_IN_DAY
                resourceManager.getQuantityString(R.plurals.common_days_format, inDays, inDays)
            } else {
                resourceManager.getQuantityString(R.plurals.common_hours_format, networkInfo.lockupPeriodInHours, networkInfo.lockupPeriodInHours)
            }
            StakingNetworkInfoModel.Parachain(lockupPeriod, minimumStakeFormatted, minimumStakeFiat)
        }.withLoading()
    }

    override suspend fun alerts(): Flow<LoadingState<List<AlertModel>>> {
        return scenarioInteractor.stakingStateFlow().map { state ->
            if (state !is StakingState.Parachain.Delegator) return@map emptyList<AlertModel>()

            val lowStakeAlerts = produceLowStakeAlerts(state)
            val collatorLeavingAlerts = produceCollatorLeavingAlerts(state)
            val readyForUnlocking = produceReadyForUnlockingAlerts(state)

            (lowStakeAlerts + collatorLeavingAlerts + readyForUnlocking).map { it.toModel() }
        }.withLoading()
    }

    private suspend fun produceCollatorLeavingAlerts(state: StakingState.Parachain.Delegator): List<Alert.CollatorLeaving> {
        val identities = scenarioInteractor.getIdentities(state.delegations.map { it.collatorId })
        return state.delegations.filter { it.status == DelegatorStateStatus.LEAVING }
            .map {
                val collatorId = it.collatorId.toHexString()
                val name = identities[collatorId]?.display ?: collatorId
                Alert.CollatorLeaving(it, name)
            }
    }

    private suspend fun produceLowStakeAlerts(state: StakingState.Parachain.Delegator): List<Alert.ChangeCollators> {
        val collatorIds = state.delegations.map { it.collatorId }
        val bottomDelegations = scenarioInteractor.getBottomDelegations(state.chain.id, collatorIds)
        val accountIdToCheck = state.accountId

        return bottomDelegations.mapNotNull { (collatorIdHex, delegations) ->
            val delegation = delegations.find { it.owner.contentEquals(accountIdToCheck) } ?: return@mapNotNull null
            val candidateInfo = scenarioInteractor.getCollator(collatorIdHex.requireHexPrefix().fromHex())
            val amountToStakeMoreInPlanks = (candidateInfo.lowestTopDelegationAmount - delegation.amount)
            val token = stakingInteractor.currentAssetFlow().first().token
            val amountToStakeMore = (token.amountFromPlanks(amountToStakeMoreInPlanks) * BigDecimal(1.1)).formatCryptoDetail(token.configuration.symbol)
            Alert.ChangeCollators(collatorIdHex.requireHexPrefix(), amountToStakeMore)
        }
    }

    private suspend fun produceReadyForUnlockingAlerts(state: StakingState.Parachain.Delegator): List<Alert.ReadyForUnlocking> {
        val collatorIds = state.delegations.map { it.collatorId }
        return scenarioInteractor.getCollatorIdsWithReadyToUnlockingTokens(collatorIds, state.accountId).map {
            Alert.ReadyForUnlocking(it)
        }
    }

    private fun Alert.toModel(): AlertModel {
        return when (this) {
            is Alert.ChangeCollators -> {
                AlertModel(
                    StakingScenarioViewModel.WARNING_ICON,
                    resourceManager.getString(R.string.staking_alert_low_stake_title),
                    resourceManager.getString(R.string.staking_alert_low_stake_text, this.amountToStakeMore),
                    AlertModel.Type.CallToAction { baseViewModel.openStakingBalance(this.collatorIdHex) }
                )
            }

            is Alert.CollatorLeaving -> {
                AlertModel(
                    StakingScenarioViewModel.WARNING_ICON,
                    resourceManager.getString(R.string.staking_alert_leaving_collator_title, this.collatorName),
                    resourceManager.getString(R.string.staking_alert_leaving_collator_text, this.collatorName),
                    AlertModel.Type.CallToAction { baseViewModel.openStakingBalance(this.delegation.collatorId.toHexString(true)) }
                )
            }

            is Alert.ReadyForUnlocking -> {
                AlertModel(
                    StakingScenarioViewModel.WARNING_ICON,
                    resourceManager.getString(R.string.staking_alert_unlock_title),
                    resourceManager.getString(R.string.staking_alert_unlock_text),
                    AlertModel.Type.CallToAction { baseViewModel.openStakingBalance(this.collatorId.toHexString(true)) }
                )
            }

            else -> error("Wrong alert type")
        }
    }

    override fun stakingStoriesFlow(): Flow<List<StoryGroup.Staking>> {
        return storiesDataSourceImpl.getStoriesFlow()
    }

    override suspend fun getBondMoreValidationSystem(): ValidationSystem<ManageStakingValidationPayload, ManageStakingValidationFailure> {
        return ValidationSystem(
            CompositeValidation(
                validations = listOf()
            )
        )
    }

    override suspend fun getRedeemValidationSystem(): ValidationSystem<ManageStakingValidationPayload, ManageStakingValidationFailure> {
        return ValidationSystem(
            CompositeValidation(
                validations = listOf()
            )
        )
    }
}

private enum class CollatorDataIssue {
    CANDIDATE_INFO,
    CURRENT_ROUND,
    ROUND_DURATION,
    LEAVE_DELAY
}

private val MILLIS_PER_HOUR = BigInteger.valueOf(3_600_000L)
private val MAX_STAKING_TIMER_MILLIS_BIG_INTEGER = BigInteger.valueOf(MAX_STAKING_TIMER_MILLIS)

internal fun calculateRoundTimeLeftMillis(
    currentRound: Round,
    currentBlock: BigInteger,
    hoursInRound: Int
): Long? {
    if (
        currentRound.current.signum() < 0 ||
        currentRound.first.signum() < 0 ||
        currentRound.length.signum() <= 0 ||
        currentBlock.signum() < 0 ||
        hoursInRound <= 0
    ) {
        return null
    }

    val finishBlock = currentRound.first + currentRound.length
    val blocksLeft = (finishBlock - currentBlock)
        .coerceAtLeast(BigInteger.ZERO)
        .coerceAtMost(currentRound.length)
    val roundDurationMillis = BigInteger.valueOf(hoursInRound.toLong()) * MILLIS_PER_HOUR
    val timeLeftMillis = blocksLeft * roundDurationMillis / currentRound.length

    return timeLeftMillis.toBoundedStakingTimerMillis()
}

internal fun calculateLeavingTimeLeftMillis(
    currentRoundNumber: BigInteger,
    leavingRound: Long?,
    hoursInRound: Int,
    leaveDelay: Int
): Long? {
    if (
        currentRoundNumber.signum() < 0 ||
        leavingRound == null ||
        leavingRound < 0 ||
        hoursInRound <= 0 ||
        leaveDelay < 0
    ) {
        return null
    }

    val executionRound = BigInteger.valueOf(leavingRound) + BigInteger.valueOf(leaveDelay.toLong())
    val roundsLeft = (executionRound - currentRoundNumber).coerceAtLeast(BigInteger.ZERO)
    val timeLeftMillis = roundsLeft * BigInteger.valueOf(hoursInRound.toLong()) * MILLIS_PER_HOUR

    return timeLeftMillis.toBoundedStakingTimerMillis()
}

private fun BigInteger.toBoundedStakingTimerMillis(): Long = when {
    signum() <= 0 -> 0L
    this >= MAX_STAKING_TIMER_MILLIS_BIG_INTEGER -> MAX_STAKING_TIMER_MILLIS
    else -> toLong()
}

internal fun <T> attachCandidateInfoPreservingDelegations(
    delegations: List<T>,
    candidateInfos: Map<String, CandidateInfo>,
    collatorIdHex: (T) -> String
): List<Pair<T, CandidateInfo?>> = delegations.map { delegation ->
    delegation to candidateInfos[collatorIdHex(delegation)]
}

internal fun mapCollatorStakeStatus(
    status: CandidateInfoStatus?,
    roundTimeLeft: Long?,
    leavingTimeLeft: Long?,
    isReadyToUnlock: Boolean
): StakeStatus {
    return when {
        isReadyToUnlock -> StakeStatus.ReadyToUnlockCollator
        status == null -> StakeStatus.UnavailableCollator
        status == CandidateInfoStatus.ACTIVE -> StakeStatus.ActiveCollator(
            timeLeft = roundTimeLeft ?: 0L,
            hideZeroTimer = roundTimeLeft == null
        )
        status is CandidateInfoStatus.LEAVING -> StakeStatus.LeavingCollator(leavingTimeLeft ?: 0L)
        status == CandidateInfoStatus.EMPTY || status == CandidateInfoStatus.IDLE -> StakeStatus.IdleCollator()
        else -> StakeStatus.IdleCollator()
    }
}
