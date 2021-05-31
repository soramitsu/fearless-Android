package jp.co.soramitsu.feature_staking_impl.presentation.staking.controller.set

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.combine
import jp.co.soramitsu.common.utils.mediatorLiveData
import jp.co.soramitsu.common.utils.updateFrom
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet.Payload
import jp.co.soramitsu.feature_account_api.presenatation.account.AddressDisplayUseCase
import jp.co.soramitsu.feature_account_api.presenatation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_staking_api.domain.model.StakingAccount
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.staking.controller.ControllerInteractor
import jp.co.soramitsu.feature_staking_impl.domain.validations.controller.SetControllerValidationPayload
import jp.co.soramitsu.feature_staking_impl.domain.validations.controller.SetControllerValidationSystem
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.FeeLoaderMixin
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.requireFee
import jp.co.soramitsu.feature_staking_impl.presentation.staking.controller.confirm.ConfirmSetControllerPayload
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SetControllerViewModel(
    private val interactor: ControllerInteractor,
    private val stackingInteractor: StakingInteractor,
    private val addressIconGenerator: AddressIconGenerator,
    private val router: StakingRouter,
    private val feeLoaderMixin: FeeLoaderMixin.Presentation,
    private val externalActions: ExternalAccountActions.Presentation,
    private val appLinksProvider: AppLinksProvider,
    private val resourceManager: ResourceManager,
    private val addressDisplayUseCase: AddressDisplayUseCase,
    private val validationExecutor: ValidationExecutor,
    private val validationSystem: SetControllerValidationSystem
) : BaseViewModel(),
    FeeLoaderMixin by feeLoaderMixin,
    ExternalAccountActions by externalActions,
    Validatable by validationExecutor {

    private val accountStakingFlow = stackingInteractor.selectedAccountStakingStateFlow()
        .filterIsInstance<StakingState.Stash>()
        .share()

    val showNotStashAccountWarning = accountStakingFlow.map { stakingState ->
        stakingState.accountAddress != stakingState.stashAddress
    }.asLiveData()

    val stashAccountModel = accountStakingFlow.map {
        generateIcon(it.stashAddress)
    }.asLiveData()

    private val assetFlow = stackingInteractor.currentAssetFlow()
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
            externalActions.showExternalActions(ExternalAccountActions.Payload.fromAddress(stashAddress()))
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
            feeConstructor = { asset ->
                val feeInPlanks = interactor.estimateFee(stashAddress(), controllerAddress())

                asset.token.amountFromPlanks(feeInPlanks)
            },
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
        return stackingInteractor.getAccountsInCurrentNetwork()
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
