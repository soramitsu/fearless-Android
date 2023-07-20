package jp.co.soramitsu.staking.impl.presentation.staking.controller.set

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.presentation.account.AddressDisplayUseCase
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.data.network.BlockExplorerUrlBuilder
import jp.co.soramitsu.common.mixin.api.RetryPayload
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.combine
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.mediatorLiveData
import jp.co.soramitsu.common.utils.updateFrom
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet.Payload
import jp.co.soramitsu.feature_wallet_api.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.getSupportedExplorers
import jp.co.soramitsu.staking.api.domain.model.StakingAccount
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.getSelectedChain
import jp.co.soramitsu.staking.impl.domain.staking.controller.ControllerInteractor
import jp.co.soramitsu.staking.impl.domain.validations.controller.SetControllerValidationPayload
import jp.co.soramitsu.staking.impl.domain.validations.controller.SetControllerValidationSystem
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.staking.controller.confirm.ConfirmSetControllerPayload
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.wallet.api.data.mappers.mapFeeToFeeModel
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeLoaderMixin
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeStatus
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltViewModel
class SetControllerViewModel @Inject constructor(
    private val interactor: ControllerInteractor,
    private val stakingInteractor: StakingInteractor,
    relayChainInteractor: StakingRelayChainScenarioInteractor,
    private val addressIconGenerator: AddressIconGenerator,
    private val router: StakingRouter,
    private val externalActions: ExternalAccountActions.Presentation,
    private val appLinksProvider: AppLinksProvider,
    private val resourceManager: ResourceManager,
    private val addressDisplayUseCase: AddressDisplayUseCase,
    private val validationExecutor: ValidationExecutor,
    private val validationSystem: SetControllerValidationSystem,
    savedStateHandle: SavedStateHandle
) : BaseViewModel(),
    FeeLoaderMixin,
    ExternalAccountActions by externalActions,
    Validatable by validationExecutor {

    val chainId = savedStateHandle.get<String>(SetControllerFragment.CHAIN_ID_KEY)

    private val accountStakingFlow = if (chainId.isNullOrEmpty()) {
        relayChainInteractor.selectedAccountStakingStateFlow()
    } else {
        relayChainInteractor.stakingStateFlow(chainId)
    }.filterIsInstance<StakingState.Stash>().share()

    val showNotStashAccountWarning = accountStakingFlow.map { stakingState ->
        stakingState.accountAddress != stakingState.stashAddress
    }.asLiveData()

    val stashAccountModel = accountStakingFlow.map {
        generateIcon(it.stashAddress)
    }.asLiveData()

    private val assetFlow = stakingInteractor.currentAssetFlow()
        .share()

    private val _controllerAccountModel = MutableLiveData<AddressModel>()
    val controllerAccountModel: LiveData<AddressModel> = _controllerAccountModel

    override val openBrowserEvent = mediatorLiveData<Event<String>> {
        updateFrom(externalActions.openBrowserEvent)
    }

    private val _showControllerChooserEvent = MutableLiveData<Event<Payload<AddressModel>>>()
    val showControllerChooserEvent: LiveData<Event<Payload<AddressModel>>> = _showControllerChooserEvent

    val isContinueButtonAvailable = combine(
        controllerAccountModel,
        accountStakingFlow.asLiveData(),
        showNotStashAccountWarning
    ) { (selectedController: AddressModel, stakingState: StakingState.Stash, warningShown: Boolean) ->
        selectedController.address != stakingState.controllerAddress && // The user selected account that was not the controller already
            warningShown.not() // The account is stash, so we don't have warning
    }

    override val feeLiveData = MutableLiveData<FeeStatus>()

    override val retryEvent = MutableLiveData<Event<RetryPayload>>()

    val isControllerSelectionEnabled = flowOf { interactor.isControllerFeatureDeprecated(chainId).not() }

    fun onMoreClicked() {
        openBrowserEvent.value = Event(appLinksProvider.setControllerLearnMore)
    }

    fun openExternalActions() {
        viewModelScope.launch {
            val stashAddress = stashAddress()
            val chain = if (chainId.isNullOrEmpty()) {
                stakingInteractor.getSelectedChain()
            } else {
                stakingInteractor.getChain(chainId)
            }
            val supportedExplorers = chain.explorers.getSupportedExplorers(BlockExplorerUrlBuilder.Type.ACCOUNT, stashAddress)
            val externalActionsPayload = ExternalAccountActions.Payload(
                value = stashAddress,
                chainId = chainId,
                chainName = chain.name,
                explorers = supportedExplorers
            )

            externalActions.showExternalActions(externalActionsPayload)
        }
    }

    fun openAccounts() {
        viewModelScope.launch {
            val accountsInNetwork = accountsInCurrentNetwork()

            _showControllerChooserEvent.value = Event(Payload(accountsInNetwork))
        }
    }

    init {
        loadFee()

        viewModelScope.launch {
            if (interactor.isControllerFeatureDeprecated(chainId)) {
                _controllerAccountModel.value = accountStakingFlow.map {
                    generateIcon(it.stashAddress)
                }.first()
            } else {
                _controllerAccountModel.value = accountStakingFlow.map {
                    generateIcon(it.controllerAddress)
                }.first()
            }
        }
    }

    private fun loadFee() {
        feeLiveData.value = FeeStatus.Loading

        viewModelScope.launch(Dispatchers.Default) {
            val chain = if (chainId.isNullOrEmpty()) {
                stakingInteractor.getSelectedChain()
            } else {
                stakingInteractor.getChain(chainId)
            }
            val asset = requireNotNull(stakingInteractor.getUtilityAsset(chain))

            val feeResult = runCatching {
                interactor.estimateFee(controllerAddress(), chain.id)
            }

            val value = if (feeResult.isSuccess) {
                val feeInPlanks = feeResult.getOrThrow()
                val fee = asset.token.amountFromPlanks(feeInPlanks)
                val feeModel = mapFeeToFeeModel(fee, asset.token)

                FeeStatus.Loaded(feeModel)
            } else if (feeResult.exceptionOrNull() is CancellationException) {
                null
            } else {
                retryEvent.postValue(
                    Event(
                        RetryPayload(
                            title = resourceManager.getString(R.string.choose_amount_network_error),
                            message = resourceManager.getString(R.string.choose_amount_error_fee),
                            onRetry = { loadFee() },
                            onCancel = ::backClicked
                        )
                    )
                )

                feeResult.exceptionOrNull()?.printStackTrace()

                FeeStatus.Error
            }
            value?.let {
                feeLiveData.postValue(it)
            }
        }
    }

    fun payoutControllerChanged(newController: AddressModel) {
        _controllerAccountModel.value = newController
    }

    fun backClicked() {
        router.back()
    }

    fun continueClicked() {
        maybeGoToConfirm()
    }

    private suspend fun stashAddress() = accountStakingFlow.first().stashAddress

    private suspend fun controllerAddress() = accountStakingFlow.first().controllerAddress

    private suspend fun accountsInCurrentNetwork(): List<AddressModel> {
        return stakingInteractor.getAccountProjectionsInSelectedChains()
            .map { generateDestinationModel(it) }
    }

    private suspend fun generateDestinationModel(account: StakingAccount): AddressModel {
        return addressIconGenerator.createAddressModel(account.address, AddressIconGenerator.SIZE_SMALL, account.name)
    }

    private suspend fun generateIcon(address: String): AddressModel {
        val name = addressDisplayUseCase(address)
        return addressIconGenerator
            .createAddressModel(
                address,
                AddressIconGenerator.SIZE_SMALL,
                name
            )
    }

    private fun maybeGoToConfirm() = viewModelScope.launch {
        val feeStatus = feeLiveData.value

        if (feeStatus is FeeStatus.Loaded) {
            val controllerAddress = controllerAccountModel.value?.address ?: return@launch

            val payload = SetControllerValidationPayload(
                stashAddress = stashAddress(),
                controllerAddress = controllerAddress,
                fee = feeStatus.feeModel.fee,
                transferable = assetFlow.first().availableForStaking
            )

            validationExecutor.requireValid(
                validationSystem = validationSystem,
                payload = payload,
                validationFailureTransformer = { bondSetControllerValidationFailure(it, resourceManager) }
            ) {
                openConfirm(
                    ConfirmSetControllerPayload(
                        fee = feeStatus.feeModel.fee,
                        stashAddress = payload.stashAddress,
                        controllerAddress = payload.controllerAddress,
                        transferable = payload.transferable,
                        chainId = chainId
                    )
                )
            }
        } else {
            showError(
                resourceManager.getString(R.string.fee_not_yet_loaded_title),
                resourceManager.getString(R.string.fee_not_yet_loaded_message)
            )
        }
    }

    private fun openConfirm(payload: ConfirmSetControllerPayload) {
        router.openConfirmSetController(payload)
    }
}
