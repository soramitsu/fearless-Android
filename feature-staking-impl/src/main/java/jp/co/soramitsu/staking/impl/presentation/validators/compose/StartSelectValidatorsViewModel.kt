package jp.co.soramitsu.staking.impl.presentation.validators.compose

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.SelectValidatorsVariantPanelViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.domain.recommendations.ValidatorRecommendatorFactory
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@HiltViewModel
class StartSelectValidatorsViewModel @Inject constructor(
    private val resourceManager: ResourceManager,
    private val validatorRecommendatorFactory: ValidatorRecommendatorFactory,
    private val router: StakingRouter
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


    val state = combine(flowOf(1), flowOf(2)) { a1, a2 ->

    }

    init {
        launch {
            validatorRecommendatorFactory.awaitBlockCreatorsLoading(router.currentStackEntryLifecycle)

//            validatorsLoading.value = false
        }
    }
}
