package jp.co.soramitsu.staking.impl.presentation.validators.change.start

import androidx.lifecycle.MutableLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.Browserable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.domain.recommendations.ValidatorRecommendatorFactory
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.staking.impl.presentation.validators.change.retractValidators
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch

private val RECOMMENDED_FEATURES_IDS = listOf(
    R.string.staking_recommended_feature_1,
    R.string.staking_recommended_feature_2,
    R.string.staking_recommended_feature_3,
    R.string.staking_recommended_feature_4,
    R.string.staking_recommended_feature_5
)

class CustomValidatorsTexts(
    val title: String,
    val badge: String?
)

@HiltViewModel
class StartChangeValidatorsViewModel @Inject constructor(
    private val router: StakingRouter,
    private val validatorRecommendatorFactory: ValidatorRecommendatorFactory,
    private val setupStakingSharedState: SetupStakingSharedState,
    private val resourceManager: ResourceManager,
    private val stakingRelayChainScenarioInteractor: StakingRelayChainScenarioInteractor
) : BaseViewModel(), Browserable {

    override val openBrowserEvent = MutableLiveData<Event<String>>()

    private val maxValidatorsPerNominator = flowOf {
        stakingRelayChainScenarioInteractor.maxValidatorsPerNominator()
    }.share()

    val validatorsLoading = MutableStateFlow(true)

    val customValidatorsTexts = setupStakingSharedState.setupStakingProcess.transform {
        when {
            it is SetupStakingProcess.ReadyToSubmit<*> && it.payload.blockProducers.isNotEmpty() -> emit(
                CustomValidatorsTexts(
                    title = resourceManager.getString(R.string.staking_custom_validators_update_list),
                    badge = resourceManager.getString(
                        R.string.staking_max_format,
                        it.payload.blockProducers.size,
                        maxValidatorsPerNominator.first()
                    )
                )
            )
            it is SetupStakingProcess.SelectBlockProducersStep -> emit(
                CustomValidatorsTexts(
                    title = resourceManager.getString(R.string.staking_select_custom),
                    badge = null
                )
            )
        }
    }

    fun getRecommendedFeaturesIds() = RECOMMENDED_FEATURES_IDS

    init {
        launch {
            validatorRecommendatorFactory.awaitBlockCreatorsLoading(router.currentStackEntryLifecycle)

            validatorsLoading.value = false
        }
    }

    fun goToCustomClicked() {
        router.openSelectCustomValidators()
    }

    fun goToRecommendedClicked() {
        router.openRecommendedValidators()
    }

    fun backClicked() {
        setupStakingSharedState.retractValidators()

        router.back()
    }
}
