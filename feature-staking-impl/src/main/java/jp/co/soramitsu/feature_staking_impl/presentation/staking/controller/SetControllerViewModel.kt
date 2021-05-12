package jp.co.soramitsu.feature_staking_impl.presentation.staking.controller

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
import jp.co.soramitsu.common.utils.mediatorLiveData
import jp.co.soramitsu.common.utils.updateFrom
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet.Payload
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_account_api.presenatation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_staking_api.domain.model.StakingAccount
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.staking.controller.ControllerInteractor
import jp.co.soramitsu.feature_staking_impl.domain.validations.controller.SetControllerValidationPayload
import jp.co.soramitsu.feature_staking_impl.domain.validations.controller.SetControllerValidationSystem
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.fee.FeeLoaderMixin
import jp.co.soramitsu.feature_staking_impl.presentation.common.fee.requireFee
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

    val isContinueButtonAvailable = accountStakingFlow.map { stakingState ->
        showNotStashAccountWarning.value != null && showNotStashAccountWarning.value!!.not()
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

    private suspend fun generateIcon(address: String) = addressIconGenerator
        .createAddressModel(
            address,
            AddressIconGenerator.SIZE_SMALL,
            stackingInteractor.getAccount(address).name
        )

    private fun maybeGoToConfirm() = feeLoaderMixin.requireFee(this) { fee ->
        launch {
            val controllerAddress = controllerAccountModel.value?.address
            if (controllerAddress == null) {
                showError(resourceManager.getString(R.string.staking_controller_address_error))
            } else {
                val payload = SetControllerValidationPayload(
                    stash = accountStakingFlow.first(),
                    controllerAddress = controllerAddress,
                    fee = fee,
                    asset = assetFlow.first().transferable
                )

                validationExecutor.requireValid(
                    validationSystem = validationSystem,
                    payload = payload,
                    validationFailureTransformer = { bondSetControllerValidationFailure(it, resourceManager) }
                ) {
                    openConfirm()
                }
            }
        }
    }

    private fun openConfirm() {
        router.openConfirmSetController()
    }
}
