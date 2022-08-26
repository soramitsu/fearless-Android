package jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios

import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.withLoading
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.validations.balance.ManageStakingValidationFailure
import jp.co.soramitsu.staking.impl.domain.validations.balance.ManageStakingValidationPayload
import jp.co.soramitsu.staking.impl.presentation.staking.alerts.model.AlertModel
import jp.co.soramitsu.staking.impl.presentation.staking.main.StakingViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.di.StakingViewStateFactory
import jp.co.soramitsu.staking.impl.presentation.staking.main.model.StakingNetworkInfoModel
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class StakingPoolViewModel(
    private val stakingPoolInteractor: StakingPoolInteractor,
    private val stakingInteractor: StakingInteractor,
    private val stakingViewStateFactory: StakingViewStateFactory,
    private val baseViewModel: BaseStakingViewModel,
) :
    StakingScenarioViewModel {

    override val stakingStateFlow: Flow<StakingState> = stakingPoolInteractor.stakingStateFlow()

    override suspend fun getStakingViewStateFlow(): Flow<StakingViewState> {
        return stakingStateFlow.map { stakingState ->
            when (stakingState) {
                is StakingState.Pool.Member -> {
                    stakingViewStateFactory.createPoolMemberViewState(
                        stakingState,
                        stakingInteractor.currentAssetFlow(),
                        baseViewModel.stakingStateScope,
                        baseViewModel::showError
                    )
                }
                is StakingState.Pool.None -> {
                    stakingViewStateFactory.createPoolWelcomeViewState(
                        stakingInteractor.currentAssetFlow(),
                        baseViewModel.stakingStateScope,
                        baseViewModel::showError
                    )
                }
                else -> error("Wrong state")
            }
        }
    }

    override suspend fun networkInfo(): Flow<LoadingState<StakingNetworkInfoModel>> {
        return stakingInteractor.currentAssetFlow().map { asset ->
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
