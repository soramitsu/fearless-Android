package jp.co.soramitsu.staking.impl.presentation.staking.rewardDestination.confirm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.BlockExplorerUrlBuilder
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.requireException
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.common.validation.progressConsumer
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.account.api.presentation.account.AddressDisplayUseCase
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.staking.api.domain.model.RewardDestination
import jp.co.soramitsu.staking.api.domain.model.StakingAccount
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.data.mappers.mapRewardDestinationModelToRewardDestination
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.staking.rewardDestination.ChangeRewardDestinationInteractor
import jp.co.soramitsu.staking.impl.domain.validations.rewardDestination.RewardDestinationValidationPayload
import jp.co.soramitsu.staking.impl.domain.validations.rewardDestination.RewardDestinationValidationSystem
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.rewardDestination.RewardDestinationModel
import jp.co.soramitsu.staking.impl.presentation.staking.rewardDestination.confirm.parcel.ConfirmRewardDestinationPayload
import jp.co.soramitsu.staking.impl.presentation.staking.rewardDestination.confirm.parcel.RewardDestinationParcelModel
import jp.co.soramitsu.staking.impl.presentation.staking.rewardDestination.select.rewardDestinationValidationFailure
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.wallet.api.data.mappers.mapFeeToFeeModel
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeStatus
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.getSupportedExplorers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConfirmRewardDestinationViewModel @Inject constructor(
    private val router: StakingRouter,
    private val interactor: StakingInteractor,
    stakingRelayChainScenarioInteractor: StakingRelayChainScenarioInteractor,
    private val addressIconGenerator: AddressIconGenerator,
    private val resourceManager: ResourceManager,
    private val validationSystem: RewardDestinationValidationSystem,
    private val rewardDestinationInteractor: ChangeRewardDestinationInteractor,
    private val chainRegistry: ChainRegistry,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    private val addressDisplayUseCase: AddressDisplayUseCase,
    private val validationExecutor: ValidationExecutor,
    private val savedStateHandle: SavedStateHandle
) : BaseViewModel(),
    Validatable by validationExecutor,
    ExternalAccountActions by externalAccountActions {

    private val payload = savedStateHandle.get<ConfirmRewardDestinationPayload>(KEY_PAYLOAD)!!

    private val stashFlow = stakingRelayChainScenarioInteractor.selectedAccountStakingStateFlow()
        .filterIsInstance<StakingState.Stash>()
        .share()

    private val controllerAssetFlow = interactor.currentAssetFlow()
        .share()

    val originAccountModelLiveData = stashFlow.map {
        generateDestinationModel(interactor.getProjectedAccount(it.controllerAddress))
    }.asLiveData()

    val rewardDestinationLiveData = flowOf(payload)
        .map { mapRewardDestinationParcelModelToRewardDestinationModel(it.rewardDestination) }
        .inBackground()
        .asLiveData()

    private val _showNextProgress = MutableLiveData(false)
    val showNextProgress: LiveData<Boolean> = _showNextProgress

    val feeLiveData = controllerAssetFlow.map {
        FeeStatus.Loaded(mapFeeToFeeModel(payload.fee, it.token))
    }
        .inBackground()
        .asLiveData()

    fun confirmClicked() {
        sendTransactionIfValid()
    }

    fun backClicked() {
        router.back()
    }

    fun originAccountClicked() {
        val originAddress = originAccountModelLiveData.value?.address ?: return

        showAddressExternalActions(originAddress)
    }

    fun payoutAccountClicked() {
        val payoutDestination = rewardDestinationLiveData.value as? RewardDestinationModel.Payout ?: return

        showAddressExternalActions(payoutDestination.destination.address)
    }

    private fun showAddressExternalActions(address: String) = launch {
        val chainId = controllerAssetFlow.first().token.configuration.chainId
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

    private suspend fun mapRewardDestinationParcelModelToRewardDestinationModel(
        rewardDestinationParcelModel: RewardDestinationParcelModel
    ): RewardDestinationModel {
        return when (rewardDestinationParcelModel) {
            is RewardDestinationParcelModel.Restake -> RewardDestinationModel.Restake
            is RewardDestinationParcelModel.Payout -> {
                val address = rewardDestinationParcelModel.targetAccountAddress
                val accountDisplay = addressDisplayUseCase(address)
                val addressModel = addressIconGenerator.createAddressModel(address, AddressIconGenerator.SIZE_SMALL, accountDisplay)

                RewardDestinationModel.Payout(addressModel)
            }
        }
    }

    private fun sendTransactionIfValid() {
        val rewardDestinationModel = rewardDestinationLiveData.value ?: return

        launch {
            val controllerAsset = controllerAssetFlow.first()
            val stashState = stashFlow.first()

            val payload = RewardDestinationValidationPayload(
                availableControllerBalance = controllerAsset.availableForStaking,
                fee = payload.fee,
                stashState = stashState
            )

            validationExecutor.requireValid(
                validationSystem = validationSystem,
                payload = payload,
                validationFailureTransformer = { rewardDestinationValidationFailure(resourceManager, it) },
                progressConsumer = _showNextProgress.progressConsumer()
            ) {
                sendTransaction(stashState, mapRewardDestinationModelToRewardDestination(rewardDestinationModel))
            }
        }
    }

    private fun sendTransaction(
        stashState: StakingState.Stash,
        rewardDestination: RewardDestination
    ) = launch {
        val setupResult = rewardDestinationInteractor.changeRewardDestination(stashState, rewardDestination)

        _showNextProgress.value = false

        if (setupResult.isSuccess) {
            showMessage(resourceManager.getString(R.string.common_transaction_submitted))

            router.returnToMain()
        } else {
            showError(setupResult.requireException())
        }
    }

    private suspend fun generateDestinationModel(account: StakingAccount): AddressModel {
        return addressIconGenerator.createAddressModel(account.address, AddressIconGenerator.SIZE_SMALL, account.name)
    }
}
