package jp.co.soramitsu.feature_staking_impl.presentation.confirm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.runtime.binding.MultiAddress
import jp.co.soramitsu.common.mixin.api.DefaultFailure
import jp.co.soramitsu.common.mixin.api.Retriable
import jp.co.soramitsu.common.mixin.api.RetryPayload
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.multipleSourceLiveData
import jp.co.soramitsu.common.utils.requireException
import jp.co.soramitsu.common.utils.toAddress
import jp.co.soramitsu.common.validation.DefaultFailureLevel
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.common.validation.unwrap
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.feature_account_api.presenatation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_staking_api.domain.model.StakingAccount
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.data.mappers.mapRewardDestinationModelToRewardDestination
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.model.RewardDestination
import jp.co.soramitsu.feature_staking_impl.domain.model.SetupStakingPayload
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationSettingsProviderFactory
import jp.co.soramitsu.feature_staking_impl.domain.setup.MaxFeeEstimator
import jp.co.soramitsu.feature_staking_impl.domain.setup.validations.StakingValidationFailure
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.StakingSharedState
import jp.co.soramitsu.feature_staking_impl.presentation.common.fee.FeeLoaderMixin
import jp.co.soramitsu.feature_staking_impl.presentation.common.mapAssetToAssetModel
import jp.co.soramitsu.feature_staking_impl.presentation.common.validation.stakingValidationFailure
import jp.co.soramitsu.feature_staking_impl.presentation.setup.RewardDestinationModel
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.planksFromAmount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.math.BigDecimal

private const val DESTINATION_SIZE_DP = 24

class ConfirmStakingViewModel(
    private val router: StakingRouter,
    private val interactor: StakingInteractor,
    private val addressIconGenerator: AddressIconGenerator,
    private val resourceManager: ResourceManager,
    private val validationSystem: ValidationSystem<SetupStakingPayload, StakingValidationFailure>,
    private val stakingSharedState: StakingSharedState,
    private val maxFeeEstimator: MaxFeeEstimator,
    private val feeLoaderMixin: FeeLoaderMixin.Presentation,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    private val recommendationSettingsProviderFactory: RecommendationSettingsProviderFactory
) : BaseViewModel(),
    Retriable,
    Validatable,
    FeeLoaderMixin by feeLoaderMixin,
    ExternalAccountActions by externalAccountActions {

    override val retryEvent: MutableLiveData<Event<RetryPayload>> = multipleSourceLiveData(
        feeLoaderMixin.retryEvent
    )

    override val validationFailureEvent = MutableLiveData<Event<DefaultFailure>>()

    private val assetFlow = interactor.currentAssetFlow()
        .share()

    val assetLiveData = assetFlow
        .map { mapAssetToAssetModel(it, resourceManager) }
        .flowOn(Dispatchers.Default)
        .asLiveData()

    val currentAccountModelLiveData = liveData(Dispatchers.Default) {
        emit(generateDestinationModel(interactor.getSelectedAccount()))
    }

    val nominationsLiveData = liveData(Dispatchers.Default) {
        val selectedCount = stakingSharedState.selectedValidators.first().size
        val maxValidatorsPerNominator = recommendationSettingsProviderFactory.get().maximumValidatorsPerNominator

        emit(resourceManager.getString(R.string.staking_confirm_nominations, selectedCount, maxValidatorsPerNominator))
    }

    val stakingAmount = stakingSharedState.amount.toString()

    val rewardDestinationLiveData = liveData(Dispatchers.Default) {
        val rewardDestination = stakingSharedState.rewardDestination

        emit(mapRewardDestinationToRewardDestinationModel(rewardDestination))
    }

    private val _showNextProgress = MutableLiveData(false)
    val showNextProgress: LiveData<Boolean> = _showNextProgress

    override fun validationWarningConfirmed() {
        sendTransactionIfValid(ignoreWarnings = true)
    }

    init {
        loadFee()
    }

    fun confirmClicked() {
        sendTransactionIfValid()
    }

    fun backClicked() {
        router.back()
    }

    fun originAccountClicked() {
        viewModelScope.launch {
            val account = interactor.getSelectedAccount()

            val payload = ExternalAccountActions.Payload(account.address, account.network.type)

            externalAccountActions.showExternalActions(payload)
        }
    }

    fun nominationsClicked() {
        router.openConfirmNominations()
    }

    private fun loadFee() {
        feeLoaderMixin.loadFee(
            viewModelScope,
            feeConstructor = { account, asset ->
                val token = asset.token

                maxFeeEstimator.estimateMaxSetupStakingFee(
                    originAddress = account.address,
                    tokenType = token.type,
                    amount = token.planksFromAmount(stakingSharedState.amount),
                    rewardDestination = stakingSharedState.rewardDestination,
                    nominations = prepareNominations()
                )
            },
            onRetryCancelled = ::backClicked
        )
    }

    private suspend fun mapRewardDestinationToRewardDestinationModel(
        rewardDestination: RewardDestination
    ): RewardDestinationModel {
        return when (rewardDestination) {
            is RewardDestination.Restake -> RewardDestinationModel.Restake
            is RewardDestination.Payout -> {
                val networkType = interactor.getSelectedNetworkType()
                val account = interactor.getAccount(rewardDestination.targetAccountId.toAddress(networkType))
                val addressModel = generateDestinationModel(account)

                RewardDestinationModel.Payout(addressModel)
            }
        }
    }

    private suspend fun prepareNominations(): List<MultiAddress> {
        return stakingSharedState.selectedValidators.first().map {
            MultiAddress.Id(it.accountIdHex.fromHex())
        }
    }

    private fun sendTransactionIfValid(
        ignoreWarnings: Boolean = false
    ) = requireFee { fee ->
        _showNextProgress.value = true

        viewModelScope.launch {
            val rewardDestination = mapRewardDestinationModelToRewardDestination(rewardDestinationLiveData.value!!)
            val tokenType = assetFlow.first().token.type

            val payload = SetupStakingPayload(
                amount = stakingSharedState.amount,
                tokenType = tokenType,
                originAddress = interactor.getSelectedAccount().address,
                maxFee = fee,
                rewardDestination = rewardDestination
            )

            val ignoreLevel = if (ignoreWarnings) DefaultFailureLevel.WARNING else null

            val validationResult = validationSystem.validate(payload, ignoreLevel)

            validationResult.unwrap(
                onValid = { sendTransaction(payload, tokenType) },
                onInvalid = {
                    _showNextProgress.value = false
                    validationFailureEvent.value = Event(stakingValidationFailure(payload, it, resourceManager))
                },
                onFailure = {
                    _showNextProgress.value = false
                    showValidationFailedToComplete()
                }
            )
        }
    }

    private fun sendTransaction(
        setupStakingPayload: SetupStakingPayload,
        tokenType: Token.Type
    ) = launch {
        val setupResult = interactor.setupStaking(
            originAddress = setupStakingPayload.originAddress,
            amount = setupStakingPayload.amount,
            tokenType = tokenType,
            rewardDestination = setupStakingPayload.rewardDestination,
            nominations = prepareNominations()
        )

        _showNextProgress.value = false

        if (setupResult.isSuccess) {
            showMessage(resourceManager.getString(R.string.staking_setup_sent_message))

            router.finishSetupStakingFlow()
        } else {
            showError(setupResult.requireException())
        }
    }

    private fun requireFee(block: (BigDecimal) -> Unit) = feeLoaderMixin.requireFee(
        block,
        onError = { title, message -> showError(title, message) }
    )

    private fun showValidationFailedToComplete() {
        retryEvent.value = Event(
            RetryPayload(
                title = resourceManager.getString(R.string.choose_amount_network_error),
                message = resourceManager.getString(R.string.choose_amount_error_balance),
                onRetry = ::sendTransactionIfValid
            )
        )
    }

    private suspend fun generateDestinationModel(account: StakingAccount): AddressModel {
        return addressIconGenerator.createAddressModel(account.address, DESTINATION_SIZE_DP, account.name)
    }
}
