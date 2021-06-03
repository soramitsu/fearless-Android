package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom

import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.CustomContributeManager
import jp.co.soramitsu.feature_crowdloan_impl.presentation.CrowdloanRouter
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.model.CustomContributePayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

class CustomContributeViewModel(
    private val customContributeManager: CustomContributeManager,
    val payload: CustomContributePayload,
    private val router: CrowdloanRouter
) : BaseViewModel() {

    val customFlowType = payload.parachainMetadata.customFlow!!

    val viewStateFlow = flow {
        emit(customContributeManager.createNewState(customFlowType, viewModelScope, payload))
    }.inBackground()
        .share()

    val applyButtonState = viewStateFlow
        .flatMapLatest { it.applyActionState }
        .share()

    private val _applyingInProgress = MutableStateFlow(false)
    val applyingInProgress: Flow<Boolean> = _applyingInProgress

    fun backClicked() {
        router.back()
    }

    fun applyClicked() {
        launch {
            _applyingInProgress.value = true

            viewStateFlow.first().generatePayload()
                .onSuccess {
                    router.setCustomBonus(it)
                    router.back()
                }
                .onFailure(::showError)

            _applyingInProgress.value = false
        }
    }
}
