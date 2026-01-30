package jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios

import java.math.BigDecimal
import jp.co.soramitsu.common.domain.model.StoryGroup
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.api.domain.model.StakingState
import java.math.BigInteger
import jp.co.soramitsu.staking.impl.domain.solana.SolanaStakingInteractor
import jp.co.soramitsu.staking.impl.domain.solana.SolanaValidator
import jp.co.soramitsu.staking.impl.presentation.staking.alerts.model.AlertModel
import jp.co.soramitsu.staking.impl.presentation.staking.main.SolanaComingSoonViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.SolanaStakingViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.SolanaValidatorViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.StakingViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.StakingViewStateOld
import jp.co.soramitsu.staking.impl.presentation.staking.main.model.StakingNetworkInfoModel
import jp.co.soramitsu.staking.impl.domain.validations.balance.ManageStakingValidationFailure
import jp.co.soramitsu.staking.impl.domain.validations.balance.ManageStakingValidationPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.flowOf

class SolanaStakingScenarioViewModel(
    private val solanaStakingInteractor: SolanaStakingInteractor,
    private val resourceManager: ResourceManager,
    private val baseViewModel: BaseStakingViewModel
) : StakingScenarioViewModel {

    override val enteredAmountFlow: MutableStateFlow<BigDecimal?> = MutableStateFlow(null)

    private val snapshots = solanaStakingInteractor.snapshotFlow()
        .shareIn(baseViewModel.stakingStateScope, SharingStarted.Eagerly, replay = 1)

    private val networkSnapshots = solanaStakingInteractor.networkInfoFlow()
        .shareIn(baseViewModel.stakingStateScope, SharingStarted.Eagerly, replay = 1)

    override val stakingStateFlow: Flow<StakingState> = snapshots.map {
        StakingState.Solana(it.chain, it.publicKey, it.address)
    }

    override val stakingViewStateFlowOld: Flow<StakingViewStateOld> = snapshots.map { SolanaComingSoonViewState(it.address) }

    override suspend fun getStakingViewStateFlowOld(): Flow<StakingViewStateOld> = stakingViewStateFlowOld

    override suspend fun getStakingViewStateFlow(): Flow<StakingViewState> {
        return networkSnapshots.map { state ->
            val data = (state as? LoadingState.Loaded)?.data
            val viewState = data?.let { buildSolanaViewState(it.validators) }
                ?: SolanaStakingViewState(
                    apy = "--",
                    totalStake = "--",
                    validatorsTitle = resourceManager.getString(R.string.staking_solana_validators_title),
                    validators = emptyList()
                )
            StakingViewState.Solana(viewState)
        }
    }

    override suspend fun networkInfo(): Flow<LoadingState<StakingNetworkInfoModel>> {
        return networkSnapshots.map { state ->
            when (state) {
                is LoadingState.Loading -> LoadingState.Loading()
                is LoadingState.Loaded -> {
                    val apy = formatApy(state.data.validators)
                    val totalStake = formatLamports(state.data.validators.totalStake())
                    LoadingState.Loaded(
                        StakingNetworkInfoModel.Solana(
                            apy = apy,
                            delegatedStake = resourceManager.getString(R.string.staking_solana_network_info_total, totalStake),
                            validatorsVisible = resourceManager.getString(R.string.staking_solana_validators_title)
                        )
                    )
                }
            }
        }
    }

    override suspend fun alerts(): Flow<LoadingState<List<AlertModel>>> {
        return flowOf(LoadingState.Loaded(emptyList()))
    }

    override suspend fun getRedeemValidationSystem(): ValidationSystem<ManageStakingValidationPayload, ManageStakingValidationFailure> {
        return emptyValidationSystem()
    }

    override suspend fun getBondMoreValidationSystem(): ValidationSystem<ManageStakingValidationPayload, ManageStakingValidationFailure> {
        return emptyValidationSystem()
    }

    override fun stakingStoriesFlow(): Flow<List<StoryGroup.Staking>> {
        return flowOf(emptyList())
    }

    private fun emptyValidationSystem() = ValidationSystem(
        CompositeValidation<ManageStakingValidationPayload, ManageStakingValidationFailure>(emptyList())
    )

    private fun buildSolanaViewState(validators: List<SolanaValidator>): SolanaStakingViewState {
        return SolanaStakingViewState(
            apy = formatApy(validators),
            totalStake = formatLamports(validators.totalStake()),
            validatorsTitle = resourceManager.getString(R.string.staking_solana_validators_title),
            validators = validators.take(5).mapIndexed { index, validator ->
                SolanaValidatorViewState(
                    rankLabel = "#${index + 1}",
                    identity = validator.voteAccount.take(8) + "…",
                    stake = formatLamports(validator.activatedStake),
                    commission = resourceManager.getString(R.string.staking_solana_validator_commission, validator.commission),
                    voteAccount = validator.voteAccount
                )
            }
        )
    }

    private fun formatApy(validators: List<SolanaValidator>): String {
        if (validators.isEmpty()) return "--"
        val avgCommission = validators.map { it.commission }.average()
        val estimatedApy = (7.0 * (1 - avgCommission / 100.0)).coerceAtLeast(0.0)
        return String.format("%.2f%%", estimatedApy)
    }

    private fun formatLamports(value: BigInteger): String {
        val sol = value.toBigDecimal().divide(SOL_IN_LAMPORTS, 2, java.math.RoundingMode.DOWN)
        return "${sol.stripTrailingZeros().toPlainString()} SOL"
    }

    private fun List<SolanaValidator>.totalStake(): BigInteger {
        return fold(BigInteger.ZERO) { acc, validator -> acc + validator.activatedStake }
    }

    companion object {
        private val SOL_IN_LAMPORTS = BigDecimal("1000000000")
    }
}
