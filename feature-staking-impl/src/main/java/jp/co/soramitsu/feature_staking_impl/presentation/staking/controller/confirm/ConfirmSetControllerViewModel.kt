package jp.co.soramitsu.feature_staking_impl.presentation.staking.controller.confirm

import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.feature_account_api.presenatation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.staking.controller.ControllerInteractor
import jp.co.soramitsu.feature_staking_impl.domain.validations.controller.SetControllerValidationPayload
import jp.co.soramitsu.feature_staking_impl.domain.validations.controller.SetControllerValidationSystem
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.fee.FeeStatus
import jp.co.soramitsu.feature_staking_impl.presentation.common.mapFeeToFeeModel
import jp.co.soramitsu.feature_staking_impl.presentation.staking.controller.set.bondSetControllerValidationFailure
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ConfirmSetControllerViewModel(
    private val router: StakingRouter,
    private val controllerInteractor: ControllerInteractor,
    private val addressIconGenerator: AddressIconGenerator,
    private val _payload: ConfirmSetControllerPayload,
    private val interactor: StakingInteractor,
    private val resourceManager: ResourceManager,
    private val externalActions: ExternalAccountActions.Presentation,
    private val validationExecutor: ValidationExecutor,
    private val validationSystem: SetControllerValidationSystem
) : BaseViewModel(),
    Validatable by validationExecutor {

    private val assetFlow = interactor.assetFlow(_payload.stashAddress)
        .share()

    val feeStatusLiveData = assetFlow.map { asset ->
        val feeModel = mapFeeToFeeModel(_payload.fee, asset.token)

        FeeStatus.Loaded(feeModel)
    }
        .inBackground()
        .asLiveData()

    val stashAddressLiveData = liveData {
        emit(generateIcon(_payload.stashAddress))
    }
    val controllerAddressLiveData = liveData {
        emit(generateIcon(_payload.controllerAddress))
    }

    fun confirmClicked() {
        maybeConfirm()
    }

    fun openStashExternalActions() {
        viewModelScope.launch {
            externalActions.showExternalActions(ExternalAccountActions.Payload.fromAddress(_payload.stashAddress))
        }
    }

    fun openControllerExternalActions() {
        viewModelScope.launch {
            externalActions.showExternalActions(ExternalAccountActions.Payload.fromAddress(_payload.controllerAddress))
        }
    }

    private fun maybeConfirm() = launch {

        val payload = SetControllerValidationPayload(
            stashAddress = _payload.stashAddress,
            controllerAddress = _payload.controllerAddress,
            fee = _payload.fee,
            tokenType = _payload.tokenType
        )

        validationExecutor.requireValid(
            validationSystem = validationSystem,
            payload = payload,
            validationFailureTransformer = { bondSetControllerValidationFailure(it, resourceManager) }
        ) {
            sendTransaction()
        }
    }

    private fun sendTransaction() = launch {
        val result = controllerInteractor.setController(
            stashAccountAddress = _payload.stashAddress,
            controllerAccountAddress = _payload.controllerAddress
        )

        if (result.isSuccess) {
            showMessage(resourceManager.getString(R.string.staking_controller_change_success))

            router.returnToMain()
        }
    }

    private suspend fun generateIcon(address: String) = addressIconGenerator
        .createAddressModel(
            address,
            AddressIconGenerator.SIZE_SMALL,
            interactor.getAccount(address).name
        )
}
