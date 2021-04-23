package jp.co.soramitsu.feature_staking_impl.presentation.confirm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.runtime.binding.MultiAddress
import jp.co.soramitsu.common.mixin.api.Retriable
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.requireException
import jp.co.soramitsu.common.utils.toAddress
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.common.validation.progressConsumer
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.feature_account_api.presenatation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_staking_api.domain.model.RewardDestination
import jp.co.soramitsu.feature_staking_api.domain.model.StakingAccount
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationSettingsProviderFactory
import jp.co.soramitsu.feature_staking_impl.domain.setup.MaxFeeEstimator
import jp.co.soramitsu.feature_staking_impl.domain.validations.setup.SetupStakingPayload
import jp.co.soramitsu.feature_staking_impl.domain.validations.setup.SetupStakingValidationFailure
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingSharedState
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
    private val validationSystem: ValidationSystem<SetupStakingPayload, SetupStakingValidationFailure>,
    private val setupStakingSharedState: SetupStakingSharedState,
    private val maxFeeEstimator: MaxFeeEstimator,
    private val feeLoaderMixin: FeeLoaderMixin.Presentation,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    private val validationExecutor: ValidationExecutor,
    private val recommendationSettingsProviderFactory: RecommendationSettingsProviderFactory,
) : BaseViewModel(),
    Retriable,
    Validatable by validationExecutor,
    FeeLoaderMixin by feeLoaderMixin,
    ExternalAccountActions by externalAccountActions {

    private val currentProcessState = setupStakingSharedState.get<SetupStakingProcess.Confirm>()

    private val assetFlow = interactor.assetFlow(currentProcessState.stashSetup.controllerAddress)
        .share()

    val assetLiveData = assetFlow
        .map { mapAssetToAssetModel(it, resourceManager) }
        .flowOn(Dispatchers.Default)
        .asLiveData()

    val currentAccountModelLiveData = liveData(Dispatchers.Default) {
        emit(generateDestinationModel(interactor.getSelectedAccount()))
    }

    val nominationsLiveData = liveData(Dispatchers.Default) {
        val selectedCount = currentProcessState.validators.size
        val maxValidatorsPerNominator = recommendationSettingsProviderFactory.get().maximumValidatorsPerNominator

        emit(resourceManager.getString(R.string.staking_confirm_nominations, selectedCount, maxValidatorsPerNominator))
    }

    val stakingAmount = currentProcessState.amount.toString()

    val rewardDestinationLiveData = liveData(Dispatchers.Default) {
        val rewardDestination = currentProcessState.stashSetup.rewardDestination

        emit(mapRewardDestinationToRewardDestinationModel(rewardDestination))
    }

    private val _showNextProgress = MutableLiveData(false)
    val showNextProgress: LiveData<Boolean> = _showNextProgress

    init {
        loadFee()
    }

    fun confirmClicked() {
        sendTransactionIfValid()
    }

    fun backClicked() {
        setupStakingSharedState.set(currentProcessState.previous())

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
            feeConstructor = { asset ->
                val token = asset.token

                maxFeeEstimator.estimateMaxSetupStakingFee(
                    tokenType = token.type,
                    amount = token.planksFromAmount(currentProcessState.amount),
                    stashSetup = currentProcessState.stashSetup,
                    nominations = prepareNominations(),
                )
            },
            onRetryCancelled = ::backClicked
        )
    }

    private suspend fun mapRewardDestinationToRewardDestinationModel(
        rewardDestination: RewardDestination,
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

    private fun prepareNominations(): List<MultiAddress> {
        return currentProcessState.validators.map {
            MultiAddress.Id(it.accountIdHex.fromHex())
        }
    }

    private fun sendTransactionIfValid() = requireFee { fee ->
        launch {
            val tokenType = assetFlow.first().token.type

            val payload = SetupStakingPayload(
                tokenType = tokenType,
                maxFee = fee,
                stashSetup = currentProcessState.stashSetup,
                amount = currentProcessState.amount
            )

            validationExecutor.requireValid(
                validationSystem = validationSystem,
                payload = payload,
                validationFailureTransformer = { stakingValidationFailure(payload, it, resourceManager) },
                progressConsumer = _showNextProgress.progressConsumer()
            ) {
                sendTransaction(payload, tokenType)
            }
        }
    }

    private fun sendTransaction(
        setupStakingPayload: SetupStakingPayload,
        tokenType: Token.Type,
    ) = launch {
        val setupResult = interactor.setupStaking(
            amount = setupStakingPayload.amount,
            tokenType = tokenType,
            nominations = prepareNominations(),
            stashSetup = setupStakingPayload.stashSetup
        )

        _showNextProgress.value = false

        if (setupResult.isSuccess) {
            showMessage(resourceManager.getString(R.string.staking_setup_sent_message))

            setupStakingSharedState.set(currentProcessState.finish())

            router.returnToMain()
        } else {
            showError(setupResult.requireException())
        }
    }

    private fun requireFee(block: (BigDecimal) -> Unit) = feeLoaderMixin.requireFee(
        block,
        onError = { title, message -> showError(title, message) }
    )

    private suspend fun generateDestinationModel(account: StakingAccount): AddressModel {
        return addressIconGenerator.createAddressModel(account.address, DESTINATION_SIZE_DP, account.name)
    }
}
