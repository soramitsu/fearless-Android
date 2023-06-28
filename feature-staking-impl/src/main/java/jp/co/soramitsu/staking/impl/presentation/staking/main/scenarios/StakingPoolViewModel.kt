package jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios

import java.math.BigDecimal
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.domain.model.StoryGroup
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.nullIfEmpty
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.withLoading
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
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
import jp.co.soramitsu.staking.impl.presentation.staking.main.default
import jp.co.soramitsu.staking.impl.presentation.staking.main.model.StakingNetworkInfoModel
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class StakingPoolViewModel(
    private val stakingPoolInteractor: StakingPoolInteractor,
    private val stakingInteractor: StakingInteractor,
    private val resourceManager: ResourceManager,
    private val rewardCalculatorFactory: RewardCalculatorFactory,
    baseViewModel: BaseStakingViewModel
) : StakingScenarioViewModel {

    private val initialValue = BigDecimal.TEN
    private val defaultAmountInputState = AmountInputViewState(
        tokenName = "...",
        tokenImage = "",
        totalBalance = resourceManager.getString(R.string.common_balance_format, "..."),
        fiatAmount = "",
        tokenAmount = initialValue,
        initial = initialValue
    )
    private val currentAssetFlow = stakingInteractor.currentAssetFlow().filter { it.token.configuration.supportStakingPool }

    override val stakingStateFlow: Flow<StakingState> = stakingPoolInteractor.stakingStateFlow()

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

    override val enteredAmountFlow = MutableStateFlow(initialValue)

    private val amountInputViewState: Flow<AmountInputViewState> = combine(enteredAmountFlow, currentAssetFlow) { amount, asset ->
        val tokenBalance = asset.transferable.formatCrypto(asset.token.configuration.symbol)
        val fiatAmount = amount?.applyFiatRate(asset.token.fiatRate)?.formatFiat(asset.token.fiatSymbol)

        AmountInputViewState(
            tokenName = asset.token.configuration.symbol,
            tokenImage = asset.token.configuration.iconUrl,
            totalBalance = resourceManager.getString(R.string.common_balance_format, tokenBalance),
            fiatAmount = fiatAmount,
            tokenAmount = amount.orZero(),
            initial = initialValue
        )
    }.stateIn(baseViewModel.stakingStateScope, SharingStarted.Eagerly, defaultAmountInputState)

    private val estimatedEarningsViewState = combine(enteredAmountFlow, currentAssetFlow) { amount, asset ->
        getReturns(asset.token.configuration.chainId, amount.orZero())
    }.stateIn(baseViewModel.stakingStateScope, SharingStarted.Eagerly, ReturnsModel.default)

    override suspend fun getStakingViewStateFlow(): Flow<StakingViewState> {
        return combine(stakingStateFlow, amountInputViewState, estimatedEarningsViewState) { state, inputState, returns ->
            when (state) {
                is StakingState.Pool.Member -> {
                    val asset = stakingInteractor.currentAssetFlow().first()
                    val poolViewState = state.pool.toViewState(asset, resourceManager)
                    StakingViewState.Pool.PoolMember(poolViewState)
                }

                is StakingState.Pool.None -> {
                    val monthly =
                        returns.monthly.gain.nullIfEmpty()?.let { TitleValueViewState(it, returns.monthly.amount.nullIfEmpty(), returns.monthly.fiatAmount) }
                    val yearly =
                        returns.yearly.gain.nullIfEmpty()?.let { TitleValueViewState(it, returns.yearly.amount.nullIfEmpty(), returns.yearly.fiatAmount) }
                    val returnsViewState = EstimatedEarningsViewState(
                        monthlyChange = monthly,
                        yearlyChange = yearly,
                        inputState
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

    private suspend fun getReturns(id: ChainId, amount: BigDecimal): ReturnsModel {
        // todo hardcoded returns for demo
        val kusamaOnTestNodeChainId = "51cdb4b3101904a9d234d126656d33cd17518249819b510a03d6c90d0a019611"
        val polkadotOnTestNodeChainId = "4f77f65b21b1f396c1555850be6f21e2b1f36c26b94dbcbfec976901c9f08bf3"
        val chainId = if (id == kusamaOnTestNodeChainId || id == polkadotOnTestNodeChainId) {
            polkadotChainId
        } else {
            id
        }
        val asset = stakingInteractor.currentAssetFlow().first()
        val calculator = rewardCalculatorFactory.create(asset.token.configuration)
        val monthly = calculator.calculateReturns(amount, PERIOD_MONTH, true, chainId)
        val yearly = calculator.calculateReturns(amount, PERIOD_YEAR, true, chainId)

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
            val minToJoinPoolFormatted = minToJoinPool.formatCryptoDetail(config.symbol)
            val minToJoinPoolFiat = asset.token.fiatAmount(minToJoinPool)?.formatFiat(asset.token.fiatSymbol)

            val minToCreatePoolInPlanks = stakingPoolInteractor.getMinToCreate(chainId)
            val minToCreatePool = asset.token.configuration.amountFromPlanks(minToCreatePoolInPlanks)
            val minToCreatePoolFormatted = minToCreatePool.formatCryptoDetail(config.symbol)
            val minToCreatePoolFiat = asset.token.fiatAmount(minToCreatePool)?.formatFiat(asset.token.fiatSymbol)

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
