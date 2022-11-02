package jp.co.soramitsu.staking.impl.presentation.validators.compose

import android.util.Log
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.AlertViewState
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.SelectValidatorsVariantPanelViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.domain.recommendations.ValidatorRecommendatorFactory
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.SelectValidatorFlowState
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class StartSelectValidatorsViewModel @Inject constructor(
    private val resourceManager: ResourceManager,
    private val validatorRecommendatorFactory: ValidatorRecommendatorFactory,
    private val router: StakingRouter,
    private val stakingPoolSharedStateProvider: StakingPoolSharedStateProvider
) : BaseViewModel() {

    private val recommendedState = SelectValidatorsVariantPanelViewState(
        title = resourceManager.getString(R.string.staking_start_change_validators_recommended_title),
        description = resourceManager.getString(R.string.staking_start_change_validators_recommended_subtitle),
        buttonText = resourceManager.getString(R.string.staking_select_suggested),
        additionalInfo = listOf(
            resourceManager.getString(R.string.staking_recommended_feature_1),
            resourceManager.getString(R.string.staking_recommended_feature_2),
            resourceManager.getString(R.string.staking_recommended_feature_3),
            resourceManager.getString(R.string.staking_recommended_feature_4)
        )
    )

    private val manualState = SelectValidatorsVariantPanelViewState<Nothing>(
        title = resourceManager.getString(R.string.staking_start_change_validators_custom_title),
        description = resourceManager.getString(R.string.staking_start_change_validators_custom_subtitle),
        buttonText = resourceManager.getString(R.string.staking_select_custom)
    )

    private val loadingState = MutableStateFlow(true)

    val state = loadingState.map {
        StartSelectValidatorsViewState(recommendedState, manualState, it)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, StartSelectValidatorsViewState(recommendedState, manualState, loadingState.value))

    init {
        launch {
            validatorRecommendatorFactory.awaitBlockCreatorsLoading(router.currentStackEntryLifecycle)

            loadingState.value = false
            launch {
                router.alertResultFlow.collect {
                    Log.d("&&&", "alertResultFlow collected")
                    if(it.isSuccess) {
                        Log.d("&&&", "alertResultFlow is success")
                        setSelectMode(SelectValidatorFlowState.ValidatorSelectMode.RECOMMENDED)
                        router.openSelectValidators()
                    }
                }
            }
        }
    }

    fun onRecommendedClick() = viewModelScope.launch {
        val payload = AlertViewState(
            resourceManager.getString(R.string.staking_suggested_validators_title),
            resourceManager.getString(R.string.alert_suggested_validators),
            resourceManager.getString(R.string.common_continue),
            R.drawable.ic_alert_16
        )
        router.openAlert(payload)
    }

    fun onManualClick() {
        setSelectMode(SelectValidatorFlowState.ValidatorSelectMode.CUSTOM)
        router.openSelectValidators()
    }

    private fun setSelectMode(mode: SelectValidatorFlowState.ValidatorSelectMode) {
        stakingPoolSharedStateProvider.selectValidatorsState.mutate {
            requireNotNull(it?.copy(selectMode = mode))
        }
    }

    fun onBackClick() {
        router.back()
    }
}
