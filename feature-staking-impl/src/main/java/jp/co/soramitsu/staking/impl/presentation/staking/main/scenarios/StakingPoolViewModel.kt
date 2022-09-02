package jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios

import java.math.BigDecimal
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.withLoading
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.staking.impl.domain.validations.balance.ManageStakingValidationFailure
import jp.co.soramitsu.staking.impl.domain.validations.balance.ManageStakingValidationPayload
import jp.co.soramitsu.staking.impl.presentation.mappers.mapPeriodReturnsToRewardEstimation
import jp.co.soramitsu.staking.impl.presentation.staking.alerts.model.AlertModel
import jp.co.soramitsu.staking.impl.presentation.staking.main.Pool
import jp.co.soramitsu.staking.impl.presentation.staking.main.ReturnsModel
import jp.co.soramitsu.staking.impl.presentation.staking.main.StakingViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.StakingViewStateOld
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.EstimatedEarningsViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.toViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.model.StakingNetworkInfoModel
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class StakingPoolViewModel(
    private val stakingPoolInteractor: StakingPoolInteractor,
    private val stakingInteractor: StakingInteractor,
    private val resourceManager: ResourceManager,
    private val rewardCalculatorFactory: RewardCalculatorFactory
) : StakingScenarioViewModel {

    override val stakingStateFlow: Flow<StakingState> = stakingPoolInteractor.stakingStateFlow()

    override suspend fun getStakingViewStateFlowOld(): Flow<StakingViewStateOld> {
        return kotlinx.coroutines.flow.flowOf(Pool)
    }

    override suspend fun getStakingViewStateFlow(): Flow<StakingViewState> {
        return stakingStateFlow.map { state ->
            when (state) {
                is StakingState.Pool.Member -> {
                    val asset = stakingInteractor.currentAssetFlow().first()
                    val poolViewState = state.pool.toViewState(asset, resourceManager)
                    StakingViewState.Pool.PoolMember(poolViewState)
                }
                is StakingState.Pool.None -> {
                    val returns = getReturns(state.chain.id)

                    val returnsViewState = EstimatedEarningsViewState(
                        monthlyChange = TitleValueViewState(returns.monthly.gain, returns.monthly.amount, returns.monthly.fiatAmount),
                        yearlyChange = TitleValueViewState(returns.yearly.gain, returns.yearly.amount, returns.yearly.fiatAmount)
                    )
                    StakingViewState.Pool.Welcome(returnsViewState)
                }
                is StakingState.Pool.Nominator -> error("StakingState.Pool.Nominator is not supported")
                is StakingState.Pool.Root -> error("StakingState.Pool.Root is not supported")
                is StakingState.Pool.StateToggler -> error("StakingState.Pool.StateToggler is not supported")
                else -> error("StakingPoolViewModel.getStakingViewStateFlow wrong staking state")
            }
        }
    }

    private suspend fun getReturns(id: ChainId): ReturnsModel {
        val calculator = rewardCalculatorFactory.createManual(id)
        val asset = stakingInteractor.currentAssetFlow().first()
        val chainId = asset.token.configuration.chainId
        val monthly = calculator.calculateReturns(BigDecimal.ONE, PERIOD_MONTH, true, chainId)
        val yearly = calculator.calculateReturns(BigDecimal.ONE, PERIOD_YEAR, true, chainId)

        val monthlyEstimation = mapPeriodReturnsToRewardEstimation(monthly, asset.token, resourceManager)
        val yearlyEstimation = mapPeriodReturnsToRewardEstimation(yearly, asset.token, resourceManager)

        return ReturnsModel(monthlyEstimation, yearlyEstimation)
    }

    override suspend fun networkInfo(): Flow<LoadingState<StakingNetworkInfoModel>> {
        return stakingInteractor.currentAssetFlow().filter { it.token.configuration.supportStakingPool }.map { asset ->
            val config = asset.token.configuration
            val chainId = config.chainId

            val minToJoinPoolInPlanks = stakingPoolInteractor.getMinToJoinPool(chainId)
            val minToJoinPool = asset.token.configuration.amountFromPlanks(minToJoinPoolInPlanks)
            val minToJoinPoolFormatted = minToJoinPool.formatTokenAmount(config)
            val minToJoinPoolFiat = asset.token.fiatAmount(minToJoinPool)?.formatAsCurrency(asset.token.fiatSymbol)

            val minToCreatePoolInPlanks = stakingPoolInteractor.getMinToCreate(chainId)
            val minToCreatePool = asset.token.configuration.amountFromPlanks(minToCreatePoolInPlanks)
            val minToCreatePoolFormatted = minToCreatePool.formatTokenAmount(config)
            val minToCreatePoolFiat = asset.token.fiatAmount(minToCreatePool)?.formatAsCurrency(asset.token.fiatSymbol)

            val existingPools = stakingPoolInteractor.getExistingPools(chainId).toString()
            val possiblePools = stakingPoolInteractor.getPossiblePools(chainId).toString()
            val maxMembersInPool = stakingPoolInteractor.getMaxMembersInPool(chainId).toString()
            val maxPoolsMembers = stakingPoolInteractor.getMaxPoolsMembers(chainId).toString()

            StakingNetworkInfoModel.Pool(
                minToJoinPoolFormatted,
                minToJoinPoolFiat,
                minToCreatePoolFormatted,
                minToCreatePoolFiat,
                existingPools,
                possiblePools,
                maxMembersInPool,
                maxPoolsMembers
            )
        }.withLoading()
    }

    override suspend fun alerts(): Flow<LoadingState<List<AlertModel>>> {
        return flowOf { LoadingState.Loaded(emptyList()) }
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
