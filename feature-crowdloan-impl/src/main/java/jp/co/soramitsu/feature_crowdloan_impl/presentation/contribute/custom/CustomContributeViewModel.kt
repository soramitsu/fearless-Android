package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom

import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.feature_account_api.domain.interfaces.SelectedAccountUseCase
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.CustomContributeManager
import jp.co.soramitsu.feature_crowdloan_impl.presentation.CrowdloanRouter
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.moonbeam.MoonbeamContributeViewState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class CustomContributeViewModel(
    private val customContributeManager: CustomContributeManager,
    val payload: CustomContributePayload,
    private val router: CrowdloanRouter,
    accountUseCase: SelectedAccountUseCase,
    addressModelGenerator: AddressIconGenerator,
) : BaseViewModel() {

    val customFlowType = payload.parachainMetadata.customFlow!!

    private val _viewStateFlow = MutableStateFlow(customContributeManager.createNewState(customFlowType, viewModelScope, payload))
    val viewStateFlow: Flow<CustomContributeViewState> = _viewStateFlow

    val selectedAddressModelFlow = _viewStateFlow
        .flatMapLatest { accountUseCase.selectedAccountFlow() }
        .map { addressModelGenerator.createAddressModel(it.address, AddressIconGenerator.SIZE_SMALL, it.name) }
        .share()

    val applyButtonState = _viewStateFlow
        .flatMapLatest { it.applyActionState }
        .share()

    private val _applyingInProgress = MutableStateFlow(false)
    val applyingInProgress: Flow<Boolean> = _applyingInProgress

    fun backClicked() {
        if (payload.isMoonbeam) {
            val currentStep = (_viewStateFlow.value as? MoonbeamContributeViewState)?.customContributePayload?.step
            if (currentStep == 0) {
                router.back()
            } else {
                launch {
                    val nextStep = currentStep?.dec() ?: 0
                    handleMoonbeamFlow(nextStep)
                }
            }
        } else {
            router.back()
        }
    }

    fun applyClicked() {
        launch {
            _applyingInProgress.value = true

            if (payload.isMoonbeam) {
                val nextStep = (_viewStateFlow.value as? MoonbeamContributeViewState)?.customContributePayload?.step?.inc() ?: 0
                handleMoonbeamFlow(nextStep)
            } else {
                //идём на след стейт
                _viewStateFlow.first().generatePayload()
                    .onSuccess {
                        router.setCustomBonus(it)
                        router.back()
                    }
                    .onFailure(::showError)
            }

            _applyingInProgress.value = false
        }
    }

    private suspend fun handleMoonbeamFlow(nextStep: Int = 0) {
        val isPrivacyAccepted = (_viewStateFlow.value as? MoonbeamContributeViewState)?.customContributePayload?.isPrivacyAccepted ?: (nextStep > 0)

        val nextStepPayload = CustomContributePayload(
            payload.paraId,
            payload.parachainMetadata,
            payload.amount,
            payload.previousBonusPayload,
            nextStep,
            isPrivacyAccepted
        )
        _viewStateFlow.emit(customContributeManager.createNewState(customFlowType, viewModelScope, nextStepPayload))
    }
}
