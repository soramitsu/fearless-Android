package jp.co.soramitsu.feature_staking_impl.presentation.staking.main

import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.presentation.map
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.childScope
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.withLoading
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.feature_staking_api.domain.model.StakingAccount
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_api.domain.model.StakingStory
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.Alert
import jp.co.soramitsu.feature_staking_impl.domain.AlertsInteractor
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.model.NetworkInfo
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.ManageStakingValidationPayload
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.ManageStakingValidationSystem
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.staking.alerts.model.AlertModel
import jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.manageStakingActionValidationFailure
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.di.StakingViewStateFactory
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.model.StakingNetworkInfoModel
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.model.StakingStoryModel
import jp.co.soramitsu.feature_staking_impl.presentation.staking.redeem.RedeemPayload
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

private const val CURRENT_ICON_SIZE = 40

private val WARNING_ICON = R.drawable.ic_warning_filled

class StakingViewModel(
    private val interactor: StakingInteractor,
    private val alertsInteractor: AlertsInteractor,
    private val addressIconGenerator: AddressIconGenerator,
    private val stakingViewStateFactory: StakingViewStateFactory,
    private val router: StakingRouter,
    private val resourceManager: ResourceManager,
    private val redeemValidationSystem: ManageStakingValidationSystem,
    private val validationExecutor: ValidationExecutor,
) : BaseViewModel(),
    Validatable by validationExecutor {

    private val currentAssetFlow = interactor.currentAssetFlow()
        .share()

    private val stakingStateScope = viewModelScope.childScope(supervised = true)

    private val stakingState = interactor.selectedAccountStakingStateFlow()
        .share()

    val stakingViewStateFlow = stakingState
        .onEach { stakingStateScope.coroutineContext.cancelChildren() }
        .map { transformStakingState(it) }
        .inBackground()
        .share()

    val networkInfoStateLiveData = interactor.selectedNetworkTypeFLow()
        .distinctUntilChanged()
        .withLoading { networkType ->
            interactor.observeNetworkInfoState(networkType).combine(currentAssetFlow) { networkInfo, asset ->
                transformNetworkInfo(asset, networkInfo)
            }
        }
        .inBackground()
        .asLiveData()

    val stories = interactor.stakingStoriesFlow()
        .map { it.map(::transformStories) }
        .asLiveData()

    val currentAddressModelLiveData = currentAddressModelFlow().asLiveData()

    fun avatarClicked() {
        router.openChangeAccountFromStaking()
    }

    val networkInfoTitle = currentAssetFlow
        .map { mapAssetToNetworkInfoTitle(it) }
        .asLiveData()

    fun storyClicked(story: StakingStoryModel) {
        if (story.elements.isNotEmpty()) {
            router.openStory(story)
        }
    }

    val alertsFlow = stakingState
        .withLoading(alertsInteractor::getAlertsFlow)
        .map { loadingState -> loadingState.map { alerts -> alerts.map(::mapAlertToAlertModel) } }
        .asLiveData()

    private fun mapAlertToAlertModel(alert: Alert): AlertModel {
        return when (alert) {
            Alert.Election -> {
                AlertModel(
                    R.drawable.ic_time_24,
                    resourceManager.getString(R.string.staking_alert_election),
                    resourceManager.getString(R.string.staking_alert_start_election_extra_message),
                    AlertModel.Type.Warning
                )
            }
            Alert.ChangeValidators -> {
                AlertModel(
                    WARNING_ICON,
                    resourceManager.getString(R.string.staking_alert_change_validators),
                    resourceManager.getString(R.string.staking_alert_change_validators_extra_message),
                    AlertModel.Type.CallToAction { router.openCurrentValidators() }
                )
            }
            is Alert.RedeemTokens -> {
                val formattedFiat = alert.token.fiatAmount(alert.amount)?.formatAsCurrency()
                val formattedAmount = alert.amount.formatTokenAmount(alert.token.type)

                val extraMessage = buildString {
                    append(formattedAmount)

                    formattedFiat?.let {
                        append(" ($it)")
                    }
                }

                AlertModel(
                    WARNING_ICON,
                    resourceManager.getString(R.string.staking_alert_redeem_title),
                    extraMessage,
                    AlertModel.Type.CallToAction(::redeemAlertClicked)
                )
            }
        }
    }

    private fun redeemAlertClicked() = launch {
        val stashState = stakingState.first() as? StakingState.Stash ?: return@launch

        validationExecutor.requireValid(
            redeemValidationSystem,
            ManageStakingValidationPayload(stashState),
            validationFailureTransformer = { manageStakingActionValidationFailure(it, resourceManager) }
        ) {
            val redeemPayload = RedeemPayload(overrideFinishAction = StakingRouter::back)

            router.openRedeem(redeemPayload)
        }
    }

    private fun transformStakingState(accountStakingState: StakingState) = when (accountStakingState) {
        is StakingState.Stash.Nominator -> stakingViewStateFactory.createNominatorViewState(
            accountStakingState,
            currentAssetFlow,
            stakingStateScope,
            ::showError
        )

        is StakingState.Stash.None -> stakingViewStateFactory.createStashNoneState(currentAssetFlow, accountStakingState, stakingStateScope, ::showError)

        is StakingState.NonStash -> stakingViewStateFactory.createWelcomeViewState(currentAssetFlow, accountStakingState, stakingStateScope, ::showError)

        is StakingState.Stash.Validator -> stakingViewStateFactory.createValidatorViewState(
            accountStakingState,
            currentAssetFlow,
            stakingStateScope,
            ::showError
        )
    }

    private fun transformStories(story: StakingStory): StakingStoryModel = with(story) {
        val elements = elements.map { StakingStoryModel.Element(it.titleRes, it.bodyRes, it.url) }
        StakingStoryModel(titleRes, iconSymbol, elements)
    }

    private fun transformNetworkInfo(asset: Asset, networkInfo: NetworkInfo): StakingNetworkInfoModel {
        val totalStake = asset.token.amountFromPlanks(networkInfo.totalStake)
        val totalStakeFormatted = totalStake.formatTokenAmount(asset.token.type)

        val totalStakeFiat = asset.token.fiatAmount(totalStake)?.formatAsCurrency()

        val minimumStake = asset.token.amountFromPlanks(networkInfo.minimumStake)
        val minimumStakeFormatted = minimumStake.formatTokenAmount(asset.token.type)

        val minimumStakeFiat = asset.token.fiatAmount(minimumStake)?.formatAsCurrency()

        val lockupPeriod = resourceManager.getQuantityString(R.plurals.staking_main_lockup_period_value, networkInfo.lockupPeriodInDays)
            .format(networkInfo.lockupPeriodInDays)

        return with(networkInfo) {
            StakingNetworkInfoModel(
                lockupPeriod,
                minimumStakeFormatted,
                minimumStakeFiat,
                totalStakeFormatted,
                totalStakeFiat,
                nominatorsCount.toString()
            )
        }
    }

    private fun mapAssetToNetworkInfoTitle(asset: Asset): String {
        return resourceManager.getString(R.string.staking_main_network_title, asset.token.type.networkType.readableName)
    }

    private fun currentAddressModelFlow(): Flow<AddressModel> {
        return interactor.selectedAccountFlow()
            .map { generateAddressModel(it, CURRENT_ICON_SIZE) }
    }

    private suspend fun generateAddressModel(account: StakingAccount, sizeInDp: Int): AddressModel {
        return addressIconGenerator.createAddressModel(account.address, sizeInDp, account.name)
    }
}
