package jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios

import java.math.BigDecimal
import jp.co.soramitsu.common.domain.model.StoryGroup
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.common.utils.withLoading
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.data.repository.datasource.StakingStoriesDataSource
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.alerts.Alert
import jp.co.soramitsu.staking.impl.domain.alerts.AlertsInteractor
import jp.co.soramitsu.staking.impl.domain.model.NetworkInfo
import jp.co.soramitsu.staking.impl.domain.validations.balance.BalanceAccountRequiredValidation
import jp.co.soramitsu.staking.impl.domain.validations.balance.ManageStakingValidationFailure
import jp.co.soramitsu.staking.impl.domain.validations.balance.ManageStakingValidationPayload
import jp.co.soramitsu.staking.impl.domain.validations.welcome.WelcomeStakingMaxNominatorsValidation
import jp.co.soramitsu.staking.impl.domain.validations.welcome.WelcomeStakingValidationFailure
import jp.co.soramitsu.staking.impl.presentation.staking.alerts.model.AlertModel
import jp.co.soramitsu.staking.impl.presentation.staking.main.StakingViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.StakingViewStateOld
import jp.co.soramitsu.staking.impl.presentation.staking.main.di.StakingViewStateFactory
import jp.co.soramitsu.staking.impl.presentation.staking.main.model.StakingNetworkInfoModel
import jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios.StakingScenarioViewModel.Companion.WAITING_ICON
import jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios.StakingScenarioViewModel.Companion.WARNING_ICON
import jp.co.soramitsu.staking.impl.scenarios.relaychain.HOURS_IN_DAY
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn

class StakingRelaychainScenarioViewModel(
    private val stakingInteractor: StakingInteractor,
    private val scenarioInteractor: StakingRelayChainScenarioInteractor,
    private val resourceManager: ResourceManager,
    private val baseViewModel: BaseStakingViewModel,
    private val alertsInteractor: AlertsInteractor,
    private val stakingViewStateFactory: StakingViewStateFactory,
    private val storiesDataSource: StakingStoriesDataSource,
    stakingSharedState: StakingSharedState
) : StakingScenarioViewModel {

    override val enteredAmountFlow: MutableStateFlow<BigDecimal?> = MutableStateFlow(BigDecimal.ZERO)

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

    private val viewStatesCash: MutableMap<String, StakingViewStateOld> = mutableMapOf()

    override val stakingStateFlow: Flow<StakingState> =
        scenarioInteractor.stakingStateFlow().shareIn(baseViewModel.stakingStateScope, SharingStarted.Eagerly, 1)

    override val stakingViewStateFlowOld: Flow<StakingViewStateOld> =
        stakingStateFlow.distinctUntilChanged().map { stakingState ->
            val key = "${stakingState.accountId.toHexString()}:${stakingState.chain.id}"
            viewStatesCash.getOrPut(key) {
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
        }.shareIn(baseViewModel.stakingStateScope, SharingStarted.Eagerly, 1)

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

    override suspend fun getStakingViewStateFlow(): Flow<StakingViewState> {
        return emptyFlow()
    }

    override suspend fun networkInfo(): Flow<LoadingState<StakingNetworkInfoModel>> {
        return combine(
            scenarioInteractor.observeNetworkInfoState().map { it as NetworkInfo.RelayChain },
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
        }.mapList(::mapAlertToAlertModel).withLoading()
    }

    private fun mapAlertToAlertModel(alert: Alert): AlertModel {
        return when (alert) {
            Alert.ChangeValidators -> {
                AlertModel(
                    WARNING_ICON,
                    resourceManager.getString(R.string.staking_alert_change_validators),
                    resourceManager.getString(R.string.staking_nominator_status_alert_no_validators),
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

            Alert.SetValidators -> AlertModel(
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
