package jp.co.soramitsu.feature_staking_impl.presentation.payouts.confirm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.DefaultFailure
import jp.co.soramitsu.common.mixin.api.RetryPayload
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.multipleSourceLiveData
import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.common.utils.requireException
import jp.co.soramitsu.common.utils.toAddress
import jp.co.soramitsu.common.validation.DefaultFailureLevel
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.common.validation.unwrap
import jp.co.soramitsu.feature_account_api.presenatation.account.AddressDisplayUseCase
import jp.co.soramitsu.feature_account_api.presenatation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_staking_api.domain.model.RewardDestination
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.data.model.Payout
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.payout.PayoutInteractor
import jp.co.soramitsu.feature_staking_impl.domain.validations.payout.MakePayoutPayload
import jp.co.soramitsu.feature_staking_impl.domain.validations.payout.PayoutValidationFailure
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.fee.FeeLoaderMixin
import jp.co.soramitsu.feature_staking_impl.presentation.common.fee.requireFee
import jp.co.soramitsu.feature_staking_impl.presentation.payouts.confirm.model.ConfirmPayoutPayload
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ConfirmPayoutViewModel(
    private val interactor: StakingInteractor,
    private val payoutInteractor: PayoutInteractor,
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

    private val payouts = payload.payouts.map { Payout(it.validatorInfo.address, it.era, it.amountInPlanks) }

    private val _showNextProgress = MutableLiveData(false)
    val showNextProgress: LiveData<Boolean> = _showNextProgress

    override val retryEvent: MutableLiveData<Event<RetryPayload>> = multipleSourceLiveData(
        feeLoaderMixin.retryEvent
    )

    val totalRewardDisplay = assetFlow.map {
        val token = it.token
        val totalReward = token.amountFromPlanks(payload.totalRewardInPlanks)
        val inToken = totalReward.formatTokenAmount(token.type, precision = 7)
        val inFiat = token.fiatAmount(totalReward)?.formatAsCurrency()

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

    val initiatorAddressModel = stakingStateFlow.map { stakingState ->
        val initiatorAddress = stakingState.accountAddress
        val initiatorDisplay = addressDisplayUseCase(initiatorAddress)

        addressModelGenerator.createAddressModel(initiatorAddress, AddressIconGenerator.SIZE_SMALL, initiatorDisplay)
    }
        .inBackground()
        .asLiveData()

    override val validationFailureEvent = MutableLiveData<Event<DefaultFailure>>()

    init {
        loadFee()
    }

    override fun validationWarningConfirmed() {
        sendTransactionIfValid(ignoreWarnings = true)
    }

    fun controllerClicked() {
        maybeShowExternalActions { initiatorAddressModel.value?.address }
    }

    fun submitClicked() {
        sendTransactionIfValid()
    }

    fun rewardDestinationClicked() {
        maybeShowExternalActions { rewardDestinationModel.value?.address }
    }

    fun backClicked() {
        router.back()
    }

    private fun sendTransactionIfValid(
        ignoreWarnings: Boolean = false,
    ) = feeLoaderMixin.requireFee(this) { fee ->
        _showNextProgress.value = true

        viewModelScope.launch {
            val tokenType = assetFlow.first().token.type
            val accountAddress = stakingStateFlow.first().accountAddress
            val amount = tokenType.amountFromPlanks(payload.totalRewardInPlanks)

            val payoutStakersPayloads = payouts.map { MakePayoutPayload.PayoutStakersPayload(it.era, it.validatorAddress) }

            val ignoreLevel = if (ignoreWarnings) DefaultFailureLevel.WARNING else null

            val makePayoutPayload = MakePayoutPayload(accountAddress, fee, amount, tokenType, payoutStakersPayloads)
            val validationResult = validationSystem.validate(makePayoutPayload, ignoreLevel)

            validationResult.unwrap(
                onValid = { sendTransaction(makePayoutPayload) },
                onFailure = {
                    _showNextProgress.value = false
                    showValidationFailedToComplete()
                },
                onInvalid = {
                    _showNextProgress.value = false
                    validationFailureEvent.value = Event(payloadValidationFailure(it))
                }
            )
        }
    }

    private fun sendTransaction(payload: MakePayoutPayload) = launch {
        val result = payoutInteractor.makePayouts(payload)

        _showNextProgress.value = false

        if (result.isSuccess) {
            showMessage(resourceManager.getString(R.string.make_payout_transaction_sent))

            router.returnToMain()
        } else {
            showError(result.requireException())
        }
    }

    private fun loadFee() {
        feeLoaderMixin.loadFee(
            viewModelScope,
            feeConstructor = { asset ->
                val address = stakingStateFlow.first().accountAddress

                val feeInPlanks = payoutInteractor.estimatePayoutFee(address, payouts)

                asset.token.amountFromPlanks(feeInPlanks)
            },
            onRetryCancelled = ::backClicked
        )
    }

    private fun payloadValidationFailure(status: ValidationStatus.NotValid<PayoutValidationFailure>): DefaultFailure {
        val (titleRes, messageRes) = when (status.reason) {
            PayoutValidationFailure.CannotPayFee -> R.string.common_not_enough_funds_title to R.string.common_not_enough_funds_message
            PayoutValidationFailure.UnprofitablePayout -> R.string.common_are_you_sure to R.string.staking_non_profitable_payout
        }

        return DefaultFailure(
            level = status.level,
            title = resourceManager.getString(titleRes),
            message = resourceManager.getString(messageRes)
        )
    }

    private fun showValidationFailedToComplete() {
        retryEvent.value = Event(
            RetryPayload(
                title = resourceManager.getString(R.string.choose_amount_network_error),
                message = resourceManager.getString(R.string.choose_amount_error_balance),
                onRetry = ::sendTransactionIfValid
            )
        )
    }

    private fun maybeShowExternalActions(addressProducer: () -> String?) {
        val address = addressProducer() ?: return

        externalAccountActions.showExternalActions(ExternalAccountActions.Payload.fromAddress(address))
    }
}
