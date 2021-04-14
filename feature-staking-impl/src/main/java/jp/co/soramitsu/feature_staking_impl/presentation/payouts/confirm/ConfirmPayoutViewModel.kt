package jp.co.soramitsu.feature_staking_impl.presentation.payouts.confirm

import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.DefaultFailure
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.common.utils.toAddress
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.feature_account_api.presenatation.account.AddressDisplayUseCase
import jp.co.soramitsu.feature_account_api.presenatation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_staking_api.domain.model.RewardDestination
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.validations.payout.MakePayoutPayload
import jp.co.soramitsu.feature_staking_impl.domain.validations.payout.PayoutValidationFailure
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.fee.FeeLoaderMixin
import jp.co.soramitsu.feature_staking_impl.presentation.payouts.confirm.model.ConfirmPayoutPayload
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatWithDefaultPrecision
import kotlinx.coroutines.flow.map

class ConfirmPayoutViewModel(
    private val interactor: StakingInteractor,
    private val router: StakingRouter,
    private val payload: ConfirmPayoutPayload,
    private val addressModelGenerator: AddressIconGenerator,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    private val feeLoaderMixin: FeeLoaderMixin.Presentation,
    private val addressDisplayUseCase: AddressDisplayUseCase,
    private val validationSystem: ValidationSystem<MakePayoutPayload, PayoutValidationFailure>,
    private val resourceManager: ResourceManager,
) : BaseViewModel(),
    ExternalAccountActions.Presentation by externalAccountActions,
    FeeLoaderMixin by feeLoaderMixin,
    Validatable {

    private val assetFlow = interactor.currentAssetFlow()
        .share()

    private val stakingStateFlow = interactor.selectedAccountStakingStateFlow()
        .share()

    val totalRewardDisplay = assetFlow.map {
        val token = it.token
        val inToken = payload.totalReward.formatWithDefaultPrecision(token.type)
        val inFiat = token.fiatAmount(payload.totalReward)?.formatAsCurrency()

        inToken to inFiat
    }
        .inBackground()
        .asLiveData()

    val rewardDestinationModel = stakingStateFlow.map { stakingState ->
        require(stakingState is StakingState.Stash)

        val networkType = stakingState.accountAddress.networkType()

        val destinationAddress = when (val rewardDestination = interactor.getRewardDestination(stakingState)) {
            RewardDestination.Restake -> stakingState.accountAddress
            is RewardDestination.Payout -> rewardDestination.targetAccountId.toAddress(networkType)
        }

        val destinationAddressDisplay = addressDisplayUseCase(destinationAddress)

        addressModelGenerator.createAddressModel(destinationAddress, AddressIconGenerator.SIZE_SMALL, destinationAddressDisplay)
    }
        .inBackground()
        .asLiveData()

    val controllerModel = stakingStateFlow.map { stakingState ->
        val controllerAddress = stakingState.accountAddress
        val controllerDisplay = addressDisplayUseCase(controllerAddress)

        addressModelGenerator.createAddressModel(controllerAddress, AddressIconGenerator.SIZE_SMALL, controllerDisplay)
    }
        .inBackground()
        .asLiveData()

    override val validationFailureEvent = MutableLiveData<Event<DefaultFailure>>()

    override fun validationWarningConfirmed() {
        // TODO
    }

    fun controllerClicked() {
        maybeShowExternalActions { controllerModel.value?.address }
    }

    fun rewardDestinationClicked() {
        maybeShowExternalActions { rewardDestinationModel.value?.address }
    }

    private fun maybeShowExternalActions(addressProducer: () -> String?) {
        val address = addressProducer() ?: return

        externalAccountActions.showExternalActions(ExternalAccountActions.Payload.fromAddress(address))
    }
}
