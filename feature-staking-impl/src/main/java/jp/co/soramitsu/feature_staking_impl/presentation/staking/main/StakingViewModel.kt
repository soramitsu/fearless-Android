package jp.co.soramitsu.feature_staking_impl.presentation.staking.main

import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.domain.model.StoryGroup
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.presentation.StakingStoryModel
import jp.co.soramitsu.common.presentation.StoryElement
import jp.co.soramitsu.common.presentation.StoryGroupModel
import jp.co.soramitsu.common.presentation.flatMapLoading
import jp.co.soramitsu.common.presentation.mapLoading
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.childScope
import jp.co.soramitsu.common.utils.format
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.common.utils.withLoading
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.alerts.Alert
import jp.co.soramitsu.feature_staking_impl.domain.alerts.AlertsInteractor
import jp.co.soramitsu.feature_staking_impl.domain.model.NetworkInfo
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.ManageStakingValidationPayload
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.ManageStakingValidationSystem
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.staking.alerts.model.AlertModel
import jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.manageStakingActionValidationFailure
import jp.co.soramitsu.feature_staking_impl.presentation.staking.bond.select.SelectBondMorePayload
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.di.StakingViewStateFactory
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.model.StakingNetworkInfoModel
import jp.co.soramitsu.feature_staking_impl.presentation.staking.redeem.RedeemPayload
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.assetSelector.AssetSelectorMixin
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.assetSelector.WithAssetSelector
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

private const val CURRENT_ICON_SIZE = 40

private val WARNING_ICON = R.drawable.ic_warning_filled
private val WAITING_ICON = R.drawable.ic_time_24

class StakingViewModel(
    private val interactor: StakingInteractor,
    private val alertsInteractor: AlertsInteractor,
    private val addressIconGenerator: AddressIconGenerator,
    private val stakingViewStateFactory: StakingViewStateFactory,
    private val router: StakingRouter,
    private val resourceManager: ResourceManager,
    private val redeemValidationSystem: ManageStakingValidationSystem,
    private val bondMoreValidationSystem: ManageStakingValidationSystem,
    private val validationExecutor: ValidationExecutor,
    private val stakingUpdateSystem: UpdateSystem,
    private val assetSelectorMixinFactory: AssetSelectorMixin.Presentation.Factory,
) : BaseViewModel(),
    WithAssetSelector,
    Validatable by validationExecutor {

    override val assetSelectorMixin = assetSelectorMixinFactory.create(scope = this)

    private val stakingStateScope = viewModelScope.childScope(supervised = true)

    private val selectionState = interactor.selectionStateFlow()
        .share()

    private val loadingStakingState = selectionState
        .withLoading { (account, assetWithToken) ->
            interactor.selectedAccountStakingStateFlow(account, assetWithToken)
        }.share()

    val stakingViewStateFlow = loadingStakingState
        .onEach { stakingStateScope.coroutineContext.cancelChildren() }
        .mapLoading(::transformStakingState)
        .inBackground()
        .share()

    private val selectedChain = interactor.selectedChainFlow()
        .share()

    val networkInfoStateLiveData = selectionState
        .distinctUntilChanged()
        .withLoading {
            val chain = it.second.chain
            val stakingType = it.second.asset.staking
            interactor.observeNetworkInfoState(chain.id, stakingType)
                .combine(assetSelectorMixin.selectedAssetFlow) { networkInfo, asset ->
                    transformNetworkInfo(asset, networkInfo)
                }
        }
        .inBackground()
        .asLiveData()

    val stories = interactor.stakingStoriesFlow()
        .map { it.map(::transformStories) }
        .asLiveData()

    val currentAddressModelLiveData = currentAddressModelFlow().asLiveData()

    val networkInfoTitle = selectedChain
        .map { it.name }
        .share()

    fun storyClicked(group: StoryGroupModel) {
        if (group.stories.isNotEmpty()) {
            router.openStory(group)
        }
    }

    val alertsFlow = loadingStakingState
        .flatMapLoading {
            alertsInteractor.getAlertsFlow(it)
                .mapList(::mapAlertToAlertModel)
        }
        .inBackground()
        .asLiveData()

    init {
        stakingUpdateSystem.start()
            .launchIn(this)
    }

    fun avatarClicked() {
        router.openChangeAccountFromStaking()
    }

    private fun mapAlertToAlertModel(alert: Alert): AlertModel {
        return when (alert) {
            Alert.ChangeValidators -> {
                AlertModel(
                    WARNING_ICON,
                    resourceManager.getString(R.string.staking_alert_change_validators),
                    resourceManager.getString(R.string.staking_nominator_status_alert_no_validators),
                    AlertModel.Type.CallToAction { router.openCurrentValidators() }
                )
            }
            is Alert.RedeemTokens -> {
                AlertModel(
                    WARNING_ICON,
                    resourceManager.getString(R.string.staking_alert_redeem_title),
                    formatAlertTokenAmount(alert.amount, alert.token),
                    AlertModel.Type.CallToAction(::redeemAlertClicked)
                )
            }
            is Alert.BondMoreTokens -> {
                val existentialDepositDisplay = formatAlertTokenAmount(alert.minimalStake, alert.token)

                AlertModel(
                    WARNING_ICON,
                    resourceManager.getString(R.string.staking_alert_bond_more_title),
                    resourceManager.getString(R.string.staking_alert_bond_more_message, existentialDepositDisplay),
                    AlertModel.Type.CallToAction(::bondMoreAlertClicked)
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
                AlertModel.Type.CallToAction { router.openCurrentValidators() }
            )
        }
    }

    private fun formatAlertTokenAmount(amount: BigDecimal, token: Token): String {
        val formattedFiat = token.fiatAmount(amount)?.formatAsCurrency(token.fiatSymbol)
        val formattedAmount = amount.formatTokenAmount(token.configuration)

        return buildString {
            append(formattedAmount)

            formattedFiat?.let {
                append(" ($it)")
            }
        }
    }

    private fun bondMoreAlertClicked() = requireValidManageStakingAction(bondMoreValidationSystem) {
        val bondMorePayload = SelectBondMorePayload(overrideFinishAction = StakingRouter::returnToMain)

        router.openBondMore(bondMorePayload)
    }

    private fun redeemAlertClicked() = requireValidManageStakingAction(redeemValidationSystem) {
        val redeemPayload = RedeemPayload(overrideFinishAction = StakingRouter::back)

        router.openRedeem(redeemPayload)
    }

    private fun requireValidManageStakingAction(
        validationSystem: ManageStakingValidationSystem,
        action: () -> Unit,
    ) = launch {
        val stakingState = (loadingStakingState.first() as? LoadingState.Loaded)?.data
        val stashState = stakingState as? StakingState.Stash ?: return@launch

        validationExecutor.requireValid(
            validationSystem,
            ManageStakingValidationPayload(stashState),
            validationFailureTransformer = { manageStakingActionValidationFailure(it, resourceManager) }
        ) {
            action()
        }
    }

    private fun transformStakingState(accountStakingState: StakingState) = when (accountStakingState) {
        is StakingState.Stash.Nominator -> stakingViewStateFactory.createNominatorViewState(
            accountStakingState,
            assetSelectorMixin.selectedAssetFlow,
            stakingStateScope,
            ::showError
        )

        is StakingState.Stash.None -> stakingViewStateFactory.createStashNoneState(
            assetSelectorMixin.selectedAssetFlow,
            accountStakingState,
            stakingStateScope,
            ::showError
        )

        is StakingState.NonStash -> stakingViewStateFactory.createWelcomeViewState(
            assetSelectorMixin.selectedAssetFlow,
            stakingStateScope,
            ::showError
        )

        is StakingState.Stash.Validator -> stakingViewStateFactory.createValidatorViewState(
            accountStakingState,
            assetSelectorMixin.selectedAssetFlow,
            stakingStateScope,
            ::showError
        )
        is StakingState.Parachain.Collator -> stakingViewStateFactory.createCollatorViewState()
        is StakingState.Parachain.Delegator -> stakingViewStateFactory.createDelegatorViewState()
        is StakingState.Parachain.None -> stakingViewStateFactory.createParachainWelcomeViewState(
            assetSelectorMixin.selectedAssetFlow,
            stakingStateScope,
            ::showError
        )
    }

    private fun transformStories(story: StoryGroup.Staking): StakingStoryModel = with(story) {
        val elements = elements.map { StoryElement.Staking(it.titleRes, it.bodyRes, it.url) }
        StakingStoryModel(titleRes, iconSymbol, elements)
    }

    private fun transformNetworkInfo(asset: Asset, networkInfo: NetworkInfo): StakingNetworkInfoModel {
        val minimumStake = asset.token.amountFromPlanks(networkInfo.minimumStake)
        val minimumStakeFormatted = minimumStake.formatTokenAmount(asset.token.configuration)

        val minimumStakeFiat = asset.token.fiatAmount(minimumStake)?.formatAsCurrency(asset.token.fiatSymbol)

        val lockupPeriod = resourceManager.getQuantityString(R.plurals.staking_main_lockup_period_value, networkInfo.lockupPeriodInDays)
            .format(networkInfo.lockupPeriodInDays)

        return when (networkInfo) {
            is NetworkInfo.RelayChain -> {
                val totalStake = asset.token.amountFromPlanks(networkInfo.totalStake)
                val totalStakeFormatted = totalStake.formatTokenAmount(asset.token.configuration)

                val totalStakeFiat = asset.token.fiatAmount(totalStake)?.formatAsCurrency(asset.token.fiatSymbol)

                StakingNetworkInfoModel.RelayChain(
                    lockupPeriod,
                    minimumStakeFormatted,
                    minimumStakeFiat,
                    totalStakeFormatted,
                    totalStakeFiat,
                    networkInfo.nominatorsCount.format()
                )
            }
            is NetworkInfo.Parachain -> {
                StakingNetworkInfoModel.Parachain(lockupPeriod, minimumStakeFormatted, minimumStakeFiat)
            }
        }
    }

    private fun currentAddressModelFlow(): Flow<AddressModel> {
        return interactor.selectedAccountProjectionFlow().map {
            addressIconGenerator.createAddressModel(it.address, CURRENT_ICON_SIZE, it.name)
        }
    }
}
