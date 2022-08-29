package jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios

import java.math.BigDecimal
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.withLoading
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.requireHexPrefix
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.api.domain.model.DelegatorStateStatus
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.alerts.Alert
import jp.co.soramitsu.staking.impl.domain.model.NetworkInfo
import jp.co.soramitsu.staking.impl.domain.validations.balance.ManageStakingValidationFailure
import jp.co.soramitsu.staking.impl.domain.validations.balance.ManageStakingValidationPayload
import jp.co.soramitsu.staking.impl.presentation.staking.alerts.model.AlertModel
import jp.co.soramitsu.staking.impl.presentation.staking.main.StakingViewStateOld
import jp.co.soramitsu.staking.impl.presentation.staking.main.StakingViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.di.StakingViewStateFactory
import jp.co.soramitsu.staking.impl.presentation.staking.main.model.StakingNetworkInfoModel
import jp.co.soramitsu.staking.impl.scenarios.parachain.StakingParachainScenarioInteractor
import jp.co.soramitsu.wallet.api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class StakingParachainScenarioViewModel(
    private val stakingInteractor: StakingInteractor,
    private val scenarioInteractor: StakingParachainScenarioInteractor,
    private val resourceManager: ResourceManager,
    private val baseViewModel: BaseStakingViewModel,
    private val stakingViewStateFactory: StakingViewStateFactory
) : StakingScenarioViewModel {

    override val stakingStateFlow: Flow<StakingState> = scenarioInteractor.stakingStateFlow

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

    override suspend fun getStakingViewStateFlow(): Flow<StakingViewState> {
        return emptyFlow()
    }

    override suspend fun networkInfo(): Flow<LoadingState<StakingNetworkInfoModel>> {
        return combine(
            scenarioInteractor.observeNetworkInfoState().map { it as NetworkInfo.Parachain },
            stakingInteractor.currentAssetFlow()
        ) { networkInfo, asset ->
            val minimumStake = asset.token.amountFromPlanks(networkInfo.minimumStake)
            val minimumStakeFormatted = minimumStake.formatTokenAmount(asset.token.configuration)

            val minimumStakeFiat = asset.token.fiatAmount(minimumStake)?.formatAsCurrency(asset.token.fiatSymbol)

            val lockupPeriod = resourceManager.getQuantityString(R.plurals.staking_main_lockup_period_value, networkInfo.lockupPeriodInDays)
                .format(networkInfo.lockupPeriodInDays)

            StakingNetworkInfoModel.Parachain(lockupPeriod, minimumStakeFormatted, minimumStakeFiat)
        }.withLoading()
    }

    override suspend fun alerts(): Flow<LoadingState<List<AlertModel>>> {
        return scenarioInteractor.stakingStateFlow.map { state ->
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
            val amountToStakeMore = (token.amountFromPlanks(amountToStakeMoreInPlanks) * BigDecimal(1.1)).formatTokenAmount(token.configuration.symbol)
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
