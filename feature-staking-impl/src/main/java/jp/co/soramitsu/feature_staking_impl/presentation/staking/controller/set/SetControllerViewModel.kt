package jp.co.soramitsu.feature_staking_impl.presentation.staking.controller.set

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.data.network.BlockExplorerUrlBuilder
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.combine
import jp.co.soramitsu.common.utils.mediatorLiveData
import jp.co.soramitsu.common.utils.updateFrom
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet.Payload
import jp.co.soramitsu.feature_account_api.presentation.account.AddressDisplayUseCase
import jp.co.soramitsu.feature_account_api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_staking_api.domain.model.StakingAccount
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.staking.controller.ControllerInteractor
import jp.co.soramitsu.feature_staking_impl.domain.validations.controller.SetControllerValidationPayload
import jp.co.soramitsu.feature_staking_impl.domain.validations.controller.SetControllerValidationSystem
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.staking.controller.confirm.ConfirmSetControllerPayload
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.fee.FeeLoaderMixin
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.fee.requireFee
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.getSupportedExplorers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SetControllerViewModel(
    private val interactor: ControllerInteractor,
    private val stakingInteractor: StakingInteractor,
    private val relayChainInteractor: StakingRelayChainScenarioInteractor,
    private val addressIconGenerator: AddressIconGenerator,
    private val router: StakingRouter,
    private val feeLoaderMixin: FeeLoaderMixin.Presentation,
    private val externalActions: ExternalAccountActions.Presentation,
    private val appLinksProvider: AppLinksProvider,
    private val resourceManager: ResourceManager,
    private val chainRegistry: ChainRegistry,
    private val addressDisplayUseCase: AddressDisplayUseCase,
    private val validationExecutor: ValidationExecutor,
    private val validationSystem: SetControllerValidationSystem
) : BaseViewModel(),
    FeeLoaderMixin by feeLoaderMixin,
    ExternalAccountActions by externalActions,
    Validatable by validationExecutor {

    private val accountStakingFlow = relayChainInteractor.selectedAccountStakingStateFlow()
        .filterIsInstance<StakingState.Stash>()
        .share()

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

    fun onMoreClicked() {
        openBrowserEvent.value = Event(appLinksProvider.setControllerLearnMore)
    }

    fun openExternalActions() {
        viewModelScope.launch {
            val stashAddress = stashAddress()
            val chainId = assetFlow.first().token.configuration.chainId
            val chain = chainRegistry.getChain(chainId)
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
            _controllerAccountModel.value = accountStakingFlow.map {
                generateIcon(it.controllerAddress)
            }.first()
        }
    }

    private fun loadFee() {
        feeLoaderMixin.loadFee(
            coroutineScope = viewModelScope,
            feeConstructor = { interactor.estimateFee(controllerAddress()) },
            onRetryCancelled = ::backClicked
        )
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

    private fun maybeGoToConfirm() = feeLoaderMixin.requireFee(this) { fee ->
        launch {
            val controllerAddress = controllerAccountModel.value?.address ?: return@launch

            val payload = SetControllerValidationPayload(
                stashAddress = stashAddress(),
                controllerAddress = controllerAddress,
                fee = fee,
                transferable = assetFlow.first().transferable
            )

            validationExecutor.requireValid(
                validationSystem = validationSystem,
                payload = payload,
                validationFailureTransformer = { bondSetControllerValidationFailure(it, resourceManager) }
            ) {
                openConfirm(
                    ConfirmSetControllerPayload(
                        fee = fee,
                        stashAddress = payload.stashAddress,
                        controllerAddress = payload.controllerAddress,
                        transferable = payload.transferable
                    )
                )
            }
        }
    }

    private fun openConfirm(payload: ConfirmSetControllerPayload) {
        router.openConfirmSetController(payload)
    }
}
