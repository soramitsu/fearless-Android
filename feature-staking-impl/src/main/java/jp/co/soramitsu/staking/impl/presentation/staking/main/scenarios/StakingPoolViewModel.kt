package jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios

import java.math.BigDecimal
import java.math.RoundingMode
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.domain.model.StoryGroup
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.formatAsPercentage
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.nullIfEmpty
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.rewards.PeriodReturns
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculator
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.staking.impl.domain.validations.balance.ManageStakingValidationFailure
import jp.co.soramitsu.staking.impl.domain.validations.balance.ManageStakingValidationPayload
import jp.co.soramitsu.staking.impl.presentation.mappers.mapPeriodReturnsToRewardEstimation
import jp.co.soramitsu.staking.impl.presentation.staking.alerts.model.AlertModel
import jp.co.soramitsu.staking.impl.presentation.staking.main.Pool
import jp.co.soramitsu.staking.impl.presentation.staking.main.ReturnsModel
import jp.co.soramitsu.staking.impl.presentation.staking.main.StakingViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.StakingViewStateOld
import jp.co.soramitsu.staking.impl.presentation.staking.main.StakingRewardEstimationBottomSheet
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.EstimatedEarningsViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.toViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.default
import jp.co.soramitsu.staking.impl.presentation.staking.main.model.StakingNetworkInfoModel
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

internal suspend fun RewardCalculator.calculatePoolStakingReturns(
    amount: BigDecimal,
    periodInDays: Int,
    chainId: ChainId
): PeriodReturns = calculateReturns(amount, periodInDays, isCompound = true, chainId = chainId)

class StakingPoolViewModel(
    private val stakingPoolInteractor: StakingPoolInteractor,
    private val stakingInteractor: StakingInteractor,
    private val resourceManager: ResourceManager,
    private val rewardCalculatorFactory: RewardCalculatorFactory,
    baseViewModel: BaseStakingViewModel
) : StakingScenarioViewModel {

    override val stakingType = jp.co.soramitsu.staking.api.data.StakingType.POOL

    private val initialValue = BigDecimal.TEN
    private val currentAssetFlow = stakingInteractor.currentAssetFlow()

    override val stakingStateFlow: Flow<StakingState?> = stakingPoolInteractor.stakingStateFlow()

    @Deprecated(
        "Don't use this method, use the getStakingViewStateFlow instead",
        ReplaceWith(
            "jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios.getStakingViewStateFlow()"
        )
    )
    override val stakingViewStateFlowOld: Flow<StakingViewStateOld> = kotlinx.coroutines.flow.flowOf(Pool)

    override suspend fun getStakingViewStateFlowOld(): Flow<StakingViewStateOld> {
        return kotlinx.coroutines.flow.flowOf(Pool)
    }

    init {
        baseViewModel.enteredAmountEvent.onEach { event ->
            enteredAmountFlow.value = event.peekContent().setScale(5, RoundingMode.HALF_DOWN)
        }.launchIn(baseViewModel.stakingStateScope)

    }

    override val enteredAmountFlow = MutableStateFlow(initialValue)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override suspend fun getStakingViewStateFlow(): Flow<StakingViewState?> {
        return stakingStateFlow.flatMapLatest { state ->
            if (state == null) return@flatMapLatest kotlinx.coroutines.flow.flowOf(null)
            combine(currentAssetFlow, enteredAmountFlow) { asset, amount -> asset to amount.orZero() }
                .transformLatest { (asset, amount) ->
                    if (!asset.token.configuration.supportStakingPool || asset.chainId != state.chain.id ||
                        !asset.accountId.contentEquals(state.accountId)) {
                        emit(null)
                        return@transformLatest
                    }
                    when (state) {
                        is StakingState.Pool.Member -> emit(
                            StakingViewState.Pool.PoolMember(state.pool.toViewState(asset, resourceManager))
                        )
                        is StakingState.Pool.None -> {
                            val input = AmountInputViewState(
                                tokenName = asset.token.configuration.symbol,
                                tokenImage = asset.token.configuration.iconUrl,
                                totalBalance = resourceManager.getString(
                                    R.string.common_balance_format, (asset.transferable?.formatCrypto(asset.token.configuration.symbol)
                ?: resourceManager.getString(jp.co.soramitsu.common.R.string.portfolio_balance_unavailable))
                                ),
                                fiatAmount = amount.applyFiatRate(asset.token.fiatRate)?.formatFiat(asset.token.fiatSymbol),
                                tokenAmount = amount
                            )
                            // Optional reward estimates never gate a healthy pool entry or retain a prior quote.
                            emit(poolWelcome(input, ReturnsModel.default))
                            try {
                                emit(poolWelcome(input, getReturns(asset, amount)))
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                // Keep the current input available without an unverified estimate.
                            }
                        }
                        else -> emit(null)
                    }
                }.onStart { emit(null) }.catch { emit(null) }
        }
    }

    private fun poolWelcome(input: AmountInputViewState, returns: ReturnsModel): StakingViewState.Pool.Welcome {
        val monthly = returns.monthly.gain.nullIfEmpty()?.let {
            TitleValueViewState(it, returns.monthly.amount.nullIfEmpty(), returns.monthly.fiatAmount)
        }
        val yearly = returns.yearly.gain.nullIfEmpty()?.let {
            TitleValueViewState(it, returns.yearly.amount.nullIfEmpty(), returns.yearly.fiatAmount)
        }
        return StakingViewState.Pool.Welcome(EstimatedEarningsViewState(monthly, yearly, input))
    }

    private suspend fun getReturns(asset: Asset, amount: BigDecimal): ReturnsModel {
        val calculator = rewardCalculatorFactory.create(asset.token.configuration)
        val chainId = asset.chainId
        val monthly = calculator.calculatePoolStakingReturns(amount, PERIOD_MONTH, chainId)
        val yearly = calculator.calculatePoolStakingReturns(amount, PERIOD_YEAR, chainId)
        val monthlyEstimation = mapPeriodReturnsToRewardEstimation(monthly, asset.token, resourceManager)
        val yearlyEstimation = mapPeriodReturnsToRewardEstimation(yearly, asset.token, resourceManager)
        return ReturnsModel(monthlyEstimation, yearlyEstimation)
    }

    override suspend fun rewardEstimationPayload(): StakingRewardEstimationBottomSheet.Payload =
        rewardEstimationPayload(currentAssetFlow.filter { it.token.configuration.supportStakingPool }.first())

    override suspend fun rewardEstimationPayload(asset: Asset): StakingRewardEstimationBottomSheet.Payload {
        val chainId = asset.token.configuration.chainId
        val calculator = rewardCalculatorFactory.create(asset.token.configuration)

        return StakingRewardEstimationBottomSheet.Payload(
            calculator.calculateMaxAPY(chainId).formatAsPercentage(),
            calculator.calculateAvgAPY().formatAsPercentage(),
            R.string.staking_reward_info_apr_max,
            R.string.staking_reward_info_apr_avg
        )
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override suspend fun networkInfo(): Flow<LoadingState<StakingNetworkInfoModel>> {
        return currentAssetFlow.transformLatest<Asset, LoadingState<StakingNetworkInfoModel>> { asset ->
            emit(LoadingState.Loading())
            if (!asset.token.configuration.supportStakingPool) return@transformLatest
            try {
                val config = asset.token.configuration
                val chainId = config.chainId
                val minToJoin = config.amountFromPlanks(stakingPoolInteractor.getMinToJoinPool(chainId))
                val minToCreate = config.amountFromPlanks(stakingPoolInteractor.getMinToCreate(chainId))
                val existingPools = stakingPoolInteractor.getExistingPools(chainId).toString()
                val possiblePools = stakingPoolInteractor.getPossiblePools(chainId).toString()
                val maxMembersInPool = stakingPoolInteractor.getMaxMembersInPool(chainId).toString()
                val maxPoolsMembers = stakingPoolInteractor.getMaxPoolsMembers(chainId).toString()
                emit(LoadingState.Loaded(StakingNetworkInfoModel.Pool(
                    minToJoin.formatCryptoDetail(config.symbol),
                    asset.token.fiatAmount(minToJoin)?.formatFiat(asset.token.fiatSymbol),
                    minToCreate.formatCryptoDetail(config.symbol),
                    asset.token.fiatAmount(minToCreate)?.formatFiat(asset.token.fiatSymbol),
                    existingPools,
                    possiblePools,
                    maxMembersInPool,
                    maxPoolsMembers
                )))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Current values remain unavailable; the next asset observation can recover.
            }
        }.onStart { emit(LoadingState.Loading()) }.catch { error ->
            if (error is CancellationException) throw error
            emit(LoadingState.Loading())
        }
    }

    override suspend fun alerts(): Flow<LoadingState<List<AlertModel>>> {
        return flowOf { LoadingState.Loaded(emptyList()) }
    }

    override fun stakingStoriesFlow(): Flow<List<StoryGroup.Staking>> {
        return emptyFlow()
    }

    override suspend fun getRedeemValidationSystem(): ValidationSystem<ManageStakingValidationPayload, ManageStakingValidationFailure> {
        return ValidationSystem(
            CompositeValidation(
                validations = listOf()
            )
        )
    }

    override suspend fun getBondMoreValidationSystem(): ValidationSystem<ManageStakingValidationPayload, ManageStakingValidationFailure> {
        return ValidationSystem(
            CompositeValidation(
                validations = listOf()
            )
        )
    }
}
