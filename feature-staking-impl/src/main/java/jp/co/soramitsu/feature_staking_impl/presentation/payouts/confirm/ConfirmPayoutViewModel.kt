package jp.co.soramitsu.feature_staking_impl.presentation.payouts.confirm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.base.TitleAndMessage
import jp.co.soramitsu.common.data.network.BlockExplorerUrlBuilder
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.requireException
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.common.validation.progressConsumer
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.addressByte
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.feature_account_api.presentation.account.AddressDisplayUseCase
import jp.co.soramitsu.feature_account_api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_staking_api.domain.model.RewardDestination
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.data.model.Payout
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.payout.PayoutInteractor
import jp.co.soramitsu.feature_staking_impl.domain.validations.payout.MakePayoutPayload
import jp.co.soramitsu.feature_staking_impl.domain.validations.payout.PayoutValidationFailure
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.payouts.confirm.model.ConfirmPayoutPayload
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.fee.FeeLoaderMixin
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.fee.requireFee
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.getSupportedExplorers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ConfirmPayoutViewModel(
    private val interactor: StakingInteractor,
    private val relayChainInteractor: StakingRelayChainScenarioInteractor,
    private val payoutInteractor: PayoutInteractor,
    private val router: StakingRouter,
    private val payload: ConfirmPayoutPayload,
    private val addressModelGenerator: AddressIconGenerator,
    private val chainRegistry: ChainRegistry,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    private val feeLoaderMixin: FeeLoaderMixin.Presentation,
    private val addressDisplayUseCase: AddressDisplayUseCase,
    private val validationSystem: ValidationSystem<MakePayoutPayload, PayoutValidationFailure>,
    private val validationExecutor: ValidationExecutor,
    private val resourceManager: ResourceManager,
) : BaseViewModel(),
    ExternalAccountActions.Presentation by externalAccountActions,
    FeeLoaderMixin by feeLoaderMixin,
    Validatable by validationExecutor {

    private val assetFlow = interactor.currentAssetFlow()
        .share()

    private val stakingStateFlow = relayChainInteractor.selectedAccountStakingStateFlow()
        .share()

    private val payouts = payload.payouts.map { Payout(it.validatorInfo.address, it.era, it.amountInPlanks) }

    private val _showNextProgress = MutableLiveData(false)
    val showNextProgress: LiveData<Boolean> = _showNextProgress

    val totalRewardDisplay = assetFlow.map {
        val token = it.token
        val totalReward = token.amountFromPlanks(payload.totalRewardInPlanks)
        val inToken = totalReward.formatTokenAmount(token.configuration)
        val inFiat = token.fiatAmount(totalReward)?.formatAsCurrency(token.fiatSymbol)

        inToken to inFiat
    }
        .inBackground()
        .asLiveData()

    val rewardDestinationModel = stakingStateFlow.map { stakingState ->
        require(stakingState is StakingState.Stash)

        val addressByte = stakingState.accountAddress.addressByte()

        val destinationAddress = when (val rewardDestination = relayChainInteractor.getRewardDestination(stakingState)) {
            RewardDestination.Restake -> stakingState.accountAddress
            is RewardDestination.Payout -> rewardDestination.targetAccountId.toAddress(addressByte)
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

    init {
        loadFee()
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

    private fun sendTransactionIfValid() = feeLoaderMixin.requireFee(this) { fee ->
        launch {
            val tokenType = assetFlow.first().token.configuration
            val accountAddress = stakingStateFlow.first().accountAddress
            val amount = tokenType.amountFromPlanks(payload.totalRewardInPlanks)

            val payoutStakersPayloads = payouts.map { MakePayoutPayload.PayoutStakersPayload(it.era, it.validatorAddress) }

            val makePayoutPayload = MakePayoutPayload(accountAddress, fee, amount, tokenType, payoutStakersPayloads)

            validationExecutor.requireValid(
                validationSystem = validationSystem,
                payload = makePayoutPayload,
                validationFailureTransformer = ::payloadValidationFailure,
                progressConsumer = _showNextProgress.progressConsumer()
            ) {
                sendTransaction(makePayoutPayload)
            }
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
            feeConstructor = {
                val address = stakingStateFlow.first().accountAddress

                payoutInteractor.estimatePayoutFee(address, payouts)
            },
            onRetryCancelled = ::backClicked
        )
    }

    private fun payloadValidationFailure(reason: PayoutValidationFailure): TitleAndMessage {
        val (titleRes, messageRes) = when (reason) {
            PayoutValidationFailure.CannotPayFee -> R.string.common_not_enough_funds_title to R.string.common_not_enough_funds_message
            PayoutValidationFailure.UnprofitablePayout -> R.string.common_confirmation_title to R.string.staking_warning_tiny_payout
        }

        return resourceManager.getString(titleRes) to resourceManager.getString(messageRes)
    }

    private fun maybeShowExternalActions(addressProducer: () -> String?) = launch {
        val address = addressProducer() ?: return@launch
        val chainId = assetFlow.first().token.configuration.chainId
        val chain = chainRegistry.getChain(chainId)
        val supportedExplorers = chain.explorers.getSupportedExplorers(BlockExplorerUrlBuilder.Type.ACCOUNT, address)
        val externalActionsPayload = ExternalAccountActions.Payload(
            value = address,
            chainId = chainId,
            chainName = chain.name,
            explorers = supportedExplorers
        )

        externalAccountActions.showExternalActions(externalActionsPayload)
    }
}
