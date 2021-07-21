package jp.co.soramitsu.feature_staking_impl.presentation.validators.change.start

import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.Browserable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.ValidatorRecommendatorFactory
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.retractValidators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch

private val RECOMMENDED_FEATURES_IDS = listOf(
    R.string.staking_recommended_feature_1,
    R.string.staking_recommended_feature_2,
    R.string.staking_recommended_feature_3,
    R.string.staking_recommended_feature_4,
    R.string.staking_recommended_feature_5,
)

class CustomValidatorsTexts(
    val title: String,
    val badge: String?
)

class StartChangeValidatorsViewModel(
    private val router: StakingRouter,
    private val validatorRecommendatorFactory: ValidatorRecommendatorFactory,
    private val setupStakingSharedState: SetupStakingSharedState,
    private val resourceManager: ResourceManager,
    private val interactor: StakingInteractor,
) : BaseViewModel(), Browserable {

    override val openBrowserEvent = MutableLiveData<Event<String>>()

    private val maxValidatorsPerNominator = flowOf {
        interactor.maxValidatorsPerNominator()
    }.share()

    val validatorsLoading = MutableStateFlow(true)

    val customValidatorsTexts = setupStakingSharedState.setupStakingProcess.transform {
        when {
            it is SetupStakingProcess.ReadyToSubmit && it.payload.validators.isNotEmpty() -> emit(
                CustomValidatorsTexts(
                    title = resourceManager.getString(R.string.staking_custom_validators_update_list),
                    badge = resourceManager.getString(
                        R.string.staking_max_format,
                        it.payload.validators.size,
                        maxValidatorsPerNominator.first()
                    )
                )
            )
            it is SetupStakingProcess.Validators -> emit(
                CustomValidatorsTexts(
                    title = resourceManager.getString(R.string.staking_select_custom),
                    badge = null
                )
            )
        }
    }

    val recommendedFeaturesText = flow {
        val texts = RECOMMENDED_FEATURES_IDS.joinToString(separator = "\n") {
            val text = resourceManager.getString(it)

            "✅  $text"
        }

        emit(texts)
    }
        .inBackground()
        .share()

    init {
        launch {
            validatorRecommendatorFactory.awaitValidatorLoading(router.currentStackEntryLifecycle)

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
